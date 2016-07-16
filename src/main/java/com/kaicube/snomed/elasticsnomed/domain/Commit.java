package com.kaicube.snomed.elasticsnomed.domain;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;

public class Commit {

	private Branch branch;

	private Date timepoint;

	private Set<String> entityVersionsReplaced;

	public Commit(Branch branch) {
		this.branch = branch;
		this.timepoint = new Date();
		entityVersionsReplaced = new HashSet<>();
	}

	public Branch getBranch() {
		return branch;
	}

	public Date getTimepoint() {
		return timepoint;
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
}
