package org.snomed.snowstorm.syndication.loinc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.snomed.snowstorm.core.util.FileUtils;
import org.snomed.snowstorm.syndication.common.SyndicationImportParams;
import org.snomed.snowstorm.syndication.common.SyndicationImportStatusService;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Optional;

import static org.apache.commons.compress.java.util.jar.Pack200.Packer.LATEST;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;
import static org.snomed.snowstorm.syndication.common.SyndicationConstants.LOCAL_VERSION;

@ExtendWith(MockitoExtension.class)
class LoincSyndicationServiceTest {

    @Mock
    private SyndicationImportStatusService importStatusService;

    @InjectMocks
    private LoincSyndicationService loincSyndicationService;

    @BeforeEach
    void setUp() throws IOException {
        ReflectionTestUtils.setField(loincSyndicationService, "workingDirectory", "/tmp");
        ReflectionTestUtils.setField(loincSyndicationService, "fileNamePattern", "loinc.zip");
        createFakeHapiFhirCli();
    }

    @Test
    void testImportLoincTerminology_FileAlreadyExists() throws IOException, InterruptedException, ServiceException {
        try (var mockStatic = mockStatic(FileUtils.class)) {
            mockStatic.when(() -> FileUtils.findFile("/tmp", "loinc.zip"))
                    .thenReturn(Optional.of(new File("loinc.zip")));

            loincSyndicationService.importTerminologyAndStoreResult(new SyndicationImportParams(LOCAL_VERSION, null, false));

            mockStatic.verify(() -> FileUtils.findFile("/tmp", "loinc.zip"), times(1));
        }
    }

    @Test
    void testImportLoincTerminology_FileNeedsDownload() throws IOException, InterruptedException, ServiceException {
        try (var mockStatic = mockStatic(FileUtils.class)) {
            mockStatic.when(() -> FileUtils.findFile("/tmp", "loinc.zip"))
                    .thenReturn(Optional.of(new File("loinc.zip")));

            loincSyndicationService.importTerminologyAndStoreResult(new SyndicationImportParams(LATEST, null, false));

            mockStatic.verify(() -> FileUtils.findFile("/tmp", "loinc.zip"), times(1));
        }
    }

    @Test
    void testImportLoincTerminology_FileNotFoundAfterDownload() {
        try (var mockStatic = mockStatic(FileUtils.class)) {
            mockStatic.when(() -> FileUtils.findFile("/tmp", "loinc.zip"))
                    .thenReturn(Optional.empty());

            assertThrows(ServiceException.class, () -> loincSyndicationService.importTerminologyAndStoreResult(new SyndicationImportParams("2.80", null, false)));
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
