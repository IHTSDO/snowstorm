package org.snomed.snowstorm.ecl.domain.filter;

import org.snomed.langauges.ecl.domain.filter.HistorySupplement;
import org.snomed.snowstorm.ecl.domain.expressionconstraint.SExpressionConstraint;

public class SHistorySupplement extends HistorySupplement {
	public void toString(StringBuffer buffer) {
		buffer.append(" {{ + HISTORY");

		if (getHistorySubset() != null) {
			buffer.append(" (");
			buffer.append(((SExpressionConstraint)getHistorySubset()).toEclString());
			buffer.append(")");
		} else if (getHistoryProfile() != null) {
			buffer.append("-").append(getHistoryProfile());
		}

		buffer.append(" }}");
	}
}
