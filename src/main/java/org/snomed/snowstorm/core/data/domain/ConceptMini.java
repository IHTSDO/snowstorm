package org.snomed.snowstorm.core.data.domain;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonView;
import com.google.common.base.Strings;
import org.snomed.snowstorm.core.pojo.LanguageDialect;
import org.snomed.snowstorm.core.pojo.TermLangPojo;
import org.snomed.snowstorm.core.util.DescriptionHelper;
import org.snomed.snowstorm.rest.View;
import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

import static java.lang.Long.parseLong;

@JsonPropertyOrder({"conceptId", "active", "definitionStatus", "moduleId", "effectiveTime", "fsn", "pt", "descendantCount", "isLeafInferred", "isLeafStated", "id"})
public class ConceptMini implements Serializable {

	private String conceptId;
	private String effectiveTime;
	private Set<Description> activeDescriptions;
	private List<LanguageDialect> requestedLanguageDialects;
	private String definitionStatusId;
	private Boolean leafInferred;
	private Boolean leafStated;
	private Long descendantCount;
	private String moduleId;
	private Boolean active;
	private Map<String, Object> extraFields;

	public ConceptMini() {
		activeDescriptions = new HashSet<>();
	}

	public ConceptMini(String conceptId, List<LanguageDialect> requestedLanguageDialects) {
		this();
		this.conceptId = conceptId;
		this.requestedLanguageDialects = requestedLanguageDialects;
	}

	public ConceptMini(Concept concept, List<LanguageDialect> requestedLanguageDialects) {
		this(concept.getConceptId(), requestedLanguageDialects);
		effectiveTime = concept.getEffectiveTime();
		active = concept.isActive();
		definitionStatusId = concept.getDefinitionStatusId();
		moduleId = concept.getModuleId();
		Set<Description> descriptions = concept.getDescriptions();
		if (descriptions != null) {
			activeDescriptions = descriptions.stream().filter(SnomedComponent::isActive).collect(Collectors.toSet());
		}
	}

	public ConceptMini addActiveDescription(Description fsn) {
		activeDescriptions.add(fsn);
		return this;
	}

	public ConceptMini addFSN(String term) {
		activeDescriptions.add(new Description(term).setTypeId(Concepts.FSN).addLanguageRefsetMember(Concepts.US_EN_LANG_REFSET, Concepts.PREFERRED));
		return this;
	}

	public void addActiveDescriptions(Collection<Description> fsns) {
		activeDescriptions.addAll(fsns);
	}

	@JsonView(value = View.Component.class)
	public String getConceptId() {
		return conceptId;
	}

	@JsonIgnore
	public Long getConceptIdAsLong() {
		return conceptId != null ? parseLong(conceptId) : null;
	}

	@JsonView(value = View.Component.class)
	public String getId() {
		return conceptId;
	}

	public String getEffectiveTime() {
		return effectiveTime;
	}

	@JsonView(value = View.Component.class)
	public TermLangPojo getFsn() {
		return DescriptionHelper.getFsnDescriptionTermAndLang(activeDescriptions, requestedLanguageDialects);
	}

	@JsonIgnore
	public String getFsnTerm() {
		return getFsn().getTerm();
	}

	public String getIdAndFsnTerm() {
		String fsnTerm = getFsnTerm();
		if (Strings.isNullOrEmpty(fsnTerm)) {
			return getConceptId();
		} else {
			return String.format("%s | %s |", getConceptId(), fsnTerm);
		}
	}

	@JsonView(value = View.Component.class)
	public TermLangPojo getPt() {
		return DescriptionHelper.getPtDescriptionTermAndLang(activeDescriptions, requestedLanguageDialects);
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

	public void addExtraField(String name, Object value) {
		if (extraFields == null) {
			extraFields = new HashMap<>();
		}
		extraFields.put(name, value);
	}

	@JsonView(value = View.Component.class)
	@JsonAnyGetter
	public Map<String, Object> getExtraFields() {
		return extraFields;
	}

	public void setExtraFields(Map<String, Object> extraFields) {
		this.extraFields = extraFields;
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

	// Call this method first otherwise minis with no descendants will have null descendant count.
	public ConceptMini startDescendantCount() {
		descendantCount = 0L;
		return this;
	}

	public void incrementDescendantCount() {
		descendantCount++;
	}

	@JsonView(value = View.Component.class)
	public Long getDescendantCount() {
		return descendantCount;
	}

	public void setDescendantCount(Long descendantCount) {
		this.descendantCount = descendantCount;
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

	@JsonIgnore
	public List <LanguageDialect> getRequestedLanguageDialects() {
		return requestedLanguageDialects;
	}

	@JsonIgnore
	public Set <Description> getActiveDescriptions() {
		return activeDescriptions;
	}

	@Override
	// hashCode() and equals() should only use conceptId.
	// If you need to check other fields consider using a custom comparator in your service / collection.
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		ConceptMini that = (ConceptMini) o;
		return Objects.equals(conceptId, that.conceptId);
	}

	@Override
	public int hashCode() {
		return Objects.hash(conceptId);
	}
}
