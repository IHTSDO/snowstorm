package org.snomed.snowstorm.rest;

import org.junit.jupiter.api.Test;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.data.services.ConceptService;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.snomed.snowstorm.rest.pojo.ConceptBulkLoadRequest;
import org.snomed.snowstorm.rest.pojo.ConceptSearchRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.RequestEntity;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConceptControllerSecurityTest extends AbstractControllerSecurityTest {

	@Autowired
	private ConceptService conceptService;

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
	void getInactivationImpact() throws URISyntaxException {
		RequestEntity<Object> request = new RequestEntity<>(HttpMethod.GET,
				new URI(url + "/browser/MAIN/concepts/" + Concepts.SNOMEDCT_ROOT + "/inactivation-impact"));
		testStatusCode(HttpStatus.NOT_FOUND, userWithoutRoleHeaders, request);
		testStatusCode(HttpStatus.NOT_FOUND, authorHeaders, request);
		testStatusCode(HttpStatus.NOT_FOUND, extensionAuthorHeaders, request);
		testStatusCode(HttpStatus.NOT_FOUND, multiExtensionAuthorHeaders, request);
		testStatusCode(HttpStatus.NOT_FOUND, extensionAdminHeaders, request);
		testStatusCode(HttpStatus.NOT_FOUND, globalAdminHeaders, request);
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
		testStatusCode(HttpStatus.BAD_REQUEST, globalAdminHeaders, request);

		// Create concept first
		testStatusCode(HttpStatus.OK, extensionAuthorHeaders, new RequestEntity<>(new Concept(Concepts.CLINICAL_FINDING).addFSN("Test"), HttpMethod.POST, new URI(
				url + "/browser/" + "MAIN/SNOMEDCT-A" + "/concepts")));

		RequestEntity<Object> requestA = new RequestEntity<>(HttpMethod.DELETE, new URI(
				url + "/" + "MAIN/SNOMEDCT-A" + "/concepts/" + Concepts.CLINICAL_FINDING));
		testStatusCode(HttpStatus.FORBIDDEN, userWithoutRoleHeaders, requestA);
		testStatusCode(HttpStatus.FORBIDDEN, authorHeaders, requestA);
		testStatusCode(HttpStatus.OK, extensionAuthorHeaders, requestA);
		testStatusCode(HttpStatus.BAD_REQUEST, multiExtensionAuthorHeaders, requestA);
		testStatusCode(HttpStatus.BAD_REQUEST, extensionAdminHeaders, requestA);
		testStatusCode(HttpStatus.BAD_REQUEST, globalAdminHeaders, requestA);
	}

	@Test
	void forceDelete_ShouldReturnExpected_WhenAuthorDeleting() throws ServiceException, URISyntaxException {
		String intMain = "MAIN";
		Concept concept;
		CodeSystem codeSystem;

		// 1. Create International Concept
		concept = new Concept()
				.addDescription(new Description("Medicine (medicine)").setTypeId(Concepts.FSN))
				.addDescription(new Description("Medicine").setTypeId(Concepts.SYNONYM))
				.addAxiom(new Relationship(Concepts.ISA, Concepts.SNOMEDCT_ROOT));
		concept = conceptService.create(concept, intMain);
		String conceptId = concept.getConceptId();

		// 2. Version International
		codeSystem = codeSystemService.find("SNOMEDCT");
		codeSystemService.createVersion(codeSystem, 20260101, "20260101");

		// 3. Force delete published Concept
		RequestEntity<Object> deleteRequest = new RequestEntity<>(HttpMethod.DELETE, new URI(url + "/" + intMain + "/concepts/" + conceptId + "?force=true"));
		testStatusCode(HttpStatus.FORBIDDEN, authorHeaders, deleteRequest);
		assertNotNull(conceptService.find(conceptId, intMain));
	}

	@Test
	void forceDelete_ShouldReturnExpected_WhenAdminDeleting() throws ServiceException, URISyntaxException {
		String intMain = "MAIN";
		Concept concept;
		CodeSystem codeSystem;

		// 1. Create International Concept
		concept = new Concept()
				.addDescription(new Description("Medicine (medicine)").setTypeId(Concepts.FSN))
				.addDescription(new Description("Medicine").setTypeId(Concepts.SYNONYM))
				.addAxiom(new Relationship(Concepts.ISA, Concepts.SNOMEDCT_ROOT));
		concept = conceptService.create(concept, intMain);
		String conceptId = concept.getConceptId();

		// 2. Version International
		codeSystem = codeSystemService.find("SNOMEDCT");
		codeSystemService.createVersion(codeSystem, 20260101, "20260101");

		// 3. Force delete published Concept
		RequestEntity<Object> deleteRequest = new RequestEntity<>(HttpMethod.DELETE, new URI(url + "/" + intMain + "/concepts/" + conceptId + "?force=true"));
		testStatusCode(HttpStatus.OK, globalAdminHeaders, deleteRequest);
		assertNull(conceptService.find(conceptId, intMain));
	}

	@Test
	void createConceptBulkChange() throws URISyntaxException {
		List<Concept> concepts = Collections.singletonList(new Concept().addFSN("Test"));
		String completedStatus = "COMPLETED";

		RequestEntity<Object> request = new RequestEntity<>(concepts, HttpMethod.POST, new URI(
				url + "/browser/" + "MAIN" + "/concepts/bulk"));
		testStatusCode(HttpStatus.FORBIDDEN, userWithoutRoleHeaders, request);
		ControllerTestHelper.waitForStatus(testStatusCode(HttpStatus.CREATED, authorHeaders, request), completedStatus, null, authorHeaders, restTemplate);
		testStatusCode(HttpStatus.FORBIDDEN, extensionAuthorHeaders, request);
		ControllerTestHelper.waitForStatus(testStatusCode(HttpStatus.CREATED, multiExtensionAuthorHeaders, request), completedStatus, null, multiExtensionAuthorHeaders, restTemplate);
		testStatusCode(HttpStatus.FORBIDDEN, extensionAdminHeaders, request);
		testStatusCode(HttpStatus.FORBIDDEN, globalAdminHeaders, request);

		RequestEntity<Object> requestA = new RequestEntity<>(concepts, HttpMethod.POST, new URI(
				url + "/browser/" + "MAIN/SNOMEDCT-A" + "/concepts/bulk"));
		testStatusCode(HttpStatus.FORBIDDEN, userWithoutRoleHeaders, requestA);
		testStatusCode(HttpStatus.FORBIDDEN, authorHeaders, requestA);
		ControllerTestHelper.waitForStatus(testStatusCode(HttpStatus.CREATED, extensionAuthorHeaders, requestA), completedStatus, null, extensionAuthorHeaders, restTemplate);
		ControllerTestHelper.waitForStatus(testStatusCode(HttpStatus.CREATED, multiExtensionAuthorHeaders, requestA), completedStatus, null, multiExtensionAuthorHeaders, restTemplate);
		testStatusCode(HttpStatus.FORBIDDEN, extensionAdminHeaders, requestA);
		testStatusCode(HttpStatus.FORBIDDEN, globalAdminHeaders, requestA);
	}

}
