package org.snomed.snowstorm.syndication.services.importers.customversion.loinc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.snomed.snowstorm.core.rf2.rf2import.ImportJob;
import org.snomed.snowstorm.syndication.utils.FileUtils;
import org.snomed.snowstorm.syndication.utils.CommandUtils;
import org.snomed.snowstorm.syndication.models.domain.SyndicationImportParams;
import org.snomed.snowstorm.syndication.services.importstatus.SyndicationImportStatusService;
import org.snomed.snowstorm.syndication.constants.SyndicationTerminology;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Optional;

import static org.apache.commons.compress.java.util.jar.Pack200.Packer.LATEST;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;
import static org.snomed.snowstorm.core.rf2.rf2import.ImportJob.ImportStatus.FAILED;
import static org.snomed.snowstorm.syndication.constants.SyndicationConstants.LOCAL_VERSION;
import static org.snomed.snowstorm.syndication.constants.SyndicationTerminology.LOINC;

@ExtendWith(MockitoExtension.class)
class LoincSyndicationServiceTest {

    @Mock
    private SyndicationImportStatusService syndicationImportStatusService;

    @InjectMocks
    private LoincSyndicationService loincSyndicationService;

    @BeforeEach
    void setUp() throws IOException {
        ReflectionTestUtils.setField(loincSyndicationService, "workingDirectory", "/tmp");
        ReflectionTestUtils.setField(loincSyndicationService, "fileNamePattern", "loinc.zip");
        createFakeHapiFhirCli();
    }

    @Test
    void testImportLoincTerminology_FileAlreadyExists() {
        try (var mockStatic = mockStatic(FileUtils.class)) {
            mockStatic.when(() -> FileUtils.findFile("/tmp", "loinc.zip"))
                    .thenReturn(Optional.of(new File("loinc.zip")));

            loincSyndicationService.fetchAndImportTerminology(new SyndicationImportParams(SyndicationTerminology.LOINC, LOCAL_VERSION, null));

            mockStatic.verify(() -> FileUtils.findFile("/tmp", "loinc.zip"), times(1));
        }
    }

    @Test
    void testImportLoincTerminology_FileNeedsDownload() {
        try (
                var mockFileUtils = mockStatic(FileUtils.class);
                var mockStatic = mockStatic(CommandUtils.class)
        ) {
            mockFileUtils.when(() -> FileUtils.findFile("/tmp", "loinc.zip"))
                    .thenReturn(Optional.of(new File("loinc.zip")));

            loincSyndicationService.fetchAndImportTerminology(new SyndicationImportParams(SyndicationTerminology.LOINC, LATEST, null));

            mockFileUtils.verify(() -> FileUtils.findFile("/tmp", "loinc.zip"), times(1));
        }
    }

    @Test
    void testImportLoincTerminology_FileNotFound() {
        try (
                var ignored = mockStatic(FileUtils.class);
                var mockStatic = mockStatic(CommandUtils.class)
        ) {
            mockStatic.when(() -> FileUtils.findFile("/tmp", "loinc.zip"))
                    .thenReturn(Optional.empty());

            loincSyndicationService.fetchAndImportTerminology(new SyndicationImportParams(SyndicationTerminology.LOINC, "2.80", null));

            ArgumentCaptor<ImportJob.ImportStatus> captor = ArgumentCaptor.forClass(ImportJob.ImportStatus.class);

            verify(syndicationImportStatusService, atLeastOnce())
                    .saveOrUpdateImportStatus(eq(LOINC), eq("2.80"), eq(null), any(), any());

            verify(syndicationImportStatusService, atLeastOnce())
                    .saveOrUpdateImportStatus(eq(LOINC), eq("2.80"), eq(null), captor.capture(), any());

            ImportJob.ImportStatus lastCall = captor.getAllValues().get(captor.getAllValues().size() - 1);

            assertEquals(FAILED, lastCall);

            mockStatic.verify(() -> CommandUtils.waitForProcessTermination(any(), any()), times(1));
        }
    }

    private void createFakeHapiFhirCli() throws IOException {
        File script = new File("/tmp/hapi-fhir-cli");
        try (FileWriter writer = new FileWriter(script)) {
            writer.write("#!/bin/bash\n");
            writer.write("echo 'HAPI FHIR CLI Simulation'\n");
            writer.write("exit 0\n");
        }
        script.setExecutable(true);
    }
}
