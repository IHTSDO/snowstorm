package org.snomed.snowstorm.rest.pojo;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.kaicode.elasticvc.domain.Branch;
import org.snomed.snowstorm.core.data.domain.security.UserBranchRoles;

import java.util.Date;
import java.util.Map;
import java.util.Set;

@JsonPropertyOrder({"path", "state", "containsContent", "locked", "creation", "base", "head", "creationTimestamp", "baseTimestamp", "headTimestamp",
		"userRoles", "globalUserRoles", "versionsReplacedCounts"})
public class BranchPojo {

	private final Branch branch;
	private final Map<String, Object> metadata;
	private final Set<String> userRoles;
	private final Set<String> globalUserRoles;

	public BranchPojo(Branch branch, Map<String, Object> metadata, UserBranchRoles userBranchRoles) {
		this.branch = branch;
		this.metadata = metadata;
		this.userRoles = userBranchRoles.getGrantedBranchRole();
		this.globalUserRoles = userBranchRoles.getGrantedGlobalRole();
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

	public Set<String> getUserRoles() {
		return userRoles;
	}

	public Set<String> getGlobalUserRoles() {
		return globalUserRoles;
	}

	public Map<String, Integer> getVersionsReplacedCounts() {
		return branch.getVersionsReplacedCounts();
	}
}
