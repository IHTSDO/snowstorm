package org.snomed.snowstorm.rest.pojo;

import io.swagger.annotations.ApiModelProperty;

public class CodeSystemUpdateRequest {

	public String name;
	public String countryCode;
	public String defaultLanguageCode;
	public String[] defaultLanguageReferenceSets;

	@ApiModelProperty(value = "false")
	public boolean dailyBuildAvailable;

	public CodeSystemUpdateRequest() {
	}

	public CodeSystemUpdateRequest(String name) {
		this.name = name;
	}

	public String getName() {
		return name;
	}

	public String getCountryCode() {
		return countryCode;
	}

	public String getDefaultLanguageCode() {
		return defaultLanguageCode;
	}

	public String[] getDefaultLanguageReferenceSets() {
		return defaultLanguageReferenceSets;
	}

	public boolean isDailyBuildAvailable() {
		return dailyBuildAvailable;
	}
}
