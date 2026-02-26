package org.snomed.snowstorm.syndication.dto;

public class InstallEditionRequest {

	private String editionId;
	private String version;

	public InstallEditionRequest() {
	}

	public InstallEditionRequest(String editionId, String version) {
		this.editionId = editionId;
		this.version = version;
	}

	public String getEditionId() {
		return editionId;
	}

	public void setEditionId(String editionId) {
		this.editionId = editionId;
	}

	public String getVersion() {
		return version;
	}

	public void setVersion(String version) {
		this.version = version;
	}
}

