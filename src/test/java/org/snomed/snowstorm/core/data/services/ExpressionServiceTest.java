package org.snomed.snowstorm.core.data.services;

import io.kaicode.elasticvc.api.BranchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.TestConfig;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.data.domain.expression.Expression;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.snomed.snowstorm.config.Config.DEFAULT_LANGUAGE_DIALECTS;
import static org.snomed.snowstorm.core.data.domain.Concepts.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = TestConfig.class)
class ExpressionServiceTest extends AbstractTest {

	@Autowired
	private BranchService branchService;

	@Autowired
	private ConceptService conceptService;

	@Autowired
	private ExpressionService expressionService;

	private static final String EXPRESSION_TEST_BRANCH = "MAIN/EXPRESSION-TEST";
	private int relId = 1;

	private Concept root;
	private Concept isa;
	private Concept attribute1;
	private Concept attribute2;
	private Concept target1;
	private Concept target2;
	private ConcreteValue concreteValue1;
	private List<Concept> allKnownConcepts = new ArrayList<>();

	@BeforeEach
	void setup() throws ServiceException {
		branchService.create(EXPRESSION_TEST_BRANCH);
		root = createConcept(SNOMEDCT_ROOT, (Concept)null, PRIMITIVE);
		// ISA needs to exist to use in it's own definition!
		isa = new Concept(ISA);
		isa = createConcept(ISA, root, PRIMITIVE);
		attribute1 = createConcept("1000090", root, PRIMITIVE);
		attribute2 = createConcept("1000091", root, PRIMITIVE);
		target1 = createConcept("1000092", root, FULLY_DEFINED);
		target2 = createConcept("1000093", root, PRIMITIVE);
		concreteValue1 = ConcreteValue.from("#500", "dec");
	}

	@Test
	void testConceptAuthoringFormSimple() throws ServiceException {

		Concept concept1 = createConcept("100001", root, PRIMITIVE);
		Concept concept2 = createConcept("100002", concept1, PRIMITIVE);
		Concept concept3 = createConcept("100003", concept2, FULLY_DEFINED);
		Concept concept4 = createConcept("100004", concept3, FULLY_DEFINED);
		concept4.addRelationship(createRelationship(attribute1, target1, 0));
		Concept concept5 = createConcept("100005", concept4, FULLY_DEFINED);
		concept5.addRelationship(createRelationship(attribute1, concreteValue1, 0));
		
		conceptService.createUpdate(allKnownConcepts, EXPRESSION_TEST_BRANCH);
		
		Expression exp = expressionService.getConceptAuthoringForm(concept4.getConceptId(), DEFAULT_LANGUAGE_DIALECTS, EXPRESSION_TEST_BRANCH);
		Expression expCD = expressionService.getConceptAuthoringForm(concept5.getConceptId(), DEFAULT_LANGUAGE_DIALECTS, EXPRESSION_TEST_BRANCH);
		
		// Expecting one attribute (fully defined), and a single focus concept of concept 2
		assertEquals(1, exp.getAttributes().size());
		assertTrue(!exp.getAttributes().get(0).getValue().isConcrete());
		
		assertEquals(1, exp.getConcepts().size());
		assertEquals(new ConceptMicro(concept2), exp.getConcepts().get(0));
		
		//Concrete example also has 1 attribute (which is concrete) and Concept4 as a focus
		assertEquals(1, expCD.getAttributes().size());
		assertTrue(expCD.getAttributes().get(0).getValue().isConcrete());
		
		assertEquals(1, expCD.getConcepts().size());
		//Concept 5 has the sufficiently defined concept4 as an immediate parent, however
		//in the authoring form we expect to see the proximal primitive parent instead, which is concept2
		assertEquals(new ConceptMicro(concept2), expCD.getConcepts().get(0));
	}

	@Test
	void testConceptAuthoringFormComplex() throws ServiceException {
		// Complex case checks correct calculation of proximal primitive parent(s)
		// First parent's parent is actually also parent of 2nd parent, so should be discounted.
		//                    Root
		//                    /   \
		//                  P       P
		//                  | \     |
		//                  F  P    F
		//                  \  |   /
		//                  Concept6
		//
		// For manual testing, a worst case scenario is suggested to be: 719378009 |Microcephalus with brachydactyly and kyphoscoliosis syndrome (disorder)|
		
		Concept concept1 = createConcept("1000021", root, PRIMITIVE);
		Concept concept2 = createConcept("1000022", root, PRIMITIVE);
		Concept concept3 = createConcept("1000023", concept1, FULLY_DEFINED);
		Concept concept4 = createConcept("1000024", concept1, PRIMITIVE);
		Concept concept5 = createConcept("1000025", concept2, FULLY_DEFINED);
		
		Concept[] parents = new Concept[] { concept3, concept4, concept5};
		Concept concept6 = createConcept("1000026", parents, FULLY_DEFINED);
		conceptService.createUpdate(allKnownConcepts, EXPRESSION_TEST_BRANCH);

		Expression exp = expressionService.getConceptAuthoringForm(concept6.getConceptId(), DEFAULT_LANGUAGE_DIALECTS, EXPRESSION_TEST_BRANCH);
		
		// Expecting concepts 2 and 4 to be identified as PPPs
		assertEquals(0, exp.getAttributes().size()); // zero attributes
		assertEquals(2, exp.getConcepts().size()); // and a single focus concept
		boolean concept2Found = false;
		boolean concept4Found = false;
		for (ConceptMicro c :  exp.getConcepts()) {
			assertTrue(c.isPrimitive());
			if (c.equals(new ConceptMicro(concept2))) {
				concept2Found = true;
			} else if (c.equals(new ConceptMicro(concept4))) {
				concept4Found = true;
			}
		}
		assertTrue (concept2Found && concept4Found);
	}

	@Test
	void testConceptAuthoringFormSimpleWithExcludedGciConcept() throws ServiceException {

		Concept concept1 = createConcept("100001", root, PRIMITIVE);
		Concept concept2 = createConcept("100002", concept1, PRIMITIVE);
		Concept concept3 = createConcept("100003", concept2, PRIMITIVE);

		// Populate GCI axiom for concept3
		Axiom gciAxiom = new Axiom();
		Set <Relationship> relationships = new HashSet <>();
		Relationship relationship = new Relationship();
		relationship.setType(new ConceptMini(isa, DEFAULT_LANGUAGE_DIALECTS));
		relationship.setTarget(new ConceptMini(concept2, DEFAULT_LANGUAGE_DIALECTS));

		Relationship relationship2 = new Relationship();
		relationship2.setType(new ConceptMini(attribute1, DEFAULT_LANGUAGE_DIALECTS));
		relationship2.setTarget(new ConceptMini(concept2, DEFAULT_LANGUAGE_DIALECTS));

		relationships.add(relationship);
		relationships.add(relationship2);
		gciAxiom.setRelationships(relationships);

		concept3.addGeneralConceptInclusionAxiom(gciAxiom);

		Concept concept4 = createConcept("100004", concept3, FULLY_DEFINED);
		concept4.addRelationship(createRelationship(attribute1, target1, 0));
		conceptService.createUpdate(allKnownConcepts, EXPRESSION_TEST_BRANCH);

		Expression exp = expressionService.getConceptAuthoringForm(concept4.getConceptId(), DEFAULT_LANGUAGE_DIALECTS, EXPRESSION_TEST_BRANCH);

		// Expecting one attribute (fully defined), and a single focus concept of concept 2
		assertEquals(1, exp.getAttributes().size());
		assertTrue(!exp.getAttributes().get(0).getValue().isConcrete());

		assertEquals(1, exp.getConcepts().size());
		assertEquals(new ConceptMicro(concept2), exp.getConcepts().get(0));
	}

	@Test
	void testConceptAuthoringFormAttributeGroups() throws ServiceException {
		Concept concept1 = createConcept("1000031", root, PRIMITIVE);
		Concept concept2 = createConcept("1000032", concept1, PRIMITIVE);
		Concept concept3 = createConcept("1000033", concept2, FULLY_DEFINED);
		Concept concept4 = createConcept("1000034", concept3, FULLY_DEFINED);
		concept4.addRelationship(createRelationship(attribute1, target1, 1));
		concept4.addRelationship(createRelationship(attribute2, target2, 1));
		conceptService.createUpdate(allKnownConcepts, EXPRESSION_TEST_BRANCH);

		Expression exp = expressionService.getConceptAuthoringForm(concept4.getConceptId(), DEFAULT_LANGUAGE_DIALECTS, EXPRESSION_TEST_BRANCH);
		
		// Expecting: 
		assertEquals(0, exp.getAttributes().size()); //zero attributes
		assertEquals(1, exp.getGroups().size()); //one group
		assertEquals(2, exp.getGroups().iterator().next().getAttributes().size()); //...containing two attributes
		assertEquals(1, exp.getConcepts().size()); //and a single focus concept
		assertEquals(new ConceptMicro(concept2), exp.getConcepts().get(0)); //... of concept 2
		assertTrue(exp.getConcepts().get(0).isPrimitive()); //... which is primitive
	}

	private String nextRel() {
		Integer nextRel = relId++;
		return nextRel.toString();
	}

	private Concept createConcept(String sctId, Concept parent, String definitionStatusSctId) throws ServiceException {
		return createConcept(sctId, new Concept[] {parent}, definitionStatusSctId);
	}
	
	private Concept createConcept(String sctId, Concept[] parents, String definitionStatusSctId) throws ServiceException {
		Concept concept = new Concept(sctId)
				.setDefinitionStatusId(definitionStatusSctId)
				.addDescription(fsn("concept" + sctId));
		for (Concept parent : parents) {
			if (parent != null) {
				concept.addRelationship(createRelationship(isa, parent, 0));
			}
		}
		conceptService.create(concept, EXPRESSION_TEST_BRANCH);
		allKnownConcepts.add(concept);
		return concept;
	}

	private Relationship createRelationship(Concept type, Concept target, int groupId) {
		return new Relationship(nextRel(), type.getConceptId(),target.getConceptId())
				.setCharacteristicTypeId(Concepts.INFERRED_RELATIONSHIP)
				.setGroupId(groupId);
	}
	
	private Relationship createRelationship(Concept type, ConcreteValue value, int groupId) {
		return new Relationship(nextRel(), type.getConceptId(), value)
				.setCharacteristicTypeId(Concepts.INFERRED_RELATIONSHIP)
				.setGroupId(groupId);
	}

	private Description fsn(String term) {
		Description description = new Description(term);
		description.setTypeId(FSN);
		return description;
	}

}
