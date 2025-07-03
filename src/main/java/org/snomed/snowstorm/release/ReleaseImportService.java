package org.snomed.snowstorm.release;

import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.domain.Branch;
import org.ihtsdo.otf.resourcemanager.ResourceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.module.storage.ModuleMetadata;
import org.snomed.module.storage.ModuleStorageCoordinator;
import org.snomed.module.storage.ModuleStorageCoordinatorException;
import org.snomed.snowstorm.core.data.domain.CodeSystem;
import org.snomed.snowstorm.core.data.domain.CodeSystemVersion;
import org.snomed.snowstorm.core.data.repositories.CodeSystemRepository;
import org.snomed.snowstorm.core.data.services.*;
import org.snomed.snowstorm.core.data.services.pojo.IntegrityIssueReport;
import org.snomed.snowstorm.core.rf2.RF2Type;
import org.snomed.snowstorm.core.rf2.rf2import.ImportService;
import org.snomed.snowstorm.dailybuild.DailyBuildService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.io.InputStream;
import java.util.*;

@Service
public class ReleaseImportService {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private static final String LOCK_MESSAGE = "Branch locked for new release import.";

    private final BranchService branchService;

    private final SBranchService sBranchService;

    private final IntegrityService integrityService;

    private final DailyBuildService dailyBuildService;

    private final ImportService importService;

    private final ResourceManager resourceManager;

    private final ModuleStorageCoordinator moduleStorageCoordinator;

    private final CodeSystemService codeSystemService;

    private final CodeSystemUpgradeService codeSystemUpgradeService;

    private final CodeSystemRepository codeSystemRepository;


    @Autowired
    public ReleaseImportService(BranchService branchService, SBranchService sBranchService, IntegrityService integrityService, DailyBuildService dailyBuildService, ImportService importService, ReleaseResourceConfig releaseResourceConfig, ResourceLoader resourceLoader, ModuleStorageCoordinator moduleStorageCoordinator, CodeSystemService codeSystemService, CodeSystemUpgradeService codeSystemUpgradeService, CodeSystemRepository codeSystemRepository) {
        this.branchService = branchService;
        this.sBranchService = sBranchService;
        this.integrityService = integrityService;
        this.dailyBuildService = dailyBuildService;
        this.importService = importService;
        this.resourceManager = new ResourceManager(releaseResourceConfig, resourceLoader);
        this.moduleStorageCoordinator = moduleStorageCoordinator;
        this.codeSystemService = codeSystemService;
        this.codeSystemUpgradeService = codeSystemUpgradeService;
        this.codeSystemRepository = codeSystemRepository;
    }

    public void performScheduledImport(CodeSystem codeSystem) throws ServiceException {
        String branchPath = codeSystem.getBranchPath();
        Branch codeSystemBranch = branchService.findBranchOrThrow(branchPath);
        if (codeSystemBranch.isLocked()) {
            logger.info("Scheduled release import is skipped as branch {} is already locked.", branchPath);
            return;
        }
        // check any new release package
        String releaseFilename = getNewReleaseFilenameIfExists(codeSystem);

        // perform rollback and import
        performReleaseSnapshotImport(codeSystem, releaseFilename);
    }

    private void performReleaseSnapshotImport(CodeSystem codeSystem, String releaseFilename) throws ServiceException {
        if (releaseFilename == null) {
            return;
        }
        logger.info("New release package {} found for {} ", releaseFilename, codeSystem.getShortName());
        // Lock branch immediately to stop other instances performing daily build/importing a new release
        branchService.lockBranch(codeSystem.getBranchPath(), LOCK_MESSAGE);
        boolean dailyBuildAvailable = codeSystem.isDailyBuildAvailable();
        try {
            // Disable Daily Build
            codeSystem.setDailyBuildAvailable(false);
            codeSystemRepository.save(codeSystem);

            dailyBuildService.rollbackDailyBuildContent(codeSystem.getShortName()); // Use the method rollbackDailyBuildContent(String) to bypass the security check

            Branch codeSystemBranch = branchService.findBranchOrThrow(codeSystem.getBranchPath());
            long branchHeadTimestamp = codeSystemBranch.getHeadTimestamp();

            // Unlock branch so that the upgrade and release import can be executed
            branchService.unlock(codeSystem.getBranchPath());

            // Upgrade code system if needed
            performCodeSystemUpgradeIfNeeded(codeSystem, releaseFilename, branchHeadTimestamp);

            logger.info("start release import for code system {}", codeSystem.getShortName());
            String importId = importService.createJob(RF2Type.SNAPSHOT, codeSystem.getBranchPath(), true, false);
            try (InputStream releaseStream = resourceManager.readResourceStreamOrNullIfNotExists(codeSystem.getShortName() + "/" + releaseFilename)) {
                importService.importArchive(importId, releaseStream);
            } catch (Exception e) {
                sBranchService.rollbackCommit(codeSystem.getBranchPath(), branchHeadTimestamp);
                throw new ServiceException("Failed to import the new release into code system " + codeSystem.getShortName(), e);
            }
            logger.info("Release import completed for code system {}", codeSystem.getShortName());
        } finally {
            Branch codeSystemBranch = branchService.findBranchOrThrow(codeSystem.getBranchPath());
            if (codeSystemBranch.isLocked()) {
                branchService.unlock(codeSystem.getBranchPath());
            }

            // Rollback daily build config
            codeSystem.setDailyBuildAvailable(dailyBuildAvailable);
            codeSystemRepository.save(codeSystem);
        }
    }

    private void performCodeSystemUpgradeIfNeeded(CodeSystem codeSystem, String releaseFilename, long branchHeadTimestamp) throws ServiceException {
        if (codeSystem.getDependantVersionEffectiveTime() == null) return;
        try {
            Map<String, List<ModuleMetadata>> allReleases = moduleStorageCoordinator.getAllReleases();
            List<ModuleMetadata> allModuleMetadata = new ArrayList<>();
            allReleases.values().forEach(allModuleMetadata::addAll);

            ModuleMetadata moduleMetadata = allModuleMetadata.stream().filter(item -> item.getFilename().equals(releaseFilename)).findFirst().orElse(null);
            if (moduleMetadata != null) {
                List<ModuleMetadata> dependencies = moduleMetadata.getDependencies();
                if (!CollectionUtils.isEmpty(dependencies)) {
                    ModuleMetadata dependency = dependencies.get(0);
                    if (dependency.getEffectiveTime() <= codeSystem.getDependantVersionEffectiveTime()) return;
                    codeSystemUpgradeService.upgrade(codeSystem.getShortName(), dependency.getEffectiveTime(), false); // Use the method upgrade(String,Integer,boolean) to bypass the security check
                    Branch extensionBranch = branchService.findLatest(codeSystem.getShortName());
                    IntegrityIssueReport integrityReport = integrityService.findChangedComponentsWithBadIntegrityNotFixed(extensionBranch);
                    if (!integrityReport.isEmpty()) {
                        sBranchService.rollbackCommit(codeSystem.getBranchPath(), branchHeadTimestamp);
                        throw new ServiceException("Bad integrity found on " + codeSystem.getBranchPath());
                    }

                }
            } else {
                throw new ResourceNotFoundException(String.format("Release file '%s' not found from Module Storage Coordinator", releaseFilename));
            }
        } catch (ModuleStorageCoordinatorException.OperationFailedException |
                 ModuleStorageCoordinatorException.InvalidArgumentsException |
                 ModuleStorageCoordinatorException.ResourceNotFoundException | ServiceException e) {
            throw new ServiceException("Failed to upgrade the code system " + codeSystem.getShortName(), e);
        }
    }

    private String getNewReleaseFilenameIfExists(CodeSystem codeSystem) throws ServiceException {
        Map<Integer, String> effectiveTimeToArchiveFilenameMap = new HashMap<>();
        Set<String> releaseArchiveFilenames = resourceManager.doListFilenames(codeSystem.getShortName());
        logger.debug("Found total release: {} for code system {}", releaseArchiveFilenames.size(), codeSystem.getShortName());
        List<CodeSystemVersion> codeSystemVersions = this.codeSystemService.findAllVersions(codeSystem.getShortName(), true, true);
        if (codeSystemVersions.isEmpty()) {
            throw new ServiceException("No versions found for code system " + codeSystem.getShortName());
        }
        CodeSystemVersion latestCodeSystemVersion = codeSystemVersions.get(codeSystemVersions.size() - 1);
        for (String filename : releaseArchiveFilenames) {
            if (filename != null && filename.startsWith(codeSystem.getShortName() + "/") && filename.endsWith(".zip")) {
                // Strip off file separators
                if (filename.contains("/")) {
                    filename = filename.substring(filename.lastIndexOf("/") + 1);
                }
                // Check the new release effective time after the latest version
                if (isAfterLatestVersion(filename, latestCodeSystemVersion.getEffectiveDate())) {
                    effectiveTimeToArchiveFilenameMap.put(getEffectiveTimeFromFilename(filename), filename);
                }
            }
        }

        // Get the latest release
        if (!effectiveTimeToArchiveFilenameMap.isEmpty()) {
            int mostRecentEffectiveTime = effectiveTimeToArchiveFilenameMap.keySet().stream().sorted(Comparator.reverseOrder()).toList().get(0);
            String mostRecentRelease = effectiveTimeToArchiveFilenameMap.get(mostRecentEffectiveTime);
            if (effectiveTimeToArchiveFilenameMap.size() > 1) {
                logger.info("Found total {} release archives. '{}' will be loaded.", effectiveTimeToArchiveFilenameMap.size(), mostRecentRelease);
            }
            return mostRecentRelease;
        }
        return null;
    }

    private boolean isAfterLatestVersion(String filename, int latestVersionEffectiveTime) {
        int effectiveTime = getEffectiveTimeFromFilename(filename);
        return effectiveTime > latestVersionEffectiveTime;
    }

    private int getEffectiveTimeFromFilename(String filename) {
        String[] split = filename.split("_");
        return Integer.parseInt(split[split.length - 1].substring(0, 8));
    }
}
