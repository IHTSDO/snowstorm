package org.snomed.snowstorm.rest.pojo;

import java.util.Set;

public class ConceptSearchRequest {

	private String termFilter;
	private Boolean activeFilter;
	private String eclFilter;
	private String statedEclFilter;
	private Set<String> conceptIds;
	private int offset = 0;
	private int limit = 50;
//		  "moduleFilter": "",
//		  "activeFilter": false,
//		  "escgFilter": "",
//		  "expand": "",


	public ConceptSearchRequest() {
	}

	public String getTermFilter() {
		return termFilter;
	}

	public void setTermFilter(String termFilter) {
		this.termFilter = termFilter;
	}

	public Boolean getActiveFilter() {
		return activeFilter;
	}

	public void setActiveFilter(Boolean activeFilter) {
		this.activeFilter = activeFilter;
	}

	public String getEclFilter() {
		return eclFilter;
	}

	public void setEclFilter(String eclFilter) {
		this.eclFilter = eclFilter;
	}

	public String getStatedEclFilter() {
		return statedEclFilter;
	}

	public void setStatedEclFilter(String statedEclFilter) {
		this.statedEclFilter = statedEclFilter;
	}

	public Set<String> getConceptIds() {
		return conceptIds;
	}

	public void setConceptIds(Set<String> conceptIds) {
		this.conceptIds = conceptIds;
	}

	public int getOffset() {
		return offset;
	}

	public void setOffset(int offset) {
		this.offset = offset;
	}

	public int getLimit() {
		return limit;
	}

	public void setLimit(int limit) {
		this.limit = limit;
	}
}
