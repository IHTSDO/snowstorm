package org.snomed.snowstorm.syndication.services.importers.customversion.snomed;

import org.ihtsdo.otf.snomedboot.ReleaseImportException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.snomed.snowstorm.core.data.domain.CodeSystem;
import org.snomed.snowstorm.core.data.services.CodeSystemService;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.snomed.snowstorm.core.rf2.rf2import.ImportJob;
import org.snomed.snowstorm.core.rf2.rf2import.ImportService;
import org.snomed.snowstorm.syndication.utils.FileUtils;
import org.snomed.snowstorm.syndication.models.domain.SyndicationImportParams;
import org.snomed.snowstorm.syndication.services.importstatus.SyndicationImportStatusService;
import org.snomed.snowstorm.syndication.constants.SyndicationTerminology;
import org.snomed.snowstorm.syndication.models.integration.SyndicationFeed;
import org.snomed.snowstorm.syndication.models.integration.SyndicationFeedEntry;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.snomed.snowstorm.core.rf2.rf2import.ImportJob.ImportStatus.FAILED;
import static org.snomed.snowstorm.syndication.constants.SyndicationConstants.LOCAL_VERSION;
import static org.snomed.snowstorm.syndication.constants.SyndicationTerminology.SNOMED;

@ExtendWith(MockitoExtension.class)
class SnomedSyndicationServiceTest {

    private static final String RELEASE_VERSION_URI = "http://snomed.info/sct/11000172109/version/20250315";
    private static final String RELEASE_URI = "http://snomed.info/sct/11000172109";

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

        syndicationService.fetchAndImportTerminology(new SyndicationImportParams(SyndicationTerminology.SNOMED, invalidUri, "BE"));

        ArgumentCaptor<ImportJob.ImportStatus> captor = ArgumentCaptor.forClass(ImportJob.ImportStatus.class);

        verify(importStatusService, atLeastOnce())
                .saveOrUpdateImportStatus(eq(SNOMED), eq(invalidUri), eq(null), any(), any());

        verify(importStatusService, atLeastOnce())
                .saveOrUpdateImportStatus(eq(SNOMED), eq(invalidUri), eq(null), captor.capture(), any());

        ImportJob.ImportStatus lastCall = captor.getAllValues().get(captor.getAllValues().size() - 1);

        assertEquals(FAILED, lastCall);    }

    @Test
    void testImportSnomedEditionAndExtension_ValidURI_CallsImportMethods() throws IOException, ServiceException, ReleaseImportException {
        List<File> filePaths = List.of(new File("edition.zip"), new File("extension.zip"));

        when(syndicationClient.downloadPackages(RELEASE_VERSION_URI, "testUser", "testPass")).thenReturn(filePaths);
        doNothing().when(importService).importArchive(any(), any());
        doReturn(null).when(syndicationService).getFileInputStream(any());

        syndicationService.fetchAndImportTerminology(new SyndicationImportParams(SyndicationTerminology.SNOMED, RELEASE_VERSION_URI, "BE"));

        verify(syndicationClient).downloadPackages(RELEASE_VERSION_URI, "testUser", "testPass");
        verify(importService, times(2)).importArchive(any(), any());
    }

    @Test
    void testImportSnomedEditionAndExtension_Local_CallsImportMethods() throws IOException, ServiceException, ReleaseImportException {
        try(var fileUtilsMock = mockStatic(FileUtils.class)) {
            fileUtilsMock.when(() -> FileUtils.findFile(any(), any()))
                    .thenReturn(Optional.of(new File("edition.zip"))).thenReturn(Optional.of(new File("extension.zip")));
            doNothing().when(importService).importArchive(any(), any());
            doReturn(null).when(syndicationService).getFileInputStream(any());
            syndicationService.fetchAndImportTerminology(new SyndicationImportParams(SyndicationTerminology.SNOMED, LOCAL_VERSION, "BE"));

            verify(syndicationClient, never()).downloadPackages(any(), any(), any());
            verify(importService, times(2)).importArchive(any(), any());
        }
    }

    @Test
    void testValidateSyndicationCredentials_BlankUsername_ThrowsException() {
        ReflectionTestUtils.setField(syndicationService, "snomedUsername", "");

        syndicationService.fetchAndImportTerminology(new SyndicationImportParams(SyndicationTerminology.SNOMED, RELEASE_VERSION_URI, "BE"));

        ArgumentCaptor<ImportJob.ImportStatus> captor = ArgumentCaptor.forClass(ImportJob.ImportStatus.class);

        verify(importStatusService, atLeastOnce())
                .saveOrUpdateImportStatus(eq(SNOMED), eq("http://snomed.info/sct/11000172109/version/20250315"), eq(null), any(), any());

        verify(importStatusService, atLeastOnce())
                .saveOrUpdateImportStatus(eq(SNOMED), eq("http://snomed.info/sct/11000172109/version/20250315"), eq(null), captor.capture(), any());

        ImportJob.ImportStatus lastCall = captor.getAllValues().get(captor.getAllValues().size() - 1);

        assertEquals(FAILED, lastCall);
    }

    @Test
    void testValidateSyndicationCredentials_BlankPassword_ThrowsException() {
        ReflectionTestUtils.setField(syndicationService, "snomedPassword", "");

        syndicationService.fetchAndImportTerminology(new SyndicationImportParams(SyndicationTerminology.SNOMED, RELEASE_VERSION_URI, "BE"));

        ArgumentCaptor<ImportJob.ImportStatus> captor = ArgumentCaptor.forClass(ImportJob.ImportStatus.class);

        verify(importStatusService, atLeastOnce())
                .saveOrUpdateImportStatus(eq(SNOMED), eq("http://snomed.info/sct/11000172109/version/20250315"), eq(null), any(), any());

        verify(importStatusService, atLeastOnce())
                .saveOrUpdateImportStatus(eq(SNOMED), eq("http://snomed.info/sct/11000172109/version/20250315"), eq(null), captor.capture(), any());

        ImportJob.ImportStatus lastCall = captor.getAllValues().get(captor.getAllValues().size() - 1);

        assertEquals(FAILED, lastCall);
    }

    @Test
    void testImportExtension_NoExtensionCode_ThrowsException() {
        List<String> filePaths = List.of("edition.zip", "extension.zip");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                ReflectionTestUtils.invokeMethod(syndicationService, "importExtension", RELEASE_VERSION_URI, null, filePaths));

        assertTrue(exception.getMessage().contains("An extension name must be specified."));
    }

    @Test
    void testImportExtension_ValidInput_CreatesCodeSystem() throws ServiceException, FileNotFoundException {
        List<String> filePaths = List.of("edition.zip", "extension.zip");
        doReturn(null).when(syndicationService).getFileInputStream(any());

        ReflectionTestUtils.invokeMethod(syndicationService, "importExtension", RELEASE_VERSION_URI, "BE", filePaths);

        verify(codeSystemService).createCodeSystem(any(CodeSystem.class));
    }

    @Test
    void testGetLatestTerminologyVersion_success() throws IOException {
        SyndicationFeed syndicationFeed = new SyndicationFeed();
        SyndicationFeedEntry syndicationFeedEntry = new SyndicationFeedEntry();
        syndicationFeedEntry.setContentItemVersion(RELEASE_VERSION_URI);
        syndicationFeed.setEntries(List.of(syndicationFeedEntry));
        doReturn(syndicationFeed).when(syndicationClient).getFeed();

        String latestVersion = ReflectionTestUtils.invokeMethod(syndicationService, "getLatestTerminologyVersion", RELEASE_URI);

        assertEquals(RELEASE_VERSION_URI, latestVersion);
    }

    @Test
    void testGetLatestTerminologyVersion_failure() throws IOException {
        doReturn(new SyndicationFeed()).when(syndicationClient).getFeed();

        assertThrows(Exception.class, () -> ReflectionTestUtils.invokeMethod(syndicationService, "getLatestTerminologyVersion", RELEASE_URI));
    }
}
