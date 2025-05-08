package org.snomed.snowstorm.syndication;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.snomed.snowstorm.syndication.models.requestDto.SyndicationImportRequest;
import org.snomed.snowstorm.syndication.models.data.SyndicationImport;
import org.snomed.snowstorm.syndication.services.importstatus.SyndicationImportStatusService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.snomed.snowstorm.syndication.constants.SyndicationConstants.LOINC_TERMINOLOGY;

class SyndicationControllerUnitTest {

    private static final String SYNDICATION_SECRET = "SECRET";
    private SyndicationImportStatusService importStatusService;
    private SyndicationController controller;

    @BeforeEach
    void setup() {
        importStatusService = mock(SyndicationImportStatusService.class);
        controller = new SyndicationController();
        ReflectionTestUtils.setField(controller, "importStatusService", importStatusService);
        ReflectionTestUtils.setField(controller, "syndicationSecret", SYNDICATION_SECRET);
    }

    @Test
    void getSyndicationStatuses_returnsList() {
        SyndicationImport mockImport = new SyndicationImport();
        when(importStatusService.getAllImportStatuses()).thenReturn(List.of(mockImport));

        List<SyndicationImport> result = controller.getSyndicationStatuses();

        assertEquals(1, result.size());
        verify(importStatusService, times(1)).getAllImportStatuses();
    }

    @Test
    void updateTerminology_WrongSecret() throws IOException, InterruptedException, ServiceException {
        ReflectionTestUtils.setField(controller, "syndicationSecret", "badSecret");

        ResponseEntity<String> response = controller.updateTerminology(getRequest());

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("Invalid syndication secret", response.getBody());
    }

    @Test
    void updateTerminology_whenImportRunning_returnsBadRequest() throws IOException, InterruptedException, ServiceException {
        when(importStatusService.isImportRunning()).thenReturn(true);

        ResponseEntity<String> response = controller.updateTerminology(getRequest());

        assertEquals(400, response.getStatusCode().value());
        assertEquals("An import process is still running", response.getBody());
    }

    @Test
    void updateTerminology_whenNewImportStarted_returnsOk() throws Exception {
        when(importStatusService.isImportRunning()).thenReturn(false);
        when(importStatusService.updateTerminology(any())).thenReturn(false);

        ResponseEntity<String> response = controller.updateTerminology(getRequest());

        assertEquals(200, response.getStatusCode().value());
        assertEquals("Update started", response.getBody());
    }

    @Test
    void updateTerminology_whenAlreadyImported_returnsOk() throws Exception {
        when(importStatusService.isImportRunning()).thenReturn(false);
        when(importStatusService.updateTerminology(any())).thenReturn(true);

        ResponseEntity<String> response = controller.updateTerminology(getRequest());

        assertEquals(200, response.getStatusCode().value());
        assertEquals("The specified terminology version has already been imported", response.getBody());
    }

    private static SyndicationImportRequest getRequest() {
        return new SyndicationImportRequest(LOINC_TERMINOLOGY, "2.80", null, SYNDICATION_SECRET);
    }
}
