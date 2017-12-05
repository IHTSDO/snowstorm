package org.snomed.snowstorm.mrcm.model;

public class Range {
	private Long conceptId;
	private InclusionType inclusionType;

	public Range(Long conceptId, InclusionType inclusionType) {
		this.conceptId = conceptId;
		this.inclusionType = inclusionType;
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

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		Range range = (Range) o;

		return conceptId.equals(range.conceptId);
	}

	@Override
	public int hashCode() {
		return conceptId.hashCode();
	}
}
