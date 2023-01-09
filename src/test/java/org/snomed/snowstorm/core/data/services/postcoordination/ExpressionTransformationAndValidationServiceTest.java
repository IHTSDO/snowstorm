package org.snomed.snowstorm.core.data.services.postcoordination;

import org.junit.jupiter.api.Test;
import org.snomed.languages.scg.domain.model.Expression;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.snomed.snowstorm.core.data.services.postcoordination.model.ComparableExpression;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExpressionTransformationAndValidationServiceTest extends AbstractExpressionTest {

	@Autowired
	private ExpressionTransformationAndValidationService transformationService;

	@Autowired
	private ExpressionParser expressionParser;

	@Test
	public void testLeve1() throws ServiceException {
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

	@Test
	public void testLevel2SelfGrouped() throws ServiceException {
        assertExpressionTransformation(
				// Input
				"195967001 |Asthma| : 42752001 |Due to|  =  55985003 |Atopic reaction|",

				// Expected output
				"=== 195967001 |Asthma| : " +
				"	{" +
				"		42752001 |Due to| =  55985003 |Atopic reaction|" +
				"	}"
		);
	}

	@Test
	public void testLevel2AddSeverityToClinicalFinding() throws ServiceException {
		// NB these examples don't make sense medically, they are just here to test the transformations
		assertExpressionTransformation(
				// Input
				"195967001 |Asthma| : 246112005 |Severity|  =  24484000 |Severe|",

				// Expected output
				"=== 195967001 |Asthma| : " +
						"	{" +
						"		246112005 |Severity| =  24484000 |Severe|" +
						"	}"
		);
	}

	@Test
	public void testLevel2AddContextToClinicalFinding() throws ServiceException {
		assertExpressionTransformation(
				// Input
				"254837009 |Breast cancer| :  408731000 |Temporal context|  =  410513005 |Past| ,\n" +
						"408732007 |Subject relationship context|  =  72705000 |Mother|",

				// Expected output
				"===  243796009 |Situation with explicit context| :\n" +
						"    {  246090004 |Associated finding|  =  254837009 |Breast cancer| , \n" +
						"     408731000 |Temporal context|  =  410513005 |Past| ,     408732007 |Subject relationship context|  =  72705000 |Mother|  }"
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
