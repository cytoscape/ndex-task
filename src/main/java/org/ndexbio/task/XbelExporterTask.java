package org.ndexbio.task;

import java.io.File;
import java.io.IOException;

import org.ndexbio.common.access.NdexAOrientDBConnectionPool;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.object.Task;
import org.ndexbio.model.object.Status;
import org.ndexbio.task.event.NdexTaskEventHandler;
import org.ndexbio.task.service.NdexJVMDataModelService;
import org.ndexbio.task.service.NdexTaskModelService;
import org.ndexbio.xbel.exporter.XbelNetworkExporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx;

/*
 * Represents an NdexTask subclass responsible for exporting an XBEL network
 * from the NDEx database to an external file in XML format that adheres to the
 * XBEL schema. If the task type is KAMCOMPILE, the class is also responsible
 * for creating a new Task enrty in the database indicating that the new XML
 * file should be processed by the Kam compiler.
 * 
 */

public class XbelExporterTask extends NdexTask {
	
	private String networkId;
//	private static final String NETWORK_EXPORT_PATH = "/opt/ndex/exported-networks/";
//	private static final String NETWORK_EXPORT_EVENT_PATH = "/opt/ndex/exported-networks-events/";
	private static final String XBEL_FILE_EXTENSION = ".xbel";
	private static final String EVENT_FILE_EXTENSION = ".csv";
//	private final NdexTaskModelService modelService;
	private NdexTaskEventHandler eventHandler;
	private Status taskStatus;
	
	
	private static final Logger logger = LoggerFactory
			.getLogger(XbelExporterTask.class);
	
	public XbelExporterTask(Task task) throws
		IllegalArgumentException, SecurityException, NdexException{
		
			super(task);
			this.networkId = this.getTask().getResource();
//			this.modelService = new NdexJVMDataModelService();
			
	}

	@Override
	public Task call() throws Exception {
		try {
			//TODO: Event stuff was commented out bj CJ. need to review later.
	/*		String eventFilename = 
					this.resolveFilename(this.NETWORK_EXPORT_EVENT_PATH, this.EVENT_FILE_EXTENSION);
			this.eventHandler = new NdexTaskEventHandler(eventFilename); */
			this.exportNetwork();
			return this.getTask();
		} catch (InterruptedException e) {
			logger.info(this.getClass().getName() +" interupted");
			return null;
		} finally {
			if (null != this.eventHandler) {
				this.eventHandler.shutdown();
			}
		}
	}
	
	/*
	 * private method to invoke the xbel network exporter
	 */
	private void exportNetwork() throws Exception{
		this.taskStatus = Status.PROCESSING;
		this.startTask();
		String exportFilename = this.resolveFilename(
				Configuration.getInstance().getNdexRoot() + "/exported-networks/", 
				this.XBEL_FILE_EXTENSION);
	
		ODatabaseDocumentTx db = null; 
		try {
			db = NdexAOrientDBConnectionPool.getInstance().acquire();
			NdexTaskModelService modelService = new NdexJVMDataModelService(db);
			XbelNetworkExporter exporter = new XbelNetworkExporter(this.getTask().getTaskOwnerId().toString(),
				this.networkId,
				 modelService, exportFilename);
			exporter.exportNetwork();
			this.taskStatus = Status.COMPLETED;
			this.updateTaskStatus(this.taskStatus);
		} finally { 
			if ( db !=null ) db.close();
		}
	}
	
	/*
	 * private method to resolve the filename for the exported file
	 * Current convention is to use a fixed based directory under /opt/ndex
	 * add a subdriectory based on the username and use the network name plus the
	 * xbel extension as a filename
	 */
	private String resolveFilename(String path, String extension) {
		// create the directory if not exists
		if (! new File(path).exists()) {
			new File(path).mkdir();
		}
		
		StringBuilder sb = new StringBuilder(path);
		sb.append(File.separator);
		sb.append(this.getTask().getExternalId());
		sb.append(extension);
		return sb.toString();		
	}
	
	

	protected String getNetworkId() {
		return networkId;
	}

	private void setNetworkId(String networkId) {
		this.networkId = networkId;
	}

}
