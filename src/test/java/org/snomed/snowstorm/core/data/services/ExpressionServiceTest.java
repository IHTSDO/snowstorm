package org.snomed.snowstorm.core.data.services;

import io.kaicode.elasticvc.api.BranchService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.TestConfig;
import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.core.data.domain.ConceptMicro;
import org.snomed.snowstorm.core.data.domain.Concepts;
import org.snomed.snowstorm.core.data.domain.Description;
import org.snomed.snowstorm.core.data.domain.Relationship;
import org.snomed.snowstorm.core.data.domain.expression.Expression;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.ArrayList;
import java.util.List;

import static org.snomed.snowstorm.core.data.domain.Concepts.*;
import static org.junit.Assert.*;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = TestConfig.class)
public class ExpressionServiceTest extends AbstractTest {

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
	private List<Concept> allKnownConcepts = new ArrayList<>();

	@Before
	public void setup() throws ServiceException {
		branchService.create("MAIN");
		branchService.create(EXPRESSION_TEST_BRANCH);
		root = createConcept(SNOMEDCT_ROOT, (Concept)null, PRIMITIVE);
		//ISA needs to exist to use in it's own definition!
		isa = new Concept(ISA);
		isa = createConcept(ISA, root, PRIMITIVE);
		attribute1 = createConcept("90", root, PRIMITIVE);
		attribute2 = createConcept("91", root, PRIMITIVE);
		target1 = createConcept("92", root, FULLY_DEFINED);
		target2 = createConcept("93", root, PRIMITIVE);
	}

	@Test
	public void testConceptAuthoringFormSimple() throws ServiceException {

		Concept concept1 = createConcept("1", root, PRIMITIVE);
		Concept concept2 = createConcept("2", concept1, PRIMITIVE);
		Concept concept3 = createConcept("3", concept2, FULLY_DEFINED);
		Concept concept4 = createConcept("4", concept3, FULLY_DEFINED);
		concept4.addRelationship(createRelationship(attribute1, target1, 0));
		conceptService.createUpdate(allKnownConcepts, EXPRESSION_TEST_BRANCH);

		Expression exp = expressionService.getConceptAuthoringForm(concept4.getConceptId(), EXPRESSION_TEST_BRANCH);
		
		// Expecting one attributes (fully defined), and a single focus concept of concept 2
		assertEquals(1, exp.getAttributes().size());
		assertTrue(!exp.getAttributes().get(0).getValue().isPrimitive());
		assertEquals(1, exp.getConcepts().size());
		assertEquals(new ConceptMicro(concept2), exp.getConcepts().get(0));
	}

	@Test
	public void testConceptAuthoringFormComplex() throws ServiceException {
		//Complex case checks correct calculation of proximal primitive parent(s)
		//First parent's parent is actually also parent of 2nd parent, so should be discounted.
		//                    Root
		//                    /   \
		//                  P       P
		//                  | \     |
		//                  F  P    F
		//                  \  |   /
		//                  Concept6
		//
		// For manual testing, a worst case scenario is suggested to be: 719378009 |Microcephalus with brachydactyly and kyphoscoliosis syndrome (disorder)|
		
		Concept concept1 = createConcept("21", root, PRIMITIVE);
		Concept concept2 = createConcept("22", root, PRIMITIVE);
		Concept concept3 = createConcept("23", concept1, FULLY_DEFINED);
		Concept concept4 = createConcept("24", concept1, PRIMITIVE);
		Concept concept5 = createConcept("25", concept2, FULLY_DEFINED);
		
		Concept[] parents = new Concept[] { concept3, concept4, concept5};
		Concept concept6 = createConcept("26", parents, FULLY_DEFINED);
		conceptService.createUpdate(allKnownConcepts, EXPRESSION_TEST_BRANCH);

		Expression exp = expressionService.getConceptAuthoringForm(concept6.getConceptId(), EXPRESSION_TEST_BRANCH);
		
		// Expecting concepts 2 and 4 to be identified as PPPs
		assertEquals(0, exp.getAttributes().size()); //zero attributes
		assertEquals(2, exp.getConcepts().size()); //and a single focus concept
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
	public void testConceptAuthoringFormAttributeGroups() throws ServiceException {
		Concept concept1 = createConcept("31", root, PRIMITIVE);
		Concept concept2 = createConcept("32", concept1, PRIMITIVE);
		Concept concept3 = createConcept("33", concept2, FULLY_DEFINED);
		Concept concept4 = createConcept("34", concept3, FULLY_DEFINED);
		concept4.addRelationship(createRelationship(attribute1, target1, 1));
		concept4.addRelationship(createRelationship(attribute2, target2, 1));
		conceptService.createUpdate(allKnownConcepts, EXPRESSION_TEST_BRANCH);

		Expression exp = expressionService.getConceptAuthoringForm(concept4.getConceptId(), EXPRESSION_TEST_BRANCH);
		
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
		return createConcept (sctId, new Concept[] {parent}, definitionStatusSctId);
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

	private Description fsn(String term) {
		Description description = new Description(term);
		description.setTypeId(FSN);
		return description;
	}

}
