package org.snomed.snowstorm.syndication;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

@JsonPropertyOrder({"id", "title", "versionsAvailable"})
public class SyndicationSnomedEdition {

	private String id;
	private List<String> versionsAvailable;
	private String title;

	public SyndicationSnomedEdition(String id) {
		this.id = id;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public List<String> getVersionsAvailable() {
		return versionsAvailable;
	}

	public void setVersionsAvailable(List<String> versionsAvailable) {
		this.versionsAvailable = versionsAvailable;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getTitle() {
		return title;
	}
}
