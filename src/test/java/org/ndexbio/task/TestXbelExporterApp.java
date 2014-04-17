package org.ndexbio.task;

import java.io.File;
import java.io.IOException;

import org.ndexbio.common.models.object.NdexDataModelService;
import org.ndexbio.task.event.NdexNetworkState;
import org.ndexbio.task.event.NdexTaskEventHandler;
import org.ndexbio.task.service.NdexJVMDataModelService;
import org.ndexbio.task.service.NdexTaskModelService;
import org.ndexbio.xbel.exporter.XbelNetworkExporter;
import org.ndexbio.xbel.exporter.XbelNetworkExporter.XbelMarshaller;

public class TestXbelExporterApp {

	private static final String NETWORK_EXPORT_PATH = "/opt/ndex/exported-networks/";
	private static final String XBEL_FILE_EXTENSION = ".xbel";
	public static void main(String[] args) throws IOException {
		//String networkId = "C25R732"; // is for large corpus
		String networkId = "C25R1308"; // is for small corpus
		String userId = "C31R3"; // dbowner
		//add shutdown hook
		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				System.out.println("TextXbelExporter completed.");
			}
		});
		
		NdexTaskModelService  modelService = new NdexJVMDataModelService();
		// initiate the network state
		initiateStateForMonitoring(modelService, userId, networkId);
		NdexTaskEventHandler eventHandler = new NdexTaskEventHandler("/tmp/ndextaskevents.csv");
		XbelNetworkExporter exporter = new XbelNetworkExporter(userId, networkId, 
				modelService,
				resolveExportFile(modelService, userId, networkId));
		//
		exporter.exportNetwork();
		eventHandler.shutdown();

	}

	private static String resolveExportFile(NdexTaskModelService  modelService, 
			String userId, String networkId) {
		StringBuilder sb = new StringBuilder(NETWORK_EXPORT_PATH);
		
		sb.append(userId);
		if (! new File(sb.toString()).exists()) {
			new File(sb.toString()).mkdir();
		}
		sb.append(File.separator);
		sb.append(modelService.getNetworkById(userId,networkId).getName());
		sb.append(XBEL_FILE_EXTENSION);
		System.out.println("Export file: " +sb.toString());
		return sb.toString();
	
	}
	
	private static void initiateStateForMonitoring(NdexTaskModelService  modelService, 
			String userId,
			String networkId) {
		NdexNetworkState.INSTANCE.setNetworkId(networkId);
		NdexNetworkState.INSTANCE.setNetworkName(modelService.getNetworkById(userId, networkId).getName());
		
		
	}
	
}