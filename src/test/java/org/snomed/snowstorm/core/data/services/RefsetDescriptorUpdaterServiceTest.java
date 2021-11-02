package org.snomed.snowstorm.core.data.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.data.services.pojo.MemberSearchRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;

@ExtendWith(SpringExtension.class)
class RefsetDescriptorUpdaterServiceTest extends AbstractTest {
	private static final PageRequest PAGE_REQUEST = PageRequest.of(0, 10);

	@Autowired
	private ReferenceSetMemberService memberService;

	@Autowired
	private ConceptService conceptService;

	@BeforeEach
	void setup() throws ServiceException, InterruptedException {
		conceptService.deleteAll();
		conceptService.create(new Concept(Concepts.SNOMEDCT_ROOT), MAIN);
		conceptService.create(new Concept(Concepts.ISA)
				.addAxiom(new Relationship(Concepts.ISA, Concepts.SNOMEDCT_ROOT)), MAIN);
		conceptService.create(new Concept(Concepts.REFSET)
				.addAxiom(new Relationship(Concepts.ISA, Concepts.FOUNDATION_METADATA)).setModuleId(Concepts.MODEL_MODULE), MAIN);
		conceptService.create(new Concept(Concepts.CLINICAL_FINDING)
				.addAxiom(new Relationship(Concepts.ISA, Concepts.SNOMEDCT_ROOT)), MAIN);
		conceptService.create(new Concept(Concepts.REFSET_HISTORICAL_ASSOCIATION)
				.addAxiom(new Relationship(Concepts.ISA, Concepts.SNOMEDCT_ROOT)), MAIN);
		conceptService.create(new Concept(Concepts.REFSET_POSSIBLY_EQUIVALENT_TO_ASSOCIATION)
				.addAxiom(new Relationship(Concepts.ISA, Concepts.REFSET_HISTORICAL_ASSOCIATION)), MAIN);
		conceptService.create(new Concept(Concepts.REFSET_SIMPLE)
				.addAxiom(new Relationship(Concepts.ISA, Concepts.REFSET)).setModuleId(Concepts.MODEL_MODULE), MAIN);
		conceptService.create(new Concept(Concepts.REFSET_DESCRIPTOR_REFSET)
				.addAxiom(new Relationship(Concepts.ISA, Concepts.REFSET)).setModuleId(Concepts.MODEL_MODULE), MAIN);
	}

	@Test
	void testRefSetDescriptorEntryInserted() throws ServiceException {
		// given
		givenRefSetAncestorsExist();
		MemberSearchRequest memberSearchRequest = buildMemberSearchRequest(true, Concepts.REFSET_DESCRIPTOR_REFSET);
		List<ReferenceSetMember> membersBefore = memberService.findMembers("MAIN", memberSearchRequest, PageRequest.of(0, 10)).getContent();
		assertEquals(1, membersBefore.size()); // simple

		// when
		Concept newRefSet = createNewRefSet();

		// then
		List<ReferenceSetMember> membersAfter = memberService.findMembers("MAIN", memberSearchRequest, PageRequest.of(0, 10)).getContent();
		ReferenceSetMember referenceSetMember = membersAfter.get(1);

		assertEquals(2, membersAfter.size()); // simple & newRefSet
		assertEquals(newRefSet.getId(), referenceSetMember.getReferencedComponentId());
		assertEquals(Concepts.REFSET_DESCRIPTOR_REFSET, referenceSetMember.getRefsetId());
		assertEquals(Concepts.REFERENCED_COMPONENT, referenceSetMember.getAdditionalField("attributeDescription"));
		assertEquals(Concepts.CONCEPT_TYPE_COMPONENT, referenceSetMember.getAdditionalField("attributeType"));
		assertEquals("0", referenceSetMember.getAdditionalField("attributeOrder"));
	}

	@Test
	void testUpdatingRefSetConcept() throws ServiceException {
		// Create & assert initial seed data
		givenRefSetAncestorsExist();
		assertEquals(1, memberService.findMembers("MAIN", buildMemberSearchRequest(true, Concepts.REFSET_DESCRIPTOR_REFSET), PAGE_REQUEST).getContent().size());

		// Create & assert two test reference sets
		Concept newRefSet1 = createNewRefSet();
		Concept newRefSet2 = createNewRefSet();
		assertEquals(3, memberService.findMembers("MAIN", buildMemberSearchRequest(true, Concepts.REFSET_DESCRIPTOR_REFSET), PAGE_REQUEST).getContent().size());

		// Change modelling for semantic index change. newRefSet2 now |is a| newRefSet1; previously it was a simple reference set.
		changeIsATarget(newRefSet1, newRefSet2);

		// when
		conceptService.update(newRefSet2, "MAIN");

		// then
		List<ReferenceSetMember> members = memberService.findMembers("MAIN", buildMemberSearchRequest(true, Concepts.REFSET_DESCRIPTOR_REFSET), PAGE_REQUEST).getContent();
		ReferenceSetMember referenceSetMember = members.get(2); // newRefSet2 is 3rd document.

		assertEquals(3, members.size());
		assertEquals(newRefSet2.getId(), referenceSetMember.getReferencedComponentId());
		assertEquals(Concepts.REFSET_DESCRIPTOR_REFSET, referenceSetMember.getRefsetId());
		assertEquals(Concepts.REFERENCED_COMPONENT, referenceSetMember.getAdditionalField("attributeDescription"));
		assertEquals(Concepts.CONCEPT_TYPE_COMPONENT, referenceSetMember.getAdditionalField("attributeType"));
		assertEquals("0", referenceSetMember.getAdditionalField("attributeOrder"));
	}

	private void givenRefSetAncestorsExist() throws ServiceException {
		// Create top-level Concept
		Concept foodStructure = conceptService.create(
				new Concept()
						.addDescription(new Description("Food structure (food structure)"))
						.addAxiom(new Relationship(Concepts.ISA, Concepts.SNOMEDCT_ROOT)),
				"MAIN"
		);

		// Add top-level Concept to simple refset
		ReferenceSetMember foodStructureMember = new ReferenceSetMember(Concepts.MODEL_MODULE, Concepts.REFSET_SIMPLE, foodStructure.getId());
		memberService.createMember("MAIN", foodStructureMember);

		// Add simple refset to refset descriptor
		ReferenceSetMember refsetInDescriptor = new ReferenceSetMember(Concepts.MODEL_MODULE, Concepts.REFSET_DESCRIPTOR_REFSET, Concepts.REFSET_SIMPLE);
		refsetInDescriptor.setAdditionalFields(Map.of(
				"attributeDescription", Concepts.REFERENCED_COMPONENT,
				"attributeType", Concepts.CONCEPT_TYPE_COMPONENT,
				"attributeOrder", "0")
		);
		memberService.createMember("MAIN", refsetInDescriptor);
	}

	private Concept createNewRefSet() throws ServiceException {
		Relationship relationship = new Relationship();
		relationship.setTypeId(Concepts.ISA);
		relationship.setDestinationId(Concepts.REFSET_SIMPLE);
		Set<Relationship> relationships = new HashSet<>();
		relationships.add(relationship);

		Axiom axiom = new Axiom();
		axiom.setRelationships(relationships);
		axiom.setModuleId(Concepts.MODEL_MODULE);
		Set<Axiom> axioms = new HashSet<>();
		axioms.add(axiom);

		Concept concept = new Concept();
		concept.setClassAxioms(axioms);
		concept.setModuleId(Concepts.MODEL_MODULE);

		return conceptService.create(concept, "MAIN");
	}

	private MemberSearchRequest buildMemberSearchRequest(boolean active, String referenceSetId) {
		MemberSearchRequest memberSearchRequest = new MemberSearchRequest();
		memberSearchRequest.active(active);
		memberSearchRequest.referenceSet(referenceSetId);

		return memberSearchRequest;
	}

	private void changeIsATarget(Concept newRefSet1, Concept newRefSet2) {
		Relationship relationship = new Relationship();
		relationship.setTypeId(Concepts.ISA);
		relationship.setDestinationId(newRefSet1.getConceptId());
		Set<Relationship> relationships = new HashSet<>();
		relationships.add(relationship);

		Axiom axiom = new Axiom();
		axiom.setRelationships(relationships);
		axiom.setModuleId(Concepts.MODEL_MODULE);
		Set<Axiom> axioms = new HashSet<>();
		axioms.add(axiom);

		newRefSet2.setRelationships(relationships);
	}
}
