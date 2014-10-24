package org.ndexbio.task.parsingengines;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;

import org.biopax.paxtools.controller.EditorMap;
import org.biopax.paxtools.controller.PropertyEditor;
import org.biopax.paxtools.controller.SimpleEditorMap;
import org.biopax.paxtools.io.BioPAXIOHandler;
import org.biopax.paxtools.io.SimpleIOHandler;
import org.biopax.paxtools.model.BioPAXElement;
import org.biopax.paxtools.model.Model;
import org.biopax.paxtools.model.level3.PublicationXref;
import org.biopax.paxtools.model.level3.RelationshipXref;
import org.biopax.paxtools.model.level3.UnificationXref;
import org.biopax.paxtools.model.level3.XReferrable;
import org.biopax.paxtools.model.level3.Xref;
import org.ndexbio.common.NdexClasses;
import org.ndexbio.common.access.NdexDatabase;
import org.ndexbio.common.exceptions.NdexException;
import org.ndexbio.common.persistence.orientdb.NdexPersistenceService;
import org.ndexbio.common.util.TermStringType;
import org.ndexbio.common.util.TermUtilities;
import org.ndexbio.model.object.NdexPropertyValuePair;
import org.ndexbio.model.object.ProvenanceEntity;
import org.ndexbio.model.object.SimplePropertyValuePair;
import org.ndexbio.model.object.network.NetworkSummary;
import org.ndexbio.model.tools.ProvenanceHelpers;
import org.ndexbio.task.parsingengines.IParsingEngine;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.io.Files;

public class BioPAXParser implements IParsingEngine {
	private final File bioPAXFile;
	private final String bioPAXURI;
	private List<String> msgBuffer;

	private static Logger logger = Logger.getLogger("BioPAXParser");
	
	private Map<String, Long> rdfIdToElementIdMap;

	private NdexPersistenceService persistenceService;

	public BioPAXParser(String fn, String ownerName, NdexDatabase db)
			throws Exception {
		Preconditions.checkArgument(!Strings.isNullOrEmpty(fn),
				"A filename is required");
		Preconditions.checkArgument(!Strings.isNullOrEmpty(ownerName),
				"A network owner name is required");
		this.msgBuffer = Lists.newArrayList();
		if (fn.startsWith("/") || fn.matches("^[a-zA-Z]:.*"))
			this.bioPAXFile = new File(fn);
		else
			this.bioPAXFile = new File(getClass().getClassLoader()
					.getResource(fn).toURI());
		this.bioPAXURI = bioPAXFile.toURI().toString();
		this.persistenceService = new NdexPersistenceService(db);
		this.rdfIdToElementIdMap = new HashMap<String, Long>();

		String title = Files.getNameWithoutExtension(this.bioPAXFile.getName());

		persistenceService.createNewNetwork(ownerName, title, null);

	}

	public List<String> getMsgBuffer() {
		return this.msgBuffer;
	}

	public String getBioPAXURI() {
		return bioPAXURI;
	}

	public File getBioPAXFile() {
		return bioPAXFile;
	}

	@Override
	public void parseFile() throws NdexException {
		BufferedReader bufferedReader = null;
		try {

			this.getMsgBuffer()
					.add("Parsing lines from " + this.getBioPAXURI());

			this.processBioPAX(this.getBioPAXFile());

			// add provenance to network
			NetworkSummary currentNetwork = this.persistenceService
					.getCurrentNetwork();

			String uri = NdexDatabase.getURIPrefix();

			ProvenanceEntity provEntity = ProvenanceHelpers
					.createProvenanceHistory(currentNetwork, uri,
							"FILE_UPLOAD", currentNetwork.getCreationTime(),
							(ProvenanceEntity) null);
			provEntity.getCreationEvent().setEndedAtTime(
					new Timestamp(Calendar.getInstance().getTimeInMillis()));

			List<SimplePropertyValuePair> l = provEntity.getCreationEvent()
					.getProperties();
			l.add(new SimplePropertyValuePair("filename", this.bioPAXFile
					.getName()));

			this.persistenceService.setNetworkProvenance(provEntity);

			// close database connection
			this.persistenceService.persistNetwork();

		} catch (Exception e) {
			// delete network and close the database connection
			e.printStackTrace();
			this.persistenceService.abortTransaction();
			throw new NdexException("Error occurred when loading file "
					+ this.bioPAXFile.getName() + ". " + e.getMessage());
		} finally {
			if (bufferedReader != null)
				try {
					bufferedReader.close();
				} catch (IOException e) {
				}
		}
	}

	private void processBioPAX(File f) throws Exception {
		FileInputStream fin = new FileInputStream(f);
		BioPAXIOHandler handler = new SimpleIOHandler();
		Model model = handler.convertFromOWL(fin);
		this.loadBioPAXModel(model);
	}

	private void loadBioPAXModel(Model model) throws Exception {
		addBioPAXNamespaces(model);

		Set<BioPAXElement> elementSet = model.getObjects();
		//
		// Iterate over all elements to create Node, Citation and BaseTerm
		// objects
		//
		for (BioPAXElement bpe : elementSet) {
			if (bpe instanceof Xref) {
				// Process Xrefs to create BaseTerm and Citation objects
				this.processXREFElement(bpe);
			} else {
				// Process all Other Elements to create Node objects
				this.processElementToNode(bpe);
			}
		}
		//
		// Iterate over all BioPAX elements to
		// process all Properties in each Element
		// to create NDExPropertyValuePair and Edge objects
		//
		for (BioPAXElement bpe : elementSet) {
			if (bpe instanceof Xref) {
				// Skip Xrefs
			} else {
				// Process all other Elements
				this.processElementProperties(bpe);
			}
		}
	}

	private void processElementToNode(BioPAXElement bpe) throws ExecutionException, NdexException {
		String rdfId = bpe.getRDFId();
		String className = bpe.getClass().getName();
		String simpleName = bpe.getModelInterface().getSimpleName();
		// System.out.println("Element To Node: " + rdfId + ": " + simpleName);
		// this.persistenceService.
		// create the node, map the id to the rdfId
		// add a property to the node, setting bp:nodeType to the simpleName
		Long nodeId = this.persistenceService.getNodeIdByBaseTerm(rdfId);
		//Long nodeId = this.persistenceService.getNodeId();
		this.mapRdfIdToElementId(rdfId, nodeId);
	}

	private void processElementProperties(BioPAXElement bpe) throws ExecutionException, NdexException {
		String rdfId = bpe.getRDFId();
		// Get the elementId for the Node corresponding to this rdfId
		Long nodeId = this.getNodeIdByRDFId(rdfId);

		String className = bpe.getClass().getName();
		String simpleName = bpe.getModelInterface().getSimpleName();
		// System.out.println("Properties for: " + rdfId + ": " + simpleName);
		//
		// To access properties requires an EditorMap
		// to get all editors for the BioPAX element
		//
		EditorMap editorMap = SimpleEditorMap.L3;
		Set<PropertyEditor> editors = editorMap.getEditorsOf(bpe);
		//
		// iterate over the property editors
		//
		for (PropertyEditor editor : editors) {
			//
			// iterate over the values for each editor:
			//
			// For each property that has a value or values, we want to see if
			// whether each value is a literal or a resource
			//
			// If the value is a Xref resource, handle specially:
			// - link the current Node to a BaseTerm or Citation
			//

			// If the value is a Resource of any other type:
			// - create an Edge from the current Node to the Node for that
			// Resource
			//
			// Else, the value is a literal:
			// - create an NdexPropertyValuePair and add it to the current Node
			// - (note that Edges do not have properties in BioPAX3, only Nodes)
			//
			List<NdexPropertyValuePair> literalProperties = new ArrayList<NdexPropertyValuePair>();
			for (Object val : editor.getValueFromBean(bpe)) {
				// System.out.println("       Property: " + editor.getProperty()
				// + " : (" + val.getClass().getName() + ") " + val.toString());
				if (val instanceof PublicationXref) {
					processPublicationXrefProperty(editor,
							(PublicationXref) val, nodeId);
				} else if (val instanceof UnificationXref) {
					processUnificationXrefProperty(editor, (UnificationXref) val,
							nodeId);
				} else if (val instanceof RelationshipXref) {
					processRelationshipXrefProperty(editor, (RelationshipXref) val,
							nodeId);
				} else if (val instanceof BioPAXElement){
					// create the edge
					processEdge(editor, (BioPAXElement) val, nodeId);
				} else if (null != val){
					// queue up a property to be in the set to add
					String propertyName = editor.getProperty();
					String valueString = val.toString();
					NdexPropertyValuePair pvp = new NdexPropertyValuePair(propertyName, valueString);
					literalProperties.add(pvp);
				}
			}
			literalProperties.add(new NdexPropertyValuePair("ndex:biopaxType", bpe.getModelInterface().getSimpleName()));
			this.persistenceService.setNodeProperties(nodeId, literalProperties, null);

		}

	}

	private void processEdge(
			PropertyEditor editor,
			BioPAXElement bpe, 
			Long subjectNodeId) throws NdexException, ExecutionException {
		Long objectNodeId = getTermIdByRdfId(bpe.getRDFId());
		// Determine the predicate Id from the editor
		Long predicateId = getPropertyEditorPredicateId(editor);
		Long supportId = null;
		Long citationId = null;
		Map<String,String> annotation = null;
		this.persistenceService.createEdge(subjectNodeId, objectNodeId, predicateId, supportId, citationId, annotation);		
	}
	
	private Long getPropertyEditorPredicateId(PropertyEditor editor) throws ExecutionException{
		String propertyName  = editor.getProperty();
		Long predicateId = this.persistenceService.getBaseTermId("bp", propertyName);
		return predicateId;
	}

	private void processUnificationXrefProperty(
			PropertyEditor editor,
			UnificationXref xref, 
			Long nodeId) throws ExecutionException, NdexException {
		Long termId = getTermIdByRdfId(xref.getRDFId());
		//this.persistenceService.setNodeRepresentTerm(nodeId, termId);
		this.persistenceService.addAliasToNode(nodeId,termId);	
	}

	private void processRelationshipXrefProperty(
			PropertyEditor editor,
			RelationshipXref xref, 
			Long nodeId) throws ExecutionException {
		Long termId = getTermIdByRdfId(xref.getRDFId());
		this.persistenceService.addRelatedTermToNode(nodeId, termId);
	}

	private void processPublicationXrefProperty(
			PropertyEditor editor,
			PublicationXref xref, 
			Long nodeId) throws ExecutionException, NdexException {
		Long citationId = getCitationIdByRdfId(xref.getRDFId());
		this.persistenceService.addCitationToElement(nodeId, citationId, NdexClasses.Node);
	}

	private void processXREFElement(BioPAXElement xref) throws NdexException,
			ExecutionException {
		if (xref instanceof PublicationXref) {
			processPublicationXref(xref);
		} else if (xref instanceof UnificationXref) {
			processUnificationXref(xref);
		} else if (xref instanceof RelationshipXref) {
			processRelationshipXref(xref);
		} else {
			// TBD: turn this into an exception?
			String name = xref.getClass().getName();
			System.out.println("Unexpected xref of type: " + name);
		}
	}

	private void processRelationshipXref(BioPAXElement xref) throws NdexException, ExecutionException {
		String rdfId = xref.getRDFId();
		String name = xref.getClass().getName();
		System.out.println("BaseTerm (r): " + rdfId + ": " + name);
		RelationshipXref rXref = (RelationshipXref) xref;
		// These are the Xref properties
		// that we have available for the the BaseTerm and Namespace
		//Map<String, Object> annotations = uXref.getAnnotations();
		//Set<String> comments = uXref.getComment();
		String xrefDb = rXref.getDb();
		String xrefDbVersion = rXref.getDbVersion();
		String xrefId = rXref.getId();
		String xrefIdVersion = rXref.getIdVersion();
		Set<XReferrable> refersTo = rXref.getXrefOf();
		
		Long termId = null;
		if (null != xrefId && null != xrefDb) {
			// We have both an identifier string for a BaseTerm
			// AND a prefix string for a Namespace
			termId = this.persistenceService.getBaseTermId(xrefDb + ":" + xrefId);
		} else if (null != xrefId) {
			// We have an identifier string for a BaseTerm but no Namespace prefix
			termId = this.persistenceService.getBaseTermId(xrefId);
		} else {
			// bad xref with no id!
			throw new NdexException("no id for UnificationXref " + rdfId);
		}
		this.mapRdfIdToElementId(rdfId, termId);
		
		System.out.println("BaseTerm (r): " + rdfId + " -> " + termId);
		
	}

	private void processUnificationXref(BioPAXElement xref) throws NdexException, ExecutionException {
		String rdfId = xref.getRDFId();
		String name = xref.getClass().getName();
		
		UnificationXref uXref = (UnificationXref) xref;
		// These are the Xref properties
		// that we have available for the the BaseTerm and Namespace
		Map<String, Object> annotations = uXref.getAnnotations();
		Set<String> comments = uXref.getComment();
		String xrefDb = uXref.getDb();
		String xrefDbVersion = uXref.getDbVersion();
		String xrefId = uXref.getId();
		String xrefIdVersion = uXref.getIdVersion();
		Set<XReferrable> refersTo = uXref.getXrefOf();
		
		Long termId = null;
		if (null != xrefId && null != xrefDb) {
			// We have both an identifier string for a BaseTerm
			// AND a prefix string for a Namespace
			termId = this.persistenceService.getBaseTermId(xrefDb + ":" + xrefId);
		} else if (null != xrefId) {
			// We have an identifier string for a BaseTerm but no Namespace prefix
			termId = this.persistenceService.getBaseTermId(xrefId);
		} else {
			// bad xref with no id!
			throw new NdexException("no id for UnificationXref " + rdfId);
		}
		this.mapRdfIdToElementId(rdfId, termId);
		
		System.out.println("BaseTerm (u): " + rdfId + " -> " + termId);

	}

	private void processPublicationXref(BioPAXElement xref)
			throws NdexException, ExecutionException {
		String rdfId = xref.getRDFId();
		String name = xref.getClass().getName();
		
		PublicationXref pubXref = (PublicationXref) xref;

		// These are the Xref properties
		// that we can encode in the Citation
		Map<String, Object> annotations = pubXref.getAnnotations();
		Set<String> authors = pubXref.getAuthor();
		Set<String> comments = pubXref.getComment();
		String xrefDb = pubXref.getDb();
		String xrefDbVersion = pubXref.getDbVersion();
		String xrefId = pubXref.getId();
		String xrefIdVersion = pubXref.getIdVersion();
		Set<String> sources = pubXref.getSource();
		String xrefTitle = pubXref.getTitle();
		Set<String> urls = pubXref.getUrl();
		int year = pubXref.getYear();
		Set<XReferrable> refersTo = pubXref.getXrefOf();

		/*
		 * 
		 * An xref that defines a reference to a publication such as a journal
		 * article, book, web page, or software manual. The reference may or may
		 * not be in a database, although references to PubMed are preferred
		 * when possible. The publication should make a direct reference to the
		 * instance it is attached to.
		 * 
		 * Comment: Publication xrefs should make use of PubMed IDs wherever
		 * possible. The db property of an xref to an entry in PubMed should use
		 * the string “PubMed” and not “MEDLINE”. Examples: PubMed:10234245
		 * 
		 * therefore, if both xref.db and xref.id are available,
		 * Citation.identifier = xref.id and Citation.idType = xref.db
		 * 
		 * The following properties may be used when the db and id fields cannot
		 * be used, such as when referencing a publication that is not in
		 * PubMed. The url property should not be used to reference publications
		 * that can be uniquely referenced using a db, id pair.
		 * 
		 * therefore, if xref.url is available, the second choices is:
		 * Citation.identifier = xref.url and Citation.idType = "url"
		 * 
		 * author - The authors of this publication, one per property value.
		 * 
		 * corresponds directly to Citation.contributors
		 * 
		 * title - The title of the publication.
		 * 
		 * corresponds directly to Citation.title
		 * 
		 * Store as pv pairs:
		 * 
		 * dbVersion idVersion source - The source in which the reference was
		 * published, such as: a book title, or a journal title and volume and
		 * pages. url - The URL at which the publication can be found, if it is
		 * available through the Web. y ear - The year in which this publication
		 * was published. store as value with datatype "integer"
		 */

		String identifier = "unspecified";
		String idType = "unspecified";
		if (null != xrefId && null != xrefDb) {
			identifier = xrefId;
			idType = xrefDb;
		} else if (null != urls && urls.size() > 0) {
			identifier = urls.toArray()[0].toString();

		} else if (null != xrefId) {
			identifier = xrefId;
		}

		List<String> contributors = new ArrayList<String>();
		if (null != authors) {
			for (String author : authors) {
				contributors.add(author);
			}
		}
		// These are the Citation properties:
		// "contributors",
		// "identifier",
		// "idType"
		// "title"

		// Other Xref properties can be stored as NdexPropertyValuePair objects
		// "properties",

		// At the moment, no BioPAX3 properties are mapped to ndex presentation
		// properties
		// "presentationProperties",

		// Create the citation
		
		Long citationId = this.persistenceService.getCitationId(xrefTitle,
				idType, identifier, contributors);
		
		System.out.println("Citation: " + rdfId + " -> " + citationId);

		this.mapRdfIdToElementId(rdfId, citationId);

	}

	private void addBioPAXNamespaces(Model model) throws NdexException {
		Map<String,String> prefixMap = model.getNameSpacePrefixMap();
		for (Entry<String, String> pair : prefixMap.entrySet()){
			String prefix = pair.getKey();
			String uri = pair.getValue();
			this.persistenceService.createNamespace2(prefix, uri);
		}
	}
	
	
	private Long getNodeIdByRDFId(String rdfId) {
		return this.rdfIdToElementIdMap.get(rdfId);
	}
	
	private Long getTermIdByRdfId(String rdfId) {
		return this.rdfIdToElementIdMap.get(rdfId);
	}
	
	private Long getCitationIdByRdfId(String rdfId) {
		return this.rdfIdToElementIdMap.get(rdfId);
	}
	
	private void mapRdfIdToElementId(String rdfId, Long elementId) throws NdexException {
		Long currentId = this.getCitationIdByRdfId(rdfId);
		if (currentId != null && currentId != elementId){
			throw new NdexException(
					"Attempted to map rdfId = " + rdfId + 
					" to elementId = " + elementId + 
					" but it is already mapped to " + currentId);
		}
		this.rdfIdToElementIdMap.put(rdfId, elementId);

	}
	
	// fragments
	/*
	 * 
	 * this.getMsgBuffer().add(e.getMessage());
	 * 
	 * ---------
	 * 
	 * counter ++; if ( counter % 2000 == 0 ) { logger.info("processed " +
	 * counter + " lines so far. commit this batch.");
	 * this.persistenceService.commit(); }
	 * 
	 * -----------
	 * 
	 * Long citationId = this.persistenceService.getCitationId( "",
	 * NdexPersistenceService.defaultCitationType,
	 * NdexPersistenceService.pmidPrefix + pubmedIdTokens[1], null); //
	 * this.pubmedIdSet.add(pubmedIdTokens[1]);
	 * this.persistenceService.addCitationToElement(edgeId, citationId,
	 * NdexClasses.Edge);
	 * 
	 * ----------
	 * 
	 * Long participantNodeId =
	 * this.persistenceService.getNodeIdByBaseTerm(participantIdentifier);
	 * 
	 * ----------
	 * 
	 * if (tokens.length > 3) { String[] unificationAliases =
	 * tokens[3].split(";");
	 * this.persistenceService.addAliasToNode(participantNodeId
	 * ,unificationAliases); if (tokens.length > 4) { String[]
	 * relationshipAliases = tokens[4].split(";");
	 * this.persistenceService.addRelatedTermToNode(participantNodeId,
	 * relationshipAliases); } }
	 * 
	 * -------------
	 * 
	 * NdexPropertyValuePair p = new NdexPropertyValuePair ("URI", values[2]);
	 * props.add(p);
	 * 
	 * -------------
	 * 
	 * 
	 * this.persistenceService.setNetworkProperties(props, null);
	 * 
	 * 
	 * -------------
	 * 
	 * this.persistenceService.createNamespace2("UniProt",
	 * "http://identifiers.org/uniprot/");
	 */

}
