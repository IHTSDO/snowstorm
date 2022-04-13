package org.snomed.snowstorm.ecl;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.api.ComponentService;
import org.snomed.snowstorm.TestConfig;
import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.core.data.domain.Concepts;
import org.snomed.snowstorm.core.data.domain.ReferenceSetMember;
import org.snomed.snowstorm.core.data.domain.Relationship;
import org.snomed.snowstorm.core.data.services.*;
import org.snomed.snowstorm.core.data.services.classification.ClassificationService;
import org.springframework.beans.factory.annotation.Autowired;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static io.kaicode.elasticvc.domain.Branch.MAIN;
import static org.junit.Assert.assertEquals;
import static org.snomed.snowstorm.TestConcepts.*;
import static org.snomed.snowstorm.core.data.domain.Concepts.*;

public class ECLQueryServiceStatedAxiomTestConfig extends TestConfig {

	@Autowired
	private BranchService branchService;

	@Autowired
	private ConceptService conceptService;

	@Autowired
	private ReferenceSetMemberService memberService;

	@Autowired
	private CodeSystemService codeSystemService;

	@Autowired
	private ClassificationService classificationService;

	@Autowired
	private PermissionService permissionService;

	@PostConstruct
	public void beforeAll() throws ServiceException {
		List<Concept> allConcepts = new ArrayList<>();

		allConcepts.add(new Concept(SNOMEDCT_ROOT));
		allConcepts.add(new Concept(ISA).addAxiom(new Relationship(ISA, SNOMEDCT_ROOT)));
		allConcepts.add(new Concept(MODEL_COMPONENT).addAxiom(new Relationship(ISA, SNOMEDCT_ROOT)));
		allConcepts.add(new Concept(CONCEPT_MODEL_ATTRIBUTE).addAxiom(new Relationship(ISA, MODEL_COMPONENT)));
		allConcepts.add(new Concept(CONCEPT_MODEL_OBJECT_ATTRIBUTE).addAxiom(new Relationship(ISA, CONCEPT_MODEL_ATTRIBUTE)));

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
		allConcepts.add(new Concept(FINDING_SITE));
		allConcepts.add(new Concept(ASSOCIATED_MORPHOLOGY));
		allConcepts.add(new Concept(PROCEDURE_SITE));
		allConcepts.add(new Concept(PROCEDURE_SITE_DIRECT));
		allConcepts.add(new Concept(LATERALITY));

		allConcepts.add(new Concept(RIGHT).addAxiom(new Relationship(ISA, SNOMEDCT_ROOT)));

		allConcepts.add(new Concept(BODY_STRUCTURE).addAxiom(new Relationship(ISA, SNOMEDCT_ROOT)));
		allConcepts.add(new Concept(Concepts.HEART_STRUCTURE).addAxiom(new Relationship(ISA, BODY_STRUCTURE)));
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
				new Relationship(PROCEDURE_SITE, Concepts.HEART_STRUCTURE)));

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

		branchService.create(MAIN);
		conceptService.batchCreate(allConcepts, MAIN);
		memberService.createMembers(MAIN, new HashSet<>(axiomMembers));

		memberService.createMembers(MAIN, Sets.newHashSet(
				new ReferenceSetMember(Concepts.CORE_MODULE, Concepts.REFSET_MRCM_ATTRIBUTE_DOMAIN, CLINICAL_FINDING),
				new ReferenceSetMember(Concepts.CORE_MODULE, Concepts.REFSET_MRCM_ATTRIBUTE_DOMAIN, BODY_STRUCTURE)));

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

	@PreDestroy
	public void tearDown() throws InterruptedException {
		branchService.deleteAll();
		conceptService.deleteAll();
		codeSystemService.deleteAll();
		classificationService.deleteAll();
		permissionService.deleteAll();
	}

}
