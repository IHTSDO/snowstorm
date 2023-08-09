package org.snomed.snowstorm.core.data.services.pojo;

public record CodeSystemConfiguration(String name, String shortName, String module, String countryCode, String owner) {


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
