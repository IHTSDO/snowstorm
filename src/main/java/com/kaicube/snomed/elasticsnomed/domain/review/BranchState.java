package com.kaicube.snomed.elasticsnomed.domain.review;

import com.kaicube.elasticversioncontrol.domain.Branch;

public class BranchState {

	private String path;
	private Long baseTimestamp;
	private Long headTimestamp;

	public BranchState(Branch branch) {
		path = branch.getFatPath();
		baseTimestamp = branch.getBase().getTime();
		headTimestamp = branch.getHead().getTime();
	}

	public String getPath() {
		return path;
	}

	public Long getBaseTimestamp() {
		return baseTimestamp;
	}

	public Long getHeadTimestamp() {
		return headTimestamp;
	}
}
