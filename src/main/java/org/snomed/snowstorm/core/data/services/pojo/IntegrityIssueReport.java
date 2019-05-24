package org.snomed.snowstorm.core.data.services.pojo;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class IntegrityIssueReport {

	private Map<String, Set<Long>> axiomsWithMissingOrInactiveReferencedConcept;
	private Map<Long, Long> relationshipsWithMissingOrInactiveSource;
	private Map<Long, Long> relationshipsWithMissingOrInactiveType;
	private Map<Long, Long> relationshipsWithMissingOrInactiveDestination;

	public boolean isEmpty() {
		return (axiomsWithMissingOrInactiveReferencedConcept == null || axiomsWithMissingOrInactiveReferencedConcept.isEmpty()) &&
		(relationshipsWithMissingOrInactiveSource == null || relationshipsWithMissingOrInactiveSource.isEmpty()) &&
		(relationshipsWithMissingOrInactiveType == null || relationshipsWithMissingOrInactiveType.isEmpty()) &&
		(relationshipsWithMissingOrInactiveDestination == null || relationshipsWithMissingOrInactiveDestination.isEmpty());
	}

	public void setAxiomsWithMissingOrInactiveReferencedConcept(Map<String, Set<Long>> axiomsWithMissingOrInactiveReferencedConcept) {
		this.axiomsWithMissingOrInactiveReferencedConcept = axiomsWithMissingOrInactiveReferencedConcept;
	}

	public Map<String, Set<Long>> getAxiomsWithMissingOrInactiveReferencedConcept() {
		return axiomsWithMissingOrInactiveReferencedConcept;
	}

	public void setRelationshipsWithMissingOrInactiveSource(Map<Long, Long> relationshipsWithMissingOrInactiveSource) {
		this.relationshipsWithMissingOrInactiveSource = relationshipsWithMissingOrInactiveSource;
	}

	public Map<Long, Long> getRelationshipsWithMissingOrInactiveSource() {
		return relationshipsWithMissingOrInactiveSource;
	}

	public void setRelationshipsWithMissingOrInactiveType(Map<Long, Long> relationshipsWithMissingOrInactiveType) {
		this.relationshipsWithMissingOrInactiveType = relationshipsWithMissingOrInactiveType;
	}

	public Map<Long, Long> getRelationshipsWithMissingOrInactiveType() {
		return relationshipsWithMissingOrInactiveType;
	}

	public void setRelationshipsWithMissingOrInactiveDestination(Map<Long, Long> relationshipsWithMissingOrInactiveDestination) {
		this.relationshipsWithMissingOrInactiveDestination = relationshipsWithMissingOrInactiveDestination;
	}

	public Map<Long, Long> getRelationshipsWithMissingOrInactiveDestination() {
		return relationshipsWithMissingOrInactiveDestination;
	}

	@Override
	public String toString() {
		return "IntegrityIssueReport{" +
				"axiomsWithMissingOrInactiveReferencedConcept=" + axiomsWithMissingOrInactiveReferencedConcept +
				", relationshipsWithMissingOrInactiveSource=" + relationshipsWithMissingOrInactiveSource +
				", relationshipsWithMissingOrInactiveType=" + relationshipsWithMissingOrInactiveType +
				", relationshipsWithMissingOrInactiveDestination=" + relationshipsWithMissingOrInactiveDestination +
				'}';
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		IntegrityIssueReport that = (IntegrityIssueReport) o;
		return Objects.equals(axiomsWithMissingOrInactiveReferencedConcept, that.axiomsWithMissingOrInactiveReferencedConcept) &&
				Objects.equals(relationshipsWithMissingOrInactiveSource, that.relationshipsWithMissingOrInactiveSource) &&
				Objects.equals(relationshipsWithMissingOrInactiveType, that.relationshipsWithMissingOrInactiveType) &&
				Objects.equals(relationshipsWithMissingOrInactiveDestination, that.relationshipsWithMissingOrInactiveDestination);
	}

	@Override
	public int hashCode() {
		return Objects.hash(axiomsWithMissingOrInactiveReferencedConcept, relationshipsWithMissingOrInactiveSource, relationshipsWithMissingOrInactiveType, relationshipsWithMissingOrInactiveDestination);
	}
}
