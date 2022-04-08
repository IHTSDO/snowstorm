package org.snomed.snowstorm.ecl.domain.filter;

import org.snomed.langauges.ecl.domain.filter.SearchType;
import org.snomed.langauges.ecl.domain.filter.TypedSearchTerm;

public class STypedSearchTerm extends TypedSearchTerm {

	@SuppressWarnings("unused")
	private STypedSearchTerm() {
		// For JSON
	}

	public STypedSearchTerm(SearchType searchType, String text) {
		super(searchType, text);
	}
}
