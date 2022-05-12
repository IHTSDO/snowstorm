package org.snomed.snowstorm.rest;

import org.junit.jupiter.api.Test;
import org.snomed.otf.snomedboot.testutil.ZipUtil;
import org.snomed.snowstorm.core.rf2.RF2Type;
import org.snomed.snowstorm.core.rf2.rf2import.ImportJob;
import org.snomed.snowstorm.rest.pojo.ImportCreationRequest;
import org.snomed.snowstorm.rest.pojo.LocalFileImportCreationRequest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.snomed.snowstorm.rest.ControllerTestHelper.waitForStatus;

class ImportControllerTest extends AbstractControllerSecurityTest {

	@Test
	void testImportLocalFile() throws IOException {
		File rf2Archive = ZipUtil.zipDirectoryRemovingCommentsAndBlankLines("src/main/resources/dummy-snomed-content/RF2Release");

		LocalFileImportCreationRequest importCreationRequest = new LocalFileImportCreationRequest();
		importCreationRequest.setFilePath(rf2Archive.getAbsolutePath());
		assertTrue(rf2Archive.isFile());
		importCreationRequest.setBranchPath("MAIN");
		importCreationRequest.setCreateCodeSystemVersion(true);
		importCreationRequest.setType(RF2Type.SNAPSHOT);

		RequestEntity<Object> request = new RequestEntity<>(importCreationRequest, null, HttpMethod.POST, URI.create(url + "/imports/start-local-file-import"));
		ResponseEntity<String> response = testStatusCode(HttpStatus.CREATED, authorHeaders, request);
		waitForStatus(response, ImportJob.ImportStatus.COMPLETED.name(), ImportJob.ImportStatus.FAILED.name(), authorHeaders, restTemplate);
		// Check local file still exists
		assertTrue(Files.exists(rf2Archive.toPath()));
		rf2Archive.delete();
	}

	@Test
	void testInputUploadedFile() throws IOException {
		File rf2Archive = ZipUtil.zipDirectoryRemovingCommentsAndBlankLines("src/main/resources/dummy-snomed-content/RF2Release");

		ImportCreationRequest importCreationRequest = new ImportCreationRequest();
		importCreationRequest.setBranchPath("MAIN");
		importCreationRequest.setCreateCodeSystemVersion(true);
		importCreationRequest.setType(RF2Type.SNAPSHOT);

		RequestEntity<Object> request = new RequestEntity<>(importCreationRequest, null, HttpMethod.POST, URI.create(url + "/imports"));
		ResponseEntity<String> response = testStatusCode(HttpStatus.CREATED, authorHeaders, request);
		URI location = response.getHeaders().getLocation();

		MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
		body.add("file", new FileSystemResource(rf2Archive));

		assertNotNull(location);
		RequestEntity<Object> requestEntity = new RequestEntity<>(body, HttpMethod.POST, URI.create(location + "/archive"));
		testStatusCode(HttpStatus.OK, authorHeaders, requestEntity);

		waitForStatus(response, ImportJob.ImportStatus.COMPLETED.name(), ImportJob.ImportStatus.FAILED.name(), authorHeaders, restTemplate);
		rf2Archive.delete();
	}

}
