package org.snomed.snowstorm.syndication.snomed;

import org.ihtsdo.otf.snomedboot.ReleaseImportException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.snomed.snowstorm.core.data.domain.CodeSystem;
import org.snomed.snowstorm.core.data.services.CodeSystemService;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.snomed.snowstorm.core.rf2.rf2import.ImportService;
import org.snomed.snowstorm.syndication.common.SyndicationImportParams;
import org.snomed.snowstorm.syndication.common.SyndicationImportStatusService;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SnomedSyndicationServiceTest {

    private static final String RELEASE_URI = "http://snomed.info/sct/11000172109/version/20250315";

    @Mock
    private SnomedSyndicationClient syndicationClient;

    @Mock
    private ImportService importService;

    @Mock
    private CodeSystemService codeSystemService;

    @Mock
    private SyndicationImportStatusService importStatusService;

    @Spy
    @InjectMocks
    private SnomedSyndicationService syndicationService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(syndicationService, "snomedUsername", "testUser");
        ReflectionTestUtils.setField(syndicationService, "snomedPassword", "testPass");
    }

    @Test
    void testImportSnomedEditionAndExtension_InvalidURI_ThrowsException() {
        String invalidUri = "invalid-uri";

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                syndicationService.importTerminologyAndStoreResult(new SyndicationImportParams(invalidUri, "BE", false)));

        assertTrue(exception.getMessage().contains("not a valid SNOMED CT Edition Version URI"));
    }

    @Test
    void testImportSnomedEditionAndExtension_ValidURI_CallsImportMethods() throws IOException, ServiceException, ReleaseImportException, InterruptedException {
        List<File> filePaths = List.of(new File("edition.zip"), new File("extension.zip"));

        when(syndicationClient.downloadPackages(RELEASE_URI, "testUser", "testPass")).thenReturn(filePaths);
        doNothing().when(importService).importArchive(any(), any());
        doReturn(null).when(syndicationService).getFileInputStream(any());

        syndicationService.importTerminologyAndStoreResult(new SyndicationImportParams(RELEASE_URI, "BE", false));

        verify(syndicationClient).downloadPackages(RELEASE_URI, "testUser", "testPass");
        verify(importService, times(2)).importArchive(any(), any());
    }

    @Test
    void testValidateSyndicationCredentials_BlankUsername_ThrowsException() {
        ReflectionTestUtils.setField(syndicationService, "snomedUsername", "");


        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                syndicationService.importTerminologyAndStoreResult(new SyndicationImportParams(RELEASE_URI, "BE", false)));

        assertEquals("Syndication username is blank.", exception.getMessage());
    }

    @Test
    void testValidateSyndicationCredentials_BlankPassword_ThrowsException() {
        ReflectionTestUtils.setField(syndicationService, "snomedPassword", "");


        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                syndicationService.importTerminologyAndStoreResult(new SyndicationImportParams(RELEASE_URI, "BE", false)));

        assertEquals("Syndication password is blank.", exception.getMessage());
    }

    @Test
    void testImportExtension_NoExtensionCode_ThrowsException() {
        List<String> filePaths = List.of("edition.zip", "extension.zip");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                ReflectionTestUtils.invokeMethod(syndicationService, "importExtension", RELEASE_URI, null, filePaths));

        assertTrue(exception.getMessage().contains("An extension name must be specified."));
    }

    @Test
    void testImportExtension_ValidInput_CreatesCodeSystem() throws ServiceException, FileNotFoundException {
        List<String> filePaths = List.of("edition.zip", "extension.zip");
        doReturn(null).when(syndicationService).getFileInputStream(any());

        ReflectionTestUtils.invokeMethod(syndicationService, "importExtension", RELEASE_URI, "BE", filePaths);

        verify(codeSystemService).createCodeSystem(any(CodeSystem.class));
    }
}
