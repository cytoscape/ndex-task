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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.ndexbio.xgmml.parser.MetadataEntries;
import org.ndexbio.xgmml.parser.MetadataParser;
import org.ndexbio.xgmml.parser.ObjectType;
import org.ndexbio.xgmml.parser.ObjectTypeMap;
import org.ndexbio.xgmml.parser.ParseState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXParseException;

public class AttributeValueUtil {

    static final String ATTR_NAME = "name";
    static final String ATTR_LABEL = "label";
    static final String ATTR_VALUE = "value";
    static final String LOCKED_VISUAL_PROPS = "lockedVisualProperties";
    
    static final Pattern XLINK_PATTERN = Pattern.compile(".*#(-?\\d+)");
    
    private Locator locator;

    private final ReadDataManager manager;
    private final ObjectTypeMap typeMap;
    
    protected static final Logger logger = LoggerFactory.getLogger(AttributeValueUtil.class);

    public AttributeValueUtil(final ReadDataManager manager) {
        this.manager = manager;
        this.typeMap = new ObjectTypeMap();
    }

    public void setLocator(Locator locator) {
        this.locator = locator;
    }
 
    /********************************************************************
     * Routines to handle attributes
     *******************************************************************/

    /**
     * Return the string attribute value for the attribute indicated by "key".
     * If no such attribute exists, return null. In particular, this routine
     * looks for an attribute with a <b>name</b> or <b>label</b> of <i>key</i>
     * and returns the <b>value</b> of that attribute.
     * 
     * @param atts
     *            the attributes
     * @param key
     *            the specific attribute to get
     * @return the value for "key" or null if no such attribute exists
     */
    protected String getAttributeValue(Attributes atts, String key) {
        String name = atts.getValue(ATTR_NAME);

        if (name == null) name = atts.getValue(ATTR_LABEL);

        if (name != null && name.equals(key))
            return atts.getValue(ATTR_VALUE);
        else
            return null;
    }

    /**
	 * Return the typed attribute value for the passed attribute. In this case, the caller has already determined that
	 * this is the correct attribute and we just lookup the value. This routine is responsible for type conversion
	 * consistent with the passed argument.
	 * 
	 * @param type the ObjectType of the value
	 * @param atts the attributes
	 * @param name the attribute name
	 * @return the value of the attribute in the appropriate type
	 */
    protected Object getTypedAttributeValue(ObjectType type, Attributes atts, String name) throws SAXParseException {
        String value = atts.getValue("value");

        try {
            return typeMap.getTypedValue(type, value, name);
        } catch (Exception e) {
            throw new SAXParseException("Unable to convert '" + value + "' to type " + type.toString(), locator);
        }
    }

    /**
     * Return the attribute value for the attribute indicated by "key". If no
     * such attribute exists, return null.
     * 
     * @param atts
     *            the attributes
     * @param key
     *            the specific attribute to get
     * @return the value for "key" or null if no such attribute exists
     */
    protected String getAttribute(Attributes atts, String key) {
        return atts.getValue(key);
    }

    /**
     * Return the attribute value for the attribute indicated by "key". If no
     * such attribute exists, return null.
     * 
     * @param atts
     *            the attributes
     * @param key
     *            the specific attribute to get
     * @param ns
     *            the namespace for the attribute we're interested in
     * @return the value for "key" or null if no such attribute exists
     */
    protected String getAttributeNS(Attributes atts, String key, String ns) {
        if (atts.getValue(ns, key) != null)
            return atts.getValue(ns, key);
        else
            return atts.getValue(key);
    }

    protected ParseState handleAttribute(Attributes atts) throws SAXParseException {
    	ParseState parseState = ParseState.NONE;
    	
    	final String name = atts.getValue("name");
    	String type = atts.getValue("type");
    	String value = atts.getValue("value");
    	
    	if ("has_nested_network".equals(name))
        	type = ObjectType.BOOLEAN.getName();
    	
    	// Not handling equations or hidden attributes in NDEx at this time
    	
    	//final boolean isEquation = ObjectTypeMap.fromXGMMLBoolean(atts.getValue("cy:equation"));
    	//final boolean isHidden = ObjectTypeMap.fromXGMMLBoolean(atts.getValue("cy:hidden"));
        
		final IMetadataObject curElement = manager.getCurrentElement();
		
		//INetwork network = manager.getCurrentNetwork();
		ObjectType objType = typeMap.getType(type);
		if (objType.equals(ObjectType.LIST)){
			manager.currentAttributeID = name;
			manager.setCurrentList(new ArrayList<String>());			
			return ParseState.LIST_ATT;
		}
		
		//System.out.println("setting attribute name = " + name + " value = " + value );
		
		if (null != type){
			setAttribute(curElement, name, value);
		}
		
		
		return parseState;
		
		//
		// TODO: review to see if this is relevant for datatype preservation
		// This section deals with datatyping, among other features.
		// 
		
        /*
		// This is necessary, because external edges of 2.x Groups may be written
		// under the group subgraph, but the edge will be created on the root-network only,
		if (curElement instanceof CyNode || curElement instanceof CyEdge) {
			boolean containsElement = (curElement instanceof CyNode && curNet.containsNode((CyNode) curElement));
			containsElement |= (curElement instanceof CyEdge && curNet.containsEdge((CyEdge) curElement));
			
			// So if the current network does not contain this element, the CyRootNetwork should contain it
			if (!containsElement)
				curNet = manager.getRootNetwork();
		}
		
		CyRow row = null;
		
		if (isHidden) {
			row = curNet.getRow(curElement, CyNetwork.HIDDEN_ATTRS);
		} else {
			// TODO: What are the rules here?
			// Node/edge attributes are always shared, except "selected"?
			// Network name must be local, right? What about other network attributes?
			if (CyNetwork.SELECTED.equals(name) || (curElement instanceof CyNetwork))
				row = curNet.getRow(curElement, CyNetwork.LOCAL_ATTRS);
			else
				row = curNet.getRow(curElement, CyNetwork.DEFAULT_ATTRS); // Will be created in the shared table
		}		
		
		CyTable table = row.getTable();
        CyColumn column = table.getColumn(name);
        
        if (column != null) {
        	// Check if it's a virtual column
        	// It's necessary because the source row may not exist yet, which would throw an exception
        	// when the value is set. Doing this forces the creation of the source row.
        	final VirtualColumnInfo info = column.getVirtualColumnInfo();
        	
        	if (info.isVirtual()) {
        		final CyTable srcTable = info.getSourceTable(); 
        		final CyColumn srcColumn = srcTable.getColumn(info.getSourceColumn());
        		final Class<?> jkColType = table.getColumn(info.getTargetJoinKey()).getType();
        		final Object jkValue = row.get(info.getTargetJoinKey(), jkColType);
        		final Collection<CyRow> srcRowList = srcTable.getMatchingRows(info.getSourceJoinKey(), jkValue);
        		final CyRow srcRow; 
        		
        		if (srcRowList == null || srcRowList.isEmpty()) {
        			if (info.getTargetJoinKey().equals(CyIdentifiable.SUID)) {
        				// Try to create the row
        				srcRow = srcTable.getRow(jkValue);
        			} else {
						logger.error("Unable to import virtual column \"" + name + "\": The source table \""
								+ srcTable.getTitle() + "\" does not have any matching rows for join key \""
								+ info.getSourceJoinKey() + "=" + jkValue + "\".");
	        			return parseState;
        			}
        		} else {
        			srcRow = srcRowList.iterator().next();
        		}
        		
        		// Use the source table instead
        		table = srcTable;
        		column = srcColumn;
        		row = srcRow;
        	}
        }
        
        
        Object value = null;
        ObjectType objType = typeMap.getType(type);

        if (isEquation) {
        	// It is an equation...
        	String formula = atts.getValue("value");
        	
            if (name != null && formula != null) {
            	manager.addEquationString(row, name, formula);
            }
        } else {
        	// Regular attribute value...
        	value = getTypedAttributeValue(objType, atts, name);
        }

        switch (objType) {
			case BOOLEAN:
				if (name != null) setAttribute(row, name, Boolean.class, (Boolean) value);
				break;
			case REAL:
				if (name != null) {
					if (false) //SUIDUpdater.isUpdatable(name))
						setAttribute(row, name, Long.class, (Long) value);
					else
						setAttribute(row, name, Double.class, (Double) value);
				}
				break;
			case INTEGER:
				if (name != null) setAttribute(row, name, Integer.class, (Integer) value);
				break;
			case STRING:
				if (name != null) setAttribute(row, name, String.class, (String) value);
				break;
			// We need to be *very* careful. Because we duplicate attributes for
			// each network we write out, we wind up reading and processing each
			// attribute multiple times, once for each network. This isn't a problem
			// for "base" attributes, but is a significant problem for attributes
			// like LIST and MAP where we add to the attribute as we parse. So, we
			// must make sure to clear out any existing values before we parse.
			case LIST:
				manager.currentAttributeID = name;
				manager.setCurrentRow(row);
				
				if (column != null && List.class.isAssignableFrom(column.getType()))
					row.set(name, null);
				
				return ParseState.LIST_ATT;
		}
		*/

        
    }
    
    public static void setAttribute(final IMetadataObject element, final String key, final String value){
    	if (null == element.getMetadata()){
    		element.setMetadata(new HashMap<String, String>());
    	}
    	element.getMetadata().put(key, value);
    }
    
    // TODO - not used at the moment, determine role - are we going to support XLINKs?
    public static Long getIdFromXLink(String href) {
		Matcher matcher = XLINK_PATTERN.matcher(href);
		return matcher.matches() ? Long.valueOf(matcher.group(1)) : null;
	}
}
