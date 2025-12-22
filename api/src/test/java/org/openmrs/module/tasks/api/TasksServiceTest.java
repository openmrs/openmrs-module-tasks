/**
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
import org.openmrs.Patient;
import org.openmrs.module.tasks.Task;
import org.openmrs.module.tasks.api.dao.TasksDao;
import org.openmrs.module.tasks.api.impl.TasksServiceImpl;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TasksService. These tests verify delegation logic without DB or Spring context.
 */
public class TasksServiceTest {
	
	@InjectMocks
	private TasksServiceImpl tasksService;
	
	@Mock
	private TasksDao dao;
	
	@Before
	public void setupMocks() {
		MockitoAnnotations.openMocks(this);
	}
	
	@Test
	public void saveTask_shouldDelegateToDao() {
		Task task = new Task();
		task.setDescription("some description");
		task.setStatus(CarePlan.CarePlanActivityStatus.NOTSTARTED);
		task.setPatient(mock(Patient.class));
		
		when(dao.saveTask(task)).thenReturn(task);
		
		Task savedTask = tasksService.saveTask(task);
		
		verify(dao).saveTask(task);
		assertThat(savedTask, is(task));
	}
	
	@Test
	public void getTaskByUuid_shouldDelegateToDao() {
		String uuid = "test-uuid";
		Task task = new Task();
		task.setUuid(uuid);
		
		when(dao.getTaskByUuid(uuid)).thenReturn(task);
		
		Task foundTask = tasksService.getTaskByUuid(uuid);
		
		verify(dao).getTaskByUuid(uuid);
		assertThat(foundTask, is(task));
	}
	
	@Test
    public void getTasksByPatientId_shouldDelegateToDao() {
        Integer patientId = 2;
        List<Task> tasks = new ArrayList<>();

        Task task = new Task();
        task.setDescription("Task 1");
        task.setPatient(mock(Patient.class));
        tasks.add(task);

        when(dao.getTasksByPatientId(patientId)).thenReturn(tasks);

        List<Task> foundTasks = tasksService.getTasksByPatientId(patientId);

        verify(dao).getTasksByPatientId(patientId);
        assertThat(foundTasks.size(), is(1));
    }
}
