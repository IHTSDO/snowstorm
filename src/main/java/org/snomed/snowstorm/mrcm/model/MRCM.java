package org.snomed.snowstorm.mrcm.model;

import org.snomed.snowstorm.core.data.domain.Concepts;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class MRCM {

	// Hardcoded Is a (attribute)
	// 'Is a' is not really an attribute at all but it's convenient for implementations to have this.
	public static final AttributeDomain IS_A_ATTRIBUTE_DOMAIN = new AttributeDomain(null, null, true, Concepts.ISA, Concepts.SNOMEDCT_ROOT, false,
			new Cardinality(1, null), new Cardinality(0, 0), RuleStrength.MANDATORY, ContentType.ALL);
	public static final AttributeRange IS_A_ATTRIBUTE_RANGE = new AttributeRange(null, null, true, Concepts.ISA, "*", "*", RuleStrength.MANDATORY, ContentType.ALL);

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

	public List<AttributeDomain> getAttributeDomainsForContentType(ContentType contentType) {
		List<AttributeDomain> attributeDomains = new ArrayList<>();
		attributeDomains.add(IS_A_ATTRIBUTE_DOMAIN);
		attributeDomains.addAll(getAttributeDomains().stream()
				.filter(attributeDomain -> attributeDomain.getContentType().ruleAppliesToContentType(contentType)).collect(Collectors.toList()));
		return attributeDomains;
	}

	public Set<AttributeRange> getMandatoryAttributeRanges(String attributeId, ContentType contentType) {
		Set<AttributeRange> attributeRanges;
		if (Concepts.ISA.equals(attributeId)) {
			attributeRanges = Collections.singleton(IS_A_ATTRIBUTE_RANGE);
		} else {
			attributeRanges = getAttributeRanges().stream()
					.filter(attributeRange -> attributeRange.getContentType().ruleAppliesToContentType(contentType)
							&& attributeRange.getRuleStrength() == RuleStrength.MANDATORY
							&& attributeRange.getReferencedComponentId().equals(attributeId)).collect(Collectors.toSet());
		}
		return attributeRanges;
	}
}
