package org.snomed.snowstorm.rest.pojo;

import java.util.Map;

public class UpdateBranchRequest {

	private Map<String, Object> metadata;

	public Map<String, Object> getMetadata() {
		return metadata;
	}

	public void setMetadata(Map<String, Object> metadata) {
		this.metadata = metadata;
	}
}
