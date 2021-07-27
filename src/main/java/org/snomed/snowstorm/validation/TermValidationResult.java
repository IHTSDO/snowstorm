package org.snomed.snowstorm.validation;

import org.ihtsdo.drools.response.InvalidContent;

import java.util.List;

public class TermValidationResult {

	private final List<InvalidContent> invalidContents;
	private final long tsvDuration;
	private final long totalDuration;

	public TermValidationResult(List<InvalidContent> invalidContents, long tsvDuration, long totalDuration) {
		this.invalidContents = invalidContents;
		this.tsvDuration = tsvDuration;
		this.totalDuration = totalDuration;
	}

	public List<InvalidContent> getInvalidContents() {
		return invalidContents;
	}

	public long getTsvDuration() {
		return tsvDuration;
	}

	public long getTotalDuration() {
		return totalDuration;
	}
}
