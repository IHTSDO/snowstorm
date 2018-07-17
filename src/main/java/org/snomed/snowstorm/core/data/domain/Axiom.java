package org.snomed.snowstorm.core.data.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonView;
import org.snomed.snowstorm.rest.View;

import java.util.Set;

@JsonView(value = View.Component.class)
public class Axiom {

	private String axiomId;
	private String moduleId;
	private boolean active;
	private boolean released;
	private String definitionStatusId;
	private Set<Relationship> relationships;
	private ReferenceSetMember referenceSetMember;

	public Axiom() {
		active = true;
	}

	public Axiom(ReferenceSetMember referenceSetMember, String definitionStatusId, Set<Relationship> relationships) {
		this();
		this.referenceSetMember = referenceSetMember;
		this.definitionStatusId = definitionStatusId;
		this.relationships = relationships;
		if (referenceSetMember != null) {
			axiomId = referenceSetMember.getId();
			moduleId = referenceSetMember.getModuleId();
			active = referenceSetMember.isActive();
			released = referenceSetMember.isReleased();
		}
	}

	public String getDefinitionStatus() {
		return Concepts.definitionStatusNames.get(definitionStatusId);
	}

	public String getAxiomId() {
		return axiomId;
	}

	public void setAxiomId(String axiomId) {
		this.axiomId = axiomId;
	}

	public String getModuleId() {
		return moduleId;
	}

	public void setModuleId(String moduleId) {
		this.moduleId = moduleId;
	}

	public boolean isActive() {
		return active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

	public boolean isReleased() {
		return released;
	}

	public void setReleased(boolean released) {
		this.released = released;
	}

	public String getDefinitionStatusId() {
		return definitionStatusId;
	}

	public void setDefinitionStatusId(String definitionStatusId) {
		this.definitionStatusId = definitionStatusId;
	}

	public Set<Relationship> getRelationships() {
		return relationships;
	}

	public void setRelationships(Set<Relationship> relationships) {
		this.relationships = relationships;
	}

	@JsonIgnore
	public ReferenceSetMember getReferenceSetMember() {
		return referenceSetMember;
	}

	public void setReferenceSetMember(ReferenceSetMember referenceSetMember) {
		this.referenceSetMember = referenceSetMember;
	}
}
