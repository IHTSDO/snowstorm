package org.snomed.snowstorm.syndication.services.importers.fixedversion.iso;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.JsonParser;
import org.hl7.fhir.r4.model.CodeSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.snomed.snowstorm.syndication.utils.FileUtils;
import org.snomed.snowstorm.fhir.domain.FHIRCodeSystemVersion;
import org.snomed.snowstorm.fhir.services.FHIRCodeSystemService;
import org.snomed.snowstorm.fhir.services.FHIRConceptService;
import org.snomed.snowstorm.syndication.models.domain.SyndicationImportParams;
import org.snomed.snowstorm.syndication.constants.SyndicationTerminology;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.snomed.snowstorm.syndication.constants.SyndicationConstants.LOCAL_VERSION;

class Iso3166SyndicationServiceTest {

    @Mock
    private FHIRCodeSystemService codeSystemService;

    @Mock
    private FHIRConceptService fhirConceptService;

    @Mock
    private FhirContext fhirContext;

    @InjectMocks
    private Iso3166SyndicationService service;

    private SyndicationImportParams params;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service.init();
        params = new SyndicationImportParams(SyndicationTerminology.ISO3166, LOCAL_VERSION, null, false);
    }

    @Test
    void fetchTerminologyPackages() {
        try (var mockStatic = mockStatic(FileUtils.class)) {
            File file = new File("iso3166-codesystem.json");
            mockStatic.when(() -> FileUtils.findFiles(anyString(), anyString()))
                    .thenReturn(List.of(file));

            List<File> fileList = ReflectionTestUtils.invokeMethod(service, "fetchTerminologyPackages", params);

            assertNotNull(fileList);
            assertEquals(file, fileList.get(0));
        }
    }

    @Test
    void importTerminology_andDeleteExistingCodeSystem() throws ServiceException, IOException {
        JsonParser parser = Mockito.mock(JsonParser.class);
        FHIRCodeSystemVersion fhirCodeSystemVersion = new FHIRCodeSystemVersion();
        doReturn(fhirCodeSystemVersion).when(codeSystemService).findCodeSystemVersion(any());
        doReturn(parser).when(fhirContext).newJsonParser();
        doReturn(new CodeSystem()).when(parser).parseResource(any(InputStream.class));

        File tempCsv = File.createTempFile("iso3166-codesystem", ".json");
        ReflectionTestUtils.invokeMethod(service, "importTerminology", params, List.of(tempCsv));

        verify(codeSystemService).deleteCodeSystemVersion(fhirCodeSystemVersion);
        Mockito.verify(codeSystemService).createUpdate(Mockito.any());
        Mockito.verify(fhirConceptService).saveAllConceptsOfCodeSystemVersion(anyList(), any());
    }
}