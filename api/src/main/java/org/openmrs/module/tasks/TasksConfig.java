package org.openmrs.module.tasks;

import org.springframework.stereotype.Component;

/**
 * Defines security privilege constants for the Tasks module.
 */
@Component("tasks.TasksConfig")
public final class TasksConfig {
	
	private TasksConfig() {
	}
	
	public static final String TASKS_VIEW_PRIVILEGE = "View Tasks";
	
	public static final String TASKS_MANAGE_PRIVILEGE = "Manage Tasks";
	
	public static final String TASKS_DELETE_PRIVILEGE = "Delete Tasks";
	
	@Deprecated
	public static final String MODULE_PRIVILEGE = TASKS_MANAGE_PRIVILEGE;
}
