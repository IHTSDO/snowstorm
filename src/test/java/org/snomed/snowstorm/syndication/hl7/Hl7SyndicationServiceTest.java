package org.snomed.snowstorm.syndication.hl7;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.snomed.snowstorm.fhir.domain.FHIRCodeSystemVersion;
import org.snomed.snowstorm.fhir.services.FHIRCodeSystemService;
import org.snomed.snowstorm.fhir.services.FHIRLoadPackageService;
import org.snomed.snowstorm.syndication.common.SyndicationImportParams;
import org.snomed.snowstorm.syndication.common.SyndicationImportStatusService;
import org.snomed.snowstorm.syndication.data.SyndicationImportStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.snomed.snowstorm.syndication.common.SyndicationConstants.LATEST_VERSION;

class Hl7SyndicationServiceTest {

    @Mock
    private FHIRLoadPackageService loadPackageService;

    @Mock
    private FHIRCodeSystemService codeSystemService;

    @Mock
    private SyndicationImportStatusService importStatusService;

    @InjectMocks
    private Hl7SyndicationService hl7SyndicationService;

    @TempDir
    Path tempDir;
    private FHIRCodeSystemVersion fhirCodeSystemVersion;

    private static final String VERSION = "6.2.0";

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        ReflectionTestUtils.setField(hl7SyndicationService, "workingDirectory", tempDir.toString());
        ReflectionTestUtils.setField(hl7SyndicationService, "fileNamePattern", "hl7.terminology.*.tgz");
        fhirCodeSystemVersion = new FHIRCodeSystemVersion();
        doReturn(fhirCodeSystemVersion).when(codeSystemService).findCodeSystemVersion(any());
    }

    @Test
    void testImportHl7Terminology_Success() throws IOException {
        Path hl7File = tempDir.resolve("hl7.terminology.r4-6.2.0.tgz");
        Files.createFile(hl7File);
        assertDoesNotThrow(() -> hl7SyndicationService.importTerminologyAndStoreResult(new SyndicationImportParams(null, null, true)));

        verify(loadPackageService, times(1))
                .uploadPackageResources(any(File.class), eq(Set.of("*")), eq("hl7.terminology.r4-6.2.0.tgz"), eq(false));
        verify(codeSystemService, times(1)).deleteCodeSystemVersion(fhirCodeSystemVersion);
        verify(importStatusService, times(1)).saveOrUpdateImportStatus(any(), any(), any(), anyBoolean(), any());
    }

    @Test
    void testImportHl7Terminology_FileNotFound() throws IOException {
        Exception exception = assertThrows(ServiceException.class, () -> hl7SyndicationService.importTerminologyAndStoreResult(new SyndicationImportParams(null, null, false)));

        assertEquals("Hl7 terminology file not found, cannot be imported", exception.getMessage());

        verify(loadPackageService, never()).uploadPackageResources(any(), any(), any(), anyBoolean());
    }

    @Test
    void testImportHl7Terminology_alreadyImported() throws IOException {
        Path hl7File = tempDir.resolve("hl7.terminology.r4-6.2.0.tgz");
        Files.createFile(hl7File);
        SyndicationImportStatus importStatus = new SyndicationImportStatus("hl7", VERSION, VERSION, true, null);
        doReturn(importStatus).when(importStatusService).getImportStatus(any());

        assertDoesNotThrow(() -> hl7SyndicationService.importTerminologyAndStoreResult(new SyndicationImportParams(VERSION, null, true)));
        verify(importStatusService, never()).saveOrUpdateImportStatus(any(), any(), any(), anyBoolean(), any());
    }

    @Test
    void testImportHl7Terminology_latestVersionalreadyImported() throws IOException {
        Path hl7File = tempDir.resolve("hl7.terminology.r4-6.2.0.tgz");
        Files.createFile(hl7File);
        SyndicationImportStatus importStatus = new SyndicationImportStatus("hl7", VERSION, VERSION, true, null);
        doReturn(importStatus).when(importStatusService).getImportStatus(any());

        assertDoesNotThrow(() -> hl7SyndicationService.importTerminologyAndStoreResult(new SyndicationImportParams(LATEST_VERSION, null, true)));
        verify(importStatusService, never()).saveOrUpdateImportStatus(any(), any(), any(), anyBoolean(), any());
    }
}
