package org.snomed.snowstorm.rest.pojo;

import java.util.Set;

public class MultibranchDescriptionSearchRequest {

	private Set<String> branches;

	public MultibranchDescriptionSearchRequest() {
	}

	public void setBranches(Set<String> branches) {
		this.branches = branches;
	}

	public Set<String> getBranches() {
		return branches;
	}

}