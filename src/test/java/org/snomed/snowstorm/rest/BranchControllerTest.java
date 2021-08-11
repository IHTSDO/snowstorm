package org.snomed.snowstorm.rest;

import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.domain.Branch;
import io.kaicode.elasticvc.domain.Metadata;
import org.junit.jupiter.api.Test;
import org.snomed.snowstorm.core.data.services.BranchMetadataHelper;
import org.snomed.snowstorm.core.data.services.IntegrityService;
import org.snomed.snowstorm.core.data.services.classification.BranchClassificationStatusService;
import org.snomed.snowstorm.rest.pojo.SetAuthorFlag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.*;

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

	@Test
	void setAuthorFlag_ShouldReturnExpectedResponse_WhenNoPermission() throws URISyntaxException {
		// given
		String requestUrl = url + "/branches/MAIN/actions/set-author-flag";
		SetAuthorFlag setAuthorFlag = new SetAuthorFlag("complex", true);
		RequestEntity<Object> request = new RequestEntity<>(setAuthorFlag, null, HttpMethod.POST, new URI(requestUrl));

		// when & then
		testStatusCode(HttpStatus.FORBIDDEN, userWithoutRoleHeaders, request);
	}

	@Test
	void setAuthorFlag_ShouldReturnExpectedResponse_WhenBranchNotFound() throws URISyntaxException {
		// given
		String requestUrl = url + "/branches/MAIN/IDoNotExist/actions/set-author-flag";
		SetAuthorFlag setAuthorFlag = new SetAuthorFlag("complex", true);
		RequestEntity<Object> request = new RequestEntity<>(setAuthorFlag, null, HttpMethod.POST, new URI(requestUrl));

		// when & then
		testStatusCode(HttpStatus.NOT_FOUND, authorHeaders, request);
	}

	@Test
	void setAuthorFlag_ShouldReturnExpectedResponse_WhenRequestIsMissingName() throws URISyntaxException {
		// given
		String requestUrl = url + "/branches/MAIN/actions/set-author-flag";
		SetAuthorFlag setAuthorFlag = new SetAuthorFlag(null, true);
		RequestEntity<Object> request = new RequestEntity<>(setAuthorFlag, null, HttpMethod.POST, new URI(requestUrl));

		// when & then
		testStatusCode(HttpStatus.BAD_REQUEST, authorHeaders, request);
	}

	@Test
	void setAuthorFlag_ShouldReturnExpectedResponse_WhenBranchHasBeenUpdated() throws URISyntaxException {
		// given
		String requestUrl = url + "/branches/MAIN/actions/set-author-flag";
		SetAuthorFlag setAuthorFlag = new SetAuthorFlag("complex", true);
		RequestEntity<Object> request = new RequestEntity<>(setAuthorFlag, null, HttpMethod.POST, new URI(requestUrl));

		// when
		ResponseEntity<Object> responseEntity = testExchange(authorHeaders, request);
		LinkedHashMap<String, Object> authorFlags = getAuthorFlags(responseEntity);

		// then
		assertEquals("true", authorFlags.get("complex"));
	}

	@Test
	void setAuthorFlag_ShouldReturnExpectedResponse_WhenRequestingTwice() throws URISyntaxException {
		// given
		String requestUrl = url + "/branches/MAIN/actions/set-author-flag";

		// first request
		SetAuthorFlag setAuthorFlag = new SetAuthorFlag("complex", true);
		RequestEntity<Object> request = new RequestEntity<>(setAuthorFlag, null, HttpMethod.POST, new URI(requestUrl));

		ResponseEntity<Object> responseEntity = testExchange(authorHeaders, request);
		LinkedHashMap<String, Object> authorFlags = getAuthorFlags(responseEntity);

		assertEquals("true", authorFlags.get("complex"));
		assertNull(authorFlags.get("simple"));

		// second request
		setAuthorFlag.setName("simple");
		setAuthorFlag.setValue(true);
		request = new RequestEntity<>(setAuthorFlag, null, HttpMethod.POST, new URI(requestUrl));

		responseEntity = testExchange(authorHeaders, request);
		authorFlags = getAuthorFlags(responseEntity);

		assertEquals("true", authorFlags.get("complex"));
		assertEquals("true", authorFlags.get("simple"));
	}

	@Test
	void setAuthorFlag_ShouldNotOverwritePreviousData() throws URISyntaxException {
		// given
		Metadata metadata = new Metadata();
		metadata.putString("assertionGroupNames", "common-authoring");
		branchService.updateMetadata("MAIN", metadata);
		String requestUrl = url + "/branches/MAIN/actions/set-author-flag";
		SetAuthorFlag setAuthorFlag = new SetAuthorFlag("complex", true);
		RequestEntity<Object> request = new RequestEntity<>(setAuthorFlag, null, HttpMethod.POST, new URI(requestUrl));

		// when
		ResponseEntity<Object> responseEntity = testExchange(authorHeaders, request);
		LinkedHashMap<String, Object> authorFlags = getAuthorFlags(responseEntity);
		LinkedHashMap<String, Object> receivedMetaData = getMetadata(responseEntity);

		// then
		assertEquals("true", authorFlags.get("complex"));
		assertEquals("common-authoring", receivedMetaData.get("assertionGroupNames"));
	}

	private void assertMetadataContains(String rawJson, String expected) {
		rawJson = rawJson.replace("  ", "").replace("\n", "");
		System.out.println("rawJson");
		System.out.println(rawJson);
		System.out.println();
		assertTrue(rawJson.contains(expected));
	}

	@SuppressWarnings("unchecked")
	private LinkedHashMap<String, Object> getMetadata(ResponseEntity<Object> responseEntity) {
		LinkedHashMap<String, Object> body = (LinkedHashMap<String, Object>) responseEntity.getBody();
		if (body == null) {
			return new LinkedHashMap<>();
		}

		return (LinkedHashMap<String, Object>) body.get("metadata");
	}

	@SuppressWarnings("unchecked")
	private LinkedHashMap<String, Object> getAuthorFlags(ResponseEntity<Object> responseEntity) {
		LinkedHashMap<String, Object> metadata = getMetadata(responseEntity);
		return (LinkedHashMap<String, Object>) metadata.get("authorFlags");
	}
}
