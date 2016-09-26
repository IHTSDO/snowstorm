package com.kaicube.snomed.elasticsnomed.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonView;
import com.kaicube.elasticversioncontrol.domain.Component;
import com.kaicube.snomed.elasticsnomed.rest.View;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldIndex;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.util.HashSet;
import java.util.Set;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

@Document(type = "concept", indexName = "snomed")
@JsonPropertyOrder({"conceptId", "fsn", "effectiveTime", "active", "moduleId", "definitionStatusId", "descriptions", "relationships"})
public class Concept extends Component<Concept> implements ConceptView {

	@JsonView(value = View.Component.class)
	@Field(type = FieldType.String, index = FieldIndex.not_analyzed)
	@Size(min = 5, max = 18)
	private String conceptId;

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
	@NotNull
	@Size(min = 5, max = 18)
	private String definitionStatusId;

	@JsonView(value = View.Component.class)
	private Set<Description> descriptions;

	@JsonView(value = View.Component.class)
	private Set<Relationship> relationships;

	public Concept() {
		moduleId = "";
		definitionStatusId = "";
		descriptions = new HashSet<>();
		relationships = new HashSet<>();
	}

	public Concept(String conceptId) {
		this();
		this.conceptId = conceptId;
	}

	public Concept(String conceptId, String moduleId) {
		this();
		this.conceptId = conceptId;
		this.moduleId = moduleId;
	}

	public Concept(String conceptId, String effectiveTime, boolean active, String moduleId, String definitionStatusId) {
		this();
		this.conceptId = conceptId;
		this.effectiveTime = effectiveTime;
		this.active = active;
		this.moduleId = moduleId;
		this.definitionStatusId = definitionStatusId;
	}

	@Override
	public boolean isComponentChanged(Concept that) {
		return that == null
				|| active != that.active
				|| !moduleId.equals(that.moduleId)
				|| !definitionStatusId.equals(that.definitionStatusId);
	}

	@JsonView(value = View.Component.class)
	@Override
	public String getFsn() {
		for (Description description : descriptions) {
			if (description.isActive() && description.getTypeId().equals(Concepts.FSN)) {
				return description.getTerm();
			}
		}
		return null;
	}

	public Concept addDescription(Description description) {
		description.setConceptId(this.conceptId);
		descriptions.add(description);
		return this;
	}

	public Concept addRelationship(Relationship relationship) {
		relationship.setSourceId(this.conceptId);
		relationships.add(relationship);
		return this;
	}

	@Override
	public Description getDescription(String descriptionId) {
		for (Description description : descriptions) {
			if (descriptionId.equals(description.getDescriptionId())) {
				return description;
			}
		}
		return null;
	}

	@Override
	@JsonIgnore
	public String getId() {
		return conceptId;
	}

	@Override
	public String getConceptId() {
		return conceptId;
	}

	public void setConceptId(String conceptId) {
		this.conceptId = conceptId;
	}

	@Override
	public String getEffectiveTime() {
		return effectiveTime;
	}

	public void setEffectiveTime(String effectiveTime) {
		this.effectiveTime = effectiveTime;
	}

	@Override
	public boolean isActive() {
		return active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

	@Override
	public String getModuleId() {
		return moduleId;
	}

	public void setModuleId(String moduleId) {
		this.moduleId = moduleId;
	}

	@Override
	public String getDefinitionStatusId() {
		return definitionStatusId;
	}

	public void setDefinitionStatusId(String definitionStatusId) {
		this.definitionStatusId = definitionStatusId;
	}

	@Override
	public Set<Description> getDescriptions() {
		return descriptions;
	}

	public void setDescriptions(Set<Description> descriptions) {
		this.descriptions = descriptions;
	}

	@Override
	public Set<Relationship> getRelationships() {
		return relationships;
	}

	public void setRelationships(Set<Relationship> relationships) {
		this.relationships = relationships;
	}

	@Override
	public String toString() {
		return "Concept{" +
				"conceptId='" + conceptId + '\'' +
				", effectiveTime='" + effectiveTime + '\'' +
				", active=" + active +
				", moduleId='" + moduleId + '\'' +
				", definitionStatusId='" + definitionStatusId + '\'' +
				", internalId='" + getInternalId() + '\'' +
				", start='" + getStart() + '\'' +
				", end='" + getEnd() + '\'' +
				", path='" + getPath() + '\'' +
				'}';
	}
}
