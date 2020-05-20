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
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

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
	private  IntegrityService integrityService;

	@Autowired
	private InactivationUpgradeService inactivationUpgradeService;

	@Autowired
	private BranchMetadataHelper branchMetadataHelper;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public void upgrade(String shortName, Integer newDependantVersion) throws  ServiceException {
		// Pre checks
		CodeSystem codeSystem = codeSystemService.find(shortName);
		if (codeSystem == null) {
			throw new NotFoundException(String.format("Code System with short name '%s' does not exist.", shortName));
		}
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

			logger.info("Running inactivation update");
			inactivationUpgradeService.findAndUpdateInactivationIndicators(codeSystem);
			logger.info("Completed inactivation update ");
			Branch extensionBranch = branchService.findLatest(branchPath);
			IntegrityIssueReport integrityReport = integrityService.findChangedComponentsWithBadIntegrity(extensionBranch);
			if (!integrityReport.isEmpty()) {
				Map<String, String> metaData = extensionBranch.getMetadata();
				Map<String, Object> metaDataExpanded = metaData == null ? new HashMap<>() : branchMetadataHelper.expandObjectValues(metaData);
				Map<String, String> integrityIssueMetaData = new HashMap<>();
				integrityIssueMetaData.put(IntegrityService.INTEGRITY_ISSUE_METADATA_KEY, "true");
				metaDataExpanded.put(IntegrityService.INTERNAL_METADATA_KEY, integrityIssueMetaData);
				branchService.updateMetadata(branchPath, branchMetadataHelper.flattenObjectValues(metaDataExpanded));
			}
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
