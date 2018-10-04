package org.snomed.snowstorm.core.data.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRawValue;
import com.fasterxml.jackson.annotation.JsonView;
import org.snomed.snowstorm.rest.View;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class ConceptMini {

	private String conceptId;
	private String effectiveTime;
	private Set<Description> activeDescriptions;
	private Collection<String> languageCodes;
	private String definitionStatusId;
	private Boolean leafInferred;
	private Boolean leafStated;
	private String moduleId;
	private Boolean active;
	private boolean flattenFsn;

	public ConceptMini() {
		activeDescriptions = new HashSet<>();
	}

	public ConceptMini(String conceptId, List<String> languageCodes) {
		this();
		this.conceptId = conceptId;
		this.languageCodes = languageCodes;
	}

	public ConceptMini(Concept concept, List<String> languageCodes) {
		this(concept.getConceptId(), languageCodes);
		effectiveTime = concept.getEffectiveTime();
		active = concept.isActive();
		definitionStatusId = concept.getDefinitionStatusId();
		moduleId = concept.getModuleId();
		Set<Description> descriptions = concept.getDescriptions();
		if (descriptions != null) {
			activeDescriptions = descriptions.stream().filter(SnomedComponent::isActive).collect(Collectors.toSet());
		}
		this.languageCodes = languageCodes;
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
		Description description = getFsnDescription();
		return description != null ? description.getTerm() : null;
	}

	private Description getFsnDescription() {
		return getBestDescription(description -> Concepts.FSN.equals(description.getTypeId()));
	}

	public String getPt() {
		Description description = getPtDescription();
		return description != null ? description.getTerm() : null;
	}

	private Description getPtDescription() {
		return getBestDescription(description -> Concepts.SYNONYM.equals(description.getTypeId()) && description.getAcceptabilityMap().values().contains(Concepts.PREFERRED_CONSTANT));
	}

	private Description getBestDescription(Predicate<Description> descriptionPredicate) {
		Map<String, Description> descriptionsByLanguageCode = activeDescriptions.stream().filter(descriptionPredicate).collect(Collectors.toMap(Description::getLanguageCode, Function.identity()));
		if (languageCodes != null) {
			for (String languageCode : languageCodes) {
				if (descriptionsByLanguageCode.containsKey(languageCode)) {
					return descriptionsByLanguageCode.get(languageCode);
				}
			}
		}
		return null;
	}

	@JsonView(value = View.Component.class)
	@JsonRawValue
	@JsonProperty("fsn")
	public String getJsonFsn() {
		return getJsonTerm(getFsnDescription());
	}

	@JsonView(value = View.Component.class)
	@JsonRawValue
	@JsonProperty("pt")
	public String getJsonPt() {
		return getJsonTerm(getPtDescription());
	}

	private String getJsonTerm(Description description) {
		if (description == null) return null;
		return flattenFsn ?
				String.format("\"%s\"", description.getTerm()) :
				String.format("{ \"term\": \"%s\", \"lang\": \"%s\", \"conceptId\": \"%s\" }", description.getTerm(), description.getLang(), conceptId);
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
