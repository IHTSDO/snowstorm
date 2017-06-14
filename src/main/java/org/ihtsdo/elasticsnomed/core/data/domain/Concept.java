package org.ihtsdo.elasticsnomed.core.data.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonView;
import org.ihtsdo.elasticsnomed.rest.View;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldIndex;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

@Document(type = "concept", indexName = "snomed", shards = 8)
@JsonPropertyOrder({"conceptId", "fsn", "effectiveTime", "active", "inactivationIndicator", "moduleId", "definitionStatus", "definitionStatusId", "descriptions", "relationships"})
public class Concept extends SnomedComponent<Concept> implements ConceptView, SnomedComponentWithInactivationIndicator {

	@JsonView(value = View.Component.class)
	@Field(type = FieldType.String, index = FieldIndex.not_analyzed)
	@Size(min = 5, max = 18)
	private String conceptId;

	@JsonView(value = View.Component.class)
	@Field(type = FieldType.Boolean, index = FieldIndex.not_analyzed)
	private boolean active;

	@JsonIgnore
	private ReferenceSetMember inactivationIndicatorMember;

	@JsonIgnore
	// Populated when requesting an update
	private String inactivationIndicatorName;

	@JsonIgnore
	private Set<ReferenceSetMember> associationTargetMembers;

	@JsonIgnore
	// Populated when requesting an update
	private Map<String, Set<String>> associationTargetStrings;

	@JsonView(value = View.Component.class)
	@Field(type = FieldType.String, index = FieldIndex.not_analyzed)
	@NotNull
	@Size(min = 5, max = 18)
	private String moduleId;

	@Field(type = FieldType.String, index = FieldIndex.not_analyzed)
	@NotNull
	@Size(min = 5, max = 18)
	private String definitionStatusId;

	@JsonView(value = View.Component.class)
	private Set<Description> descriptions;

	@JsonView(value = View.Component.class)
	private Set<Relationship> relationships;

	public Concept() {
		active = true;
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
		setEffectiveTime(effectiveTime);
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

	@Override
	protected Object[] getReleaseHashObjects() {
		return new Object[]{active, moduleId, definitionStatusId};
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

	@JsonView(value = View.Component.class)
	public String getInactivationIndicator() {
		if (inactivationIndicatorMember != null && inactivationIndicatorMember.isActive()) {
			return Concepts.inactivationIndicatorNames.get(inactivationIndicatorMember.getAdditionalField("valueId"));
		}
		return inactivationIndicatorName;
	}

	public void setInactivationIndicatorName(String inactivationIndicatorName) {
		this.inactivationIndicatorName = inactivationIndicatorName;
	}

	public void addAssociationTargetMember(ReferenceSetMember member) {
		if (associationTargetMembers == null) {
			associationTargetMembers = new HashSet<>();
		}
		associationTargetMembers.add(member);
	}

	@JsonView(value = View.Component.class)
	public Map<String, Set<String>> getAssociationTargets() {
		if (associationTargetMembers != null) {
			Map<String, Set<String>> map = new HashMap<>();
			associationTargetMembers.stream().filter(ReferenceSetMember::isActive).forEach(member -> {
				final String refsetId = member.getRefsetId();
				String association = Concepts.historicalAssociationNames.get(refsetId);
				if (association == null) {
					association = refsetId;
				}
				Set<String> associationType = map.computeIfAbsent(association, k -> new HashSet<>());
				associationType.add(member.getAdditionalField("targetComponentId"));
			});
			return map;
		}
		return associationTargetStrings;
	}

	public void setAssociationTargets(Map<String, Set<String>> associationTargetStrings) {
		this.associationTargetStrings = associationTargetStrings;
	}

	public Set<ReferenceSetMember> getAssociationTargetMembers() {
		return associationTargetMembers;
	}

	public void setAssociationTargetMembers(Set<ReferenceSetMember> associationTargetMembers) {
		this.associationTargetMembers = associationTargetMembers;
	}

	@JsonView(value = View.Component.class)
	public String getDefinitionStatus() {
		return Concepts.definitionStatusNames.get(definitionStatusId);
	}

	public void setDefinitionStatus(String definitionStatusName) {
		definitionStatusId = Concepts.definitionStatusNames.inverse().get(definitionStatusName);
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

	@JsonIgnore
	public Long getConceptIdAsLong() {
		return conceptId == null ? null : Long.parseLong(conceptId);
	}

	public void setConceptId(String conceptId) {
		this.conceptId = conceptId;
	}

	@Override
	public boolean isActive() {
		return active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

	@JsonIgnore
	public ReferenceSetMember getInactivationIndicatorMember() {
		return inactivationIndicatorMember;
	}

	public void setInactivationIndicatorMember(ReferenceSetMember inactivationIndicatorMember) {
		this.inactivationIndicatorMember = inactivationIndicatorMember;
	}

	public Set<Relationship> getRelationshipsWithDestination(String conceptId) {
		return relationships.stream().filter(r -> conceptId.equals(r.getDestinationId())).collect(Collectors.toSet());
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

	public Concept setDefinitionStatusId(String definitionStatusId) {
		this.definitionStatusId = definitionStatusId;
		return this;
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
				", effectiveTime='" + getEffectiveTime() + '\'' +
				", active=" + active +
				", moduleId='" + moduleId + '\'' +
				", definitionStatusId='" + definitionStatusId + '\'' +
				", internalId='" + getInternalId() + '\'' +
				", start='" + getStartDebugFormat() + '\'' +
				", end='" + getEndDebugFormat() + '\'' +
				", path='" + getPath() + '\'' +
				'}';
	}
}
