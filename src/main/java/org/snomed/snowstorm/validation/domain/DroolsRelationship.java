package org.snomed.snowstorm.validation.domain;

import org.snomed.snowstorm.core.data.domain.Relationship;

public class DroolsRelationship implements org.ihtsdo.drools.domain.Relationship {

	private final String axiomId;
	private Relationship relationship;

	public DroolsRelationship(String axiomId, Relationship relationship) {
		this.axiomId = axiomId;
		this.relationship = relationship;
		if (relationship.getDestinationId() == null && relationship.getTarget() != null) {
			relationship.setDestinationId(relationship.getTarget().getConceptId());
		}
	}

	@Override
	public String getAxiomId() {
		return axiomId;
	}

	@Override
	public String getSourceId() {
		return relationship.getSourceId();
	}

	@Override
	public String getDestinationId() {
		return relationship.getDestinationId();
	}

	@Override
	public int getRelationshipGroup() {
		return relationship.getRelationshipGroup();
	}

	@Override
	public String getTypeId() {
		return relationship.getTypeId();
	}

	@Override
	public String getCharacteristicTypeId() {
		return relationship.getCharacteristicTypeId();
	}

	@Override
	public String getId() {
		String id = relationship.getId();
		if (id == null && axiomId != null) {
			id = String.format("%s_%s_%s_%s", axiomId, getRelationshipGroup(), getTypeId(), getDestinationId());
		}
		return id;
	}

	@Override
	public boolean isActive() {
		return relationship.isActive();
	}

	@Override
	public boolean isPublished() {
		return relationship.getEffectiveTimeI() != null;
	}

	@Override
	public boolean isReleased() {
		return relationship.isReleased();
	}

	@Override
	public String getModuleId() {
		return relationship.getModuleId();
	}
}
