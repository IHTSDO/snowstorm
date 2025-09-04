package org.snomed.snowstorm.core.data.services;

import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.api.PathUtil;
import io.kaicode.elasticvc.domain.Branch;
import io.kaicode.elasticvc.domain.Metadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.core.data.domain.CodeSystem;
import org.snomed.snowstorm.core.data.domain.CodeSystemVersion;
import org.snomed.snowstorm.core.data.repositories.CodeSystemRepository;
import org.snomed.snowstorm.core.data.services.pojo.CodeSystemUpgradeJob;
import org.snomed.snowstorm.core.data.services.pojo.IntegrityIssueReport;
import org.snomed.snowstorm.dailybuild.DailyBuildService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

import static org.snomed.snowstorm.core.data.services.BranchMetadataHelper.INTERNAL_METADATA_KEY;
import static org.snomed.snowstorm.core.data.services.BranchMetadataKeys.DEPENDENCY_PACKAGE;
import static org.snomed.snowstorm.core.data.services.BranchMetadataKeys.DEPENDENCY_RELEASE;

@Service
public class CodeSystemUpgradeService {
	private static final long ONE_HOUR_IN_MILLI_SEC = 3600 * 1000;
	private static final long ONE_DAY_IN_MILLI_SEC = 24 * ONE_HOUR_IN_MILLI_SEC;

	private final CodeSystemService codeSystemService;
	private final BranchService branchService;
	private final BranchMergeService branchMergeService;
	private final CodeSystemRepository codeSystemRepository;
	private final DailyBuildService dailyBuildService;
	private final IntegrityService integrityService;
	private final UpgradeInactivationService upgradeInactivationService;
	private final ExecutorService executorService;
	private final ModuleDependencyService moduleDependencyService;
	private final CodeSystemVersionService codeSystemVersionService;

	private static final Map<String, CodeSystemUpgradeJob> upgradeJobMap = new HashMap<>();

	private final Logger logger = LoggerFactory.getLogger(getClass());

	@Autowired
	public CodeSystemUpgradeService(
			CodeSystemService codeSystemService,
			BranchService branchService,
			BranchMergeService branchMergeService,
			CodeSystemRepository codeSystemRepository,
			DailyBuildService dailyBuildService,
			IntegrityService integrityService,
			UpgradeInactivationService upgradeInactivationService,
			ExecutorService executorService,
			ModuleDependencyService moduleDependencyService,
			CodeSystemVersionService codeSystemVersionService) {
		this.codeSystemService = codeSystemService;
		this.branchService = branchService;
		this.branchMergeService = branchMergeService;
		this.codeSystemRepository = codeSystemRepository;
		this.dailyBuildService = dailyBuildService;
		this.integrityService = integrityService;
		this.upgradeInactivationService = upgradeInactivationService;
		this.executorService = executorService;
		this.moduleDependencyService = moduleDependencyService;
		this.codeSystemVersionService = codeSystemVersionService;
		
		Timer timer = new Timer();

		// Schedules the specified task for repeated fixed-delay execution
		timer.schedule(new CodeSystemUpgradeJobReminder(), 0, ONE_HOUR_IN_MILLI_SEC);
	}

	public String findRunningJob(String codeSystemShortname, Integer newDependantVersion) {
		for(String key : upgradeJobMap.keySet()) {
			CodeSystemUpgradeJob job = upgradeJobMap.get(key);
			if (job.getCodeSystemShortname().equalsIgnoreCase(codeSystemShortname)
				&& job.getNewDependantVersion().compareTo(newDependantVersion) == 0
				&& CodeSystemUpgradeJob.UpgradeStatus.RUNNING.equals(job.getStatus())) {
				return key;
			}
		}
		return null;
	}

	public String createJob(String codeSystemShortname, Integer newDependantVersion) {
		String id = UUID.randomUUID().toString();
		upgradeJobMap.put(id, new CodeSystemUpgradeJob(codeSystemShortname, newDependantVersion));
		return id;
	}

	public CodeSystemUpgradeJob getJob(String jobId) {
		return upgradeJobMap.get(jobId);
	}

	public CodeSystemUpgradeJob getUpgradeJobOrThrow(String jobId) {
		CodeSystemUpgradeJob job = getJob(jobId);
		if (job == null) {
			throw new NotFoundException("Upgrade job not found.");
		}
		return job;
	}

	@PreAuthorize("hasPermission('ADMIN', #codeSystem.branchPath) || hasPermission('RELEASE_LEAD', #codeSystem.branchPath)")
	public String upgradeAsync(CodeSystem codeSystem, Integer newDependantVersion, boolean contentAutomations) {
		String runningJobId = findRunningJob(codeSystem.getShortName(), newDependantVersion);
		if (runningJobId != null) {
			return runningJobId;
		}
		final String newJobId = createJob(codeSystem.getShortName(), newDependantVersion);
		final SecurityContext securityContext = SecurityContextHolder.getContext();
		executorService.submit(() -> {
			// Bring user security context into new thread
			SecurityContextHolder.setContext(securityContext);
			try {
				upgrade(newJobId, codeSystem, newDependantVersion, contentAutomations);
			} catch (ServiceException e) {
				logger.error(e.getMessage(), e);
			}
		});
		return newJobId;
	}

	public void upgrade(String codeSystemShortname, Integer newDependantVersion, boolean contentAutomations) throws ServiceException {
		final String newJobId = createJob(codeSystemShortname, newDependantVersion);
		this.upgrade(newJobId, codeSystemService.findOrThrow(codeSystemShortname), newDependantVersion, contentAutomations);
	}

	@PreAuthorize("hasPermission('ADMIN', #codeSystem.branchPath)")
	public synchronized void upgrade(String id, CodeSystem codeSystem, Integer newDependantVersion, boolean contentAutomations) throws  ServiceException {
		CodeSystemUpgradeJob job = null;
		if (id != null) {
			job = getJob(id);
		}
		// preUpgrade checks
		UpgradePreCheckResult result = preUpgradeChecks(codeSystem, newDependantVersion, job);

		// Disable daily build during upgrade
		boolean dailyBuildAvailable = codeSystem.isDailyBuildAvailable();
		if (dailyBuildAvailable) {
			logger.info("Disabling daily build before upgrade.");
			codeSystem.setDailyBuildAvailable(false);
			codeSystemRepository.save(codeSystem);

			// Rollback daily build content
			logger.info("Rolling back any daily build content before upgrade.");
			dailyBuildService.rollbackDailyBuildContent(codeSystem);
		}
		boolean upgradedSuccessfully = false;
		String branchPath = codeSystem.getBranchPath();
		try {
			CodeSystemVersion newParentVersion = result.getNewParentVersion();
			Branch newParentVersionBranch = branchService.findLatest(newParentVersion.getBranchPath());
			Date newParentBaseTimepoint = newParentVersionBranch.getBase();
			logger.info("Running upgrade of {} to {} version {}.", codeSystem, result.getParentCodeSystem(), newDependantVersion);

			String parentPath = result.getParentCodeSystem().getBranchPath();
			CodeSystem parentCodeSystem = result.getParentCodeSystem();
			branchMergeService.rebaseToSpecificTimepointAndRemoveDuplicateContent(parentPath, newParentBaseTimepoint, branchPath, String.format("Upgrading extension to %s@%s.", parentPath, newParentVersion.getVersion()));
			logger.info("Completed rebase of {} to {} version {}.", codeSystem, parentCodeSystem, newDependantVersion);

			if (contentAutomations) {
				logger.info("Running upgrade content automations on {}.", branchPath);
				moduleDependencyService.setTargetEffectiveTime(branchPath, newDependantVersion);
				upgradeInactivationService.findAndUpdateDescriptionsInactivation(codeSystem);
				upgradeInactivationService.findAndUpdateLanguageRefsets(codeSystem);
				upgradeInactivationService.findAndUpdateAdditionalAxioms(codeSystem);
				logger.info("Completed upgrade content automations on {}.", branchPath);
			}

			logger.info("Running post upgrade integrity check on {}", branchPath);
			Branch extensionBranch = branchService.findLatest(branchPath);
			IntegrityIssueReport integrityReport = integrityService.findChangedComponentsWithBadIntegrityNotFixed(extensionBranch);
			logger.info("Completed post upgrade integrity check on {}", branchPath);

			logger.info("Running post upgrade metadata update on {}", branchPath);
			updateBranchMetaData(branchPath, newParentVersion, extensionBranch, integrityReport.isEmpty());
			if (job != null) {
				job.setStatus(CodeSystemUpgradeJob.UpgradeStatus.COMPLETED);
			}

			upgradedSuccessfully = true;
		} catch (Exception e) {
			logger.error("Upgrade on {} failed", branchPath, e);
			if (job != null) {
				job.setStatus(CodeSystemUpgradeJob.UpgradeStatus.FAILED);
				job.setErrorMessage(e.getMessage());
			}
			throw e;
		} finally {
			// Re-enable daily build
			if (dailyBuildAvailable) {
				logger.info("Re-enabling daily build after upgrade.");
				codeSystem.setDailyBuildAvailable(true);
				codeSystemRepository.save(codeSystem);
			}

			logger.info("Upgrade {} on {}", upgradedSuccessfully?"completed":"unsuccessful", branchPath);
		}
	}


	UpgradePreCheckResult preUpgradeChecks(CodeSystem codeSystem, Integer newDependantVersion, CodeSystemUpgradeJob job) {
		String branchPath = codeSystem.getBranchPath();
		String parentPath = PathUtil.getParentPath(branchPath);
		if (parentPath == null) {
			String errorMessage = "The root Code System can not be upgraded.";
			if (job != null) {
				job.setStatus(CodeSystemUpgradeJob.UpgradeStatus.FAILED);
				job.setErrorMessage(errorMessage);
			}
			throw new IllegalArgumentException(errorMessage);
		}
		if (codeSystem.getDependantVersionEffectiveTime() != null && newDependantVersion.compareTo(codeSystem.getDependantVersionEffectiveTime()) <= 0) {
			String errorMessage = "The new dependant version must be after the current dependant version.";
			if (job != null) {
				job.setStatus(CodeSystemUpgradeJob.UpgradeStatus.FAILED);
				job.setErrorMessage(errorMessage);
			}
			throw new IllegalStateException(errorMessage);
		}
		CodeSystem parentCodeSystem = codeSystemService.findOneByBranchPath(parentPath);
		if (parentCodeSystem == null) {
			String errorMessage = String.format("The Code System to be upgraded must be on a branch which is the direct child of another Code System. " +
					"There is no Code System on parent branch '%s'.", parentPath);
			if (job != null) {
				job.setStatus(CodeSystemUpgradeJob.UpgradeStatus.FAILED);
				job.setErrorMessage(errorMessage);
			}
			throw new IllegalStateException(errorMessage);
		}
		CodeSystemVersion newParentVersion = codeSystemService.findVersion(parentCodeSystem.getShortName(), newDependantVersion);
		if (newParentVersion == null) {
			String errorMessage = String.format("Parent Code System %s has no version with effectiveTime '%s'.", parentCodeSystem.getShortName(), newDependantVersion);
			if (job != null) {
				job.setStatus(CodeSystemUpgradeJob.UpgradeStatus.FAILED);
				job.setErrorMessage(errorMessage);
			}
			throw new IllegalArgumentException(errorMessage);
		}
		// Additional dependency checks
		performAdditionalDependencyCheck(codeSystem, newDependantVersion, job, parentCodeSystem);
		return new UpgradePreCheckResult(newParentVersion, parentCodeSystem);
	}

	private void performAdditionalDependencyCheck(CodeSystem codeSystem, Integer newDependantVersion, CodeSystemUpgradeJob job, CodeSystem parentCodeSystem) {
		// Get additional dependencies directly using the existing method
		Set<CodeSystem> dependentCodeSystems = moduleDependencyService.getAllDependentCodeSystems(codeSystem);
		Set<CodeSystem> additionalCodeSystems = dependentCodeSystems.stream().filter(additional -> !additional.equals(parentCodeSystem)).collect(Collectors.toSet());
		
		List<String> missingDeps = new ArrayList<>();
		Integer currentDependantVersion = codeSystem.getDependantVersionEffectiveTime();
		Map<CodeSystem, Set<Integer>> possibleUpgradeVersions = new HashMap<>();

		for (CodeSystem additional : additionalCodeSystems) {
			List<CodeSystemVersion> depVersions = codeSystemService.findAllVersions(additional.getShortName(), true, true);
			depVersions.forEach(codeSystemVersionService::populateDependantVersion);

			depVersions.stream()
					.map(CodeSystemVersion::getDependantVersionEffectiveTime)
					.filter(Objects::nonNull)
					.forEach(depIntVersion -> {
						if (currentDependantVersion == null || depIntVersion > currentDependantVersion) {
							possibleUpgradeVersions.computeIfAbsent(additional, k -> new HashSet<>()).add(depIntVersion);
						}
					});

			boolean foundCompatible = depVersions.stream()
					.map(CodeSystemVersion::getDependantVersionEffectiveTime)
					.anyMatch(depIntVersion -> depIntVersion != null && depIntVersion.equals(newDependantVersion));

			if (!foundCompatible) {
				missingDeps.add(additional.getShortName());
			}
		}

		if (!missingDeps.isEmpty()) {
			// Check if there are any versionsToRecommend
			Set<Integer> versionsToRecommend = findCommonVersions(additionalCodeSystems, possibleUpgradeVersions);
			if (versionsToRecommend.isEmpty()) {
				throwUpgradeBlockedException(newDependantVersion, missingDeps, job);
			} else {
				recommendCompatibleVersion(newDependantVersion, versionsToRecommend, additionalCodeSystems, job);
			}
		}
	}


	Set<Integer> findCommonVersions(Set<CodeSystem> additionalCodeSystems, Map<CodeSystem, Set<Integer>> possibleUpgradeVersions) {
		Set<Integer> common = new HashSet<>();
		if (!possibleUpgradeVersions.keySet().containsAll(additionalCodeSystems)) {
			return common;
		}
		for (Set<Integer> versions : possibleUpgradeVersions.values()) {
			if (common.isEmpty()) {
				common.addAll(versions);
			} else {
				common.retainAll(versions);
			}
			if (common.isEmpty()) {
				break;
			}
		}
		return common;
	}

	private void throwUpgradeBlockedException(Integer newDependantVersion, List<String> missingDependencyCodeSystems, CodeSystemUpgradeJob job) {
		String errorMessage = String.format("No compatible release found for dependent code system %s with the requested International version %s." +
						" Please wait for a compatible release before proceeding.",
				String.join(", ", missingDependencyCodeSystems), newDependantVersion);
		if (job != null) {
			job.setStatus(CodeSystemUpgradeJob.UpgradeStatus.FAILED);
			job.setErrorMessage(errorMessage);
		}
		throw new IllegalStateException(errorMessage);
	}

	private void recommendCompatibleVersion(Integer newDependantVersion, Set<Integer> possibleUpgradeVersions, Set<CodeSystem> additionalCodeSystems, CodeSystemUpgradeJob job) {
		for (Integer possibleVersion : possibleUpgradeVersions) {
			boolean compatible = additionalCodeSystems.stream().allMatch(additional -> {
				List<CodeSystemVersion> depVersions = codeSystemService.findAllVersions(additional.getShortName(), true, true);
				depVersions.forEach(codeSystemVersionService::populateDependantVersion);
				return depVersions.stream()
						.map(CodeSystemVersion::getDependantVersionEffectiveTime)
						.anyMatch(depIntVersion -> depIntVersion != null && depIntVersion.equals(possibleVersion));
			});
			if (compatible) {
				String errorMessage = String.format("Version %s of the International release isn’t compatible with all additional dependencies. " +
						"Try upgrading to %s, which is fully compatible.", newDependantVersion, possibleVersion);
				if (job != null) {
					job.setStatus(CodeSystemUpgradeJob.UpgradeStatus.FAILED);
					job.setErrorMessage(errorMessage);
				}
				throw new IllegalStateException(errorMessage);
			}
		}
	}

	private void updateBranchMetaData(String branchPath, CodeSystemVersion newParentVersion, Branch extensionBranch, boolean isReportEmpty) {
		final Metadata metadata = extensionBranch.getMetadata();

		if (newParentVersion.getReleasePackage() != null) {
			metadata.putString(DEPENDENCY_PACKAGE, newParentVersion.getReleasePackage());
		} else {
			logger.error("No release package is set for version {}", newParentVersion);
		}
		metadata.putString(DEPENDENCY_RELEASE, String.valueOf(newParentVersion.getEffectiveDate()));
		if (!isReportEmpty) {
			logger.warn("Bad integrity found on {}", branchPath);
			metadata.getMapOrCreate(INTERNAL_METADATA_KEY).put(IntegrityService.INTEGRITY_ISSUE_METADATA_KEY, "true");
		} else {
			logger.info("No issues found in the integrity issue report.");
		}

		// update branch metadata
		branchService.updateMetadata(branchPath, metadata);
	}

	class CodeSystemUpgradeJobReminder extends TimerTask {
		public void run() {
			clearExpiredElementsFromMap();
		}
	}

	// Check for element's expired time. If element is > 1 day old then remove it
	private static void clearExpiredElementsFromMap() {

		long currentTimestamp = System.currentTimeMillis();

		Iterator<Entry<String, CodeSystemUpgradeJob>> iterator = CodeSystemUpgradeService.upgradeJobMap.entrySet().iterator();

		while (iterator.hasNext()) {
			Entry<String, CodeSystemUpgradeJob> entry = iterator.next();
			CodeSystemUpgradeJob value = entry.getValue();

			if ((currentTimestamp - value.getCreationTimestamp()) >= ONE_DAY_IN_MILLI_SEC) {
				iterator.remove();
			}
		}
	}

	record UpgradePreCheckResult(CodeSystemVersion newParentVersion, CodeSystem parentCodeSystem) {
    public CodeSystemVersion getNewParentVersion() {
        return newParentVersion;
    }

    public CodeSystem getParentCodeSystem() {
        return parentCodeSystem;
    }
}
}
