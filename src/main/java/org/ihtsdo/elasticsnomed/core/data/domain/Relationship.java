package org.ihtsdo.elasticsnomed.core.data.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonView;
import org.ihtsdo.elasticsnomed.rest.View;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldIndex;
import org.springframework.data.elasticsearch.annotations.FieldType;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import static org.ihtsdo.elasticsnomed.core.data.domain.Concepts.relationshipCharacteristicTypeNames;
import static org.ihtsdo.elasticsnomed.core.data.domain.Concepts.relationshipModifierNames;

@Document(type = "relationship", indexName = "snomed", shards = 8)
public class Relationship extends SnomedComponent<Relationship> {

	public enum CharacteristicType {

		inferred(Concepts.INFERRED_RELATIONSHIP), stated(Concepts.STATED_RELATIONSHIP);

		String conceptId;

		CharacteristicType(String conceptId) {
			this.conceptId = conceptId;
		}

		public String getConceptId() {
			return conceptId;
		}
	}

	@JsonView(value = View.Component.class)
	@Field(type = FieldType.String, index = FieldIndex.not_analyzed)
	private String relationshipId;

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

	@JsonView(value = View.Component.class)
	@Field(type = FieldType.String, index = FieldIndex.not_analyzed)
	@NotNull
	@Size(min = 5, max = 18)
	private String destinationId;

	@Field(type = FieldType.Integer, index = FieldIndex.not_analyzed)
	private int relationshipGroup;

	@JsonView(value = View.Component.class)
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

	private ConceptMini source;

	private ConceptMini type;

	private ConceptMini target;

	public Relationship() {
		active = true;
		moduleId = "";
		destinationId = "";
		typeId = "";
		characteristicTypeId = Concepts.STATED_RELATIONSHIP;
		modifierId = "";
	}

	public Relationship(String relationshipId) {
		this();
		this.relationshipId = relationshipId;
	}

	public Relationship(String typeId, String destinationId) {
		this();
		this.typeId = typeId;
		this.destinationId = destinationId;
	}

	public Relationship(String relationshipId, String typeId, String destinationId) {
		this(relationshipId);
		this.typeId = typeId;
		this.destinationId = destinationId;
	}

	public Relationship(String id, String effectiveTime, boolean active, String moduleId, String sourceId, String destinationId, int relationshipGroup, String typeId, String characteristicTypeId, String modifierId) {
		this();
		this.relationshipId = id;
		setEffectiveTime(effectiveTime);
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
		return that == null
				|| active != that.active
				|| !moduleId.equals(that.moduleId)
				|| !destinationId.equals(that.destinationId)
				|| relationshipGroup != that.relationshipGroup
				|| !typeId.equals(that.typeId)
				|| !characteristicTypeId.equals(that.characteristicTypeId)
				|| !modifierId.equals(that.modifierId);
	}

	@Override
	protected Object[] getReleaseHashObjects() {
		return new Object[] {active, moduleId, destinationId, relationshipGroup, typeId, characteristicTypeId, modifierId};
	}

	@Override
	@JsonIgnore
	public String getId() {
		return relationshipId;
	}

	@JsonView(value = View.Component.class)
	@JsonProperty("id")
	public String getRelId() {
		return relationshipId;
	}

	@JsonView(value = View.Component.class)
	public ConceptMini source() {
		return source;
	}

	public void setSource(ConceptMini source) {
		this.source = source;
		this.sourceId = source == null ? null : source.getConceptId();
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

	public void setRelationshipId(String relationshipId) {
		this.relationshipId = relationshipId;
	}

	public boolean isActive() {
		return active;
	}

	public void setActive(boolean active) {
		this.active = active;
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

		return relationshipId != null ? relationshipId.equals(that.relationshipId) : that.relationshipId == null;
	}

	@Override
	public int hashCode() {
		return relationshipId != null ? relationshipId.hashCode() : 0;
	}

	@Override
	public String toString() {
		return "Relationship{" +
				"relationshipId='" + relationshipId + '\'' +
				", effectiveTime='" + getEffectiveTime() + '\'' +
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
