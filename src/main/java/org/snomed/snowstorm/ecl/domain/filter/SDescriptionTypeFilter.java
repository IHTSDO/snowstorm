package org.snomed.snowstorm.ecl.domain.filter;

import org.snomed.langauges.ecl.domain.filter.DescriptionType;
import org.snomed.langauges.ecl.domain.filter.DescriptionTypeFilter;
import org.snomed.snowstorm.ecl.domain.expressionconstraint.SSubExpressionConstraint;

import java.util.List;
import java.util.Locale;

public class SDescriptionTypeFilter extends DescriptionTypeFilter {

	@SuppressWarnings("unused")
	private SDescriptionTypeFilter() {
		// For JSON
	}

	public SDescriptionTypeFilter(String text) {
		super(text);
	}

	public void toString(StringBuffer buffer) {
		buffer.append(" type ").append(getBooleanComparisonOperator()).append(" ");
		if (getSubExpressionConstraint() != null) {
			((SSubExpressionConstraint)getSubExpressionConstraint()).toString(buffer);
		} else {
			List<DescriptionType> types = getTypes();
			if (types.size() > 1) {
				buffer.append("(");
			}
			int i = 0;
			for (DescriptionType type : types) {
				if (i++ > 0) {
					buffer.append(" ");
				}
				buffer.append(type.name().toLowerCase(Locale.ROOT));
			}
			if (types.size() > 1) {
				buffer.append(")");
			}
		}
	}
}
