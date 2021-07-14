package org.snomed.snowstorm.core.data.services.pojo;

public class CodeSystemConfiguration {

	private final String name;
	private final String shortName;
	private final String module;
	private final String countryCode;
	private final String owner;

	public CodeSystemConfiguration(String name, String shortName, String module, String countryCode, String owner) {
		this.name = name;
		this.shortName = shortName;
		this.module = module;
		this.countryCode = countryCode;
		this.owner = owner;
	}

	public String getName() {
		return name;
	}

	public String getShortName() {
		return shortName;
	}

	public String getModule() {
		return module;
	}

	public String getCountryCode() {
		return countryCode;
	}

	public String getOwner() {
		return owner;
	}

	@Override
	public String toString() {
		return "CodeSystemConfiguration{" +
				"name='" + name + '\'' +
				", shortName='" + shortName + '\'' +
				", module='" + module + '\'' +
				", countryCode='" + countryCode + '\'' +
				", owner='" + owner + '\'' +
				'}';
	}
}
