package org.snomed.snowstorm.release;

import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.domain.Branch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.module.storage.Channel;
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
import org.snomed.snowstorm.core.rf2.rf2import.RF2ImportConfiguration;
import org.snomed.snowstorm.dailybuild.DailyBuildService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

@Service
public class ReleaseImportService {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private static final String LOCK_MESSAGE = "Branch locked for new release import.";

    private final BranchService branchService;

    private final SBranchService sBranchService;

    private final IntegrityService integrityService;

    private final DailyBuildService dailyBuildService;

    private final ImportService importService;

    private final ModuleStorageCoordinator moduleStorageCoordinator;

    private final CodeSystemService codeSystemService;

    private final CodeSystemUpgradeService codeSystemUpgradeService;

    private final CodeSystemRepository codeSystemRepository;

    @Autowired
    public ReleaseImportService(BranchService branchService, SBranchService sBranchService, IntegrityService integrityService,
                                DailyBuildService dailyBuildService, ImportService importService, ModuleStorageCoordinator moduleStorageCoordinator,
                                CodeSystemService codeSystemService, CodeSystemUpgradeService codeSystemUpgradeService, CodeSystemRepository codeSystemRepository) {
        this.branchService = branchService;
        this.sBranchService = sBranchService;
        this.integrityService = integrityService;
        this.dailyBuildService = dailyBuildService;
        this.importService = importService;
        this.moduleStorageCoordinator = moduleStorageCoordinator;
        this.codeSystemService = codeSystemService;
        this.codeSystemUpgradeService = codeSystemUpgradeService;
        this.codeSystemRepository = codeSystemRepository;
    }

    public void performScheduledImport(CodeSystem codeSystem) {
        logger.debug("Finding RF2 for auto import for {}", codeSystem.getShortName());
        String branchPath = codeSystem.getBranchPath();
        Branch codeSystemBranch = branchService.findBranchOrThrow(branchPath);
        if (codeSystemBranch.isLocked()) {
            logger.info("Auto import skipped - branch {} is already locked.", branchPath);
            return;
        }

        try {
            ModuleMetadata moduleMetadata = getLatestUnimportedReleaseFromMSC(codeSystem);
            if (moduleMetadata == null) {
                logger.debug("No RF2 found for auto import for {}.", codeSystem.getShortName());
                return;
            }

            performReleaseSnapshotImport(moduleMetadata, codeSystem, isInternalRelease(moduleMetadata));
        } catch (Exception e) {
            logger.error("Auto import failed for code system {}", codeSystem.getShortName(), e);
        }
    }

    private ModuleMetadata getLatestUnimportedReleaseFromMSC(CodeSystem codeSystem) {
        // Find all by CodeSystem
        List<ModuleMetadata> releases = getAllReleasesFromMSC(codeSystem);

        // Identify latest version
        List<CodeSystemVersion> versions = codeSystemService.findAllVersions(codeSystem.getShortName(), true, true);
        int latestVersionEffectiveTime = versions.isEmpty() ? 0 : versions.getLast().getEffectiveDate();

        // Filter to most recent & available for import
        return releases.stream()
                .filter(isPublished())
                .filter(hasBrowserChannelAvailable())
                .filter(isNewerThan(latestVersionEffectiveTime))
                .max(Comparator.comparingInt(ModuleMetadata::getEffectiveTime))
                .orElse(null);
    }

    private List<ModuleMetadata> getAllReleasesFromMSC(CodeSystem codeSystem) {
        try {
            String mscCodeSystem = codeSystem.getShortCode() != null ? codeSystem.getShortCode() : "INT";
            List<ModuleMetadata> allReleases = moduleStorageCoordinator.getAllReleases(mscCodeSystem);
            return allReleases != null ? allReleases : new ArrayList<>();
        } catch (ModuleStorageCoordinatorException.ResourceNotFoundException e) {
            return new ArrayList<>();
        } catch (ModuleStorageCoordinatorException e) {
            logger.error("Failed to list releases from MSC for code system {}", codeSystem.getShortName(), e);
            return new ArrayList<>();
        }
    }

    private Predicate<ModuleMetadata> isPublished() {
        return m -> Boolean.TRUE.equals(m.getPublished());
    }

    private Predicate<ModuleMetadata> hasBrowserChannelAvailable() {
        return m -> m.getChannel(Channel.Name.BROWSER)
                .map(ch -> ch.isAvailable() && isKnownVisibility(ch.getPropertyValueAsStringNullable(Channel.Property.Visibility.NAME)))
                .orElse(false);
    }

    private boolean isKnownVisibility(String visibility) {
        return Channel.Property.Visibility.PUBLIC.equals(visibility) || Channel.Property.Visibility.PRIVATE.equals(visibility);
    }

    private Predicate<ModuleMetadata> isNewerThan(int latestVersionEffectiveTime) {
        return m -> m.getEffectiveTime() != null && m.getEffectiveTime() > latestVersionEffectiveTime;
    }

    private boolean isInternalRelease(ModuleMetadata release) {
        Channel browserChannel = release.getChannelNullable(Channel.Name.BROWSER);
        String visibility = browserChannel.getPropertyValueAsStringNullable(Channel.Property.Visibility.NAME);

        return !Channel.Property.Visibility.PUBLIC.equals(visibility);
    }

    private void performReleaseSnapshotImport(ModuleMetadata release, CodeSystem codeSystem, boolean internalRelease) throws ServiceException {
        logger.info("Importing release package {} for code system {} ({})", release.getFilename(), codeSystem.getShortName(), internalRelease ? "internal" : "public");
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
            performCodeSystemUpgradeIfNeeded(codeSystem, release, branchHeadTimestamp);

            RF2ImportConfiguration importConfiguration = new RF2ImportConfiguration(RF2Type.SNAPSHOT, codeSystem.getBranchPath());
            importConfiguration.setCreateCodeSystemVersion(true);
            importConfiguration.setInternalRelease(internalRelease);
            importConfiguration.setClearEffectiveTimes(false);

            String importId = importService.createJob(importConfiguration);
            try {
                logger.info("Downloading release package {} for code system {}", release.getFilename(), codeSystem.getShortName());
                moduleStorageCoordinator.addFileLocally(release);
                try (InputStream releaseStream = new FileInputStream(release.getFile())) {
                    importService.importArchive(importId, releaseStream);
                }
            } catch (Exception e) {
                sBranchService.rollbackCommit(codeSystem.getBranchPath(), branchHeadTimestamp);
                throw new ServiceException("Failed to import the new release into code system " + codeSystem.getShortName(), e);
            }
            logger.info("Release import completed for code system {} ({})", codeSystem.getShortName(), release.getFilename());
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

    private void performCodeSystemUpgradeIfNeeded(CodeSystem codeSystem, ModuleMetadata release, long branchHeadTimestamp) throws ServiceException {
        if (codeSystem.getDependantVersionEffectiveTime() == null) {
            return;
        }

        List<ModuleMetadata> dependencies = release.getDependencies();
        if (CollectionUtils.isEmpty(dependencies)) {
            return;
        }

        try {
            ModuleMetadata dependency = dependencies.getFirst();
            if (dependency.getEffectiveTime() <= codeSystem.getDependantVersionEffectiveTime()) {
                logger.debug("Skipping upgrade for code system {} - dependency effectiveTime {} is not newer than current {}", codeSystem.getShortName(), dependency.getEffectiveTime(), codeSystem.getDependantVersionEffectiveTime());
                return;
            }

            logger.info("Upgrading code system {} to dependency version {}", codeSystem.getShortName(), dependency.getEffectiveTime());
            codeSystemUpgradeService.upgrade(codeSystem.getShortName(), dependency.getEffectiveTime(), false); // Use the method upgrade(String,Integer,boolean) to bypass the security check
            Branch extensionBranch = branchService.findLatest(codeSystem.getBranchPath());
            IntegrityIssueReport integrityReport = integrityService.findChangedComponentsWithBadIntegrityNotFixed(extensionBranch);
            if (!integrityReport.isEmpty()) {
                sBranchService.rollbackCommit(codeSystem.getBranchPath(), branchHeadTimestamp);
                throw new ServiceException("Bad integrity found on " + codeSystem.getBranchPath());
            }
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException("Failed to upgrade the code system " + codeSystem.getShortName(), e);
        }
    }
}
