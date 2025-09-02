package org.snomed.snowstorm.rest.pojo;

import io.swagger.v3.oas.annotations.media.Schema;
import org.snomed.snowstorm.core.data.domain.CodeSystem;
import org.snomed.snowstorm.core.data.services.pojo.CodeSystemDefaultConfiguration;

public class CodeSystemUpdateRequest {

	private String name;
	private String uriModuleId;
	private String owner;
	private String countryCode;
	private String maintainerType;
	private String defaultLanguageCode;
	private String[] defaultLanguageReferenceSets;

	@Schema(defaultValue = "false")
	public boolean dailyBuildAvailable;

	public CodeSystemUpdateRequest() {
	}

	public CodeSystemUpdateRequest(CodeSystem codeSystem) {
		name = codeSystem.getName();
		uriModuleId = codeSystem.getUriModuleId();
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

	public CodeSystemUpdateRequest populate(CodeSystemDefaultConfiguration configuration) {
		if (configuration.name() != null) {
			name = configuration.name();
		}
		if (configuration.countryCode() != null) {
			countryCode = configuration.countryCode();
		}
		if (configuration.owner() != null) {
			owner = configuration.owner();
		}
		return this;
	}

	public String getName() {
		return name;
	}

	public String getUriModuleId() {
		return uriModuleId;
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
