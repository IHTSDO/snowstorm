package org.snomed.snowstorm.core.data.domain;


public class ConceptMicro {

	private final String id;
	private boolean primitive;
	private String term;
	
	public ConceptMicro(ConceptMini mini) {
		this.id = mini.getConceptId();
		this.term = mini.getFsn();
		this.primitive = mini.getDefinitionStatus().equals(Concepts.PRIMITIVE);
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

}
