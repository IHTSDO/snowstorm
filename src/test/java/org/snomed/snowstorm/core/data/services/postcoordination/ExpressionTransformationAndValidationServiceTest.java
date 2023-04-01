package org.snomed.snowstorm.core.data.services.postcoordination;

import org.junit.jupiter.api.Test;
import org.snomed.languages.scg.domain.model.Expression;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.snomed.snowstorm.core.data.services.postcoordination.model.ComparableExpression;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Fail.fail;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ExpressionTransformationAndValidationServiceTest extends AbstractExpressionTest {

	@Autowired
	private ExpressionTransformationAndValidationService transformationService;

	@Autowired
	private ExpressionParser expressionParser;

	@Test
	public void testLevel0() throws ServiceException {
		assertExpressionTransformation(
				// Input
				"=== 83152002 |Oophorectomy| : { 405815000 |Procedure device|  =  122456005 |Laser device| }",

				// Expected output
				"=== 83152002 |Oophorectomy| : " +
						"	{" +
						"		405815000 |Procedure device|  =  122456005 |Laser device|" +
						"	}"
		);
	}

	@Test
	public void testLevel0MRCMAttributeDomainError() throws ServiceException {
		try {
			assertExpressionTransformation(
					// Input
					"=== 83152002 |Oophorectomy| : { " +
							"405815000 |Procedure device|  =  122456005 |Laser device|, " +
							"		246112005 |Severity| =  24484000 |Severe|" +
							"}",

					// Expected output
					"83152002"
			);
			fail("Should have thrown exception");
		} catch (ExpressionValidationException e) {
			assertEquals("Attribute Type 246112005 can not be used with the given focus concepts [83152002] " +
					"because the attribute can only be used in the following MRCM domains: [404684003 |Clinical finding|].", e.getMessage());
		}
	}

	@Test
	public void testLevel1SelfGrouped() throws ServiceException {
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
	public void testLevel1AddSeverityToClinicalFinding() throws ServiceException {
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
	public void testLevel1AddContextToClinicalFinding() throws ServiceException {
		assertExpressionTransformation(
				// Input
				"254837009 |Breast cancer| :  408731000 |Temporal context|  =  410513005 |Past| ,\n" +
						"408732007 |Subject relationship context|  =  72705000 |Mother|",

				// Expected output
				"===  413350009 |Finding with explicit context| :\n" +
						"  { 246090004 |Associated finding|  =  254837009 |Breast cancer| , \n" +
						"    408729009 |Finding context|  =  410515003 |Known present|, " +
						"    408731000 |Temporal context|  =  410513005 |Past| , " +
						"    408732007 |Subject relationship context|  =  72705000 |Mother|  }"
		);
	}

	@Test
	public void testLevel1AddLateralityToClinicalFinding() throws ServiceException {
		assertExpressionTransformation(
				// Input
				"449702005 |Cellulitis and abscess of lower limb| :  272741003 |Laterality| = 7771000 |Left|  ",

				// Expected output
				"=== 449702005 |Cellulitis and abscess of lower limb| : \n" +
						"{  363698007 |Finding site|  = ( 61685007 |Lower limb structure| :  272741003 |Laterality| = 7771000 |Left| ), \n" +
						"116676008 |Associated morphology|  =  385627004 |Cellulitis|  }\n" +
						"{  363698007 |Finding site|  = ( 61685007 |Lower limb structure| :  272741003 |Laterality| = 7771000 |Left| ), \n" +
						"116676008 |Associated morphology|  =  44132006 |Abscess|  }"
		);
	}

	@Test
	public void testLevel1AddLateralityAndContextToClinicalFinding() throws ServiceException {
		assertExpressionTransformation(
				// Input
				"449702005 |Cellulitis and abscess of lower limb| :  272741003 |Laterality| = 7771000 |Left|, 408732007 |Subject relationship context|  =  72705000 |Mother|",

				// Expected output
				"=== 413350009 : " +
						"{ 246090004 = " +
						"   ( 449702005 : " +
						"       { 116676008 = 385627004, " +
						"         363698007 = ( 61685007 : 272741003 = 7771000 ) " +
						"       }" +
						"       { 116676008 = 44132006, " +
						"         363698007 = ( 61685007 : 272741003 = 7771000 ) " +
						"       }" +
						"   ), " +
						"   408729009 |Finding context|  =  410515003 |Known present|," +
						"   408732007 |Subject relationship context|  =  72705000 |Mother|," +
						"   408731000 |Temporal context|  =  410512000 |Current or specified time| }"
		);
	}

	@Test
	public void testLevel1AddLateralityToProcedure() throws ServiceException {
		assertExpressionTransformation(
				// Input
				"14600001000004107 |Closure of wound of ankle with flap| :  272741003 |Laterality| = 7771000 |Left|",

				// Expected output
				"=== 14600001000004107 |Closure of wound of ankle with flap| :  \n" +
						"{ 260686004 |Method|  =  129357001 |Closure - action| ,\n" +
						"405813007 |Procedure site - Direct|  = ( 344001 |Ankle region structure| :  272741003 |Laterality| = 7771000 |Left| ),\n" +
						"363700003 |Direct morphology|  =  13924000 |Wound| ,\n" +
						"424361007 |Using substance|  =  256683004 |Flap| }"
		);
	}

	@Test
	public void testLevel1RefineExistingAttribute() throws ServiceException {
		assertExpressionTransformation(
				// Input
				"6471000179103 |Transplantation of kidney and pancreas|  :  405813007 |Procedure site - Direct|  =  9846003 |Right kidney structure|",

				// Expected output
				"=== 6471000179103 |Transplantation of kidney and pancreas|  :\n" +
						"{ 260686004 |Method|  =  410820007 |Surgical transplantation - action| , \n" +
						"405813007 |Procedure site - Direct|  =  9846003 |Right kidney structure| , \n" +
						"363701004 |Direct substance|  =  420852008 |Kidney graft - material|  }"
		);
	}

	private void assertExpressionTransformation(String input, String expected) throws ServiceException {
		ComparableExpression expectedExpression = expressionParser.parseExpression(expected);
		ComparableExpression inputExpression = expressionParser.parseExpression(input);
		ComparableExpression actualExpression = transformationService.validateAndTransform(inputExpression, expressionContext);
		assertExpressionsEqual(expectedExpression, actualExpression);
	}

	private void assertExpressionsEqual(Expression expectedExpression, Expression actualExpression) {
		assertEquals(expectedExpression.toString(), actualExpression.toString().replace("  ", " "));
	}

}
