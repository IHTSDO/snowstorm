package org.snomed.snowstorm.ecl.domain.filter;

import org.snomed.langauges.ecl.domain.filter.MemberFieldFilter;
import org.snomed.snowstorm.ecl.domain.expressionconstraint.SSubExpressionConstraint;

public class SMemberFieldFilter extends MemberFieldFilter {

	@SuppressWarnings("unused")
	protected SMemberFieldFilter() {
		// For JSON
	}

	public SMemberFieldFilter(String fieldName) {
		super(fieldName);
	}

	public void toString(StringBuffer buffer) {
		buffer.append(" ").append(getFieldName()).append(" ");

		if (getExpressionComparisonOperator() != null) {
			buffer.append(getExpressionComparisonOperator()).append(" ").append(((SSubExpressionConstraint)getSubExpressionConstraint()).toEclString());
		} else if (getNumericComparisonOperator() != null) {
			buffer.append(getNumericComparisonOperator()).append(" #").append(getNumericValue());
		} else if (getStringComparisonOperator() != null) {
			buffer.append(getStringComparisonOperator());
			ECLToStringUtil.toStringTypedSearchTerms(buffer, getSearchTerms());
		} else if (getBooleanComparisonOperator() != null) {
			buffer.append(getBooleanComparisonOperator()).append(" ").append(getBooleanValue());
		} else if (getTimeComparisonOperator() != null) {
			buffer.append(getTimeComparisonOperator());
			ECLToStringUtil.toString(buffer, getTimeValues());
		}
	}
}
