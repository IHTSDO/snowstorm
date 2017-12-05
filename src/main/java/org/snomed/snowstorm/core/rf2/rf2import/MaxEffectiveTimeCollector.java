package org.snomed.snowstorm.core.rf2.rf2import;

import java.util.regex.Pattern;

public class MaxEffectiveTimeCollector {

	private static final Pattern EFFECTIVE_TIME_PATTERN = Pattern.compile("\\d{8}");

	private Long maxEffectiveTime;

	public void add(String effectiveTime) {
		if (effectiveTime != null && EFFECTIVE_TIME_PATTERN.matcher(effectiveTime).matches()) {
			if (maxEffectiveTime == null || Long.parseLong(effectiveTime) > maxEffectiveTime) {
				maxEffectiveTime = Long.parseLong(effectiveTime);
			}
		}
	}

	public String getMaxEffectiveTime() {
		if (maxEffectiveTime != null) {
			return maxEffectiveTime.toString();
		}
		return null;
	}
}
