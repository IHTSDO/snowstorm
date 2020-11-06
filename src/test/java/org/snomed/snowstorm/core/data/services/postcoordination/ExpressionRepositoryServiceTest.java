package org.snomed.snowstorm.core.data.services.postcoordination;

import org.junit.jupiter.api.Test;
import org.snomed.snowstorm.AbstractTest;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

class ExpressionRepositoryServiceTest extends AbstractTest {

	@Autowired
	private ExpressionRepositoryService expressionRepository;

	@Test
	public void createExpression() {
		// Single concept
		assertEquals("=== 83152002",
				expressionRepository.createExpression("83152002 |Oophorectomy|").getCloseToUserForm());

		// Single concept with explicit definition status
		assertEquals("=== 83152002",
				expressionRepository.createExpression("===83152002 |Oophorectomy|").getCloseToUserForm());

		// Single concept with explicit subtype definition status
		assertEquals("<<< 83152002",
				expressionRepository.createExpression("<<<  83152002 |Oophorectomy|").getCloseToUserForm());

		// Multiple focus concepts
		assertEquals("=== 421720008 + 7946007",
				expressionRepository.createExpression("421720008 |Spray dose form| + 7946007 |Drug suspension|").getCloseToUserForm());
		// Same concepts stated in reverse order to test concept sorting
		assertEquals("=== 421720008 + 7946007",
				expressionRepository.createExpression("7946007 |Drug suspension| + 421720008 |Spray dose form|").getCloseToUserForm());


		// With single refinement
		assertEquals("=== 83152002 : 405815000 = 122456005",
				expressionRepository.createExpression("    83152002 |Oophorectomy| :  405815000 |Procedure device|  =  122456005 |Laser device|").getCloseToUserForm());

		// With multiple refinements, attributes are sorted
		assertEquals("=== 71388002 : 260686004 = 129304002, 405813007 = 15497006, 405815000 = 122456005",
				expressionRepository.createExpression("   71388002 |Procedure| :" +
						"       405815000 |Procedure device|  =  122456005 |Laser device| ," +
						"       260686004 |Method|  =  129304002 |Excision - action| ," +
						"       405813007 |Procedure site - direct|  =  15497006 |Ovarian structure|").getCloseToUserForm());
	}

	@Test
	public void handleExpressionWithBadSyntax() {
		// Missing colon
		assertIllegalArgumentError("373873005 |Pharmaceutical / biologic product|" +
				"\t\t411116001 |Has dose form|  = 421720008 |Spray dose form|");

		// Missing equals
		assertIllegalArgumentError("373873005 |Pharmaceutical / biologic product| :" +
				"\t\t411116001 |Has dose form| 421720008 |Spray dose form|");

		// Double equals
		assertIllegalArgumentError("373873005 |Pharmaceutical / biologic product| :" +
				"\t\t411116001 |Has dose form| == 421720008 |Spray dose form|");
	}

	private void assertIllegalArgumentError(String closeToUserForm) {
		try {
			expressionRepository.createExpression(closeToUserForm);
			fail();
		} catch (IllegalArgumentException e) {
			// Good
		}
	}
}
