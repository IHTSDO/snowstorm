package org.snomed.snowstorm.syndication.common;

import java.util.List;

public class SyndicationConstants {

    public static final String IMPORT_LOINC_TERMINOLOGY = "import-loinc-terminology";
    public static final String IMPORT_HL_7_TERMINOLOGY = "import-hl7-terminology";
    public static final String IMPORT_SNOMED_TERMINOLOGY = "import-snomed-terminology";
    public static final String EXTENSION_COUNTRY_CODE = "extension-country-code";
    public static final String LATEST_VERSION = "latest";
    public static final String LOCAL_VERSION = "local";

    public static final List<String> SUPPORTED_TERMINOLOGIES = List.of(IMPORT_SNOMED_TERMINOLOGY, IMPORT_LOINC_TERMINOLOGY, IMPORT_HL_7_TERMINOLOGY);
}
