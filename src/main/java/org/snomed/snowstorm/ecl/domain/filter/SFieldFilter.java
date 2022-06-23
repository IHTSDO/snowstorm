package org.snomed.snowstorm.ecl.domain.filter;

import org.snomed.langauges.ecl.domain.filter.FieldFilter;

public class SFieldFilter extends FieldFilter {

	public SFieldFilter() {
	}

	public SFieldFilter(String fieldName, boolean equals) {
		super(fieldName, equals);
	}

	public void toString(StringBuffer buffer) {
		buffer.append(" ").append(getField()).append(" ")
				.append(isEquals() ? "=" : "!=");
		ECLToStringUtil.toString(buffer, getConceptReferences());
		ECLToStringUtil.toString(buffer, getSubExpressionConstraint());
	}
}
