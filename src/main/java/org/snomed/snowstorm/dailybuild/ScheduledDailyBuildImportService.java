package org.snomed.snowstorm.dailybuild;


import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.domain.Branch;
import io.kaicode.elasticvc.domain.DomainEntity;
import org.ihtsdo.otf.resourcemanager.ResourceConfiguration;
import org.ihtsdo.otf.resourcemanager.ResourceManager;
import org.ihtsdo.otf.snomedboot.ReleaseImportException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.core.data.domain.CodeSystem;
import org.snomed.snowstorm.core.data.domain.CodeSystemVersion;
import org.snomed.snowstorm.core.data.services.CodeSystemService;
import org.snomed.snowstorm.core.data.services.DomainEntityConfiguration;
import org.snomed.snowstorm.core.rf2.RF2Type;
import org.snomed.snowstorm.core.rf2.rf2import.ImportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
@ConditionalOnProperty(name = "daily-build.delta-import.enabled", havingValue = "true")
public class ScheduledDailyBuildImportService {

	static final String DAILY_BUILD_DATE_FORMAT = "yyyy-MM-dd-HHmmss";
	private static final String LOCK_MESSAGE = "Branch locked for daily build import.";

	@Autowired
	private DailyBuildResourceConfig dailyBuildResourceConfig;

	@Autowired
	private BranchService branchService;

	@Autowired
	private ImportService importService;

	@Autowired
	private ResourceLoader resourceLoader;

	@Autowired
	private CodeSystemService codeSystemService;

	@Autowired
	private ResourcePatternResolver resourcePatternResolver;

	@Autowired
	private DomainEntityConfiguration domainEntityConfiguration;

	private boolean initialised = false;

	private ResourceManager resourceManager;

	private Logger logger = LoggerFactory.getLogger(getClass());

	@PostConstruct
	public void init() {
		initialised = true;
		resourceManager = new ResourceManager(dailyBuildResourceConfig, resourceLoader);
		logger.info("Daily build import is enabled.");
	}

	@Scheduled(fixedDelay = 60_000)
	public void scheduledDailyBuildDeltaImport() {
		if (!initialised) {
			return;
		}

		List<CodeSystem> codeSystems = codeSystemService.findAll().stream()
				.filter(CodeSystem::isDailyBuildAvailable).collect(Collectors.toList());

		for (CodeSystem codeSystem : codeSystems) {
			performScheduledImport(codeSystem);
		}
	}

	private void performScheduledImport(CodeSystem codeSystem) {
		String branchPath = codeSystem.getBranchPath();
		Branch codeSystemBranch = branchService.findBranchOrThrow(branchPath);
		if (codeSystemBranch.isLocked()) {
			return;
		}

		try {
			// Lock branch immediately to stop other instances performing daily build.
			branchService.lockBranch(branchPath, LOCK_MESSAGE);

			try (InputStream dailyBuildSteam = getNewDailyBuildIfExists(codeSystem, codeSystemBranch.getHeadTimestamp())) {
				dailyBuildDeltaImport(codeSystem, dailyBuildSteam);
			} catch (IOException e) {
				logger.error("Failed to import daily build for CodeSystem " + codeSystem.getShortName(), e);
			}
		} finally {
			branchService.unlock(branchPath);
		}
	}

	void dailyBuildDeltaImport(CodeSystem codeSystem, InputStream dailyBuildSteam) {
		if (dailyBuildSteam == null) {
			return;
		}
		logger.info("New daily build found for " + codeSystem.getShortName());

		try {
			// Roll back commits on Code System branch which were made after latest release branch base timepoint.
			CodeSystemVersion latestVersion = codeSystemService.findLatestVersion(codeSystem.getShortName());
			Branch latestReleaseBranch = branchService.findLatest(latestVersion.getBranchPath());
			rollbackCommitsAfterTimepoint(codeSystem.getBranchPath(), latestReleaseBranch.getBase());

			logger.info("Start daily build delta import for code system " +  codeSystem.getShortName());
			String importId = importService.createJob(RF2Type.DELTA, codeSystem.getBranchPath(), false, true);
			importService.importArchive(importId, dailyBuildSteam);
			logger.info("Daily build delta import completed for code system " +  codeSystem.getShortName());

		} catch (ReleaseImportException e) {
			logger.error("Failed to import daily build delta.", e);
		}
	}

	private void rollbackCommitsAfterTimepoint(String path, Date timepoint) {
		try {
			// Find all versions after base timestamp
			Page<Branch> branchPage = branchService.findAllVersionsAfterTimestamp(path, timepoint, Pageable.unpaged());

			logger.info("{} branch commits found to roll back on {} after timepoint {}.", branchPage.getTotalElements(), path, timepoint);

			// Roll back in reverse order (i.e the most recent first)
			List<Branch> rollbackList = branchPage.getContent();
			Collections.reverse(rollbackList);

			List<Class<? extends DomainEntity>> domainTypes = new ArrayList<>(domainEntityConfiguration.getAllDomainEntityTypes());
			for (Branch branchVersion : rollbackList) {
				branchService.rollbackCompletedCommit(branchVersion, domainTypes);
			}
		} catch (Exception e) {
			logger.error("Failed to rollback commits on {} started at {}.", path, timepoint, e);
		} finally {
			branchService.unlock(path);
		}
	}

	InputStream getNewDailyBuildIfExists(CodeSystem codeSystem, long lastImportTimepoint) {
		String deltaDirectoryPath = ResourcePathHelper.getFullPath(dailyBuildResourceConfig, codeSystem.getShortName());
		logger.debug("Daily build resources path '{}'.", deltaDirectoryPath);

		Resource codeSystemDeltaDirectory = resourcePatternResolver.getResource(deltaDirectoryPath);
		if (codeSystemDeltaDirectory.exists()) {
			List<String> archiveFilenames = new ArrayList<>();
			try {
				Resource[] deltaArchives = resourcePatternResolver.getResources(deltaDirectoryPath + "/" + "*.zip");
				for (Resource deltaArchive : deltaArchives) {
					String filename = deltaArchive.getFilename();
					if (filename != null && filename.endsWith(".zip")) {
						// Check the uploaded time after the last import
						if (isAfter(filename, lastImportTimepoint)) {
							archiveFilenames.add(filename);
						}
					}
				}
			} catch (FileNotFoundException e) {
				logger.info("No daily builds found from '{}'.", deltaDirectoryPath);
			} catch (IOException e) {
				logger.error("Failed to fetch delta archives from '{}'.", deltaDirectoryPath, e);
			}

			// Get the most recent build for today
			Collections.sort(archiveFilenames);
			Collections.reverse(archiveFilenames);
			if (!archiveFilenames.isEmpty()) {
				String mostRecentBuild = archiveFilenames.iterator().next();
				if (archiveFilenames.size() > 1) {
					logger.info("Found total {} daily builds. '{}' will be loaded.", archiveFilenames.size(), mostRecentBuild);
				}
				String archivePath = codeSystem.getShortName() + "/" + mostRecentBuild;
				try {
					return resourceManager.readResourceStream(archivePath);
				} catch (IOException e) {
					logger.error("Failed to read resource from '{}'", archivePath, e);
				}
			}
		}
		return null;
	}

	private boolean isAfter(String filename, long timestamp) {
		String dateStr = filename.substring(0, filename.lastIndexOf("."));
		SimpleDateFormat formatter = new SimpleDateFormat(DAILY_BUILD_DATE_FORMAT);
		try {
			if (formatter.parse(dateStr).after(new Date(timestamp))) {
				return true;
			}
		} catch (ParseException e) {
			logger.error("File name contains invalid date format expected '{}' but is '{}'.", DAILY_BUILD_DATE_FORMAT, dateStr);
		}
		return false;
	}

	private static class ResourcePathHelper {

		static String getFullPath(ResourceConfiguration resourceConfiguration, String relativePath) {
			if (resourceConfiguration.isUseCloud()) {
				return "s3://" + resourceConfiguration.getCloud().getBucketName()
						+ "/" + getPathAndRelative(resourceConfiguration.getCloud().getPath(), relativePath);
			} else {
				return getPathAndRelative(resourceConfiguration.getLocal().getPath(), relativePath);
			}
		}
		private static String getPathAndRelative(String path, String relativePath) {
			if (!path.isEmpty() && !path.endsWith("/")) {
				path = path + "/";
			}
			if (relativePath.startsWith("/")) {
				relativePath = relativePath.substring(1);
			}
			return path + relativePath;
		}
	}
}
