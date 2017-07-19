package org.ihtsdo.elasticsnomed.core.rf2.export;

public enum ExportType {

    SNAPSHOT("Snapshot"), DELTA("Delta");

    private String name;

    private ExportType(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
