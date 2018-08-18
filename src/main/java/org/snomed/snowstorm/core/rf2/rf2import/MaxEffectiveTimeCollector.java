package org.snomed.snowstorm.core.rf2.rf2import;

public class MaxEffectiveTimeCollector {

	private Integer maxEffectiveTime;

	public void add(Integer effectiveTime) {
		if (maxEffectiveTime == null || maxEffectiveTime < effectiveTime) {
			maxEffectiveTime = effectiveTime;
		}
	}

	public Integer getMaxEffectiveTime() {
		return maxEffectiveTime;
	}
}
