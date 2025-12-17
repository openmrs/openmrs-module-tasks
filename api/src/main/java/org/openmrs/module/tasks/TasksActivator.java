package org.openmrs.module.tasks;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.module.BaseModuleActivator;

/**
 * Module lifecycle handler for the Tasks module.
 */
public class TasksActivator extends BaseModuleActivator {
	
	private static final Log log = LogFactory.getLog(TasksActivator.class);
	
	@Override
	public void started() {
		log.info("Tasks module started");
	}
	
	@Override
	public void stopped() {
		log.info("Tasks module stopped");
	}
	
}
