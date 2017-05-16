package org.ihtsdo.elasticsnomed.core.data.domain.review;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Date;

public class BranchReview {

	private final boolean sourceIsParent;
	private String id;
	private Date lastUpdated;
	private ReviewStatus status;
	private BranchState source;
	private BranchState target;

	@JsonIgnore
	private BranchReviewConceptChanges changes;

	public BranchReview(String id, Date lastUpdated, ReviewStatus status, BranchState source, BranchState target, boolean sourceIsParent) {
		this.id = id;
		this.lastUpdated = lastUpdated;
		this.status = status;
		this.source = source;
		this.target = target;
		this.sourceIsParent = sourceIsParent;
	}

	public String getId() {
		return id;
	}

	public Date getLastUpdated() {
		return lastUpdated;
	}

	public ReviewStatus getStatus() {
		return status;
	}

	public void setStatus(ReviewStatus status) {
		this.status = status;
	}

	public BranchState getSource() {
		return source;
	}

	public BranchState getTarget() {
		return target;
	}

	public boolean isSourceIsParent() {
		return sourceIsParent;
	}

	public void setChanges(BranchReviewConceptChanges changes) {
		this.changes = changes;
	}

	public BranchReviewConceptChanges getChanges() {
		return changes;
	}
}
