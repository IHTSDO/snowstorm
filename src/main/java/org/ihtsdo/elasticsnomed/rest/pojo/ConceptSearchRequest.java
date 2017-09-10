package org.ihtsdo.elasticsnomed.rest.pojo;

public class ConceptSearchRequest {

	private String termFilter;
	private String eclFilter;
	private boolean stated;
//		  "moduleFilter": "",
//		  "activeFilter": false,
//		  "escgFilter": "",
//		  "conceptIds": [""],
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

	public boolean isStated() {
		return stated;
	}

	public void setStated(boolean stated) {
		this.stated = stated;
	}
}
