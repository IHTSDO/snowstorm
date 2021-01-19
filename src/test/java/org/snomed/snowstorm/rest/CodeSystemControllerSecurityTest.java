package org.snomed.snowstorm.rest;

import org.junit.jupiter.api.Test;
import org.snomed.snowstorm.rest.pojo.CodeSystemMigrationRequest;
import org.snomed.snowstorm.rest.pojo.CodeSystemUpdateRequest;
import org.snomed.snowstorm.rest.pojo.CodeSystemUpgradeRequest;
import org.snomed.snowstorm.rest.pojo.CreateCodeSystemVersionRequest;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;

import java.net.URI;
import java.net.URISyntaxException;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodeSystemControllerSecurityTest extends AbstractControllerSecurityTest {

	@Test
	void createCodeSystem() throws URISyntaxException {
		RequestEntity<Object> request = new RequestEntity<>(extensionBCodeSystem, HttpMethod.POST, new URI(url + "/codesystems"));
		testStatusCode(HttpStatus.FORBIDDEN, userWithoutRoleHeaders, request);
		testStatusCode(HttpStatus.FORBIDDEN, authorHeaders, request);
		testStatusCode(HttpStatus.CREATED, extensionAdminHeaders, request);
		testStatusCode(HttpStatus.BAD_REQUEST, globalAdminHeaders, request);// Already created
	}

	@Test
	void findAll() throws URISyntaxException {
		RequestEntity<Object> request = new RequestEntity<>(HttpMethod.GET, new URI(url + "/codesystems"));

		ResponseEntity<String> response = testStatusCode(HttpStatus.OK, userWithoutRoleHeaders, request);
		assertNotNull(response.getBody());
		assertTrue(response.getBody().contains("\"userRoles\" : [ ]"));

		response = testStatusCode(HttpStatus.OK, authorHeaders, request);
		assertNotNull(response.getBody());
		assertTrue(response.getBody().contains("\"userRoles\" : [ ]"));

		response = testStatusCode(HttpStatus.OK, extensionAdminHeaders, request);
		assertNotNull(response.getBody());
		assertTrue(response.getBody().contains("\"userRoles\" : [ \"ADMIN\" ]"));

		response = testStatusCode(HttpStatus.OK, globalAdminHeaders, request);
		assertNotNull(response.getBody());
		assertTrue(response.getBody().contains("\"userRoles\" : [ \"ADMIN\" ]"));
	}

	@Test
	void updateCodeSystem() throws URISyntaxException {
		RequestEntity<Object> requestMAIN = new RequestEntity<>(new CodeSystemUpdateRequest("International"), HttpMethod.PUT, new URI(url + "/codesystems/" + "SNOMEDCT"));
		testStatusCode(HttpStatus.FORBIDDEN, userWithoutRoleHeaders, requestMAIN);
		testStatusCode(HttpStatus.FORBIDDEN, authorHeaders, requestMAIN);
		testStatusCode(HttpStatus.FORBIDDEN, extensionAdminHeaders, requestMAIN);
		testStatusCode(HttpStatus.OK, globalAdminHeaders, requestMAIN);

		RequestEntity<Object> requestA = new RequestEntity<>(new CodeSystemUpdateRequest("A"), HttpMethod.PUT, new URI(url + "/codesystems/" + "SNOMEDCT-A"));
		testStatusCode(HttpStatus.FORBIDDEN, userWithoutRoleHeaders, requestA);
		testStatusCode(HttpStatus.FORBIDDEN, authorHeaders, requestA);
		testStatusCode(HttpStatus.OK, extensionAdminHeaders, requestA);
		testStatusCode(HttpStatus.OK, globalAdminHeaders, requestA);
	}

	@Test
	void deleteCodeSystem() throws URISyntaxException {
		RequestEntity<Object> requestMAIN = new RequestEntity<>(HttpMethod.DELETE, new URI(url + "/codesystems/" + "SNOMEDCT"));
		testStatusCode(HttpStatus.FORBIDDEN, userWithoutRoleHeaders, requestMAIN);
		testStatusCode(HttpStatus.FORBIDDEN, authorHeaders, requestMAIN);
		testStatusCode(HttpStatus.FORBIDDEN, extensionAdminHeaders, requestMAIN);
		testStatusCode(HttpStatus.BAD_REQUEST, globalAdminHeaders, requestMAIN);

		RequestEntity<Object> requestA = new RequestEntity<>(HttpMethod.DELETE, new URI(url + "/codesystems/" + "SNOMEDCT-A"));
		testStatusCode(HttpStatus.FORBIDDEN, userWithoutRoleHeaders, requestA);
		testStatusCode(HttpStatus.FORBIDDEN, authorHeaders, requestA);
		testStatusCode(HttpStatus.OK, extensionAdminHeaders, requestA);
		testStatusCode(HttpStatus.NOT_FOUND, globalAdminHeaders, requestA);// Already deleted
	}

	@Test
	void findAllVersions() throws URISyntaxException {
		RequestEntity<Object> requestMAIN = new RequestEntity<>(HttpMethod.GET, new URI(url + "/codesystems/" + "SNOMEDCT" + "/versions"));
		testStatusCode(HttpStatus.OK, userWithoutRoleHeaders, requestMAIN);
		testStatusCode(HttpStatus.OK, authorHeaders, requestMAIN);
		testStatusCode(HttpStatus.OK, extensionAdminHeaders, requestMAIN);
		testStatusCode(HttpStatus.OK, globalAdminHeaders, requestMAIN);

		RequestEntity<Object> requestA = new RequestEntity<>(HttpMethod.GET, new URI(url + "/codesystems/" + "SNOMEDCT-A" + "/versions"));
		testStatusCode(HttpStatus.OK, userWithoutRoleHeaders, requestA);
		testStatusCode(HttpStatus.OK, authorHeaders, requestA);
		testStatusCode(HttpStatus.OK, extensionAdminHeaders, requestA);
		testStatusCode(HttpStatus.OK, globalAdminHeaders, requestA);
	}

	@Test
	void createVersion() throws URISyntaxException {
		RequestEntity<Object> requestMAIN = new RequestEntity<>(new CreateCodeSystemVersionRequest(20210131, "Jan 20201"), HttpMethod.POST, new URI(url + "/codesystems/" + "SNOMEDCT" + "/versions"));
		testStatusCode(HttpStatus.FORBIDDEN, userWithoutRoleHeaders, requestMAIN);
		testStatusCode(HttpStatus.FORBIDDEN, authorHeaders, requestMAIN);
		testStatusCode(HttpStatus.FORBIDDEN, extensionAdminHeaders, requestMAIN);
		testStatusCode(HttpStatus.CREATED, globalAdminHeaders, requestMAIN);

		RequestEntity<Object> requestA = new RequestEntity<>(new CreateCodeSystemVersionRequest(20210201, "Feb 20201"), HttpMethod.POST, new URI(url + "/codesystems/" + "SNOMEDCT-A" + "/versions"));
		testStatusCode(HttpStatus.FORBIDDEN, userWithoutRoleHeaders, requestA);
		testStatusCode(HttpStatus.FORBIDDEN, authorHeaders, requestA);
		testStatusCode(HttpStatus.CREATED, extensionAdminHeaders, requestA);
		testStatusCode(HttpStatus.BAD_REQUEST, globalAdminHeaders, requestA);// Done already
	}

	@Test
	void upgradeCodeSystem() throws URISyntaxException {
		RequestEntity<Object> requestMAIN = new RequestEntity<>(new CodeSystemUpgradeRequest(20250101), HttpMethod.POST, new URI(url + "/codesystems/" + "SNOMEDCT" + "/upgrade"));
		testStatusCode(HttpStatus.FORBIDDEN, userWithoutRoleHeaders, requestMAIN);
		testStatusCode(HttpStatus.FORBIDDEN, authorHeaders, requestMAIN);
		testStatusCode(HttpStatus.FORBIDDEN, extensionAdminHeaders, requestMAIN);
		testStatusCode(HttpStatus.BAD_REQUEST, globalAdminHeaders, requestMAIN);

		RequestEntity<Object> requestA = new RequestEntity<>(new CodeSystemUpgradeRequest(20250101), HttpMethod.POST, new URI(url + "/codesystems/" + "SNOMEDCT-A" + "/upgrade"));
		testStatusCode(HttpStatus.FORBIDDEN, userWithoutRoleHeaders, requestA);
		testStatusCode(HttpStatus.FORBIDDEN, authorHeaders, requestA);
		testStatusCode(HttpStatus.BAD_REQUEST, extensionAdminHeaders, requestA);
		testStatusCode(HttpStatus.BAD_REQUEST, globalAdminHeaders, requestA);
	}

	@Test
	void rollbackDailyBuildContent() throws URISyntaxException {
		RequestEntity<Object> requestMAIN = new RequestEntity<>(HttpMethod.POST, new URI(url + "/codesystems/" + "SNOMEDCT" + "/daily-build/rollback"));
		testStatusCode(HttpStatus.FORBIDDEN, userWithoutRoleHeaders, requestMAIN);
		testStatusCode(HttpStatus.FORBIDDEN, authorHeaders, requestMAIN);
		testStatusCode(HttpStatus.FORBIDDEN, extensionAdminHeaders, requestMAIN);
		testStatusCode(HttpStatus.OK, globalAdminHeaders, requestMAIN);

		RequestEntity<Object> requestA = new RequestEntity<>(HttpMethod.POST, new URI(url + "/codesystems/" + "SNOMEDCT-A" + "/daily-build/rollback"));
		testStatusCode(HttpStatus.FORBIDDEN, userWithoutRoleHeaders, requestA);
		testStatusCode(HttpStatus.FORBIDDEN, authorHeaders, requestA);
		testStatusCode(HttpStatus.OK, extensionAdminHeaders, requestA);
		testStatusCode(HttpStatus.OK, globalAdminHeaders, requestA);
	}

	@Test
	void generateAdditionalLanguageRefsetDelta() throws URISyntaxException {
		RequestEntity<Object> requestMAIN = new RequestEntity<>(HttpMethod.POST, new URI(url + "/codesystems/" + "SNOMEDCT" + "/additional-en-language-refset-delta?branchPath=MAIN"));
		testStatusCode(HttpStatus.FORBIDDEN, userWithoutRoleHeaders, requestMAIN);
		testStatusCode(HttpStatus.FORBIDDEN, authorHeaders, requestMAIN);
		testStatusCode(HttpStatus.FORBIDDEN, extensionAdminHeaders, requestMAIN);
		testStatusCode(HttpStatus.BAD_REQUEST, globalAdminHeaders, requestMAIN);

		RequestEntity<Object> requestDK = new RequestEntity<>(HttpMethod.POST, new URI(url + "/codesystems/" + "SNOMEDCT-A" + "/additional-en-language-refset-delta?branchPath=MAIN/SNOMEDCT-A"));
		testStatusCode(HttpStatus.FORBIDDEN, userWithoutRoleHeaders, requestDK);
		testStatusCode(HttpStatus.FORBIDDEN, authorHeaders, requestDK);
		testStatusCode(HttpStatus.BAD_REQUEST, extensionAdminHeaders, requestDK);
		testStatusCode(HttpStatus.BAD_REQUEST, globalAdminHeaders, requestDK);
	}

}
