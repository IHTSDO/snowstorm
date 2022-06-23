package org.snomed.snowstorm.ecl.domain.filter;

import org.snomed.langauges.ecl.domain.filter.EffectiveTimeFilter;
import org.snomed.langauges.ecl.domain.filter.NumericComparisonOperator;

import java.util.List;

public class SEffectiveTimeFilter extends EffectiveTimeFilter {

	public SEffectiveTimeFilter() {
	}

	public SEffectiveTimeFilter(NumericComparisonOperator operator, List<Integer> effectiveTimes) {
		super(operator, effectiveTimes);
	}

	public void toString(StringBuffer buffer) {
		buffer.append(" effectiveTime ").append(getOperator().getText());
		ECLToStringUtil.toString(buffer, getEffectiveTime());
	}
}
