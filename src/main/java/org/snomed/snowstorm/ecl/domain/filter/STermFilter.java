package org.snomed.snowstorm.ecl.domain.filter;

import org.snomed.langauges.ecl.domain.filter.TermFilter;

public class STermFilter extends TermFilter {

	@SuppressWarnings("unused")
	private STermFilter() {
		// For JSON
	}

	public STermFilter(String text) {
		super(text);
	}

	public void toString(StringBuffer buffer) {
		buffer.append(" term ").append(getBooleanComparisonOperator()).append(" ");
		ECLToStringUtil.toStringTypedSearchTerms(buffer, getTypedSearchTermSet());
	}
}
