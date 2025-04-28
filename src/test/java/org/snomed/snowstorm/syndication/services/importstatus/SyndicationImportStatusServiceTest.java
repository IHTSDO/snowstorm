package org.snomed.snowstorm.syndication.services.importstatus;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.snomed.snowstorm.core.rf2.rf2import.ImportJob;
import org.snomed.snowstorm.syndication.models.requestDto.SyndicationImportRequest;
import org.snomed.snowstorm.syndication.services.importers.SyndicationService;
import org.snomed.snowstorm.syndication.constants.SyndicationTerminology;
import org.snomed.snowstorm.syndication.models.data.SyndicationImport;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import static org.mockito.Mockito.*;
import static org.snomed.snowstorm.core.rf2.rf2import.ImportJob.ImportStatus.*;
import static org.snomed.snowstorm.syndication.constants.SyndicationTerminology.HL7;
import static org.snomed.snowstorm.syndication.constants.SyndicationTerminology.LOINC;
import static org.snomed.snowstorm.syndication.constants.SyndicationTerminology.SNOMED;

class SyndicationImportStatusServiceTest {

    private SyndicationImportStatusDao syndicationImportStatusDao;
    private SyndicationImportStatusService service;
    private SyndicationService mockSyndicationService;
    private ExecutorService executorService;


    @BeforeEach
    void setup() {
        syndicationImportStatusDao = mock(SyndicationImportStatusDao.class);
        mockSyndicationService = mock(SyndicationService.class);
        executorService = mock(ExecutorService.class);

        service = new SyndicationImportStatusService();
        ReflectionTestUtils.setField(service, "syndicationImportStatusDao", syndicationImportStatusDao);
        ReflectionTestUtils.setField(service, "executorService", executorService);

        Map<String, SyndicationService> syndicationServices = new HashMap<>();
        syndicationServices.put(LOINC.getName(), mockSyndicationService);
        syndicationServices.put(HL7.getName(), mockSyndicationService);
        syndicationServices.put(SNOMED.getName(), mockSyndicationService);
        ReflectionTestUtils.setField(service, "syndicationServices", syndicationServices);
    }

    @Test
    void testSaveOrUpdateImportStatus_successfulSave() {
        SyndicationTerminology terminology = LOINC;
        String requestedVersion = "2.80";
        String actualVersion = "2.80";
        ImportJob.ImportStatus status = COMPLETED;
        String exception = null;

        service.saveOrUpdateImportStatus(terminology, requestedVersion, actualVersion, status, exception);

        verify(syndicationImportStatusDao).saveOrUpdateImportStatus(eq(terminology.getName()), eq(requestedVersion), eq(actualVersion), eq(status), isNull());
    }

    @Test
    void testSaveOrUpdateImportStatus_withException() {
        SyndicationTerminology terminology = SyndicationTerminology.HL7;
        String requestedVersion = "6.2.0";
        String actualVersion = "6.2.0";
        ImportJob.ImportStatus status = FAILED;
        String exception = "Download failed";

        service.saveOrUpdateImportStatus(terminology, requestedVersion, actualVersion, status, exception);

        verify(syndicationImportStatusDao).saveOrUpdateImportStatus(eq(terminology.getName()), eq(requestedVersion), eq(actualVersion), eq(status), eq(exception));
    }

    @Test
    void testGetImportStatus_found() {
        SyndicationTerminology terminology = SyndicationTerminology.SNOMED;
        SyndicationImport mockStatus = new SyndicationImport(terminology.getName(), "20250301", "20250301", RUNNING, null);
        when(syndicationImportStatusDao.getImportStatus(terminology.getName())).thenReturn(mockStatus);

        SyndicationImport result = service.getImportStatus(terminology);

        assertNotNull(result);
        assertEquals(terminology.getName(), result.getTerminology());
        assertEquals(RUNNING, result.getStatus());
    }

    @Test
    void testGetImportStatus_notFound() {

        when(syndicationImportStatusDao.getImportStatus(anyString())).thenReturn(null);

        SyndicationImport result = service.getImportStatus(LOINC);

        assertNull(result);
    }

    @Test
    void testGetAllImportStatuses_found() {
        String terminology = "snomed";
        ImportJob.ImportStatus status = RUNNING;
        SyndicationImport mockStatus = new SyndicationImport(terminology, "20250301", "20250301", status, null);
        when(syndicationImportStatusDao.getAllImportStatuses()).thenReturn(List.of(mockStatus));

        List<SyndicationImport> result = service.getAllImportStatuses();

        assertNotNull(result);
        SyndicationImport syndicationImport = result.get(0);
        assertNotNull(syndicationImport);
        assertEquals(terminology, syndicationImport.getTerminology());
        assertEquals(status, syndicationImport.getStatus());
    }

    @Test
    void testIsLoincPresent_true() {
        SyndicationImport loincImport = new SyndicationImport(LOINC.getName(), "v", "v", COMPLETED, null);
        when(syndicationImportStatusDao.getAllImportStatuses()).thenReturn(List.of(loincImport));

        assertTrue(service.isLoincPresent());
    }

    @Test
    void testIsLoincPresent_false() {
        SyndicationImport hl7Import = new SyndicationImport(HL7.getName(), "v", "v", COMPLETED, null);
        when(syndicationImportStatusDao.getAllImportStatuses()).thenReturn(List.of(hl7Import));

        assertFalse(service.isLoincPresent());
    }

    @Test
    void testUpdateTerminology_success() throws Exception {
        SyndicationImportRequest request = new SyndicationImportRequest(LOINC.getName(), "2.80", null, null);
        SyndicationImport notRunningStatus = new SyndicationImport(LOINC.getName(), "2.79", "2.79", COMPLETED, null);

        when(syndicationImportStatusDao.getAllImportStatuses()).thenReturn(List.of());
        when(syndicationImportStatusDao.getImportStatus(LOINC.getName())).thenReturn(notRunningStatus);

        boolean alreadyImported = service.updateTerminology(request);

        assertFalse(alreadyImported);
        verify(executorService).submit(any(Runnable.class));
    }

    @Test
    void testUpdateTerminology_alreadyImported() throws Exception {
        SyndicationImportRequest request = new SyndicationImportRequest(LOINC.getName(), "2.80", null, null);
        SyndicationImport notRunningStatus = new SyndicationImport(LOINC.getName(), "2.79", "2.79", COMPLETED, null);

        when(syndicationImportStatusDao.getAllImportStatuses()).thenReturn(List.of());
        when(syndicationImportStatusDao.getImportStatus(LOINC.getName())).thenReturn(notRunningStatus);
        when(mockSyndicationService.alreadyImported(any(), any())).thenReturn(true);

        boolean alreadyImported = service.updateTerminology(request);

        assertTrue(alreadyImported);
        verify(executorService, never()).submit(any(Runnable.class));
    }

    @Test
    void testIsImportRunning_true() {
        SyndicationImport runningStatus = new SyndicationImport(SNOMED.getName(), "20250301", "20250301", RUNNING, null);

        when(syndicationImportStatusDao.getAllImportStatuses()).thenReturn(List.of(runningStatus));

        assertTrue(service.isImportRunning());
    }
}
