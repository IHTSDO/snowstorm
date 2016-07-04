package com.kaicube.snomed.elasticsnomed.domain;

import com.fasterxml.jackson.annotation.JsonView;
import com.kaicube.snomed.elasticsnomed.rest.View;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldIndex;

@Document(type = "relationship", indexName = "snomed")
public class Relationship extends Component {

	@JsonView(value = View.Component.class)
	@Field(index = FieldIndex.not_analyzed)
	private String relationshipId;

	@JsonView(value = View.Component.class)
	@Field(index = FieldIndex.not_analyzed)
	private String effectiveTime;

	@JsonView(value = View.Component.class)
	private boolean active;

	@JsonView(value = View.Component.class)
	@Field(index = FieldIndex.not_analyzed)
	private String moduleId;

	@JsonView(value = View.Component.class)
	@Field(index = FieldIndex.not_analyzed)
	private String sourceId;

	@JsonView(value = View.Component.class)
	@Field(index = FieldIndex.not_analyzed)
	private String destinationId;

	@JsonView(value = View.Component.class)
	@Field(index = FieldIndex.not_analyzed)
	private String relationshipGroup;

	@JsonView(value = View.Component.class)
	@Field(index = FieldIndex.not_analyzed)
	private String typeId;

	@JsonView(value = View.Component.class)
	@Field(index = FieldIndex.not_analyzed)
	private String characteristicTypeId;

	@JsonView(value = View.Component.class)
	@Field(index = FieldIndex.not_analyzed)
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
				", internalId='" + getInternalId() + '\'' +
				", commit='" + getCommit() + '\'' +
				", path='" + getPath() + '\'' +
				'}';
	}
}
