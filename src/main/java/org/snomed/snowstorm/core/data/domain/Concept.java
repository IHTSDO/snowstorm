package org.snomed.snowstorm.core.data.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonView;
import com.google.common.collect.Sets;
import org.snomed.snowstorm.core.pojo.TermLangPojo;
import org.snomed.snowstorm.core.util.DescriptionHelper;
import org.snomed.snowstorm.rest.View;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.*;
import java.util.stream.Collectors;

import static org.snomed.snowstorm.core.util.DescriptionHelper.EN_LANGUAGE_CODE;

@Document(indexName = "concept")
@JsonPropertyOrder({"conceptId", "fsn", "active", "effectiveTime", "released", "releasedEffectiveTime",  "inactivationIndicator", "associationTargets",
		"moduleId", "definitionStatus", "definitionStatusId", "descriptions", "classAxioms", "gciAxioms", "relationships"})
public class Concept extends SnomedComponent<Concept> implements ConceptView, SnomedComponentWithInactivationIndicator, SnomedComponentWithAssociations {

	public interface Fields extends SnomedComponent.Fields {

		String CONCEPT_ID = "conceptId";
		String MODULE_ID = "moduleId";
		String DEFINITION_STATUS_ID = "definitionStatusId";
	}
	@JsonView(value = View.Component.class)
	@Field(type = FieldType.keyword, store = true)
	@Size(min = 5, max = 18)
	private String conceptId;

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
	@Field(type = FieldType.keyword)
	@NotNull
	@Size(min = 5, max = 18)
	private String moduleId;

	@Field(type = FieldType.keyword)
	@NotNull
	@Size(min = 5, max = 18)
	private String definitionStatusId;

	@Valid
	private Set<Description> descriptions;

	@Valid
	private Set<Relationship> relationships;

	private Set<Axiom> classAxioms;

	private Set<Axiom> generalConceptInclusionAxioms;

	public Concept() {
		active = true;
		moduleId = Concepts.CORE_MODULE;
		definitionStatusId = Concepts.PRIMITIVE;
		descriptions = new HashSet<>();
		relationships = new HashSet<>();
		classAxioms = new HashSet<>();
		generalConceptInclusionAxioms = new HashSet<>();
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

	public Concept(String conceptId, Integer effectiveTime, boolean active, String moduleId, String definitionStatusId) {
		this();
		this.conceptId = conceptId;
		setEffectiveTimeI(effectiveTime);
		this.active = active;
		this.moduleId = moduleId;
		this.definitionStatusId = definitionStatusId;
	}

	@Override
	public String getIdField() {
		return Fields.CONCEPT_ID;
	}

	@Override
	public boolean isComponentChanged(Concept that) {
		return that == null
				|| active != that.active
				|| !moduleId.equals(that.moduleId)
				|| !definitionStatusId.equals(that.definitionStatusId);
	}

	public boolean isPrimitive() {
		return definitionStatusId.equals(Concepts.PRIMITIVE);
	}

	@Override
	protected Object[] getReleaseHashObjects() {
		return new Object[]{active, moduleId, definitionStatusId};
	}

	@JsonView(value = View.Component.class)
	@Override
	public TermLangPojo getFsn() {
		return DescriptionHelper.getFsnDescriptionTermAndLang(descriptions, EN_LANGUAGE_CODE);
	}

	@JsonView(value = View.Component.class)
	@Override
	public TermLangPojo getPt() {
		return DescriptionHelper.getFsnDescriptionTermAndLang(descriptions, EN_LANGUAGE_CODE);
	}

	@JsonView(value = View.Component.class)
	public String getInactivationIndicator() {
		if (inactivationIndicatorMember != null && inactivationIndicatorMember.isActive()) {
			return Concepts.inactivationIndicatorNames.get(inactivationIndicatorMember.getAdditionalField("valueId"));
		}
		return inactivationIndicatorName;
	}

	public Concept setInactivationIndicator(String inactivationIndicatorName) {
		this.inactivationIndicatorName = inactivationIndicatorName;
		return this;
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

	@JsonView(value = View.Component.class)
	public String getDefinitionStatus() {
		return Concepts.definitionStatusNames.get(definitionStatusId);
	}

	public void setDefinitionStatus(String definitionStatusName) {
		definitionStatusId = Concepts.definitionStatusNames.inverse().get(definitionStatusName);
	}

	public Concept addFSN(String term) {
		addDescription(new Description(term).setTypeId(Concepts.FSN));
		return this;
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

	public Concept addAxiom(Axiom axiom) {
		axiom.getRelationships().forEach(r -> r.setSourceId(this.conceptId));
		classAxioms.add(axiom);
		return this;
	}

	public Concept addAxiom(Relationship... axiomFragments) {
		Axiom axiom = new Axiom(moduleId, true, Concepts.PRIMITIVE, Sets.newHashSet(axiomFragments));
		axiom.getRelationships().forEach(r -> r.setSourceId(this.conceptId));
		classAxioms.add(axiom);
		return this;
	}

	public Concept addGeneralConceptInclusionAxiom(Axiom axiom) {
		axiom.getRelationships().forEach(r -> r.setSourceId(this.conceptId));
		generalConceptInclusionAxioms.add(axiom);
		return this;
	}

	public Concept addGeneralConceptInclusionAxiom(Relationship... axiomFragments) {
		Axiom axiom = new Axiom(moduleId, true, Concepts.PRIMITIVE, Sets.newHashSet(axiomFragments));
		axiom.getRelationships().forEach(r -> r.setSourceId(this.conceptId));
		generalConceptInclusionAxioms.add(axiom);
		return this;
	}

	public Set<ReferenceSetMember> getAllOwlAxiomMembers() {
		Set<ReferenceSetMember> members = classAxioms.stream().map(Axiom::getReferenceSetMember).collect(Collectors.toSet());
		members.addAll(generalConceptInclusionAxioms.stream().map(Axiom::getReferenceSetMember).collect(Collectors.toSet()));
		return members;
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

	/**
	 * TODO pass acceptability as an ordered list (by preference)
	 * @param activeFlag 1 or 0 or pass null to obtain either
	 * @param type - the SCTID for fsn, synonym or textDefn, or pass null to obtain either
	 * @param acceptability - the SCTID for acceptable or preferred, or pass null to ignore acceptability
	 * @param refsetId - the SCTID of the language refset for which the acceptability must apply.  Ignored if the acceptability is null.
	 * @return a collection of descriptions that match the specified criteria
	 */
	public List<Description> getDescriptions(Boolean activeFlag, String type, String acceptability, String refsetId) {
		List<Description> matchingDescriptions = descriptions.stream()
							.filter(desc -> (activeFlag == null || activeFlag.equals(desc.isActive())))
							.filter(desc -> (type == null || desc.getType().equals(type)))
							.filter(desc -> (acceptability == null || desc.hasAcceptability(acceptability, refsetId)))
							.collect(Collectors.toList());
		return matchingDescriptions;
	}

	public Relationship getRelationship(String relationshipId) {
		for (Relationship relationship : relationships) {
			if (relationshipId.equals(relationship.getRelationshipId())) {
				return relationship;
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

	@JsonIgnore
	public ReferenceSetMember getInactivationIndicatorMember() {
		return inactivationIndicatorMember;
	}

	public void setInactivationIndicatorMember(ReferenceSetMember inactivationIndicatorMember) {
		this.inactivationIndicatorMember = inactivationIndicatorMember;
	}

	public Set<Relationship> getRelationshipsWithDestination(String destinationId) {
		return relationships.stream().filter(r -> destinationId.equals(r.getDestinationId())).collect(Collectors.toSet());
	}
	
	public List<Relationship> getRelationships(Boolean activeFlag, String typeId, String destinationId, String charTypeId) {
		List<Relationship> matchingDescriptions = relationships.stream()
							.filter(rel -> (activeFlag == null || activeFlag.equals(rel.isActive())))
							.filter(rel -> (typeId == null || rel.getTypeId().equals(typeId)))
							.filter(rel -> (destinationId == null || rel.getDestinationId().equals(destinationId)))
							.filter(rel -> (charTypeId == null || rel.getCharacteristicTypeId().equals(charTypeId)))
							.collect(Collectors.toList());
		return matchingDescriptions;
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
	@JsonView(value = View.Component.class)
	public Set<Description> getDescriptions() {
		return descriptions;
	}

	public void setDescriptions(Set<Description> descriptions) {
		this.descriptions = descriptions;
	}

	@Override
	@JsonView(value = View.Component.class)
	public Set<Relationship> getRelationships() {
		return relationships;
	}

	public void setRelationships(Set<Relationship> relationships) {
		this.relationships = relationships;
	}

	@JsonView(value = View.Component.class)
	public Set<Axiom> getClassAxioms() {
		return classAxioms;
	}

	public void setClassAxioms(Set<Axiom> axioms) {
		this.classAxioms = axioms;
	}

	@JsonView(value = View.Component.class)
	public Set<Axiom> getGciAxioms() {
		return generalConceptInclusionAxioms;
	}

	public void setGciAxioms(Set<Axiom> generalConceptInclusionAxioms) {
		this.generalConceptInclusionAxioms = generalConceptInclusionAxioms;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		Concept concept = (Concept) o;

		if (conceptId != null || concept.conceptId != null) {
			return Objects.equals(conceptId, concept.conceptId);
		}

		return Objects.equals(moduleId, concept.moduleId) &&
				Objects.equals(definitionStatusId, concept.definitionStatusId);
	}

	@Override
	public int hashCode() {
		return Objects.hash(conceptId, moduleId, definitionStatusId);
	}

	@Override
	public String toString() {
		return "Concept{" +
				"conceptId='" + conceptId + '\'' +
				", effectiveTime='" + getEffectiveTimeI() + '\'' +
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
