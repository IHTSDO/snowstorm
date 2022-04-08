package org.snomed.snowstorm.ecl.domain.filter;

import org.snomed.langauges.ecl.domain.filter.DialectAcceptability;
import org.snomed.langauges.ecl.domain.filter.DialectFilter;
import org.snomed.snowstorm.ecl.domain.expressionconstraint.SSubExpressionConstraint;

import java.util.List;

public class SDialectFilter extends DialectFilter {

	@SuppressWarnings("unused")
	private SDialectFilter() {
		// For JSON
	}

	public SDialectFilter(String text, boolean dialectAliasFilter) {
		super(text, dialectAliasFilter);
	}

	public void toString(StringBuffer buffer) {
		buffer.append(" ").append(isDialectAliasFilter() ? "dialect" : "dialectId").append(" ").append(getBooleanComparisonOperator())
				.append(" ");
		if (getSubExpressionConstraint() != null) {
			((SSubExpressionConstraint)getSubExpressionConstraint()).toString(buffer);
		} else {
			List<DialectAcceptability> acceptabilities = getDialectAcceptabilities();
			if (acceptabilities.size() > 1) {
				buffer.append("(");
			}
			int a = 0;
			for (DialectAcceptability acceptability : acceptabilities) {
				if (a++ > 0) {
					buffer.append(" ");
				}
				((SDialectAcceptability) acceptability).toString(buffer);
			}
			if (acceptabilities.size() > 1) {
				buffer.append(")");
			}
		}
	}
}
