package org.snomed.snowstorm.mrcm.model;

import java.util.Map;

public class MRCM {

	private Map<Long, Domain> domainMap;
	private Map<Long, Attribute> attributeMap;

	public void setDomainMap(Map<Long, Domain> domainsMap) {
		this.domainMap = domainsMap;
	}

	public Map<Long, Domain> getDomainMap() {
		return domainMap;
	}

	public void setAttributeMap(Map<Long, Attribute> attributeMap) {
		this.attributeMap = attributeMap;
	}

	public Map<Long, Attribute> getAttributeMap() {
		return attributeMap;
	}
}
