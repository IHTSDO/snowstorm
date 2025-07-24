package org.snomed.snowstorm.ecl.domain.filter;

import org.snomed.langauges.ecl.domain.filter.DescriptionIdFilter;

import java.util.Set;

public class SDescriptionIdFilter extends DescriptionIdFilter {

	@SuppressWarnings("unused")
	private SDescriptionIdFilter() {
		// For JSON
	}

	public SDescriptionIdFilter(String text) {
		super(text);
	}

	public void toString(StringBuffer buffer) {
		buffer.append(" id ").append(getBooleanComparisonOperator()).append(" ");
		Set<String> descriptionIds = getDescriptionIds();
		if (descriptionIds.size() > 1) {
			buffer.append("(");
		}
		int i = 0;
		for (String id : descriptionIds) {
			if (i++ > 0) {
				buffer.append(" ");
			}
			buffer.append(id);
		}
		if (descriptionIds.size() > 1) {
			buffer.append(")");
		}
	}
}
