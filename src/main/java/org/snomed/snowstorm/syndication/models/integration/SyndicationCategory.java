package org.snomed.snowstorm.syndication.models.integration;

import jakarta.xml.bind.annotation.XmlAttribute;

public class SyndicationCategory {

	private String term;

	@XmlAttribute
	public String getTerm() {
		return term;
	}

	public void setTerm(String term) {
		this.term = term;
	}
}
