package org.snomed.snowstorm.syndication.common;

public class SyndicationImportParams {

    private String version;
    private String extensionName;
    private boolean isLoincImportIncluded;

    /**
     *
     * @param version the terminology version to import. The possible values for each terminology are enumerated below.
     *                LOINC: latest, local, 2.80, 2.79, 2.78, ...
     *                Hl7: latest, local, 6.2.0, 6.1.0, ...
     *                Snomed: local, http ://snomed.info/sct/11000172109/version/20250315, ...
     * @param extensionName specific to Snomed, e.g. "BE"
     * @param isLoincImportIncluded whether the loinc terminology is used as well.
     */
    public SyndicationImportParams(String version, String extensionName, boolean isLoincImportIncluded) {
        this.version = version;
        this.extensionName = extensionName;
        this.isLoincImportIncluded = isLoincImportIncluded;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getExtensionName() {
        return extensionName;
    }

    public boolean isLoincImportIncluded() {
        return isLoincImportIncluded;
    }
}
