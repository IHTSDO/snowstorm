package org.snomed.snowstorm.core.data.domain;


import java.util.Objects;

public class ConceptMicro {

	private final String id;
	private boolean primitive;
	private String term;
	
	public ConceptMicro(ConceptMini mini) {
		this.id = mini.getConceptId();
		this.term = mini.getFsn();
		this.primitive = mini.isPrimitive();
	}
	
	public ConceptMicro(Concept c) {
		this.id = c.getConceptId();
		this.term = c.getFsn();
		this.primitive = c.isPrimitive();
	}

	public ConceptMicro(String id) {
		this.id = id;
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
	
	public boolean isPrimitive() {
		return primitive;
	}
	
	public void setPrimitive(boolean isPrimitive) {
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
}
