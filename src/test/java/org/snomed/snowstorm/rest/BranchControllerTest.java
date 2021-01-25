package org.snomed.snowstorm.rest;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;

import java.net.URI;
import java.net.URISyntaxException;

import static org.junit.jupiter.api.Assertions.*;

class BranchControllerTest extends AbstractControllerSecurityTest {

	@Test
	void testGetBranch() throws URISyntaxException {
		RequestEntity<Object> request = new RequestEntity<>(HttpMethod.GET, new URI(url + "/branches/MAIN"));

		ResponseEntity<String> response = testStatusCode(HttpStatus.OK, userWithoutRoleHeaders, request);
		assertNotNull(response.getBody());
		assertTrue(response.getBody().contains("\"userRoles\" : [ ],"));
		assertTrue(response.getBody().contains("\"globalUserRoles\" : [ ]"));

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
