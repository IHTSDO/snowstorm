package org.snomed.snowstorm.ecl.domain.refinement;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static java.lang.Long.parseLong;

class AttributeRange {

	private final String id;
	private final boolean attributeTypeWildcard;
	private final Set<String> possibleAttributeTypes;
	private final List<Long> possibleAttributeValues;
	private final Integer cardinalityMin;
	private final Integer cardinalityMax;

	AttributeRange(boolean attributeTypeWildcard, Set<String> possibleAttributeTypes, List<Long> possibleAttributeValues, Integer cardinalityMin, Integer cardinalityMax) {
		id = UUID.randomUUID().toString();
		this.attributeTypeWildcard = attributeTypeWildcard;
		this.possibleAttributeTypes = possibleAttributeTypes;
		this.possibleAttributeValues = possibleAttributeValues;
		this.cardinalityMin = cardinalityMin;
		this.cardinalityMax = cardinalityMax;
	}

	boolean isTypeAcceptable(String conceptId) {
		return attributeTypeWildcard || possibleAttributeTypes.contains(conceptId);
	}

	boolean isValueAcceptable(String conceptAttributeValue) {
		return possibleAttributeValues == null || possibleAttributeValues.contains(parseLong(conceptAttributeValue));
	}

	public Integer getCardinalityMin() {
		return cardinalityMin;
	}

	public Integer getCardinalityMax() {
		return cardinalityMax;
	}
}
