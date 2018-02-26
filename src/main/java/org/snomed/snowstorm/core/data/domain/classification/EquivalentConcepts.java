package org.snomed.snowstorm.core.data.domain.classification;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.util.HashSet;
import java.util.Set;

@Document(indexName = "es-class-eq-concepts", type = "eq-concepts", shards = 8)
public class EquivalentConcepts {

	@Id
	@Field
	private String internalId;

	@Field(type = FieldType.keyword)
	private String classificationId;

	@Field(type = FieldType.keyword)
	private Set<String> conceptIds;

	public EquivalentConcepts() {
	}

	public EquivalentConcepts(String classificationId) {
		this.classificationId = classificationId;
		conceptIds = new HashSet<>();
	}

	public void addConceptId(String conceptId) {
		conceptIds.add(conceptId);
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

	public Set<String> getConceptIds() {
		return conceptIds;
	}

	public void setConceptIds(Set<String> conceptIds) {
		this.conceptIds = conceptIds;
	}
}
