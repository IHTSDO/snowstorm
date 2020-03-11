package org.snomed.snowstorm.core.data.services.pojo;

import java.util.Set;

public class ConceptCriteria {

	private Boolean active;
	private Set<String> conceptIds;

	public boolean hasConceptCriteria() {
		return conceptIds != null;
	}

	public ConceptCriteria conceptIds(Set<String> conceptIds) {
		this.conceptIds = conceptIds;
		return this;
	}
	
	public Set<String> getConceptIds() {
		return conceptIds;
	}

	public ConceptCriteria active(Boolean active) {
		this.active = active;
		return this;
	}

	public Boolean getActive() {
		return active;
	}

}
