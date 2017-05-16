package org.ihtsdo.elasticsnomed.mrcm.model.load;

import org.ihtsdo.elasticsnomed.mrcm.model.InclusionType;

import java.util.Set;

public class Attribute {

	private Long conceptId;
	private InclusionType inclusionType;
	private Set<Children> childrens;

	public Long getConceptId() {
		return conceptId;
	}

	public void setConceptId(Long conceptId) {
		this.conceptId = conceptId;
	}

	public InclusionType getInclusionType() {
		return inclusionType;
	}

	public void setInclusionType(InclusionType inclusionType) {
		this.inclusionType = inclusionType;
	}

	public Set<Children> getChildrens() {
		return childrens;
	}

	public void setChildrens(Set<Children> childrens) {
		this.childrens = childrens;
	}
}
