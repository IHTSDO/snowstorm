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

	public static final String CONCEPT_REFERENCE_SET_SIMPLE = "{\n" +
			"  \"conceptId\": \"12345678901\",\n" +
			"  \"descriptions\": [\n" +
			"    {\n" +
			"      \"active\": true,\n" +
			"      \"moduleId\": \"900000000000207008\",\n" +
			"      \"type\": \"FSN\",\n" +
			"      \"term\": \"Car history reference set (foundation metadata concept)\",\n" +
			"      \"lang\": \"en\",\n" +
			"      \"caseSignificance\": \"CASE_INSENSITIVE\",\n" +
			"      \"conceptId\": null,\n" +
			"      \"acceptabilityMap\": {\n" +
			"        \"900000000000509007\": \"PREFERRED\",\n" +
			"        \"900000000000508004\": \"PREFERRED\"\n" +
			"      },\n" +
			"      \"descriptionId\": \"12345678910\"\n" +
			"    },\n" +
			"    {\n" +
			"      \"active\": true,\n" +
			"      \"moduleId\": \"900000000000207008\",\n" +
			"      \"type\": \"SYNONYM\",\n" +
			"      \"term\": \"Car history reference set\",\n" +
			"      \"lang\": \"en\",\n" +
			"      \"caseSignificance\": \"CASE_INSENSITIVE\",\n" +
			"      \"conceptId\": null,\n" +
			"      \"acceptabilityMap\": {\n" +
			"        \"900000000000509007\": \"PREFERRED\",\n" +
			"        \"900000000000508004\": \"PREFERRED\"\n" +
			"      },\n" +
			"      \"descriptionId\": \"1234567891011\"\n" +
			"    }\n" +
			"  ],\n" +
			"  \"relationships\": [],\n" +
			"  \"classAxioms\": [\n" +
			"    {\n" +
			"      \"axiomId\": \"50cb0bf1-24f5-4951-ae84-4955654dd932\",\n" +
			"      \"definitionStatus\": \"PRIMITIVE\",\n" +
			"      \"effectiveTime\": null,\n" +
			"      \"active\": true,\n" +
			"      \"released\": false,\n" +
			"      \"moduleId\": \"900000000000207008\",\n" +
			"      \"relationships\": [\n" +
			"        {\n" +
			"          \"active\": true,\n" +
			"          \"groupId\": 0,\n" +
			"          \"target\": {\n" +
			"            \"conceptId\": \"446609009\",\n" +
			"            \"fsn\": \"Simple type reference set (foundation metadata concept)\",\n" +
			"            \"definitionStatus\": \"PRIMITIVE\",\n" +
			"            \"effectiveTime\": \"20020131\",\n" +
			"            \"moduleId\": \"900000000000012004\",\n" +
			"            \"active\": true\n" +
			"          },\n" +
			"          \"type\": {\n" +
			"            \"conceptId\": \"116680003\",\n" +
			"            \"pt\": \"Is a\"\n" +
			"          },\n" +
			"          \"moduleId\": \"900000000000207008\"\n" +
			"        }\n" +
			"      ]\n" +
			"    }\n" +
			"  ],\n" +
			"  \"fsn\": null,\n" +
			"  \"definitionStatus\": \"PRIMITIVE\",\n" +
			"  \"active\": true,\n" +
			"  \"released\": false,\n" +
			"  \"moduleId\": \"900000000000207008\"\n" +
			"}";

	private ConceptControllerTestConstants() {
	}
}
