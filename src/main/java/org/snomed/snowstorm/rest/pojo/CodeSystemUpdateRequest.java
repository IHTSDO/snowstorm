package org.snomed.snowstorm.rest.pojo;

import io.swagger.annotations.ApiModelProperty;
import org.snomed.snowstorm.core.data.domain.CodeSystem;
import org.snomed.snowstorm.core.data.services.pojo.CodeSystemConfiguration;

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

	public CodeSystemUpdateRequest(CodeSystem codeSystem) {
		name = codeSystem.getName();
		owner = codeSystem.getOwner();
		countryCode = codeSystem.getCountryCode();
		maintainerType = codeSystem.getMaintainerType();
		defaultLanguageCode = codeSystem.getDefaultLanguageCode();
		defaultLanguageReferenceSets = codeSystem.getDefaultLanguageReferenceSets();
		dailyBuildAvailable = codeSystem.isDailyBuildAvailable();
	}

	public CodeSystemUpdateRequest(String name) {
		this.name = name;
	}

	public CodeSystemUpdateRequest(String name, String owner) {
		this.name = name;
		this.owner = owner;
	}

	public CodeSystemUpdateRequest populate(CodeSystemConfiguration configuration) {
		if (configuration.getName() != null) {
			name = configuration.getName();
		}
		if (configuration.getCountryCode() != null) {
			countryCode = configuration.getCountryCode();
		}
		if (configuration.getOwner() != null) {
			owner = configuration.getOwner();
		}
		return this;
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
