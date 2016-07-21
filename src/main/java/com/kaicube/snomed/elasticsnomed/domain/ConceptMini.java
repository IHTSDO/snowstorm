package com.kaicube.snomed.elasticsnomed.domain;

import com.fasterxml.jackson.annotation.JsonView;
import com.kaicube.snomed.elasticsnomed.rest.View;

import java.util.HashSet;
import java.util.Set;

public class ConceptMini {

	private String conceptId;
	private Set<Description> activeFsns;
	private String definitionStatusId;
	private Boolean leafInferred;

	public ConceptMini(String conceptId) {
		this.conceptId = conceptId;
		activeFsns = new HashSet<>();
	}

	public ConceptMini(Concept concept) {
		this(concept.getConceptId());
		definitionStatusId = concept.getDefinitionStatusId();
	}

	public void addActiveFsn(Description fsn) {
		activeFsns.add(fsn);
	}

	@JsonView(value = View.Component.class)
	public String getConceptId() {
		return conceptId;
	}

	@JsonView(value = View.Component.class)
	public String getFsn() {
		return activeFsns.isEmpty() ? null : activeFsns.iterator().next().getTerm();
	}

	@JsonView(value = View.Component.class)
	public String getDefinitionStatusId() {
		return definitionStatusId;
	}

	public void setDefinitionStatusId(String definitionStatusId) {
		this.definitionStatusId = definitionStatusId;
	}

	@JsonView(value = View.Component.class)
	public Boolean getLeafInferred() {
		return leafInferred;
	}

	public ConceptMini setLeafInferred(Boolean leafInferred) {
		this.leafInferred = leafInferred;
		return this;
	}
}
