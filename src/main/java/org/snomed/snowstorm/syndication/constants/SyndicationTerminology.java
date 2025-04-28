package org.snomed.snowstorm.syndication.constants;

import static org.snomed.snowstorm.syndication.constants.SyndicationConstants.ATC_CODESYSTEM;
import static org.snomed.snowstorm.syndication.constants.SyndicationConstants.BCP13_CODESYSTEM;
import static org.snomed.snowstorm.syndication.constants.SyndicationConstants.BCP47_CODESYSTEM;
import static org.snomed.snowstorm.syndication.constants.SyndicationConstants.HL_7_TERMINOLOGY;
import static org.snomed.snowstorm.syndication.constants.SyndicationConstants.ISO3166_CODESYSTEM;
import static org.snomed.snowstorm.syndication.constants.SyndicationConstants.LOINC_TERMINOLOGY;
import static org.snomed.snowstorm.syndication.constants.SyndicationConstants.M49_CODESYSTEM;
import static org.snomed.snowstorm.syndication.constants.SyndicationConstants.SNOMED_TERMINOLOGY;
import static org.snomed.snowstorm.syndication.constants.SyndicationConstants.UCUM_CODESYSTEM;

public enum SyndicationTerminology {
    LOINC(LOINC_TERMINOLOGY, false, true, false),
    HL7(HL_7_TERMINOLOGY, false, true, false),
    SNOMED(SNOMED_TERMINOLOGY, false, true, false),
    ATC(ATC_CODESYSTEM, true, true, false),
    UCUM(UCUM_CODESYSTEM, true, true, true),
    BCP13(BCP13_CODESYSTEM,  true, true, false),
    BCP47(BCP47_CODESYSTEM, true, true, false),
    ISO3166(ISO3166_CODESYSTEM,  true, true, false),
    M49(M49_CODESYSTEM, true, false, false)
    ;

    private final String name;
    private final boolean importByDefault;
    private final boolean requiresFiles;
    private final boolean alwaysReimport;

    SyndicationTerminology(String name, boolean importByDefault, boolean requiresFiles, boolean alwaysReimport) {
        this.name = name;
        this.importByDefault = importByDefault;
        this.requiresFiles = requiresFiles;
        this.alwaysReimport = alwaysReimport;
    }

    public String getName() {
        return name;
    }

    public boolean importByDefault() {
        return importByDefault;
    }

    public boolean requiresFiles() {
        return requiresFiles;
    }

    public boolean alwaysReimport() {
        return alwaysReimport;
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
