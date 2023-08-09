package org.snomed.snowstorm.util;

public final class ConceptControllerTestConstants {

	public static final String CONCEPT_WITH_VALIDATION_ERRORS_AND_WARNINGS = """
            {
              "conceptId": "9999005",
              "active": true,
              "definitionStatus": "PRIMITIVE",
              "moduleId": "900000000000207008",
              "effectiveTime": "20020131",
              "fsn": {
                "term": "Duodenal ampulla structure (body structure)",
                "lang": "en"
              },
              "pt": {
                "term": "Duodenal ampulla structure",
                "lang": "en"
              },
              "id": "9999005"
            }""";

	public static final String CONCEPT_WITH_VALIDATION_WARNINGS_ONLY = """
            {
                  "conceptId": "99970008",
                  "active": false,
                  "definitionStatus": "PRIMITIVE",
                  "moduleId": "900000000000207008",
                  "effectiveTime": "20090731",
                  "fsn": {
                    "term": "BIO-BURS I (product)",
                    "lang": "en"
                  },
                  "pt": {
                    "term": "BIO-BURS I",
                    "lang": "en"
                  },
                  "id": "99970008"
                }""";

	public static final String CONCEPT_REFERENCE_SET_SIMPLE = """
            {
              "conceptId": "12345678901",
              "descriptions": [
                {
                  "active": true,
                  "moduleId": "900000000000207008",
                  "type": "FSN",
                  "term": "Car history reference set (foundation metadata concept)",
                  "lang": "en",
                  "caseSignificance": "CASE_INSENSITIVE",
                  "conceptId": null,
                  "acceptabilityMap": {
                    "900000000000509007": "PREFERRED",
                    "900000000000508004": "PREFERRED"
                  },
                  "descriptionId": "12345678910"
                },
                {
                  "active": true,
                  "moduleId": "900000000000207008",
                  "type": "SYNONYM",
                  "term": "Car history reference set",
                  "lang": "en",
                  "caseSignificance": "CASE_INSENSITIVE",
                  "conceptId": null,
                  "acceptabilityMap": {
                    "900000000000509007": "PREFERRED",
                    "900000000000508004": "PREFERRED"
                  },
                  "descriptionId": "1234567891011"
                }
              ],
              "relationships": [],
              "classAxioms": [
                {
                  "axiomId": "50cb0bf1-24f5-4951-ae84-4955654dd932",
                  "definitionStatus": "PRIMITIVE",
                  "effectiveTime": null,
                  "active": true,
                  "released": false,
                  "moduleId": "900000000000207008",
                  "relationships": [
                    {
                      "active": true,
                      "groupId": 0,
                      "target": {
                        "conceptId": "446609009",
                        "fsn": "Simple type reference set (foundation metadata concept)",
                        "definitionStatus": "PRIMITIVE",
                        "effectiveTime": "20020131",
                        "moduleId": "900000000000012004",
                        "active": true
                      },
                      "type": {
                        "conceptId": "116680003",
                        "pt": "Is a"
                      },
                      "moduleId": "900000000000207008"
                    }
                  ]
                }
              ],
              "fsn": null,
              "definitionStatus": "PRIMITIVE",
              "active": true,
              "released": false,
              "moduleId": "900000000000207008"
            }""";

	private ConceptControllerTestConstants() {
	}
}
