package org.snomed.snowstorm.ecl.domain.filter;

import org.snomed.langauges.ecl.domain.filter.ActiveFilter;

public class SActiveFilter extends ActiveFilter {

	@SuppressWarnings("unused")
	private SActiveFilter() {
		// For JSON
	}

	public SActiveFilter(boolean b) {
		super(b);
	}

	public void toString(StringBuffer buffer) {
		buffer.append(" active = ").append(isActive());
	}
}
