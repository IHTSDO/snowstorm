package org.snomed.snowstorm.syndication.client;

import jakarta.xml.bind.annotation.XmlElement;

import java.util.List;

public class SyndicationDependency {

	private String editionDependency;
	private List<String> derivativeDependency;

	@XmlElement(namespace = "http://snomed.info/syndication/sct-extension/1.0.0", name = "editionDependency")
	public String getEditionDependency() {
		return editionDependency;
	}

	public void setEditionDependency(String editionDependency) {
		this.editionDependency = editionDependency;
	}

	@XmlElement(namespace = "http://snomed.info/syndication/sct-extension/1.0.0", name = "derivativeDependency")
	public List<String> getDerivativeDependency() {
		return derivativeDependency;
	}

	public void setDerivativeDependency(List<String> derivativeDependency) {
		this.derivativeDependency = derivativeDependency;
	}
}
