package com.kaicube.snomed.elasticsnomed.domain;

import com.fasterxml.jackson.annotation.JsonView;
import com.kaicube.snomed.elasticsnomed.rest.View;

import java.util.HashSet;
import java.util.Set;

public class ConceptMini {

	private String conceptId;
	private Set<Description> activeFsns;

	public ConceptMini(String conceptId) {
		this.conceptId = conceptId;
		activeFsns = new HashSet<>();
	}

	@JsonView(value = View.Component.class)
	public String getConceptId() {
		return conceptId;
	}

	@JsonView(value = View.Component.class)
	public String getFsn() {
		return activeFsns.isEmpty() ? null : activeFsns.iterator().next().getTerm();
	}

	public void addActiveFsn(Description fsn) {
		activeFsns.add(fsn);
	}
}
