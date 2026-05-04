package org.snomed.snowstorm.core.rf2.rf2import;

import org.snomed.snowstorm.core.data.services.RuntimeServiceException;
import org.snomed.snowstorm.core.data.services.identifier.IdentifierService;

import java.util.regex.Pattern;

/**
 * Validates RF2 core columns before components are persisted (non-blank values and SNOMED CT identifier shape).
 */
final class Rf2ComponentFieldValidator {

	/** RF2 reference set member id: SNOMED CT identifier or UUID. */
	private static final Pattern MEMBER_ROW_ID = Pattern.compile(
			"\\d{6,18}|[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
	public static final String IS_MISSING_OR_BLANK = " is missing or blank.";

	private Rf2ComponentFieldValidator() {
	}

	static void requireSnomedId(String context, String fieldName, String value) {
		String trimmed = trimOrNull(value);
		if (trimmed == null) {
			throw new RuntimeServiceException(context + ": " + fieldName + IS_MISSING_OR_BLANK);
		}
		if (!IdentifierService.SCTID_PATTERN.matcher(trimmed).matches()) {
			throw new RuntimeServiceException(context + ": " + fieldName + " has invalid SNOMED CT id format: '" + value + "'.");
		}
	}

	static void requireNonBlank(String context, String fieldName, String value) {
		if (trimOrNull(value) == null) {
			throw new RuntimeServiceException(context + ": " + fieldName + IS_MISSING_OR_BLANK);
		}
	}

	static void requireMemberRowId(String context, String fieldName, String value) {
		String trimmed = trimOrNull(value);
		if (trimmed == null) {
			throw new RuntimeServiceException(context + ": " + fieldName + IS_MISSING_OR_BLANK);
		}
		if (!MEMBER_ROW_ID.matcher(trimmed).matches()) {
			throw new RuntimeServiceException(context + ": " + fieldName + " must be a SNOMED CT id or UUID, got: '" + value + "'.");
		}
	}

	static void requireNonBlankRelationshipGroup(String context, String relationshipGroup) {
		if (trimOrNull(relationshipGroup) == null) {
			throw new RuntimeServiceException(context + ": relationshipGroup is missing or blank.");
		}
		try {
			Integer.parseInt(relationshipGroup.trim());
		} catch (NumberFormatException e) {
			throw new RuntimeServiceException(context + ": relationshipGroup must be an integer, got: '" + relationshipGroup + "'.");
		}
	}

	private static String trimOrNull(String value) {
		if (value == null) {
			return null;
		}
		String t = value.trim();
		return t.isEmpty() ? null : t;
	}
}
