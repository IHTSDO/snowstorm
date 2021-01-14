package org.snomed.snowstorm.ecl.domain.refinement;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.Optional;
import java.util.Set;

class AttributeRange {

	private final boolean attributeTypeWildcard;
	private final Set<String> possibleAttributeTypes;
	private final List<String> possibleAttributeValues;
	private final Integer cardinalityMin;
	private final Integer cardinalityMax;
	private final Optional<Page<Long>> attributeTypesOptional;
	private String concreteValueOperator;
	private boolean isNumeric;

	AttributeRange(boolean attributeTypeWildcard, Optional<Page<Long>> attributeTypesOptional, Set<String> possibleAttributeTypes, List<String> possibleAttributeValues, Integer cardinalityMin, Integer cardinalityMax) {
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
		return possibleAttributeValues == null || possibleAttributeValues.contains(conceptAttributeValue);
	}

	Optional<Page<Long>> getAttributeTypesOptional() {
		return attributeTypesOptional;
	}

	Set<String> getPossibleAttributeTypes() {
		return possibleAttributeTypes;
	}

	List<String> getPossibleAttributeValues() {
		return possibleAttributeValues;
	}

	Integer getCardinalityMin() {
		return cardinalityMin;
	}

	Integer getCardinalityMax() {
		return cardinalityMax;
	}

	public void setConcreteValueOperator(String comparator) {
		this.concreteValueOperator = comparator;
	}

	public String getConcreteValueOperator() {
		return concreteValueOperator;
	}

	public boolean isNumericQuery() {
		return isNumeric;
	}

	public void setNumericQuery(boolean isNumeric) {
		this.isNumeric = isNumeric;
	}
}
