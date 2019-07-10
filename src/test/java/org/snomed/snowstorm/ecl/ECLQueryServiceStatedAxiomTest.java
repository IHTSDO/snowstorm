package org.snomed.snowstorm.ecl;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import io.kaicode.elasticvc.api.ComponentService;
import org.junit.Before;
import org.junit.Test;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.data.services.ReferenceSetMemberService;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.snomed.snowstorm.core.data.domain.Concepts.*;

/**
 * In this test suite we run all the same ECL query tests again against the stated form
 * but the data is set up using axioms without any stated relationships.
 */
public class ECLQueryServiceStatedAxiomTest extends ECLQueryServiceTest {

	@Autowired
	private ReferenceSetMemberService referenceSetMemberService;

	@Before
	public void setup() throws ServiceException {
		List<Concept> allConcepts = new ArrayList<>();

		allConcepts.add(new Concept(SNOMEDCT_ROOT));
		allConcepts.add(new Concept(ISA).addAxiom(new Relationship(ISA, SNOMEDCT_ROOT)));
		allConcepts.add(new Concept(MODEL_COMPONENT).addAxiom(new Relationship(ISA, SNOMEDCT_ROOT)));
		allConcepts.add(new Concept(CONCEPT_MODEL_ATTRIBUTE).addAxiom(new Relationship(ISA, MODEL_COMPONENT)));

		// Add attributes using axioms
		// NOTE there is no axiom linking the Concept Model Object Attribute to the concept hierarchy.
		// This is because the properties are considered in a separate hierarchy in OWL.
		List<ReferenceSetMember> axiomMembers = Lists.newArrayList(
				axiomMember(MODEL_MODULE, OWL_AXIOM_REFERENCE_SET, FINDING_SITE, String.format("SubObjectPropertyOf(:%s :%s)", FINDING_SITE, CONCEPT_MODEL_OBJECT_ATTRIBUTE)),
				axiomMember(MODEL_MODULE, OWL_AXIOM_REFERENCE_SET, ASSOCIATED_MORPHOLOGY, String.format("SubObjectPropertyOf(:%s :%s)", ASSOCIATED_MORPHOLOGY, CONCEPT_MODEL_OBJECT_ATTRIBUTE)),
				axiomMember(MODEL_MODULE, OWL_AXIOM_REFERENCE_SET, PROCEDURE_SITE, String.format("SubObjectPropertyOf(:%s :%s)", PROCEDURE_SITE, CONCEPT_MODEL_OBJECT_ATTRIBUTE)),
				axiomMember(MODEL_MODULE, OWL_AXIOM_REFERENCE_SET, PROCEDURE_SITE_DIRECT, String.format("SubObjectPropertyOf(:%s :%s)", PROCEDURE_SITE_DIRECT, PROCEDURE_SITE)),
				axiomMember(MODEL_MODULE, OWL_AXIOM_REFERENCE_SET, LATERALITY, String.format("SubObjectPropertyOf(:%s :%s)", LATERALITY, CONCEPT_MODEL_OBJECT_ATTRIBUTE))
		);
		// Also add attribute concepts, with no stated relationships.
		allConcepts.add(new Concept(CONCEPT_MODEL_OBJECT_ATTRIBUTE));
		allConcepts.add(new Concept(FINDING_SITE));
		allConcepts.add(new Concept(ASSOCIATED_MORPHOLOGY));
		allConcepts.add(new Concept(PROCEDURE_SITE));
		allConcepts.add(new Concept(PROCEDURE_SITE_DIRECT));
		allConcepts.add(new Concept(LATERALITY));

		allConcepts.add(new Concept(RIGHT).addAxiom(new Relationship(ISA, SNOMEDCT_ROOT)));

		allConcepts.add(new Concept(BODY_STRUCTURE).addAxiom(new Relationship(ISA, SNOMEDCT_ROOT)));
		allConcepts.add(new Concept(HEART_STRUCTURE).addAxiom(new Relationship(ISA, BODY_STRUCTURE)));
		allConcepts.add(new Concept(SKIN_STRUCTURE).addAxiom(new Relationship(ISA, BODY_STRUCTURE)));
		allConcepts.add(new Concept(THORACIC_STRUCTURE).addAxiom(new Relationship(ISA, BODY_STRUCTURE)));
		allConcepts.add(new Concept(PULMONARY_VALVE_STRUCTURE).addAxiom(new Relationship(ISA, BODY_STRUCTURE)));
		allConcepts.add(new Concept(RIGHT_VENTRICULAR_STRUCTURE).addAxiom(
				new Relationship(ISA, BODY_STRUCTURE),
				new Relationship(LATERALITY, RIGHT)));
		allConcepts.add(new Concept(LEFT_FOOT).addAxiom(new Relationship(ISA, BODY_STRUCTURE)));
		allConcepts.add(new Concept(RIGHT_FOOT).addAxiom(new Relationship(ISA, BODY_STRUCTURE)));

		allConcepts.add(new Concept(STENOSIS).addAxiom(new Relationship(ISA, BODY_STRUCTURE)));
		allConcepts.add(new Concept(HYPERTROPHY).addAxiom(new Relationship(ISA, BODY_STRUCTURE)));
		allConcepts.add(new Concept(HEMORRHAGE).addAxiom(new Relationship(ISA, BODY_STRUCTURE)));

		allConcepts.add(new Concept(CLINICAL_FINDING).addAxiom(new Relationship(ISA, SNOMEDCT_ROOT)));
		allConcepts.add(new Concept(DISORDER).addAxiom(new Relationship(ISA, CLINICAL_FINDING)));
		allConcepts.add(new Concept(BLEEDING).addAxiom(
				new Relationship(ISA, CLINICAL_FINDING),
				new Relationship(ASSOCIATED_MORPHOLOGY, HEMORRHAGE)));

		allConcepts.add(new Concept(BLEEDING_SKIN).addAxiom(
				new Relationship(ISA, BLEEDING),
				new Relationship(ASSOCIATED_MORPHOLOGY, HEMORRHAGE),
				new Relationship(FINDING_SITE, SKIN_STRUCTURE)));

		allConcepts.add(new Concept(PENTALOGY_OF_FALLOT).addAxiom(
				new Relationship(ISA, DISORDER),
				new Relationship(FINDING_SITE, PULMONARY_VALVE_STRUCTURE).setGroupId(1),
				new Relationship(ASSOCIATED_MORPHOLOGY, STENOSIS).setGroupId(1),
				new Relationship(FINDING_SITE, RIGHT_VENTRICULAR_STRUCTURE).setGroupId(2),
				new Relationship(ASSOCIATED_MORPHOLOGY, HYPERTROPHY).setGroupId(2)));

		allConcepts.add(new Concept(PENTALOGY_OF_FALLOT_INCORRECT_GROUPING).addAxiom(
				new Relationship(ISA, DISORDER),
				new Relationship(FINDING_SITE, PULMONARY_VALVE_STRUCTURE).setGroupId(1),
				new Relationship(ASSOCIATED_MORPHOLOGY, STENOSIS).setGroupId(2),// <-- was group 1
				new Relationship(FINDING_SITE, RIGHT_VENTRICULAR_STRUCTURE).setGroupId(2),
				new Relationship(ASSOCIATED_MORPHOLOGY, HYPERTROPHY).setGroupId(1)));// <-- was group 2

		allConcepts.add(new Concept(PROCEDURE).addAxiom(new Relationship(ISA, SNOMEDCT_ROOT)));
		
		allConcepts.add(new Concept(OPERATION_ON_HEART).addAxiom(
				new Relationship(ISA, PROCEDURE),
				new Relationship(PROCEDURE_SITE, HEART_STRUCTURE)));

		allConcepts.add(new Concept(CHEST_IMAGING).addAxiom(
				new Relationship(ISA, PROCEDURE),
				new Relationship(PROCEDURE_SITE_DIRECT, THORACIC_STRUCTURE)));
		
		allConcepts.add(new Concept(AMPUTATION_FOOT_LEFT).addAxiom(
				new Relationship(ISA, PROCEDURE),
				new Relationship(PROCEDURE_SITE, LEFT_FOOT).setGroupId(1)));
		
		allConcepts.add(new Concept(AMPUTATION_FOOT_RIGHT).addAxiom(
				new Relationship(ISA, PROCEDURE),
				new Relationship(PROCEDURE_SITE, RIGHT_FOOT).setGroupId(1)));
	
		allConcepts.add(new Concept(AMPUTATION_FOOT_BILATERAL).addAxiom(
				new Relationship(ISA, PROCEDURE),
				new Relationship(PROCEDURE_SITE, LEFT_FOOT).setGroupId(1),
				new Relationship(PROCEDURE_SITE, RIGHT_FOOT).setGroupId(2)));

		conceptService.batchCreate(allConcepts, MAIN);
		referenceSetMemberService.createMembers(MAIN, new HashSet<>(axiomMembers));

		allConceptIds = allConcepts.stream().map(Concept::getId).collect(Collectors.toSet());

		memberService.createMembers(MAIN, Sets.newHashSet(
				new ReferenceSetMember(Concepts.CORE_MODULE, Concepts.REFSET_MRCM_ATTRIBUTE_DOMAIN, CLINICAL_FINDING),
				new ReferenceSetMember(Concepts.CORE_MODULE, Concepts.REFSET_MRCM_ATTRIBUTE_DOMAIN, BODY_STRUCTURE)));

		branchCriteria = versionControlHelper.getBranchCriteria(MAIN);

		String bleedingOwlExpression = memberService.findMembers(MAIN, BLEEDING, ComponentService.LARGE_PAGE).getContent().iterator().next().getAdditionalField(ReferenceSetMember.OwlExpressionFields.OWL_EXPRESSION);

		/*
		SubClassOf(
			:131148009
			ObjectIntersectionOf(
				:404684003
				ObjectSomeValuesFrom(
					:609096000 - group - self grouped
					ObjectSomeValuesFrom(
						:116676008
						:50960005))))
		 */

		assertEquals("SubClassOf(:131148009 ObjectIntersectionOf(:404684003 ObjectSomeValuesFrom(:609096000 ObjectSomeValuesFrom(:116676008 :50960005))))", bleedingOwlExpression);

		/*
		SubClassOf(
			:297968009
			ObjectIntersectionOf(
				:131148009
				ObjectSomeValuesFrom(
					:609096000 - group - self grouped
					ObjectSomeValuesFrom(
						:116676008
						:50960005
					)
				)
				ObjectSomeValuesFrom(
					:609096000 - group - self grouped
					ObjectSomeValuesFrom(
						:363698007
						:39937001))))
		 */
		assertEquals("SubClassOf(:297968009 ObjectIntersectionOf(:131148009 ObjectSomeValuesFrom(:609096000 ObjectSomeValuesFrom(:116676008 :50960005)) ObjectSomeValuesFrom(:609096000 ObjectSomeValuesFrom(:363698007 :39937001))))",
				memberService.findMembers(MAIN, BLEEDING_SKIN, ComponentService.LARGE_PAGE).getContent().iterator().next().getAdditionalField(ReferenceSetMember.OwlExpressionFields.OWL_EXPRESSION));
	}

	private ReferenceSetMember axiomMember(String moduleId, String refsetId, String conceptId, String axiomOwlExpression) {
		ReferenceSetMember member = new ReferenceSetMember(moduleId, refsetId, conceptId);
		member.setAdditionalField(ReferenceSetMember.OwlExpressionFields.OWL_EXPRESSION, axiomOwlExpression);
		return member;
	}

	@Test
	// In axioms all non is-a attributes in group 0 become self grouped unless the MRCM Attribute Domain reference set explicitly states that they should never be grouped
	public void attributeGroupCardinality() {
		assertEquals(
				"Match clinical finding with at least one grouped finding site attributes.",
				Sets.newHashSet(PENTALOGY_OF_FALLOT, PENTALOGY_OF_FALLOT_INCORRECT_GROUPING, BLEEDING_SKIN),
				strings(selectConceptIds("<404684003 |Clinical finding|: { 363698007 |Finding site| = * }")));

		assertEquals(
				"Match clinical finding with zero grouped finding site attributes.",
				Sets.newHashSet(DISORDER, BLEEDING),
				strings(selectConceptIds("<404684003 |Clinical finding|: [0..0]{ 363698007 |Finding site| = * }")));

		assertEquals(
				"Match clinical finding with one grouped finding site attributes.",
				Sets.newHashSet(BLEEDING_SKIN),
				strings(selectConceptIds("<404684003 |Clinical finding|: [1..1]{ 363698007 |Finding site| = * }")));

		assertEquals(
				"Match clinical finding with one or two grouped finding site attributes.",
				Sets.newHashSet(PENTALOGY_OF_FALLOT, PENTALOGY_OF_FALLOT_INCORRECT_GROUPING, BLEEDING_SKIN),
				strings(selectConceptIds("<404684003 |Clinical finding|: [1..2]{ 363698007 |Finding site| = * }")));

		assertEquals(
				"Match clinical finding with three or more grouped finding site attributes.",
				Sets.newHashSet(),
				strings(selectConceptIds("<404684003 |Clinical finding|: [3..*]{ 363698007 |Finding site| = * }")));
	}
	
	@Test
	public void attributeGroupDisjunction() {
		//This test has been overridden from the base class because the serialisation of axioms into the axiom reference
		//set causes attributes to be grouped where this is required by the MRCM.
		//As such, Bleeding and Bleeding skin - which are created with ungrouped attributes above, will
		//match this ECL as those attributes become grouped when expressed in axioms.
		assertEquals(
				"Match clinical finding with at least one grouped finding site attributes.",
				Sets.newHashSet(PENTALOGY_OF_FALLOT, PENTALOGY_OF_FALLOT_INCORRECT_GROUPING, BLEEDING, BLEEDING_SKIN),
				strings(selectConceptIds("<404684003 |Clinical finding|: { 363698007 |Finding site| = * } OR { 116676008 |Associated morphology| = * }")));
	}

	@Test
	public void selectMemberOfReferenceSet() {
		// Member of x
		assertEquals(
				Sets.newHashSet(CLINICAL_FINDING, BODY_STRUCTURE),
				strings(selectConceptIds("^" + REFSET_MRCM_ATTRIBUTE_DOMAIN))
		);

		// Member of any reference set
		// All concepts with axioms are members
		assertEquals(allConceptIds.stream().filter(id -> !id.equals(Concepts.SNOMEDCT_ROOT) && !id.equals(CONCEPT_MODEL_OBJECT_ATTRIBUTE)).collect(Collectors.toSet()), strings(selectConceptIds("^*")));
	}

}
