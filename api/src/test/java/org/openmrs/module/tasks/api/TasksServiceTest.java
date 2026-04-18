/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.tasks.api;

import org.hl7.fhir.r4.model.CarePlan;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.openmrs.api.APIException;
import org.openmrs.module.tasks.SystemTask;
import org.openmrs.module.tasks.Task;
import org.openmrs.module.tasks.api.dao.TasksDao;
import org.openmrs.module.tasks.api.impl.TasksServiceImpl;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * This is a unit test, which verifies logic in TasksService. It doesn't extend
 * BaseModuleContextSensitiveTest, thus it is run without the in-memory DB and Spring context.
 */
public class TasksServiceTest {
	
	@InjectMocks
	TasksServiceImpl tasksService;
	
	@Mock
	TasksDao dao;
	
	@Before
	public void setupMocks() {
		MockitoAnnotations.initMocks(this);
	}
	
	@Test
	public void saveTask_shouldDelegateToDao() {
		//Given
		Task task = new Task();
		task.setDescription("some description");
		task.setStatus(CarePlan.CarePlanActivityStatus.NOTSTARTED);
		task.setKind(CarePlan.CarePlanActivityKind.APPOINTMENT);
		
		when(dao.saveTask(task)).thenReturn(task);
		
		//When
		Task savedTask = tasksService.saveTask(task);
		
		//Then
		verify(dao).saveTask(task);
		assertThat(savedTask, is(task));
	}
	
	@Test
	public void getTaskByUuid_shouldDelegateToDao() {
		//Given
		String uuid = "test-uuid";
		Task task = new Task();
		task.setUuid(uuid);
		
		when(dao.getTaskByUuid(uuid)).thenReturn(task);
		
		//When
		Task foundTask = tasksService.getTaskByUuid(uuid);
		
		//Then
		verify(dao).getTaskByUuid(uuid);
		assertThat(foundTask, is(task));
	}
	
	@Test
	public void getTasksByPatientId_shouldDelegateToDao() {
		//Given
		Integer patientId = 2;
		List<Task> tasks = new ArrayList<>();
		Task task1 = new Task();
		task1.setDescription("Task 1");
		tasks.add(task1);
		
		when(dao.getTasksByPatientId(patientId)).thenReturn(tasks);
		
		//When
		List<Task> foundTasks = tasksService.getTasksByPatientId(patientId);
		
		//Then
		verify(dao).getTasksByPatientId(patientId);
		assertThat(foundTasks.size(), is(1));
		assertThat(foundTasks.get(0).getDescription(), is("Task 1"));
	}
	
	@Test
	public void voidTask_shouldMarkTaskVoidedAndDelegateToDao() {
		//Given
		Task task = new Task();
		when(dao.saveTask(task)).thenReturn(task);
		String reason = "Test reason";
		
		//When
		tasksService.voidTask(task, reason);
		
		//Then
		assertThat(task.getVoided(), is(true));
		assertThat(task.getVoidReason(), is(reason));
		verify(dao).saveTask(task);
	}
	
	@Test(expected = APIException.class)
	public void voidTask_withNullTask_shouldThrow() {
		tasksService.voidTask(null, "reason");
	}
	
	@Test(expected = APIException.class)
	public void voidTask_withNullReason_shouldThrow() {
		tasksService.voidTask(new Task(), null);
	}
	
	@Test(expected = APIException.class)
	public void voidTask_withEmptyReason_shouldThrow() {
		tasksService.voidTask(new Task(), "");
	}
	
	@Test(expected = APIException.class)
	public void voidTask_withWhitespaceReason_shouldThrow() {
		tasksService.voidTask(new Task(), "   ");
	}
	
	@Test
	public void voidTask_whenAlreadyVoided_shouldReturnEarlyWithoutSaving() {
		Task task = new Task();
		task.setVoided(true);
		task.setVoidReason("already voided");
		
		tasksService.voidTask(task, "new reason");
		
		// Reason is NOT updated, and DAO is not called again
		assertThat(task.getVoidReason(), is("already voided"));
		verify(dao, never()).saveTask(task);
	}
	
	@Test
	public void voidTask_shouldSetDateVoidedWhenNull() {
		Task task = new Task();
		when(dao.saveTask(task)).thenReturn(task);
		
		Date before = new Date();
		tasksService.voidTask(task, "reason");
		Date after = new Date();
		
		assertThat(task.getDateVoided(), is(notNullValue()));
		assertThat(task.getDateVoided().getTime() >= before.getTime(), is(true));
		assertThat(task.getDateVoided().getTime() <= after.getTime(), is(true));
	}
	
	@Test
	public void voidTask_shouldPreserveExistingDateVoided() {
		Task task = new Task();
		Date existing = new Date(1000L);
		task.setDateVoided(existing);
		when(dao.saveTask(task)).thenReturn(task);
		
		tasksService.voidTask(task, "reason");
		
		assertThat(task.getDateVoided(), is(sameInstance(existing)));
	}
	
	@Test
	public void purgeTask_shouldDelegateToDao() {
		//Given
		Task task = new Task();
		
		//When
		tasksService.purgeTask(task);
		
		//Then
		verify(dao).deleteTask(task);
	}
	
	@Test(expected = APIException.class)
	public void purgeTask_withNullTask_shouldThrow() {
		tasksService.purgeTask(null);
	}
	
	@Test
	public void saveSystemTask_shouldDelegateToDao() {
		SystemTask systemTask = new SystemTask();
		systemTask.setName("a-task");
		when(dao.saveSystemTask(systemTask)).thenReturn(systemTask);
		
		SystemTask saved = tasksService.saveSystemTask(systemTask);
		
		verify(dao).saveSystemTask(systemTask);
		assertThat(saved, is(sameInstance(systemTask)));
	}
	
	@Test
	public void getSystemTaskByUuid_shouldDelegateToDao() {
		String uuid = "system-task-uuid";
		SystemTask systemTask = new SystemTask();
		systemTask.setUuid(uuid);
		when(dao.getSystemTaskByUuid(uuid)).thenReturn(systemTask);
		
		SystemTask found = tasksService.getSystemTaskByUuid(uuid);
		
		verify(dao).getSystemTaskByUuid(uuid);
		assertThat(found, is(sameInstance(systemTask)));
	}
	
	@Test
	public void getAllSystemTasks_shouldDelegateToDaoWithIncludeRetiredFlag() {
		SystemTask active = new SystemTask();
		SystemTask retired = new SystemTask();
		when(dao.getAllSystemTasks(false)).thenReturn(Arrays.asList(active));
		when(dao.getAllSystemTasks(true)).thenReturn(Arrays.asList(active, retired));
		
		assertThat(tasksService.getAllSystemTasks(false).size(), is(1));
		assertThat(tasksService.getAllSystemTasks(true).size(), is(2));
		verify(dao).getAllSystemTasks(false);
		verify(dao).getAllSystemTasks(true);
	}
	
	@Test
	public void retireSystemTask_shouldMarkRetiredAndDelegateToDao() {
		SystemTask systemTask = new SystemTask();
		when(dao.saveSystemTask(systemTask)).thenReturn(systemTask);
		
		tasksService.retireSystemTask(systemTask, "no longer needed");
		
		assertThat(systemTask.getRetired(), is(true));
		assertThat(systemTask.getRetireReason(), is("no longer needed"));
		assertThat(systemTask.getDateRetired(), is(notNullValue()));
		verify(dao).saveSystemTask(systemTask);
	}
	
	@Test(expected = APIException.class)
	public void retireSystemTask_withNullSystemTask_shouldThrow() {
		tasksService.retireSystemTask(null, "reason");
	}
	
	@Test(expected = APIException.class)
	public void retireSystemTask_withNullReasonAndNoExistingRetireReason_shouldThrow() {
		tasksService.retireSystemTask(new SystemTask(), null);
	}
	
	@Test(expected = APIException.class)
	public void retireSystemTask_withEmptyReasonAndNoExistingRetireReason_shouldThrow() {
		tasksService.retireSystemTask(new SystemTask(), "   ");
	}
	
	@Test
	public void retireSystemTask_withNullReasonAndExistingRetireReason_shouldReuseExisting() {
		SystemTask systemTask = new SystemTask();
		systemTask.setRetireReason("previously set");
		when(dao.saveSystemTask(systemTask)).thenReturn(systemTask);
		
		tasksService.retireSystemTask(systemTask, null);
		
		assertThat(systemTask.getRetired(), is(true));
		assertThat(systemTask.getRetireReason(), is("previously set"));
		verify(dao).saveSystemTask(systemTask);
	}
	
	@Test
	public void retireSystemTask_withEmptyReasonAndExistingRetireReason_shouldReuseExisting() {
		SystemTask systemTask = new SystemTask();
		systemTask.setRetireReason("previously set");
		when(dao.saveSystemTask(systemTask)).thenReturn(systemTask);
		
		tasksService.retireSystemTask(systemTask, " ");
		
		assertThat(systemTask.getRetireReason(), is("previously set"));
	}
	
	@Test
	public void retireSystemTask_shouldPreserveExistingDateRetired() {
		SystemTask systemTask = new SystemTask();
		Date existing = new Date(1000L);
		systemTask.setDateRetired(existing);
		when(dao.saveSystemTask(systemTask)).thenReturn(systemTask);
		
		tasksService.retireSystemTask(systemTask, "reason");
		
		assertThat(systemTask.getDateRetired(), is(sameInstance(existing)));
	}
	
	@Test
	public void retireSystemTask_shouldSetDateRetiredWhenNull() {
		SystemTask systemTask = new SystemTask();
		when(dao.saveSystemTask(systemTask)).thenReturn(systemTask);
		
		Date before = new Date();
		tasksService.retireSystemTask(systemTask, "reason");
		Date after = new Date();
		
		assertThat(systemTask.getDateRetired(), is(notNullValue()));
		assertThat(systemTask.getDateRetired().getTime() >= before.getTime(), is(true));
		assertThat(systemTask.getDateRetired().getTime() <= after.getTime(), is(true));
	}
}
