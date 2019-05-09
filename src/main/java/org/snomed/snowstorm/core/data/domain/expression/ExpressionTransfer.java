package org.snomed.snowstorm.core.data.domain.expression;

public class ExpressionTransfer {

	private String expression;
	
	ExpressionTransfer (String expression) {
		this.expression = expression;
	}
	
	public String getExpression() {
		return expression;
	}

	public static ExpressionTransfer transfer(Expression expression, boolean includeTerms) {
		return new ExpressionTransfer(expression.toString(includeTerms));
	}
}
