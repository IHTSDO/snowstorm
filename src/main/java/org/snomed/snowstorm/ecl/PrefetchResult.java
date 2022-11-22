package org.snomed.snowstorm.ecl;

import java.util.List;

public class PrefetchResult {

	private List<Long> ids;

	public void set(List<Long> ids) {
		this.ids = ids;
	}

	public List<Long> getIds() {
		return ids;
	}

	public boolean isSet() {
		return ids != null;
	}
}
