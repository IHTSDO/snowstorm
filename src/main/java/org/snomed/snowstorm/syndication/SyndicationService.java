package org.snomed.snowstorm.syndication;

import org.ihtsdo.otf.snomedboot.ReleaseImportException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.core.data.domain.CodeSystem;
import org.snomed.snowstorm.core.data.domain.CodeSystemVersion;
import org.snomed.snowstorm.core.data.services.CodeSystemService;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.snomed.snowstorm.core.rf2.RF2Type;
import org.snomed.snowstorm.core.rf2.rf2import.ImportService;
import org.snomed.snowstorm.syndication.client.SyndicationClient;
import org.snomed.snowstorm.syndication.client.SyndicationFeed;
import org.snomed.snowstorm.syndication.client.SyndicationFeedEntry;
import org.springframework.data.util.Pair;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class SyndicationService {

	private final SyndicationClient syndicationClient;
	private final Queue<InstallationTask> installationQueue;
	private final AtomicBoolean isProcessing;
	private final Map<String, InstallationTask> activeTasks;
	private final ImportService importService;
	private final CodeSystemService codeSystemService;
	private final ExecutorService executorService;
	private final Logger logger = LoggerFactory.getLogger(getClass());

	public SyndicationService(SyndicationClient syndicationClient, ImportService importService, CodeSystemService codeSystemService) {
		this.syndicationClient = syndicationClient;
		this.importService = importService;
		this.codeSystemService = codeSystemService;
		this.executorService = Executors.newFixedThreadPool(1);
		this.installationQueue = new ConcurrentLinkedQueue<>();
		this.isProcessing = new AtomicBoolean(false);
		this.activeTasks = new ConcurrentHashMap<>();
	}

	public List<SyndicationSnomedEdition> getSnomedEditions() throws IOException {
		SyndicationFeed feed = syndicationClient.getFeed();
		Map<String, List<SyndicationFeedEntry>> entryGroups = new HashMap<>();
		feed.getEntries().stream()
				.filter(entry -> entry.getContentItemIdentifier() != null)
				.filter(entry -> entry.getContentItemIdentifier().startsWith("http://snomed.info/sct/"))
				.forEach(entry -> entryGroups.computeIfAbsent(entry.getContentItemIdentifier(), i -> new ArrayList<>()).add(entry));
		List<SyndicationSnomedEdition> snomedEditions = new ArrayList<>();
		SyndicationSnomedEdition internationalEdition = null;
		for (Map.Entry<String, List<SyndicationFeedEntry>> mapEntry : entryGroups.entrySet()) {
			SyndicationSnomedEdition edition = new SyndicationSnomedEdition(mapEntry.getKey());
			List<SyndicationFeedEntry> feedEntries = mapEntry.getValue();
			String titleCleaned = feedEntries.get(0).getTitleCleaned();
			edition.setTitle(titleCleaned);
			String versionUriPrefix = mapEntry.getKey() + "/version/";
			edition.setVersionsAvailable(feedEntries.stream().map(entry -> entry.getContentItemVersion().replace(versionUriPrefix, "")).toList());
			if (titleCleaned.equals("SNOMED CT International Edition")) {
				internationalEdition = edition;
			} else {
				snomedEditions.add(edition);
			}
		}
		snomedEditions.sort(Comparator.comparing(SyndicationSnomedEdition::getTitle));
		if (internationalEdition != null) {
			snomedEditions.add(0, internationalEdition);
		}
		return snomedEditions;
	}

	public String installEdition(String editionId, String version) {
		SecurityContext securityContext = SecurityContextHolder.getContext();
		InstallationTask task = new InstallationTask(editionId, version, securityContext);
		installationQueue.offer(task);
		activeTasks.put(task.getTaskId(), task);
		logger.info("Created installation task {} for edition {} version {}", task.getTaskId(), editionId, version);
		
		// Trigger processing if not already processing
		if (isProcessing.compareAndSet(false, true)) {
			executorService.submit(() -> {
				try {
					processInstallationTasks();
				} finally {
					isProcessing.set(false);
				}
			});
		}
		
		return task.getTaskId();
	}

	public InstallationTask getInstallationTask(String taskId) {
		return activeTasks.get(taskId);
	}

	private void processInstallationTasks() {
		while (!installationQueue.isEmpty()) {
			InstallationTask task = installationQueue.poll();
			if (task != null) {
				processTask(task);
			}
		}
	}

	private void processTask(InstallationTask task) {
		logger.info("Processing installation task {} for edition {} version {}", task.getTaskId(), task.getEditionId(), task.getVersion());
		task.setStatus(InstallationTask.InstallationStatus.IN_PROGRESS);
		
		// Set the SecurityContext from the task
		SecurityContext originalContext = SecurityContextHolder.getContext();
		try {
			SecurityContextHolder.setContext(task.getSecurityContext());
			
			try {
				// Get feed
				SyndicationFeed feed = syndicationClient.getFeed();

				// Construct version URI
				String versionUri = task.getEditionId() + "/version/" + task.getVersion();

				// Find entry
				SyndicationFeedEntry entry = syndicationClient.findEntry(versionUri, feed);
				if (entry == null) {
					throw new ServiceException("No matching syndication entry found for URI: " + versionUri);
				}

				// Get credentials
				Pair<String, String> creds = syndicationClient.getSyndicationCredentials();

				// Download packages - this returns file paths but we need to track categories
				// We'll use downloadPackages which handles dependencies, then find entries for each file
				Set<String> downloadedFiles = syndicationClient.downloadPackages(entry, feed, creds);
				task.getDownloadedFiles().addAll(downloadedFiles);

				// Extract module ID from editionId (e.g., "http://snomed.info/sct/900000000000207008" -> "900000000000207008")
				String moduleId = extractModuleIdFromEditionId(task.getEditionId());
				if (moduleId == null) {
					throw new ServiceException("Unable to extract module ID from edition ID: " + task.getEditionId());
				}

				// Find or create CodeSystem for this edition
				CodeSystem codeSystem = codeSystemService.findByDefaultModule(moduleId);
				if (codeSystem == null) {
					// Use empty version of MAIN as parent
					// The whole Edition will be imported onto the new CodeSystem
					CodeSystemVersion empty2000Version = codeSystemService.getOrCreateEmpty2000Version();

					// Create new CodeSystem
					String shortName = "SNOMEDCT-" + moduleId;
					String branchPath = "MAIN/SNOMEDCT-" + moduleId;
					codeSystem = new CodeSystem(shortName, branchPath);
					codeSystem.setUriModuleId(moduleId);
					codeSystem.setName(entry.getTitleCleaned());
					codeSystem.setDependantVersionEffectiveTime(empty2000Version.getEffectiveDate());
					codeSystem = codeSystemService.createCodeSystem(codeSystem);
					logger.info("Created new CodeSystem {} with branchPath {}", shortName, branchPath);
				} else {
					logger.info("Found existing CodeSystem {} with branchPath {}", codeSystem.getShortName(), codeSystem.getBranchPath());
				}

				String branchPath = codeSystem.getBranchPath();

				// Create import jobs and trigger imports for each downloaded file
				List<String> fileList = new ArrayList<>(downloadedFiles);
				for (int i = 0; i < fileList.size(); i++) {
					String filePath = fileList.get(i);
					File file = new File(filePath);
					if (!file.exists()) {
						logger.warn("Downloaded file does not exist: {}", filePath);
						continue;
					}

					// Only create CodeSystem version on the last file import
					boolean createCodeSystemVersion = (i == fileList.size() - 1);
					importFile(task, file, branchPath, createCodeSystemVersion, filePath);
				}

				task.setStatus(InstallationTask.InstallationStatus.COMPLETED);
				task.setCompletedAt(new Date());
				logger.info("Completed installation task {} for edition {} version {}", task.getTaskId(), task.getEditionId(), task.getVersion());

			} catch (Exception e) {
				logger.error("Failed installation task {} for edition {} version {}", task.getTaskId(), task.getEditionId(), task.getVersion(), e);
				task.setStatus(InstallationTask.InstallationStatus.FAILED);
				task.setErrorMessage(e.getMessage());
				task.setCompletedAt(new Date());
			}
		} finally {
			// Restore original SecurityContext
			SecurityContextHolder.setContext(originalContext);
		}
	}

	private void importFile(InstallationTask task, File file, String branchPath, boolean createCodeSystemVersion, String filePath) throws ReleaseImportException {
		try (FileInputStream inputStream = new FileInputStream(file)) {
			// Create import job using the CodeSystem's branchPath
			String importId = importService.createJob(RF2Type.SNAPSHOT, branchPath, createCodeSystemVersion, false);
			task.getImportJobIds().add(importId);
			logger.info("Created import job {} for file {} on branch {} (createCodeSystemVersion: {})", importId, filePath, branchPath, createCodeSystemVersion);
			importService.importArchive(importId, inputStream);
		} catch (IOException e) {
			logger.error("Failed to create import job for file {}", filePath, e);
			// Continue with other files
		} finally {
			if (!file.delete()) {
				logger.warn("Failed to delete temp SNOMED CT archive file.");
			}
		}
	}

	private String extractModuleIdFromEditionId(String editionId) {
		if (editionId == null || editionId.isEmpty()) {
			return null;
		}
		// Extract module ID from URI like "http://snomed.info/sct/900000000000207008"
		// The module ID is the last segment after the last "/"
		int lastSlashIndex = editionId.lastIndexOf('/');
		if (lastSlashIndex >= 0 && lastSlashIndex < editionId.length() - 1) {
			return editionId.substring(lastSlashIndex + 1);
		}
		return null;
	}

}
