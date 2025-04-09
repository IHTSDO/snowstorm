package org.snomed.snowstorm.syndication.importstatus;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.snomed.snowstorm.core.data.repositories.ImportStatusRepository;
import org.snomed.snowstorm.core.rf2.rf2import.ImportJob;
import org.snomed.snowstorm.syndication.data.SyndicationImport;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.snomed.snowstorm.core.rf2.rf2import.ImportJob.ImportStatus.COMPLETED;
import static org.snomed.snowstorm.core.rf2.rf2import.ImportJob.ImportStatus.FAILED;
import static org.snomed.snowstorm.core.rf2.rf2import.ImportJob.ImportStatus.RUNNING;

class SyndicationImportServiceTest {

    private ImportStatusRepository repository;
    private SyndicationImportService service;

    @BeforeEach
    void setup() {
        repository = mock(ImportStatusRepository.class);
        service = new SyndicationImportService();
        ReflectionTestUtils.setField(service, "repository", repository);
    }

    @Test
    void testSaveOrUpdateImportStatus_successfulSave() {
        // Arrange
        String terminology = "loinc";
        String requestedVersion = "2.80";
        String actualVersion = "2.80";
        ImportJob.ImportStatus status = COMPLETED;
        String exception = null;

        // Act
        service.saveOrUpdateImportStatus(terminology, requestedVersion, actualVersion, status, exception);

        // Assert
        ArgumentCaptor<SyndicationImport> captor = ArgumentCaptor.forClass(SyndicationImport.class);
        verify(repository).save(captor.capture());
        SyndicationImport saved = captor.getValue();

        assertEquals(terminology, saved.getTerminology());
        assertEquals(requestedVersion, saved.getRequestedVersion());
        assertEquals(actualVersion, saved.getActualVersion());
        assertEquals(status, saved.getStatus());
        assertNull(saved.getException());
    }

    @Test
    void testSaveOrUpdateImportStatus_withException() {
        // Arrange
        String terminology = "hl7";
        String requestedVersion = "6.2.0";
        String actualVersion = "6.2.0";
        ImportJob.ImportStatus status = FAILED;
        String exception = "Download failed";

        // Act
        service.saveOrUpdateImportStatus(terminology, requestedVersion, actualVersion, status, exception);

        // Assert
        ArgumentCaptor<SyndicationImport> captor = ArgumentCaptor.forClass(SyndicationImport.class);
        verify(repository).save(captor.capture());
        SyndicationImport saved = captor.getValue();

        assertEquals(terminology, saved.getTerminology());
        assertEquals(status, saved.getStatus());
        assertEquals("Download failed", saved.getException());
    }

    @Test
    void testGetImportStatus_found() {
        // Arrange
        String terminology = "snomed";
        SyndicationImport mockStatus = new SyndicationImport(terminology, "20250301", "20250301", RUNNING, null);
        when(repository.findById(terminology)).thenReturn(Optional.of(mockStatus));

        // Act
        SyndicationImport result = service.getImportStatus(terminology);

        // Assert
        assertNotNull(result);
        assertEquals(terminology, result.getTerminology());
        assertEquals(RUNNING, result.getStatus());
    }

    @Test
    void testGetImportStatus_notFound() {
        // Arrange
        String terminology = "nonexistent";
        when(repository.findById(terminology)).thenReturn(Optional.empty());

        // Act
        SyndicationImport result = service.getImportStatus(terminology);

        // Assert
        assertNull(result);
    }

    @Test
    void testGetAllImportStatuses_found() {
        // Arrange
        String terminology = "snomed";
        ImportJob.ImportStatus status = RUNNING;
        SyndicationImport mockStatus = new SyndicationImport(terminology, "20250301", "20250301", status, null);
        when(repository.findAll()).thenReturn(List.of(mockStatus));

        // Act
        List<SyndicationImport> result = service.getAllImportStatuses();

        // Assert
        assertNotNull(result);
        SyndicationImport syndicationImport = result.get(0);
        assertNotNull(syndicationImport);
        assertEquals(terminology, syndicationImport.getTerminology());
        assertEquals(status, syndicationImport.getStatus());
    }
}
