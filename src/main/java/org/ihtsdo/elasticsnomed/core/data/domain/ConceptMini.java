package org.ihtsdo.elasticsnomed.core.data.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRawValue;
import com.fasterxml.jackson.annotation.JsonView;
import org.ihtsdo.elasticsnomed.rest.View;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class ConceptMini {

	private String conceptId;
	private Set<Description> activeFsns;
	private String definitionStatusId;
	private Boolean leafInferred;
	private Boolean leafStated;
	private String moduleId;
	private Boolean active;
	private boolean nestFsn;

	public ConceptMini() {
		activeFsns = new HashSet<>();
	}

	public ConceptMini(String conceptId) {
		this();
		this.conceptId = conceptId;
	}

	public ConceptMini(Concept concept) {
		this(concept.getConceptId());
		active = concept.isActive();
		definitionStatusId = concept.getDefinitionStatusId();
		moduleId = concept.getModuleId();
		Set<Description> descriptions = concept.getDescriptions();
		if (descriptions != null) {
			activeFsns = descriptions.stream().filter(d -> d.isActive() && Concepts.FSN.equals(d.getTypeId())).collect(Collectors.toSet());
		}
	}

	public void addActiveFsn(Description fsn) {
		activeFsns.add(fsn);
	}

	public void addActiveFsns(Collection<Description> fsns) {
		activeFsns.addAll(fsns);
	}

	@JsonView(value = View.Component.class)
	public String getConceptId() {
		return conceptId;
	}

	@JsonView(value = View.Component.class)
	public String getId() {
		return conceptId;
	}

	public String getFsn() {
		return activeFsns.isEmpty() ? null : activeFsns.iterator().next().getTerm();
	}

	@JsonView(value = View.Component.class)
	@JsonRawValue
	@JsonProperty("fsn")
	public String getJsonFsn() {
		String term = getFsn();
		return nestFsn ? "{ \"term\": \"" + term + "\" }" : "\"" + term + "\"";
	}

	public void setDefinitionStatusId(String definitionStatusId) {
		this.definitionStatusId = definitionStatusId;
	}

	@JsonView(value = View.Component.class)
	public String getDefinitionStatus() {
		return Concepts.definitionStatusNames.get(definitionStatusId);
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

	public void nestFsn() {
		this.nestFsn = true;
	}
}
