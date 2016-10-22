package com.kaicube.elasticversioncontrol.domain;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;

public class Commit {

	private Branch branch;

	private Date timepoint;

	private Set<String> entityVersionsReplaced;

	private CommitType commitType;
	private String sourceBranchPath;

	public Commit(Branch branch) {
		this.branch = branch;
		this.timepoint = new Date();
		entityVersionsReplaced = new HashSet<>();
		commitType = CommitType.CONTENT;
	}

	public Branch getBranch() {
		return branch;
	}

	public Date getTimepoint() {
		return timepoint;
	}

	public CommitType getCommitType() {
		return commitType;
	}

	public void setCommitType(CommitType commitType) {
		this.commitType = commitType;
	}

	@Override
	public String toString() {
		return "Commit{" +
				"branch=" + branch +
				", timepoint=" + timepoint +
				'}';
	}

	public String getFlatBranchPath() {
		return branch.getFlatPath();
	}

	public void addVersionReplaced(String internalId) {
		entityVersionsReplaced.add(internalId);
	}

	public Set<String> getEntityVersionsReplaced() {
		return entityVersionsReplaced;
	}

	public void setSourceBranchPath(String sourceBranchPath) {
		this.sourceBranchPath = sourceBranchPath;
	}

	public String getSourceBranchPath() {
		return sourceBranchPath;
	}

	public enum CommitType {
		CONTENT, REBASE, PROMOTION
	}
}
