package org.snomed.snowstorm.core.rf2;

public enum RF2Type {

	DELTA("Delta"), SNAPSHOT("Snapshot"), FULL("Full");

	private String name;

	private RF2Type(String name) {
		this.name = name;
	}

	public String getName() {
		return name;
	}

}
