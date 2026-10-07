package org.snomed.snowstorm.rest;

import org.junit.jupiter.api.Test;
import org.snomed.snowstorm.core.rf2.RF2Type;
import org.snomed.snowstorm.rest.pojo.ExportRequestView;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;

import java.net.URI;
import java.net.URISyntaxException;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class ExportControllerSecurityTest extends AbstractControllerSecurityTest {

	@Test
	void createExportJob() throws URISyntaxException {
		// given
		ExportRequestView body = new ExportRequestView();
		body.setBranchPath("MAIN");
		body.setType(RF2Type.DELTA);
		RequestEntity<Object> request = new RequestEntity<>(body, HttpMethod.POST, new URI(url + "/exports"));

		// when then
		testStatusCode(HttpStatus.FORBIDDEN, userWithoutRoleHeaders, request);
		testStatusCode(HttpStatus.CREATED, authorHeaders, request);
		testStatusCode(HttpStatus.FORBIDDEN, extensionAuthorHeaders, request);
		testStatusCode(HttpStatus.FORBIDDEN, extensionAdminHeaders, request);
		testStatusCode(HttpStatus.FORBIDDEN, globalAdminHeaders, request);
	}

	@Test
	void getExportJob() throws URISyntaxException {
		// given
		String exportId = createExportJobAsAuthor();
		RequestEntity<Object> getRequest = new RequestEntity<>(HttpMethod.GET, new URI(url + "/exports/" + exportId));

		// when / then
		testStatusCode(HttpStatus.OK, authorHeaders, getRequest);
		testStatusCode(HttpStatus.FORBIDDEN, userWithoutRoleHeaders, getRequest);
		testStatusCode(HttpStatus.FORBIDDEN, extensionAuthorHeaders, getRequest);
		testStatusCode(HttpStatus.FORBIDDEN, extensionAdminHeaders, getRequest);
		testStatusCode(HttpStatus.FORBIDDEN, globalAdminHeaders, getRequest);
	}

	@Test
	void downloadExportArchive() throws URISyntaxException {
		// given
		String exportId = createExportJobAsAuthor();
		RequestEntity<Object> archiveRequest = new RequestEntity<>(HttpMethod.GET, new URI(url + "/exports/" + exportId + "/archive"));

		// when / then
		testStatusCode(HttpStatus.FORBIDDEN, userWithoutRoleHeaders, archiveRequest);
		testStatusCode(HttpStatus.FORBIDDEN, extensionAuthorHeaders, archiveRequest);
		testStatusCode(HttpStatus.FORBIDDEN, extensionAdminHeaders, archiveRequest);
		testStatusCode(HttpStatus.FORBIDDEN, globalAdminHeaders, archiveRequest);
		testStatusCode(HttpStatus.OK, authorHeaders, archiveRequest);
	}

	@Test
	void userCannotExportFromBranchTheyHaveNoAccessTo() throws URISyntaxException {
		// given
		ExportRequestView extensionBody = new ExportRequestView();
		extensionBody.setBranchPath("MAIN/SNOMEDCT-A");
		extensionBody.setType(RF2Type.DELTA);
		RequestEntity<Object> extensionRequest = new RequestEntity<>(extensionBody, HttpMethod.POST, new URI(url + "/exports"));

		ExportRequestView mainBody = new ExportRequestView();
		mainBody.setBranchPath("MAIN");
		mainBody.setType(RF2Type.DELTA);
		RequestEntity<Object> mainRequest = new RequestEntity<>(mainBody, HttpMethod.POST, new URI(url + "/exports"));

		// when / then
		testStatusCode(HttpStatus.FORBIDDEN, authorHeaders, extensionRequest);
		testStatusCode(HttpStatus.FORBIDDEN, extensionAuthorHeaders, mainRequest);
		testStatusCode(HttpStatus.CREATED, authorHeaders, mainRequest);
		testStatusCode(HttpStatus.CREATED, extensionAuthorHeaders, extensionRequest);
	}

	@Test
	void userCannotReadExportJobFromBranchTheyHaveNoAccessTo() throws URISyntaxException {
		// given
		String exportId = createExportJobAs(extensionAuthorHeaders, "MAIN/SNOMEDCT-A");
		RequestEntity<Object> getRequest = new RequestEntity<>(HttpMethod.GET, new URI(url + "/exports/" + exportId));

		// when / then
		testStatusCode(HttpStatus.FORBIDDEN, authorHeaders, getRequest);
		testStatusCode(HttpStatus.OK, extensionAuthorHeaders, getRequest);
	}

	private String createExportJobAsAuthor() throws URISyntaxException {
		return createExportJobAs(authorHeaders, "MAIN");
	}

	private String createExportJobAs(HttpHeaders headers, String branchPath) throws URISyntaxException {
		ExportRequestView body = new ExportRequestView();
		body.setBranchPath(branchPath);
		body.setType(RF2Type.DELTA);
		RequestEntity<Object> createRequest = new RequestEntity<>(body, HttpMethod.POST, new URI(url + "/exports"));
		ResponseEntity<String> createResponse = testStatusCode(HttpStatus.CREATED, headers, createRequest);
		String location = createResponse.getHeaders().getLocation().toString();
		String exportId = location.substring(location.lastIndexOf('/') + 1);
		assertNotNull(exportId);
		return exportId;
	}
}
