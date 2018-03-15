package org.snomed.snowstorm.ecl.domain.refinement;

import org.snomed.snowstorm.ecl.domain.RefinementBuilder;

public class SubAttributeSet implements Refinement {

	private EclAttribute attribute;
	private EclAttributeSet attributeSet;

	@Override
	public void addCriteria(RefinementBuilder refinementBuilder) {
		if (attribute != null) {
			attribute.addCriteria(refinementBuilder);
		} else {
			attributeSet.addCriteria(refinementBuilder);
		}
	}

	public void setAttribute(EclAttribute attribute) {
		this.attribute = attribute;
	}

	public EclAttribute getAttribute() {
		return attribute;
	}

	public void setAttributeSet(EclAttributeSet attributeSet) {
		this.attributeSet = attributeSet;
	}

	@Override
	public String toString() {
		return "SubAttributeSet{" +
				"attribute=" + attribute +
				", attributeSet=" + attributeSet +
				'}';
	}
}
