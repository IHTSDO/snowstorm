package org.snomed.snowstorm.validation.domain;

import org.ihtsdo.drools.domain.Description;
import org.ihtsdo.drools.response.InvalidContent;
import org.ihtsdo.drools.response.Severity;

public class InvalidContentDuplicate extends InvalidContent {

	private final String otherConceptId;

	public InvalidContentDuplicate(String ruleId, Description description, String message, Severity severity, String otherConceptId) {
		super(ruleId, description, message, severity);
		this.otherConceptId = otherConceptId;
	}

	public String getOtherConceptId() {
		return otherConceptId;
	}
}
