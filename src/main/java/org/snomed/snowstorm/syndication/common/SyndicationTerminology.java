package org.snomed.snowstorm.syndication.common;

import static org.snomed.snowstorm.syndication.common.SyndicationConstants.ATC_CODESYSTEM;
import static org.snomed.snowstorm.syndication.common.SyndicationConstants.BCP13_CODESYSTEM;
import static org.snomed.snowstorm.syndication.common.SyndicationConstants.BCP47_CODESYSTEM;
import static org.snomed.snowstorm.syndication.common.SyndicationConstants.HL_7_TERMINOLOGY;
import static org.snomed.snowstorm.syndication.common.SyndicationConstants.ISO3166_CODESYSTEM;
import static org.snomed.snowstorm.syndication.common.SyndicationConstants.LOINC_TERMINOLOGY;
import static org.snomed.snowstorm.syndication.common.SyndicationConstants.M49_CODESYSTEM;
import static org.snomed.snowstorm.syndication.common.SyndicationConstants.SNOMED_TERMINOLOGY;

public enum SyndicationTerminology {
    LOINC(LOINC_TERMINOLOGY, "import-loinc-terminology", false, true),
    HL7(HL_7_TERMINOLOGY, "import-hl7-terminology", false, true),
    SNOMED(SNOMED_TERMINOLOGY, "import-snomed-terminology", false, true),
    ATC(ATC_CODESYSTEM, null, true, true),
    BCP13(BCP13_CODESYSTEM, null, true, true),
    BCP47(BCP47_CODESYSTEM, null, true, true),
    ISO3166(ISO3166_CODESYSTEM, null, true, true),
    M49(M49_CODESYSTEM, null, true, false)
    ;

    private final String name;
    private final String importArg;
    private final boolean importByDefault;
    private final boolean requiresFiles;

    SyndicationTerminology(String name, String importArg, boolean importByDefault, boolean requiresFiles) {
        this.name = name;
        this.importArg = importArg;
        this.importByDefault = importByDefault;
        this.requiresFiles = requiresFiles;
    }

    public String getName() {
        return name;
    }

    public String getImportArg() {
        return importArg;
    }

    public boolean importByDefault() {
        return importByDefault;
    }

    public boolean requiresFiles() {
        return requiresFiles;
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
