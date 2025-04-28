package org.snomed.snowstorm.syndication.models.integration;

import jakarta.xml.bind.annotation.XmlElement;

public class SyndicationDependency {

	private String editionDependency;

	@XmlElement(namespace = "http://snomed.info/syndication/sct-extension/1.0.0", name = "editionDependency")
	public String getEditionDependency() {
		return editionDependency;
	}

	public void setEditionDependency(String editionDependency) {
		this.editionDependency = editionDependency;
	}
}
