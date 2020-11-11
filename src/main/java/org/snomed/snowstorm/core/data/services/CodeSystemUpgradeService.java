package org.snomed.snowstorm.core.data.services;

import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.api.PathUtil;
import io.kaicode.elasticvc.domain.Branch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.core.data.domain.CodeSystem;
import org.snomed.snowstorm.core.data.domain.CodeSystemVersion;
import org.snomed.snowstorm.core.data.repositories.CodeSystemRepository;
import org.snomed.snowstorm.core.data.services.pojo.IntegrityIssueReport;
import org.snomed.snowstorm.dailybuild.DailyBuildService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.snomed.snowstorm.core.data.services.BranchMetadataHelper.INTERNAL_METADATA_KEY;

@Service
public class CodeSystemUpgradeService {

	@Autowired
	private CodeSystemService codeSystemService;

	@Autowired
	private BranchService branchService;

	@Autowired
	private BranchMergeService branchMergeService;

	@Autowired
	private CodeSystemRepository codeSystemRepository;

	@Autowired
	private DailyBuildService dailyBuildService;

	@Autowired
	private IntegrityService integrityService;

	@Autowired
	private UpgradeInactivationService upgradeInactivationService;

	@Autowired
	private BranchMetadataHelper branchMetadataHelper;

	@Value("${snowstorm.rest-api.readonly}")
	private boolean isReadOnly;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	@PreAuthorize("hasPermission('ADMIN', #codeSystem.branchPath)")
	public void upgrade(CodeSystem codeSystem, Integer newDependantVersion, boolean contentAutomations) throws  ServiceException {
		// Pre checks
		String branchPath = codeSystem.getBranchPath();
		String parentPath = PathUtil.getParentPath(branchPath);
		if (parentPath == null) {
			throw new IllegalArgumentException("The root Code System can not be upgraded.");
		}
		CodeSystem parentCodeSystem = codeSystemService.findOneByBranchPath(parentPath);
		if (parentCodeSystem == null) {
			throw new IllegalStateException(String.format("The Code System to be upgraded must be on a branch which is the direct child of another Code System. " +
					"There is no Code System on parent branch '%s'.", parentPath));
		}
		CodeSystemVersion newParentVersion = codeSystemService.findVersion(parentCodeSystem.getShortName(), newDependantVersion);
		if (newParentVersion == null) {
			throw new IllegalArgumentException(String.format("Parent Code System %s has no version with effectiveTime '%s'.", parentCodeSystem.getShortName(), newDependantVersion));
		}
		// Checks complete

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
		try {
			Branch newParentVersionBranch = branchService.findLatest(newParentVersion.getBranchPath());
			Date newParentBaseTimepoint = newParentVersionBranch.getBase();
			logger.info("Running upgrade of {} to {} version {}.", codeSystem, parentCodeSystem, newDependantVersion);
			branchMergeService.rebaseToSpecificTimepointAndRemoveDuplicateContent(parentPath, newParentBaseTimepoint, branchPath, String.format("Upgrading extension to %s@%s.", parentPath, newParentVersion.getVersion()));
			logger.info("Completed upgrade of {} to {} version {}.", codeSystem, parentCodeSystem, newDependantVersion);

			if (contentAutomations) {
				logger.info("Running upgrade content automations.");
				upgradeInactivationService.findAndUpdateDescriptionsInactivation(codeSystem);
				upgradeInactivationService.findAndUpdateLanguageRefsets(codeSystem);
				upgradeInactivationService.findAndUpdateAdditionalAxioms(codeSystem);
				logger.info("Completed upgrade content automations.");
			}

			logger.info("Running integrity check on {}", branchPath);
			Branch extensionBranch = branchService.findLatest(branchPath);
			IntegrityIssueReport integrityReport = integrityService.findChangedComponentsWithBadIntegrity(extensionBranch);
			if (!integrityReport.isEmpty()) {
				logger.warn("Bad integrity found on {}", branchPath);
				Map<String, Object> metaDataExpanded = branchMetadataHelper.expandObjectValues(extensionBranch.getMetadata());
				Map<String, String> integrityIssueMetaData = new HashMap<>();
				integrityIssueMetaData.put(IntegrityService.INTEGRITY_ISSUE_METADATA_KEY, "true");
				if (metaDataExpanded == null) {
					metaDataExpanded = new HashMap<>();
				}
				metaDataExpanded.put(INTERNAL_METADATA_KEY, integrityIssueMetaData);
				branchService.updateMetadata(branchPath, branchMetadataHelper.flattenObjectValues(metaDataExpanded));
			} else {
				logger.info("No issues found in the integrity issue report.");
			}
			logger.info("Completed integrity check on {}", branchPath);

		} finally {
			// Re-enable daily build
			if (dailyBuildAvailable) {
				logger.info("Re-enabling daily build after upgrade.");
				codeSystem.setDailyBuildAvailable(true);
				codeSystemRepository.save(codeSystem);
			}
		}
	}

}
