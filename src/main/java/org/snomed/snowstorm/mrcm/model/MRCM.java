package org.snomed.snowstorm.mrcm.model;

import java.util.List;

public class MRCM {

	private final List<Domain> domains;
	private final List<AttributeDomain> attributeDomains;
	private final List<AttributeRange> attributeRanges;

	public MRCM(List<Domain> domains, List<AttributeDomain> attributeDomains, List<AttributeRange> attributeRanges) {
		this.domains = domains;
		this.attributeDomains = attributeDomains;
		this.attributeRanges = attributeRanges;
	}

	public List<Domain> getDomains() {
		return domains;
	}

	public List<AttributeDomain> getAttributeDomains() {
		return attributeDomains;
	}

	public List<AttributeRange> getAttributeRanges() {
		return attributeRanges;
	}
}
