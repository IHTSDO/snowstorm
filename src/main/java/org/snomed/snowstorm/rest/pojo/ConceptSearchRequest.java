package org.snomed.snowstorm.rest.pojo;

import java.util.Set;

public class ConceptSearchRequest {

	private String termFilter;
	private Boolean activeFilter;
	private String eclFilter;
	private Set<String> conceptIds;
	private Boolean stated;
	private int offset = 0;
	private int limit = 50;
//		  "moduleFilter": "",
//		  "activeFilter": false,
//		  "escgFilter": "",
//		  "expand": "",
//		  "limit": 0,
//		  "offset": 0


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

	public Set<String> getConceptIds() {
		return conceptIds;
	}

	public void setConceptIds(Set<String> conceptIds) {
		this.conceptIds = conceptIds;
	}

	public Boolean getStated() {
		return stated;
	}

	public void setStated(boolean stated) {
		this.stated = stated;
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
