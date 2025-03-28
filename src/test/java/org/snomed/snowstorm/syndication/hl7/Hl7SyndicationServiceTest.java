package org.snomed.snowstorm.syndication.hl7;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.snomed.snowstorm.fhir.services.FHIRLoadPackageService;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class Hl7SyndicationServiceTest {

    @Mock
    private FHIRLoadPackageService loadPackageService;

    @InjectMocks
    private Hl7SyndicationService hl7SyndicationService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        ReflectionTestUtils.setField(hl7SyndicationService, "workingDirectory", tempDir.toString());
    }

    @Test
    void testImportHl7Terminology_Success() throws IOException {
        Path hl7File = tempDir.resolve("hl7.terminology.r4-6.2.0.tgz");
        Files.createFile(hl7File);

        assertDoesNotThrow(() -> hl7SyndicationService.importHl7Terminology());

        verify(loadPackageService, times(1))
                .uploadPackageResources(any(File.class), eq(Set.of("*")), eq("hl7.terminology.r4-6.2.0.tgz"), eq(false));
    }

    @Test
    void testImportHl7Terminology_FileNotFound() throws IOException {
        Exception exception = assertThrows(RuntimeException.class, () -> hl7SyndicationService.importHl7Terminology());

        assertEquals("FHIR package not found!", exception.getMessage());

        verify(loadPackageService, never()).uploadPackageResources(any(), any(), any(), anyBoolean());
    }
}
