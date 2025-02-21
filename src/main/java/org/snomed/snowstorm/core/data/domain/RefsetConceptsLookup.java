package org.snomed.snowstorm.core.data.domain;

import io.kaicode.elasticvc.domain.DomainEntity;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.util.Objects;
import java.util.Set;

@Document(indexName = "#{@indexNameProvider.indexName('refset-concepts-lookup')}", createIndex = false)
public class RefsetConceptsLookup extends DomainEntity<RefsetConceptsLookup> {
    @Field(type = FieldType.Keyword)
    private String refsetId;

    @Field(type = FieldType.Long)
    private Set<Long> conceptIds;

    @Field(type = FieldType.Integer)
    private Integer total;

    @Field(type = FieldType.Keyword)
    private Type type;

    public interface Fields {
        String CONCEPT_IDS = "conceptIds";
        String REFSETID = "refsetId";
    }

    public enum Type {
        INCLUDE,
        EXCLUDE
    }

    public RefsetConceptsLookup() {
        // Default constructor for serialization
    }

    public RefsetConceptsLookup(String refsetId, Set<Long> conceptIds, Type type) {
        this.refsetId = refsetId;
        this.conceptIds = conceptIds;
        this.type = type;
        this.total = conceptIds.size();
    }

    public String getRefsetId() {
        return refsetId;
    }

    public void setRefsetId(String refsetId) {
        this.refsetId = refsetId;
    }

    public Set<Long> getConceptIds() {
        return conceptIds;
    }

    public void setConceptIds(Set<Long> conceptIds) {
        this.conceptIds = conceptIds;
    }

    public Integer getTotal() {
        return total;
    }

    public void setTotal(Integer total) {
        this.total = total;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    @Override
    public String getId() {
        return super.getInternalId();
    }

    @Override
    public boolean isComponentChanged(RefsetConceptsLookup existingComponent) {
        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RefsetConceptsLookup that)) return false;
        return Objects.equals(refsetId, that.refsetId) && type == that.type && Objects.equals(getInternalId(), that.getInternalId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(refsetId, type, getInternalId());
    }
}
