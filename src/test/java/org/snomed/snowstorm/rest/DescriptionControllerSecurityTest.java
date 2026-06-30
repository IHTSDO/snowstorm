package org.snomed.snowstorm.rest;

import org.junit.jupiter.api.Test;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.data.services.ConceptService;
import org.snomed.snowstorm.core.data.services.DescriptionService;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.RequestEntity;

import java.net.URI;
import java.net.URISyntaxException;

import static org.junit.jupiter.api.Assertions.*;
import static org.snomed.snowstorm.core.data.domain.Concepts.*;

class DescriptionControllerSecurityTest extends AbstractControllerSecurityTest {

	@Autowired
	private ConceptService conceptService;

	@Autowired
	private DescriptionService descriptionService;

	@Test
	void forceDelete_ShouldReturnExpected_WhenAuthorDeleting() throws ServiceException, URISyntaxException {
		String intMain = "MAIN";
		Concept concept;
		CodeSystem codeSystem;
		Description description;

		// 1. Create International Concept
		concept = new Concept()
				.addDescription(new Description("Medicine (medicine)").setTypeId(FSN))
				.addDescription(new Description("Medicine").setTypeId(SYNONYM))
				.addAxiom(new Relationship(ISA, SNOMEDCT_ROOT));
		concept = conceptService.create(concept, intMain);
		String conceptId = concept.getConceptId();

		// 2. Version International
		codeSystem = codeSystemService.find("SNOMEDCT");
		codeSystemService.createVersion(codeSystem, 20260101, "20260101");
		concept = conceptService.find(conceptId, intMain);
		description = concept.getDescriptions().stream().filter(d -> FSN.equals(d.getTypeId())).findFirst().orElseThrow();
		description = descriptionService.findDescription(intMain, description.getDescriptionId());
		assertTrue(description.isReleased());

		// 3. Force delete published Description
		RequestEntity<Object> deleteRequest = new RequestEntity<>(HttpMethod.DELETE, new URI(url + "/" + intMain + "/descriptions/" + description.getDescriptionId() + "?force=true"));
		testStatusCode(HttpStatus.FORBIDDEN, authorHeaders, deleteRequest);
		assertNotNull(descriptionService.findDescription(intMain, description.getDescriptionId()));
	}

	@Test
	void forceDelete_ShouldReturnExpected_WhenAdminDeleting() throws ServiceException, URISyntaxException {
		String intMain = "MAIN";
		Concept concept;
		CodeSystem codeSystem;
		Description description;

		// 1. Create International Concept
		concept = new Concept()
				.addDescription(new Description("Medicine (medicine)").setTypeId(FSN))
				.addDescription(new Description("Medicine").setTypeId(SYNONYM))
				.addAxiom(new Relationship(ISA, SNOMEDCT_ROOT));
		concept = conceptService.create(concept, intMain);
		String conceptId = concept.getConceptId();

		// 2. Version International
		codeSystem = codeSystemService.find("SNOMEDCT");
		codeSystemService.createVersion(codeSystem, 20260101, "20260101");
		concept = conceptService.find(conceptId, intMain);
		description = concept.getDescriptions().stream().filter(d -> FSN.equals(d.getTypeId())).findFirst().orElseThrow();
		description = descriptionService.findDescription(intMain, description.getDescriptionId());
		assertTrue(description.isReleased());

		// 3. Force delete published Description
		RequestEntity<Object> deleteRequest = new RequestEntity<>(HttpMethod.DELETE, new URI(url + "/" + intMain + "/descriptions/" + description.getDescriptionId() + "?force=true"));
		testStatusCode(HttpStatus.OK, globalAdminHeaders, deleteRequest);
		assertNull(descriptionService.findDescription(intMain, description.getDescriptionId()));
	}

	@Test
	void delete_ShouldReturnExpected_WhenAuthorDeleting() throws ServiceException, URISyntaxException {
		String intMain = "MAIN";
		Concept concept;
		Description description;

		// 1. Create International Concept
		concept = new Concept()
				.addDescription(new Description("Medicine (medicine)").setTypeId(FSN))
				.addDescription(new Description("Medicine").setTypeId(SYNONYM))
				.addAxiom(new Relationship(ISA, SNOMEDCT_ROOT));
		concept = conceptService.create(concept, intMain);
		description = concept.getDescriptions().stream().filter(d -> SYNONYM.equals(d.getTypeId())).findFirst().orElseThrow();

		// 2. Delete Description
		RequestEntity<Object> request = new RequestEntity<>(HttpMethod.DELETE, new URI(url + "/" + intMain + "/descriptions/" + description.getDescriptionId()));
		testStatusCode(HttpStatus.FORBIDDEN, userWithoutRoleHeaders, request);
		testStatusCode(HttpStatus.OK, authorHeaders, request);
		assertNull(descriptionService.findDescription(intMain, description.getDescriptionId()));
	}
}
