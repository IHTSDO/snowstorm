package org.ihtsdo.elasticsnomed.core.data.domain.classification;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldIndex;
import org.springframework.data.elasticsearch.annotations.FieldType;

@Document(type = "relationship", indexName = "classification-rel", shards = 8)
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
	private String relationshipGroup;

	@Field(type = FieldType.String, index = FieldIndex.not_analyzed)
	private String typeId;

	@Field(type = FieldType.String, index = FieldIndex.not_analyzed)
	private String modifierId;

	public RelationshipChange() {
	}

	public RelationshipChange(String classificationId, String relationshipId, boolean active, String sourceId, String destinationId, String relationshipGroup, String typeId, String modifierId) {
		this.classificationId = classificationId;
		this.relationshipId = relationshipId;
		this.active = active;
		this.sourceId = sourceId;
		this.destinationId = destinationId;
		this.relationshipGroup = relationshipGroup;
		this.typeId = typeId;
		this.modifierId = modifierId;
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

	public String getRelationshipGroup() {
		return relationshipGroup;
	}

	public void setRelationshipGroup(String relationshipGroup) {
		this.relationshipGroup = relationshipGroup;
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

	@Override
	public String toString() {
		return "RelationshipChange{" +
				"internalId='" + internalId + '\'' +
				", classificationId='" + classificationId + '\'' +
				", relationshipId='" + relationshipId + '\'' +
				", active=" + active +
				", sourceId='" + sourceId + '\'' +
				", destinationId='" + destinationId + '\'' +
				", relationshipGroup='" + relationshipGroup + '\'' +
				", typeId='" + typeId + '\'' +
				", modifierId='" + modifierId + '\'' +
				'}';
	}
}
