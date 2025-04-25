package org.snomed.snowstorm.syndication.models.integration;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

import java.util.ArrayList;
import java.util.List;

@XmlRootElement(name = "feed")
public class SyndicationFeed {

	private String title;

	private List<SyndicationFeedEntry> entries;

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	@XmlElement(name="entry", type = SyndicationFeedEntry.class)
	public List<SyndicationFeedEntry> getEntries() {
		if(entries == null) {
			entries = new ArrayList<>();
		}
		return entries;
	}

	public void setEntries(List<SyndicationFeedEntry> entries) {
		this.entries = entries;
	}
}
