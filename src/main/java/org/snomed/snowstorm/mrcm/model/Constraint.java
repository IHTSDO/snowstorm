package org.snomed.snowstorm.mrcm.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.snomed.langauges.ecl.domain.refinement.Operator;

public class Constraint {

	private final String expression;
	private final String conceptId;
	private final Operator operator;

	public Constraint(String expression, String conceptId, Operator operator) {
		this.expression = expression;
		this.conceptId = conceptId;
		this.operator = operator;
	}

	public String getExpression() {
		return expression;
	}

	@JsonIgnore
	public String getConceptId() {
		return conceptId;
	}

	@JsonIgnore
	public Operator getOperator() {
		return operator;
	}
}
