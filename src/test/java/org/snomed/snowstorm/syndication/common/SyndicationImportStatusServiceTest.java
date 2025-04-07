package org.snomed.snowstorm.syndication.common;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.snomed.snowstorm.core.data.repositories.ImportStatusRepository;
import org.snomed.snowstorm.syndication.data.SyndicationImportStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.mockito.Mockito.*;

class SyndicationImportStatusServiceTest {

    private ImportStatusRepository repository;
    private SyndicationImportStatusService service;

    @BeforeEach
    void setup() {
        repository = mock(ImportStatusRepository.class);
        service = new SyndicationImportStatusService();
        ReflectionTestUtils.setField(service, "repository", repository);
    }

    @Test
    void testSaveOrUpdateImportStatus_successfulSave() {
        // Arrange
        String terminology = "loinc";
        String requestedVersion = "2.80";
        String actualVersion = "2.80";
        boolean success = true;
        String exception = null;

        // Act
        service.saveOrUpdateImportStatus(terminology, requestedVersion, actualVersion, success, exception);

        // Assert
        ArgumentCaptor<SyndicationImportStatus> captor = ArgumentCaptor.forClass(SyndicationImportStatus.class);
        verify(repository).save(captor.capture());
        SyndicationImportStatus saved = captor.getValue();

        assertEquals(terminology, saved.getTerminology());
        assertEquals(requestedVersion, saved.getRequestedVersion());
        assertEquals(actualVersion, saved.getActualVersion());
        assertTrue(saved.isSuccess());
        assertNull(saved.getException());
    }

    @Test
    void testSaveOrUpdateImportStatus_withException() {
        // Arrange
        String terminology = "hl7";
        String requestedVersion = "6.2.0";
        String actualVersion = "6.2.0";
        boolean success = false;
        String exception = "Download failed";

        // Act
        service.saveOrUpdateImportStatus(terminology, requestedVersion, actualVersion, success, exception);

        // Assert
        ArgumentCaptor<SyndicationImportStatus> captor = ArgumentCaptor.forClass(SyndicationImportStatus.class);
        verify(repository).save(captor.capture());
        SyndicationImportStatus saved = captor.getValue();

        assertEquals(terminology, saved.getTerminology());
        assertFalse(saved.isSuccess());
        assertEquals("Download failed", saved.getException());
    }

    @Test
    void testGetImportStatus_found() {
        // Arrange
        String terminology = "snomed";
        SyndicationImportStatus mockStatus = new SyndicationImportStatus(terminology, "20250301", "20250301", true, null);
        when(repository.findById(terminology)).thenReturn(Optional.of(mockStatus));

        // Act
        SyndicationImportStatus result = service.getImportStatus(terminology);

        // Assert
        assertNotNull(result);
        assertEquals(terminology, result.getTerminology());
        assertTrue(result.isSuccess());
    }

    @Test
    void testGetImportStatus_notFound() {
        // Arrange
        String terminology = "nonexistent";
        when(repository.findById(terminology)).thenReturn(Optional.empty());

        // Act
        SyndicationImportStatus result = service.getImportStatus(terminology);

        // Assert
        assertNull(result);
    }
}
