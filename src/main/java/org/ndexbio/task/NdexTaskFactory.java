package org.ndexbio.task;

import org.ndexbio.common.exceptions.NdexException;
import org.ndexbio.orientdb.domain.TaskType;
import org.ndexbio.orientdb.persistence.NdexTaskService;
import org.ndexbio.rest.models.Task;

/*
 * Singleton responsible for instantiating the appropriate implementation
 * of NdexTask based on the Task type
 */
 enum NdexTaskFactory {
	INSTANCE;
	
	NdexTask getNdexTaskByTaskType(String taskId){
		NdexTaskService taskService = new NdexTaskService();
		try {
			Task task = taskService.getITask(taskId);
			if( task.getType() == TaskType.PROCESS_UPLOADED_NETWORK) {
				return new FileUploadTask(taskId);
			}
			throw new IllegalArgumentException("TTask type: " +task.getType() +" is not supported");
		} catch (IllegalArgumentException | SecurityException | NdexException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
		
	}
}
