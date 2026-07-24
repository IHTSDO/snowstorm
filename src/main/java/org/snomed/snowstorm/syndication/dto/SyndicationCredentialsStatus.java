package org.snomed.snowstorm.syndication.dto;

public class SyndicationCredentialsStatus {

	private boolean configured;

	public SyndicationCredentialsStatus() {
	}

	public SyndicationCredentialsStatus(boolean configured) {
		this.configured = configured;
	}

	public boolean isConfigured() {
		return configured;
	}

	public void setConfigured(boolean configured) {
		this.configured = configured;
	}
}
