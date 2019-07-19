package org.snomed.snowstorm.rest.pojo;

public class CodeSystemUpdateRequest {

	public String name;
	public String countryCode;
	public String defaultLanguageCode;
	public String branchPath;

	public String getName() {
		return name;
	}

	public String getCountryCode() {
		return countryCode;
	}

	public String getDefaultLanguageCode() {
		return defaultLanguageCode;
	}

	public String getBranchPath() {
		return branchPath;
	}
}
