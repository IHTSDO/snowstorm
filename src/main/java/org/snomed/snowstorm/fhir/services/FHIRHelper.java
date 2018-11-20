package org.snomed.snowstorm.fhir.services;


import java.time.Year;

import org.hl7.fhir.dstu3.model.OperationOutcome.IssueType;
import org.hl7.fhir.dstu3.model.StringType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.core.data.domain.Concepts;
import org.snomed.snowstorm.fhir.config.FHIRConstants;

public class FHIRHelper {

	private static int MIN_RELEASE = 19920131;
	private static int MAX_RELEASE = Integer.parseInt((Year.now().getValue() + 1) + "0731");

	private final Logger logger = LoggerFactory.getLogger(getClass());
	
/*	public FHIROperationOutcome validationFailure(String diagnostics) {
		FHIRIssue issue = new FHIRIssue(IssueType.Invariant, diagnostics);
		return new FHIROperationOutcome(issue);
	}*/

	public String getBranchForVersion(StringType versionStr) throws FHIROperationException {
		if (versionStr == null || versionStr.getValueAsString().isEmpty() || versionStr.getValueAsString().equals(FHIRConstants.SNOMED_URI)) {
			return FHIRConstants.DEFAULT_BRANCH;
		}
		try {
			logger.info("Snomed version requested {}", versionStr.getValueAsString());
			// separate edition and version
			String sctid = FHIRConstants.SnomedEdition.lookup(getSnomedEdition(versionStr.getValueAsString())).sctid();
			String snomedVersion = getSnomedVersion(versionStr.getValueAsString());
			if(snomedVersion != null && !snomedVersion.isEmpty()) {
				int version = Integer.parseInt(snomedVersion);
				if (version < MIN_RELEASE || version > MAX_RELEASE) {
					throw new FHIROperationException(IssueType.VALUE, "Version outside of range" + versionStr);
				}
				return FHIRConstants.DEFAULT_BRANCH + "/" + sctid + "/" + version;
			}
			return FHIRConstants.DEFAULT_BRANCH + "/" + sctid;
		} catch (NumberFormatException e) {
			throw new FHIROperationException(IssueType.VALUE, "Invalid version: " + versionStr, e);
		}
	}

	private String getSnomedVersion(String versionStr) {
		String versionUri = "/" + FHIRConstants.VERSION + "/";
		return !versionStr.contains("/" + FHIRConstants.VERSION + "/")
				? null
				: versionStr.substring(versionStr.indexOf(versionUri) + versionUri.length());
	}

	private String getSnomedEdition(String versionStr) {
		return !versionStr.contains("/" + FHIRConstants.VERSION + "/")
				? versionStr.substring(FHIRConstants.SNOMED_URI.length() + 1,  FHIRConstants.SNOMED_URI.length() + versionStr.length() - FHIRConstants.SNOMED_URI.length())
				: versionStr.substring(FHIRConstants.SNOMED_URI.length() + 1, versionStr.indexOf("/" + FHIRConstants.VERSION + "/"));
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

	public FHIRConstants.SnomedEdition getSnomedEdition(StringType versionStr) {
		if (versionStr == null || versionStr.getValueAsString().isEmpty() || versionStr.getValueAsString().equals(FHIRConstants.SNOMED_URI)) {
			return FHIRConstants.SnomedEdition.INTERNATIONAL;
		}
		return FHIRConstants.SnomedEdition.lookup(getSnomedEdition(versionStr.getValueAsString()));
	}
}
