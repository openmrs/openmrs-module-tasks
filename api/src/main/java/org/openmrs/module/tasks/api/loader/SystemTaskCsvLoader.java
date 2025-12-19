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

import org.apache.commons.lang3.StringUtils;
import org.openmrs.ProviderRole;
import org.openmrs.api.AdministrationService;
import org.openmrs.api.ProviderService;
import org.openmrs.api.context.Context;
import org.openmrs.module.tasks.Priority;
import org.openmrs.module.tasks.SystemTask;
import org.openmrs.module.tasks.api.TasksService;
import org.openmrs.util.OpenmrsUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Loads SystemTask entities from CSV files in the configuration/systemtasks/ directory. Uses
 * checksum-based change detection to skip unchanged files. CSV Format:
 * Uuid,Name,Title,Description,Priority,Default Assignee Role,Rationale
 * <ul>
 * <li>Uuid - Required. Unique identifier for the system task.</li>
 * <li>Name - Required. Machine-readable identifier (maps to PlanDefinition.name).</li>
 * <li>Title - Required. Human-readable display name (maps to PlanDefinition.title and
 * action.title).</li>
 * <li>Description - Optional. Description of the task.</li>
 * <li>Priority - Optional. HIGH, MEDIUM, or LOW.</li>
 * <li>Default Assignee Role - Optional. UUID or name of the provider role.</li>
 * <li>Rationale - Optional. Reason for the task (maps to action.reason).</li>
 * </ul>
 */
public class SystemTaskCsvLoader {
	
	private static final Logger log = LoggerFactory.getLogger(SystemTaskCsvLoader.class);
	
	private static final String SYSTEMTASKS_DIR = "systemtasks";
	
	private static final String CHECKSUM_GLOBAL_PROPERTY_PREFIX = "tasks.systemtask.checksum.";
	
	/**
	 * Loads all system task CSV files from the configuration directory. Files that have not changed
	 * (based on checksum) are skipped.
	 */
	public void loadSystemTasksFromCsvFiles() {
		File configDir = OpenmrsUtil.getDirectoryInApplicationDataDirectory("configuration");
		File systemTasksDir = new File(configDir, SYSTEMTASKS_DIR);

		if (!systemTasksDir.exists() || !systemTasksDir.isDirectory()) {
			log.info("System tasks directory does not exist: {}", systemTasksDir.getAbsolutePath());
			return;
		}

		File[] csvFiles = systemTasksDir.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".csv"));
		if (csvFiles == null || csvFiles.length == 0) {
			log.info("No CSV files found in {}", systemTasksDir.getAbsolutePath());
			return;
		}

		for (File csvFile : csvFiles) {
			try {
				loadCsvFile(csvFile);
			}
			catch (Exception e) {
				log.error("Error loading system tasks from file: {}", csvFile.getName(), e);
			}
		}
	}
	
	/**
	 * Loads system tasks from a single CSV file if it has changed.
	 * 
	 * @param csvFile the CSV file to load
	 */
	private void loadCsvFile(File csvFile) throws IOException {
		String currentChecksum = computeFileChecksum(csvFile);
		String storedChecksum = getStoredChecksum(csvFile.getName());
		
		if (currentChecksum.equals(storedChecksum)) {
			log.debug("Skipping unchanged file: {}", csvFile.getName());
			return;
		}
		
		log.info("Loading system tasks from: {}", csvFile.getName());
		
		List<SystemTask> systemTasks = parseCsvFile(csvFile);
		TasksService tasksService = Context.getService(TasksService.class);
		
		int created = 0;
		int updated = 0;
		
		for (SystemTask systemTask : systemTasks) {
			SystemTask existing = tasksService.getSystemTaskByUuid(systemTask.getUuid());
			if (existing != null) {
				// Update existing system task
				existing.setName(systemTask.getName());
				existing.setTitle(systemTask.getTitle());
				existing.setDescription(systemTask.getDescription());
				existing.setPriority(systemTask.getPriority());
				existing.setDefaultAssigneeProviderRoleId(systemTask.getDefaultAssigneeProviderRoleId());
				existing.setRationale(systemTask.getRationale());
				// Unretire if it was retired
				if (Boolean.TRUE.equals(existing.getRetired())) {
					existing.setRetired(false);
					existing.setRetireReason(null);
					existing.setRetiredBy(null);
					existing.setDateRetired(null);
				}
				tasksService.saveSystemTask(existing);
				log.debug("Updated system task: {}", existing.getName());
				updated++;
			} else {
				// Create new system task
				tasksService.saveSystemTask(systemTask);
				log.debug("Created system task: {}", systemTask.getName());
				created++;
			}
		}
		
		// Store the new checksum
		storeChecksum(csvFile.getName(), currentChecksum);
		
		log.info("Successfully loaded system tasks from {}: {} created, {} updated", csvFile.getName(), created, updated);
	}
	
	/**
	 * Parses a CSV file and returns a list of SystemTask entities.
	 * 
	 * @param csvFile the CSV file to parse
	 * @return list of SystemTask entities
	 */
	private List<SystemTask> parseCsvFile(File csvFile) throws IOException {
		List<SystemTask> systemTasks = new ArrayList<>();

		try (BufferedReader reader = new BufferedReader(
		        new InputStreamReader(new FileInputStream(csvFile), StandardCharsets.UTF_8))) {

			String headerLine = reader.readLine();
			if (headerLine == null) {
				return systemTasks;
			}

			Map<String, Integer> columnIndices = parseHeader(headerLine);

			String line;
			int lineNumber = 1;
			while ((line = reader.readLine()) != null) {
				lineNumber++;
				if (StringUtils.isBlank(line)) {
					continue;
				}

				try {
					SystemTask systemTask = parseLine(line, columnIndices);
					if (systemTask != null) {
						systemTasks.add(systemTask);
					}
				}
				catch (Exception e) {
					log.warn("Error parsing line {} in {}: {}", lineNumber, csvFile.getName(), e.getMessage());
				}
			}
		}

		return systemTasks;
	}
	
	/**
	 * Parses the CSV header line and returns a map of column names to indices.
	 */
	private Map<String, Integer> parseHeader(String headerLine) {
		Map<String, Integer> columnIndices = new HashMap<>();
		String[] headers = splitCsvLine(headerLine);

		for (int i = 0; i < headers.length; i++) {
			String header = headers[i].trim().toLowerCase(Locale.ROOT);
			columnIndices.put(header, i);
		}

		return columnIndices;
	}
	
	/**
	 * Parses a single CSV line into a SystemTask entity.
	 */
	private SystemTask parseLine(String line, Map<String, Integer> columnIndices) {
		String[] values = splitCsvLine(line);
		
		String uuid = getColumnValue(values, columnIndices, "uuid");
		String name = getColumnValue(values, columnIndices, "name");
		String title = getColumnValue(values, columnIndices, "title");
		
		if (StringUtils.isBlank(uuid) || StringUtils.isBlank(name) || StringUtils.isBlank(title)) {
			log.warn("Skipping line with missing uuid, name, or title");
			return null;
		}
		
		SystemTask systemTask = new SystemTask();
		systemTask.setUuid(uuid);
		systemTask.setName(name);
		systemTask.setTitle(title);
		systemTask.setDescription(getColumnValue(values, columnIndices, "description"));
		systemTask.setRationale(getColumnValue(values, columnIndices, "rationale"));
		
		String priorityStr = getColumnValue(values, columnIndices, "priority");
		if (StringUtils.isNotBlank(priorityStr)) {
			try {
				systemTask.setPriority(Priority.valueOf(priorityStr.toUpperCase(Locale.ROOT)));
			}
			catch (IllegalArgumentException e) {
				log.warn("Invalid priority value: {}", priorityStr);
			}
		}
		
		String assigneeRole = getColumnValue(values, columnIndices, "default assignee role");
		if (StringUtils.isNotBlank(assigneeRole)) {
			Integer providerRoleId = resolveProviderRoleId(assigneeRole);
			systemTask.setDefaultAssigneeProviderRoleId(providerRoleId);
		}
		
		return systemTask;
	}
	
	/**
	 * Gets a column value from the parsed values array.
	 */
	private String getColumnValue(String[] values, Map<String, Integer> columnIndices, String columnName) {
		Integer index = columnIndices.get(columnName.toLowerCase(Locale.ROOT));
		if (index == null || index >= values.length) {
			return null;
		}
		String value = values[index].trim();
		// Remove surrounding quotes if present
		if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
			value = value.substring(1, value.length() - 1);
		}
		return StringUtils.isBlank(value) ? null : value;
	}
	
	/**
	 * Splits a CSV line into values, handling quoted fields.
	 */
	private String[] splitCsvLine(String line) {
		List<String> values = new ArrayList<>();
		StringBuilder current = new StringBuilder();
		boolean inQuotes = false;

		for (int i = 0; i < line.length(); i++) {
			char c = line.charAt(i);

			if (c == '"') {
				if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
					// Escaped quote
					current.append('"');
					i++;
				} else {
					inQuotes = !inQuotes;
				}
			} else if (c == ',' && !inQuotes) {
				values.add(current.toString());
				current = new StringBuilder();
			} else {
				current.append(c);
			}
		}
		values.add(current.toString());

		return values.toArray(new String[0]);
	}
	
	/**
	 * Resolves a provider role ID from either a UUID or a name.
	 * 
	 * @param roleReference the UUID or name of the provider role
	 * @return the provider role ID, or null if not found
	 */
	private Integer resolveProviderRoleId(String roleReference) {
		if (StringUtils.isBlank(roleReference)) {
			return null;
		}
		
		ProviderService providerService = Context.getProviderService();
		
		// Check if it looks like a UUID (contains hyphens and is 36 characters)
		if (roleReference.contains("-") && roleReference.length() == 36) {
			ProviderRole role = providerService.getProviderRoleByUuid(roleReference);
			if (role != null) {
				return role.getProviderRoleId();
			}
		}
		
		// Try to find by name using reflection to call getAllProviderRoles if available
		try {
			java.lang.reflect.Method method = providerService.getClass().getMethod("getAllProviderRoles");
			@SuppressWarnings("unchecked")
			List<ProviderRole> allRoles = (List<ProviderRole>) method.invoke(providerService);
			for (ProviderRole role : allRoles) {
				if (role.getName() != null && role.getName().equalsIgnoreCase(roleReference)) {
					return role.getProviderRoleId();
				}
			}
		}
		catch (Exception e) {
			log.debug("getAllProviderRoles not available, name-based lookup skipped for: {}", roleReference);
		}
		
		log.warn("Could not find provider role: {}", roleReference);
		return null;
	}
	
	/**
	 * Computes the MD5 checksum of a file.
	 */
	private String computeFileChecksum(File file) throws IOException {
		try {
			MessageDigest md = MessageDigest.getInstance("MD5");
			try (FileInputStream fis = new FileInputStream(file)) {
				byte[] buffer = new byte[8192];
				int bytesRead;
				while ((bytesRead = fis.read(buffer)) != -1) {
					md.update(buffer, 0, bytesRead);
				}
			}
			byte[] digest = md.digest();
			StringBuilder sb = new StringBuilder();
			for (byte b : digest) {
				sb.append(String.format("%02x", b));
			}
			return sb.toString();
		}
		catch (NoSuchAlgorithmException e) {
			throw new IOException("MD5 algorithm not available", e);
		}
	}
	
	/**
	 * Gets the stored checksum for a file from global properties.
	 */
	private String getStoredChecksum(String fileName) {
		AdministrationService adminService = Context.getAdministrationService();
		String propertyName = CHECKSUM_GLOBAL_PROPERTY_PREFIX + fileName;
		return adminService.getGlobalProperty(propertyName, "");
	}
	
	/**
	 * Stores the checksum for a file in global properties.
	 */
	private void storeChecksum(String fileName, String checksum) {
		AdministrationService adminService = Context.getAdministrationService();
		String propertyName = CHECKSUM_GLOBAL_PROPERTY_PREFIX + fileName;
		adminService.setGlobalProperty(propertyName, checksum);
	}
}
