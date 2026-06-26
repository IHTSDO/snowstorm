package org.snomed.snowstorm.fhir.domain;

public enum SubsumesResult {

	EQUIVALENT("equivalent"),
	SUBSUMES("subsumes"),
	SUBSUMED_BY("subsumed-by"),
	NOT_SUBSUMED("not-subsumed");

	private final String text;

	SubsumesResult(String text) {
		this.text = text;
	}

	public String getText() {
		return text;
	}
}
