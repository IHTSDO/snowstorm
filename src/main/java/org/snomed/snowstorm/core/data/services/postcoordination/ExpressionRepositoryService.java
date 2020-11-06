package org.snomed.snowstorm.core.data.services.postcoordination;

import org.snomed.languages.scg.SCGException;
import org.snomed.languages.scg.SCGExpressionParser;
import org.snomed.languages.scg.SCGObjectFactory;
import org.snomed.languages.scg.domain.model.Expression;
import org.snomed.snowstorm.core.data.services.postcoordination.model.ComparableExpression;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class ExpressionRepositoryService {

	private final SCGExpressionParser expressionParser;
	private Set<PostCoordinatedExpression> expressions = new HashSet<>();

	public ExpressionRepositoryService() {
		expressionParser = new SCGExpressionParser(new SCGObjectFactory());
	}

	public Collection<PostCoordinatedExpression> findAll() {
		return expressions;
	}

	public PostCoordinatedExpression createExpression(String closeToUserForm) {
		try {
			Expression expression = expressionParser.parseExpression(closeToUserForm);
			// Sort contents of expression
			expression = new ComparableExpression(expression);
			PostCoordinatedExpression postCoordinatedExpression = new PostCoordinatedExpression(expression.toString());
			expressions.add(postCoordinatedExpression);
			return postCoordinatedExpression;
		} catch (SCGException e) {
			throw new IllegalArgumentException("Failed to parse expression: " + e.getMessage(), e);
		}
	}

}
