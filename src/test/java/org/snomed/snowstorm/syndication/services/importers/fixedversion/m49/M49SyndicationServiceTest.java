package org.snomed.snowstorm.syndication.services.importers.fixedversion.m49;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.snomed.snowstorm.fhir.domain.FHIRCodeSystemVersion;
import org.snomed.snowstorm.fhir.services.FHIRCodeSystemService;
import org.snomed.snowstorm.fhir.services.FHIRConceptService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;

class M49SyndicationServiceTest {
    @Mock
    private FHIRCodeSystemService codeSystemService;

    @Mock
    private FHIRConceptService fhirConceptService;

    @InjectMocks
    private M49SyndicationService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testImportCodeSystem() throws ServiceException {
        FHIRCodeSystemVersion fhirCodeSystemVersion = new FHIRCodeSystemVersion();
        doReturn(fhirCodeSystemVersion).when(codeSystemService).createUpdate(any());
        service.importTerminology(null, null);
        Mockito.verify(codeSystemService).createUpdate(Mockito.any());
        Mockito.verify(fhirConceptService).saveAllConceptsOfCodeSystemVersion(anyList(), eq(fhirCodeSystemVersion));
    }
}