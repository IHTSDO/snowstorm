package org.snomed.snowstorm.fhir.services;

import org.snomed.snowstorm.fhir.domain.*;
import org.snomed.snowstorm.fhir.domain.element.FHIRIssue;
import org.snomed.snowstorm.fhir.domain.element.FHIRIssue.*;
import org.snomed.snowstorm.fhir.domain.resource.FHIROperationOutcome;

public class FHIRHelper {
	
	public FHIROperationOutcome validationFailure(String diagnostics) {
		FHIROperationOutcome outcome = new FHIROperationOutcome();
		FHIRIssue issue = new FHIRIssue();
		issue.setSeverity(Severity.Error);
		issue.setCode(IssueType.Invariant);
		issue.setDiagnostics(diagnostics);
		outcome.setIssue(issue);
		return outcome;
	}

}
