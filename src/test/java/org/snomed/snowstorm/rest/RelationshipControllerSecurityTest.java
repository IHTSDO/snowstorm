package org.snomed.snowstorm.rest;

import org.junit.jupiter.api.Test;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.data.services.ConceptService;
import org.snomed.snowstorm.core.data.services.RelationshipService;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.RequestEntity;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.snomed.snowstorm.core.data.domain.Concepts.*;

class RelationshipControllerSecurityTest extends AbstractControllerSecurityTest {

	@Autowired
	private ConceptService conceptService;

	@Autowired
	private RelationshipService relationshipService;

	@Test
	void forceDelete_ShouldReturnExpected_WhenAuthorDeleting() throws ServiceException, URISyntaxException {
		String intMain = "MAIN";
		Concept concept;
		CodeSystem codeSystem;
		Relationship relationship;

		// 1. Create International Concept
		concept = new Concept()
				.addDescription(new Description("Medicine (medicine)").setTypeId(FSN))
				.addDescription(new Description("Medicine").setTypeId(SYNONYM))
				.addRelationship(new Relationship(ISA, SNOMEDCT_ROOT));
		concept = conceptService.create(concept, intMain);
		String conceptId = concept.getConceptId();

		// 2. Version International
		codeSystem = codeSystemService.find("SNOMEDCT");
		codeSystemService.createVersion(codeSystem, 20260101, "20260101");
		concept = conceptService.find(conceptId, intMain);
		relationship = concept.getRelationships().iterator().next();
		relationship = relationshipService.findRelationship(intMain, relationship.getRelationshipId());
		assertTrue(relationship.isReleased());

		// 3. Force delete published Relationship
		RequestEntity<Object> deleteRequest = new RequestEntity<>(HttpMethod.DELETE, new URI(url + "/" + intMain + "/relationships/" + relationship.getRelationshipId() + "?force=true"));
		testStatusCode(HttpStatus.FORBIDDEN, authorHeaders, deleteRequest);
		assertNotNull(relationshipService.findRelationship(intMain, relationship.getRelationshipId()));
	}

	@Test
	void forceDelete_ShouldReturnExpected_WhenAdminDeleting() throws ServiceException, URISyntaxException {
		String intMain = "MAIN";
		Concept concept;
		CodeSystem codeSystem;
		Relationship relationship;

		// 1. Create International Concept
		concept = new Concept()
				.addDescription(new Description("Medicine (medicine)").setTypeId(FSN))
				.addDescription(new Description("Medicine").setTypeId(SYNONYM))
				.addRelationship(new Relationship(ISA, SNOMEDCT_ROOT));
		concept = conceptService.create(concept, intMain);
		String conceptId = concept.getConceptId();

		// 2. Version International
		codeSystem = codeSystemService.find("SNOMEDCT");
		codeSystemService.createVersion(codeSystem, 20260101, "20260101");
		concept = conceptService.find(conceptId, intMain);
		relationship = concept.getRelationships().iterator().next();
		relationship = relationshipService.findRelationship(intMain, relationship.getRelationshipId());
		assertTrue(relationship.isReleased());

		// 3. Force delete published Relationship
		RequestEntity<Object> deleteRequest = new RequestEntity<>(HttpMethod.DELETE, new URI(url + "/" + intMain + "/relationships/" + relationship.getRelationshipId() + "?force=true"));
		testStatusCode(HttpStatus.NO_CONTENT, globalAdminHeaders, deleteRequest);
		assertNull(relationshipService.findRelationship(intMain, relationship.getRelationshipId()));
	}

	@Test
	void delete_ShouldReturnExpected_WhenAuthorDeleting() throws ServiceException, URISyntaxException {
		String intMain = "MAIN";
		Concept concept;
		Relationship relationship;

		// 1. Create International Concept
		concept = new Concept()
				.addDescription(new Description("Medicine (medicine)").setTypeId(FSN))
				.addDescription(new Description("Medicine").setTypeId(SYNONYM))
				.addRelationship(new Relationship(ISA, SNOMEDCT_ROOT));
		concept = conceptService.create(concept, intMain);
		relationship = concept.getRelationships().iterator().next();

		// 2. Delete Relationship
		RequestEntity<Object> request = new RequestEntity<>(HttpMethod.DELETE, new URI(url + "/" + intMain + "/relationships/" + relationship.getRelationshipId()));
		testStatusCode(HttpStatus.FORBIDDEN, userWithoutRoleHeaders, request);
		testStatusCode(HttpStatus.NO_CONTENT, authorHeaders, request);
		assertNull(relationshipService.findRelationship(intMain, relationship.getRelationshipId()));
	}
}
