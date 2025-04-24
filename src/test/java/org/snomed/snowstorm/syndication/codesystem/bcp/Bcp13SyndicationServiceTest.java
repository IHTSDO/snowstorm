package org.snomed.snowstorm.syndication.codesystem.bcp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.snomed.snowstorm.fhir.services.FHIRCodeSystemService;
import org.snomed.snowstorm.fhir.services.FHIRConceptService;
import org.snomed.snowstorm.syndication.codesystem.atc.AtcSyndicationService;
import org.snomed.snowstorm.syndication.common.SyndicationImportParams;
import org.snomed.snowstorm.syndication.common.SyndicationTerminology;

import java.io.File;
import java.io.FileWriter;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;

class Bcp13SyndicationServiceTest {
    @Mock
    private FHIRCodeSystemService codeSystemService;

    @Mock
    private FHIRConceptService fhirConceptService;

    @InjectMocks
    private Bcp13SyndicationService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testImportTerminology_withValidCsv_shouldCreateConcepts() throws Exception {
        // Create a temporary CSV file
        File tempCsv = File.createTempFile("atc", ".csv");
        try (FileWriter writer = new FileWriter(tempCsv)) {
            writer.write("display,code\n");
            writer.write("Image and more particularly a GIF,image/gif\n");
        }

        SyndicationImportParams params = new SyndicationImportParams(SyndicationTerminology.BCP13, null, null, false);
        service.importTerminology(params, Collections.singletonList(tempCsv));

        Mockito.verify(codeSystemService).createUpdate(Mockito.any());
        Mockito.verify(fhirConceptService).saveAllConceptsOfCodeSystemVersion(anyList(), any());
    }

    @Test
    void testImportTerminology_withEmptyFile_shouldNotThrow() throws Exception {
        File tempCsv = File.createTempFile("empty", ".csv");
        try (FileWriter writer = new FileWriter(tempCsv)) {
            writer.write("display,code\n");
        }

        SyndicationImportParams params = new SyndicationImportParams(SyndicationTerminology.BCP13, null, null, false);
        assertDoesNotThrow(() -> service.importTerminology(params, Collections.singletonList(tempCsv)));
    }

    @Test
    void testReadConceptsFromFile_withInvalidFile_shouldThrowServiceException() {
        File invalidFile = new File("nonexistent.csv");
        SyndicationImportParams params = new SyndicationImportParams(SyndicationTerminology.BCP13, null, null, false);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.importTerminology(params, Collections.singletonList(invalidFile)));

        assertTrue(exception.getMessage().contains("Failed to read CSV file"));
    }
}