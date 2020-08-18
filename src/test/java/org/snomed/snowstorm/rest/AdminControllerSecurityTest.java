package org.snomed.snowstorm.rest;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;

class AdminControllerSecurityTest extends AbstractControllerSecurityTest {

	@Test
	void rebuildDescriptionIndexForLanguage() throws URISyntaxException {
		RequestEntity<Object> request = new RequestEntity<>(HttpMethod.POST, new URI(url + "/admin/actions/rebuild-description-index-for-language?languageCode=it"));
		testStatusCode(HttpStatus.FORBIDDEN, userWithoutRoleHeaders, request);
		testStatusCode(HttpStatus.FORBIDDEN, authorHeaders, request);
		testStatusCode(HttpStatus.FORBIDDEN, extensionAdminHeaders, request);
		testStatusCode(HttpStatus.OK, globalAdminHeaders, request);
	}

	@Test
	void rebuildBranchTransitiveClosure() throws URISyntaxException {
		RequestEntity<Object> requestMAIN = new RequestEntity<>(HttpMethod.POST, new URI(url + "/admin/" + "MAIN" + "/actions/rebuild-semantic-index"));
		testStatusCode(HttpStatus.FORBIDDEN, userWithoutRoleHeaders, requestMAIN);
		testStatusCode(HttpStatus.FORBIDDEN, authorHeaders, requestMAIN);
		testStatusCode(HttpStatus.FORBIDDEN, extensionAdminHeaders, requestMAIN);
		testStatusCode(HttpStatus.OK, globalAdminHeaders, requestMAIN);

		RequestEntity<Object> requestA = new RequestEntity<>(HttpMethod.POST, new URI(url + "/admin/" + "MAIN/SNOMEDCT-A" + "/actions/rebuild-semantic-index"));
		testStatusCode(HttpStatus.FORBIDDEN, userWithoutRoleHeaders, requestA);
		testStatusCode(HttpStatus.FORBIDDEN, authorHeaders, requestA);
		testStatusCode(HttpStatus.OK, extensionAdminHeaders, requestA);
		testStatusCode(HttpStatus.OK, globalAdminHeaders, requestA);
	}

	@Test
	void updateDefinitionStatuses() throws URISyntaxException {
		RequestEntity<Object> requestMAIN = new RequestEntity<>(HttpMethod.POST, new URI(url + "/admin/" + "MAIN" + "/actions/update-definition-statuses"));
		testStatusCode(HttpStatus.FORBIDDEN, userWithoutRoleHeaders, requestMAIN);
		testStatusCode(HttpStatus.FORBIDDEN, authorHeaders, requestMAIN);
		testStatusCode(HttpStatus.FORBIDDEN, extensionAdminHeaders, requestMAIN);
		testStatusCode(HttpStatus.OK, globalAdminHeaders, requestMAIN);

		RequestEntity<Object> requestA = new RequestEntity<>(HttpMethod.POST, new URI(url + "/admin/" + "MAIN/SNOMEDCT-A" + "/actions/update-definition-statuses"));
		testStatusCode(HttpStatus.FORBIDDEN, userWithoutRoleHeaders, requestA);
		testStatusCode(HttpStatus.FORBIDDEN, authorHeaders, requestA);
		testStatusCode(HttpStatus.OK, extensionAdminHeaders, requestA);
		testStatusCode(HttpStatus.OK, globalAdminHeaders, requestA);
	}

	@Test
	void endDonatedContent() throws URISyntaxException {
		RequestEntity<Object> requestMAIN = new RequestEntity<>(HttpMethod.POST, new URI(url + "/admin/" + "MAIN" + "/actions/end-donated-content"));
		testStatusCode(HttpStatus.FORBIDDEN, userWithoutRoleHeaders, requestMAIN);
		testStatusCode(HttpStatus.FORBIDDEN, authorHeaders, requestMAIN);
		testStatusCode(HttpStatus.FORBIDDEN, extensionAdminHeaders, requestMAIN);
		// Allow but bad request
		testStatusCode(HttpStatus.BAD_REQUEST, globalAdminHeaders, requestMAIN);

		RequestEntity<Object> requestA = new RequestEntity<>(HttpMethod.POST, new URI(url + "/admin/" + "MAIN/SNOMEDCT-A" + "/actions/end-donated-content"));
		// Deny
		testStatusCode(HttpStatus.FORBIDDEN, userWithoutRoleHeaders, requestA);
		testStatusCode(HttpStatus.FORBIDDEN, authorHeaders, requestA);
		// Allow
		testStatusCode(HttpStatus.OK, extensionAdminHeaders, requestA);
		testStatusCode(HttpStatus.OK, globalAdminHeaders, requestA);
	}

	@Test
	void findDuplicateAndHideParentVersion() throws URISyntaxException {
		RequestEntity<Object> requestMAIN = new RequestEntity<>(HttpMethod.POST, new URI(url + "/admin/" + "MAIN/ProjectA" + "/actions/find-duplicate-hide-parent-version"));
		testStatusCode(HttpStatus.FORBIDDEN, userWithoutRoleHeaders, requestMAIN);
		testStatusCode(HttpStatus.FORBIDDEN, authorHeaders, requestMAIN);
		testStatusCode(HttpStatus.FORBIDDEN, extensionAdminHeaders, requestMAIN);
		testStatusCode(HttpStatus.OK, globalAdminHeaders, requestMAIN);

		RequestEntity<Object> requestA = new RequestEntity<>(HttpMethod.POST, new URI(url + "/admin/" + "MAIN/SNOMEDCT-A" + "/actions/find-duplicate-hide-parent-version"));
		testStatusCode(HttpStatus.FORBIDDEN, userWithoutRoleHeaders, requestA);
		testStatusCode(HttpStatus.FORBIDDEN, authorHeaders, requestA);
		testStatusCode(HttpStatus.OK, extensionAdminHeaders, requestA);
		testStatusCode(HttpStatus.OK, globalAdminHeaders, requestA);
	}

	@Test
	void rollbackCommit() throws URISyntaxException {
		RequestEntity<Object> requestMAIN = new RequestEntity<>(HttpMethod.POST, new URI(url + "/admin/" + "MAIN" + "/actions/rollback-commit?commitHeadTime=123"));
		testStatusCode(HttpStatus.FORBIDDEN, userWithoutRoleHeaders, requestMAIN);
		testStatusCode(HttpStatus.FORBIDDEN, authorHeaders, requestMAIN);
		testStatusCode(HttpStatus.FORBIDDEN, extensionAdminHeaders, requestMAIN);
		testStatusCode(HttpStatus.BAD_REQUEST, globalAdminHeaders, requestMAIN);

		RequestEntity<Object> requestA = new RequestEntity<>(HttpMethod.POST, new URI(url + "/admin/" + "MAIN/SNOMEDCT-A" + "/actions/rollback-commit?commitHeadTime=123"));
		// Deny
		testStatusCode(HttpStatus.FORBIDDEN, userWithoutRoleHeaders, requestA);
		testStatusCode(HttpStatus.FORBIDDEN, authorHeaders, requestA);
		testStatusCode(HttpStatus.BAD_REQUEST, extensionAdminHeaders, requestA);
		testStatusCode(HttpStatus.BAD_REQUEST, globalAdminHeaders, requestA);
	}

	@Test
	void hardDeleteBranch() throws URISyntaxException {
		RequestEntity<Object> requestMAIN = new RequestEntity<>(HttpMethod.DELETE, new URI(url + "/admin/" + "MAIN/ProjectA" + "/actions/hard-delete"));
		testStatusCode(HttpStatus.FORBIDDEN, userWithoutRoleHeaders, requestMAIN);
		testStatusCode(HttpStatus.FORBIDDEN, authorHeaders, requestMAIN);
		testStatusCode(HttpStatus.FORBIDDEN, extensionAdminHeaders, requestMAIN);
		testStatusCode(HttpStatus.OK, globalAdminHeaders, requestMAIN);

		RequestEntity<Object> requestA = new RequestEntity<>(HttpMethod.DELETE, new URI(url + "/admin/" + "MAIN/SNOMEDCT-A" + "/actions/hard-delete"));
		testStatusCode(HttpStatus.FORBIDDEN, userWithoutRoleHeaders, requestA);
		testStatusCode(HttpStatus.FORBIDDEN, authorHeaders, requestA);
		testStatusCode(HttpStatus.OK, extensionAdminHeaders, requestA);
		testStatusCode(HttpStatus.NOT_FOUND, globalAdminHeaders, requestA);
	}

	@Test
	void restoreGroupNumberOfInactiveRelationships() throws URISyntaxException {
		RequestEntity<Object> requestMAIN = new RequestEntity<>(HttpMethod.POST, new URI(url + "/admin/" + "MAIN" + "/actions/inactive-relationships-restore-group-number?currentEffectiveTime=200731&previousReleaseBranch=MAIN/2020-07-31"));
		testStatusCode(HttpStatus.FORBIDDEN, userWithoutRoleHeaders, requestMAIN);
		testStatusCode(HttpStatus.FORBIDDEN, authorHeaders, requestMAIN);
		testStatusCode(HttpStatus.FORBIDDEN, extensionAdminHeaders, requestMAIN);
		testStatusCode(HttpStatus.BAD_REQUEST, globalAdminHeaders, requestMAIN);

		RequestEntity<Object> requestA = new RequestEntity<>(HttpMethod.POST, new URI(url + "/admin/" + "MAIN/SNOMEDCT-A" + "/actions/inactive-relationships-restore-group-number?currentEffectiveTime=200731&previousReleaseBranch=MAIN/2020-07-31"));
		testStatusCode(HttpStatus.FORBIDDEN, userWithoutRoleHeaders, requestA);
		testStatusCode(HttpStatus.FORBIDDEN, authorHeaders, requestA);
		testStatusCode(HttpStatus.BAD_REQUEST, extensionAdminHeaders, requestA);
		testStatusCode(HttpStatus.BAD_REQUEST, globalAdminHeaders, requestA);
	}

	@Test
	void deleteExtraInferredRelationships() throws URISyntaxException, IOException {
		HttpHeaders httpHeaders = new HttpHeaders();
		httpHeaders.setContentType(MediaType.MULTIPART_FORM_DATA);
		MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
		File temp = Files.createTempFile("temp-test-file", ".txt").toFile();
		body.add("relationshipsToKeep", new FileSystemResource(temp));
		RequestEntity<Object> requestMAIN = new RequestEntity<>(body, httpHeaders, HttpMethod.POST, new URI(url + "/admin/" + "MAIN" + "/actions/delete-extra-inferred-relationships?effectiveTime=20200731"));
		testStatusCode(HttpStatus.FORBIDDEN, userWithoutRoleHeaders, requestMAIN);
		testStatusCode(HttpStatus.FORBIDDEN, authorHeaders, requestMAIN);
		testStatusCode(HttpStatus.FORBIDDEN, extensionAdminHeaders, requestMAIN);
		testStatusCode(HttpStatus.BAD_REQUEST, globalAdminHeaders, requestMAIN);

		RequestEntity<Object> requestA = new RequestEntity<>(body, httpHeaders, HttpMethod.POST, new URI(url + "/admin/" + "MAIN/SNOMEDCT-A" + "/actions/delete-extra-inferred-relationships?effectiveTime=20200731"));
		testStatusCode(HttpStatus.FORBIDDEN, userWithoutRoleHeaders, requestA);
		testStatusCode(HttpStatus.FORBIDDEN, authorHeaders, requestA);
		testStatusCode(HttpStatus.BAD_REQUEST, extensionAdminHeaders, requestA);
		testStatusCode(HttpStatus.BAD_REQUEST, globalAdminHeaders, requestA);
	}

	@Test
	void promoteReleaseFix() throws URISyntaxException {
		RequestEntity<Object> requestMAIN = new RequestEntity<>(HttpMethod.POST, new URI(url + "/admin/" + "MAIN" + "/actions/promote-release-fix"));
		testStatusCode(HttpStatus.FORBIDDEN, userWithoutRoleHeaders, requestMAIN);
		testStatusCode(HttpStatus.FORBIDDEN, authorHeaders, requestMAIN);
		testStatusCode(HttpStatus.FORBIDDEN, extensionAdminHeaders, requestMAIN);
		testStatusCode(HttpStatus.BAD_REQUEST, globalAdminHeaders, requestMAIN);

		RequestEntity<Object> requestA = new RequestEntity<>(HttpMethod.POST, new URI(url + "/admin/" + "MAIN/SNOMEDCT-A" + "/actions/promote-release-fix"));
		testStatusCode(HttpStatus.FORBIDDEN, userWithoutRoleHeaders, requestA);
		testStatusCode(HttpStatus.FORBIDDEN, authorHeaders, requestA);
		testStatusCode(HttpStatus.BAD_REQUEST, extensionAdminHeaders, requestA);
		testStatusCode(HttpStatus.BAD_REQUEST, globalAdminHeaders, requestA);
	}

	@Test
	void cloneChildBranch() throws URISyntaxException {
		RequestEntity<Object> requestMAIN = new RequestEntity<>(HttpMethod.POST, new URI(url + "/admin/" + "MAIN/ProjectA" + "/actions/clone-child-branch?newBranch=MAIN/ProjectA1"));
		testStatusCode(HttpStatus.FORBIDDEN, userWithoutRoleHeaders, requestMAIN);
		testStatusCode(HttpStatus.FORBIDDEN, authorHeaders, requestMAIN);
		testStatusCode(HttpStatus.FORBIDDEN, extensionAdminHeaders, requestMAIN);
		testStatusCode(HttpStatus.OK, globalAdminHeaders, requestMAIN);

		RequestEntity<Object> requestA = new RequestEntity<>(HttpMethod.POST, new URI(url + "/admin/" + "MAIN/SNOMEDCT-A" + "/actions/clone-child-branch?newBranch=MAIN/SNOMEDCT-A123"));
		testStatusCode(HttpStatus.FORBIDDEN, userWithoutRoleHeaders, requestA);
		testStatusCode(HttpStatus.FORBIDDEN, authorHeaders, requestA);
		testStatusCode(HttpStatus.OK, extensionAdminHeaders, requestA);
		testStatusCode(HttpStatus.BAD_REQUEST, globalAdminHeaders, requestA);// Previous call already made the branch
	}

	@Test
	void updateMRCMDomainTemplatesAndAttributeRules() throws URISyntaxException {
		RequestEntity<Object> requestMAIN = new RequestEntity<>(HttpMethod.POST, new URI(url + "/admin/" + "MAIN" + "/actions/update-mrcm-domain-templates-and-attribute-rules"));
		testStatusCode(HttpStatus.FORBIDDEN, userWithoutRoleHeaders, requestMAIN);
		testStatusCode(HttpStatus.FORBIDDEN, authorHeaders, requestMAIN);
		testStatusCode(HttpStatus.FORBIDDEN, extensionAdminHeaders, requestMAIN);
		testStatusCode(HttpStatus.OK, globalAdminHeaders, requestMAIN);
	}

	@Test
	void findExtraConceptsInSemanticIndex() throws URISyntaxException {
		RequestEntity<Object> requestMAIN = new RequestEntity<>(HttpMethod.POST, new URI(url + "/admin/" + "MAIN" + "/actions/find-extra-concepts-in-semantic-index"));
		testStatusCode(HttpStatus.FORBIDDEN, userWithoutRoleHeaders, requestMAIN);
		testStatusCode(HttpStatus.FORBIDDEN, authorHeaders, requestMAIN);
		testStatusCode(HttpStatus.FORBIDDEN, extensionAdminHeaders, requestMAIN);
		testStatusCode(HttpStatus.OK, globalAdminHeaders, requestMAIN);

		RequestEntity<Object> requestA = new RequestEntity<>(HttpMethod.POST, new URI(url + "/admin/" + "MAIN/SNOMEDCT-A" + "/actions/find-extra-concepts-in-semantic-index"));
		testStatusCode(HttpStatus.FORBIDDEN, userWithoutRoleHeaders, requestA);
		testStatusCode(HttpStatus.FORBIDDEN, authorHeaders, requestA);
		testStatusCode(HttpStatus.OK, extensionAdminHeaders, requestA);
		testStatusCode(HttpStatus.OK, globalAdminHeaders, requestA);
	}

	@Test
	void restoreReleasedStatus() throws URISyntaxException {
		RequestEntity<Object> requestMAIN = new RequestEntity<>(HttpMethod.POST, new URI(url + "/admin/" + "MAIN" + "/actions/restore-released-status?conceptId=123"));
		testStatusCode(HttpStatus.FORBIDDEN, userWithoutRoleHeaders, requestMAIN);
		testStatusCode(HttpStatus.FORBIDDEN, authorHeaders, requestMAIN);
		testStatusCode(HttpStatus.FORBIDDEN, extensionAdminHeaders, requestMAIN);
		testStatusCode(HttpStatus.BAD_REQUEST, globalAdminHeaders, requestMAIN);

		RequestEntity<Object> requestA = new RequestEntity<>(HttpMethod.POST, new URI(url + "/admin/" + "MAIN/SNOMEDCT-A" + "/actions/restore-released-status?conceptId=123"));
		testStatusCode(HttpStatus.FORBIDDEN, userWithoutRoleHeaders, requestA);
		testStatusCode(HttpStatus.FORBIDDEN, authorHeaders, requestA);
		testStatusCode(HttpStatus.BAD_REQUEST, extensionAdminHeaders, requestA);
		testStatusCode(HttpStatus.BAD_REQUEST, globalAdminHeaders, requestA);
	}
}