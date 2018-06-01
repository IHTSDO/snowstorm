package org.snomed.snowstorm.core.data.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonView;
import org.snomed.snowstorm.rest.View;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import static org.snomed.snowstorm.core.data.domain.Concepts.relationshipCharacteristicTypeNames;
import static org.snomed.snowstorm.core.data.domain.Concepts.relationshipModifierNames;

@Document(indexName = "es-rel", type = "relationship", shards = 8)
public class Relationship extends SnomedComponent<Relationship> {

	public enum CharacteristicType {

		inferred(Concepts.INFERRED_RELATIONSHIP),
		stated(Concepts.STATED_RELATIONSHIP),
		additional(Concepts.ADDITIONAL_RELATIONSHIP);

		String conceptId;

		CharacteristicType(String conceptId) {
			this.conceptId = conceptId;
		}

		public String getConceptId() {
			return conceptId;
		}

	}
	public interface Fields extends SnomedComponent.Fields {

		String RELATIONSHIP_ID = "relationshipId";
		String SOURCE_ID = "sourceId";
		String DESTINATION_ID = "destinationId";
		String RELATIONSHIP_GROUP = "relationshipGroup";
		String TYPE_ID = "typeId";
		String CHARACTERISTIC_TYPE_ID = "characteristicTypeId";
		String MODIFIER_ID = "modifierId";
	}
	@JsonView(value = View.Component.class)
	@Field(type = FieldType.keyword)
	private String relationshipId;

	@JsonView(value = View.Component.class)
	@Field(type = FieldType.keyword)
	@NotNull
	@Size(min = 5, max = 18)
	private String moduleId;

	@JsonView(value = View.Component.class)
	@Field(type = FieldType.keyword, store = true)
	private String sourceId;

	@JsonView(value = View.Component.class)
	@Field(type = FieldType.keyword)
	@NotNull
	@Size(min = 5, max = 18)
	private String destinationId;

	@Field(type = FieldType.Integer)
	private int relationshipGroup;

	@JsonView(value = View.Component.class)
	@Field(type = FieldType.keyword)
	@NotNull
	@Size(min = 5, max = 18)
	private String typeId;

	@Field(type = FieldType.keyword)
	@NotNull
	@Size(min = 5, max = 18)
	private String characteristicTypeId;

	@Field(type = FieldType.keyword)
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

	public ConceptMini getSource() {
		return source;
	}

	public ConceptMini getType() {
		return type;
	}

	public ConceptMini getTarget() {
		return target;
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

	public Relationship setGroupId(int groupId) {
		this.relationshipGroup = groupId;
		return this;
	}

	@JsonView(value = View.Component.class)
	public String getCharacteristicType() {
		return relationshipCharacteristicTypeNames.get(characteristicTypeId);
	}

	public void setCharacteristicType(String characteristicTypeName) {
		characteristicTypeId = relationshipCharacteristicTypeNames.inverse().get(characteristicTypeName);
	}

	public Relationship setCharacteristicTypeId(String characteristicTypeId) {
		this.characteristicTypeId = characteristicTypeId;
		return this;
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

	public String getModuleId() {
		return moduleId;
	}

	public void setModuleId(String moduleId) {
		this.moduleId = moduleId;
	}

	public String getSourceId() {
		return sourceId;
	}

	public Relationship setSourceId(String sourceId) {
		this.sourceId = sourceId;
		return this;
	}

	public String getDestinationId() {
		return destinationId;
	}

	public void setDestinationId(String destinationId) {
		this.destinationId = destinationId;
	}

	public int getRelationshipGroup() {
		return relationshipGroup;
	}

	public void setRelationshipGroup(int relationshipGroup) {
		this.relationshipGroup = relationshipGroup;
	}

	public String getTypeId() {
		return typeId;
	}

	public void setTypeId(String typeId) {
		this.typeId = typeId;
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

		if (relationshipId != null && relationshipId.equals(that.relationshipId)) {
			return true;
		}

		if (relationshipGroup != that.relationshipGroup) return false;
		if (sourceId != null ? !sourceId.equals(that.sourceId) : that.sourceId != null) return false;
		if (destinationId != null ? !destinationId.equals(that.destinationId) : that.destinationId != null) return false;
		if (typeId != null ? !typeId.equals(that.typeId) : that.typeId != null) return false;
		return characteristicTypeId != null ? characteristicTypeId.equals(that.characteristicTypeId) : that.characteristicTypeId == null;
	}

	@Override
	public int hashCode() {
		int result = relationshipId != null ? relationshipId.hashCode() : 0;
		if (result != 0) {
			return result;
		}
		result = 31 * result + (sourceId != null ? sourceId.hashCode() : 0);
		result = 31 * result + (destinationId != null ? destinationId.hashCode() : 0);
		result = 31 * result + relationshipGroup;
		result = 31 * result + (typeId != null ? typeId.hashCode() : 0);
		result = 31 * result + (characteristicTypeId != null ? characteristicTypeId.hashCode() : 0);
		return result;
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
