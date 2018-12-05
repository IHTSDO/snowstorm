package org.snomed.snowstorm.core.data.services.pojo;

public class CodeSystemConfiguration {

	private String name;
	private String shortName;
	private String module;

	public CodeSystemConfiguration(String name, String shortName, String module) {
		this.name = name;
		this.shortName = shortName;
		this.module = module;
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

	@Override
	public String toString() {
		return "CodeSystemConfiguration{" +
				"name='" + name + '\'' +
				", shortName='" + shortName + '\'' +
				", module='" + module + '\'' +
				'}';
	}
}
