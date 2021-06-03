package org.snomed.snowstorm.core.data.services.servicehook;

import io.kaicode.elasticvc.domain.Branch;
import io.kaicode.elasticvc.domain.Commit;

import java.util.Map;

public class CommitInformation {

	private final String path;
	private final Commit.CommitType commitType;
	private final long headTime;
	private final Map<String, Object> metadata;

	public CommitInformation(Commit commit) {
		final Branch branch = commit.getBranch();
		path = branch.getPath();
		commitType = commit.getCommitType();
		headTime = commit.getBranch().getHeadTimestamp();
		metadata = branch.getMetadata().getAsMap();
	}

	public String getPath() {
		return path;
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
