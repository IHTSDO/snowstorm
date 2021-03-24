package org.snomed.snowstorm.core.data.services.postcoordination;

import org.junit.jupiter.api.Test;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.snomed.snowstorm.core.data.services.identifier.LocalRandomIdentifierSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import static org.junit.jupiter.api.Assertions.*;

class ExpressionRepositoryServiceTest extends AbstractExpressionTest {

	@Autowired
	private ExpressionRepositoryService expressionRepository;

	@Test
	public void createExpression() throws ServiceException {
		String branch = "MAIN";

		PostCoordinatedExpression expression = expressionRepository.createExpression(branch, "83152002 |Oophorectomy|", "");
		String expressionId = expression.getId();
		System.out.println("Expression ID is " + expressionId);
		assertEquals("06", LocalRandomIdentifierSource.POSTCOORDINATED_EXPRESSION_PARTITION_ID);
		assertEquals("06", expressionId.substring(expressionId.length() - 3, expressionId.length() - 1));

		// Single concept
		assertEquals("=== 83152002",
				expressionRepository.createExpression(branch, "83152002 |Oophorectomy|", "").getClassifiableForm());

		// Single concept with explicit definition status
		assertEquals("=== 83152002",
				expressionRepository.createExpression(branch, "===83152002 |Oophorectomy|", "").getClassifiableForm());

		// Single concept with explicit subtype definition status
		assertEquals("<<< 83152002",
				expressionRepository.createExpression(branch, "<<<  83152002 |Oophorectomy|", "").getClassifiableForm());

		// Multiple focus concepts
		assertEquals("=== 421720008 + 7946007",
				expressionRepository.createExpression(branch, "421720008 |Spray dose form| + 7946007 |Drug suspension|", "").getClassifiableForm());
		// Same concepts stated in reverse order to test concept sorting
		assertEquals("=== 421720008 + 7946007",
				expressionRepository.createExpression(branch, "7946007 |Drug suspension| + 421720008 |Spray dose form|", "").getClassifiableForm());


		// With single refinement
		assertEquals("=== 83152002 : { 260686004 = 129304002, 405813007 = 15497006, 405815000 = 122456005 }",
				expressionRepository.createExpression(branch, "83152002 |Oophorectomy| :  405815000 |Procedure device|  =  122456005 |Laser device|", "").getClassifiableForm());

		// With multiple refinements, attributes are sorted
		assertEquals("=== 71388002 : { 260686004 = 129304002, 405813007 = 15497006, 405815000 = 122456005 }",
				expressionRepository.createExpression(branch, "   71388002 |Procedure| :" +
						"       405815000 |Procedure device|  =  122456005 |Laser device| ," +
						"       260686004 |Method|  =  129304002 |Excision - action| ," +
						"       405813007 |Procedure site - direct|  =  15497006 |Ovarian structure|", "").getClassifiableForm());

		Page<PostCoordinatedExpression> page = expressionRepository.findAll(branch, PageRequest.of(0, 10));
		assertEquals(8, page.getTotalElements());

		Page<PostCoordinatedExpression> results = expressionRepository.findByCanonicalCloseToUserForm(branch, "83152002 |Oophorectomy| :  405815000 |Procedure device|  =  122456005 |Laser device|", PageRequest.of(0, 1));
		assertEquals(1, results.getTotalElements());
//		assertEquals("=== 83152002 : { 260686004 = 129304002, 405813007 = 15497006, 405815000 = 122456005 }", results.getContent().get(0).getClassifiableForm());
	}

	@Test
	public void handleExpressionWithBadSyntax() throws ServiceException {
		// Missing colon
		assertIllegalArgumentParsingError("373873005 |Pharmaceutical / biologic product|" +
				"\t\t411116001 |Has dose form|  = 421720008 |Spray dose form|");

		// Missing equals
		assertIllegalArgumentParsingError("373873005 |Pharmaceutical / biologic product| :" +
				"\t\t411116001 |Has dose form| 421720008 |Spray dose form|");

		// Double equals
		assertIllegalArgumentParsingError("373873005 |Pharmaceutical / biologic product| :" +
				"\t\t411116001 |Has dose form| == 421720008 |Spray dose form|");

		assertEquals(0, expressionRepository.findAll("MAIN", PageRequest.of(0, 5)).getTotalElements(), "No invalid expressions should have been saved.");
	}

	@Test
	public void attributeRangeMRCMValidation() throws ServiceException {
		String branch = "MAIN";

		// All in range as per data in setup
		expressionRepository.createExpression(branch, "   71388002 |Procedure| :" +
				"       405815000 |Procedure device| = 122456005 |Laser device| ," +
				"       260686004 |Method| = 129304002 |Excision - action| ," +
				"       405813007 |Procedure site - direct| = 15497006 |Ovarian structure|", "");

		try {
			expressionRepository.createExpression(branch, "   71388002 |Procedure| :" +
					"       405815000 |Procedure device| = 122456005 |Laser device| ," +
					"       260686004 |Method| = 129304002 |Excision - action| ," +
					"       405813007 |Procedure site - direct| = 388441000 |Horse|", "");
			fail("Should have thrown exception.");
		} catch (IllegalArgumentException e) {
			assertEquals("Value 388441000 | Horse | is not within the permitted range" +
							" of attribute 405813007 | Procedure site - direct (attribute) | - (<< 442083009 |Anatomical or acquired body structure (body structure)|).",
					e.getMessage());
		}
	}

	@Test
	public void attributeRangeMRCMValidationOfAttributeValueWithinExpression() throws ServiceException {
		String branch = "MAIN";

		// All in range as per data in setup
		expressionRepository.createExpression(branch, "71388002 |Procedure| :" +
				"       405815000 |Procedure device| = 122456005 |Laser device| ," +
				"       260686004 |Method| = 129304002 |Excision - action| ," +
				"       405813007 |Procedure site - direct| = ( 15497006 |Ovarian structure| : 272741003 |Laterality| = 24028007 |Right| )", "");

		try {
			expressionRepository.createExpression(branch, "71388002 |Procedure| :" +
					"       405815000 |Procedure device| = 122456005 |Laser device| ," +
					"       260686004 |Method| = 129304002 |Excision - action| ," +
					"       405813007 |Procedure site - direct| = ( 15497006 |Ovarian structure| : 272741003 |Laterality| = 388441000 |Horse| )", "");
			fail("Should have thrown exception.");
		} catch (IllegalArgumentException e) {
			assertEquals("Value 388441000 | Horse | is not within the permitted range" +
							" of attribute 272741003 | Laterality (attribute) | - (<< 182353008 |Side (qualifier value)|).",
					e.getMessage());
		}
	}

	@Test
	public void attributeRangeMRCMValidationOfAttributeWithExpressionValue() throws ServiceException {
		String branch = "MAIN";

		// All in range as per data in setup
		expressionRepository.createExpression(branch, "71388002 |Procedure| :" +
				"       405815000 |Procedure device| = 122456005 |Laser device| ," +
				"       260686004 |Method| = 129304002 |Excision - action| ," +
				"       405813007 |Procedure site - direct| = ( 15497006 |Ovarian structure| : 272741003 |Laterality| = 24028007 |Right| )", "");

		try {
			expressionRepository.createExpression(branch, "71388002 |Procedure| :" +
					"       405815000 |Procedure device| = 122456005 |Laser device| ," +
					"       260686004 |Method| = 129304002 |Excision - action| ," +
					"       405813007 |Procedure site - direct| = ( 388441000 |Horse| : 272741003 |Laterality| = 24028007 |Right| )", "");
			fail("Should have thrown exception.");
		} catch (IllegalArgumentException e) {
			assertEquals("Value 388441000 | Horse | is not within the permitted range" +
							" of attribute 405813007 | Procedure site - direct (attribute) | - (<< 442083009 |Anatomical or acquired body structure (body structure)|).",
					e.getMessage());
		}
	}

	private void assertIllegalArgumentParsingError(String closeToUserForm) {
		try {
			expressionRepository.createExpression("MAIN", closeToUserForm, "");
			fail();
		} catch (ServiceException e) {
			// Good
			assertTrue(e.getMessage().startsWith("Failed to parse expression"), "Message: " + e.getMessage());
		}
	}
}
