package com.kaicube.snomed.elasticsnomed.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonView;
import com.kaicube.elasticversioncontrol.domain.Component;
import com.kaicube.snomed.elasticsnomed.rest.View;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldIndex;
import org.springframework.data.elasticsearch.annotations.FieldType;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import static com.kaicube.snomed.elasticsnomed.domain.Concepts.*;

@Document(type = "relationship", indexName = "snomed")
public class Relationship extends Component<Relationship> {

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
	@NotNull
	@Size(min = 5, max = 18)
	private String moduleId;

	@JsonView(value = View.Component.class)
	@Field(type = FieldType.String, index = FieldIndex.not_analyzed)
	private String sourceId;

	@Field(type = FieldType.String, index = FieldIndex.not_analyzed)
	@NotNull
	@Size(min = 5, max = 18)
	private String destinationId;

	@Field(type = FieldType.Integer, index = FieldIndex.not_analyzed)
	private int relationshipGroup;

	@Field(type = FieldType.String, index = FieldIndex.not_analyzed)
	@NotNull
	@Size(min = 5, max = 18)
	private String typeId;

	@Field(type = FieldType.String, index = FieldIndex.not_analyzed)
	@NotNull
	@Size(min = 5, max = 18)
	private String characteristicTypeId;

	@Field(type = FieldType.String, index = FieldIndex.not_analyzed)
	@NotNull
	@Size(min = 5, max = 18)
	private String modifierId;

	private ConceptMini type;

	private ConceptMini target;

	private static final Logger logger = LoggerFactory.getLogger(Relationship.class);

	public Relationship() {
		moduleId = "";
		destinationId = "";
		typeId = "";
		characteristicTypeId = "";
		modifierId = "";
	}

	public Relationship(String relationshipId) {
		this();
		this.relationshipId = relationshipId;
	}

	public Relationship(String id, String effectiveTime, boolean active, String moduleId, String sourceId, String destinationId, int relationshipGroup, String typeId, String characteristicTypeId, String modifierId) {
		this();
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
	public boolean isComponentChanged(Relationship that) {
		final boolean changed = that == null
				|| active != that.active
				|| !moduleId.equals(that.moduleId)
				|| !destinationId.equals(that.destinationId)
				|| relationshipGroup != that.relationshipGroup
				|| !typeId.equals(that.typeId)
				|| !characteristicTypeId.equals(that.characteristicTypeId)
				|| !modifierId.equals(that.modifierId);
		if (changed) logger.debug("Relationship changed:\n{}\n{}", this, that);
		return changed;
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
		this.typeId = type == null ? null : type.getConceptId();
	}

	@JsonView(value = View.Component.class)
	public ConceptMini target() {
		return target;
	}

	public void setTarget(ConceptMini target) {
		this.target = target;
		this.destinationId = target == null ? null : target.getConceptId();
	}

	@JsonView(value = View.Component.class)
	public int getGroupId() {
		return relationshipGroup;
	}

	public void setGroupId(int groupId) {
		this.relationshipGroup = groupId;
	}

	@JsonView(value = View.Component.class)
	public String getCharacteristicType() {
		return relationshipCharacteristicTypeNames.get(characteristicTypeId);
	}

	public void setCharacteristicType(String characteristicTypeName) {
		characteristicTypeId = relationshipCharacteristicTypeNames.inverse().get(characteristicTypeName);
	}

	@JsonView(value = View.Component.class)
	public String getModifier() {
		return relationshipModifierNames.get(modifierId);
	}

	public void setModifier(String modifierName) {
		modifierId = relationshipModifierNames.inverse().get(modifierName);
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
