package org.ndexbio.xbel.service;

import java.util.concurrent.ExecutionException;

import org.ndexbio.orientdb.domain.IBaseTerm;
import org.ndexbio.orientdb.domain.ICitation;
import org.ndexbio.orientdb.domain.IEdge;
import org.ndexbio.orientdb.domain.IFunctionTerm;
import org.ndexbio.orientdb.domain.INamespace;
import org.ndexbio.orientdb.domain.INetwork;
import org.ndexbio.orientdb.domain.INetworkMembership;
import org.ndexbio.orientdb.domain.INode;
import org.ndexbio.orientdb.domain.ISupport;
import org.ndexbio.orientdb.domain.IUser;
import org.ndexbio.orientdb.persistence.NDExPersistenceService;
import org.ndexbio.orientdb.persistence.NDExPersistenceServiceFactory;
import org.ndexbio.service.JdexIdService;
import org.ndexbio.common.cache.NdexIdentifierCache;
import org.ndexbio.common.exceptions.NdexException;
import org.ndexbio.xbel.model.Citation;
import org.ndexbio.xbel.model.Function;
import org.ndexbio.xbel.model.Namespace;
import org.ndexbio.xbel.model.Parameter;
import org.ndexbio.xbel.model.Relationship;
import org.ndexbio.rest.models.*;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

/*
 * represents a class responsible for mapping XBel model objects to new Ndex domain objects
 * 
 * The primary justification for this class is to separate the use of XBel
 * model objects from identically named NDEx model objects
 */
public class XBelNetworkService {

	private static XBelNetworkService instance;

	private NDExPersistenceService persistenceService;
	private static Joiner idJoiner = Joiner.on(":").skipNulls();

	public static XBelNetworkService getInstance() {
		if (null == instance) {
			instance = new XBelNetworkService();
		}
		return instance;
	}

	private XBelNetworkService() {
		super();
		this.persistenceService = NDExPersistenceServiceFactory.INSTANCE
				.getNDExPersistenceService();
	}

	public INetwork createNewNetwork() throws Exception {
		return this.persistenceService.getCurrentNetwork();
	}

	public IUser createNewUser(String username) {
		Preconditions.checkArgument(!Strings.isNullOrEmpty(username));
		IUser user = this.persistenceService.getCurrentUser();
		user.setUsername(username);
		return user;
	}

	public INetworkMembership createNewMember() {
		return this.persistenceService.createNetworkMembership();
	}

	public SearchResult<IUser> findUsers(SearchParameters searchParameters)
			throws NdexException {
		return this.persistenceService.findUsers(searchParameters);
	}

	public void persistNewNetwork() {
		this.persistenceService.persistNetwork();
	}

	public void rollbackCurrentTransaction() {
		this.persistenceService.abortTransaction();
	}

	public IBaseTerm createIBaseTerm(Parameter p, Long jdexId)
			throws ExecutionException {
		Preconditions
				.checkArgument(null != p, "A Parameter object is required");
		Preconditions.checkArgument(null != jdexId && jdexId.longValue() > 0,
				"A valid jdex id is required");
		boolean persisted = persistenceService.isEntityPersisted(jdexId);
		final IBaseTerm bt = persistenceService.findOrCreateIBaseTerm(jdexId);
		if (persisted)
			return bt;

		bt.setName(p.getValue());
		// resolve INamespace reference for this parameter from cache

		bt.setTermNamespace(persistenceService.findNamespaceByPrefix(p.getNs()));
		bt.setJdexId(jdexId.toString());
		return bt;
	}

	/*
	 * public method to map a XBEL model namespace object to a orientdb
	 * INamespace object n.b. this method may result in a new vertex in the
	 * orientdb database being created
	 */
	public INamespace createINamespace(Namespace ns, Long jdexId)
			throws ExecutionException {
		Preconditions.checkArgument(null != ns,
				"A Namespace object is required");
		Preconditions.checkArgument(null != jdexId && jdexId.longValue() > 0,
				"A valid jdex id is required");
		INamespace newNamespace = persistenceService
				.findOrCreateINamespace(jdexId);
		newNamespace.setJdexId(jdexId.toString());
		newNamespace.setPrefix(ns.getPrefix());
		newNamespace.setUri(ns.getResourceLocation());
		return newNamespace;
	}

	/*
	 * public method to map a XBEL model Citation object to a orientdb ICitation
	 * object n.b. this method may result in a new vertex in the orientdb
	 * database being created
	 */
	public ICitation findOrCreateICitation(Citation citation)
			throws ExecutionException {
		Preconditions.checkArgument(null != citation,
				"A Citation object is required");
		String citationIdentifier = idJoiner.join("CITATION",
				citation.getName(), citation.getReference());
		Long jdexId = NdexIdentifierCache.INSTANCE.accessIdentifierCache().get(
				citationIdentifier);
		boolean persisted = persistenceService.isEntityPersisted(jdexId);
		ICitation iCitation = persistenceService.findOrCreateICitation(jdexId);
		if (persisted)
			return iCitation;
		iCitation.setJdexId(jdexId.toString());
		iCitation.setTitle(citation.getName());
		iCitation.setType(citation.getType().value());
		if (null != citation.getAuthorGroup() && null != citation.getAuthorGroup().getAuthor()) {
			iCitation.setContributors(citation.getAuthorGroup().getAuthor());
		}
		return iCitation;

	}

	/*
	 * public method to map a XBEL model evidence string in the context of a
	 * Citation to a orientdb ISupport object n.b. this method may result in a
	 * new vertex in the orientdb database being created
	 */
	public ISupport findOrCreateISupport(String evidenceString,
			ICitation iCitation) throws ExecutionException {
		Preconditions.checkArgument(null != evidenceString,
				"An evidence string is required");
		String supportIdentifier = idJoiner.join("SUPPORT",
				iCitation.getJdexId(), (String) evidenceString);
		Long jdexId = NdexIdentifierCache.INSTANCE.accessIdentifierCache().get(
				supportIdentifier);
		boolean persisted = persistenceService.isEntityPersisted(jdexId);
		ISupport iSupport = persistenceService.findOrCreateISupport(jdexId);
		if (persisted)
			return iSupport;
		iSupport.setJdexId(jdexId.toString());
		iSupport.setText(evidenceString);
		if (null != iCitation) {
			iSupport.setSupportCitation(iCitation);
		}
		return iSupport;
	}

	public void createIEdge(INode subjectNode, INode objectNode,
			IBaseTerm predicate, ISupport support, ICitation citation)
			throws ExecutionException {
		if (null != objectNode && null != subjectNode && null != predicate) {
			Long jdexId = JdexIdService.INSTANCE.getNextJdexId();
			IEdge edge = persistenceService.findOrCreateIEdge(jdexId);
			edge.setJdexId(jdexId.toString());
			edge.setSubject(subjectNode);
			edge.setPredicate(predicate);
			edge.setObject(objectNode);
			if (null != support) {
				edge.addSupport(support);
			}
			if (null != citation) {
				edge.addCitation(citation);
			}
			System.out.println("Created edge " + edge.getJdexId());
		} 
	}

	/*
	 * public method to map a XBEL model Parameter object to a orientdb
	 * IBaseTerm object n.b. this method creates a vertex in the orientdb
	 * database
	 */
	public IBaseTerm findOrCreateParameter(Parameter parameter)
			throws ExecutionException {
		if (null == parameter.getNs())
			parameter.setNs("BEL");
		String identifier = idJoiner.join("BASE", parameter.getNs(),
				parameter.getValue());
		Long jdexId = NdexIdentifierCache.INSTANCE.accessTermCache().get(
				identifier);
		return this.createIBaseTerm(parameter, jdexId);
	}

	public IBaseTerm findOrCreatePredicate(Relationship relationship)
			throws ExecutionException {
		Parameter parameter = new Parameter();
		parameter.setNs("BEL");
		parameter.setValue(relationship.name());
		String identifier = idJoiner.join("BASE", parameter.getNs(),
				parameter.getValue());
		Long jdexId = NdexIdentifierCache.INSTANCE.accessTermCache().get(
				identifier);
		return this.createIBaseTerm(parameter, jdexId);
	}

	public IBaseTerm findOrCreateFunction(Function function)
			throws ExecutionException {
		Parameter parameter = new Parameter();
		parameter.setNs("BEL");
		parameter.setValue(function.name());
		String identifier = idJoiner.join("BASE", parameter.getNs(),
				parameter.getValue());
		Long jdexId = NdexIdentifierCache.INSTANCE.accessTermCache().get(
				identifier);
		return this.createIBaseTerm(parameter, jdexId);
	}

	public INode findOrCreateINodeForIFunctionTerm(IFunctionTerm representedTerm)
			throws ExecutionException {
		String nodeIdentifier = idJoiner.join("NODE",
				representedTerm.getJdexId());
		Long jdexId = NdexIdentifierCache.INSTANCE.accessIdentifierCache().get(
				nodeIdentifier);
		boolean persisted = persistenceService.isEntityPersisted(jdexId);
		INode iNode = persistenceService.findOrCreateINode(jdexId);
		if (persisted)
			return iNode;
		iNode.setJdexId(jdexId.toString());
		iNode.setRepresents(representedTerm);
		return iNode;
	}

}
