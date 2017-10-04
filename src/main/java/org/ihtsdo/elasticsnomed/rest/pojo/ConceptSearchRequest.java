package org.ihtsdo.elasticsnomed.rest.pojo;

import java.util.Set;

public class ConceptSearchRequest {

	private String termFilter;
	private String eclFilter;
	private Set<String> conceptIds;
	private boolean stated;
	private int page = 0;
	private int size = 50;
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

	public boolean isStated() {
		return stated;
	}

	public void setStated(boolean stated) {
		this.stated = stated;
	}

	public int getPage() {
		return page;
	}

	public void setPage(int page) {
		this.page = page;
	}

	public int getSize() {
		return size;
	}

	public void setSize(int size) {
		this.size = size;
	}
}
