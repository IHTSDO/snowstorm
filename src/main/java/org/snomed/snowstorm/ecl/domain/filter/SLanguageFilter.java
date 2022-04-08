package org.snomed.snowstorm.ecl.domain.filter;

import org.snomed.langauges.ecl.domain.filter.LanguageFilter;

import java.util.List;

public class SLanguageFilter extends LanguageFilter {

	@SuppressWarnings("unused")
	private SLanguageFilter() {
		// For JSON
	}

	public SLanguageFilter(String text) {
		super(text);
	}

	public void toString(StringBuffer buffer) {
		buffer.append(" language ").append(getBooleanComparisonOperator()).append(" ");
		List<String> codes = getLanguageCodes();
		if (codes.size() > 1) {
			buffer.append("(");
		}
		int i = 0;
		for (String code : codes) {
			if (i++ > 1) {
				buffer.append(" ");
			}
			buffer.append(code);
		}
		if (codes.size() > 1) {
			buffer.append(")");
		}
	}
}
