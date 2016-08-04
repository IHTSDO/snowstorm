package com.kaicube.snomed.elasticsnomed.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonView;
import com.kaicube.snomed.elasticsnomed.rest.View;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldIndex;
import org.springframework.data.elasticsearch.annotations.FieldType;

@Document(type = "relationship", indexName = "snomed")
public class Relationship extends Component {

	@JsonView(value = View.Component.class)
	@Field(type = FieldType.String, index = FieldIndex.not_analyzed)
	private String relationshipId;

	@JsonView(value = View.Component.class)
	@Field(type = FieldType.String, index = FieldIndex.not_analyzed)
	private String effectiveTime;

	@JsonView(value = View.Component.class)
	@Field(type = FieldType.Boolean, index = FieldIndex.not_analyzed)
	private boolean active;

	@JsonView(value = View.Component.class)
	@Field(type = FieldType.String, index = FieldIndex.not_analyzed)
	private String moduleId;

	@JsonView(value = View.Component.class)
	@Field(type = FieldType.String, index = FieldIndex.not_analyzed)
	private String sourceId;

	@Field(type = FieldType.String, index = FieldIndex.not_analyzed)
	private String destinationId;

	@JsonView(value = View.Component.class)
	@Field(type = FieldType.Integer, index = FieldIndex.not_analyzed)
	private int relationshipGroup;

	@Field(type = FieldType.String, index = FieldIndex.not_analyzed)
	private String typeId;

	@JsonView(value = View.Component.class)
	@Field(type = FieldType.String, index = FieldIndex.not_analyzed)
	private String characteristicTypeId;

	@JsonView(value = View.Component.class)
	@Field(type = FieldType.String, index = FieldIndex.not_analyzed)
	private String modifierId;

	private ConceptMini type;

	private ConceptMini destination;

	public Relationship() {
	}

	public Relationship(String relationshipId) {
		this.relationshipId = relationshipId;
	}

	public Relationship(String id, String effectiveTime, boolean active, String moduleId, String sourceId, String destinationId, int relationshipGroup, String typeId, String characteristicTypeId, String modifierId) {
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

	@Override
	@JsonIgnore
	public String getId() {
		return relationshipId;
	}

	@JsonView(value = View.Component.class)
	public ConceptMini type() {
		return type;
	}

	public void setType(ConceptMini type) {
		this.type = type;
	}

	@JsonView(value = View.Component.class)
	public ConceptMini destination() {
		return destination;
	}

	public void setDestination(ConceptMini destination) {
		this.destination = destination;
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

	public void setSourceId(String sourceId) {
		this.sourceId = sourceId;
	}

	public String getDestinationId() {
		return destinationId;
	}

	public int getRelationshipGroup() {
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
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		Relationship that = (Relationship) o;

		return relationshipId.equals(that.relationshipId);

	}

	@Override
	public int hashCode() {
		return relationshipId.hashCode();
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
				", start='" + getStart() + '\'' +
				", end='" + getEnd() + '\'' +
				", path='" + getPath() + '\'' +
				'}';
	}
}
