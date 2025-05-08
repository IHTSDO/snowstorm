package org.snomed.snowstorm.syndication.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.snomed.snowstorm.syndication.models.domain.SyndicationImportParams;
import org.snomed.snowstorm.syndication.services.importers.SyndicationService;
import org.snomed.snowstorm.syndication.services.importstatus.SyndicationImportStatusService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StartupSyndicationServiceTest {

    private StartupSyndicationService startupService;
    private SyndicationService snomedService;
    private SyndicationService loincService;
    private SyndicationService hl7Service;
    private SyndicationService atcService;
    private SyndicationService ucumService;
    private SyndicationService bcp13Service;
    private SyndicationService bcp47Service;
    private SyndicationService iso3166Service;
    private SyndicationService m49Service;
    private SyndicationImportStatusService importStatusService;

    @BeforeEach
    void setup() {
        startupService = new StartupSyndicationService();

        snomedService = mock(SyndicationService.class);
        loincService = mock(SyndicationService.class);
        hl7Service = mock(SyndicationService.class);
        atcService = mock(SyndicationService.class);
        ucumService = mock(SyndicationService.class);
        bcp13Service = mock(SyndicationService.class);
        bcp47Service = mock(SyndicationService.class);
        iso3166Service = mock(SyndicationService.class);
        m49Service = mock(SyndicationService.class);
        importStatusService = mock(SyndicationImportStatusService.class);

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
        ReflectionTestUtils.setField(startupService, "importStatusService", importStatusService);
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
        assertTrue(params.isLoincImportIncluded());
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
}
