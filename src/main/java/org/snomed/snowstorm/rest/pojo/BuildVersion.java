package org.snomed.snowstorm.rest.pojo;

public class BuildVersion {

	private String version;
	private String time;

	public BuildVersion(String version, String time) {
		this.version = version;
		this.time = time;
	}

	public String getVersion() {
		return version;
	}

	public String getTime() {
		return time;
	}
}
