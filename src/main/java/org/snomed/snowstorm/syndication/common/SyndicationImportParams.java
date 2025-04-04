package org.snomed.snowstorm.syndication.common;

public class SyndicationImportParams {

    private String version;
    private String extensionName;
    private boolean isLoincImportIncluded;

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

    public void setExtensionName(String extensionName) {
        this.extensionName = extensionName;
    }

    public boolean isLoincImportIncluded() {
        return isLoincImportIncluded;
    }

    public void setLoincImportIncluded(boolean loincImportIncluded) {
        isLoincImportIncluded = loincImportIncluded;
    }
}
