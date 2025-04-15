package org.snomed.snowstorm.syndication.common;

import static org.snomed.snowstorm.syndication.common.SyndicationConstants.HL_7_TERMINOLOGY;
import static org.snomed.snowstorm.syndication.common.SyndicationConstants.LOINC_TERMINOLOGY;
import static org.snomed.snowstorm.syndication.common.SyndicationConstants.SNOMED_TERMINOLOGY;

public enum SyndicationTerminology {
    LOINC(LOINC_TERMINOLOGY, "import-loinc-terminology"),
    HL7(HL_7_TERMINOLOGY, "import-hl7-terminology"),
    SNOMED(SNOMED_TERMINOLOGY, "import-snomed-terminology");

    private final String name;
    private final String importName;

    SyndicationTerminology(String name, String importName) {
        this.name = name;
        this.importName = importName;
    }

    public String getName() {
        return name;
    }

    public String getImportName() {
        return importName;
    }

    public static SyndicationTerminology fromName(String name) {
        for (SyndicationTerminology terminology : values()) {
            if (terminology.getName().equalsIgnoreCase(name)) {
                return terminology;
            }
        }
        throw new IllegalArgumentException("Unknown syndication terminology: " + name);
    }
}
