package org.snomed.snowstorm.fhir.services;

import org.hl7.fhir.r4.model.StringType;
import org.snomed.snowstorm.core.data.domain.Concepts;
import org.snomed.snowstorm.fhir.config.FHIRConstants;

class FHIRHelper {

	Integer getSnomedVersion(String versionStr) {
		String versionUri = "/" + FHIRConstants.VERSION + "/";
		return !versionStr.contains("/" + FHIRConstants.VERSION + "/")
				? null
				: Integer.parseInt(versionStr.substring(versionStr.indexOf(versionUri) + versionUri.length()));
	}

	//TODO Maintain a cache of known concepts so we can look up the preferred term at runtime
	// ... Peter, this doesn't sound like a good idea. Just lookup all required preferred terms in batch including these known concepts.
	// 		Looking up every time means you don't need to cache multiple versions of PTs for different snomed versions/extensions/languages. Kaicode


	static String translateDescType(String typeSctid) {
		switch (typeSctid) {
			case Concepts.FSN : return "Fully specified name";
			case Concepts.SYNONYM : return "Synonym";
			case Concepts.TEXT_DEFINITION : return "Text definition";
		}
		return null;
	}

	String getSnomedEditionModule(StringType versionStr) {
		if (versionStr == null || versionStr.getValueAsString().isEmpty() || versionStr.getValueAsString().equals(FHIRConstants.SNOMED_URI)) {
			return Concepts.CORE_MODULE;
		}
		return getSnomedEditionModule(versionStr.getValueAsString());
	}

	private String getSnomedEditionModule(String versionStr) {
		return !versionStr.contains("/" + FHIRConstants.VERSION + "/")
				? versionStr.substring(FHIRConstants.SNOMED_URI.length() + 1,  FHIRConstants.SNOMED_URI.length() + versionStr.length() - FHIRConstants.SNOMED_URI.length())
				: versionStr.substring(FHIRConstants.SNOMED_URI.length() + 1, versionStr.indexOf("/" + FHIRConstants.VERSION + "/"));
	}
}
