package org.snomed.snowstorm.syndication.services.importers.fixedversion.atc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.snomed.snowstorm.fhir.services.FHIRCodeSystemService;
import org.snomed.snowstorm.fhir.services.FHIRConceptService;
import org.snomed.snowstorm.syndication.models.domain.SyndicationImportParams;
import org.snomed.snowstorm.syndication.constants.SyndicationTerminology;

import java.io.File;
import java.io.FileWriter;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;

class AtcSyndicationServiceTest {


    @Mock
    private FHIRCodeSystemService codeSystemService;

    @Mock
    private FHIRConceptService fhirConceptService;

    @InjectMocks
    private AtcSyndicationService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testImportTerminology_withValidCsv_shouldCreateConcepts() throws Exception {
        // Create a temporary CSV file
        File tempCsv = File.createTempFile("atc", ".csv");
        try (FileWriter writer = new FileWriter(tempCsv)) {
            writer.write("L1_CODE,L1_href,L1_NAME,L2_CODE,L2_href,L2_NAME,L3_CODE,L3_href,L3_NAME,L4_CODE,L4_href,L4_NAME,L5_CODE,L5_NAME\n");
            writer.write("A,,Alimentary tract,B,,Blood,C,,Cardiovascular,D,,Dermatologicals,E,Endocrine\n");
        }

        SyndicationImportParams params = new SyndicationImportParams(SyndicationTerminology.ATC, null, null, false);
        service.importTerminology(params, Collections.singletonList(tempCsv));

        Mockito.verify(codeSystemService).createUpdate(Mockito.any());
        Mockito.verify(fhirConceptService).saveAllConceptsOfCodeSystemVersion(anyList(), any());
    }

    @Test
    void testImportTerminology_withEmptyFile_shouldNotThrow() throws Exception {
        File tempCsv = File.createTempFile("empty", ".csv");
        try (FileWriter writer = new FileWriter(tempCsv)) {
            writer.write("L1_CODE,L1_href,L1_NAME,L2_CODE,L2_href,L2_NAME,L3_CODE,L3_href,L3_NAME,L4_CODE,L4_href,L4_NAME,L5_CODE,L5_NAME\n");
        }

        SyndicationImportParams params = new SyndicationImportParams(SyndicationTerminology.ATC, null, null, false);
        assertDoesNotThrow(() -> service.importTerminology(params, Collections.singletonList(tempCsv)));
    }

    @Test
    void testReadConceptsFromFile_withInvalidFile_shouldThrowServiceException() {
        File invalidFile = new File("nonexistent.csv");
        SyndicationImportParams params = new SyndicationImportParams(SyndicationTerminology.ATC, null, null, false);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.importTerminology(params, Collections.singletonList(invalidFile)));

        assertTrue(exception.getMessage().contains("Failed to read CSV file"));
    }
}
