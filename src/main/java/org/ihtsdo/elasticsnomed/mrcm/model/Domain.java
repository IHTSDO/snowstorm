package org.ihtsdo.elasticsnomed.mrcm.model;

import java.util.HashSet;
import java.util.Set;

public class Domain {
	private Long conceptId;
	private InclusionType inclusionType;
	private Set<Attribute> attributes;

	public Domain(Long conceptId, InclusionType inclusionType) {
		this.conceptId = conceptId;
		this.inclusionType = inclusionType;
		attributes = new HashSet<>();
	}

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

	public Set<Attribute> getAttributes() {
		return attributes;
	}

	public void setAttributes(Set<Attribute> attributes) {
		this.attributes = attributes;
	}
}
