package org.snomed.snowstorm.mrcm.model;

import java.util.HashSet;
import java.util.Set;

public class Attribute {
	private Long conceptId;
	private InclusionType inclusionType;
	private Set<Range> rangeSet;

	public Attribute(Long conceptId, InclusionType inclusionType) {
		this.conceptId = conceptId;
		this.inclusionType = inclusionType;
		rangeSet = new HashSet<>();
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

	public Set<Range> getRangeSet() {
		return rangeSet;
	}

	public void setRangeSet(Set<Range> rangeSet) {
		this.rangeSet = rangeSet;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		Attribute attribute = (Attribute) o;

		return conceptId.equals(attribute.conceptId);
	}

	@Override
	public int hashCode() {
		return conceptId.hashCode();
	}
}
