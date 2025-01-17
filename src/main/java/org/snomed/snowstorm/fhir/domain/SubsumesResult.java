package org.snomed.snowstorm.fhir.domain;

public enum SubsumesResult {

	equivalent("equivalent"),
	subsumes("subsumes"),
	subsumed_by("subsumed-by"),
	not_subsumed("not-subsumed");

	private String text;

	SubsumesResult(String text) {
		this.text = text;
	}

	public String getText() {
		return text;
	}
}
