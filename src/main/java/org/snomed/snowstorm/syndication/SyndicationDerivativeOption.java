package org.snomed.snowstorm.syndication;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"contentItemIdentifier", "title", "contentItemVersion", "versionDate"})
public class SyndicationDerivativeOption {

	private String contentItemIdentifier;
	private String title;
	private String contentItemVersion;
	private int versionDate;

	public SyndicationDerivativeOption() {
	}

	public SyndicationDerivativeOption(String contentItemIdentifier, String contentItemVersion, String title, int versionDate) {
		this.contentItemIdentifier = contentItemIdentifier;
		this.contentItemVersion = contentItemVersion;
		this.title = title;
		this.versionDate = versionDate;
	}

	public String getContentItemIdentifier() {
		return contentItemIdentifier;
	}

	public void setContentItemIdentifier(String contentItemIdentifier) {
		this.contentItemIdentifier = contentItemIdentifier;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getContentItemVersion() {
		return contentItemVersion;
	}

	public void setContentItemVersion(String contentItemVersion) {
		this.contentItemVersion = contentItemVersion;
	}

	public int getVersionDate() {
		return versionDate;
	}

	public void setVersionDate(int versionDate) {
		this.versionDate = versionDate;
	}
}
