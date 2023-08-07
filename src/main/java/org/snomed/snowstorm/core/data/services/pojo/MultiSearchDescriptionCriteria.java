package org.snomed.snowstorm.core.data.services.pojo;

import org.snomed.snowstorm.rest.pojo.MultibranchDescriptionSearchRequest;

import java.util.Set;

public class MultiSearchDescriptionCriteria extends DescriptionCriteria{

	private Set<Long> conceptIds;
	private String ecl;
	private MultibranchDescriptionSearchRequest includeBranches;
	
	public MultiSearchDescriptionCriteria conceptIds(Set<Long> conceptIds) {
		this.conceptIds = conceptIds;
		return this;
	}

	public Set<Long> getConceptIds() {
		return conceptIds;
	}
	
	public MultiSearchDescriptionCriteria ecl(String ecl) {
		this.ecl = ecl;
		return this;
	}
	
	public String getEcl() {
		return ecl;
	}
	
	public MultiSearchDescriptionCriteria includeBranches(MultibranchDescriptionSearchRequest includeBranches) {
		this.includeBranches = includeBranches;
		return this;
	}
	
	public MultibranchDescriptionSearchRequest getIncludeBranches() {
		return includeBranches;
	}
}