package org.snomed.snowstorm.rest.pojo;

import com.fasterxml.jackson.annotation.JsonView;
import org.snomed.snowstorm.core.data.domain.ConceptMini;
import org.snomed.snowstorm.rest.View;

public class BrowserDescriptionSearchResult {

	private String term;
	private boolean active;
	private ConceptMini concept;

	public BrowserDescriptionSearchResult(String term, boolean active, ConceptMini concept) {
		this.term = term;
		this.active = active;
		this.concept = concept;
		concept.flattenFsn();
	}

	@JsonView(value = View.Component.class)
	public String getTerm() {
		return term;
	}

	@JsonView(value = View.Component.class)
	public boolean isActive() {
		return active;
	}

	@JsonView(value = View.Component.class)
	public ConceptMini getConcept() {
		return concept;
	}
}
