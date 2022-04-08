package org.snomed.snowstorm.ecl.domain.filter;

import org.snomed.langauges.ecl.domain.filter.*;

import static org.snomed.snowstorm.core.util.CollectionUtils.orEmpty;

public class SDescriptionFilterConstraint extends DescriptionFilterConstraint {
	public void toString(StringBuffer buffer) {
		buffer.append(" {{ D");

		int f = 0;
		for (TermFilter termFilter : orEmpty(getTermFilters())) {
			if (f++ > 0) {
				buffer.append(",");
			}
			((STermFilter) termFilter).toString(buffer);
		}

		for (DescriptionTypeFilter descriptionTypeFilter : orEmpty(getDescriptionTypeFilters())) {
			if (f++ > 0) {
				buffer.append(",");
			}
			((SDescriptionTypeFilter) descriptionTypeFilter).toString(buffer);
		}

		for (LanguageFilter languageFilter : orEmpty(getLanguageFilters())) {
			if (f++ > 0) {
				buffer.append(",");
			}
			((SLanguageFilter) languageFilter).toString(buffer);
		}

		for (DialectFilter dialectFilter : orEmpty(getDialectFilters())) {
			if (f++ > 0) {
				buffer.append(",");
			}
			((SDialectFilter) dialectFilter).toString(buffer);
		}

		buffer.append(" }}");
	}
}
