package org.snomed.snowstorm.rest.pojo;

public class CodeSystemUpdateRequest {

	public String name;
	public String countryCode;
	public String branchPath;

	public String getName() {
		return name;
	}

	public String getCountryCode() {
		return countryCode;
	}

	public String getBranchPath() {
		return branchPath;
	}
}
