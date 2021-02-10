package org.snomed.snowstorm.mrcm.model;

import org.snomed.snowstorm.core.data.domain.ConcreteValue;

public class AttributeRange {

	private final String id;
	private final String effectiveTime;
	private final boolean active;
	private final String referencedComponentId;
	private String rangeConstraint;
	private String rangeMin;
	private String rangeMax;
	private String attributeRule;
	private final RuleStrength ruleStrength;
	private final ContentType contentType;
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
		this(attributeRange.getId(), attributeRange.getEffectiveTime(), attributeRange.isActive(), attributeRange.getReferencedComponentId(),
				attributeRange.getRangeConstraint(), attributeRange.getAttributeRule(), attributeRange.getRuleStrength(), attributeRange.getContentType());
	}

	public AttributeRange(String id, String effectiveTime, boolean active, String referencedComponentId, String rangeConstraint, String attributeRule,
						  RuleStrength ruleStrength, ContentType contentType, ConcreteValue.DataType dataType) {
		this(id, effectiveTime, active, referencedComponentId, rangeConstraint, attributeRule, ruleStrength, contentType);
		if (dataType != null) {
			final ConcreteValueRangeConstraint concreteValueRangeConstraint = new ConcreteValueRangeConstraint(rangeConstraint);
			this.rangeMin = concreteValueRangeConstraint.getMinimumValue();
			this.rangeMax = concreteValueRangeConstraint.getMaximumValue();
		}
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

	public final String getRangeMin() {
		return rangeMin;
	}

	public final String getRangeMax() {
		return rangeMax;
	}

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
