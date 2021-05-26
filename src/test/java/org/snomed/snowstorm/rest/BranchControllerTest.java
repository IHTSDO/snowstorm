package org.snomed.snowstorm.rest;

import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.domain.Metadata;
import org.junit.jupiter.api.Test;
import org.snomed.snowstorm.core.data.services.classification.BranchClassificationStatusService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;

import java.net.URI;
import java.net.URISyntaxException;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BranchControllerTest extends AbstractControllerSecurityTest {

	@Autowired
	private BranchService branchService;

	@Test
	void testGetBranch() throws URISyntaxException {
		final Metadata metadata = new Metadata();
		BranchClassificationStatusService.setClassificationStatus(metadata, true);
		branchService.updateMetadata("MAIN", metadata);

		RequestEntity<Object> request = new RequestEntity<>(HttpMethod.GET, new URI(url + "/branches/MAIN"));

		ResponseEntity<String> response = testStatusCode(HttpStatus.OK, userWithoutRoleHeaders, request);
		assertNotNull(response.getBody());
		assertTrue(response.getBody().contains("\"userRoles\" : [ ],"));
		assertTrue(response.getBody().contains("\"globalUserRoles\" : [ ]"));
		String body = response.getBody();
		body = body.replace("  ", "").replace("\n", "");
		System.out.println("Body");
		System.out.println(body);
		System.out.println();
		assertTrue(body.contains("\"metadata\" : {\"internal\" : {\"classified\" : \"true\"}}"));

		response = testStatusCode(HttpStatus.OK, authorHeaders, request);
		assertNotNull(response.getBody());
		assertTrue(response.getBody().contains("\"userRoles\" : [ \"AUTHOR\" ],"));
		assertTrue(response.getBody().contains("\"globalUserRoles\" : [ ]"));

		response = testStatusCode(HttpStatus.OK, extensionAdminHeaders, request);
		assertNotNull(response.getBody());
		assertTrue(response.getBody().contains("\"userRoles\" : [ ],"));
		assertTrue(response.getBody().contains("\"globalUserRoles\" : [ ]"));

		response = testStatusCode(HttpStatus.OK, globalAdminHeaders, request);
		assertNotNull(response.getBody());
		assertTrue(response.getBody().contains("\"userRoles\" : [ \"ADMIN\" ],"));
		assertTrue(response.getBody().contains("\"globalUserRoles\" : [ \"ADMIN\" ]"));
	}

}
