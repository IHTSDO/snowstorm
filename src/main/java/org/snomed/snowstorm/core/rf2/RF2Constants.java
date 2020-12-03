package org.snomed.snowstorm.core.rf2;

import java.util.regex.Pattern;

public final class RF2Constants {

	public static final String CONCEPT_HEADER = "id\teffectiveTime\tactive\tmoduleId\tdefinitionStatusId";
	public static final String DESCRIPTION_HEADER = "id\teffectiveTime\tactive\tmoduleId\tconceptId\tlanguageCode\ttypeId\tterm\tcaseSignificanceId";
	public static final String RELATIONSHIP_HEADER = "id\teffectiveTime\tactive\tmoduleId\tsourceId\tdestinationId\trelationshipGroup\ttypeId\tcharacteristicTypeId\tmodifierId";
	public static final String SIMPLE_REFSET_HEADER = "id\teffectiveTime\tactive\tmoduleId\trefsetId\treferencedComponentId";
	public static final Pattern EFFECTIVE_DATE_PATTERN = Pattern.compile("\\d{8}");
	public static final int MEMBER_ADDITIONAL_FIELD_OFFSET = 6;

	private RF2Constants() {
	}
}
