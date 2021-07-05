package org.snomed.snowstorm.validation;

import org.ihtsdo.drools.response.InvalidContent;
import org.ihtsdo.drools.response.Severity;

import java.util.List;

public class InvalidContentWithSeverityStatus {

	private final List<InvalidContent> invalidContents;
	private final Severity severity;

	public InvalidContentWithSeverityStatus(final List<InvalidContent> invalidContents) {
		this.invalidContents = invalidContents;
		this.severity = invalidContents.stream().anyMatch(invalidContent -> invalidContent.getSeverity() == Severity.ERROR) ? Severity.ERROR : Severity.WARNING;
	}

	public final List<InvalidContent> getInvalidContents() {
		return invalidContents;
	}

	public final Severity getSeverity() {
		return severity;
	}
}
