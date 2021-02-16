package org.snomed.snowstorm.core.data.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonView;
import com.google.common.collect.Sets;
import org.ihtsdo.drools.response.InvalidContent;
import org.snomed.snowstorm.core.pojo.LanguageDialect;
import org.snomed.snowstorm.core.pojo.TermLangPojo;
import org.snomed.snowstorm.core.util.DescriptionHelper;
import org.snomed.snowstorm.rest.View;
import org.springframework.data.annotation.Transient;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.*;
import java.util.stream.Collectors;

import static org.snomed.snowstorm.config.Config.DEFAULT_LANGUAGE_DIALECTS;

@Document(indexName = "concept")
@JsonPropertyOrder({"conceptId", "descendantCount", "fsn", "pt", "active", "effectiveTime", "released", "releasedEffectiveTime",  "inactivationIndicator", "associationTargets",
		"moduleId", "definitionStatus", "definitionStatusId", "descriptions", "classAxioms", "gciAxioms", "relationships", "validationResults"})
public class Concept extends SnomedComponent<Concept> implements ConceptView, SnomedComponentWithInactivationIndicator, SnomedComponentWithAssociations {

	public interface Fields extends SnomedComponent.Fields {
		String CONCEPT_ID = "conceptId";
		String DEFINITION_STATUS_ID = "definitionStatusId";
	}

	@JsonView(value = View.Component.class)
	@Field(type = FieldType.Keyword, store = true)
	@Size(min = 5, max = 18)
	private String conceptId;

	@Transient
	@JsonIgnore
	private final List<ReferenceSetMember> inactivationIndicatorMembers;

	@JsonIgnore
	// Populated when requesting an update
	@Transient
	private String inactivationIndicatorName;

	@JsonIgnore
	@Transient
	private List<ReferenceSetMember> associationTargetMembers;

	@JsonIgnore
	// Populated when requesting an update
	@Transient
	private Map<String, Set<String>> associationTargetStrings;

	@Field(type = FieldType.Keyword)
	@NotNull
	@Size(min = 5, max = 18)
	private String definitionStatusId;

	@Valid
	@Transient
	private Set<Description> descriptions;

	@Valid
	@Transient
	private Set<Relationship> relationships;

	@Transient
	private Set<Axiom> classAxioms;

	@Transient
	private Set<Axiom> generalConceptInclusionAxioms;

	@Transient
	@JsonIgnore
	private List<LanguageDialect> requestedLanguageDialects;

	@Transient
	private Long descendantCount;

	@Transient
	private List<InvalidContent> validationResults;

	public Concept() {
		active = true;
		setModuleId(Concepts.CORE_MODULE);
		definitionStatusId = Concepts.PRIMITIVE;
		descriptions = new HashSet<>();
		relationships = new HashSet<>();
		classAxioms = new HashSet<>();
		generalConceptInclusionAxioms = new HashSet<>();
		inactivationIndicatorMembers = new ArrayList<>();
		validationResults = new ArrayList<>();
	}

	public Concept(String conceptId) {
		this();
		this.conceptId = conceptId;
	}

	public Concept(String conceptId, String moduleId) {
		this();
		this.conceptId = conceptId;
		setModuleId(moduleId);
	}

	public Concept(String conceptId, Integer effectiveTime, boolean active, String moduleId, String definitionStatusId) {
		this();
		this.conceptId = conceptId;
		setEffectiveTimeI(effectiveTime);
		this.active = active;
		setModuleId(moduleId);
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
				|| !getModuleId().equals(that.getModuleId())
				|| !definitionStatusId.equals(that.definitionStatusId);
	}

	public boolean isPrimitive() {
		return definitionStatusId.equals(Concepts.PRIMITIVE);
	}

	@Override
	protected Object[] getReleaseHashObjects() {
		return new Object[]{active, getModuleId(), definitionStatusId};
	}

	@JsonView(value = View.Component.class)
	@Override
	public TermLangPojo getFsn() {
		return DescriptionHelper.getFsnDescriptionTermAndLang(descriptions, requestedLanguageDialects != null ? requestedLanguageDialects : DEFAULT_LANGUAGE_DIALECTS);
	}
	
	public TermLangPojo getFsn(List<LanguageDialect> requestedLanguageDialects) {
		return DescriptionHelper.getFsnDescriptionTermAndLang(descriptions, requestedLanguageDialects != null ? requestedLanguageDialects : DEFAULT_LANGUAGE_DIALECTS);
	}

	@JsonView(value = View.Component.class)
	@Override
	public TermLangPojo getPt() {
		return DescriptionHelper.getPtDescriptionTermAndLang(descriptions, requestedLanguageDialects != null ? requestedLanguageDialects : DEFAULT_LANGUAGE_DIALECTS);
	}

	@JsonView(value = View.Component.class)
	public String getInactivationIndicator() {
		Collection<ReferenceSetMember> inactivationIndicatorMembers = getInactivationIndicatorMembers();
		if (inactivationIndicatorMembers != null) {
			for (ReferenceSetMember inactivationIndicatorMember : inactivationIndicatorMembers) {
				if (inactivationIndicatorMember.isActive()) {
					return Concepts.inactivationIndicatorNames.get(inactivationIndicatorMember.getAdditionalField("valueId"));
				}
			}
		}
		return inactivationIndicatorName;
	}

	@Override
	public Concept setActive(boolean active) {
		return (Concept) super.setActive(active);
	}

	public Concept setInactivationIndicator(String inactivationIndicatorName) {
		this.inactivationIndicatorName = inactivationIndicatorName;
		return this;
	}

	public void addAssociationTargetMember(ReferenceSetMember member) {
		if (associationTargetMembers == null) {
			associationTargetMembers = new ArrayList<>();
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

	public List<ReferenceSetMember> getAssociationTargetMembers() {
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
		addDescription(new Description(term).setTypeId(Concepts.FSN).addLanguageRefsetMember(Concepts.US_EN_LANG_REFSET, Concepts.PREFERRED));
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
		Axiom axiom = new Axiom(getModuleId(), true, Concepts.PRIMITIVE, Sets.newHashSet(axiomFragments));
		axiom.getRelationships().forEach(r -> {
			r.setSourceId(this.conceptId);
			r.setInferred(false);
		});
		classAxioms.add(axiom);
		return this;
	}

	public Concept addGeneralConceptInclusionAxiom(Axiom axiom) {
		axiom.getRelationships().forEach(r -> r.setSourceId(this.conceptId));
		generalConceptInclusionAxioms.add(axiom);
		return this;
	}

	public Concept addGeneralConceptInclusionAxiom(Relationship... axiomFragments) {
		Axiom axiom = new Axiom(getModuleId(), true, Concepts.PRIMITIVE, Sets.newHashSet(axiomFragments));
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

	public List<Description> getActiveDescriptions() {
		return descriptions.stream().filter(SnomedComponent::isActive).collect(Collectors.toList());
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
		return !inactivationIndicatorMembers.isEmpty() ? inactivationIndicatorMembers.iterator().next() : null;
	}

	/*
	 * There should be at most one inactivation indicator apart from part way through a branch merge.
	 */
	@JsonIgnore
	public Collection<ReferenceSetMember> getInactivationIndicatorMembers() {
		return inactivationIndicatorMembers;
	}

	public void addInactivationIndicatorMember(ReferenceSetMember inactivationIndicatorMember) {
		inactivationIndicatorMembers.add(inactivationIndicatorMember);
	}

	public Set<Relationship> getRelationshipsWithDestination(String destinationId) {
		return relationships.stream().filter(r -> destinationId.equals(r.getDestinationId())).collect(Collectors.toSet());
	}

	public List<Relationship> getRelationships(Boolean activeFlag, String typeId, String destinationId, String charTypeId) {
		return relationships.stream()
							.filter(rel -> (activeFlag == null || activeFlag.equals(rel.isActive())))
							.filter(rel -> (typeId == null || rel.getTypeId().equals(typeId)))
							.filter(rel -> (destinationId == null || rel.getDestinationId().equals(destinationId)))
							.filter(rel -> (charTypeId == null || rel.getCharacteristicTypeId().equals(charTypeId)))
							.collect(Collectors.toList());
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

	@JsonView(value = View.Component.class)
	public Long getDescendantCount() {
		return descendantCount;
	}

	public void setDescendantCount(Long descendantCount) {
		this.descendantCount = descendantCount;
	}

	public void setRequestedLanguageDialects(List<LanguageDialect> requestedLanguageDialects) {
		this.requestedLanguageDialects = requestedLanguageDialects;
	}

	@JsonView(value = View.Component.class)
	@Override
	public List<InvalidContent> getValidationResults() {
		return validationResults;
	}

	public void setValidationResults(final List<InvalidContent> validationResults) {
		this.validationResults = validationResults;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		Concept concept = (Concept) o;

		if (conceptId != null || concept.conceptId != null) {
			return Objects.equals(conceptId, concept.conceptId);
		}

		return Objects.equals(getModuleId(), concept.getModuleId()) &&
				Objects.equals(definitionStatusId, concept.definitionStatusId);
	}

	@Override
	public int hashCode() {
		return Objects.hash(conceptId, getModuleId(), definitionStatusId);
	}

	@Override
	public String toString() {
		return "Concept{" +
				"conceptId='" + conceptId + '\'' +
				", effectiveTime='" + getEffectiveTimeI() + '\'' +
				", active=" + active +
				", moduleId='" + getModuleId() + '\'' +
				", definitionStatusId='" + definitionStatusId + '\'' +
				", internalId='" + getInternalId() + '\'' +
				", start='" + getStartDebugFormat() + '\'' +
				", end='" + getEndDebugFormat() + '\'' +
				", path='" + getPath() + '\'' +
				'}';
	}
}
