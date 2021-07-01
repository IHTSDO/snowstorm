package org.snomed.snowstorm.core.data.services.pojo;

public class CodeSystemConfiguration {

	private final String owner;
	private final String name;
	private final String shortName;
	private final String module;

	public CodeSystemConfiguration(String name, String shortName, String module, String owner) {
		this.name = name;
		this.shortName = shortName;
		this.module = module;
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

	public String getOwner() {
		return owner;
	}

	@Override
	public String toString() {
		return "CodeSystemConfiguration{" +
				"name='" + name + '\'' +
				", shortName='" + shortName + '\'' +
				", module='" + module + '\'' +
				", owner='" + owner + '\'' +
				'}';
	}
}
