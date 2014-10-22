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
import java.util.List;
import java.util.Map;
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

	private NdexPersistenceService persistenceService;

	public BioPAXParser(String fn, String ownerName, NdexDatabase db) throws Exception {
		Preconditions.checkArgument(!Strings.isNullOrEmpty(fn),
				"A filename is required");
		Preconditions.checkArgument(!Strings.isNullOrEmpty(ownerName),
				"A network owner name is required");
		this.msgBuffer = Lists.newArrayList();
		if ( fn.startsWith("/") || fn.matches("^[a-zA-Z]:.*")) 
			this.bioPAXFile = new File(fn);
		else
		    this.bioPAXFile = new File(getClass().getClassLoader().getResource(fn).toURI());
		this.bioPAXURI = bioPAXFile.toURI().toString();
		this.persistenceService = new NdexPersistenceService(db);
		
		String title =  Files.getNameWithoutExtension(this.bioPAXFile.getName());

		persistenceService.createNewNetwork(ownerName, title, null);
		
		this.addBioPAXNamespaces();

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
	public void parseFile() throws  NdexException {
		BufferedReader bufferedReader = null;
		try {

			this.getMsgBuffer().add("Parsing lines from " + this.getBioPAXURI());

			this.processBioPAX(this.getBioPAXFile());

			//add provenance to network
			NetworkSummary currentNetwork = this.persistenceService.getCurrentNetwork();
			
			String uri = NdexDatabase.getURIPrefix();

			ProvenanceEntity provEntity = ProvenanceHelpers.createProvenanceHistory(currentNetwork,
					uri, "FILE_UPLOAD", currentNetwork.getCreationTime(), (ProvenanceEntity)null);
			provEntity.getCreationEvent().setEndedAtTime(new Timestamp(Calendar.getInstance().getTimeInMillis()));
			
			List<SimplePropertyValuePair> l = provEntity.getCreationEvent().getProperties();
			l.add(	new SimplePropertyValuePair ( "filename",this.bioPAXFile.getName()) );
			
			this.persistenceService.setNetworkProvenance(provEntity);
			
			// close database connection
			this.persistenceService.persistNetwork();
			
		} catch (Exception e) {
			// delete network and close the database connection
			e.printStackTrace();
			this.persistenceService.abortTransaction();
			throw new NdexException("Error occurred when loading file " +
					this.bioPAXFile.getName() + ". " + e.getMessage() );
		} finally {
			if ( bufferedReader != null )
				try {
					bufferedReader.close();
				} catch (IOException e) {}
		}
	}
	
	private void processBioPAX(File f) throws Exception{
		FileInputStream fin = new FileInputStream(f);
		BioPAXIOHandler handler = new SimpleIOHandler();
		Model model = handler.convertFromOWL(fin);
		this.loadBioPAXModel(model);
	}
	
	private void loadBioPAXModel(Model model) throws Exception{
		Set<BioPAXElement> elementSet = model.getObjects();
		//
		// Iterate over all elements to create Node, Citation and BaseTerm objects
		//
		for (BioPAXElement bpe : elementSet) {
			if (bpe instanceof Xref){
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
			if (bpe instanceof Xref){
				// Skip Xrefs
			} else {
				// Process all other Elements
				this.processElementProperties(bpe);
			}
		}
	}
	
	private void processElementToNode(BioPAXElement bpe){
		String rdfId = bpe.getRDFId();
		String className = bpe.getClass().getName();
		String simpleName = bpe.getModelInterface().getSimpleName();
		System.out.println("Element To Node: " + rdfId + ": " + simpleName);
		
	}
	
	private void processElementProperties(BioPAXElement bpe){
		String rdfId = bpe.getRDFId();
		String className = bpe.getClass().getName();
		String simpleName = bpe.getModelInterface().getSimpleName();
		System.out.println("Properties for: " + rdfId + ": " + simpleName);
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
			//     - link the current Node to a BaseTerm or Citation
			//
			// If the value is a Resource of any other type:
			//     - create an Edge from the current Node to the Node for that Resource
			// 
			// Else, the value is a literal:
			//     - create an NdexPropertyValuePair and add it to the current Node
			//     - (note that Edges do not have properties in BioPAX3, only Nodes)
			//
			for (Object val : editor.getValueFromBean(bpe)) {
				System.out.println("       Property: " + editor.getProperty() + " : ("
						+ val.getClass().getName() + ") " + val.toString());
			}

		}
		
	}
	
	private void processXREFElement(BioPAXElement xref) throws NdexException, ExecutionException{	
		if (xref instanceof PublicationXref){
			processPublicationXref(xref);				
		} else if (xref instanceof UnificationXref){
			processUnificationXref(xref);		
		} else if (xref instanceof RelationshipXref){
			processRelationshipXref(xref);			
		} else {
			// TBD: turn this into an exception?
			String name = xref.getClass().getName();
			System.out.println("Unexpected xref of type: " + name);
		}
	}

	/*
	private void handleXrefProperty(long elementId, long ){
		
		Long elementId = null
		this.persistenceService.addCitationToElement(edgeId, citationId, NdexClasses.Edge);
	}
*/
	
	private void processRelationshipXref(BioPAXElement xref) {
		String rdfId = xref.getRDFId();
		String name = xref.getClass().getName();
		System.out.println("BaseTerm (r): " + rdfId + ": " + name );
		
		// 	this.persistenceService.addRelatedTermToNode(participantNodeId, relationshipAliases);

		
	}

	private void processUnificationXref(BioPAXElement xref) {
		String rdfId = xref.getRDFId();
		String name = xref.getClass().getName();
		System.out.println("BaseTerm (u): " + rdfId + ": " + name );
		
		// this.persistenceService.addAliasToNode(participantNodeId,unificationAliases);
		
	}

	private void processPublicationXref(BioPAXElement xref) throws NdexException, ExecutionException {
		String rdfId = xref.getRDFId();
		String name = xref.getClass().getName();
		System.out.println("Citation: " + rdfId + ": " + name );
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

An xref that defines a reference to a publication such as a
journal article, book, web page, or software manual. The reference
may or may not be in a database, although references to PubMed are
preferred when possible. The publication should make a direct
reference to the instance it is attached to.

Comment: Publication xrefs should make use of PubMed IDs wherever
possible. The db property of an xref to an entry in PubMed should use
the string “PubMed” and not “MEDLINE”.
Examples: PubMed:10234245

*therefore, if both xref.db and xref.id are available, 
*Citation.identifier = xref.id and Citation.idType = xref.db

The following properties may be used when the db and id fields cannot
be used, such as when referencing a publication that is not in PubMed.
The url property should not be used to reference publications that can
be uniquely referenced using a db, id pair. 

*therefore, if xref.url is available, the second choices is:
* Citation.identifier = xref.url and Citation.idType = "url"

author - The authors of this publication, one per property value.

* corresponds directly to Citation.contributors

title - The title of the publication.

* corresponds directly to Citation.title

* Store as pv pairs:

dbVersion
idVersion
source - The source in which the reference was published, such as: a
book title, or a journal title and volume and pages.
url - The URL at which the publication can be found, if it is available
through the Web.
y ear - The year in which this publication was published. 
* store as value with datatype "integer"
		 */
		
		String identifier = "unspecified";
		String idType = "unspecified";
		if (null != xrefId && null != xrefDb){
			identifier = xrefId;
			idType = xrefDb;
		} else if (null != urls && urls.size() > 0){
			identifier = urls.toArray()[0].toString();
			
		} else if (null != xrefId){
			identifier = xrefId;
		}
		
		List<String> contributors = new ArrayList<String>();
		if (null != authors){
			for (String author : authors){
				contributors.add(author);
			}
		}
		
		this.persistenceService.getCitationId(xrefTitle, idType, identifier, contributors);
		
		// These are the Citation properties:
		//"contributors",
		//"identifier",
		//"idType"
		// "title"
		
		// Other Xref properties can be stored as NdexPropertyValuePair objects
        //"properties", 
		
		// At the moment, no BioPAX3 properties are mapped to ndex presentation properties
        //"presentationProperties",
        
		
        
		
		// Create the citation
		/*
		Long citationId = this.persistenceService.getCitationId(
					"", 
					NdexPersistenceService.defaultCitationType,
					NdexPersistenceService.pmidPrefix + pubmedIdTokens[1], 
					null
					);
		
		// need to map the ndex element id to the bioPAX element id
			//	this.pubmedIdSet.add(pubmedIdTokens[1]);
				
	*/
			
	}

	private Long addNode(String name) throws ExecutionException, NdexException {
		TermStringType stype = TermUtilities.getTermType(name);
		if ( stype == TermStringType.NAME) {
			return persistenceService.getNodeIdByName(name);
		} 
		return persistenceService.getNodeIdByBaseTerm(name);		
	}

	private Long addEdge(String subject, String predicate, String object)
			throws ExecutionException, NdexException {
		Long subjectNodeId = addNode(subject);
		Long objectNodeId = addNode(object);
		Long predicateTermId = persistenceService.getBaseTermId(predicate);
		return persistenceService.getEdge(subjectNodeId, objectNodeId,
				predicateTermId, null,null,null);

	}
	
	private void addBioPAXNamespaces(){
		
	}

	// fragments
	/*
	 
	this.getMsgBuffer().add(e.getMessage()); 
	
	---------
	
						counter ++;
					if ( counter % 2000 == 0 ) {
						logger.info("processed " + counter + " lines so far. commit this batch.");
						this.persistenceService.commit();
					}
	 
	 -----------
	 
	 Long citationId = this.persistenceService.getCitationId(
										"", NdexPersistenceService.defaultCitationType,
										NdexPersistenceService.pmidPrefix + pubmedIdTokens[1], null);
								//	this.pubmedIdSet.add(pubmedIdTokens[1]);
									this.persistenceService.addCitationToElement(edgeId, citationId, NdexClasses.Edge);
									
	----------
	
	Long participantNodeId = this.persistenceService.getNodeIdByBaseTerm(participantIdentifier);
	
	----------
	
	if (tokens.length > 3) {
						String[] unificationAliases = tokens[3].split(";");
						this.persistenceService.addAliasToNode(participantNodeId,unificationAliases);
						if (tokens.length > 4) {
							String[] relationshipAliases = tokens[4].split(";");
							this.persistenceService.addRelatedTermToNode(participantNodeId, relationshipAliases);
						}
					}
					
	-------------
	
	                NdexPropertyValuePair p = new NdexPropertyValuePair ("URI", values[2]);
                props.add(p);
                
    -------------
    
    
    this.persistenceService.setNetworkProperties(props, null);
    
    
    -------------
    
    this.persistenceService.createNamespace2("UniProt", 	"http://identifiers.org/uniprot/");
                
	 */


}
