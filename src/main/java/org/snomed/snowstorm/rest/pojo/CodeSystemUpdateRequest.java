package org.snomed.snowstorm.rest.pojo;

import io.swagger.annotations.ApiModelProperty;

public class CodeSystemUpdateRequest {

	public String name;
	public String owner;
	public String countryCode;
	public String maintainerType;
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

	public String getOwner() {
		return owner;
	}

	public String getCountryCode() {
		return countryCode;
	}

	public String getMaintainerType() {
		return maintainerType;
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
