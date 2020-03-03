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
	private String domainTemplateForPrecoordination;
	private String domainTemplateForPostcoordination;

	public Domain(String id, String effectiveTime, boolean active, String referencedComponentId, Constraint domainConstraint, String parentDomain,
			Constraint proximalPrimitiveConstraint, String proximalPrimitiveRefinement,
				  String domainTemplateForPrecoordination, String domainTemplateForPostcoordination) {

		this.id = id;
		this.effectiveTime = effectiveTime;
		this.active = active;
		this.referencedComponentId = referencedComponentId;
		this.domainConstraint = domainConstraint;
		this.parentDomain = parentDomain;
		this.proximalPrimitiveConstraint = proximalPrimitiveConstraint;
		this.proximalPrimitiveRefinement = proximalPrimitiveRefinement;
		this.domainTemplateForPrecoordination = domainTemplateForPrecoordination;
		this.domainTemplateForPostcoordination = domainTemplateForPostcoordination;
	}

	public Domain (Domain domain) {
		this.id = domain.getId();
		this.effectiveTime = domain.getEffectiveTime();
		this.active = domain.isActive();
		this.referencedComponentId = domain.getReferencedComponentId();
		this.domainConstraint = domain.getDomainConstraint();
		this.parentDomain = domain.getParentDomain();
		this.proximalPrimitiveConstraint = domain.getProximalPrimitiveConstraint();
		this.proximalPrimitiveRefinement = domain.getProximalPrimitiveRefinement();
		this.domainTemplateForPrecoordination = domain.getDomainTemplateForPrecoordination();
		this.domainTemplateForPostcoordination = domain.getDomainTemplateForPostcoordination();
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

	public String getParentDomain() { return parentDomain; }

	public Constraint getProximalPrimitiveConstraint() {
		return proximalPrimitiveConstraint;
	}

	public String getProximalPrimitiveRefinement() {
		return proximalPrimitiveRefinement;
	}

	public String getDomainTemplateForPostcoordination() { return domainTemplateForPostcoordination; }

	public String getDomainTemplateForPrecoordination() { return domainTemplateForPrecoordination; }

	public void setDomainTemplateForPrecoordination(String domainTemplateForPrecoordination) {
		this.domainTemplateForPrecoordination = domainTemplateForPrecoordination;
	}

	public void setDomainTemplateForPostcoordination(String domainTemplateForPostcoordination) {
		this.domainTemplateForPostcoordination = domainTemplateForPostcoordination;
	}
}
