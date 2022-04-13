package org.snomed.snowstorm.ecl.domain.filter;

import org.snomed.langauges.ecl.domain.filter.*;

import static org.snomed.snowstorm.core.util.CollectionUtils.orEmpty;

public class SMemberFilterConstraint extends MemberFilterConstraint {

	public void toString(StringBuffer buffer) {
		buffer.append(" {{ M");

		int f = 0;
		for (MemberFieldFilter memberFieldFilter : orEmpty(getMemberFieldFilters())) {
			if (f++ > 0) {
				buffer.append(",");
			}
			((SMemberFieldFilter) memberFieldFilter).toString(buffer);
		}

		for (FieldFilter fieldFilter : orEmpty(getModuleFilters())) {
			if (f++ > 0) {
				buffer.append(",");
			}
			((SFieldFilter) fieldFilter).toString(buffer);
		}

		for (EffectiveTimeFilter effectiveTimeFilter : orEmpty(getEffectiveTimeFilters())) {
			if (f++ > 0) {
				buffer.append(",");
			}
			((SEffectiveTimeFilter) effectiveTimeFilter).toString(buffer);
		}

		for (ActiveFilter activeFilter : orEmpty(getActiveFilters())) {
			if (f++ > 0) {
				buffer.append(",");
			}
			((SActiveFilter) activeFilter).toString(buffer);
		}

		buffer.append(" }}");
	}

}
