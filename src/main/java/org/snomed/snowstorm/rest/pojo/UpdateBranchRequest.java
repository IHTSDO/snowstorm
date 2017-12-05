package org.snomed.snowstorm.rest.pojo;

import java.util.Map;

public class UpdateBranchRequest {

	private Map<String, String> metadata;

	public Map<String, String> getMetadata() {
		return metadata;
	}

	public void setMetadata(Map<String, String> metadata) {
		this.metadata = metadata;
	}
}
