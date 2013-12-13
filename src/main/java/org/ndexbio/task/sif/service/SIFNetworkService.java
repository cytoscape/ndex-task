package org.ndexbio.task.sif.service;

import java.util.concurrent.ExecutionException;

import org.ndexbio.common.cache.NdexIdentifierCache;
import org.ndexbio.common.exceptions.NdexException;
import org.ndexbio.orientdb.domain.IBaseTerm;
import org.ndexbio.orientdb.domain.IEdge;
import org.ndexbio.orientdb.domain.INamespace;
import org.ndexbio.orientdb.domain.INetwork;
import org.ndexbio.orientdb.domain.INetworkMembership;
import org.ndexbio.orientdb.domain.INode;
import org.ndexbio.orientdb.domain.IUser;
import org.ndexbio.orientdb.persistence.NDExPersistenceService;
import org.ndexbio.orientdb.persistence.NDExPersistenceServiceFactory;
import org.ndexbio.rest.models.SearchParameters;
import org.ndexbio.rest.models.SearchResult;
import org.ndexbio.service.JdexIdService;
import org.ndexbio.xbel.model.*;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

public class SIFNetworkService {

	private static SIFNetworkService instance;

	private NDExPersistenceService persistenceService;
	private static Joiner idJoiner = Joiner.on(":").skipNulls();

	public static SIFNetworkService getInstance() {
		if (null == instance) {
			instance = new SIFNetworkService();
		}
		return instance;
	}

	private SIFNetworkService() {
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

	public IBaseTerm findOrCreateIBaseTerm(String name, Long jdexId)
			throws ExecutionException {
		Preconditions.checkArgument(null != name, "A name is required");
		boolean persisted = persistenceService.isEntityPersisted(jdexId);
		final IBaseTerm bt = persistenceService.findOrCreateIBaseTerm(jdexId);
		if (persisted) return bt;
		bt.setName(name);
		bt.setJdexId(jdexId.toString());
		this.persistenceService.getCurrentNetwork().addTerm(bt);
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
		this.persistenceService.getCurrentNetwork().addNamespace(newNamespace);
		return newNamespace;
	}


	public INode findOrCreateINode(IBaseTerm baseTerm)
			throws ExecutionException {
		Preconditions.checkArgument(null != baseTerm,
				"A IBaseTerm object is required");
		String nodeIdentifier = idJoiner.join("NODE", baseTerm.getName());
		Long jdexId = NdexIdentifierCache.INSTANCE.accessIdentifierCache().get(
				nodeIdentifier);
		boolean persisted = persistenceService.isEntityPersisted(jdexId);
		INode iNode = persistenceService.findOrCreateINode(jdexId);
		if (persisted) return iNode;
		iNode.setJdexId(jdexId.toString());
		iNode.setRepresents(baseTerm);
		this.persistenceService.getCurrentNetwork().addNdexNode(iNode);
		return iNode;

	}

	public void createIEdge(INode subjectNode, INode objectNode,
			IBaseTerm predicate)
			throws ExecutionException {
		if (null != objectNode && null != subjectNode && null != predicate) {
			Long jdexId = JdexIdService.INSTANCE.getNextJdexId();
			IEdge edge = persistenceService.findOrCreateIEdge(jdexId);
			edge.setJdexId(jdexId.toString());
			edge.setSubject(subjectNode);
			edge.setPredicate(predicate);
			edge.setObject(objectNode);
			this.persistenceService.getCurrentNetwork().addNdexEdge(edge);
			System.out.println("Created edge " + edge.getJdexId());
		} 
	}

	public IBaseTerm findOrCreateNodeBaseTerm(String name)
			throws ExecutionException {
		String identifier = idJoiner.join("BASE", name);
		Long jdexId = NdexIdentifierCache.INSTANCE.accessTermCache().get(
				identifier);
		return this.findOrCreateIBaseTerm(name, jdexId);
	}

	public IBaseTerm findOrCreatePredicate(String name)
			throws ExecutionException {
		String identifier = idJoiner.join("PREDICATE", name);
		Long jdexId = NdexIdentifierCache.INSTANCE.accessTermCache().get(
				identifier);
		return this.findOrCreateIBaseTerm(name, jdexId);
	}



}
