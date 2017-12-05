package org.snomed.snowstorm.core.data.domain.review;

import java.util.Set;

public class BranchReviewConceptChanges {

	private String id;
	private Set<Long> newConcepts;
	private Set<Long> changedConcepts;
	private Set<Long> deletedConcepts;

	public BranchReviewConceptChanges(String id, Set<Long> newConcepts, Set<Long> changedConcepts, Set<Long> deletedConcepts) {
		this.id = id;
		this.newConcepts = newConcepts;
		this.changedConcepts = changedConcepts;
		this.deletedConcepts = deletedConcepts;
	}

	public String getId() {
		return id;
	}

	public Set<Long> getNewConcepts() {
		return newConcepts;
	}

	public Set<Long> getChangedConcepts() {
		return changedConcepts;
	}

	public Set<Long> getDeletedConcepts() {
		return deletedConcepts;
	}
}
