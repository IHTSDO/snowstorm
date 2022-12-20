package org.snomed.snowstorm.core.data.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonView;
import org.snomed.snowstorm.rest.View;

import java.util.HashSet;
import java.util.Set;

@JsonView(value = View.Component.class)
public class Axiom implements IdAndEffectiveTimeComponent {

	private String axiomId;
	private String moduleId;
	private boolean active;
	private boolean released;
	private String definitionStatusId;
	private Set<Relationship> relationships;
	private ReferenceSetMember referenceSetMember;

	public Axiom() {
		active = true;
		definitionStatusId = Concepts.PRIMITIVE;
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

	public Axiom(String moduleId, boolean active, String definitionStatusId, Set<Relationship> relationships) {
		this();
		this.moduleId = moduleId;
		this.active = active;
		this.definitionStatusId = definitionStatusId;
		this.relationships = relationships;
	}

	@Override
	public String getId() {
		return axiomId;
	}

	@Override
	@JsonProperty("effectiveTime")
	public Integer getEffectiveTimeI() {
		return referenceSetMember == null ? null : referenceSetMember.getEffectiveTimeI();
	}

	@JsonView(value = View.Component.class)
	public String getDefinitionStatus() {
		return Concepts.definitionStatusNames.get(definitionStatusId);
	}

	public void setDefinitionStatus(String definitionStatusName) {
		definitionStatusId = Concepts.definitionStatusNames.inverse().get(definitionStatusName);
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

	public Axiom setModuleId(String moduleId) {
		this.moduleId = moduleId;
		return this;
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

	public Axiom setRelationships(Set<Relationship> relationships) {
		if (relationships != null) {
			for (Relationship relationship : relationships) {
				relationship.setInferred(false);
			}
		}
		this.relationships = relationships;
		return this;
	}

	@JsonIgnore
	public ReferenceSetMember getReferenceSetMember() {
		return referenceSetMember;
	}

	public void setReferenceSetMember(ReferenceSetMember referenceSetMember) {
		this.referenceSetMember = referenceSetMember;
	}

    public void clone(Axiom axiom) {
        setAxiomId(axiom.getAxiomId());
        setModuleId(axiom.getModuleId());
        setActive(axiom.isActive());
        setReleased(axiom.isReleased());
        setDefinitionStatusId(axiom.getDefinitionStatusId());

        Set<Relationship> clonedRelationships = new HashSet<>();
        Set<Relationship> relationships = axiom.getRelationships();
        if (relationships != null && !relationships.isEmpty()) {
            for (Relationship relationship : relationships) {
                Relationship clone = new Relationship();
				clone.clone(relationship);
                clonedRelationships.add(clone);
            }
            setRelationships(clonedRelationships);
        }

        if (axiom.getReferenceSetMember() != null) {
            ReferenceSetMember referenceSetMember = new ReferenceSetMember();
            referenceSetMember.clone(axiom.getReferenceSetMember());
            setReferenceSetMember(referenceSetMember);
        }
    }

    @Override
    public String toString() {
        return "Axiom{" +
                "axiomId='" + axiomId + '\'' +
                ", moduleId='" + moduleId + '\'' +
                ", active=" + active +
                ", released=" + released +
                ", definitionStatusId='" + definitionStatusId + '\'' +
                ", relationships=" + relationships +
                ", referenceSetMember=" + referenceSetMember +
                '}';
    }
}
