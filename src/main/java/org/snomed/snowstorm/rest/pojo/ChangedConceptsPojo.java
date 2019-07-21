package org.snomed.snowstorm.rest.pojo;

import java.util.Set;

public class ChangedConceptsPojo {

	private Set<Long> changedConcepts;

	public ChangedConceptsPojo(Set<Long> changedConcepts) {
		this.changedConcepts = changedConcepts;
	}

	public Set<Long> getChangedConcepts() {
		return changedConcepts;
	}
}
