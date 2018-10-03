package org.snomed.snowstorm.core.data.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRawValue;
import com.fasterxml.jackson.annotation.JsonView;
import org.snomed.snowstorm.rest.View;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class ConceptMini {

	private String conceptId;
	private String effectiveTime;
	private Set<Description> activeDescriptions;
	private String definitionStatusId;
	private Boolean leafInferred;
	private Boolean leafStated;
	private String moduleId;
	private Boolean active;
	private boolean flattenFsn;

	public ConceptMini() {
		activeDescriptions = new HashSet<>();
	}

	public ConceptMini(String conceptId) {
		this();
		this.conceptId = conceptId;
	}

	public ConceptMini(Concept concept) {
		this(concept.getConceptId());
		effectiveTime = concept.getEffectiveTime();
		active = concept.isActive();
		definitionStatusId = concept.getDefinitionStatusId();
		moduleId = concept.getModuleId();
		Set<Description> descriptions = concept.getDescriptions();
		if (descriptions != null) {
			activeDescriptions = descriptions.stream().filter(SnomedComponent::isActive).collect(Collectors.toSet());
		}
	}

	public void addActiveDescription(Description fsn) {
		activeDescriptions.add(fsn);
	}

	public void addActiveDescriptions(Collection<Description> fsns) {
		activeDescriptions.addAll(fsns);
	}

	@JsonView(value = View.Component.class)
	public String getConceptId() {
		return conceptId;
	}

	@JsonView(value = View.Component.class)
	public String getId() {
		return conceptId;
	}

	public String getEffectiveTime() {
		return effectiveTime;
	}

	public String getFsn() {
		for (Description activeDescription : activeDescriptions) {
			if (Concepts.FSN.equals(activeDescription.getTypeId())) {
				return activeDescription.getTerm();
			}
		}
		return null;
	}

	public String getPt() {
		for (Description activeDescription : activeDescriptions) {
			if (Concepts.SYNONYM.equals(activeDescription.getTypeId()) && activeDescription.getAcceptabilityMap().values().contains(Concepts.PREFERRED_CONSTANT)) {
				return activeDescription.getTerm();
			}
		}
		return null;
	}

	@JsonView(value = View.Component.class)
	@JsonRawValue
	@JsonProperty("fsn")
	public String getJsonFsn() {
		String term = getFsn();
		return flattenFsn ? "\"" + term + "\"" : String.format("{ \"term\": \"%s\", \"conceptId\": \"%s\" }", term, conceptId);
	}

	@JsonView(value = View.Component.class)
	@JsonRawValue
	@JsonProperty("pt")
	public String getJsonPt() {
		String term = getPt();
		return flattenFsn ? "\"" + term + "\"" : String.format("{ \"term\": \"%s\", \"conceptId\": \"%s\" }", term, conceptId);
	}

	public void setDefinitionStatusId(String definitionStatusId) {
		this.definitionStatusId = definitionStatusId;
	}

	@JsonView(value = View.Component.class)
	public String getDefinitionStatus() {
		return Concepts.definitionStatusNames.get(definitionStatusId);
	}
	
	@JsonIgnore
	public boolean isPrimitive() {
		return definitionStatusId.equals(Concepts.PRIMITIVE);
	}

	public void setDefinitionStatus(String definitionStatusName) {
		definitionStatusId = Concepts.definitionStatusNames.inverse().get(definitionStatusName);
	}


	public ConceptMini setLeaf(Relationship.CharacteristicType relationshipType, boolean bool) {
		switch (relationshipType) {
			case inferred:
				setLeafInferred(bool);
				break;
			case stated:
				setLeafStated(bool);
				break;
		}
		return this;
	}

	@JsonView(value = View.Component.class)
	public Boolean getIsLeafInferred() {
		return leafInferred;
	}

	public ConceptMini setLeafInferred(Boolean leafInferred) {
		this.leafInferred = leafInferred;
		return this;
	}

	@JsonView(value = View.Component.class)
	public Boolean getIsLeafStated() {
		return leafStated;
	}

	public ConceptMini setLeafStated(Boolean leafStated) {
		this.leafStated = leafStated;
		return this;
	}

	@JsonView(value = View.Component.class)
	public String getModuleId() {
		return moduleId;
	}

	public void setModuleId(String moduleId) {
		this.moduleId = moduleId;
	}

	@JsonView(value = View.Component.class)
	public Boolean getActive() {
		return active;
	}

	public void setActive(Boolean active) {
		this.active = active;
	}

	/**
	 * Changes the way that the FSN is returned in the JSON representation.
	 * By default the FSN is displayed as an object with a term and conceptId property.
	 * Calling this method results in just the term being returned as the value of the fsn property.
	 */
	public void flattenFsn() {
		this.flattenFsn = true;
	}
}
