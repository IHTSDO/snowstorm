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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.*;
import static org.snomed.snowstorm.core.data.domain.Concepts.*;

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
		ReferenceSetMember referenceSetMember = getReferenceSetMember(members, newRefSet2.getConceptId());

		assertEquals(3, members.size());
		assertEquals(newRefSet2.getId(), referenceSetMember.getReferencedComponentId());
		assertEquals(Concepts.REFSET_DESCRIPTOR_REFSET, referenceSetMember.getRefsetId());
		assertEquals(Concepts.REFERENCED_COMPONENT, referenceSetMember.getAdditionalField("attributeDescription"));
		assertEquals(Concepts.CONCEPT_TYPE_COMPONENT, referenceSetMember.getAdditionalField("attributeType"));
		assertEquals("0", referenceSetMember.getAdditionalField("attributeOrder"));
	}

	/*
	 * A
	 * B C
	 * D E
	 * F
	 *
	 * E has a descriptor entry. F needs to find it and duplicate it.
	 * */
	@Test
	void testDescriptorsWhenMultipleParentsAndGrandParentsWithSequenceA() throws ServiceException {
		Concept concept;
		List<ReferenceSetMember> referenceSetMembers;

		// Create reference sets
		concept = new Concept()
				.addDescription(new Description("A reference set (reference set)"))
				.addDescription(new Description("A reference set"))
				.addAxiom(new Relationship(ISA, REFSET))
				.addRelationship(new Relationship(ISA, REFSET));
		concept = conceptService.create(concept, "MAIN");
		String aReferenceSetId = concept.getConceptId();

		concept = new Concept()
				.addDescription(new Description("B reference set (reference set)"))
				.addDescription(new Description("B reference set"))
				.addAxiom(new Relationship(ISA, aReferenceSetId))
				.addRelationship(new Relationship(ISA, aReferenceSetId));
		concept = conceptService.create(concept, "MAIN");
		String bReferenceSetId = concept.getConceptId();

		concept = new Concept()
				.addDescription(new Description("C reference set (reference set)"))
				.addDescription(new Description("C reference set"))
				.addAxiom(new Relationship(ISA, aReferenceSetId))
				.addRelationship(new Relationship(ISA, aReferenceSetId));
		concept = conceptService.create(concept, "MAIN");
		String cReferenceSetId = concept.getConceptId();

		concept = new Concept()
				.addDescription(new Description("D reference set (reference set)"))
				.addDescription(new Description("D reference set"))
				.addAxiom(new Relationship(ISA, bReferenceSetId), new Relationship(ISA, cReferenceSetId))
				.addRelationship(new Relationship(ISA, bReferenceSetId))
				.addRelationship(new Relationship(ISA, cReferenceSetId));
		concept = conceptService.create(concept, "MAIN");
		String dReferenceSetId = concept.getConceptId();

		concept = new Concept()
				.addDescription(new Description("E reference set (reference set)"))
				.addDescription(new Description("E reference set"))
				.addAxiom(new Relationship(ISA, bReferenceSetId), new Relationship(ISA, cReferenceSetId))
				.addRelationship(new Relationship(ISA, bReferenceSetId))
				.addRelationship(new Relationship(ISA, cReferenceSetId));
		concept = conceptService.create(concept, "MAIN");
		String eReferenceSetId = concept.getConceptId();

		// Create Descriptor manually
		memberService.createMember("MAIN", new ReferenceSetMember(MODEL_MODULE, REFSET_DESCRIPTOR_REFSET, eReferenceSetId).setAdditionalField("attributeType", "1").setAdditionalField("attributeOrder", "0").setAdditionalField("attributeDescription", "3"));
		referenceSetMembers = memberService.findMembers("MAIN", new MemberSearchRequest().referencedComponentId(eReferenceSetId).referenceSet(REFSET_DESCRIPTOR_REFSET), PAGE_REQUEST).getContent();
		assertFalse(referenceSetMembers.isEmpty());

		memberService.createMember("MAIN", new ReferenceSetMember(MODEL_MODULE, REFSET_DESCRIPTOR_REFSET, dReferenceSetId).setAdditionalField("attributeType", "1").setAdditionalField("attributeOrder", "0").setAdditionalField("attributeDescription", "3"));
		referenceSetMembers = memberService.findMembers("MAIN", new MemberSearchRequest().referencedComponentId(eReferenceSetId).referenceSet(REFSET_DESCRIPTOR_REFSET), PAGE_REQUEST).getContent();
		assertFalse(referenceSetMembers.isEmpty());

		// Create reference set
		concept = new Concept()
				.addDescription(new Description("F reference set (reference set)"))
				.addDescription(new Description("F reference set"))
				.addAxiom(new Relationship(ISA, dReferenceSetId), new Relationship(ISA, eReferenceSetId))
				.addRelationship(new Relationship(ISA, dReferenceSetId))
				.addRelationship(new Relationship(ISA, eReferenceSetId));

		concept = conceptService.create(concept, "MAIN");
		String fReferenceSetId = concept.getConceptId();

		// Assert
		referenceSetMembers = memberService.findMembers("MAIN", new MemberSearchRequest().referencedComponentId(fReferenceSetId).referenceSet(REFSET_DESCRIPTOR_REFSET), PAGE_REQUEST).getContent();
		assertFalse(referenceSetMembers.isEmpty());
		assertEquals(1, referenceSetMembers.size());
	}

	/*
	 * A
	 * B C
	 * D E
	 * F
	 *
	 * E & D have descriptor entries. F needs to ignore them as they have different formats.
	 * */
	@Test
	void testDescriptorsWhenMultipleParentsAndGrandParentsWithSequenceB() throws ServiceException {
		Concept concept;
		List<ReferenceSetMember> referenceSetMembers;

		// Create reference sets
		concept = new Concept()
				.addDescription(new Description("A reference set (reference set)"))
				.addDescription(new Description("A reference set"))
				.addAxiom(new Relationship(ISA, REFSET))
				.addRelationship(new Relationship(ISA, REFSET));
		concept = conceptService.create(concept, "MAIN");
		String aReferenceSetId = concept.getConceptId();

		concept = new Concept()
				.addDescription(new Description("B reference set (reference set)"))
				.addDescription(new Description("B reference set"))
				.addAxiom(new Relationship(ISA, aReferenceSetId))
				.addRelationship(new Relationship(ISA, aReferenceSetId));
		concept = conceptService.create(concept, "MAIN");
		String bReferenceSetId = concept.getConceptId();

		concept = new Concept()
				.addDescription(new Description("C reference set (reference set)"))
				.addDescription(new Description("C reference set"))
				.addAxiom(new Relationship(ISA, aReferenceSetId))
				.addRelationship(new Relationship(ISA, aReferenceSetId));
		concept = conceptService.create(concept, "MAIN");
		String cReferenceSetId = concept.getConceptId();

		concept = new Concept()
				.addDescription(new Description("D reference set (reference set)"))
				.addDescription(new Description("D reference set"))
				.addAxiom(new Relationship(ISA, bReferenceSetId), new Relationship(ISA, cReferenceSetId))
				.addRelationship(new Relationship(ISA, bReferenceSetId))
				.addRelationship(new Relationship(ISA, cReferenceSetId));
		concept = conceptService.create(concept, "MAIN");
		String dReferenceSetId = concept.getConceptId();

		concept = new Concept()
				.addDescription(new Description("E reference set (reference set)"))
				.addDescription(new Description("E reference set"))
				.addAxiom(new Relationship(ISA, bReferenceSetId), new Relationship(ISA, cReferenceSetId))
				.addRelationship(new Relationship(ISA, bReferenceSetId))
				.addRelationship(new Relationship(ISA, cReferenceSetId));
		concept = conceptService.create(concept, "MAIN");
		String eReferenceSetId = concept.getConceptId();

		// Create Descriptor manually
		memberService.createMember("MAIN", new ReferenceSetMember(MODEL_MODULE, REFSET_DESCRIPTOR_REFSET, eReferenceSetId).setAdditionalField("attributeType", "1").setAdditionalField("attributeOrder", "0").setAdditionalField("attributeDescription", "3"));
		memberService.createMember("MAIN", new ReferenceSetMember(MODEL_MODULE, REFSET_DESCRIPTOR_REFSET, eReferenceSetId).setAdditionalField("attributeType", "1").setAdditionalField("attributeOrder", "1").setAdditionalField("attributeDescription", "3"));
		referenceSetMembers = memberService.findMembers("MAIN", new MemberSearchRequest().referencedComponentId(eReferenceSetId).referenceSet(REFSET_DESCRIPTOR_REFSET), PAGE_REQUEST).getContent();
		assertFalse(referenceSetMembers.isEmpty());

		// Create reference set
		concept = new Concept()
				.addDescription(new Description("F reference set (reference set)"))
				.addDescription(new Description("F reference set"))
				.addAxiom(new Relationship(ISA, dReferenceSetId), new Relationship(ISA, eReferenceSetId))
				.addRelationship(new Relationship(ISA, dReferenceSetId))
				.addRelationship(new Relationship(ISA, eReferenceSetId));

		concept = conceptService.create(concept, "MAIN");
		String fReferenceSetId = concept.getConceptId();

		// Assert
		referenceSetMembers = memberService.findMembers("MAIN", new MemberSearchRequest().referencedComponentId(fReferenceSetId).referenceSet(REFSET_DESCRIPTOR_REFSET), PAGE_REQUEST).getContent();
		assertFalse(referenceSetMembers.isEmpty());
		assertEquals(2, referenceSetMembers.size());
	}

	@Test
	void testDescriptorsWhenMultipleParentsAndGrandParentsWithSequenceC() throws ServiceException {
		Concept concept;
		List<ReferenceSetMember> referenceSetMembers;

		// Create reference sets
		concept = new Concept()
				.addDescription(new Description("A reference set (reference set)"))
				.addDescription(new Description("A reference set"))
				.addAxiom(new Relationship(ISA, REFSET))
				.addRelationship(new Relationship(ISA, REFSET));
		concept = conceptService.create(concept, "MAIN");
		String aReferenceSetId = concept.getConceptId();

		concept = new Concept()
				.addDescription(new Description("B reference set (reference set)"))
				.addDescription(new Description("B reference set"))
				.addAxiom(new Relationship(ISA, aReferenceSetId))
				.addRelationship(new Relationship(ISA, aReferenceSetId));
		concept = conceptService.create(concept, "MAIN");
		String bReferenceSetId = concept.getConceptId();

		concept = new Concept()
				.addDescription(new Description("C reference set (reference set)"))
				.addDescription(new Description("C reference set"))
				.addAxiom(new Relationship(ISA, aReferenceSetId))
				.addRelationship(new Relationship(ISA, aReferenceSetId));
		concept = conceptService.create(concept, "MAIN");
		String cReferenceSetId = concept.getConceptId();

		concept = new Concept()
				.addDescription(new Description("D reference set (reference set)"))
				.addDescription(new Description("D reference set"))
				.addAxiom(new Relationship(ISA, bReferenceSetId), new Relationship(ISA, cReferenceSetId))
				.addRelationship(new Relationship(ISA, bReferenceSetId))
				.addRelationship(new Relationship(ISA, cReferenceSetId));
		concept = conceptService.create(concept, "MAIN");
		String dReferenceSetId = concept.getConceptId();

		concept = new Concept()
				.addDescription(new Description("E reference set (reference set)"))
				.addDescription(new Description("E reference set"))
				.addAxiom(new Relationship(ISA, bReferenceSetId), new Relationship(ISA, cReferenceSetId))
				.addRelationship(new Relationship(ISA, bReferenceSetId))
				.addRelationship(new Relationship(ISA, cReferenceSetId));
		concept = conceptService.create(concept, "MAIN");
		String eReferenceSetId = concept.getConceptId();

		// Create Descriptor manually
		memberService.createMember("MAIN", new ReferenceSetMember(MODEL_MODULE, REFSET_DESCRIPTOR_REFSET, eReferenceSetId).setAdditionalField("attributeType", "1").setAdditionalField("attributeOrder", "0").setAdditionalField("attributeDescription", "2"));
		memberService.createMember("MAIN", new ReferenceSetMember(MODEL_MODULE, REFSET_DESCRIPTOR_REFSET, eReferenceSetId).setAdditionalField("attributeType", "1").setAdditionalField("attributeOrder", "1").setAdditionalField("attributeDescription", "2"));
		referenceSetMembers = memberService.findMembers("MAIN", new MemberSearchRequest().referencedComponentId(eReferenceSetId).referenceSet(REFSET_DESCRIPTOR_REFSET), PAGE_REQUEST).getContent();
		assertFalse(referenceSetMembers.isEmpty());

		memberService.createMember("MAIN", new ReferenceSetMember(MODEL_MODULE, REFSET_DESCRIPTOR_REFSET, dReferenceSetId).setAdditionalField("attributeType", "3").setAdditionalField("attributeOrder", "0").setAdditionalField("attributeDescription", "4"));
		memberService.createMember("MAIN", new ReferenceSetMember(MODEL_MODULE, REFSET_DESCRIPTOR_REFSET, dReferenceSetId).setAdditionalField("attributeType", "3").setAdditionalField("attributeOrder", "1").setAdditionalField("attributeDescription", "4"));
		referenceSetMembers = memberService.findMembers("MAIN", new MemberSearchRequest().referencedComponentId(eReferenceSetId).referenceSet(REFSET_DESCRIPTOR_REFSET), PAGE_REQUEST).getContent();
		assertFalse(referenceSetMembers.isEmpty());

		// Create reference set
		concept = new Concept()
				.addDescription(new Description("F reference set (reference set)"))
				.addDescription(new Description("F reference set"))
				.addAxiom(new Relationship(ISA, dReferenceSetId), new Relationship(ISA, eReferenceSetId))
				.addRelationship(new Relationship(ISA, dReferenceSetId))
				.addRelationship(new Relationship(ISA, eReferenceSetId));

		concept = conceptService.create(concept, "MAIN");
		String fReferenceSetId = concept.getConceptId();

		// Assert
		referenceSetMembers = memberService.findMembers("MAIN", new MemberSearchRequest().referencedComponentId(fReferenceSetId).referenceSet(REFSET_DESCRIPTOR_REFSET), PAGE_REQUEST).getContent();
		assertTrue(referenceSetMembers.isEmpty());
	}

	@Test
	void testDescriptorsWhenMultipleParentsAndGrandParentsWithSequenceD() throws ServiceException {
		Concept concept;
		List<ReferenceSetMember> referenceSetMembers;

		// Create reference sets
		concept = new Concept()
				.addDescription(new Description("A reference set (reference set)"))
				.addDescription(new Description("A reference set"))
				.addAxiom(new Relationship(ISA, REFSET))
				.addRelationship(new Relationship(ISA, REFSET));
		concept = conceptService.create(concept, "MAIN");
		String aReferenceSetId = concept.getConceptId();

		concept = new Concept()
				.addDescription(new Description("B reference set (reference set)"))
				.addDescription(new Description("B reference set"))
				.addAxiom(new Relationship(ISA, aReferenceSetId))
				.addRelationship(new Relationship(ISA, aReferenceSetId));
		concept = conceptService.create(concept, "MAIN");
		String bReferenceSetId = concept.getConceptId();

		concept = new Concept()
				.addDescription(new Description("C reference set (reference set)"))
				.addDescription(new Description("C reference set"))
				.addAxiom(new Relationship(ISA, aReferenceSetId))
				.addRelationship(new Relationship(ISA, aReferenceSetId));
		concept = conceptService.create(concept, "MAIN");
		String cReferenceSetId = concept.getConceptId();

		concept = new Concept()
				.addDescription(new Description("D reference set (reference set)"))
				.addDescription(new Description("D reference set"))
				.addAxiom(new Relationship(ISA, bReferenceSetId), new Relationship(ISA, cReferenceSetId))
				.addRelationship(new Relationship(ISA, bReferenceSetId))
				.addRelationship(new Relationship(ISA, cReferenceSetId));
		concept = conceptService.create(concept, "MAIN");
		String dReferenceSetId = concept.getConceptId();

		concept = new Concept()
				.addDescription(new Description("E reference set (reference set)"))
				.addDescription(new Description("E reference set"))
				.addAxiom(new Relationship(ISA, bReferenceSetId), new Relationship(ISA, cReferenceSetId))
				.addRelationship(new Relationship(ISA, bReferenceSetId))
				.addRelationship(new Relationship(ISA, cReferenceSetId));
		concept = conceptService.create(concept, "MAIN");
		String eReferenceSetId = concept.getConceptId();

		// Create Descriptor manually
		memberService.createMember("MAIN", new ReferenceSetMember(MODEL_MODULE, REFSET_DESCRIPTOR_REFSET, eReferenceSetId).setAdditionalField("attributeType", "1").setAdditionalField("attributeOrder", "0").setAdditionalField("attributeDescription", "3"));
		memberService.createMember("MAIN", new ReferenceSetMember(MODEL_MODULE, REFSET_DESCRIPTOR_REFSET, eReferenceSetId).setAdditionalField("attributeType", "2").setAdditionalField("attributeOrder", "1").setAdditionalField("attributeDescription", "4"));
		referenceSetMembers = memberService.findMembers("MAIN", new MemberSearchRequest().referencedComponentId(eReferenceSetId).referenceSet(REFSET_DESCRIPTOR_REFSET), PAGE_REQUEST).getContent();
		assertFalse(referenceSetMembers.isEmpty());

		memberService.createMember("MAIN", new ReferenceSetMember(MODEL_MODULE, REFSET_DESCRIPTOR_REFSET, dReferenceSetId).setAdditionalField("attributeType", "5").setAdditionalField("attributeOrder", "0").setAdditionalField("attributeDescription", "7"));
		memberService.createMember("MAIN", new ReferenceSetMember(MODEL_MODULE, REFSET_DESCRIPTOR_REFSET, dReferenceSetId).setAdditionalField("attributeType", "6").setAdditionalField("attributeOrder", "1").setAdditionalField("attributeDescription", "8"));
		referenceSetMembers = memberService.findMembers("MAIN", new MemberSearchRequest().referencedComponentId(eReferenceSetId).referenceSet(REFSET_DESCRIPTOR_REFSET), PAGE_REQUEST).getContent();
		assertFalse(referenceSetMembers.isEmpty());

		// Create reference set
		concept = new Concept()
				.addDescription(new Description("F reference set (reference set)"))
				.addDescription(new Description("F reference set"))
				.addAxiom(new Relationship(ISA, dReferenceSetId), new Relationship(ISA, eReferenceSetId))
				.addRelationship(new Relationship(ISA, dReferenceSetId))
				.addRelationship(new Relationship(ISA, eReferenceSetId));

		concept = conceptService.create(concept, "MAIN");
		String fReferenceSetId = concept.getConceptId();

		// Assert
		referenceSetMembers = memberService.findMembers("MAIN", new MemberSearchRequest().referencedComponentId(fReferenceSetId).referenceSet(REFSET_DESCRIPTOR_REFSET), PAGE_REQUEST).getContent();
		assertTrue(referenceSetMembers.isEmpty());
	}

	@Test
	void testDescriptorsWhenMultipleParentsAndGrandParentsWithSequenceE() throws ServiceException {
		Concept concept;
		List<ReferenceSetMember> referenceSetMembers;

		// Create reference sets
		concept = new Concept()
				.addDescription(new Description("A reference set (reference set)"))
				.addDescription(new Description("A reference set"))
				.addAxiom(new Relationship(ISA, REFSET))
				.addRelationship(new Relationship(ISA, REFSET));
		concept = conceptService.create(concept, "MAIN");
		String aReferenceSetId = concept.getConceptId();

		concept = new Concept()
				.addDescription(new Description("B reference set (reference set)"))
				.addDescription(new Description("B reference set"))
				.addAxiom(new Relationship(ISA, aReferenceSetId))
				.addRelationship(new Relationship(ISA, aReferenceSetId));
		concept = conceptService.create(concept, "MAIN");
		String bReferenceSetId = concept.getConceptId();

		concept = new Concept()
				.addDescription(new Description("C reference set (reference set)"))
				.addDescription(new Description("C reference set"))
				.addAxiom(new Relationship(ISA, aReferenceSetId))
				.addRelationship(new Relationship(ISA, aReferenceSetId));
		concept = conceptService.create(concept, "MAIN");
		String cReferenceSetId = concept.getConceptId();

		concept = new Concept()
				.addDescription(new Description("D reference set (reference set)"))
				.addDescription(new Description("D reference set"))
				.addAxiom(new Relationship(ISA, bReferenceSetId), new Relationship(ISA, cReferenceSetId))
				.addRelationship(new Relationship(ISA, bReferenceSetId))
				.addRelationship(new Relationship(ISA, cReferenceSetId));
		concept = conceptService.create(concept, "MAIN");
		String dReferenceSetId = concept.getConceptId();

		concept = new Concept()
				.addDescription(new Description("E reference set (reference set)"))
				.addDescription(new Description("E reference set"))
				.addAxiom(new Relationship(ISA, bReferenceSetId), new Relationship(ISA, cReferenceSetId))
				.addRelationship(new Relationship(ISA, bReferenceSetId))
				.addRelationship(new Relationship(ISA, cReferenceSetId));
		concept = conceptService.create(concept, "MAIN");
		String eReferenceSetId = concept.getConceptId();

		// Create Descriptor manually
		memberService.createMember("MAIN", new ReferenceSetMember(MODEL_MODULE, REFSET_DESCRIPTOR_REFSET, eReferenceSetId).setAdditionalField("attributeType", "1").setAdditionalField("attributeOrder", "0").setAdditionalField("attributeDescription", "1"));
		referenceSetMembers = memberService.findMembers("MAIN", new MemberSearchRequest().referencedComponentId(eReferenceSetId).referenceSet(REFSET_DESCRIPTOR_REFSET), PAGE_REQUEST).getContent();
		assertFalse(referenceSetMembers.isEmpty());

		memberService.createMember("MAIN", new ReferenceSetMember(MODEL_MODULE, REFSET_DESCRIPTOR_REFSET, dReferenceSetId).setAdditionalField("attributeType", "2").setAdditionalField("attributeOrder", "0").setAdditionalField("attributeDescription", "2"));
		referenceSetMembers = memberService.findMembers("MAIN", new MemberSearchRequest().referencedComponentId(eReferenceSetId).referenceSet(REFSET_DESCRIPTOR_REFSET), PAGE_REQUEST).getContent();
		assertFalse(referenceSetMembers.isEmpty());

		// Create reference set
		concept = new Concept()
				.addDescription(new Description("F reference set (reference set)"))
				.addDescription(new Description("F reference set"))
				.addAxiom(new Relationship(ISA, dReferenceSetId), new Relationship(ISA, eReferenceSetId))
				.addRelationship(new Relationship(ISA, dReferenceSetId))
				.addRelationship(new Relationship(ISA, eReferenceSetId));

		concept = conceptService.create(concept, "MAIN");
		String fReferenceSetId = concept.getConceptId();

		// Assert
		referenceSetMembers = memberService.findMembers("MAIN", new MemberSearchRequest().referencedComponentId(fReferenceSetId).referenceSet(REFSET_DESCRIPTOR_REFSET), PAGE_REQUEST).getContent();
		assertTrue(referenceSetMembers.isEmpty());
	}

	@Test
	void testDescriptorsWhenMultipleParentsAndGrandParentsWithSequenceF() throws ServiceException {
		Concept concept;
		List<ReferenceSetMember> referenceSetMembers;

		// Create reference sets
		concept = new Concept()
				.addDescription(new Description("A reference set (reference set)"))
				.addDescription(new Description("A reference set"))
				.addAxiom(new Relationship(ISA, REFSET))
				.addRelationship(new Relationship(ISA, REFSET));
		concept = conceptService.create(concept, "MAIN");
		String aReferenceSetId = concept.getConceptId();

		concept = new Concept()
				.addDescription(new Description("B reference set (reference set)"))
				.addDescription(new Description("B reference set"))
				.addAxiom(new Relationship(ISA, aReferenceSetId))
				.addRelationship(new Relationship(ISA, aReferenceSetId));
		concept = conceptService.create(concept, "MAIN");
		String bReferenceSetId = concept.getConceptId();

		concept = new Concept()
				.addDescription(new Description("C reference set (reference set)"))
				.addDescription(new Description("C reference set"))
				.addAxiom(new Relationship(ISA, aReferenceSetId))
				.addRelationship(new Relationship(ISA, aReferenceSetId));
		concept = conceptService.create(concept, "MAIN");
		String cReferenceSetId = concept.getConceptId();

		concept = new Concept()
				.addDescription(new Description("D reference set (reference set)"))
				.addDescription(new Description("D reference set"))
				.addAxiom(new Relationship(ISA, bReferenceSetId), new Relationship(ISA, cReferenceSetId))
				.addRelationship(new Relationship(ISA, bReferenceSetId))
				.addRelationship(new Relationship(ISA, cReferenceSetId));
		concept = conceptService.create(concept, "MAIN");
		String dReferenceSetId = concept.getConceptId();

		concept = new Concept()
				.addDescription(new Description("E reference set (reference set)"))
				.addDescription(new Description("E reference set"))
				.addAxiom(new Relationship(ISA, bReferenceSetId), new Relationship(ISA, cReferenceSetId))
				.addRelationship(new Relationship(ISA, bReferenceSetId))
				.addRelationship(new Relationship(ISA, cReferenceSetId));
		concept = conceptService.create(concept, "MAIN");
		String eReferenceSetId = concept.getConceptId();

		concept = new Concept()
				.addDescription(new Description("F reference set (reference set)"))
				.addDescription(new Description("F reference set"))
				.addAxiom(new Relationship(ISA, bReferenceSetId), new Relationship(ISA, cReferenceSetId))
				.addRelationship(new Relationship(ISA, bReferenceSetId))
				.addRelationship(new Relationship(ISA, cReferenceSetId));
		concept = conceptService.create(concept, "MAIN");
		String fReferenceSetId = concept.getConceptId();

		// Create Descriptor manually
		memberService.createMember("MAIN", new ReferenceSetMember(MODEL_MODULE, REFSET_DESCRIPTOR_REFSET, bReferenceSetId).setAdditionalField("attributeType", "0").setAdditionalField("attributeOrder", "0").setAdditionalField("attributeDescription", "0"));
		referenceSetMembers = memberService.findMembers("MAIN", new MemberSearchRequest().referencedComponentId(bReferenceSetId).referenceSet(REFSET_DESCRIPTOR_REFSET), PAGE_REQUEST).getContent();
		assertFalse(referenceSetMembers.isEmpty());

		memberService.createMember("MAIN", new ReferenceSetMember(MODEL_MODULE, REFSET_DESCRIPTOR_REFSET, eReferenceSetId).setAdditionalField("attributeType", "1").setAdditionalField("attributeOrder", "0").setAdditionalField("attributeDescription", "1"));
		referenceSetMembers = memberService.findMembers("MAIN", new MemberSearchRequest().referencedComponentId(eReferenceSetId).referenceSet(REFSET_DESCRIPTOR_REFSET), PAGE_REQUEST).getContent();
		assertFalse(referenceSetMembers.isEmpty());

		memberService.createMember("MAIN", new ReferenceSetMember(MODEL_MODULE, REFSET_DESCRIPTOR_REFSET, dReferenceSetId).setAdditionalField("attributeType", "2").setAdditionalField("attributeOrder", "0").setAdditionalField("attributeDescription", "2"));
		referenceSetMembers = memberService.findMembers("MAIN", new MemberSearchRequest().referencedComponentId(eReferenceSetId).referenceSet(REFSET_DESCRIPTOR_REFSET), PAGE_REQUEST).getContent();
		assertFalse(referenceSetMembers.isEmpty());

		// Create reference set
		concept = new Concept()
				.addDescription(new Description("G reference set (reference set)"))
				.addDescription(new Description("G reference set"))
				.addAxiom(new Relationship(ISA, dReferenceSetId), new Relationship(ISA, eReferenceSetId), new Relationship(ISA, fReferenceSetId))
				.addRelationship(new Relationship(ISA, dReferenceSetId))
				.addRelationship(new Relationship(ISA, eReferenceSetId))
				.addRelationship(new Relationship(ISA, fReferenceSetId));

		concept = conceptService.create(concept, "MAIN");
		String gReferenceSetId = concept.getConceptId();

		// Assert
		referenceSetMembers = memberService.findMembers("MAIN", new MemberSearchRequest().referencedComponentId(gReferenceSetId).referenceSet(REFSET_DESCRIPTOR_REFSET), PAGE_REQUEST).getContent();
		assertTrue(referenceSetMembers.isEmpty());
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

	private ReferenceSetMember getReferenceSetMember(List<ReferenceSetMember> members, String referencedComponentId) {
		for (ReferenceSetMember member : members) {
			if (member.getReferencedComponentId().equals(referencedComponentId)) {
				return member;
			}

		}

		return null;
	}
}
