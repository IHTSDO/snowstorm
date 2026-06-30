package org.snomed.snowstorm.rest;

import org.junit.jupiter.api.Test;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.data.services.ConceptService;
import org.snomed.snowstorm.core.data.services.ReferenceSetMemberService;
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

class ReferenceSetMemberControllerSecurityTest extends AbstractControllerSecurityTest {
	@Autowired
	private ConceptService conceptService;

	@Autowired
	private ReferenceSetMemberService memberService;

	@Test
	void forceDelete_ShouldReturnExpected_WhenAuthorDeleting() throws ServiceException, URISyntaxException {
		String intMain = "MAIN";
		Concept concept;
		CodeSystem codeSystem;
		ReferenceSetMember referenceSetMember;

		// 1. Create International Concept
		concept = new Concept()
				.addDescription(new Description("Medicine (medicine)").setTypeId(FSN))
				.addDescription(new Description("Medicine").setTypeId(SYNONYM))
				.addAxiom(new Relationship(ISA, SNOMEDCT_ROOT));
		concept = conceptService.create(concept, intMain);
		String medicineId = concept.getConceptId();

		// 2. Version International
		codeSystem = codeSystemService.find("SNOMEDCT");
		codeSystemService.createVersion(codeSystem, 20260101, "20260101");
		concept = conceptService.find(medicineId, intMain);
		referenceSetMember = concept.getClassAxioms().iterator().next().getReferenceSetMember();
		referenceSetMember = memberService.findMember(intMain, referenceSetMember.getMemberId());
		assertTrue(referenceSetMember.isReleased());

		// 3. Force delete published Axiom
		RequestEntity<Object> deleteRequest = new RequestEntity<>(HttpMethod.DELETE, new URI(url + "/MAIN/members/" + referenceSetMember.getMemberId() + "?force=true"));
		testStatusCode(HttpStatus.FORBIDDEN, authorHeaders, deleteRequest);
		assertNotNull(memberService.findMember(intMain, referenceSetMember.getMemberId()));
	}

	@Test
	void forceDelete_ShouldReturnExpected_WhenAdminDeleting() throws ServiceException, URISyntaxException {
		String intMain = "MAIN";
		Concept concept;
		CodeSystem codeSystem;
		ReferenceSetMember referenceSetMember;

		// 1. Create International Concept
		concept = new Concept()
				.addDescription(new Description("Medicine (medicine)").setTypeId(FSN))
				.addDescription(new Description("Medicine").setTypeId(SYNONYM))
				.addAxiom(new Relationship(ISA, SNOMEDCT_ROOT));
		concept = conceptService.create(concept, intMain);
		String medicineId = concept.getConceptId();

		// 2. Version International
		codeSystem = codeSystemService.find("SNOMEDCT");
		codeSystemService.createVersion(codeSystem, 20260101, "20260101");
		concept = conceptService.find(medicineId, intMain);
		referenceSetMember = concept.getClassAxioms().iterator().next().getReferenceSetMember();
		referenceSetMember = memberService.findMember(intMain, referenceSetMember.getMemberId());
		assertTrue(referenceSetMember.isReleased());

		// 3. Force delete published Axiom
		RequestEntity<Object> deleteRequest = new RequestEntity<>(HttpMethod.DELETE, new URI(url + "/MAIN/members/" + referenceSetMember.getMemberId() + "?force=true"));
		testStatusCode(HttpStatus.NO_CONTENT, globalAdminHeaders, deleteRequest);
		assertNull(memberService.findMember(intMain, referenceSetMember.getMemberId()));
	}

	@Test
	void delete_ShouldReturnExpected_WhenAuthorDeleting() throws ServiceException, URISyntaxException {
		String intMain = "MAIN";
		Concept concept;
		ReferenceSetMember referenceSetMember;

		// 1. Create International Concept
		concept = new Concept()
				.addDescription(new Description("Medicine (medicine)").setTypeId(FSN))
				.addDescription(new Description("Medicine").setTypeId(SYNONYM))
				.addAxiom(new Relationship(ISA, SNOMEDCT_ROOT));
		concept = conceptService.create(concept, intMain);
		referenceSetMember = concept.getClassAxioms().iterator().next().getReferenceSetMember();

		// 2. Delete Member
		RequestEntity<Object> request = new RequestEntity<>(HttpMethod.DELETE, new URI(url + "/" + intMain + "/members/" + referenceSetMember.getMemberId()));
		testStatusCode(HttpStatus.FORBIDDEN, userWithoutRoleHeaders, request);
		testStatusCode(HttpStatus.NO_CONTENT, authorHeaders, request);
		assertNull(memberService.findMember(intMain, referenceSetMember.getMemberId()));
	}
}
