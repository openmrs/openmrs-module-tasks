/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.tasks.api.loader;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openmrs.api.AdministrationService;
import org.openmrs.api.context.Context;
import org.openmrs.module.tasks.Priority;
import org.openmrs.module.tasks.SystemTask;
import org.openmrs.module.tasks.api.TasksService;
import org.openmrs.test.BaseModuleContextSensitiveTest;
import org.openmrs.util.OpenmrsUtil;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Properties;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Integration tests for SystemTaskCsvLoader. Tests that CSV files are properly loaded and parsed
 * into SystemTask entities.
 */
public class SystemTaskCsvLoaderTest extends BaseModuleContextSensitiveTest {
	
	@Override
	public Properties getRuntimeProperties() {
		Properties props = super.getRuntimeProperties();
		props.setProperty("module.allow_web_admin", "false");
		return props;
	}
	
	private File systemTasksDir;
	
	private TasksService tasksService;
	
	private AdministrationService adminService;
	
	@Before
	public void setUp() throws Exception {
		tasksService = Context.getService(TasksService.class);
		adminService = Context.getAdministrationService();
		
		// Create the systemtasks directory in the application data directory
		File configDir = OpenmrsUtil.getDirectoryInApplicationDataDirectory("configuration");
		systemTasksDir = new File(configDir, "systemtasks");
		if (!systemTasksDir.exists()) {
			systemTasksDir.mkdirs();
		}
		
		// Clean up any existing CSV files and checksums
		cleanupTestFiles();
	}
	
	@After
	public void tearDown() {
		cleanupTestFiles();
	}
	
	private void cleanupTestFiles() {
		if (systemTasksDir != null && systemTasksDir.exists()) {
			File[] files = systemTasksDir.listFiles();
			if (files != null) {
				for (File file : files) {
					file.delete();
				}
			}
		}
		
		// Clean up checksum global properties
		try {
			List<org.openmrs.GlobalProperty> props = adminService.getAllGlobalProperties();
			for (org.openmrs.GlobalProperty prop : props) {
				if (prop.getProperty().startsWith("tasks.systemtask.checksum.")) {
					adminService.purgeGlobalProperty(prop);
				}
			}
		}
		catch (Exception e) {
			// Ignore cleanup errors
		}
	}
	
	@Test
	public void loadSystemTasksFromCsvFiles_shouldLoadSystemTasks() throws IOException {
		// Given: A CSV file with multiple system tasks
		String csvContent = "Uuid,Name,Title,Description,Priority,Default Assignee Role,Rationale\n"
		        + "test-uuid-101,task-one,Task One,First task,HIGH,,Reason one\n"
		        + "test-uuid-102,task-two,Task Two,Second task,MEDIUM,,Reason two\n"
		        + "test-uuid-103,task-three,Task Three,Third task,LOW,,Reason three\n";
		
		createCsvFile("multiple_tasks.csv", csvContent);
		
		// When: Loading system tasks
		SystemTaskCsvLoader loader = new SystemTaskCsvLoader();
		loader.loadSystemTasksFromCsvFiles();
		
		Context.flushSession();
		Context.clearSession();
		
		// Then: All system tasks should be created
		List<SystemTask> allTasks = tasksService.getAllSystemTasks(false);
		assertThat(allTasks.size(), is(greaterThanOrEqualTo(3)));
		
		SystemTask task1 = tasksService.getSystemTaskByUuid("test-uuid-101");
		SystemTask task2 = tasksService.getSystemTaskByUuid("test-uuid-102");
		SystemTask task3 = tasksService.getSystemTaskByUuid("test-uuid-103");
		
		assertThat(task1, is(notNullValue()));
		assertThat(task1.getName(), is("task-one"));
		assertThat(task1.getTitle(), is("Task One"));
		assertThat(task1.getPriority(), is(Priority.HIGH));
		
		assertThat(task2, is(notNullValue()));
		assertThat(task2.getName(), is("task-two"));
		assertThat(task2.getTitle(), is("Task Two"));
		assertThat(task2.getPriority(), is(Priority.MEDIUM));
		
		assertThat(task3, is(notNullValue()));
		assertThat(task3.getName(), is("task-three"));
		assertThat(task3.getTitle(), is("Task Three"));
		assertThat(task3.getPriority(), is(Priority.LOW));
	}
	
	@Test
	public void loadSystemTasksFromCsvFiles_shouldSkipUnchangedFiles() throws IOException {
		// Given: A CSV file that has already been loaded
		String csvContent = "Uuid,Name,Title,Description,Priority,Default Assignee Role,Rationale\n"
		        + "test-uuid-201,original-task,Original Task,Original description,HIGH,,Original rationale\n";
		
		createCsvFile("unchanged_tasks.csv", csvContent);
		
		// Load it first time
		SystemTaskCsvLoader loader = new SystemTaskCsvLoader();
		loader.loadSystemTasksFromCsvFiles();
		
		Context.flushSession();
		Context.clearSession();
		
		// Modify the task directly in database
		SystemTask task = tasksService.getSystemTaskByUuid("test-uuid-201");
		task.setDescription("Modified in database");
		tasksService.saveSystemTask(task);
		
		Context.flushSession();
		Context.clearSession();
		
		// When: Loading again without changing the file
		loader.loadSystemTasksFromCsvFiles();
		
		Context.flushSession();
		Context.clearSession();
		
		// Then: The database modification should remain (file was skipped due to same checksum)
		SystemTask reloaded = tasksService.getSystemTaskByUuid("test-uuid-201");
		assertThat(reloaded.getDescription(), is("Modified in database"));
	}
	
	@Test
	public void loadSystemTasksFromCsvFiles_shouldReloadChangedFiles() throws IOException {
		// Given: A CSV file that has been loaded
		String originalContent = "Uuid,Name,Title,Description,Priority,Default Assignee Role,Rationale\n"
		        + "test-uuid-301,original-task,Original Task,Original description,HIGH,,Original rationale\n";
		
		createCsvFile("changed_tasks.csv", originalContent);
		
		// Load it first time
		SystemTaskCsvLoader loader = new SystemTaskCsvLoader();
		loader.loadSystemTasksFromCsvFiles();
		
		Context.flushSession();
		Context.clearSession();
		
		// Verify initial load
		SystemTask task = tasksService.getSystemTaskByUuid("test-uuid-301");
		assertThat(task.getDescription(), is("Original description"));
		
		// When: Changing the file content and reloading
		String updatedContent = "Uuid,Name,Title,Description,Priority,Default Assignee Role,Rationale\n"
		        + "test-uuid-301,updated-task,Updated Task,Updated description,MEDIUM,,Updated rationale\n";
		
		createCsvFile("changed_tasks.csv", updatedContent);
		
		loader.loadSystemTasksFromCsvFiles();
		
		Context.flushSession();
		Context.clearSession();
		
		// Then: The task should be updated with new values
		SystemTask reloaded = tasksService.getSystemTaskByUuid("test-uuid-301");
		assertThat(reloaded.getName(), is("updated-task"));
		assertThat(reloaded.getTitle(), is("Updated Task"));
		assertThat(reloaded.getDescription(), is("Updated description"));
		assertThat(reloaded.getPriority(), is(Priority.MEDIUM));
		assertThat(reloaded.getRationale(), is("Updated rationale"));
	}
	
	@Test
	public void loadSystemTasksFromCsvFiles_shouldHandleQuotedFields() throws IOException {
		// Given: A CSV file with quoted fields containing commas
		String csvContent = "Uuid,Name,Title,Description,Priority,Default Assignee Role,Rationale\n"
		        + "test-uuid-401,task-with-comma,\"Task with, comma\",\"Description with, comma and \"\"quotes\"\"\",HIGH,,\"Rationale with, comma\"\n";
		
		createCsvFile("quoted_tasks.csv", csvContent);
		
		// When: Loading system tasks
		SystemTaskCsvLoader loader = new SystemTaskCsvLoader();
		loader.loadSystemTasksFromCsvFiles();
		
		Context.flushSession();
		Context.clearSession();
		
		// Then: The quoted fields should be parsed correctly
		SystemTask loaded = tasksService.getSystemTaskByUuid("test-uuid-401");
		assertThat(loaded, is(notNullValue()));
		assertThat(loaded.getName(), is("task-with-comma"));
		assertThat(loaded.getTitle(), is("Task with, comma"));
		assertThat(loaded.getDescription(), is("Description with, comma and \"quotes\""));
		assertThat(loaded.getRationale(), is("Rationale with, comma"));
	}
	
	@Test
	public void loadSystemTasksFromCsvFiles_shouldHandleEmptyOptionalFields() throws IOException {
		// Given: A CSV file with empty optional fields
		String csvContent = "Uuid,Name,Title,Description,Priority,Default Assignee Role,Rationale\n"
		        + "test-uuid-501,minimal-task,Minimal Task,,,, \n";
		
		createCsvFile("minimal_tasks.csv", csvContent);
		
		// When: Loading system tasks
		SystemTaskCsvLoader loader = new SystemTaskCsvLoader();
		loader.loadSystemTasksFromCsvFiles();
		
		Context.flushSession();
		Context.clearSession();
		
		// Then: The task should be created with null optional fields
		SystemTask loaded = tasksService.getSystemTaskByUuid("test-uuid-501");
		assertThat(loaded, is(notNullValue()));
		assertThat(loaded.getName(), is("minimal-task"));
		assertThat(loaded.getTitle(), is("Minimal Task"));
		assertThat(loaded.getDescription(), is(nullValue()));
		assertThat(loaded.getPriority(), is(nullValue()));
		assertThat(loaded.getRationale(), is(nullValue()));
	}
	
	@Test
	public void loadSystemTasksFromCsvFiles_shouldUnretireRetiredTask() throws IOException {
		// Given: A retired system task exists
		SystemTask retiredTask = new SystemTask();
		retiredTask.setUuid("test-uuid-601");
		retiredTask.setName("previously-retired");
		retiredTask.setTitle("Previously Retired");
		retiredTask.setRetired(true);
		retiredTask.setRetireReason("Was retired");
		tasksService.saveSystemTask(retiredTask);
		
		Context.flushSession();
		Context.clearSession();
		
		// Verify it's retired
		SystemTask beforeReload = tasksService.getSystemTaskByUuid("test-uuid-601");
		assertThat(beforeReload.getRetired(), is(true));
		
		// When: A CSV file references this task
		String csvContent = "Uuid,Name,Title,Description,Priority,Default Assignee Role,Rationale\n"
		        + "test-uuid-601,now-active,Now Active,Active again,HIGH,,Back in action\n";
		
		createCsvFile("unretire_tasks.csv", csvContent);
		
		SystemTaskCsvLoader loader = new SystemTaskCsvLoader();
		loader.loadSystemTasksFromCsvFiles();
		
		Context.flushSession();
		Context.clearSession();
		
		// Then: The task should be unretired
		SystemTask afterReload = tasksService.getSystemTaskByUuid("test-uuid-601");
		assertThat(afterReload.getRetired(), is(false));
		assertThat(afterReload.getRetireReason(), is(nullValue()));
		assertThat(afterReload.getName(), is("now-active"));
		assertThat(afterReload.getTitle(), is("Now Active"));
	}
	
	@Test
	public void loadSystemTasksFromCsvFiles_shouldLoadFromMultipleCsvFiles() throws IOException {
		// Given: Multiple CSV files
		String csvContent1 = "Uuid,Name,Title,Description,Priority,Default Assignee Role,Rationale\n"
		        + "test-uuid-701,task-from-file-1,Task from File 1,From first file,HIGH,,\n";
		
		String csvContent2 = "Uuid,Name,Title,Description,Priority,Default Assignee Role,Rationale\n"
		        + "test-uuid-702,task-from-file-2,Task from File 2,From second file,LOW,,\n";
		
		createCsvFile("file1.csv", csvContent1);
		createCsvFile("file2.csv", csvContent2);
		
		// When: Loading system tasks
		SystemTaskCsvLoader loader = new SystemTaskCsvLoader();
		loader.loadSystemTasksFromCsvFiles();
		
		Context.flushSession();
		Context.clearSession();
		
		// Then: Tasks from both files should be created
		SystemTask task1 = tasksService.getSystemTaskByUuid("test-uuid-701");
		SystemTask task2 = tasksService.getSystemTaskByUuid("test-uuid-702");
		
		assertThat(task1, is(notNullValue()));
		assertThat(task1.getName(), is("task-from-file-1"));
		assertThat(task1.getTitle(), is("Task from File 1"));
		
		assertThat(task2, is(notNullValue()));
		assertThat(task2.getName(), is("task-from-file-2"));
		assertThat(task2.getTitle(), is("Task from File 2"));
	}
	
	@Test
	public void loadSystemTasksFromCsvFiles_shouldSkipLinesWithMissingRequiredFields() throws IOException {
		// Given: A CSV file with some invalid lines (missing UUID, name, or title)
		// Line 2: valid, Line 3: missing UUID, Line 4: missing name, Line 5: missing title, Line 6: valid
		String csvContent = "Uuid,Name,Title,Description,Priority,Default Assignee Role,Rationale\n"
		        + "test-uuid-801,valid-task,Valid Task,Valid description,HIGH,,\n"
		        + ",missing-uuid,Missing UUID,Has description,HIGH,,\n"
		        + "test-uuid-803,,Missing Name,Missing name,HIGH,,\n"
		        + "test-uuid-804,missing-title,,Missing title,HIGH,,\n"
		        + "test-uuid-805,another-valid,Another Valid,Also valid,LOW,,\n";

		createCsvFile("invalid_lines.csv", csvContent);

		// When: Loading system tasks
		// Note: Invalid lines will be logged as warnings by the loader
		SystemTaskCsvLoader loader = new SystemTaskCsvLoader();
		loader.loadSystemTasksFromCsvFiles();

		Context.flushSession();
		Context.clearSession();

		// Then: Only valid tasks should be created (2 out of 5 data lines)
		SystemTask valid1 = tasksService.getSystemTaskByUuid("test-uuid-801");
		SystemTask valid2 = tasksService.getSystemTaskByUuid("test-uuid-805");
		SystemTask invalidMissingName = tasksService.getSystemTaskByUuid("test-uuid-803");
		SystemTask invalidMissingTitle = tasksService.getSystemTaskByUuid("test-uuid-804");

		assertThat("Task with valid UUID, name, and title should be created", valid1, is(notNullValue()));
		assertThat(valid1.getName(), is("valid-task"));
		assertThat(valid1.getTitle(), is("Valid Task"));

		assertThat("Second valid task should be created", valid2, is(notNullValue()));
		assertThat(valid2.getName(), is("another-valid"));
		assertThat(valid2.getTitle(), is("Another Valid"));

		// Lines with missing required fields should be skipped (logs a warning)
		assertThat("Task with missing name should NOT be created", invalidMissingName, is(nullValue()));
		assertThat("Task with missing title should NOT be created", invalidMissingTitle, is(nullValue()));

		// Verify total count - should only have the 2 valid tasks from this file
		List<SystemTask> allTasks = tasksService.getAllSystemTasks(false);
		long testTaskCount = allTasks.stream()
		        .filter(t -> t.getUuid().startsWith("test-uuid-80"))
		        .count();
		assertThat("Only 2 valid tasks should be created from the 5 data lines", testTaskCount, is(2L));
	}
	
	private void createCsvFile(String filename, String content) throws IOException {
		File csvFile = new File(systemTasksDir, filename);
		try (FileWriter writer = new FileWriter(csvFile)) {
			writer.write(content);
		}
	}
}
