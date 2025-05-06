package org.snomed.snowstorm.validation.domain;

import org.ihtsdo.drools.domain.OntologyAxiom;

import java.util.Collection;

public class DroolsOntologyAxiom implements OntologyAxiom {

	private String id;
	private String effectiveTime;
	private boolean active;
	private boolean published;
	private boolean primitive;
	private boolean released;
	private boolean axiomGci;
	private String moduleId;
	private Collection<String> ownExpressionNamedConcepts;
	private String owlExpression;
	private String referencedComponentId;

	public DroolsOntologyAxiom(String id, Integer effectiveTimeI, boolean active, boolean primitive, String referencedComponentId, String moduleId, boolean axiomGci) {
		this.id = id;
		this.effectiveTime = effectiveTimeI != null ? effectiveTimeI.toString() : null;
		this.active = active;
		this.primitive = primitive;
		this.referencedComponentId = referencedComponentId;
		this.moduleId = moduleId;
		this.axiomGci = axiomGci;
	}

	@Override
	public String getReferencedComponentId() {
		return referencedComponentId;
	}

	@Override
	public String getOwlExpression() {
		return owlExpression;
	}

	@Override
	public Collection<String> getOwlExpressionNamedConcepts() {
		return ownExpressionNamedConcepts;
	}

	@Override
	public boolean isAxiomGCI() {
		return axiomGci;
	}

	@Override
	public String getId() {
		return id;
	}

	@Override
	public boolean isActive() {
		return active;
	}

	@Override
	public boolean isPublished() {
		return published;
	}

	@Override
	public boolean isPrimitive() {
		return primitive;
	}

	@Override
	public boolean isReleased() {
		return released;
	}

	@Override
	public String getModuleId() {
		return moduleId;
	}

	@Override
	public String getEffectiveTime() {
		return effectiveTime;
	}

}
