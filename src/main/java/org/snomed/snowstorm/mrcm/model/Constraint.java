package org.snomed.snowstorm.mrcm.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.snomed.langauges.ecl.domain.refinement.Operator;

public record Constraint(String expression, String conceptId, Operator operator) {


	@Override
	@JsonIgnore
	public String conceptId() {
		return conceptId;
	}

	@Override
	@JsonIgnore
	public Operator operator() {
		return operator;
	}
}
