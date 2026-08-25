package org.snomed.snowstorm.core.data.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.core.data.domain.ConceptMini;
import org.snomed.snowstorm.core.data.domain.ConceptMicro;
import org.snomed.snowstorm.core.data.domain.ReferenceSetMember;
import org.snomed.snowstorm.core.data.domain.Relationship;
import org.snomed.snowstorm.rest.pojo.InactivationImpactResponse;
import org.snomed.snowstorm.rest.pojo.InactivationImpactResponse.InactivationImpactConcept;
import org.snomed.snowstorm.rest.pojo.InactivationReasonsResponse;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.snomed.snowstorm.config.Config.DEFAULT_LANGUAGE_DIALECTS;
import static org.snomed.snowstorm.core.data.domain.Concepts.*;

class InactivationServiceTest extends AbstractTest {

	private static final String TO_INACTIVATE = "100001";
	private static final String CHILD = "100002";
	private static final String ATTRIBUTE_SOURCE = "100003";
	private static final String GCI_SOURCE = "100004";
	private static final String ASSOC_SOURCE = "100005";
	private static final String REPLACEMENT = "100006";

	@Autowired
	private InactivationService inactivationService;

	@Autowired
	private ConceptService conceptService;

	@Autowired
	private ReferenceSetMemberService referenceSetMemberService;

	@BeforeEach
	void setUp() throws ServiceException {
		conceptService.create(new Concept(SNOMEDCT_ROOT).addFSN("SNOMED CT Concept (SNOMED RT+CTV3)"), MAIN);
		conceptService.create(new Concept(ISA).addFSN("Is a (attribute)").addAxiom(new Relationship(ISA, SNOMEDCT_ROOT)), MAIN);
		conceptService.create(new Concept(CLINICAL_FINDING).addFSN("Clinical finding (finding)").addAxiom(new Relationship(ISA, SNOMEDCT_ROOT)), MAIN);
		conceptService.create(new Concept(FINDING_SITE).addFSN("Finding site (attribute)").addAxiom(new Relationship(ISA, SNOMEDCT_ROOT)), MAIN);
		conceptService.create(new Concept(REFSET).addFSN("Reference set (foundation metadata concept)").addAxiom(new Relationship(ISA, SNOMEDCT_ROOT)), MAIN);
		conceptService.create(new Concept(REFSET_HISTORICAL_ASSOCIATION).addFSN("Historical association reference set")
				.addAxiom(new Relationship(ISA, REFSET)), MAIN);
		conceptService.create(new Concept(REFSET_SAME_AS_ASSOCIATION).addFSN("SAME AS association reference set")
				.addAxiom(new Relationship(ISA, REFSET_HISTORICAL_ASSOCIATION)), MAIN);

		conceptService.create(new Concept(TO_INACTIVATE).addFSN("Disease to inactivate (disorder)")
				.addAxiom(new Relationship(ISA, CLINICAL_FINDING)), MAIN);
		conceptService.create(new Concept(CHILD).addFSN("Child of disease (disorder)")
				.addAxiom(new Relationship(ISA, TO_INACTIVATE)), MAIN);
		conceptService.create(new Concept(ATTRIBUTE_SOURCE).addFSN("Finding with site (finding)")
				.addAxiom(new Relationship(ISA, CLINICAL_FINDING), new Relationship(FINDING_SITE, TO_INACTIVATE)), MAIN);
		conceptService.create(new Concept(GCI_SOURCE).addFSN("GCI source (disorder)")
				.addAxiom(new Relationship(ISA, CLINICAL_FINDING))
				.addGeneralConceptInclusionAxiom(new Relationship(ISA, CLINICAL_FINDING), new Relationship(FINDING_SITE, TO_INACTIVATE)), MAIN);
		conceptService.create(new Concept(ASSOC_SOURCE).addFSN("Inactive duplicate (disorder)")
				.addAxiom(new Relationship(ISA, CLINICAL_FINDING)), MAIN);
		conceptService.create(new Concept(REPLACEMENT).addFSN("Replacement disease (disorder)")
				.addAxiom(new Relationship(ISA, CLINICAL_FINDING)), MAIN);

		referenceSetMemberService.createMember(MAIN, new ReferenceSetMember(CORE_MODULE, REFSET_SAME_AS_ASSOCIATION, ASSOC_SOURCE)
				.setAdditionalField(ReferenceSetMember.AssociationFields.TARGET_COMP_ID, TO_INACTIVATE));
	}

	@Test
	void getInactivationReasons_shouldReturnConceptAndDescriptionReasons() {
		InactivationReasonsResponse response = inactivationService.getInactivationReasons(MAIN);

		assertFalse(response.conceptReasons().isEmpty());
		assertFalse(response.descriptionReasons().isEmpty());
		assertTrue(response.conceptReasons().stream().anyMatch(reason -> "DUPLICATE".equals(reason.name())));
		assertTrue(response.descriptionReasons().stream().anyMatch(reason -> "NOT_SEMANTICALLY_EQUIVALENT".equals(reason.name())));
	}

	@Test
	void getInactivationImpact_shouldPopulateAllReviewTabs() {
		InactivationImpactResponse response = inactivationService.getInactivationImpact(
				MAIN, TO_INACTIVATE, DEFAULT_LANGUAGE_DIALECTS);

		assertEquals(1, response.affectedChildren().size());
		InactivationImpactConcept child = response.affectedChildren().get(0);
		assertEquals(CHILD, child.concept().getId());
		assertEquals("Child of disease (disorder)", child.concept().getTerm());
		assertEquals(List.of(new ConceptMicro(TO_INACTIVATE, "Disease to inactivate (disorder)")), child.currentParents());
		assertEquals("Disease to inactivate (disorder)", child.currentParents().get(0).getTerm());

		assertEquals(1, response.affectedAttributeConcepts().size());
		InactivationImpactConcept attribute = response.affectedAttributeConcepts().get(0);
		assertEquals(ATTRIBUTE_SOURCE, attribute.concept().getId());
		assertEquals("Finding with site (finding)", attribute.concept().getTerm());

		assertEquals(1, response.affectedGcis().size());
		InactivationImpactConcept gci = response.affectedGcis().get(0);
		assertEquals(GCI_SOURCE, gci.concept().getId());
		assertEquals("GCI source (disorder)", gci.concept().getTerm());

		assertEquals(1, response.existingHistoricalAssociations().size());
		ReferenceSetMember association = response.existingHistoricalAssociations().get(0);
		assertEquals(ASSOC_SOURCE, association.getReferencedComponentId());
		assertEquals(REFSET_SAME_AS_ASSOCIATION, association.getRefsetId());
		assertTrue(association.getReferencedComponent() instanceof ConceptMini);
		assertEquals("Inactive duplicate (disorder)", ((ConceptMini) association.getReferencedComponent()).getFsnTerm());

		assertEquals(4, response.totalAffectedConcepts());
	}

	@Test
	void getInactivationImpact_shouldReturnEmptyImpactWhenConceptHasNoDependents() {
		InactivationImpactResponse response = inactivationService.getInactivationImpact(
				MAIN, REPLACEMENT, DEFAULT_LANGUAGE_DIALECTS);

		assertTrue(response.affectedChildren().isEmpty());
		assertTrue(response.affectedAttributeConcepts().isEmpty());
		assertTrue(response.affectedGcis().isEmpty());
		assertTrue(response.existingHistoricalAssociations().isEmpty());
		assertEquals(0, response.totalAffectedConcepts());
	}

	@Test
	void getInactivationImpact_shouldThrowWhenConceptMissing() {
		NotFoundException exception = assertThrows(NotFoundException.class,
				() -> inactivationService.getInactivationImpact(MAIN, "999999", DEFAULT_LANGUAGE_DIALECTS));
		assertTrue(exception.getMessage().contains("999999"));
	}
}
