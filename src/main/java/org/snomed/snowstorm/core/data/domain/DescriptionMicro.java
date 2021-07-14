package org.snomed.snowstorm.core.data.domain;

import java.util.Objects;

public class DescriptionMicro {

	private final String id;
	private final String conceptId;
	private String term;

	public DescriptionMicro(String id, String conceptId, String term) {
		this.id = id;
		this.conceptId = conceptId;
		this.term = term;
	}
	
	public DescriptionMicro(Description d) {
		this.id = d.getId();
		this.conceptId = d.getConceptId();
		this.term = d.getTerm();
	}

	public String getId() {
		return id;
	}
	
	public String getConceptId() {
		return conceptId;
	}

	public String getTerm() {
		return term;
	}

	@Override
	public boolean equals(Object other) {
		if (other instanceof DescriptionMicro) {
			String otherId = ((DescriptionMicro)other).getId();
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
