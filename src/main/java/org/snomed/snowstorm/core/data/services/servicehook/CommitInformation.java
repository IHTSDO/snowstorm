package org.snomed.snowstorm.core.data.services.servicehook;

import io.kaicode.elasticvc.domain.Branch;
import io.kaicode.elasticvc.domain.Commit;

import java.util.Map;

public class CommitInformation {

	private final String sourceBranchPath;
	private final String targetBranchPath;
	private final Commit.CommitType commitType;
	private final long headTime;
	private final Map<String, Object> metadata;

	public CommitInformation(Commit commit) {
		final Branch branch = commit.getBranch();
		commitType = commit.getCommitType();
		headTime = commit.getBranch().getHeadTimestamp();
		metadata = branch.getMetadata().getAsMap();

		if (Commit.CommitType.CONTENT == commitType) {
			this.sourceBranchPath = branch.getPath();
			this.targetBranchPath = null;
		} else {
			this.sourceBranchPath = commit.getSourceBranchPath();
			this.targetBranchPath = branch.getPath();
		}
	}

	public String getSourceBranchPath() {
		return sourceBranchPath;
	}

	public String getTargetBranchPath() {
		return targetBranchPath;
	}

	public Commit.CommitType getCommitType() {
		return commitType;
	}

	public long getHeadTime() {
		return headTime;
	}

	public Map<String, Object> getMetadata() {
		return metadata;
	}
}
