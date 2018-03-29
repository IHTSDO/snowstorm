package org.snomed.snowstorm.fhir.services;


import java.time.Year;

import org.snomed.snowstorm.core.data.domain.Concepts;
import org.snomed.snowstorm.fhir.domain.element.FHIRIssue;
import org.snomed.snowstorm.fhir.domain.element.FHIRIssue.*;
import org.snomed.snowstorm.fhir.domain.resource.FHIROperationOutcome;

public class FHIRHelper {
	
	private static String DEFAULT_BRANCH = "MAIN";
	private static int MIN_RELEASE = 19920131;
	private static int MAX_RELEASE = Integer.parseInt((Year.now().getValue() + 1) + "0731");	
	
	public FHIROperationOutcome validationFailure(String diagnostics) {
		FHIRIssue issue = new FHIRIssue(IssueType.Invariant, diagnostics);
		return new FHIROperationOutcome(issue);
	}

	public String getBranchForVersion(String versionStr) throws FHIROperationException {
		if (versionStr == null || versionStr.isEmpty()) {
			return DEFAULT_BRANCH;
		}
		try {
			int version = Integer.parseInt(versionStr);
			if (version < MIN_RELEASE || version > MAX_RELEASE) {
				throw new FHIROperationException(IssueType.Value, "Version outside of range" + versionStr);
			}
			//TODO Look up and cache the correct branch for this version
			return DEFAULT_BRANCH;
		} catch (NumberFormatException e) {
			throw new FHIROperationException(IssueType.Value, "Invalid version: " + versionStr, e);
		}
	}

	//TODO Maintain a cache of known concepts so we can look up the preferred term at runtime
	public static String translateDescType(String typeSctid) {
		switch (typeSctid) {
			case Concepts.FSN : return "Fully specified name";
			case Concepts.SYNONYM : return "Synonym";
			case Concepts.TEXT_DEFINITION : return "Text definition";
		}
		return null;
	}

}
