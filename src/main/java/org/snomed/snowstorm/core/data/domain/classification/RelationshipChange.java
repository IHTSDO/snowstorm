package org.snomed.snowstorm.core.data.domain.classification;

import org.snomed.snowstorm.core.data.domain.ConceptMini;
import org.snomed.snowstorm.core.data.domain.Concepts;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldIndex;
import org.springframework.data.elasticsearch.annotations.FieldType;

@Document(indexName = "es-class-rel", type = "rel", shards = 8)
public class RelationshipChange {

	@Id
	@Field(index = FieldIndex.not_analyzed)
	private String internalId;

	@Field(type = FieldType.String, index = FieldIndex.not_analyzed)
	private String classificationId;

	@Field(type = FieldType.String, index = FieldIndex.not_analyzed)
	private String relationshipId;

	@Field(type = FieldType.Boolean, index = FieldIndex.not_analyzed)
	private boolean active;

	@Field(type = FieldType.String, index = FieldIndex.not_analyzed)
	private String sourceId;

	@Field(type = FieldType.String, index = FieldIndex.not_analyzed)
	private String destinationId;

	@Field(type = FieldType.String, index = FieldIndex.not_analyzed)
	private int group;

	@Field(type = FieldType.String, index = FieldIndex.not_analyzed)
	private String typeId;

	@Field(type = FieldType.String, index = FieldIndex.not_analyzed)
	private String modifierId;

	@Transient
	private ConceptMini source;

	@Transient
	private ConceptMini destination;

	@Transient
	private ConceptMini type;

	@Transient
	private ChangeNature changeNature;

	public RelationshipChange() {
	}

	public RelationshipChange(String classificationId, String relationshipId, boolean active,
							  String sourceId, String destinationId, int group,
							  String typeId, String modifierId) {
		this.classificationId = classificationId;
		this.relationshipId = relationshipId;
		this.active = active;
		this.sourceId = sourceId;
		this.destinationId = destinationId;
		this.group = group;
		this.typeId = typeId;
		this.modifierId = modifierId;
	}

	public String getCharacteristicTypeId() {
		return Concepts.INFERRED_RELATIONSHIP;
	}

	public int getUnionGroup() {
		return 0;
	}

	public String getInternalId() {
		return internalId;
	}

	public void setInternalId(String internalId) {
		this.internalId = internalId;
	}

	public String getClassificationId() {
		return classificationId;
	}

	public void setClassificationId(String classificationId) {
		this.classificationId = classificationId;
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

	public String getSourceId() {
		return sourceId;
	}

	public void setSourceId(String sourceId) {
		this.sourceId = sourceId;
	}

	public String getDestinationId() {
		return destinationId;
	}

	public void setDestinationId(String destinationId) {
		this.destinationId = destinationId;
	}

	public int getGroup() {
		return group;
	}

	public void setGroup(int group) {
		this.group = group;
	}

	public String getTypeId() {
		return typeId;
	}

	public void setTypeId(String typeId) {
		this.typeId = typeId;
	}

	public String getModifierId() {
		return modifierId;
	}

	public void setModifierId(String modifierId) {
		this.modifierId = modifierId;
	}

	public void setSource(ConceptMini source) {
		this.source = source;
	}

	public ConceptMini getSource() {
		return source;
	}

	public void setDestination(ConceptMini destination) {
		this.destination = destination;
	}

	public ConceptMini getDestination() {
		return destination;
	}

	public void setType(ConceptMini type) {
		this.type = type;
	}

	public ConceptMini getType() {
		return type;
	}

	public ChangeNature getChangeNature() {
		return changeNature;
	}

	public void setChangeNature(ChangeNature changeNature) {
		this.changeNature = changeNature;
	}

	@Override
	public String toString() {
		return "RelationshipChange{" +
				"internalId='" + internalId + '\'' +
				", classificationId='" + classificationId + '\'' +
				", relationshipId='" + relationshipId + '\'' +
				", active=" + active +
				", sourceId='" + sourceId + '\'' +
				", destinationId='" + destinationId + '\'' +
				", relationshipGroup='" + group + '\'' +
				", typeId='" + typeId + '\'' +
				", modifierId='" + modifierId + '\'' +
				'}';
	}
}
