package org.snomed.snowstorm.mrcm.model;

public class Domain {

	private String id;
	private String effectiveTime;
	private boolean active;
	private String referencedComponentId;
	private Constraint domainConstraint;
	private String parentDomain;
	private Constraint proximalPrimitiveConstraint;
	private String proximalPrimitiveRefinement;

	public Domain(String id, String effectiveTime, boolean active, String referencedComponentId, Constraint domainConstraint, String parentDomain,
			Constraint proximalPrimitiveConstraint, String proximalPrimitiveRefinement) {

		this.id = id;
		this.effectiveTime = effectiveTime;
		this.active = active;
		this.referencedComponentId = referencedComponentId;
		this.domainConstraint = domainConstraint;
		this.parentDomain = parentDomain;
		this.proximalPrimitiveConstraint = proximalPrimitiveConstraint;
		this.proximalPrimitiveRefinement = proximalPrimitiveRefinement;
	}

	public String getId() {
		return id;
	}

	public String getEffectiveTime() {
		return effectiveTime;
	}

	public boolean isActive() {
		return active;
	}

	public String getReferencedComponentId() {
		return referencedComponentId;
	}

	public Constraint getDomainConstraint() {
		return domainConstraint;
	}

	public String getParentDomain() {
		return parentDomain;
	}

	public Constraint getProximalPrimitiveConstraint() {
		return proximalPrimitiveConstraint;
	}

	public String getProximalPrimitiveRefinement() {
		return proximalPrimitiveRefinement;
	}

}
