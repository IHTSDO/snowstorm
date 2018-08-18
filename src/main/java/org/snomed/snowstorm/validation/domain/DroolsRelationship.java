package org.snomed.snowstorm.validation.domain;

import org.snomed.snowstorm.core.data.domain.Relationship;

public class DroolsRelationship implements org.ihtsdo.drools.domain.Relationship {

	private Relationship relationship;

	public DroolsRelationship(Relationship relationship) {
		this.relationship = relationship;
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
		return relationship.getId();
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
