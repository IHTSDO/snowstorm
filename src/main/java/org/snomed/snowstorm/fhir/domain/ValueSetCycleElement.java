package org.snomed.snowstorm.fhir.domain;

public record ValueSetCycleElement (boolean include, String valueSetUrl, String valueSetVersion) {

    public String getCanonicalUrlVersion() {
        return valueSetUrl + "|" + valueSetVersion;
    }
}
