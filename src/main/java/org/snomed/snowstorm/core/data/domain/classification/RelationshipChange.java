package org.snomed.snowstorm.core.data.domain.classification;

import org.snomed.snowstorm.core.data.domain.ConceptMini;
import org.snomed.snowstorm.core.data.domain.Concepts;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

@Document(indexName = "classification-relationship-change")
public class RelationshipChange {

	public interface Fields {
		String SORT_NUMBER = "sortNumber";
		String SOURCE_ID = "sourceId";
		String GROUP = "group";
	}

	@Id
	@Field
	private String internalId;

	@Field(type = FieldType.Integer)
	private int sortNumber;

	@Field(type = FieldType.keyword)
	private String classificationId;

	@Field(type = FieldType.keyword)
	private String relationshipId;

	@Field(type = FieldType.Boolean)
	private boolean active;

	@Field(type = FieldType.keyword)
	private String sourceId;

	@Field(type = FieldType.keyword)
	private String destinationId;

	@Field(type = FieldType.keyword)
	private int group;

	@Field(type = FieldType.keyword)
	private String typeId;

	@Field(type = FieldType.keyword)
	private String modifierId;

	@Field(type = FieldType.Boolean)
	private boolean inferredNotStated;

	@Transient
	private ConceptMini source;

	@Transient
	private ConceptMini destination;

	@Transient
	private ConceptMini type;

	public RelationshipChange() {
	}

	public RelationshipChange(int sortNumber, String classificationId, String relationshipId, boolean active,
							  String sourceId, String destinationId, int group,
							  String typeId, String modifierId, boolean inferredNotStated) {
		this.sortNumber = sortNumber;
		this.classificationId = classificationId;
		this.relationshipId = relationshipId;
		this.active = active;
		this.sourceId = sourceId;
		this.destinationId = destinationId;
		this.group = group;
		this.typeId = typeId;
		this.modifierId = modifierId;
		this.inferredNotStated = inferredNotStated;
	}

	public String getSourceFsn() {
		return source != null ? source.getFsnTerm() : null;
	}

	public String getTypeFsn() {
		return type != null ? type.getFsnTerm() : null;
	}

	public String getDestinationFsn() {
		return destination != null ? destination.getFsnTerm() : null;
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

	public int getSortNumber() {
		return sortNumber;
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

	public boolean isInferredNotStated() {
		return inferredNotStated;
	}

	public void setInferredNotStated(boolean inferredNotStated) {
		this.inferredNotStated = inferredNotStated;
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
		return active ? ChangeNature.INFERRED : ChangeNature.REDUNDANT;
	}

	@Override
	public String toString() {
		return "RelationshipChange{" +
				"internalId='" + internalId + '\'' +
				", sortNumber='" + sortNumber + '\'' +
				", classificationId='" + classificationId + '\'' +
				", relationshipId='" + relationshipId + '\'' +
				", active=" + active +
				", sourceId='" + sourceId + '\'' +
				", destinationId='" + destinationId + '\'' +
				", relationshipGroup='" + group + '\'' +
				", typeId='" + typeId + '\'' +
				", modifierId='" + modifierId + '\'' +
				", notStated='" + inferredNotStated + '\'' +
				'}';
	}
}
