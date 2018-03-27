package org.snomed.snowstorm.fhir.domain.resource;

import org.snomed.snowstorm.fhir.domain.element.FHIRIssue;

public class FHIROperationOutcome extends FHIRResource {
	
	FHIRIssue issue;

	public void setIssue(FHIRIssue issue) {
		this.issue = issue;
	}
	public FHIRIssue getIssue() {
		return issue;
	}
	
}
