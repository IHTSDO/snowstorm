package org.snomed.snowstorm.ecl.domain.filter;

import org.snomed.langauges.ecl.domain.filter.ActiveFilter;
import org.snomed.langauges.ecl.domain.filter.ConceptFilterConstraint;
import org.snomed.langauges.ecl.domain.filter.EffectiveTimeFilter;
import org.snomed.langauges.ecl.domain.filter.FieldFilter;

import static org.snomed.snowstorm.core.util.CollectionUtils.orEmpty;

public class SConceptFilterConstraint extends ConceptFilterConstraint {

	public void toString(StringBuffer buffer) {
		buffer.append(" {{ C");

		for (ActiveFilter activeFilter : orEmpty(getActiveFilters())) {
			((SActiveFilter)activeFilter).toString(buffer);
		}
		for (FieldFilter definitionStatusFilter : orEmpty(getDefinitionStatusFilters())) {
			((SFieldFilter) definitionStatusFilter).toString(buffer);
		}
		for (FieldFilter moduleFilters : orEmpty(getModuleFilters())) {
			((SFieldFilter) moduleFilters).toString(buffer);
		}
		for (EffectiveTimeFilter effectiveTimeFilter : orEmpty(getEffectiveTimeFilters())) {
			((SEffectiveTimeFilter)effectiveTimeFilter).toString(buffer);
		}

		buffer.append(" }}");
	}

}
