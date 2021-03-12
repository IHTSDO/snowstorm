package org.snomed.snowstorm.rest.pojo;

import java.util.Map;

public class UpdatedDocumentCount {

	private final Map<String, Integer> updateCount;

	public UpdatedDocumentCount(Map<String, Integer> updateCount) {
		this.updateCount = updateCount;
	}

	public Map<String, Integer> getUpdateCount() {
		return updateCount;
	}
}
