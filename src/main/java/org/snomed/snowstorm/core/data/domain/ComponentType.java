package org.snomed.snowstorm.core.data.domain;

public enum ComponentType {
	Concept, Description, Relationship, Axiom;

	public static ComponentType getTypeFromPartition(String partitionId) {
		int lastDigit = Integer.parseInt(partitionId.substring(partitionId.length() - 1));
		return ComponentType.values()[lastDigit];
	}
}
