package org.snomed.snowstorm.rest.pojo;

public class CodeSystemUpgradeRequest {

	private Integer newDependantVersion;
	private Boolean contentAutomations;
	private Boolean asyncRequest;

	public CodeSystemUpgradeRequest() {
	}

	public CodeSystemUpgradeRequest(Integer newDependantVersion) {
		this.newDependantVersion = newDependantVersion;
	}

	public Integer getNewDependantVersion() {
		return newDependantVersion;
	}

	public void setNewDependantVersion(Integer newDependantVersion) {
		this.newDependantVersion = newDependantVersion;
	}

	public Boolean getContentAutomations() {
		return contentAutomations;
	}

	public void setAsyncRequest(Boolean asyncRequest) {
		this.asyncRequest = asyncRequest;
	}

	public Boolean isAsyncRequest() {
		return asyncRequest;
	}
}
