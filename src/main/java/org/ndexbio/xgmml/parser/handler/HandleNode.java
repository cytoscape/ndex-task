package org.ndexbio.xgmml.parser.handler;

/*
 * #%L
 * Cytoscape IO Impl (io-impl)
 * $Id:$
 * $HeadURL:$
 * %%
 * Copyright (C) 2006 - 2013 The Cytoscape Consortium
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as 
 * published by the Free Software Foundation, either version 2.1 of the 
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 * 
 * You should have received a copy of the GNU General Lesser Public 
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-2.1.html>.
 * #L%
 */


import java.util.concurrent.ExecutionException;

import org.ndexbio.common.exceptions.NdexException;
import org.ndexbio.model.object.network.Network;
import org.ndexbio.model.object.network.Node;
import org.ndexbio.xgmml.parser.ParseState;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

public class HandleNode extends AbstractHandler {

	@Override
	public ParseState handle(final String namespace, final String tag, final String qName,  final Attributes atts, final ParseState current) throws SAXException, NdexException, ExecutionException {
		final String href = atts.getValue(ReadDataManager.XLINK, "href");
		Object id = null;
		String label = null;
		String nodeName = null;
		Node node = null;
		final Network curNet = manager.getCurrentNetwork();
		//final CyNetwork rootNet = manager.getRootNetwork();
		
		if (href == null) {
			
			id = getId(atts);
			// Create the node
			
			nodeName = atts.getValue("label");
			if (nodeName == null){
				nodeName = atts.getValue("name");
			}
			
			node = manager.findOrCreateNode(id.toString(), nodeName);
			
			node.setName(nodeName);

			
		} else {
			throw new NdexException("Not yet handling XLINKs");
		}
		
		if (node != null){
			manager.setCurrentElement(node);
			manager.setCurrentNode(node);
		}
		
		return current;
	}
}
