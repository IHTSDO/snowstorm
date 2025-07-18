package org.snomed.snowstorm.core.data.services;

import io.kaicode.elasticvc.domain.Branch;
import io.kaicode.elasticvc.domain.Metadata;
import org.snomed.snowstorm.config.Config;
import org.snomed.snowstorm.core.data.domain.Concepts;

import static org.snomed.snowstorm.core.data.domain.Concepts.inactivationIndicatorNames;

public abstract class InactivationIndicatorUtil {
	private InactivationIndicatorUtil() {

	}

	public static String getInactivationIndicatorInverse(Branch branch, String inactivationIndicator) {
		return getInactivationIndicator(branch, inactivationIndicator, true);
	}

	public static String getInactivationIndicator(Branch branch, String inactivationIndicator) {
		return getInactivationIndicator(branch, inactivationIndicator, false);
	}

	private static String getInactivationIndicator(Branch branch, String inactivationIndicator, boolean inverse) {
		if (branch == null || inactivationIndicator == null || inactivationIndicator.isEmpty()) {
			return null;
		}

		Metadata metadata = branch.getMetadata();
		if (metadata == null || metadata.size() == 0) {
			return getNullable(inactivationIndicator, inverse);
		}

		String cncEnabled = metadata.containsKey(Config.CNC_ENABLED) ? metadata.getString(Config.CNC_ENABLED) : null;
		boolean enabled = cncEnabled == null || "true".equalsIgnoreCase(cncEnabled);
		inactivationIndicator = getNullable(inactivationIndicator, inverse);
		if (!enabled && (Concepts.CONCEPT_NON_CURRENT.equals(inactivationIndicator) || "CONCEPT_NON_CURRENT".equals(inactivationIndicator))) {
			return null;
		}

		return inactivationIndicator;
	}

	private static String getNullable(String value, boolean inverse) {
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
