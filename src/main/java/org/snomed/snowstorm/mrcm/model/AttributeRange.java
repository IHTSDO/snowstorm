package org.snomed.snowstorm.mrcm.model;

import org.snomed.snowstorm.core.data.domain.ConcreteValue;

public class AttributeRange {

	private String id;
	private String effectiveTime;
	private boolean active;
	private String referencedComponentId;
	private String rangeConstraint;
	private String attributeRule;
	private RuleStrength ruleStrength;
	private ContentType contentType;
	private ConcreteValue.DataType dataType;

	public AttributeRange(String id, String effectiveTime, boolean active, String referencedComponentId, String rangeConstraint, String attributeRule,
			RuleStrength ruleStrength, ContentType contentType) {
		this.id = id;
		this.effectiveTime = effectiveTime;
		this.active = active;
		this.referencedComponentId = referencedComponentId;
		this.rangeConstraint = rangeConstraint;
		this.attributeRule = attributeRule;
		this.ruleStrength = ruleStrength;
		this.contentType = contentType;
	}

	public AttributeRange(AttributeRange attributeRange)  {
		this.id = attributeRange.getId();
		this.effectiveTime = attributeRange.getEffectiveTime();
		this.active = attributeRange.isActive();
		this.referencedComponentId = attributeRange.getReferencedComponentId();
		this.rangeConstraint = attributeRange.getRangeConstraint();
		this.attributeRule = attributeRange.getAttributeRule();
		this.ruleStrength = attributeRange.getRuleStrength();
		this.contentType = attributeRange.getContentType();
	}

	public AttributeRange(String id, String effectiveTime, boolean active, String referencedComponentId, String rangeConstraint, String attributeRule,
						  RuleStrength ruleStrength, ContentType contentType, ConcreteValue.DataType dataType) {
		this.id = id;
		this.effectiveTime = effectiveTime;
		this.active = active;
		this.referencedComponentId = referencedComponentId;
		this.rangeConstraint = rangeConstraint;
		this.attributeRule = attributeRule;
		this.ruleStrength = ruleStrength;
		this.contentType = contentType;
		this.dataType = dataType;
	}

	public void setRangeConstraint(String rangeConstraint) { this.rangeConstraint = rangeConstraint; }

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

	public String getRangeConstraint() { return rangeConstraint; }

	public String getAttributeRule() { return attributeRule; }

	public void setAttributeRule(String attributeRule) { this.attributeRule = attributeRule; }

	public RuleStrength getRuleStrength() {
		return ruleStrength;
	}

	public ContentType getContentType() {
		return contentType;
	}

	public ConcreteValue.DataType getDataType() {
		return dataType;
	}

	public void setDataType(ConcreteValue.DataType dataType) {
		this.dataType = dataType;
	}
}
