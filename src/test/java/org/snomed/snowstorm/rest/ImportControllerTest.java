package org.snomed.snowstorm.rest;

import org.junit.jupiter.api.Test;
import org.snomed.otf.snomedboot.testutil.ZipUtil;
import org.snomed.snowstorm.core.rf2.RF2Type;
import org.snomed.snowstorm.core.rf2.rf2import.ImportJob;
import org.snomed.snowstorm.rest.pojo.LocalFileImportCreationRequest;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;

import java.io.File;
import java.io.IOException;
import java.net.URI;

class ImportControllerTest extends AbstractControllerSecurityTest {

	@Test
	void testImportLocalFile() throws IOException {
		File rf2Archive = ZipUtil.zipDirectoryRemovingCommentsAndBlankLines("src/main/resources/dummy-snomed-content/RF2Release");

		LocalFileImportCreationRequest importCreationRequest = new LocalFileImportCreationRequest();
		importCreationRequest.setFilePath(rf2Archive.getAbsolutePath());
		importCreationRequest.setBranchPath("MAIN");
		importCreationRequest.setCreateCodeSystemVersion(true);
		importCreationRequest.setType(RF2Type.SNAPSHOT);

		RequestEntity<Object> request = new RequestEntity<>(importCreationRequest, null, HttpMethod.POST, URI.create(url + "/imports/start-local-file-import"));
		ResponseEntity<String> response = testStatusCode(HttpStatus.CREATED, authorHeaders, request);
		ControllerTestHelper.waitForStatus(response, ImportJob.ImportStatus.COMPLETED.name(), ImportJob.ImportStatus.FAILED.name(), authorHeaders, restTemplate);
	}

}
