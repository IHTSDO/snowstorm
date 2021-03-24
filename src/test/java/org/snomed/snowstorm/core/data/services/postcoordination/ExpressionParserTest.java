package org.snomed.snowstorm.core.data.services.postcoordination;

import org.junit.jupiter.api.Test;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.snomed.snowstorm.core.data.services.postcoordination.model.ComparableExpression;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;

class ExpressionParserTest extends AbstractTest {

	@Autowired
	private ExpressionParser expressionParser;

	@Test
	public void testParseSimpleExpression() throws ServiceException {
		ComparableExpression comparableExpression = expressionParser.parseExpression("83152002 |Oophorectomy| : { 405815000 |Procedure device|  =  122456005 |Laser device| }");
		assertEquals("83152002 : { 405815000 = 122456005 }", comparableExpression.toString());
	}

	@Test
	public void testParseSimpleExpressionSorting() throws ServiceException {
		ComparableExpression comparableExpression = expressionParser.parseExpression("=== 64887002 |Operation on ovary (procedure)| + \n" +
				"    450669005 |Excision of adnexa of uterus (procedure)| + \n" +
				"    107983004 |Endocrine system excision (procedure)| :\n" +
				"            { " +
				"				405813007 |Procedure site - Direct (attribute)| = 15497006 |Ovarian structure (body structure)|," +
				"				260686004 |Method (attribute)| = 129304002 |Excision - action (qualifier value)|" +
				"}");
		assertEquals("=== 107983004 + 450669005 + 64887002 : { 260686004 = 129304002, 405813007 = 15497006 }", comparableExpression.toString());
	}

}
