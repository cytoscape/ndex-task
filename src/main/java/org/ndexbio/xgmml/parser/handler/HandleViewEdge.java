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

import org.ndexbio.xgmml.parser.ParseState;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

public class HandleViewEdge extends AbstractHandler {

	// This handler is not currently used - no special handling for this RDF property
	// Also not clear if NDEx will handle viewEdge case
	@Override
	public ParseState handle(final String namespace, final String tag, final String qName,  Attributes atts, ParseState current) throws SAXException {
		String sId = atts.getValue("cy:edgeId");
		
		try {
			Long id = Long.valueOf(sId);
			manager.setCurrentElementId(id);
		} catch (NumberFormatException nfe) {
			logger.error("cy:edgeId is not a number: " + sId);
			manager.setCurrentElementId(null);
		}
		
		return current;
	}
}