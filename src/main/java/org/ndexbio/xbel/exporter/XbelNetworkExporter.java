package org.ndexbio.xbel.exporter;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.TreeMap;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.namespace.QName;
import javax.xml.stream.FactoryConfigurationError;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.ndexbio.common.models.object.BaseTerm;
import org.ndexbio.common.models.object.Edge;
import org.ndexbio.common.models.object.FunctionTerm;
import org.ndexbio.common.models.object.NdexDataModelService;
import org.ndexbio.common.models.object.Network;
import org.ndexbio.common.models.object.Namespace;
import org.ndexbio.common.models.object.ReifiedEdgeTerm;
import org.ndexbio.common.models.object.Support;
import org.ndexbio.common.models.object.Term;
import org.ndexbio.common.models.object.Node;
import org.ndexbio.task.audit.NdexAuditService;
import org.ndexbio.task.audit.NdexAuditServiceFactory;
import org.ndexbio.task.audit.NdexAuditUtils;
import org.ndexbio.task.audit.network.NdexObjectAuditor;
import org.ndexbio.task.service.NdexTaskModelService;
import org.ndexbio.xbel.model.AnnotationDefinitionGroup;
import org.ndexbio.xbel.model.AnnotationGroup;
import org.ndexbio.xbel.model.Annotation;
import org.ndexbio.xbel.model.AuthorGroup;
import org.ndexbio.xbel.model.Citation;
import org.ndexbio.xbel.model.Document;
import org.ndexbio.xbel.model.ExternalAnnotationDefinition;
import org.ndexbio.xbel.model.Header;
import org.ndexbio.xbel.model.InternalAnnotationDefinition;
import org.ndexbio.xbel.model.LicenseGroup;
import org.ndexbio.xbel.model.NamespaceGroup;
import org.ndexbio.xbel.model.ObjectFactory;
import org.ndexbio.xbel.model.Relationship;
import org.ndexbio.xbel.model.Statement;
import org.ndexbio.xbel.model.StatementGroup;
import org.ndexbio.xbel.model.Subject;
import org.ndexbio.xbel.model.Function;
import org.ndexbio.xbel.model.Parameter;
import org.ndexbio.xbel.model.CitationType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.sun.xml.bind.marshaller.NamespacePrefixMapper;

public class XbelNetworkExporter {
	private final NdexTaskModelService modelService;
	private final String networkId;
	private final String userId;
	private final Network network;
	private Network subNetwork;
	private XbelStack<StatementGroup> sgStack;
	private XbelStack<Statement> stmtStack;
	private XbelStack<org.ndexbio.xbel.model.Term> xbelTermStack;
	
	private final String version = "1,2";
	private final String copyright = "Copyright (c) 2011, Selventa. All Rights Reserved.";
	private final String contactInfo = "support@belframework.org";
	private final String author = "Selventa";
	private final String license = "Creative Commons Attribution-Non-Commercial-ShareAlike 3.0 Unported License";
	private XbelMarshaller xm;
	private final ObjectFactory xbelFactory = new ObjectFactory();
	// incorporate operation auditing
	private NdexAuditService auditService;
	private final NdexAuditUtils.AuditOperation operation = NdexAuditUtils.AuditOperation.NETWORK_EXPORT;
	private final NdexObjectAuditor<Edge> edgeAuditor = new NdexObjectAuditor<Edge>(
			Edge.class);
	private final NdexObjectAuditor<Node> nodeAuditor = new NdexObjectAuditor<Node>(
			Node.class);
	private final NdexObjectAuditor<Term> termAuditor = new NdexObjectAuditor<Term>(
			Term.class);
	private final NdexObjectAuditor<Support> supportAuditor = new NdexObjectAuditor<Support>(
			Support.class);

	private static final Logger logger = LoggerFactory
			.getLogger(XbelNetworkExporter.class);

	public XbelNetworkExporter(String userId, String networkId, NdexTaskModelService service,
			String exportFilename) {
		Preconditions.checkArgument(!Strings.isNullOrEmpty(userId),
				"A userId id is required");
		Preconditions.checkArgument(!Strings.isNullOrEmpty(networkId),
				"A network id is required");
		Preconditions.checkArgument(null != service,
				"A NdexDataModelService object is required");
		Preconditions.checkArgument(!Strings.isNullOrEmpty(exportFilename),"A filename is required");
		this.userId = userId;
		this.networkId = networkId;
		this.modelService = service;
		xm = new XbelMarshaller(exportFilename);
		this.network = this.modelService.getNetworkById(this.userId, this.networkId);
		this.sgStack = new XbelStack<StatementGroup>("StatementGroup",
				Boolean.FALSE);
		this.stmtStack = new XbelStack<Statement>("Statement", Boolean.FALSE);
		this.xbelTermStack = new XbelStack<org.ndexbio.xbel.model.Term>(
				"XBEL Term", Boolean.FALSE);
		
		this.initiateAuditService(network.getName());
		System.out.println("XBEL network id " +this.networkId 
				+" will be exported to " +exportFilename
				+" for user id " +this.userId
				);

	}
	
	

	private void initiateAuditService(String networkName) {
		Optional<NdexAuditService> optService = NdexAuditServiceFactory.INSTANCE
				.getAuditServiceByOperation(networkName, this.operation);
		if (optService.isPresent()) {
			this.auditService = optService.get();
			logger.info("NdexAuditServiceFactory returned an instance of "
					+ this.auditService.getClass().getName());
		} else {
			logger.error("NdexAuditServiceFactory failed to return a NdexAuditService subclass");
		}
	}

	private void setAuditExpectedMetrics(Network subNetwork) {
		// edge count

		this.auditService.increaseExpectedMetricValue("edge count",
				(long) subNetwork.getEdgeCount());

		for (Map.Entry<String, Term> entry : subNetwork.getTerms().entrySet()) {
			if (entry.getValue() instanceof FunctionTerm) {
				this.auditService
						.incrementExpectedMetricValue("function term count");
			} else if (entry.getValue() instanceof BaseTerm) {
				this.auditService
						.incrementExpectedMetricValue("base term count");
			} else if (entry.getValue() instanceof ReifiedEdgeTerm) {
				this.auditService
						.incrementExpectedMetricValue("reified edge count");
			} else {
				logger.error("Unknown term class "
						+ entry.getValue().getClass().getName());
			}
		}
		this.auditService.increaseExpectedMetricValue("citation count",
				(long) subNetwork.getCitations().size());
		this.auditService.increaseExpectedMetricValue("node count",
				(long) subNetwork.getNodeCount());
		this.auditService.increaseExpectedMetricValue("support count",
				(long) subNetwork.getSupports().size());
		logger.info("Expected values set in Audit Service");

	}

	/*
	 * public method to initiate export of the specified network uses an
	 * instance of an inner class to control marshalling of JAXB objects to an
	 * XML file
	 */

	public void exportNetwork() {

		
		xm.open();

		// export the header
		this.createHeader();
		// export the namespaces
		Iterable<Namespace> namespaces = this.modelService
				.getNamespacesByNetworkId(networkId);
		this.addNamespaceGroup(namespaces);
		// process the annotation definition group
				this.processAnnotationDefinitionGroup();
		/*
		 * process the network in segments to manage memory requirements each
		 * citation within the network is treated as a subnetwork and represnts
		 * an outer level statement group
		 */
		this.processCitationSubnetworks();
		
		// output the observed metrics
		this.auditService.registerComment(this.edgeAuditor
				.displayUnprocessedNdexObjects());
		this.auditService.registerComment(this.nodeAuditor
				.displayUnprocessedNdexObjects());
		this.auditService.registerComment(this.termAuditor
				.displayUnprocessedNdexObjects());
		this.auditService.registerComment(this.supportAuditor
				.displayUnprocessedNdexObjects());
		System.out.println(this.auditService.displayObservedValues());
		System.out.println(this.auditService.displayExpectedValues());
		System.out.println(this.auditService.displayDeltaValues());
		// close the XML document
		xm.close();
	}
	
	/*
	 * private method to map internal and external annotations to the document's
	 * AnnotationDefinitionGroup
	 * The entity is marshalled to to document by a dedicated method
	 */
	private void processAnnotationDefinitionGroup() {
		AnnotationDefinitionGroup adg = this.xbelFactory.createAnnotationDefinitionGroup();
		
		processInternalAnnotations(adg);
		processExternalAnnotations(adg);
		try {
			xm.writeAnnotationDefinitionGroup(adg);
		} catch (JAXBException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/*
	 * 		private method to add external annotation definitions to the annotation definition group
	 */
	private void processExternalAnnotations(AnnotationDefinitionGroup adg) {
		for (Namespace ns : this.modelService.getExternalAnnotationsByNetworkId(networkId)){
			ExternalAnnotationDefinition ead = this.xbelFactory.createExternalAnnotationDefinition();
			ead.setId(ns.getPrefix());
			ead.setUrl(ns.getUri());
			adg.getExternalAnnotationDefinition().add(ead);
		}
	}

	/*
	 * 		private method to add internal annotation definitions to the annotation definition group
	 */
	private void processInternalAnnotations(AnnotationDefinitionGroup adg) {
		for (Namespace ns : this.modelService.getInternalAnnotationsByNetworkId(networkId)){
			InternalAnnotationDefinition iad = this.xbelFactory.createInternalAnnotationDefinition();
			adg.getInternalAnnotationDefinition().add(iad);
			iad.setId(ns.getPrefix());
			iad.setDescription(ns.getMetadata().get("description"));
			iad.setUsage(ns.getMetadata().get("description"));
			iad.setListAnnotation(this.xbelFactory.createListAnnotation());
			for (BaseTerm bt : this.modelService.getBaseTermsByNamespace(this.userId, ns.getPrefix(), this.networkId)){
				iad.getListAnnotation().getListValue().add(bt.getName());
			}
		}
	}

	/*
	 * Each citation within the network is used as a marker for a new
	 * outer-level statement group. A subnetwork is obtained from the database
	 * for the NDEx objects that belong to that Citation
	 */
	private void processCitationSubnetworks() {
		List<org.ndexbio.common.models.object.Citation> modelCitations = this
				.getCitationsByNetworkId();
		for (org.ndexbio.common.models.object.Citation citation : modelCitations) {
			this.subNetwork = this.modelService.getSubnetworkByCitationId(this.userId,
					this.networkId, citation.getId());
			if (null == subNetwork) {
				continue;
			}
			System.out.println(" Citation " + citation.getTitle()
					+ " has a subnetwork with  " + subNetwork.getEdgeCount()
					+ " edges and " + subNetwork.getCitations().size()
					+ " citations");
			// register expected values in audit service
			this.setAuditExpectedMetrics(subNetwork);
			// add the edges to an audited Set
			this.edgeAuditor.registerJdexIds(subNetwork.getEdges());
			this.nodeAuditor.registerJdexIds(subNetwork.getNodes());
			this.termAuditor.registerJdexIds(subNetwork.getTerms());
			this.supportAuditor.registerJdexIds(subNetwork.getSupports());
			this.processCitationStatementGroup();
			this.processCitationSupports(citation);
			try {
				xm.writeStatementGroup(sgStack.pop());

			} catch (JAXBException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		}
	}

	/*
	 * Initiate a new outer level statement group and create the outer-level
	 * annotation group to hold the citation data
	 */

	private void processCitationStatementGroup() {
		// clear the statement group stack
		this.sgStack.clear();
		StatementGroup sg = new StatementGroup();
		AnnotationGroup ag = new AnnotationGroup();
		sg.setName(this.createXbelCitation(ag));
		sg.setAnnotationGroup(ag);
		sgStack.push(sg);
		// increment the audit citation count
		this.auditService.incrementObservedMetricValue("citation count");

	}

	/*
	 * Process the supports for a given citation Each support represents an
	 * inner level statement group and contains a collection of edges
	 */
	private void processCitationSupports(
			org.ndexbio.common.models.object.Citation modelCitation) {
		for (String supportId : modelCitation.getSupports()) {

			Support support = this.subNetwork.getSupports().get(supportId);
			if (null != support) {
				StatementGroup supportStatementGroup = new StatementGroup();
				AnnotationGroup ag = new AnnotationGroup();
				String evidence = Objects.firstNonNull(support.getText(), " ");
				ag.getAnnotationOrEvidenceOrCitation().add(evidence);
				// increment audit support count
				this.auditService.incrementObservedMetricValue("support count");
				// add support annotations
				this.processSupportAnnotations(ag, support);
				supportStatementGroup.setAnnotationGroup(ag);
				sgStack.peek().getStatementGroup().add(supportStatementGroup);
				// increment audit support count
				this.auditService.incrementObservedMetricValue("support count");
				this.supportAuditor.removeProcessedNdexObject(support);

				this.processSupportStatementGroup(supportId);
			} else {
				System.out.println("Support id " + supportId
						+ " not found in newtork map");
			}

		}

	}

	private void processSupportAnnotations(AnnotationGroup ag, Support support) {
		if (null == support.getMetadata() || support.getMetadata().isEmpty()) {
			return;
		}
		for (Map.Entry<String, String> entry : support.getMetadata().entrySet()) {
			Annotation annotation = new Annotation();
			annotation.setRefID(entry.getKey());
			annotation.setValue(entry.getValue());
			System.out.println("Support Annotation " + entry.getValue());
			ag.getAnnotationOrEvidenceOrCitation().add(annotation);
		}
	}

	/*
	 * The collection of edges which reference the same support (i.e. evidence)
	 * represent an inner level statement group wrt to the outer level citation
	 * statement group
	 */
	private void processSupportStatementGroup(String supportId) {

		for (Map.Entry<String, Edge> entry : this.subNetwork.getEdges()
				.entrySet()) {
			Edge edge = entry.getValue();
			if (edge.getSupports().contains(supportId)) {
				// we've identified an Edge that belongs to this support
				this.processSupportEdge(entry.getKey(), edge);
				this.edgeAuditor.removeProcessedNdexObject(edge);
			}
		}

	}

	/*
	 * An NDEx Edge object is equivalent to an XBEL Statement object we need to
	 * construct a new Statement, and complete its Subject, Predicate, and
	 * Object properties by transversing the Terms associated with the Edge
	 * Since we are starting processing for a new Support we can clear the
	 * Statement stack
	 */
	private void processSupportEdge(String edgeId, Edge edge) {
		// we're at the outer level so clear the Statement stack
		this.stmtStack.clear();
		Statement stmt = new Statement();
		this.sgStack.peek().getStatement().add(stmt);
		
		this.processStatement(stmt, edge);
	}

	private void processStatement(Statement stmt, Edge edge) {

		// increment the audit edge count
		this.auditService.incrementObservedMetricValue("edge count");
		// put this new Statement into the Statement stack
		this.stmtStack.push(stmt);
		// process statement annotations
	
		this.processStatementAnnotations(edge);
		
		if (!Strings.isNullOrEmpty(edge.getP())) {
			this.processTermPredicate(edge.getP());
		} else {
			System.out.println("Edge " + edge.getId()					
					+ " does not have a predicate");
		}
		if (!Strings.isNullOrEmpty(edge.getS())) {
			this.processTermSubject(edge.getS());
		} else {
			System.out.println("Edge " + edge.getId()					
					+ " does not have a subject");
		}
		if (!Strings.isNullOrEmpty(edge.getO())) {
			this.processTermObject(edge.getO());
		} else {
			System.out.println("Edge " + edge.getId()
					+ " does not have a object");
		}
		// we're done with the current Statement remove it from the stack
		this.stmtStack.pop();
	}

	/*
	 * private method to map NDEx Edge metadata to an XBEL AnnotationGroup
	 */
	private void processStatementAnnotations(Edge edge) {
		if (null == edge.getMetadata() || edge.getMetadata().isEmpty()) {
			return;
		}
		AnnotationGroup ag = new AnnotationGroup();
		this.stmtStack.peek().setAnnotationGroup(ag);
		for (Map.Entry<String, String> entry : edge.getMetadata().entrySet()) {
			String refid = entry.getKey();
			String value = entry.getValue();
			Annotation annotation = new Annotation();
			annotation.setRefID(refid);
			annotation.setValue(value);
			ag.getAnnotationOrEvidenceOrCitation().add(annotation);

		}
	}

	/*
	 * An NDEx Edge subject maps to an XBEL subject element with 1 or more
	 * terms. The terms may have nested terms as well as parameters. The input
	 * parameter is the JdexId for the top level object for an edge
	 */
	private void processTermSubject(String edgeSubjectId) {
		Node node = this.subNetwork.getNodes().get(edgeSubjectId);
		Subject subject = new Subject();
		org.ndexbio.xbel.model.Term xbelTerm = new org.ndexbio.xbel.model.Term();
		if (null != node && !Strings.isNullOrEmpty(node.getRepresents())) {
			// increment audit node count
			this.auditService.incrementObservedMetricValue("node count");
			// get initial function term
			Term term = this.subNetwork.getTerms().get(node.getRepresents());
			if (null != term && term instanceof FunctionTerm) {
				// clear the term statck
				this.xbelTermStack.clear();
				subject.setTerm(this.processFunctionTerm((FunctionTerm) term));
			}
			this.stmtStack.peek().setSubject(subject);
			this.nodeAuditor.removeProcessedNdexObject(node);
		}
	}

	/*
	 * An NDEx Edge maps to an XBEL object element with 1 or more terms. These
	 * terms may have nested terms as well as parameters The input parameter is
	 * the JdexId for the top level object for an edge
	 * 
	 * mod 14Mar2014 - add support for inner statements: ReifiedEdgeTerm
	 */
	private void processTermObject(String edgeObjectId) {
		Node node = this.subNetwork.getNodes().get(edgeObjectId);
		org.ndexbio.xbel.model.Object object = new org.ndexbio.xbel.model.Object();
		org.ndexbio.xbel.model.Term xbelTerm = new org.ndexbio.xbel.model.Term();
		if (null != node && !Strings.isNullOrEmpty(node.getRepresents())) {
			// get initial function term
			Term term = this.subNetwork.getTerms().get(node.getRepresents());
			if ( null != term && term instanceof ReifiedEdgeTerm){
				// this represents an inner statement - add to Object
				ReifiedEdgeTerm rt = (ReifiedEdgeTerm) term;
				Edge innerEdge = this.subNetwork.getEdges().
						get(rt.getTermEdge());
				if(null != innerEdge){
					Statement stmt = new Statement();
					object.setStatement(stmt);
					this.processStatement(stmt, innerEdge);
				} else {
					System.out.println("ReifiedEdgeTerm edge " +rt.getTermEdge()
							+" not found in subnetwork edges");
				}
			}
			else if (null != term && term instanceof FunctionTerm) {
				// clear the term statck
				this.xbelTermStack.clear();
				object.setTerm(this.processFunctionTerm((FunctionTerm) term));
			}
			this.stmtStack.peek().setObject(object);
			this.nodeAuditor.removeProcessedNdexObject(node);
		}
	}

	/*
	 * private method to map a hierarchy of NDEx function term model objects to
	 * an equivalent hierarchy of XBEL term objects this method can be invoked
	 * recursively
	 */
	private org.ndexbio.xbel.model.Term processFunctionTerm(FunctionTerm ft) {
		org.ndexbio.xbel.model.Term xbelTerm = new org.ndexbio.xbel.model.Term();

		// push new term onto the stack as the current term being constructed
		this.xbelTermStack.push(xbelTerm);
		// set the function attribute for the current term
		BaseTerm bt = (BaseTerm) this.subNetwork.getTerms().get(
				ft.getTermFunction());
		this.xbelTermStack.peek().setFunction(Function.valueOf(bt.getName()));
		// increment audit base term count
		this.auditService.incrementObservedMetricValue("base term count");
		// remove base term from list of unprocess terms
		this.termAuditor.removeProcessedNdexObject(bt);
		// now process any parameters (either base terms or inner function
		// terms)
		// the function terms parameters map to XBEL parameter elements
		//
		
		// the parameter map key now represents an ordering value
		Map<String,String> parameterTreeMap = new TreeMap<String,String>(ft.getParameters());
		// process map sorted by keys
		for (Map.Entry<String, String> entry : parameterTreeMap.entrySet()) {
			Term parameter = this.subNetwork.getTerms().get(entry.getValue());
			if (parameter instanceof FunctionTerm) {
				// register the generated XBEL term in the hierarchy
				this.xbelTermStack
						.peek()
						.getParameterOrTerm()
						.add(this.processFunctionTerm((FunctionTerm) parameter));
				// increment audit function term count
				this.auditService
						.incrementObservedMetricValue("function term count");
				// remove function term from unprocessed Term collection
				this.termAuditor.removeProcessedNdexObject(ft);
			} else if (parameter instanceof BaseTerm) {
				BaseTerm parameterBt = (BaseTerm) parameter;
				Namespace ns = this.subNetwork.getNamespaces().get(
						parameterBt.getNamespace());
				Parameter xbelParameter = new Parameter();
				/*
				 * don't export the parameter namespace if it is BEL
				 */
				if (!Strings.isNullOrEmpty(ns.getId()) && !ns.getPrefix().equals("BEL")) {
					// this.xbelTermStack.peek().getParameterOrTerm().add(xbelParameter);
					xbelParameter.setNs(ns.getPrefix());
				}
				xbelParameter.setValue(parameterBt.getName());
				this.xbelTermStack.peek().getParameterOrTerm()
				.add(xbelParameter);
				this.auditService.incrementObservedMetricValue("base term count");
			}

		} // end of parameter processing
			
		return this.xbelTermStack.pop();
	}

	/*
	 * An NDEx Edge predicate maps to an XBEL Statement relationship attribute
	 */
	private void processTermPredicate(String jdexId) {
		Term term = this.subNetwork.getTerms().get(jdexId);
		if (null != term && term instanceof BaseTerm) {
			BaseTerm bt = (BaseTerm) term;
			// add the base term to the current Statement as a relationship
			// attribute
			this.stmtStack.peek().setRelationship(
					Relationship.valueOf(bt.getName()));
			// increment the audit base term count
			this.auditService.incrementObservedMetricValue("base term count");
		}

	}

	private String createXbelCitation(AnnotationGroup annotGroup) {
		Citation xbelCitation = new Citation();
		Map<String, org.ndexbio.common.models.object.Citation> networkCitations = this.subNetwork
				.getCitations();
		if (networkCitations.size() == 1) {

			for (Map.Entry<String, org.ndexbio.common.models.object.Citation> entry : networkCitations
					.entrySet()) {
				org.ndexbio.common.models.object.Citation modelCitation = entry
						.getValue();
				xbelCitation.setName(modelCitation.getType());
				xbelCitation.setReference(modelCitation.getIdentifier());
				xbelCitation.setName(modelCitation.getTitle());

				xbelCitation.setType(CitationType.fromValue(modelCitation
						.getType()));
				if (null != modelCitation.getContributors()) {
					org.ndexbio.xbel.model.Citation.AuthorGroup authors = new org.ndexbio.xbel.model.Citation.AuthorGroup();
					for (String contributor : modelCitation.getContributors()) {
						authors.getAuthor().add(contributor);
					}
					xbelCitation.setAuthorGroup(authors);
				}

			}
			annotGroup.getAnnotationOrEvidenceOrCitation().add(xbelCitation);

			return xbelCitation.getType() + " " + xbelCitation.getReference();
		}

		return " ";
	}

	/*
	 * Create the bel document header section
	 */
	private void createHeader() {
		// Document document = new Document();
		Header header = new Header();
		header.setName(this.network.getName());
		String description = Objects.firstNonNull(
				this.network.getDescription(), "XBEL network");
		header.setDescription(description);
		header.setVersion(version);
		header.setCopyright(copyright);
		header.setContactInfo(contactInfo);
		AuthorGroup ag = new AuthorGroup();
		ag.getAuthor().add(author);
		header.setAuthorGroup(ag);
		LicenseGroup lg = new LicenseGroup();
		lg.getLicense().add(license);
		header.setLicenseGroup(lg);
		// document.setHeader(header);
		try {
			xm.writeHeader(header);
		} catch (JAXBException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			header = null;
		}

	}
	
	
	

	private void addNamespaceGroup(Iterable<Namespace> namespaces) {
		NamespaceGroup nsg = new NamespaceGroup();
		for (Namespace modelNamespace : namespaces) {
			org.ndexbio.xbel.model.Namespace xbelNamespace = this.xbelFactory.createNamespace();
			xbelNamespace.setPrefix(modelNamespace.getPrefix());
			xbelNamespace.setResourceLocation(modelNamespace.getUri());
			if (Strings.isNullOrEmpty(modelNamespace.getUri())) {
				System.out.println("++++ empty namespace uri prefix = "
						+ modelNamespace.getPrefix());
			}
			//System.out.println("Namespace: "+modelNamespace.getPrefix() +" " +modelNamespace.getUri());
			nsg.getNamespace().add(xbelNamespace);
		}
		try {
			xm.writeNamespaceGroup(nsg);
		} catch (JAXBException e) {
			logger.error(e.getMessage());
			e.printStackTrace();
		} finally {
			nsg = null;
		}

	}

	private List<org.ndexbio.common.models.object.Citation> getCitationsByNetworkId() {
		List<org.ndexbio.common.models.object.Citation> modelCitations = this.modelService
						.getCitationsByNetworkId(networkId);
		System.out.println("Network " + networkId + " has "
				+ modelCitations.size() + " citations");
		return modelCitations;

	}

	/*
	 * An inner class to keep track of processed edges
	 */

	/*
	 * An inner class responsible marshalling (i.e. outputting) an JAXB object
	 * graph to an XML document
	 */

	public class XbelMarshaller {

		private final String exportedFilename;
		
		private JAXBContext context;
		private XMLStreamWriter writer;
		private Class type;
		private Marshaller marshaller;

		public XbelMarshaller(String nn) {
			Preconditions.checkArgument(!Strings.isNullOrEmpty(nn),
					"A export file name is required");
			this.exportedFilename = nn;

			try {
				
				this.context = JAXBContext.newInstance(Document.class);
				this.marshaller = context.createMarshaller();
				this.marshaller.setProperty(Marshaller.JAXB_FRAGMENT,
						Boolean.TRUE);
				this.marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT,
						Boolean.TRUE);
				NamespacePrefixMapper npm = new XbelPrefixMapper();
				this.marshaller.setProperty(
						"com.sun.xml.bind.namespacePrefixMapper", npm);

			} catch (JAXBException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		public void open() {
			try {
				XMLOutputFactory xmlFactory = XMLOutputFactory.newFactory();
				xmlFactory.setProperty(
						"javax.xml.stream.isRepairingNamespaces", Boolean.TRUE);
				
				this.writer = xmlFactory
						.createXMLStreamWriter(new FileOutputStream(
								this.exportedFilename));
				 this.writer.setDefaultNamespace("http://belframework.org/schema/1.0/xbel");
				this.writer.setPrefix("bel",
						"http://belframework.org/schema/1.0/xbel");
				this.writer.writeStartDocument("UTF-8", "1.0");
				this.writer.writeStartElement("bel:document");
				this.writer.writeNamespace("xsi",
						"http://www.w3.org/2001/XMLSchema-instance");
				this.writer
						.writeAttribute(
								"http://www.w3.org/2001/XMLSchema-instance",
								"schemaLocation",
								"http://belframework.org/schema/1.0/xbel http://resource.belframework.org/belframework/1.0/schema/xbel.xsd");

			} catch (FileNotFoundException | XMLStreamException
					| FactoryConfigurationError e) {

				e.printStackTrace();
			}
		}

		public void writeDocument(Document d) throws JAXBException {
			JAXBElement<Document> element = new JAXBElement<Document>(
					QName.valueOf("bel:document"),
					(Class<Document>) d.getClass(), d);
			this.marshaller.marshal(element, this.writer);
		}

		public void writeHeader(Header h) throws JAXBException {
			JAXBElement<Header> element = new JAXBElement<Header>(
					QName.valueOf("bel:header"), (Class<Header>) h.getClass(),
					h);

			this.marshaller.marshal(element, this.writer);
		}

		public void writeNamespaceGroup(NamespaceGroup nsg)
				throws JAXBException {
			JAXBElement<NamespaceGroup> element = new JAXBElement<NamespaceGroup>(
					QName.valueOf("bel:namespaceGroup"),
					(Class<NamespaceGroup>) nsg.getClass(), nsg);
			this.marshaller.marshal(element, this.writer);
		}
		
		public void writeAnnotationDefinitionGroup(AnnotationDefinitionGroup adg) throws JAXBException{
			JAXBElement<AnnotationDefinitionGroup> element = new JAXBElement<AnnotationDefinitionGroup>(
					QName.valueOf("bel:annotationDefinitionGroup"),
					(Class<AnnotationDefinitionGroup>) adg.getClass(), adg);
			this.marshaller.marshal(element, this.writer);
		}

		public void writeStatementGroup(StatementGroup sg) throws JAXBException {
			JAXBElement<StatementGroup> element = new JAXBElement<StatementGroup>(
					QName.valueOf("bel:statementGroup"),
					(Class<StatementGroup>) sg.getClass(), sg);
			this.marshaller.marshal(element, this.writer);
		}

		public void close() {
			try {
				this.writer.writeEndElement();
				this.writer.writeEndDocument();
			} catch (XMLStreamException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} finally {
				try {
					this.writer.close();
				} catch (XMLStreamException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}

	}

	public class XbelPrefixMapper extends NamespacePrefixMapper {
		private static final String BEL_NS = "bel";
		private static final String BEL_URI = "http://belframework.org/schema/1.0/xbel";
		private static final String XSI_NS = "xsi";
		private static final String XSI_URI = "http://www.w3.org/2001/XMLSchema-instance";

		@Override
		public String getPreferredPrefix(String nsUri, String suggestion,
				boolean requiredPrefix) {
			if (nsUri.equals(XSI_URI)) {
				return XSI_NS;
			}

			return BEL_NS;

		}

	}

	/*
	 * an inner class that encapsulates a regular stack supPorts a VERBOSE
	 * option that logs stack operations
	 */

	public class XbelStack<T> {
		private final Stack<T> stack;
		private final String stackName;
		private Boolean VERBOSE;

		public XbelStack(String aName) {
			this.stackName = Objects.firstNonNull(aName, "unspecified");
			this.stack = new Stack<T>();
			this.VERBOSE = false;
		}

		public XbelStack(String aName, Boolean verbose) {
			this(aName);
			this.VERBOSE = verbose;
		}

		T pop() {

			try {
				T obj = this.stack.pop();
				if (this.VERBOSE) {
					System.out.println("The current " + this.stackName
							+ " stack level is " + this.stack.size());
				}
				return obj;
			} catch (java.util.EmptyStackException e) {
				System.out.println("ERROR pop " + this.stackName
						+ " stack is empty");
				e.printStackTrace();
			}
			return null;

		}

		T peek() {
			try {
				return this.stack.peek();
			} catch (java.util.EmptyStackException e) {
				System.out.println("ERROR peek " + this.stackName
						+ " stack is empty");
				e.printStackTrace();
			}
			return null;
		}

		void push(T obj) {
			Preconditions.checkArgument(null != obj,
					"invalid push operation with null object");
			this.stack.push(obj);
			if (this.VERBOSE) {
				System.out.println("The current " + this.stackName
						+ " stack level is " + this.stack.size());
			}

		}

		boolean empty() {
			return this.stack.empty();
		}

		void clear() {
			this.stack.clear();
			if (this.VERBOSE) {
				System.out.println("The  " + this.stackName
						+ " stack has been cleared ");
			}
		}
	} // end of XbelStack inner class
}