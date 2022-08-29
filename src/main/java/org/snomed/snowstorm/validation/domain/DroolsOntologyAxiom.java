package org.snomed.snowstorm.validation.domain;

import org.ihtsdo.drools.domain.OntologyAxiom;
import org.snomed.snowstorm.core.data.domain.Relationship;

import java.util.Collection;

public class DroolsOntologyAxiom implements OntologyAxiom {

	private String id;
	private boolean active;
	private boolean published;
	private boolean primitive;
	private boolean released;
	private String moduleId;
	private Collection<String> ownExpressionNamedConcepts;
	private String owlExpression;
	private String referencedComponentId;

	public DroolsOntologyAxiom(String id, boolean active, boolean primitive, String referencedComponentId, String moduleId) {
		this.id = id;
		this.active = active;
		this.primitive = primitive;
		this.referencedComponentId = referencedComponentId;
		this.moduleId = moduleId;
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
}
