package org.snomed.snowstorm.core.data.services.pojo;


import org.snomed.snowstorm.core.data.domain.Relationship;

import java.util.Set;

public class SAxiomRepresentation {

	private boolean primitive;
	private Long leftHandSideNamedConcept;
	private Set<Relationship> leftHandSideRelationships;
	private Long rightHandSideNamedConcept;
	private Set<Relationship> rightHandSideRelationships;

	public boolean isPrimitive() {
		return primitive;
	}

	public void setPrimitive(boolean primitive) {
		this.primitive = primitive;
	}

	public Long getLeftHandSideNamedConcept() {
		return leftHandSideNamedConcept;
	}

	public void setLeftHandSideNamedConcept(Long leftHandSideNamedConcept) {
		this.leftHandSideNamedConcept = leftHandSideNamedConcept;
	}

	public Set<Relationship> getLeftHandSideRelationships() {
		return leftHandSideRelationships;
	}

	public void setLeftHandSideRelationships(Set<Relationship> leftHandSideRelationships) {
		this.leftHandSideRelationships = leftHandSideRelationships;
	}

	public Long getRightHandSideNamedConcept() {
		return rightHandSideNamedConcept;
	}

	public void setRightHandSideNamedConcept(Long rightHandSideNamedConcept) {
		this.rightHandSideNamedConcept = rightHandSideNamedConcept;
	}

	public Set<Relationship> getRightHandSideRelationships() {
		return rightHandSideRelationships;
	}

	public void setRightHandSideRelationships(Set<Relationship> rightHandSideRelationships) {
		this.rightHandSideRelationships = rightHandSideRelationships;
	}
}
