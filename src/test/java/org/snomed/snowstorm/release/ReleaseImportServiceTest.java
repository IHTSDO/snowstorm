package org.snomed.snowstorm.release;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import io.kaicode.elasticvc.api.BranchService;
import org.snomed.module.storage.Channel;
import org.snomed.module.storage.ModuleMetadata;
import org.snomed.module.storage.ModuleStorageCoordinator;
import org.snomed.otf.snomedboot.testutil.ZipUtil;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.TestConfig;
import org.snomed.snowstorm.core.data.domain.CodeSystem;
import org.snomed.snowstorm.core.data.domain.CodeSystemVersion;
import org.snomed.snowstorm.core.data.services.CodeSystemService;
import org.snomed.snowstorm.core.data.services.CodeSystemUpgradeService;
import org.snomed.snowstorm.core.rf2.RF2Type;
import org.snomed.snowstorm.core.rf2.rf2import.ImportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.File;
import java.io.FileInputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = TestConfig.class)
class ReleaseImportServiceTest extends AbstractTest {

    @Autowired
    private ReleaseImportService releaseImportService;

    @Autowired
    private ImportService importService;

    @Autowired
    private CodeSystemService codeSystemService;

    @MockitoBean
    private ModuleStorageCoordinator moduleStorageCoordinator;

    @MockitoBean
    private CodeSystemUpgradeService codeSystemUpgradeService;

    @Autowired
    private BranchService branchService;

    @Test
    void testPerformScheduledImport() throws Exception {
        // given
        codeSystemService.createCodeSystem(new CodeSystem("SNOMEDCT", "MAIN"));
        givenRelease("INT", "900000000000207008", 20250701, "SnomedCT_InternationalRF2_PRODUCTION_20250701T120000Z.zip");
        CodeSystem codeSystem = codeSystemService.find("SNOMEDCT");

        // when
        releaseImportService.performScheduledImport(codeSystem);

        // then
        List<CodeSystemVersion> codeSystemVersions = this.codeSystemService.findAllVersions(codeSystem.getShortName(), true, true);
        assertEquals(1, codeSystemVersions.size());
        assertEquals("2025-07-01", codeSystemVersions.get(0).getVersion());
        assertFalse(codeSystemVersions.get(0).isInternalRelease());
    }

    @Test
    void testPerformScheduledImport_notImportedWhenNotPublished() throws Exception {
        // given
        codeSystemService.createCodeSystem(new CodeSystem("SNOMEDCT", "MAIN"));
        givenReleaseNotPublished("INT", "900000000000207008", 20250701, "SnomedCT_InternationalRF2_PRODUCTION_20250701T120000Z.zip");
        CodeSystem codeSystem = codeSystemService.find("SNOMEDCT");

        // when
        releaseImportService.performScheduledImport(codeSystem);

        // then
        assertTrue(codeSystemService.findAllVersions(codeSystem.getShortName(), true, true).isEmpty());
    }

    @Test
    void testPerformScheduledImport_notImportedWhenNoBrowserChannel() throws Exception {
        // given
        codeSystemService.createCodeSystem(new CodeSystem("SNOMEDCT", "MAIN"));
        givenReleaseWithNoChannel("INT", "900000000000207008", 20250701, "SnomedCT_InternationalRF2_PRODUCTION_20250701T120000Z.zip");
        CodeSystem codeSystem = codeSystemService.find("SNOMEDCT");

        // when
        releaseImportService.performScheduledImport(codeSystem);

        // then
        assertTrue(codeSystemService.findAllVersions(codeSystem.getShortName(), true, true).isEmpty());
    }

    @Test
    void testPerformScheduledImport_notImportedWhenBrowserChannelNotYetAvailable() throws Exception {
        // given
        codeSystemService.createCodeSystem(new CodeSystem("SNOMEDCT", "MAIN"));
        String tomorrow = LocalDate.now().plusDays(1).format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        givenReleaseWithFutureAvailability("INT", "900000000000207008", 20250701, "SnomedCT_InternationalRF2_PRODUCTION_20250701T120000Z.zip", tomorrow);
        CodeSystem codeSystem = codeSystemService.find("SNOMEDCT");

        // when
        releaseImportService.performScheduledImport(codeSystem);

        // then
        assertTrue(codeSystemService.findAllVersions(codeSystem.getShortName(), true, true).isEmpty());
    }

    @Test
    void testPerformScheduledImport_notImportedWhenUnknownVisibility() throws Exception {
        // given
        codeSystemService.createCodeSystem(new CodeSystem("SNOMEDCT", "MAIN"));
        givenReleaseWithUnknownVisibility("INT", "900000000000207008", 20250701, "SnomedCT_InternationalRF2_PRODUCTION_20250701T120000Z.zip");
        CodeSystem codeSystem = codeSystemService.find("SNOMEDCT");

        // when
        releaseImportService.performScheduledImport(codeSystem);

        // then
        assertTrue(codeSystemService.findAllVersions(codeSystem.getShortName(), true, true).isEmpty());
    }

    @Test
    void testPerformScheduledImport_notImportedWhenNoVisibilityProperty() throws Exception {
        // given
        codeSystemService.createCodeSystem(new CodeSystem("SNOMEDCT", "MAIN"));
        givenReleaseWithNoVisibilityProperty("INT", "900000000000207008", 20250701, "SnomedCT_InternationalRF2_PRODUCTION_20250701T120000Z.zip");
        CodeSystem codeSystem = codeSystemService.find("SNOMEDCT");

        // when
        releaseImportService.performScheduledImport(codeSystem);

        // then
        assertTrue(codeSystemService.findAllVersions(codeSystem.getShortName(), true, true).isEmpty());
    }

    @Test
    void testPerformScheduledImport_importedWhenPrivateVisibility() throws Exception {
        // given
        codeSystemService.createCodeSystem(new CodeSystem("SNOMEDCT", "MAIN"));
        givenReleaseWithPrivateVisibility("INT", "900000000000207008", 20250701, "SnomedCT_InternationalRF2_PRODUCTION_20250701T120000Z.zip");
        CodeSystem codeSystem = codeSystemService.find("SNOMEDCT");

        // when
        releaseImportService.performScheduledImport(codeSystem);

        // then
        List<CodeSystemVersion> codeSystemVersions = this.codeSystemService.findAllVersions(codeSystem.getShortName(), true, true);
        assertEquals(1, codeSystemVersions.size());
        assertEquals("2025-07-01", codeSystemVersions.get(0).getVersion());
        assertTrue(codeSystemVersions.get(0).isInternalRelease());
    }

    @Test
    void testPerformScheduledImport_extensionUpgradesAndImports() throws Exception {
        // given
        givenBaseline("SNOMEDCT", "MAIN", "SnomedCT_MiniRF2_Base_snapshot");
        givenBaseline("SNOMEDCT-XX", "MAIN/SNOMEDCT-XX", "SnomedCT_MiniRF2_XX_snapshot_20260102");
        givenExtensionRelease("XX", "11000001101", 20260202, 20260201);
        CodeSystem codeSystem = codeSystemService.find("SNOMEDCT-XX");

        // when
        releaseImportService.performScheduledImport(codeSystem);

        // then
        verify(codeSystemUpgradeService).upgrade("SNOMEDCT-XX", 20260201, false);
        List<CodeSystemVersion> codeSystemVersions = codeSystemService.findAllVersions(codeSystem.getShortName(), true, true);
        assertEquals(2, codeSystemVersions.size());
        assertEquals("2026-01-02", codeSystemVersions.get(0).getVersion());
        assertEquals("2026-02-02", codeSystemVersions.get(1).getVersion());
    }

    @Test
    void testPerformScheduledImport_notImportedWhenAlreadyAtSameVersion() throws Exception {
        // given
        codeSystemService.createCodeSystem(new CodeSystem("SNOMEDCT", "MAIN"));
        givenRelease("INT", "900000000000207008", 20250701, "SnomedCT_InternationalRF2_PRODUCTION_20250701T120000Z.zip");
        CodeSystem codeSystem = codeSystemService.find("SNOMEDCT");
        releaseImportService.performScheduledImport(codeSystem);
        assertEquals(1, codeSystemService.findAllVersions(codeSystem.getShortName(), true, true).size());

        // when
        releaseImportService.performScheduledImport(codeSystem);

        // then
        assertEquals(1, codeSystemService.findAllVersions(codeSystem.getShortName(), true, true).size());
    }

    @Test
    void testPerformScheduledImport_extensionNotUpgradedWhenDependencyAlreadyCurrent() throws Exception {
        // given
        givenBaseline("SNOMEDCT", "MAIN", "SnomedCT_MiniRF2_Base_snapshot");
        givenBaseline("SNOMEDCT-XX", "MAIN/SNOMEDCT-XX", "SnomedCT_MiniRF2_XX_snapshot_20260102");
        givenExtensionRelease("XX", "11000001101", 20260202, 20180731); // INT dependency matches baseline already imported
        CodeSystem codeSystem = codeSystemService.find("SNOMEDCT-XX");

        // when
        releaseImportService.performScheduledImport(codeSystem);

        // then
        verify(codeSystemUpgradeService, never()).upgrade(any(), any(), anyBoolean());
        List<CodeSystemVersion> versions = codeSystemService.findAllVersions(codeSystem.getShortName(), true, true);
        assertEquals(2, versions.size());
        assertEquals("2026-01-02", versions.get(0).getVersion());
        assertEquals("2026-02-02", versions.get(1).getVersion());
    }

    @Test
    void testPerformScheduledImport_rollsBackBranchOnDownloadFailure() throws Exception {
        // given
        codeSystemService.createCodeSystem(new CodeSystem("SNOMEDCT", "MAIN"));
        givenRelease("INT", "900000000000207008", 20250701, "SnomedCT_InternationalRF2_PRODUCTION_20250701T120000Z.zip");
        doThrow(new RuntimeException("S3 unavailable")).when(moduleStorageCoordinator).addFileLocally(any(ModuleMetadata.class));
        CodeSystem codeSystem = codeSystemService.find("SNOMEDCT");

        // when
        releaseImportService.performScheduledImport(codeSystem);

        // then
        assertTrue(codeSystemService.findAllVersions(codeSystem.getShortName(), true, true).isEmpty());
        assertFalse(branchService.findBranchOrThrow(codeSystem.getBranchPath()).isLocked());
    }

    @Test
    void testPerformScheduledImport_picksLatestWhenMultipleAvailable() throws Exception {
        // given
        codeSystemService.createCodeSystem(new CodeSystem("SNOMEDCT", "MAIN"));
        givenMultipleReleases("INT", "900000000000207008",
                List.of(20250101, 20250701),
                "SnomedCT_InternationalRF2_PRODUCTION_20250701T120000Z.zip");
        CodeSystem codeSystem = codeSystemService.find("SNOMEDCT");

        // when
        releaseImportService.performScheduledImport(codeSystem);

        // then
        verify(moduleStorageCoordinator).addFileLocally(argThat(m -> Integer.valueOf(20250701).equals(m.getEffectiveTime())));
        List<CodeSystemVersion> versions = codeSystemService.findAllVersions(codeSystem.getShortName(), true, true);
        assertEquals(1, versions.size());
        assertEquals("2025-07-01", versions.get(0).getVersion());
    }

    private void givenMultipleReleases(String codeSystemCode, String moduleId, List<Integer> effectiveTimes, String filenameForDownload) throws Exception {
        List<ModuleMetadata> releases = new ArrayList<>();
        for (int effectiveTime : effectiveTimes) {
            Channel browserChannel = new Channel();
            browserChannel.setChannel(Channel.Name.BROWSER);
            browserChannel.setProperties(Map.of(Channel.Property.Visibility.NAME, Channel.Property.Visibility.PUBLIC));

            ModuleMetadata releaseMetadata = new ModuleMetadata();
            releaseMetadata.setFilename("SnomedCT_InternationalRF2_PRODUCTION_" + effectiveTime + "T120000Z.zip");
            releaseMetadata.setCodeSystemShortName(codeSystemCode);
            releaseMetadata.setIdentifyingModuleId(moduleId);
            releaseMetadata.setEffectiveTime(effectiveTime);
            releaseMetadata.setPublished(true);
            releaseMetadata.setChannels(List.of(browserChannel));
            releases.add(releaseMetadata);
        }

        File releaseZip = new File("src/test/resources/dummy-release-import/SNOMEDCT/" + filenameForDownload);
        when(moduleStorageCoordinator.getAllReleases(codeSystemCode)).thenReturn(releases);
        doAnswer(invocation -> {
            ModuleMetadata metadata = invocation.getArgument(0);
            metadata.setFile(releaseZip);
            return null;
        }).when(moduleStorageCoordinator).addFileLocally(any(ModuleMetadata.class));
    }

    private void givenBaseline(String shortName, String branchPath, String snapshotDir) throws Exception {
        codeSystemService.createCodeSystem(new CodeSystem(shortName, branchPath));
        File baseline = ZipUtil.zipDirectoryRemovingCommentsAndBlankLines("src/test/resources/dummy-snomed-content/" + snapshotDir);
        String importId = importService.createJob(RF2Type.SNAPSHOT, branchPath, true, false);
        importService.importArchive(importId, new FileInputStream(baseline));
    }

    private void givenRelease(String codeSystem, String moduleId, int effectiveTime, String filename) throws Exception {
        Channel browserChannel = new Channel();
        browserChannel.setChannel(Channel.Name.BROWSER);
        browserChannel.setProperties(Map.of(Channel.Property.Visibility.NAME, Channel.Property.Visibility.PUBLIC));

        ModuleMetadata releaseMetadata = new ModuleMetadata();
        releaseMetadata.setFilename(filename);
        releaseMetadata.setCodeSystemShortName(codeSystem);
        releaseMetadata.setIdentifyingModuleId(moduleId);
        releaseMetadata.setEffectiveTime(effectiveTime);
        releaseMetadata.setPublished(true);
        releaseMetadata.setChannels(List.of(browserChannel));

        File releaseZip = new File("src/test/resources/dummy-release-import/SNOMEDCT/" + filename);

        when(moduleStorageCoordinator.getAllReleases(codeSystem)).thenReturn(List.of(releaseMetadata));
        doAnswer(invocation -> {
            ModuleMetadata metadata = invocation.getArgument(0);
            metadata.setFile(releaseZip);
            return null;
        }).when(moduleStorageCoordinator).addFileLocally(any(ModuleMetadata.class));
    }

    private void givenReleaseNotPublished(String codeSystem, String moduleId, int effectiveTime, String filename) throws Exception {
        Channel browserChannel = new Channel();
        browserChannel.setChannel(Channel.Name.BROWSER);
        browserChannel.setProperties(Map.of(Channel.Property.Visibility.NAME, Channel.Property.Visibility.PUBLIC));

        ModuleMetadata releaseMetadata = new ModuleMetadata();
        releaseMetadata.setFilename(filename);
        releaseMetadata.setCodeSystemShortName(codeSystem);
        releaseMetadata.setIdentifyingModuleId(moduleId);
        releaseMetadata.setEffectiveTime(effectiveTime);
        releaseMetadata.setPublished(false);
        releaseMetadata.setChannels(List.of(browserChannel));

        when(moduleStorageCoordinator.getAllReleases(codeSystem)).thenReturn(List.of(releaseMetadata));
    }

    private void givenReleaseWithNoChannel(String codeSystem, String moduleId, int effectiveTime, String filename) throws Exception {
        ModuleMetadata releaseMetadata = new ModuleMetadata();
        releaseMetadata.setFilename(filename);
        releaseMetadata.setCodeSystemShortName(codeSystem);
        releaseMetadata.setIdentifyingModuleId(moduleId);
        releaseMetadata.setEffectiveTime(effectiveTime);
        releaseMetadata.setPublished(true);

        when(moduleStorageCoordinator.getAllReleases(codeSystem)).thenReturn(List.of(releaseMetadata));
    }

    private void givenReleaseWithFutureAvailability(String codeSystem, String moduleId, int effectiveTime, String filename, String availableFrom) throws Exception {
        Channel browserChannel = new Channel();
        browserChannel.setChannel(Channel.Name.BROWSER);
        browserChannel.setAvailableFrom(availableFrom);
        browserChannel.setProperties(Map.of(Channel.Property.Visibility.NAME, Channel.Property.Visibility.PUBLIC));

        ModuleMetadata releaseMetadata = new ModuleMetadata();
        releaseMetadata.setFilename(filename);
        releaseMetadata.setCodeSystemShortName(codeSystem);
        releaseMetadata.setIdentifyingModuleId(moduleId);
        releaseMetadata.setEffectiveTime(effectiveTime);
        releaseMetadata.setPublished(true);
        releaseMetadata.setChannels(List.of(browserChannel));

        when(moduleStorageCoordinator.getAllReleases(codeSystem)).thenReturn(List.of(releaseMetadata));
    }

    private void givenReleaseWithUnknownVisibility(String codeSystem, String moduleId, int effectiveTime, String filename) throws Exception {
        Channel browserChannel = new Channel();
        browserChannel.setChannel(Channel.Name.BROWSER);
        browserChannel.setProperties(Map.of(Channel.Property.Visibility.NAME, "RESTRICTED"));

        ModuleMetadata releaseMetadata = new ModuleMetadata();
        releaseMetadata.setFilename(filename);
        releaseMetadata.setCodeSystemShortName(codeSystem);
        releaseMetadata.setIdentifyingModuleId(moduleId);
        releaseMetadata.setEffectiveTime(effectiveTime);
        releaseMetadata.setPublished(true);
        releaseMetadata.setChannels(List.of(browserChannel));

        when(moduleStorageCoordinator.getAllReleases(codeSystem)).thenReturn(List.of(releaseMetadata));
    }

    private void givenReleaseWithNoVisibilityProperty(String codeSystem, String moduleId, int effectiveTime, String filename) throws Exception {
        Channel browserChannel = new Channel();
        browserChannel.setChannel(Channel.Name.BROWSER);

        ModuleMetadata releaseMetadata = new ModuleMetadata();
        releaseMetadata.setFilename(filename);
        releaseMetadata.setCodeSystemShortName(codeSystem);
        releaseMetadata.setIdentifyingModuleId(moduleId);
        releaseMetadata.setEffectiveTime(effectiveTime);
        releaseMetadata.setPublished(true);
        releaseMetadata.setChannels(List.of(browserChannel));

        when(moduleStorageCoordinator.getAllReleases(codeSystem)).thenReturn(List.of(releaseMetadata));
    }

    private void givenExtensionRelease(String codeSystem, String moduleId, int effectiveTime, int dependencyEffectiveTime) throws Exception {
        ModuleMetadata intDependency = new ModuleMetadata();
        intDependency.setCodeSystemShortName("INT");
        intDependency.setIdentifyingModuleId("900000000000207008");
        intDependency.setEffectiveTime(dependencyEffectiveTime);

        Channel browserChannel = new Channel();
        browserChannel.setChannel(Channel.Name.BROWSER);
        browserChannel.setProperties(Map.of(Channel.Property.Visibility.NAME, Channel.Property.Visibility.PUBLIC));

        ModuleMetadata releaseMetadata = new ModuleMetadata();
        releaseMetadata.setFilename("SnomedCT_XxRF2_PRODUCTION_" + effectiveTime + "T120000Z.zip");
        releaseMetadata.setCodeSystemShortName(codeSystem);
        releaseMetadata.setIdentifyingModuleId(moduleId);
        releaseMetadata.setEffectiveTime(effectiveTime);
        releaseMetadata.setPublished(true);
        releaseMetadata.setChannels(List.of(browserChannel));
        releaseMetadata.setDependencies(List.of(intDependency));

        File releaseZip = ZipUtil.zipDirectoryRemovingCommentsAndBlankLines("src/test/resources/dummy-snomed-content/SnomedCT_MiniRF2_XX_snapshot_20260202");

        when(moduleStorageCoordinator.getAllReleases(codeSystem)).thenReturn(List.of(releaseMetadata));
        doAnswer(invocation -> {
            ModuleMetadata metadata = invocation.getArgument(0);
            metadata.setFile(releaseZip);
            return null;
        }).when(moduleStorageCoordinator).addFileLocally(any(ModuleMetadata.class));
    }

    private void givenReleaseWithPrivateVisibility(String codeSystem, String moduleId, int effectiveTime, String filename) throws Exception {
        Channel browserChannel = new Channel();
        browserChannel.setChannel(Channel.Name.BROWSER);
        browserChannel.setProperties(Map.of(Channel.Property.Visibility.NAME, Channel.Property.Visibility.PRIVATE));

        ModuleMetadata releaseMetadata = new ModuleMetadata();
        releaseMetadata.setFilename(filename);
        releaseMetadata.setCodeSystemShortName(codeSystem);
        releaseMetadata.setIdentifyingModuleId(moduleId);
        releaseMetadata.setEffectiveTime(effectiveTime);
        releaseMetadata.setPublished(true);
        releaseMetadata.setChannels(List.of(browserChannel));

        File releaseZip = new File("src/test/resources/dummy-release-import/SNOMEDCT/" + filename);

        when(moduleStorageCoordinator.getAllReleases(codeSystem)).thenReturn(List.of(releaseMetadata));
        doAnswer(invocation -> {
            ModuleMetadata metadata = invocation.getArgument(0);
            metadata.setFile(releaseZip);
            return null;
        }).when(moduleStorageCoordinator).addFileLocally(any(ModuleMetadata.class));
    }
}
