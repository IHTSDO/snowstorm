package org.snomed.snowstorm.rest.pojo;

import java.util.Set;

public class BranchReviewConceptChanges {

	private Set<Long> changedConcepts;

	public BranchReviewConceptChanges(Set<Long> changedConcepts) {
		this.changedConcepts = changedConcepts;
	}

	public Set<Long> getChangedConcepts() {
		return changedConcepts;
	}

}
