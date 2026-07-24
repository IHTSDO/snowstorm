package org.snomed.snowstorm.syndication;

import org.ihtsdo.otf.snomedboot.ReleaseImportException;
import org.jetbrains.annotations.NotNull;
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
import org.snomed.snowstorm.syndication.client.SyndicationLink;
import org.springframework.data.util.Pair;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class SyndicationService {

	private static final long STANDARD_RF2_IMPORT_DURATION_MS = 50L * 60 * 1000;
	private static final long STANDARD_RF2_PACKAGE_BYTES = SyndicationClient.DEFAULT_RF2_PACKAGE_LENGTH_BYTES;
	private static final long MIN_RF2_IMPORT_DURATION_MS = 30_000L;
	public static final String VERSION = "/version/";

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
			String versionUriPrefix = mapEntry.getKey() + VERSION;
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

	public List<SyndicationDerivativeOption> listRefsetDerivatives(String editionId, String editionSelectedVersion) throws IOException {
		if (editionId == null || editionId.isBlank() || editionSelectedVersion == null || !editionSelectedVersion.matches("\\d{8}")) {
			throw new IllegalArgumentException("editionId and an 8-digit yyyyMMdd version are required");
		}
		int editionDate = Integer.parseInt(editionSelectedVersion);
		SyndicationFeed feed = syndicationClient.getFeed();
		List<SyndicationDerivativeOption> options = new ArrayList<>();
		for (SyndicationFeedEntry entry : feed.getEntries()) {
			if (entry.getTitle() == null || !entry.getTitle().toLowerCase(Locale.ROOT).contains("refset")) {
				continue;
			}
			if (entry.getCategory() == null || !SyndicationClient.acceptablePackageTypes.contains(entry.getCategory().getTerm())) {
				continue;
			}
			if (entry.getZipLink() == null) {
				continue;
			}
			Optional<Integer> derivativeDate = versionDateFromContentItemVersion(entry.getContentItemVersion());
			if (derivativeDate.isEmpty() || derivativeDate.get() > editionDate) {
				continue;
			}
			options.add(new SyndicationDerivativeOption(
					identifierForRefsetEntry(entry),
					entry.getContentItemVersion(),
					displayTitle(entry),
					derivativeDate.get()));
		}
		options.sort(Comparator.comparing(SyndicationDerivativeOption::getContentItemIdentifier, String.CASE_INSENSITIVE_ORDER)
				.thenComparing(SyndicationDerivativeOption::getVersionDate, Comparator.reverseOrder()));
		return options;
	}

	private static String identifierForRefsetEntry(SyndicationFeedEntry entry) {
		String id = entry.getContentItemIdentifier();
		if (id != null && !id.isBlank()) {
			return id;
		}
		String versionUri = entry.getContentItemVersion();
		if (versionUri == null) {
			return "";
		}
		int idx = versionUri.lastIndexOf(VERSION);
		if (idx <= 0) {
			return versionUri;
		}
		return versionUri.substring(0, idx);
	}

	private static String displayTitle(SyndicationFeedEntry entry) {
		String title = entry.getTitle();
		int dash = title.indexOf('-');
		if (dash > 0) {
			return title.substring(0, dash).trim();
		}
		return title.trim();
	}

	static Optional<Integer> versionDateFromContentItemVersion(String contentItemVersion) {
		if (contentItemVersion == null) {
			return Optional.empty();
		}
		int idx = contentItemVersion.lastIndexOf(VERSION);
		if (idx < 0) {
			return Optional.empty();
		}
		String suffix = contentItemVersion.substring(idx + VERSION.length());
		if (suffix.length() != 8 || !suffix.chars().allMatch(Character::isDigit)) {
			return Optional.empty();
		}
		return Optional.of(Integer.parseInt(suffix));
	}

	public boolean isConfigCredentialsConfigured() {
		return syndicationClient.isConfigCredentialsConfigured();
	}

	public String installEdition(String editionId, String version, List<String> derivativeContentItemVersions, String username,
			String password) {
		SecurityContext securityContext = SecurityContextHolder.getContext();
		InstallationTask task = new InstallationTask(editionId, version, derivativeContentItemVersions, username, password,
				securityContext);
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

	public List<InstallationTask> getActiveInstallationTasks() {
		return activeTasks.values().stream()
				.filter(t -> t.getStatus() == InstallationTask.InstallationStatus.PENDING
						|| t.getStatus() == InstallationTask.InstallationStatus.IN_PROGRESS)
				.sorted(Comparator.comparing(InstallationTask::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
				.toList();
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
				String versionUri = task.getEditionId() + VERSION + task.getVersion();

				// Find entry
				SyndicationFeedEntry entry = syndicationClient.findEntry(versionUri, feed);
				if (entry == null) {
					throw new ServiceException("No matching syndication entry found for URI: " + versionUri);
				}

				logger.info("Installation task {}: syndication entry matched {} ({}) — resolving credentials, then building package list.",
						task.getTaskId(), entry.getContentItemVersion(), entry.getTitleCleaned());

				// Resolve credentials from config or this install request only
				Pair<String, String> creds = syndicationClient.resolveCredentials(StringUtils.hasText(task.getUsername()) ? Pair.of(task.getUsername(), task.getPassword()) : null);
				logger.info("Installation task {}: credentials step finished (HTTP Basic for package downloads: {}).",
						task.getTaskId(), creds != null);

				int editionEffectiveDate = Integer.parseInt(task.getVersion());
				validateDerivativeSelections(feed, task.getDerivativeContentItemVersions(), editionEffectiveDate);
				logger.info("Installation task {}: derivative selections OK ({} optional refset package(s)).",
						task.getTaskId(), task.getDerivativeContentItemVersions().size());

				Set<String> consumedVersionUris = new HashSet<>();
				List<InstallationPackageProgress> packageSlots = new ArrayList<>();
				List<Pair<SyndicationFeedEntry, SyndicationLink>> allOrdered = new ArrayList<>();

				List<Pair<SyndicationFeedEntry, SyndicationLink>> mainOrdered = syndicationClient.collectOrderedPackages(entry, feed, consumedVersionUris);
				for (Pair<SyndicationFeedEntry, SyndicationLink> pair : mainOrdered) {
					packageSlots.add(progressSlot(pair.getFirst(), pair.getSecond()));
					allOrdered.add(pair);
				}
				for (String derivativeUri : task.getDerivativeContentItemVersions()) {
					SyndicationFeedEntry derivativeEntry = syndicationClient.findEntry(derivativeUri, feed);
					if (derivativeEntry == null) {
						throw new ServiceException("No matching syndication entry found for derivative URI: " + derivativeUri);
					}
					List<Pair<SyndicationFeedEntry, SyndicationLink>> derivOrdered = syndicationClient.collectOrderedPackages(derivativeEntry, feed, consumedVersionUris, false);
					for (Pair<SyndicationFeedEntry, SyndicationLink> pair : derivOrdered) {
						packageSlots.add(progressSlot(pair.getFirst(), pair.getSecond()));
						allOrdered.add(pair);
					}
				}
				logger.info("Installation task {}: {} package(s) queued for download/import (edition + dependencies + selected refsets).",
						task.getTaskId(), allOrdered.size());
				task.replacePackageProgress(packageSlots);
				List<String> orderedFiles = new ArrayList<>(syndicationClient.downloadOrderedPackageList(allOrdered, creds, packageSlots));
				task.getDownloadedFiles().addAll(orderedFiles);

				// Extract module ID from editionId (e.g., "http://snomed.info/sct/900000000000207008" -> "900000000000207008")
				String moduleId = extractModuleIdFromEditionId(task.getEditionId());
				if (moduleId == null) {
					throw new ServiceException("Unable to extract module ID from edition ID: " + task.getEditionId());
				}

				// Find or create CodeSystem for this edition
				CodeSystem codeSystem = codeSystemService.findByUriModule(moduleId);
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

				for (int i = 0; i < orderedFiles.size(); i++) {
					String filePath = orderedFiles.get(i);
					File file = new File(filePath);
					InstallationPackageProgress pkg = i < packageSlots.size() ? packageSlots.get(i) : null;
					if (!file.exists()) {
						throw new ServiceException("Downloaded file does not exist: " + filePath);
					}
					long est = estimatedImportMillis(pkg != null ? pkg.getDeclaredSizeBytes() : STANDARD_RF2_PACKAGE_BYTES);
					if (pkg != null) {
						pkg.beginImportEstimate(est);
					}
					importFile(task, file, branchPath, filePath, pkg);
				}

				task.startVersioningPhase();
				codeSystemService.createVersion(codeSystem, editionEffectiveDate,
						String.format("%s syndication import %s", codeSystem.getShortName(), task.getVersion()));

				task.setStatus(InstallationTask.InstallationStatus.COMPLETED);
				task.setCompletedAt(new Date());
				logger.info("Completed installation task {} for edition {} version {}", task.getTaskId(), task.getEditionId(), task.getVersion());

			} catch (Exception e) {
				logger.error("Failed installation task {} for edition {} version {}", task.getTaskId(), task.getEditionId(), task.getVersion(), e);
				task.setStatus(InstallationTask.InstallationStatus.FAILED);
				task.setErrorMessage(e.getMessage());
				task.setCompletedAt(new Date());
			} finally {
				cleanupRemainingDownloadedFiles(task);
			}
		} finally {
			// Restore original SecurityContext
			SecurityContextHolder.setContext(originalContext);
		}
	}

	private void cleanupRemainingDownloadedFiles(InstallationTask task) {
		for (String filePath : task.getDownloadedFiles()) {
			try {
				Files.deleteIfExists(Path.of(filePath));
			} catch (IOException e) {
				logger.warn("Failed to delete temp syndication archive {}", filePath, e);
			}
		}
	}

	private void importFile(InstallationTask task, File file, String branchPath, String filePath, InstallationPackageProgress pkg)
			throws ReleaseImportException, IOException {
		try (FileInputStream inputStream = new FileInputStream(file)) {
			String importId = importService.createJob(RF2Type.SNAPSHOT, branchPath, false, false);
			task.getImportJobIds().add(importId);
			logger.info("Created import job {} for file {} on branch {}", importId, filePath, branchPath);
			importService.importArchive(importId, inputStream);
			if (pkg != null) {
				pkg.markImportComplete();
			}
		} finally {
			try {
				Files.delete(file.toPath());
			} catch (IOException deleteException) {
				logger.warn("Failed to delete temp SNOMED CT archive file.", deleteException);
			}
		}
	}

	private static long estimatedImportMillis(long declaredSizeBytes) {
		long size = declaredSizeBytes > 0 ? declaredSizeBytes : STANDARD_RF2_PACKAGE_BYTES;
		long scaled = (long) (STANDARD_RF2_IMPORT_DURATION_MS * (size / (double) STANDARD_RF2_PACKAGE_BYTES));
		return Math.max(MIN_RF2_IMPORT_DURATION_MS, scaled);
	}

	private InstallationPackageProgress progressSlot(SyndicationFeedEntry entry, SyndicationLink link) {
		long bytes = SyndicationClient.parseDeclaredPackageBytes(link.getLength());
		String title = progressSlotTitle(entry);
		String ver = entry.getContentItemVersion() != null ? entry.getContentItemVersion() : "";
		return new InstallationPackageProgress(ver, title, bytes);
	}

	private static String progressSlotTitle(SyndicationFeedEntry entry) {
		try {
			return entry.getTitleCleaned();
		} catch (Exception e) {
			return entry.getTitle() != null ? entry.getTitle() : "";
		}
	}

	private void validateDerivativeSelections(SyndicationFeed feed, List<String> derivativeUris, int editionEffectiveDate)
			throws ServiceException {
		if (derivativeUris == null || derivativeUris.isEmpty()) {
			return;
		}
		for (String uri : derivativeUris) {
			SyndicationFeedEntry matching = getSyndicationFeedEntry(feed, uri);
			if (matching.getCategory() == null || !SyndicationClient.acceptablePackageTypes.contains(matching.getCategory().getTerm())) {
				throw new ServiceException("Unacceptable package type for derivative: " + uri);
			}
			Optional<Integer> derivativeDate = versionDateFromContentItemVersion(uri);
			if (derivativeDate.isEmpty() || derivativeDate.get() > editionEffectiveDate) {
				throw new ServiceException("Derivative version date is after the selected edition: " + uri);
			}
		}
	}

	private static @NotNull SyndicationFeedEntry getSyndicationFeedEntry(SyndicationFeed feed, String uri) throws ServiceException {
		SyndicationFeedEntry matching = null;
		for (SyndicationFeedEntry candidate : feed.getEntries()) {
			if (uri.equals(candidate.getContentItemVersion())) {
				matching = candidate;
				break;
			}
		}
		if (matching == null) {
			throw new ServiceException("Derivative not found in syndication feed: " + uri);
		}
		if (matching.getTitle() == null || !matching.getTitle().toLowerCase(Locale.ROOT).contains("refset")) {
			throw new ServiceException("Not a refset derivative package: " + uri);
		}
		return matching;
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
