package org.snomed.snowstorm.syndication.dto;

import java.util.Collections;
import java.util.List;

public class InstallEditionRequest {

	private String editionId;
	private String version;
	private List<String> derivativeContentItemVersions;
	private String username;
	private String password;

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

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}
}

