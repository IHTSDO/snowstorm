package org.snomed.snowstorm.fhir.domain.element;

import java.util.List;

public class FHIRIssue {
	
	Severity severity;
	IssueType code;
	List<FHIRCodeableConcept> details;
	String diagnostics;
	String location;
	String expression;

	
	public Severity getSeverity() {
		return severity;
	}

	public void setSeverity(Severity severity) {
		this.severity = severity;
	}

	public IssueType getCode() {
		return code;
	}

	public void setCode(IssueType code) {
		this.code = code;
	}

	public List<FHIRCodeableConcept> getDetails() {
		return details;
	}

	public void setDetails(List<FHIRCodeableConcept> details) {
		this.details = details;
	}

	public String getDiagnostics() {
		return diagnostics;
	}

	public void setDiagnostics(String diagnostics) {
		this.diagnostics = diagnostics;
	}

	public String getLocation() {
		return location;
	}

	public void setLocation(String location) {
		this.location = location;
	}

	public String getExpression() {
		return expression;
	}

	public void setExpression(String expression) {
		this.expression = expression;
	}

	public enum IssueType {
		Required, Invalid, Invariant;
		
		@Override
		public String toString() {
			return name().toLowerCase();
		}
	}
	
	public enum Severity {
		Fatal, Error, Warning, Information;
		
		@Override
		public String toString() {
			return name().toLowerCase();
		}
	}
}
