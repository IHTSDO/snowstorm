package org.ihtsdo.elasticsnomed.core.data.services.pojo;

import java.io.Serializable;
import java.util.Map;

public class ResultMapPage<S extends Serializable, T> {

	private Map<S, T> resultsMap;
	private long totalElements;

	public ResultMapPage(Map<S, T> resultsMap, long totalElements) {
		this.resultsMap = resultsMap;
		this.totalElements = totalElements;
	}

	public Map<S, T> getResultsMap() {
		return resultsMap;
	}

	public long getTotalElements() {
		return totalElements;
	}
}
