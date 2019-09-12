package org.snomed.snowstorm.rest.pojo;

public class CodeSystemUpgradeRequest {

	private Integer newDependantVersion;

	public Integer getNewDependantVersion() {
		return newDependantVersion;
	}

	public void setNewDependantVersion(Integer newDependantVersion) {
		this.newDependantVersion = newDependantVersion;
	}
}
