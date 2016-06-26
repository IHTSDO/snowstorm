package com.kaicube.snomed.elasticsnomed.domain;

import org.springframework.data.elasticsearch.annotations.Document;

@Document(type = "relationship", indexName = "snomed")
public class Relationship extends Component {

	private String relationshipId;
	private String effectiveTime;
	private boolean active;
	private String moduleId;
	private String sourceId;
	private String destinationId;
	private String relationshipGroup;
	private String typeId;
	private String characteristicTypeId;
	private String modifierId;

	public Relationship() {
	}

	public Relationship(String id, String effectiveTime, boolean active, String moduleId, String sourceId, String destinationId, String relationshipGroup, String typeId, String characteristicTypeId, String modifierId) {
		this.relationshipId = id;
		this.effectiveTime = effectiveTime;
		this.active = active;
		this.moduleId = moduleId;
		this.sourceId = sourceId;
		this.destinationId = destinationId;
		this.relationshipGroup = relationshipGroup;
		this.typeId = typeId;
		this.characteristicTypeId = characteristicTypeId;
		this.modifierId = modifierId;
	}

	public String getRelationshipId() {
		return relationshipId;
	}

	public String getEffectiveTime() {
		return effectiveTime;
	}

	public boolean isActive() {
		return active;
	}

	public String getModuleId() {
		return moduleId;
	}

	public String getSourceId() {
		return sourceId;
	}

	public String getDestinationId() {
		return destinationId;
	}

	public String getRelationshipGroup() {
		return relationshipGroup;
	}

	public String getTypeId() {
		return typeId;
	}

	public String getCharacteristicTypeId() {
		return characteristicTypeId;
	}

	public String getModifierId() {
		return modifierId;
	}

	@Override
	public String toString() {
		return "Relationship{" +
				"relationshipId='" + relationshipId + '\'' +
				", effectiveTime='" + effectiveTime + '\'' +
				", active=" + active +
				", moduleId='" + moduleId + '\'' +
				", sourceId='" + sourceId + '\'' +
				", destinationId='" + destinationId + '\'' +
				", relationshipGroup='" + relationshipGroup + '\'' +
				", typeId='" + typeId + '\'' +
				", characteristicTypeId='" + characteristicTypeId + '\'' +
				", modifierId='" + modifierId + '\'' +
				", uuid='" + getUuid() + '\'' +
				", commit='" + getCommit() + '\'' +
				", path='" + getPath() + '\'' +
				'}';
	}
}
