package org.ihtsdo.elasticsnomed.core.data.services.authoringmirror;

import java.util.Date;
import java.util.Map;

public class TraceabilityActivity {

	private String userId;
	private String commitComment;
	private String branchPath;
	private Long commitTimestamp;
	private Map<String, ConceptChange> changes;

	public TraceabilityActivity() {
	}

	public String getUserId() {
		return userId;
	}

	public void setUserId(String userId) {
		this.userId = userId;
	}

	public String getCommitComment() {
		return commitComment;
	}

	public void setCommitComment(String commitComment) {
		this.commitComment = commitComment;
	}

	public String getBranchPath() {
		return branchPath;
	}

	public void setBranchPath(String branchPath) {
		this.branchPath = branchPath;
	}

	public Date getCommitTimestamp() {
		return new Date(commitTimestamp);
	}

	public void setCommitTimestamp(Long commitTimestamp) {
		this.commitTimestamp = commitTimestamp;
	}

	public Map<String, ConceptChange> getChanges() {
		return changes;
	}

	public void setChanges(Map<String, ConceptChange> changes) {
		this.changes = changes;
	}
}
