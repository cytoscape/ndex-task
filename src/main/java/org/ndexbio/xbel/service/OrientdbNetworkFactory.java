package org.ndexbio.xbel.service;

import java.util.ArrayList;
import java.util.List;
import org.ndexbio.common.exceptions.NdexException;
import org.ndexbio.common.models.data.INetwork;
import org.ndexbio.common.models.data.INetworkMembership;
import org.ndexbio.common.models.data.IUser;
import org.ndexbio.common.models.data.Permissions;
import org.ndexbio.common.models.object.*;
import org.ndexbio.xbel.service.XBelNetworkService;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

/*
 * A Singleton to provide a instance of a test network with fixed metadata
 * 
 * FOR TESTING PURPOSES ONLY
 */
public enum OrientdbNetworkFactory {
	INSTANCE;
	private String testUserName = "jstegall";
	
	public INetwork createTestNetwork(String title) throws Exception {
		 final INetwork testNetwork = XBelNetworkService.getInstance().createNewNetwork();
		
		 List<Membership> membershipList = new ArrayList<Membership>();
			Membership membership = new Membership();
			IUser testUser = this.resolveUserUserByUsername(testUserName);
			INetworkMembership newMember = XBelNetworkService.getInstance().createNewMember();
			//membership.setResourceId();
			membership.setResourceName(testUser.getUsername());
			membership.setPermissions(Permissions.ADMIN);
			membershipList.add(membership);
			testNetwork.addMember(newMember);
			testNetwork.setTitle(title);
			// commit new network
			XBelNetworkService.getInstance().commitCurrentNetwork();
		 return  testNetwork;
	}
	 public IUser resolveUserUserByUsername(String userName) {
	    	Preconditions.checkArgument(!Strings.isNullOrEmpty(userName), 
	    			"A username is required");
			SearchParameters searchParameters = new SearchParameters();
			searchParameters.setSearchString(userName);
			searchParameters.setSkip(0);
			searchParameters.setTop(1);

			try {
				SearchResult<IUser> result = XBelNetworkService.getInstance().findUsers(searchParameters);
				return  (IUser) result.getResults().iterator().next();
				
			} catch (NdexException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();		
			}
			return null;
		}
}
