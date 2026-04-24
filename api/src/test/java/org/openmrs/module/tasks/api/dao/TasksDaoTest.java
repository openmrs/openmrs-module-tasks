/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.tasks.api.dao;

import org.hl7.fhir.r4.model.CarePlan;
import org.junit.Test;
import org.openmrs.Patient;
import org.openmrs.api.PatientService;
import org.openmrs.api.ProviderService;
import org.openmrs.api.context.Context;
import org.openmrs.module.tasks.Task;
import org.openmrs.test.BaseModuleContextSensitiveTest;
import org.springframework.beans.factory.annotation.Autowired;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import java.util.List;
import java.util.Properties;

/**
 * It is an integration test (extends BaseModuleContextSensitiveTest), which verifies DAO methods
 * against the in-memory H2 database. The database is initially loaded with data from
 * standardTestDataset.xml in openmrs-api. All test methods are executed in transactions, which are
 * rolled back by the end of each test method.
 */
public class TasksDaoTest extends BaseModuleContextSensitiveTest {
	
	@Override
	public Properties getRuntimeProperties() {
		Properties props = super.getRuntimeProperties();
		// Exclude FHIR2 module to avoid cacheInterceptor dependency issues in tests
		props.setProperty("module.allow_web_admin", "false");
		return props;
	}
	
	@Autowired
	TasksDao dao;
	
	@Autowired
	PatientService patientService;
	
	@Autowired
	ProviderService providerService;
	
	@Test
	public void saveTask_onExistingEntity_shouldUpdateInPlace() {
		Task task = new Task();
		task.setDescription("initial");
		task.setStatus(CarePlan.CarePlanActivityStatus.NOTSTARTED);
		task.setKind(CarePlan.CarePlanActivityKind.APPOINTMENT);
		task.setPatient(patientService.getPatient(2));
		dao.saveTask(task);

		Context.flushSession();
		Integer originalId = task.getId();
		String originalUuid = task.getUuid();
		assertThat(originalId, is(notNullValue()));

		Task reloaded = dao.getTaskByUuid(originalUuid);
		reloaded.setDescription("updated");
		reloaded.setStatus(CarePlan.CarePlanActivityStatus.INPROGRESS);
		dao.saveTask(reloaded);

		Context.flushSession();
		Context.clearSession();

		Task afterUpdate = dao.getTaskByUuid(originalUuid);
		assertThat(afterUpdate.getId(), is(originalId));
		assertThat(afterUpdate.getDescription(), is("updated"));
		assertThat(afterUpdate.getStatus(), is(CarePlan.CarePlanActivityStatus.INPROGRESS));
	}

	@Test
	public void deleteTask_shouldRemoveRowFromDb() {
		Task task = new Task();
		task.setDescription("to be deleted");
		task.setStatus(CarePlan.CarePlanActivityStatus.NOTSTARTED);
		task.setKind(CarePlan.CarePlanActivityKind.APPOINTMENT);
		task.setPatient(patientService.getPatient(2));
		dao.saveTask(task);
		Context.flushSession();
		String uuid = task.getUuid();
		assertThat(dao.getTaskByUuid(uuid), is(notNullValue()));

		dao.deleteTask(task);
		Context.flushSession();
		Context.clearSession();

		assertThat(dao.getTaskByUuid(uuid), is(nullValue()));
	}

	@Test
	public void saveTask_shouldSaveAllPropertiesInDb() {
		//Given
		Task task = new Task();
		task.setDescription("some description");
		task.setStatus(CarePlan.CarePlanActivityStatus.NOTSTARTED);
		task.setKind(CarePlan.CarePlanActivityKind.APPOINTMENT);
		Patient patient = patientService.getPatient(2);
		task.setPatient(patient);
		task.setAssignee(providerService.getProvider(1));
		task.setAssigneeProviderRoleId(1);
		
		//When
		dao.saveTask(task);
		
		//Let's clean up the cache to be sure getTaskByUuid fetches from DB and not from cache
		Context.flushSession();
		Context.clearSession();
		
		//Then
		Task savedTask = dao.getTaskByUuid(task.getUuid());
		
		assertThat(savedTask, hasProperty("uuid", is(task.getUuid())));
		assertThat(savedTask, hasProperty("patient", is(task.getPatient())));
		assertThat(savedTask, hasProperty("description", is(task.getDescription())));
		assertThat(savedTask, hasProperty("status", is(task.getStatus())));
		assertThat(savedTask, hasProperty("kind", is(task.getKind())));
		assertThat(savedTask, hasProperty("assignee", is(task.getAssignee())));
		assertThat(savedTask, hasProperty("assigneeProviderRoleId", is(task.getAssigneeProviderRoleId())));
	}
	
	@Test
	public void getTasksByPatientId_shouldReturnTasksForPatient() {
		//Given
		Patient patient = patientService.getPatient(2);
		
		Task task1 = new Task();
		task1.setDescription("Task 1");
		task1.setStatus(CarePlan.CarePlanActivityStatus.NOTSTARTED);
		task1.setKind(CarePlan.CarePlanActivityKind.APPOINTMENT);
		task1.setPatient(patient);
		task1.setAssigneeProviderRoleId(1);
		dao.saveTask(task1);
		
		Task task2 = new Task();
		task2.setDescription("Task 2");
		task2.setStatus(CarePlan.CarePlanActivityStatus.INPROGRESS);
		task2.setKind(CarePlan.CarePlanActivityKind.MEDICATIONREQUEST);
		task2.setPatient(patient);
		dao.saveTask(task2);
		
		Context.flushSession();
		Context.clearSession();
		
		//When
		List<Task> tasks = dao.getTasksByPatientId(patient.getId());
		
		//Then
		assertThat(tasks.size(), is(2));
		assertThat(tasks, hasItems(hasProperty("description", is("Task 1")), hasProperty("description", is("Task 2"))));
		assertThat(tasks, hasItem(hasProperty("assigneeProviderRoleId", is(1))));
	}
	
	@Test
	public void getTasksByPatientId_shouldExcludeVoidedTasksByDefault() {
		Patient patient = patientService.getPatient(2);
		
		Task active = new Task();
		active.setDescription("Active Task");
		active.setStatus(CarePlan.CarePlanActivityStatus.NOTSTARTED);
		active.setKind(CarePlan.CarePlanActivityKind.APPOINTMENT);
		active.setPatient(patient);
		active.setVoided(false);
		dao.saveTask(active);
		
		Task voided = new Task();
		voided.setDescription("Voided Task");
		voided.setStatus(CarePlan.CarePlanActivityStatus.CANCELLED);
		voided.setKind(CarePlan.CarePlanActivityKind.APPOINTMENT);
		voided.setPatient(patient);
		voided.setVoided(true);
		voided.setDateVoided(new java.util.Date());
		dao.saveTask(voided);
		
		Context.flushSession();
		Context.clearSession();
		
		List<Task> tasks = dao.getTasksByPatientId(patient.getId());
		
		assertThat(tasks.size(), is(1));
		assertThat(tasks, hasItem(hasProperty("description", is("Active Task"))));
		assertThat(tasks, not(hasItem(hasProperty("description", is("Voided Task")))));
	}
	
	@Test
	public void getTasksByPatientId_withIncludeVoidedFalse_shouldExcludeVoidedTasks() {
		Patient patient = patientService.getPatient(2);
		
		Task active = new Task();
		active.setDescription("Active Task");
		active.setStatus(CarePlan.CarePlanActivityStatus.NOTSTARTED);
		active.setKind(CarePlan.CarePlanActivityKind.APPOINTMENT);
		active.setPatient(patient);
		active.setVoided(false);
		dao.saveTask(active);
		
		Task voided = new Task();
		voided.setDescription("Voided Task");
		voided.setStatus(CarePlan.CarePlanActivityStatus.CANCELLED);
		voided.setKind(CarePlan.CarePlanActivityKind.APPOINTMENT);
		voided.setPatient(patient);
		voided.setVoided(true);
		voided.setDateVoided(new java.util.Date());
		dao.saveTask(voided);
		
		Context.flushSession();
		Context.clearSession();
		
		List<Task> tasks = dao.getTasksByPatientId(patient.getId(), false);
		
		assertThat(tasks.size(), is(1));
		assertThat(tasks, hasItem(hasProperty("description", is("Active Task"))));
	}
	
	@Test
	public void getTasksByPatientId_withIncludeVoidedTrue_shouldReturnVoidedAndNonVoidedTasks() {
		Patient patient = patientService.getPatient(2);
		
		Task active = new Task();
		active.setDescription("Active Task");
		active.setStatus(CarePlan.CarePlanActivityStatus.NOTSTARTED);
		active.setKind(CarePlan.CarePlanActivityKind.APPOINTMENT);
		active.setPatient(patient);
		active.setVoided(false);
		dao.saveTask(active);
		
		Task voided = new Task();
		voided.setDescription("Voided Task");
		voided.setStatus(CarePlan.CarePlanActivityStatus.CANCELLED);
		voided.setKind(CarePlan.CarePlanActivityKind.APPOINTMENT);
		voided.setPatient(patient);
		voided.setVoided(true);
		voided.setDateVoided(new java.util.Date());
		dao.saveTask(voided);
		
		Context.flushSession();
		Context.clearSession();
		
		List<Task> tasks = dao.getTasksByPatientId(patient.getId(), true);
		
		assertThat(tasks.size(), is(2));
		assertThat(tasks,
		    hasItems(hasProperty("description", is("Active Task")), hasProperty("description", is("Voided Task"))));
		assertThat(tasks, hasItem(hasProperty("voided", is(true))));
		assertThat(tasks, hasItem(hasProperty("voided", is(false))));
	}
	
	@Test
	public void getTasksByPatientId_forPatientWithNoTasks_shouldReturnEmptyList() {
		Patient patient = patientService.getPatient(2);
		
		List<Task> tasks = dao.getTasksByPatientId(patient.getId());
		
		assertThat(tasks, is(empty()));
	}
	
	@Test
	public void getTasksByPatientId_forNonExistentPatientId_shouldReturnEmptyList() {
		List<Task> tasks = dao.getTasksByPatientId(Integer.MAX_VALUE);
		
		assertThat(tasks, is(empty()));
	}
	
	@Test
	public void getTasksByPatientId_shouldReturnTasksOrderedByDateCreatedDescending() {
		Patient patient = patientService.getPatient(2);

		Task older = new Task();
		older.setDescription("older task");
		older.setStatus(CarePlan.CarePlanActivityStatus.NOTSTARTED);
		older.setKind(CarePlan.CarePlanActivityKind.APPOINTMENT);
		older.setPatient(patient);
		older.setDateCreated(new java.util.Date(1000000L));
		dao.saveTask(older);

		Task middle = new Task();
		middle.setDescription("middle task");
		middle.setStatus(CarePlan.CarePlanActivityStatus.NOTSTARTED);
		middle.setKind(CarePlan.CarePlanActivityKind.APPOINTMENT);
		middle.setPatient(patient);
		middle.setDateCreated(new java.util.Date(2000000L));
		dao.saveTask(middle);

		Task newer = new Task();
		newer.setDescription("newer task");
		newer.setStatus(CarePlan.CarePlanActivityStatus.NOTSTARTED);
		newer.setKind(CarePlan.CarePlanActivityKind.APPOINTMENT);
		newer.setPatient(patient);
		newer.setDateCreated(new java.util.Date(3000000L));
		dao.saveTask(newer);

		Context.flushSession();
		Context.clearSession();

		List<Task> tasks = dao.getTasksByPatientId(patient.getId());

		assertThat(tasks.size(), is(3));
		assertThat(tasks.get(0).getDescription(), is("newer task"));
		assertThat(tasks.get(1).getDescription(), is("middle task"));
		assertThat(tasks.get(2).getDescription(), is("older task"));
	}
}
