package org.snomed.snowstorm.rest;

import org.junit.jupiter.api.Test;
import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.core.data.domain.Concepts;
import org.snomed.snowstorm.rest.pojo.*;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

import static java.lang.String.format;
import static org.junit.Assert.fail;

class ConceptControllerSecurityTest extends AbstractControllerSecurityTest {

	@Test
	void findConcepts() throws URISyntaxException {
		RequestEntity<Object> request = new RequestEntity<>(HttpMethod.GET, new URI(url + "/MAIN/concepts"));
		testStatusCode(HttpStatus.OK, userWithoutRoleHeaders, request);
		testStatusCode(HttpStatus.OK, authorHeaders, request);
		testStatusCode(HttpStatus.OK, extensionAuthorHeaders, request);
		testStatusCode(HttpStatus.OK, multiExtensionAuthorHeaders, request);
		testStatusCode(HttpStatus.OK, extensionAdminHeaders, request);
		testStatusCode(HttpStatus.OK, globalAdminHeaders, request);
	}

	@Test
	void searchViaPost() throws URISyntaxException {
		RequestEntity<Object> request = new RequestEntity<>(new ConceptSearchRequest(), HttpMethod.POST, new URI(url + "/MAIN/concepts/search"));
		testStatusCode(HttpStatus.OK, userWithoutRoleHeaders, request);
		testStatusCode(HttpStatus.OK, authorHeaders, request);
		testStatusCode(HttpStatus.OK, extensionAuthorHeaders, request);
		testStatusCode(HttpStatus.OK, multiExtensionAuthorHeaders, request);
		testStatusCode(HttpStatus.OK, extensionAdminHeaders, request);
		testStatusCode(HttpStatus.OK, globalAdminHeaders, request);
	}

	@Test
	void getBrowserConceptsViaPost() throws URISyntaxException {
		RequestEntity<Object> request = new RequestEntity<>(new ConceptBulkLoadRequest(), HttpMethod.POST, new URI(url + "/browser/MAIN/concepts/bulk-load"));
		testStatusCode(HttpStatus.OK, userWithoutRoleHeaders, request);
		testStatusCode(HttpStatus.OK, authorHeaders, request);
		testStatusCode(HttpStatus.OK, extensionAuthorHeaders, request);
		testStatusCode(HttpStatus.OK, multiExtensionAuthorHeaders, request);
		testStatusCode(HttpStatus.OK, extensionAdminHeaders, request);
		testStatusCode(HttpStatus.OK, globalAdminHeaders, request);
	}

	@Test
	void createConcept() throws URISyntaxException {
		Concept concept = new Concept().addFSN("Test");

		RequestEntity<Object> request = new RequestEntity<>(concept, HttpMethod.POST, new URI(
				url + "/browser/" + "MAIN" + "/concepts"));
		testStatusCode(HttpStatus.FORBIDDEN, userWithoutRoleHeaders, request);
		testStatusCode(HttpStatus.OK, authorHeaders, request);
		testStatusCode(HttpStatus.FORBIDDEN, extensionAuthorHeaders, request);
		testStatusCode(HttpStatus.OK, multiExtensionAuthorHeaders, request);
		testStatusCode(HttpStatus.FORBIDDEN, extensionAdminHeaders, request);
		testStatusCode(HttpStatus.FORBIDDEN, globalAdminHeaders, request);

		RequestEntity<Object> requestA = new RequestEntity<>(concept, HttpMethod.POST, new URI(
				url + "/browser/" + "MAIN/SNOMEDCT-A" + "/concepts"));
		testStatusCode(HttpStatus.FORBIDDEN, userWithoutRoleHeaders, requestA);
		testStatusCode(HttpStatus.FORBIDDEN, authorHeaders, requestA);
		testStatusCode(HttpStatus.OK, extensionAuthorHeaders, requestA);
		testStatusCode(HttpStatus.OK, multiExtensionAuthorHeaders, requestA);
		testStatusCode(HttpStatus.FORBIDDEN, extensionAdminHeaders, requestA);
		testStatusCode(HttpStatus.FORBIDDEN, globalAdminHeaders, requestA);
	}

	@Test
	void updateConcept() throws URISyntaxException {
		Concept concept = new Concept(Concepts.CLINICAL_FINDING).addFSN("Test");
		// Create concept first
		testStatusCode(HttpStatus.OK, authorHeaders, new RequestEntity<>(concept, HttpMethod.POST, new URI(
				url + "/browser/" + "MAIN" + "/concepts")));


		RequestEntity<Object> request = new RequestEntity<>(concept, HttpMethod.PUT, new URI(
				url + "/browser/" + "MAIN" + "/concepts/" + Concepts.CLINICAL_FINDING));
		testStatusCode(HttpStatus.FORBIDDEN, userWithoutRoleHeaders, request);
		testStatusCode(HttpStatus.OK, authorHeaders, request);
		testStatusCode(HttpStatus.FORBIDDEN, extensionAuthorHeaders, request);
		testStatusCode(HttpStatus.OK, multiExtensionAuthorHeaders, request);
		testStatusCode(HttpStatus.FORBIDDEN, extensionAdminHeaders, request);
		testStatusCode(HttpStatus.FORBIDDEN, globalAdminHeaders, request);

		// Create concept first
		testStatusCode(HttpStatus.OK, extensionAuthorHeaders, new RequestEntity<>(concept, HttpMethod.POST, new URI(
				url + "/browser/" + "MAIN/SNOMEDCT-A" + "/concepts")));

		RequestEntity<Object> requestA = new RequestEntity<>(concept, HttpMethod.PUT, new URI(
				url + "/browser/" + "MAIN/SNOMEDCT-A" + "/concepts/" + Concepts.CLINICAL_FINDING));
		testStatusCode(HttpStatus.FORBIDDEN, userWithoutRoleHeaders, requestA);
		testStatusCode(HttpStatus.FORBIDDEN, authorHeaders, requestA);
		testStatusCode(HttpStatus.OK, extensionAuthorHeaders, requestA);
		testStatusCode(HttpStatus.OK, multiExtensionAuthorHeaders, requestA);
		testStatusCode(HttpStatus.FORBIDDEN, extensionAdminHeaders, requestA);
		testStatusCode(HttpStatus.FORBIDDEN, globalAdminHeaders, requestA);
	}

	@Test
	void deleteConcept() throws URISyntaxException {
		// Create concept first
		testStatusCode(HttpStatus.OK, authorHeaders, new RequestEntity<>(new Concept(Concepts.CLINICAL_FINDING).addFSN("Test"), HttpMethod.POST, new URI(
				url + "/browser/" + "MAIN" + "/concepts")));

		RequestEntity<Object> request = new RequestEntity<>(HttpMethod.DELETE, new URI(
				url + "/" + "MAIN" + "/concepts/" + Concepts.CLINICAL_FINDING));
		testStatusCode(HttpStatus.FORBIDDEN, userWithoutRoleHeaders, request);
		testStatusCode(HttpStatus.OK, authorHeaders, request);
		testStatusCode(HttpStatus.FORBIDDEN, extensionAuthorHeaders, request);
		testStatusCode(HttpStatus.BAD_REQUEST, multiExtensionAuthorHeaders, request);// concept already deleted
		testStatusCode(HttpStatus.FORBIDDEN, extensionAdminHeaders, request);
		testStatusCode(HttpStatus.FORBIDDEN, globalAdminHeaders, request);

		// Create concept first
		testStatusCode(HttpStatus.OK, extensionAuthorHeaders, new RequestEntity<>(new Concept(Concepts.CLINICAL_FINDING).addFSN("Test"), HttpMethod.POST, new URI(
				url + "/browser/" + "MAIN/SNOMEDCT-A" + "/concepts")));

		RequestEntity<Object> requestA = new RequestEntity<>(HttpMethod.DELETE, new URI(
				url + "/" + "MAIN/SNOMEDCT-A" + "/concepts/" + Concepts.CLINICAL_FINDING));
		testStatusCode(HttpStatus.FORBIDDEN, userWithoutRoleHeaders, requestA);
		testStatusCode(HttpStatus.FORBIDDEN, authorHeaders, requestA);
		testStatusCode(HttpStatus.OK, extensionAuthorHeaders, requestA);
		testStatusCode(HttpStatus.BAD_REQUEST, multiExtensionAuthorHeaders, requestA);// concept already deleted
		testStatusCode(HttpStatus.FORBIDDEN, extensionAdminHeaders, requestA);
		testStatusCode(HttpStatus.FORBIDDEN, globalAdminHeaders, requestA);
	}

	@Test
	void createConceptBulkChange() throws URISyntaxException {
		List<Concept> concepts = Collections.singletonList(new Concept().addFSN("Test"));
		String completedStatus = "COMPLETED";

		RequestEntity<Object> request = new RequestEntity<>(concepts, HttpMethod.POST, new URI(
				url + "/browser/" + "MAIN" + "/concepts/bulk"));
		testStatusCode(HttpStatus.FORBIDDEN, userWithoutRoleHeaders, request);
		waitForStatus(testStatusCode(HttpStatus.CREATED, authorHeaders, request), completedStatus, authorHeaders);
		testStatusCode(HttpStatus.FORBIDDEN, extensionAuthorHeaders, request);
		waitForStatus(testStatusCode(HttpStatus.CREATED, multiExtensionAuthorHeaders, request), completedStatus, multiExtensionAuthorHeaders);
		testStatusCode(HttpStatus.FORBIDDEN, extensionAdminHeaders, request);
		testStatusCode(HttpStatus.FORBIDDEN, globalAdminHeaders, request);

		RequestEntity<Object> requestA = new RequestEntity<>(concepts, HttpMethod.POST, new URI(
				url + "/browser/" + "MAIN/SNOMEDCT-A" + "/concepts/bulk"));
		testStatusCode(HttpStatus.FORBIDDEN, userWithoutRoleHeaders, requestA);
		testStatusCode(HttpStatus.FORBIDDEN, authorHeaders, requestA);
		waitForStatus(testStatusCode(HttpStatus.CREATED, extensionAuthorHeaders, requestA), completedStatus, extensionAuthorHeaders);
		waitForStatus(testStatusCode(HttpStatus.CREATED, multiExtensionAuthorHeaders, requestA), completedStatus, multiExtensionAuthorHeaders);
		testStatusCode(HttpStatus.FORBIDDEN, extensionAdminHeaders, requestA);
		testStatusCode(HttpStatus.FORBIDDEN, globalAdminHeaders, requestA);
	}

}
