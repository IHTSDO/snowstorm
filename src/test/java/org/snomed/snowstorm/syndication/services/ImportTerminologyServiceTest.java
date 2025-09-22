package org.snomed.snowstorm.syndication.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.snomed.snowstorm.syndication.models.data.SyndicationImport;
import org.snomed.snowstorm.syndication.models.domain.SyndicationImportParams;
import org.snomed.snowstorm.syndication.models.requestDto.SyndicationImportRequest;
import org.snomed.snowstorm.syndication.services.importers.SyndicationService;
import org.snomed.snowstorm.syndication.services.importstatus.SyndicationImportStatusDao;
import org.springframework.boot.ApplicationArguments;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.snomed.snowstorm.core.rf2.rf2import.ImportJob.ImportStatus.COMPLETED;
import static org.snomed.snowstorm.syndication.constants.SyndicationTerminology.LOINC;

class ImportTerminologyServiceTest {

    private ImportTerminologyService startupService;
    private SyndicationService snomedService;
    private SyndicationService loincService;
    private SyndicationService hl7Service;
    private SyndicationService atcService;
    private SyndicationService ucumService;
    private SyndicationService bcp13Service;
    private SyndicationService bcp47Service;
    private SyndicationService iso3166Service;
    private SyndicationService m49Service;
    private SyndicationImportStatusDao syndicationImportStatusDao;
    private ExecutorService executorService;

    @BeforeEach
    void setup() {
        startupService = new ImportTerminologyService();

        snomedService = mock(SyndicationService.class);
        loincService = mock(SyndicationService.class);
        hl7Service = mock(SyndicationService.class);
        atcService = mock(SyndicationService.class);
        ucumService = mock(SyndicationService.class);
        bcp13Service = mock(SyndicationService.class);
        bcp47Service = mock(SyndicationService.class);
        iso3166Service = mock(SyndicationService.class);
        m49Service = mock(SyndicationService.class);
        syndicationImportStatusDao = mock(SyndicationImportStatusDao.class);
        executorService = mock(ExecutorService.class);

        Map<String, SyndicationService> services = Map.of(
                "snomed", snomedService,
                "loinc", loincService,
                "hl7", hl7Service,
                "atc", atcService,
                "ucum", ucumService,
                "bcp13", bcp13Service,
                "bcp47", bcp47Service,
                "iso3166", iso3166Service,
                "m49", m49Service
        );

        ReflectionTestUtils.setField(startupService, "syndicationServices", services);
        ReflectionTestUtils.setField(startupService, "syndicationImportStatusDao", syndicationImportStatusDao);
        ReflectionTestUtils.setField(startupService, "executorService", executorService);
    }

    @Test
    void testHandleStartupSyndication_triggersOnlyPresentOptions() throws Exception {
        ApplicationArguments mockArgs = mock(ApplicationArguments.class);

        when(mockArgs.containsOption("snomed")).thenReturn(true);
        when(mockArgs.getOptionValues("snomed")).thenReturn(List.of("20250101"));
        when(mockArgs.containsOption("loinc")).thenReturn(false);
        when(mockArgs.containsOption("hl7")).thenReturn(false);
        when(mockArgs.containsOption("extension-country-code")).thenReturn(false);

        startupService.handleStartupSyndication(mockArgs);

        verify(snomedService, times(1)).fetchAndImportTerminology(any());
        verify(loincService, never()).fetchAndImportTerminology(any());
        verify(hl7Service, never()).fetchAndImportTerminology(any());
    }

    @Test
    void testHandleStartupSyndication_passesCorrectParams() throws Exception {
        ApplicationArguments mockArgs = mock(ApplicationArguments.class);

        when(mockArgs.containsOption("hl7")).thenReturn(true);
        when(mockArgs.getOptionValues("hl7")).thenReturn(List.of("6.2.0"));
        when(mockArgs.containsOption("loinc")).thenReturn(true);
        when(mockArgs.containsOption("extension-country-code")).thenReturn(true);
        when(mockArgs.getOptionValues("extension-country-code")).thenReturn(List.of("US"));

        startupService.handleStartupSyndication(mockArgs);

        ArgumentCaptor<SyndicationImportParams> captor = ArgumentCaptor.forClass(SyndicationImportParams.class);
        verify(hl7Service).fetchAndImportTerminology(captor.capture());

        SyndicationImportParams params = captor.getValue();
        assertEquals("6.2.0", params.version());
        assertEquals("US", params.extensionName());
    }

    @Test
    void testHandleStartupSyndication_missingServiceThrowsException() {
        ApplicationArguments mockArgs = mock(ApplicationArguments.class);
        when(mockArgs.containsOption("snomed")).thenReturn(true);
        when(mockArgs.getOptionValues("snomed")).thenReturn(List.of("20250101"));

        // remove the service from the map to simulate missing handler
        ReflectionTestUtils.setField(startupService, "syndicationServices", Map.of());

        Exception exception = assertThrows(IllegalStateException.class, () ->
                startupService.handleStartupSyndication(mockArgs));

        assertTrue(exception.getMessage().contains("No service found for terminology"));
    }

    @Test
    void testUpdateTerminology_success() throws Exception {
        SyndicationImportRequest request = new SyndicationImportRequest(LOINC.getName(), "2.80", null, null);
        SyndicationImport notRunningStatus = new SyndicationImport(LOINC.getName(), "2.79", "2.79", COMPLETED, null);

        when(syndicationImportStatusDao.getAllImportStatuses()).thenReturn(List.of());
        when(syndicationImportStatusDao.getImportStatus(LOINC.getName())).thenReturn(notRunningStatus);

        boolean alreadyImported = startupService.updateTerminology(request);

        assertFalse(alreadyImported);
        verify(executorService).submit(any(Runnable.class));
    }

    @Test
    void testUpdateTerminology_alreadyImported() throws Exception {
        SyndicationImportRequest request = new SyndicationImportRequest(LOINC.getName(), "2.80", null, null);
        SyndicationImport notRunningStatus = new SyndicationImport(LOINC.getName(), "2.79", "2.79", COMPLETED, null);

        when(syndicationImportStatusDao.getAllImportStatuses()).thenReturn(List.of());
        when(syndicationImportStatusDao.getImportStatus(LOINC.getName())).thenReturn(notRunningStatus);
        when(loincService.alreadyImported(any(), any())).thenReturn(true);

        boolean alreadyImported = startupService.updateTerminology(request);

        assertTrue(alreadyImported);
        verify(executorService, never()).submit(any(Runnable.class));
    }

}
