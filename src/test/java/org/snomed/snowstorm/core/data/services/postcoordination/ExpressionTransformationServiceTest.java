package org.snomed.snowstorm.core.data.services.postcoordination;

import io.kaicode.elasticvc.api.VersionControlHelper;
import org.ihtsdo.otf.snomedboot.ReleaseImportException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.snomed.languages.scg.domain.model.Expression;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.snomed.snowstorm.core.data.services.postcoordination.model.ComparableExpression;
import org.snomed.snowstorm.core.util.TimerUtil;
import org.snomed.snowstorm.mrcm.MRCMService;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExpressionTransformationServiceTest extends AbstractExpressionTest {

	@Autowired
	private ExpressionTransformationService transformationService;

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
		ComparableExpression actualExpression = transformationService.transform(inputExpression, expressionContext);
		assertExpressionsEqual(expectedExpression, actualExpression);
	}

	private void assertExpressionsEqual(Expression expectedExpression, Expression actualExpression) {
		assertEquals(expectedExpression.toString(), actualExpression.toString());
	}

}
