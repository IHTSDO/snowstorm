package org.snomed.snowstorm.core.data.services.postcoordination;

import org.snomed.languages.scg.SCGException;
import org.snomed.languages.scg.SCGExpressionParser;
import org.snomed.languages.scg.SCGObjectFactory;
import org.snomed.languages.scg.domain.model.Expression;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.snomed.snowstorm.core.data.services.postcoordination.model.ComparableExpression;
import org.springframework.stereotype.Service;

import static java.lang.String.format;

@Service
public class ExpressionParser {

	private final SCGExpressionParser expressionParser;

	public ExpressionParser() {
		expressionParser = new SCGExpressionParser(new SCGObjectFactory());
	}

	public ComparableExpression parseExpression(String expressionString) throws ServiceException {
		try {
			Expression expression = expressionParser.parseExpression(expressionString);
			return new ComparableExpression(expression);
		} catch (SCGException e) {
			throw new ServiceException(format("Failed to parse expression \"%s\" due to: %s", expressionString, e.getMessage()), e);
		}
	}

}
