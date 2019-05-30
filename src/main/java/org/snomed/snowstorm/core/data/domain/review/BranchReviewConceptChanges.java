package org.snomed.snowstorm.core.data.domain.review;

import java.util.Set;

public class BranchReviewConceptChanges {

	private String id;
	private Set<Long> changedConcepts;

	public BranchReviewConceptChanges(String id, Set<Long> changedConcepts) {
		this.id = id;
		this.changedConcepts = changedConcepts;
	}

	public String getId() {
		return id;
	}

	public Set<Long> getChangedConcepts() {
		return changedConcepts;
	}

}
