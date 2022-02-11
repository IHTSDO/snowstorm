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
import org.snomed.snowstorm.core.data.services.SBranchService;
import org.snomed.snowstorm.core.rf2.RF2Type;
import org.snomed.snowstorm.core.rf2.rf2import.ImportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.snomed.snowstorm.core.data.repositories.CodeSystemRepository;

import javax.annotation.PostConstruct;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DailyBuildService {

	private static final String DAILY_BUILD_DATE_FORMAT = "yyyy-MM-dd-HHmmss";
	private static final String LOCK_MESSAGE = "Branch locked for daily build import.";

	@Autowired
	private CodeSystemRepository codeSystemRepository;
	
	@Autowired
	private DailyBuildResourceConfig dailyBuildResourceConfig;

	@Autowired
	private BranchService branchService;

	@Autowired
	private SBranchService sBranchService;

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

	private ResourceManager resourceManager;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	@PostConstruct
	public void init() {
		resourceManager = new ResourceManager(dailyBuildResourceConfig, resourceLoader);
	}

	public boolean hasLatestDailyBuild(String shortName) {
		CodeSystem codeSystem = codeSystemService.findOrThrow(shortName);
		String latestDailyBuild = codeSystem.getLatestDailyBuild();         
		if (latestDailyBuild == null || latestDailyBuild.length() == 0) {
			return false;
		}  
		latestDailyBuild = latestDailyBuild.substring(0, latestDailyBuild.lastIndexOf("-"));
		SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
		return latestDailyBuild.equals(formatter.format(new Date()));
	}

	void performScheduledImport(CodeSystem codeSystem) throws IOException, ReleaseImportException {
		String branchPath = codeSystem.getBranchPath();
		Branch codeSystemBranch = branchService.findBranchOrThrow(branchPath);
		if (codeSystemBranch.isLocked()) {
			logger.info("Scheduled daily build import is skipped as branch {} is already locked.", branchPath);
			return;
		}
		// check any new daily builds
		String dailyBuildFilename = getNewDailyBuildIfExists(codeSystem, codeSystemBranch.getHeadTimestamp());
		// perform rollback and import
		dailyBuildDeltaImport(codeSystem, dailyBuildFilename);
	}

	void dailyBuildDeltaImport(CodeSystem codeSystem, String dailyBuildFilename) throws IOException, ReleaseImportException {
		if (dailyBuildFilename == null) {
			return;
		}
		logger.info("New daily build {} found for {} ", dailyBuildFilename, codeSystem.getShortName());
		// Lock branch immediately to stop other instances performing daily build.
		branchService.lockBranch(codeSystem.getBranchPath(), LOCK_MESSAGE);

		rollbackDailyBuildContent(codeSystem);

		logger.info("start daily build delta import for code system {}", codeSystem.getShortName());
		String importId = importService.createJob(RF2Type.DELTA, codeSystem.getBranchPath(), false, true);
		try (InputStream dailyBuildStream = resourceManager.readResourceStreamOrNullIfNotExists(codeSystem.getShortName() + "/" + dailyBuildFilename)) {
			// Unlock branch so that delta import can be executed
			branchService.unlock(codeSystem.getBranchPath());
			importService.importArchive(importId, dailyBuildStream);
		}
		logger.info("Daily build delta import completed for code system {}", codeSystem.getShortName());
		codeSystem.setLatestDailyBuild(dailyBuildFilename.substring(0, dailyBuildFilename.lastIndexOf(".")));
		codeSystemRepository.save(codeSystem);
	}

	@PreAuthorize("hasPermission('ADMIN', #codeSystem.branchPath)")
	public void rollbackDailyBuildContent(CodeSystem codeSystem) {
		// Roll back commits on Code System branch if commit starts after latest release commit
		// AND new base timestamp does not match one of the parent codeSystem release branch timePoints.

		CodeSystemVersion latestVersion = codeSystemService.findLatestImportedVersion(codeSystem.getShortName());
		Date releaseCommitHead = null;
		if (latestVersion != null) {
			// Release branch base timestamp is the same as the head timestamp on the MAIN branch for the commit containing the release.
			releaseCommitHead = branchService.findLatest(latestVersion.getBranchPath()).getBase();
			logger.info("Latest version found at {} on {}.", releaseCommitHead.getTime(), codeSystem.getBranchPath());
		}

		List<Branch> commitsToRollback = new ArrayList<>();
		String branchPath = codeSystem.getBranchPath();

		// Find commit of latest release and any after that
		Date baseForReleaseCommit = null;
		Date baseForUpgradeCommit = null;
		if (releaseCommitHead != null) {
			List<Branch> lightCommits = sBranchService.findAllVersionsAfterOrEqualToTimestampAsLightCommits(branchPath, releaseCommitHead, Pageable.unpaged()).getContent();
			logger.info("{} commits found on {} since latest release.", lightCommits.size() - 1, branchPath);
			for (Branch lightCommit : lightCommits) {
				// Don't rollback the release commit
				if (lightCommit.getHead().equals(releaseCommitHead)) {
					baseForReleaseCommit = lightCommit.getBase();
					commitsToRollback.clear();
					logger.info("Release commit found, base version {} recorded.", baseForReleaseCommit.getTime());
					continue;
				}
				// Exclude additional upgrade commits after code system version
				if (baseForUpgradeCommit != null && !lightCommit.getBase().equals(baseForUpgradeCommit)) {
					logger.info("Commit {} has base {} which is different to the last upgrade commit. " +
									"This upgrade commit will not be rolled back nor will any commits before this.",
							lightCommit.getHeadTimestamp(), lightCommit.getBaseTimestamp());
					baseForUpgradeCommit = lightCommit.getBase();
					commitsToRollback.clear();
					continue;
				// Exclude first upgrade commit after code system version
				} else if (baseForUpgradeCommit == null && baseForReleaseCommit != null && !lightCommit.getBase().equals(baseForReleaseCommit)) {
					logger.info("Commit {} has base {} which is different to the release commit. " +
									"This upgrade commit will not be rolled back nor will any commits before this.",
							lightCommit.getHeadTimestamp(), lightCommit.getBaseTimestamp());
					baseForUpgradeCommit = lightCommit.getBase();
					commitsToRollback.clear();
					continue;
				} else {
					logger.info("Commit {} does not have a new base ({}) so will be rolled back.",
							lightCommit.getHeadTimestamp(), lightCommit.getBaseTimestamp());
				}
				Branch commit = branchService.findAtTimepointOrThrow(lightCommit.getPath(), lightCommit.getHead());
				commitsToRollback.add(commit);
			}
		} else {
			// If never released roll back all versions
			logger.info("No release versions found on {}, all commit apart from branch creation will be rolled back.", codeSystem.getBranchPath());
			List<Branch> allCommits = branchService.findAllVersions(branchPath, Pageable.unpaged()).stream().sorted(Comparator.comparing(Branch::getStart)).collect(Collectors.toList());
			allCommits.remove(0);// Don't rollback the commit which creates the branch.
			commitsToRollback = allCommits;
		}

		// Roll back in reverse order (i.e the most recent first)
		Collections.reverse(commitsToRollback);

		rollbackCommits(branchPath, commitsToRollback);
		codeSystem.setLatestDailyBuild("");
		codeSystemRepository.save(codeSystem);
	}

	private void rollbackCommits(String path, List<Branch> rollbackList) {
		logger.info("{} branch commits found to roll back on {}.", rollbackList.size(), path);
		List<Class<? extends DomainEntity>> domainTypes = new ArrayList<>(domainEntityConfiguration.getAllDomainEntityTypes());
		for (Branch branchVersion : rollbackList) {
			branchService.rollbackCompletedCommit(branchVersion, domainTypes);
		}
	}

	private String getNewDailyBuildIfExists(CodeSystem codeSystem, long lastImportTimePoint) {
		String deltaDirectoryPath = ResourcePathHelper.getFullPath(dailyBuildResourceConfig, codeSystem.getShortName());
		logger.debug("Daily build resources path '{}'.", deltaDirectoryPath);
		List<String> archiveFilenames = new ArrayList<>();
		try {
			Resource[] deltaArchives = resourcePatternResolver.getResources(deltaDirectoryPath + "/" + "*.zip");
			logger.debug("Found total builds {} from {}",  deltaArchives.length, deltaDirectoryPath);
			for (Resource deltaArchive : deltaArchives) {
				String filename = deltaArchive.getFilename();
				if (filename != null && filename.endsWith(".zip")) {
					// Strip off file separators
					if (filename.contains("/")) {
						filename = filename.substring(filename.lastIndexOf("/") + 1);
					}
					// Check the uploaded time after the last import
					if (isAfterAndNotFuture(filename, lastImportTimePoint)) {
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
			return mostRecentBuild;
		}
		return null;
	}

	private boolean isAfterAndNotFuture(String filename, long timestamp) {
		String dateStr = filename.substring(0, filename.lastIndexOf("."));
		SimpleDateFormat formatter = new SimpleDateFormat(DAILY_BUILD_DATE_FORMAT);
		try {
			Date buildDate = formatter.parse(dateStr);
			if (buildDate.before(new Date()) && buildDate.after(new Date(timestamp))) {
				return true;
			}
		} catch (ParseException e) {
			logger.error("File name contains invalid date format expected '{}' but is '{}'.", DAILY_BUILD_DATE_FORMAT, dateStr);
		}
		return false;
	}

	void setResourceManager(ResourceManager resourceManager) {
		this.resourceManager = resourceManager;
	}

	private static class ResourcePathHelper {

		static String getFullPath(ResourceConfiguration resourceConfiguration, String relativePath) {
			if (resourceConfiguration.isUseCloud()) {
				return "s3://" + resourceConfiguration.getCloud().getBucketName()
						+ "/" + getPathAndRelative(resourceConfiguration.getCloud().getPath(), relativePath);
			} else {
				return "file:" + getPathAndRelative(resourceConfiguration.getLocal().getPath(), relativePath);
			}
		}
		private static String getPathAndRelative(String path, String relativePath) {
			if (!path.isEmpty() && !path.endsWith("/")) {
				path += "/";
			}
			if (relativePath.startsWith("/")) {
				relativePath = relativePath.substring(1);
			}
			return path + relativePath;
		}
	}
}
