package org.snomed.snowstorm.core.data.services;

import static org.snomed.snowstorm.core.data.domain.Concepts.inactivationIndicatorNames;

public abstract class InactivationIndicatorUtil {
	private InactivationIndicatorUtil() {

	}

	public static String getInactivationIndicatorInverse(String inactivationIndicator) {
		return getNullable(inactivationIndicator, true);
	}

	public static String getInactivationIndicator(String inactivationIndicator) {
		return getNullable(inactivationIndicator, false);
	}

	private static String getNullable(String value, boolean inverse) {
		if (value == null || value.isEmpty()) {
			return null;
		}
		try {
			if (inverse) {
				return inactivationIndicatorNames.inverse().get(value);
			} else {
				return inactivationIndicatorNames.get(value);
			}
		} catch (Exception e) {
			return null;
		}
	}
}
