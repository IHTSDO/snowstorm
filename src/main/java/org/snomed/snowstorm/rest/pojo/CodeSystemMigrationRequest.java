package org.snomed.snowstorm.rest.pojo;

public class CodeSystemMigrationRequest {

	private String dependantCodeSystem;
	private Integer newDependantVersion;
	private boolean copyMetadata;

	public String getDependantCodeSystem() {
		return dependantCodeSystem;
	}

	public void setDependantCodeSystem(String dependantCodeSystem) {
		this.dependantCodeSystem = dependantCodeSystem;
	}

	public Integer getNewDependantVersion() {
		return newDependantVersion;
	}

	public void setNewDependantVersion(Integer newDependantVersion) {
		this.newDependantVersion = newDependantVersion;
	}

	public boolean isCopyMetadata() {
		return copyMetadata;
	}

	public void setCopyMetadata(boolean copyMetadata) {
		this.copyMetadata = copyMetadata;
	}
}
