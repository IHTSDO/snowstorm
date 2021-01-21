package org.snomed.snowstorm.util;

public final class ConceptControllerTestConstants {

	public static final String CONCEPT_WITH_VALIDATION_ERRORS_AND_WARNINGS = "{\n" +
			"  \"conceptId\": \"9999005\",\n" +
			"  \"active\": true,\n" +
			"  \"definitionStatus\": \"PRIMITIVE\",\n" +
			"  \"moduleId\": \"900000000000207008\",\n" +
			"  \"effectiveTime\": \"20020131\",\n" +
			"  \"fsn\": {\n" +
			"    \"term\": \"Duodenal ampulla structure (body structure)\",\n" +
			"    \"lang\": \"en\"\n" +
			"  },\n" +
			"  \"pt\": {\n" +
			"    \"term\": \"Duodenal ampulla structure\",\n" +
			"    \"lang\": \"en\"\n" +
			"  },\n" +
			"  \"id\": \"9999005\"\n" +
			"}";

	public static final String CONCEPT_WITH_VALIDATION_WARNINGS_ONLY = "{\n" +
			"      \"conceptId\": \"99970008\",\n" +
			"      \"active\": false,\n" +
			"      \"definitionStatus\": \"PRIMITIVE\",\n" +
			"      \"moduleId\": \"900000000000207008\",\n" +
			"      \"effectiveTime\": \"20090731\",\n" +
			"      \"fsn\": {\n" +
			"        \"term\": \"BIO-BURS I (product)\",\n" +
			"        \"lang\": \"en\"\n" +
			"      },\n" +
			"      \"pt\": {\n" +
			"        \"term\": \"BIO-BURS I\",\n" +
			"        \"lang\": \"en\"\n" +
			"      },\n" +
			"      \"id\": \"99970008\"\n" +
			"    }";

	private ConceptControllerTestConstants() {
	}
}
