package org.snomed.snowstorm.core.data.domain;


import com.fasterxml.jackson.annotation.JsonView;
import org.snomed.snowstorm.rest.View;

import java.util.Objects;

public class ConceptMicro {

	@JsonView(View.Component.class)
	private final String id;

	@JsonView(View.Component.class)
	private Boolean primitive;

	@JsonView(View.Component.class)
	private String term;
	
	public ConceptMicro(ConceptMini mini) {
		this.id = mini.getConceptId();
		this.term = mini.getFsnTerm();
		this.primitive = mini.isPrimitive();
	}
	
	public ConceptMicro(Concept c) {
		this.id = c.getConceptId();
		this.term = c.getFsn().getTerm();
		this.primitive = c.isPrimitive();
	}

	public ConceptMicro(String conceptId) {
		this.id = conceptId;
	}

	public ConceptMicro(String conceptId, String term) {
		this.id = conceptId;
		this.term = term;
	}

	public void setTerm(String term) {
		this.term = term;
	}
	
	public String getId() {
		return id;
	}

	public String getTerm() {
		return term;
	}
	
	public Boolean isPrimitive() {
		return primitive;
	}
	
	public void setPrimitive(Boolean isPrimitive) {
		this.primitive = isPrimitive;
	}

	@Override
	public boolean equals(Object other) {
		if (other instanceof ConceptMicro) {
			String otherId = ((ConceptMicro)other).getId();
			return this.id.equals(otherId);
		}
		return false;
	}

	@Override
	public int hashCode() {
		return Objects.hash(id);
	}
	
	public String toString(boolean includeTerm) {
		if (includeTerm) {
			return getId() + "|" + getTerm() + "|";
		} else {
			return getId();
		}
	}

}
