package org.snomed.snowstorm.syndication;

import org.ihtsdo.otf.snomedboot.ReleaseImportException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.snomed.snowstorm.core.data.services.CodeSystemService;
import org.snomed.snowstorm.core.rf2.RF2Type;
import org.snomed.snowstorm.core.rf2.rf2import.ImportService;
import org.snomed.snowstorm.syndication.client.SyndicationClient;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SyndicationServiceImportFailureTest {

	@Mock
	private SyndicationClient syndicationClient;

	@Mock
	private ImportService importService;

	@Mock
	private CodeSystemService codeSystemService;

	private SyndicationService syndicationService;

	private Method importFileMethod;

	@BeforeEach
	void setUp() throws NoSuchMethodException {
		syndicationService = new SyndicationService(syndicationClient, importService, codeSystemService);
		importFileMethod = SyndicationService.class.getDeclaredMethod(
				"importFile", InstallationTask.class, File.class, String.class, String.class, InstallationPackageProgress.class);
		importFileMethod.setAccessible(true);
	}

	@Test
	void importFilePropagatesIOException() throws Exception {
		File missingArchive = new File("non-existent-syndication-archive-" + System.nanoTime() + ".zip");
		assertFalse(missingArchive.exists());

		InstallationTask task = new InstallationTask(
				"http://snomed.info/sct/900000000000207008", "20250301", null, SecurityContextHolder.getContext());
		InstallationPackageProgress pkg = new InstallationPackageProgress("http://snomed.info/sct/version/20250301", "Test Edition", 1000);
		pkg.beginImportEstimate(30_000L);

		InvocationTargetException thrown = assertThrows(InvocationTargetException.class,
				() -> importFileMethod.invoke(syndicationService, task, missingArchive, "MAIN/SNOMEDCT-TEST", missingArchive.getPath(), pkg));

		assertInstanceOf(IOException.class, thrown.getCause());
		assertNotEquals(InstallationPackageProgress.PHASE_DONE, pkg.getPhase());
	}

	@Test
	void importFilePropagatesReleaseImportException(@TempDir Path tempDir) throws Exception {
		File archive = tempDir.resolve("edition.zip").toFile();
		assertTrue(archive.createNewFile());

		InstallationTask task = new InstallationTask(
				"http://snomed.info/sct/900000000000207008", "20250301", null, SecurityContextHolder.getContext());
		InstallationPackageProgress pkg = new InstallationPackageProgress("http://snomed.info/sct/version/20250301", "Test Edition", 1000);
		pkg.beginImportEstimate(30_000L);

		when(importService.createJob(eq(RF2Type.SNAPSHOT), eq("MAIN/SNOMEDCT-TEST"), eq(false), eq(false)))
				.thenReturn("import-job-1");
		doThrow(new ReleaseImportException("Invalid RF2 archive")).when(importService).importArchive(eq("import-job-1"), any());

		InvocationTargetException thrown = assertThrows(InvocationTargetException.class,
				() -> importFileMethod.invoke(syndicationService, task, archive, "MAIN/SNOMEDCT-TEST", archive.getPath(), pkg));

		assertInstanceOf(ReleaseImportException.class, thrown.getCause());
		assertNotEquals(InstallationPackageProgress.PHASE_DONE, pkg.getPhase());
	}

}
