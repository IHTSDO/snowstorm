package org.snomed.snowstorm.core.data.services.postcoordination;

import org.junit.jupiter.api.Test;
import org.snomed.languages.scg.domain.model.Expression;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.snomed.snowstorm.core.data.services.postcoordination.model.ComparableExpression;
import org.springframework.beans.factory.annotation.Autowired;


import static org.junit.jupiter.api.Assertions.assertEquals;

class ExpressionTransformationServiceGenericLevelXTest extends AbstractExpressionTest {

	@Autowired
	private ExpressionTransformationServiceGenericLevelX transformationService;

	@Autowired
	private ExpressionParser expressionParser;

	@Test
	public void testTransformUserFormWithoutBraces() throws ServiceException {
        assertExpressionTransformation(
				// Input
				"83152002 |Oophorectomy| : 405815000 |Procedure device|  =  122456005 |Laser device|",

				// Concept has one group

				// Expected output
				"=== 83152002 |Oophorectomy| : " +
				"	{" +
				"		260686004 |Method| = 129304002 |Excision - action|," +
				"		405813007 |Procedure site - Direct| = 15497006 |Ovarian structure|," +
				"		405815000 |Procedure device|  =  122456005 |Laser device|" +
				"	}"
		);
	}

	@Test
	public void testTransformUserFormWithBraces() throws ServiceException {
		assertExpressionTransformation(
				// Input
				"83152002 |Oophorectomy| : { 405815000 |Procedure device|  =  122456005 |Laser device| }",

				// Expected output
				"=== 83152002 |Oophorectomy| : " +
				"	{" +
				"		405815000 |Procedure device|  =  122456005 |Laser device|" +
				"	}"
		);
	}

	private void assertExpressionTransformation(String input, String expected) throws ServiceException {
		ComparableExpression expectedExpression = expressionParser.parseExpression(expected);
		ComparableExpression inputExpression = expressionParser.parseExpression(input);
		ComparableExpression actualExpression = transformationService.validateAndTransform(inputExpression, expressionContext);
		assertExpressionsEqual(expectedExpression, actualExpression);
	}

	private void assertExpressionsEqual(Expression expectedExpression, Expression actualExpression) {
		assertEquals(expectedExpression.toString(), actualExpression.toString());
	}

}
