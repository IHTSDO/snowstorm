package org.snomed.snowstorm.rest.pojo;

import io.kaicode.elasticvc.domain.Branch;

import java.util.Date;
import java.util.Map;
import java.util.Set;

public class BranchPojo {

	private final Branch branch;
	private final Map<String, Object> metadata;
	private final Set<String> userRoles;

	public BranchPojo(Branch branch, Map<String, Object> metadata, Set<String> userRoles) {
		this.branch = branch;
		this.metadata = metadata;
		this.userRoles = userRoles;
	}

	public String getPath() {
		return branch.getPath();
	}

	public Date getBase() {
		return branch.getBase();
	}

	public Date getHead() {
		return branch.getHead();
	}

	public long getBaseTimestamp() {
		return branch.getBaseTimestamp();
	}

	public long getHeadTimestamp() {
		return branch.getHeadTimestamp();
	}

	public long getCreationTimestamp() {
		return branch.getCreationTimestamp();
	}

	public Date getCreation() {
		return branch.getCreation();
	}

	public Map<String, Set<String>> getVersionsReplaced() {
		return branch.getVersionsReplaced();
	}

	public boolean isLocked() {
		return branch.isLocked();
	}

	public boolean isContainsContent() {
		return branch.isContainsContent();
	}

	public Branch.BranchState getState() {
		return branch.getState();
	}

	public Map<String, Object> getMetadata() {
		return metadata;
	}

	public Map<String, Integer> getVersionsReplacedCounts() {
		return branch.getVersionsReplacedCounts();
	}

	public Set<String> getUserRoles() {
		return userRoles;
	}
}
