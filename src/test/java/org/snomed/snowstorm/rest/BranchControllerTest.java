package org.snomed.snowstorm.rest;

import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.domain.Branch;
import io.kaicode.elasticvc.domain.Metadata;
import org.junit.jupiter.api.Test;
import org.snomed.snowstorm.core.data.services.BranchMetadataHelper;
import org.snomed.snowstorm.core.data.services.IntegrityService;
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
		Metadata metadata = new Metadata();
		metadata.putString("assertionGroupNames", "common-authoring");
		BranchClassificationStatusService.setClassificationStatus(metadata, true);
		metadata.getMapOrCreate(BranchMetadataHelper.INTERNAL_METADATA_KEY).put(IntegrityService.INTEGRITY_ISSUE_METADATA_KEY, "true");

		branchService.updateMetadata("MAIN", metadata);

		RequestEntity<Object> request = new RequestEntity<>(HttpMethod.GET, new URI(url + "/branches/MAIN"));

		ResponseEntity<String> response = testStatusCode(HttpStatus.OK, userWithoutRoleHeaders, request);
		assertNotNull(response.getBody());
		assertTrue(response.getBody().contains("\"userRoles\" : [ ],"));
		assertTrue(response.getBody().contains("\"globalUserRoles\" : [ ]"));
		assertMetadataContains(response.getBody(), "\"metadata\" : {\"assertionGroupNames\" : \"common-authoring\",\"internal\" : {\"classified\" : \"true\",\"integrityIssue\" : \"true\"}}");

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

		// create a task and fetch branch metadata from parent branch
		Branch task = branchService.create("MAIN/task1");
		request = new RequestEntity<>(HttpMethod.GET, new URI(url + "/branches/MAIN/task1?includeInheritedMetadata=true"));

		response = testStatusCode(HttpStatus.OK, userWithoutRoleHeaders, request);
		assertNotNull(response.getBody());
		assertMetadataContains(response.getBody(), "\"metadata\" : {\"assertionGroupNames\" : \"common-authoring\",\"internal\" : {\"classified\" : \"true\",\"integrityIssue\" : \"true\"}}");

		// added task metadata
		metadata = new Metadata();
		metadata.putString("assertionGroupNames", "int-authoring");
		BranchClassificationStatusService.setClassificationStatus(metadata, false);
		metadata.getMapOrCreate(BranchMetadataHelper.INTERNAL_METADATA_KEY).put(IntegrityService.INTEGRITY_ISSUE_METADATA_KEY, "false");
		branchService.updateMetadata(task.getPath(), metadata);
		request = new RequestEntity<>(HttpMethod.GET, new URI(url + "/branches/MAIN/task1?includeInheritedMetadata=true"));

		response = testStatusCode(HttpStatus.OK, userWithoutRoleHeaders, request);
		assertNotNull(response.getBody());
		assertMetadataContains(response.getBody(), "\"metadata\" : {\"assertionGroupNames\" : \"int-authoring\",\"internal\" : {\"classified\" : \"false\",\"integrityIssue\" : \"false\"}}");

	}

	private void assertMetadataContains(String rawJson, String expected) {
		rawJson = rawJson.replace("  ", "").replace("\n", "");
		System.out.println("rawJson");
		System.out.println(rawJson);
		System.out.println();
		assertTrue(rawJson.contains(expected));
	}
}
