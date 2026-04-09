package org.snomed.snowstorm.syndication.dto;

import java.util.Collections;
import java.util.List;

public class InstallEditionRequest {

	private String editionId;
	private String version;
	private List<String> derivativeContentItemVersions;

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

	public List<String> getDerivativeContentItemVersions() {
		return derivativeContentItemVersions != null ? derivativeContentItemVersions : Collections.emptyList();
	}

	public void setDerivativeContentItemVersions(List<String> derivativeContentItemVersions) {
		this.derivativeContentItemVersions = derivativeContentItemVersions;
	}
}

