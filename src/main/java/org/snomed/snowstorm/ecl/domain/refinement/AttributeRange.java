package org.snomed.snowstorm.ecl.domain.refinement;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static java.lang.Long.parseLong;

class AttributeRange {

	private final boolean attributeTypeWildcard;
	private final Set<String> possibleAttributeTypes;
	private final List<Long> possibleAttributeValues;
	private final Integer cardinalityMin;
	private final Integer cardinalityMax;
	private final Optional<Page<Long>> attributeTypesOptional;

	AttributeRange(boolean attributeTypeWildcard, Optional<Page<Long>> attributeTypesOptional, Set<String> possibleAttributeTypes, List<Long> possibleAttributeValues, Integer cardinalityMin, Integer cardinalityMax) {
		this.attributeTypeWildcard = attributeTypeWildcard;
		this.attributeTypesOptional = attributeTypesOptional;
		this.possibleAttributeTypes = possibleAttributeTypes;
		this.possibleAttributeValues = possibleAttributeValues;
		this.cardinalityMin = cardinalityMin;
		this.cardinalityMax = cardinalityMax;
	}

	boolean isTypeWithinRange(String conceptId) {
		return attributeTypeWildcard || possibleAttributeTypes.contains(conceptId);
	}

	boolean isValueWithinRange(String conceptAttributeValue) {
		return possibleAttributeValues == null || possibleAttributeValues.contains(parseLong(conceptAttributeValue));
	}

	Optional<Page<Long>> getAttributeTypesOptional() {
		return attributeTypesOptional;
	}

	Set<String> getPossibleAttributeTypes() {
		return possibleAttributeTypes;
	}

	List<Long> getPossibleAttributeValues() {
		return possibleAttributeValues;
	}

	Integer getCardinalityMin() {
		return cardinalityMin;
	}

	Integer getCardinalityMax() {
		return cardinalityMax;
	}

}
