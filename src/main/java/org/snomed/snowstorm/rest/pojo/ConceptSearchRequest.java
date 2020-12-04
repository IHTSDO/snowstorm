package org.snomed.snowstorm.rest.pojo;

import java.util.Set;

public class ConceptSearchRequest {

	private String termFilter;
	private Boolean termActive;
	private Boolean activeFilter;
	private Set<Long> descriptionType;
	private Set<String> language;
	private Set<Long> preferredIn;
	private Set<Long> acceptableIn;
	private Set<Long> preferredOrAcceptableIn;
	private String definitionStatusFilter;
	private String eclFilter;
	private String statedEclFilter;
	private Set<String> conceptIds;
	private boolean returnIdOnly;
	private int offset = 0;
	private int limit = 50;
	private String searchAfter;
//		  "moduleFilter": "",


	public ConceptSearchRequest() {
	}

	public String getTermFilter() {
		return termFilter;
	}

	public void setTermFilter(String termFilter) {
		this.termFilter = termFilter;
	}

	public Boolean getTermActive() {
		return termActive;
	}

	public void setTermActive(Boolean termActive) {
		this.termActive = termActive;
	}

	public Boolean getActiveFilter() {
		return activeFilter;
	}

	public void setActiveFilter(Boolean activeFilter) {
		this.activeFilter = activeFilter;
	}

	public Set<Long> getDescriptionType() {
		return descriptionType;
	}

	public void setDescriptionType(Set<Long> descriptionType) {
		this.descriptionType = descriptionType;
	}

	public Set<String> getLanguage() {
		return language;
	}

	public Set<Long> getPreferredIn() {
		return preferredIn;
	}

	public Set<Long> getAcceptableIn() {
		return acceptableIn;
	}

	public Set<Long> getPreferredOrAcceptableIn() {
		return preferredOrAcceptableIn;
	}

	public String getDefinitionStatusFilter() {
		return definitionStatusFilter;
	}

	public void setDefinitionStatusFilter(String definitionStatusFilter) {
		this.definitionStatusFilter = definitionStatusFilter;
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

	public boolean isReturnIdOnly() {
		return returnIdOnly;
	}

	public void setReturnIdOnly(boolean returnIdOnly) {
		this.returnIdOnly = returnIdOnly;
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

	public String getSearchAfter() {
		return searchAfter;
	}

	public void setSearchAfter(String searchAfter) {
		this.searchAfter = searchAfter;
	}
}
