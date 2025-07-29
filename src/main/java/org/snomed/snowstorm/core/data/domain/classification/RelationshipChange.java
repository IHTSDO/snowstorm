package org.snomed.snowstorm.core.data.domain.classification;

import org.snomed.snowstorm.core.data.domain.ConceptMini;
import org.snomed.snowstorm.core.data.domain.Concepts;
import org.snomed.snowstorm.core.data.domain.ConcreteValue;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.io.Serializable;
import java.util.UUID;

@Document(indexName = "#{@indexNameProvider.indexName('classification-relationship-change')}", createIndex = false)
public class RelationshipChange {

	public interface Fields {
		String SOURCE_ID = "sourceId";
		String INTERNAL_ID = "internalId";
	}

	@Id
	@Field(type = FieldType.Keyword)
	private String internalId;

	@Field(type = FieldType.Keyword)
	private String classificationId;

	@Field(type = FieldType.Keyword)
	private String relationshipId;

	@Field(type = FieldType.Boolean)
	private boolean active;

	@Field(type = FieldType.Keyword)
	private String sourceId;

	@Field(type = FieldType.Keyword)
	private String destinationId;

	@Field(type = FieldType.Keyword)
	private String value;

	@Field(type = FieldType.Keyword)
	private int group;

	@Field(type = FieldType.Keyword)
	private String typeId;

	@Field(type = FieldType.Keyword)
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

	public RelationshipChange(String classificationId, String relationshipId, boolean active,
							  String sourceId, String destinationIdOrValue, int group,
							  String typeId, String modifierId, boolean concrete) {
		this.classificationId = classificationId;
		this.relationshipId = relationshipId;
		this.active = active;
		this.sourceId = sourceId;
		if (concrete) {
			this.value = destinationIdOrValue;
		} else {
			this.destinationId = destinationIdOrValue;
		}
		this.group = group;
		this.typeId = typeId;
		this.modifierId = modifierId;
		this.internalId = UUID.randomUUID().toString();
	}

	public boolean isConcrete() {
		return this.value != null;
	}

	public String getDestinationOrValue() {
		return destinationId != null ? destinationId : value;
	}

	public String getDestinationOrValueWithoutPrefix() {
	    if (destinationId != null) {
	        return destinationId;
        } else if (value != null) {
            return ConcreteValue.removeConcretePrefix(value);
        }
		return null;
	}

	public Serializable getDestinationOrRawValue() {
	    if (destinationId != null) {
	        return destinationId;
        } else if (value != null) {
            final String valueRaw = ConcreteValue.removeConcretePrefix(this.value);
            if (value.startsWith("#")) {
                if (valueRaw.contains(".")) {
                    return Float.parseFloat(valueRaw);
                }
                return Integer.parseInt(valueRaw);
            }
            return value;
        }
		return null;
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

	public String getValue() {
		return value;
	}

	public void setValue(String value) {
		this.value = value;
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
				", classificationId='" + classificationId + '\'' +
				", relationshipId='" + relationshipId + '\'' +
				", active=" + active +
				", sourceId='" + sourceId + '\'' +
				", destinationId='" + destinationId + '\'' +
				", value='" + value + '\'' +
				", relationshipGroup='" + group + '\'' +
				", typeId='" + typeId + '\'' +
				", modifierId='" + modifierId + '\'' +
				", notStated='" + inferredNotStated + '\'' +
				'}';
	}
}
