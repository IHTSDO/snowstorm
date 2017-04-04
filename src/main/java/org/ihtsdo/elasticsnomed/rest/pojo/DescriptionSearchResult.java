package org.ihtsdo.elasticsnomed.rest.pojo;

import com.fasterxml.jackson.annotation.JsonView;
import org.ihtsdo.elasticsnomed.domain.ConceptMini;
import org.ihtsdo.elasticsnomed.rest.View;

public class DescriptionSearchResult {

	private String term;
	private boolean active;
	private ConceptMini concept;

	public DescriptionSearchResult(String term, boolean active, ConceptMini concept) {
		this.term = term;
		this.active = active;
		this.concept = concept;
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
