package org.snomed.snowstorm.mrcm;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.snomed.snowstorm.util.ExpressionUtil;

import static junit.framework.TestCase.assertEquals;

@RunWith(JUnit4.class)
public class ExpressionUtilTest {

	@Test(expected = IllegalStateException.class)
	public void testInvalidEcl() {
		String invalidRule = "<< 71388002 |Procedure (procedure)|: [0..*] { [0..*] 405815000 |Procedure device| = << 49062001 |Device (physical object)| }" +
				", << 363787002 |Observable entity (observable entity)|: [0..*] { [0..1] 405815000 |Procedure device| = << 49062001 |Device (physical object)| }";
		ExpressionUtil.sortExpressionConstraintByConceptId(invalidRule);
	}

	@Test
	public void testConstraintWithWildcard() {
		String published = "<< 413350009 |Finding with explicit context (situation)|: [0..0] 408730004 |Procedure context| = *, [0..0] 363589002 |Associated procedure| = *," +
				" [0..*] { [0..1] 246090004 |Associated finding| = " +
				"(<< 404684003 |Clinical finding (finding)| OR << 272379006 |Event (event)| OR << 363787002 |Observable entity (observable entity)| OR << 416698001 |Link assertion (link assertion)|) }";

		String sorted = "<< 413350009 |Finding with explicit context (situation)|: [0..0] 408730004 |Procedure context| = *, [0..0] 363589002 |Associated procedure| = *," +
				" [0..*] { [0..1] 246090004 |Associated finding| = " +
				"(<< 272379006 |Event (event)| OR << 363787002 |Observable entity (observable entity)| OR << 404684003 |Clinical finding (finding)| OR << 416698001 |Link assertion (link assertion)|) }";

		assertEquals(sorted, ExpressionUtil.sortExpressionConstraintByConceptId(published));
	}

	@Test
	public void testRefinedExpressionConstraint() {
		String refined = "(<< 386053000 |Evaluation procedure (procedure)| OR << 363787002 |Observable entity (observable entity)|): [0..1] { [0..1] 370130000 |Property| = << 118598001 |Property of measurement (qualifier value)| }";
		String expected = "(<< 363787002 |Observable entity (observable entity)| OR << 386053000 |Evaluation procedure (procedure)|): [0..1] { [0..1] 370130000 |Property| = << 118598001 |Property of measurement (qualifier value)| }";
		assertEquals(expected, ExpressionUtil.sortExpressionConstraintByConceptId(refined));
	}

	@Test
	public void testRefinedConstraintWithDisjunctions() {
		String published = "<< 363787002 |Observable entity (observable entity)|: [0..*] { [0..1] 704327008 |Direct site| = (<< 123037004 |Body structure (body structure)| OR << 410607006 |Organism (organism)| OR << 105590001 |Substance (substance)| OR << 123038009 |Specimen (specimen)| OR << 260787004 |Physical object (physical object)| OR << 373873005 |Pharmaceutical / biologic product (product)| OR << 419891008 |Record artifact (record artifact)|) }";
		String expected = "<< 363787002 |Observable entity (observable entity)|: [0..*] { [0..1] 704327008 |Direct site| = (<< 105590001 |Substance (substance)| OR << 123037004 |Body structure (body structure)| OR << 123038009 |Specimen (specimen)| OR << 260787004 |Physical object (physical object)| OR << 373873005 |Pharmaceutical / biologic product (product)| OR << 410607006 |Organism (organism)| OR << 419891008 |Record artifact (record artifact)|) }";
		assertEquals(expected, ExpressionUtil.sortExpressionConstraintByConceptId(published));
	}

	@Test
	public void testCompoundConstraint() {
		String published = "(<< 71388002 |Procedure (procedure)|: [0..*] { [0..*] 405815000 |Procedure device| = << 49062001 |Device (physical object)|}) " +
				"OR (<< 363787002 |Observable entity (observable entity)|: [0..*] 405815000 |Procedure device| = << 49062001 |Device (physical object)|)";

		String sorted = "(<< 363787002 |Observable entity (observable entity)|: [0..*] 405815000 |Procedure device| = << 49062001 |Device (physical object)|) " +
				"OR (<< 71388002 |Procedure (procedure)|: [0..*] { [0..*] 405815000 |Procedure device| = << 49062001 |Device (physical object)| })";
		assertEquals(sorted, ExpressionUtil.sortExpressionConstraintByConceptId(published));
	}
}
