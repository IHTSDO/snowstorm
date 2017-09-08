package org.ihtsdo.elasticsnomed.ecl.domain;

public class SubAttributeSet {
	private EclAttribute attribute;

	public void setAttribute(EclAttribute attribute) {
		this.attribute = attribute;
	}

	public EclAttribute getAttribute() {
		return attribute;
	}

}
