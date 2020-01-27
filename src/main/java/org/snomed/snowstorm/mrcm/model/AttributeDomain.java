package org.snomed.snowstorm.mrcm.model;

public class AttributeDomain {

	private String id;
	private String effectiveTime;
	private boolean active;
	private String referencedComponentId;
	private String domainId;
	private boolean grouped;
	private Cardinality attributeCardinality;
	private Cardinality attributeInGroupCardinality;
	private RuleStrength ruleStrength;
	private ContentType contentType;

	public AttributeDomain(String id, String effectiveTime, boolean active, String referencedComponentId, String domainId, boolean grouped,
			Cardinality attributeCardinality, Cardinality attributeInGroupCardinality, RuleStrength ruleStrength, ContentType contentType) {
		this.id = id;
		this.effectiveTime = effectiveTime;
		this.active = active;
		this.referencedComponentId = referencedComponentId;
		this.domainId = domainId;
		this.grouped = grouped;
		this.attributeCardinality = attributeCardinality;
		this.attributeInGroupCardinality = attributeInGroupCardinality;
		this.ruleStrength = ruleStrength;
		this.contentType = contentType;
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

	public String getDomainId() {
		return domainId;
	}

	public boolean isGrouped() {
		return grouped;
	}

	public Cardinality getAttributeCardinality() {
		return attributeCardinality;
	}

	public Cardinality getAttributeInGroupCardinality() {
		return attributeInGroupCardinality;
	}

	public RuleStrength getRuleStrength() {
		return ruleStrength;
	}

	public ContentType getContentType() {
		return contentType;
	}
}
