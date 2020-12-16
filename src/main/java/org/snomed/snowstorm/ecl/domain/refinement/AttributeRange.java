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
	private String concreteValueComparator;
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
		if (possibleAttributeValues == null) {
			return true;
		}
		if (getConcreteValueComparator() == null) {
			return possibleAttributeValues.contains(conceptAttributeValue);
		}
		// concrete value comparison
		if (isNumeric()) {
			if (!conceptAttributeValue.startsWith("#")) {
				return false;
			}
			Float concreteValue = Float.valueOf(conceptAttributeValue.substring(1));
			for (String value : possibleAttributeValues) {
				// TODO make this better
				Float floatValue = Float.valueOf(value);
				if (">".equals(getConcreteValueComparator())) {
					return concreteValue > floatValue;
				} else if (">=".equals(getConcreteValueComparator())) {
					return concreteValue >= floatValue;
				} else if ("<=".equals(getConcreteValueComparator())) {
					return concreteValue <= floatValue;
				} else if ("<".equals(getConcreteValueComparator())) {
					return concreteValue < floatValue;
				} else if ("!=".equals(getConcreteValueComparator())) {
					return concreteValue > floatValue || concreteValue < floatValue;
				}
			}
		} else {
			if (conceptAttributeValue.startsWith("#")) {
				return false;
			}
			// must be !=
			if ("!=".equals(getConcreteValueComparator())) {
				for (String value : possibleAttributeValues) {
					return !value.equals(conceptAttributeValue);
				}
			} else {
				throw new IllegalArgumentException(String.format("String value comparison operator %s is not supported", getConcreteValueComparator()));
			}
		}
		return false;
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

	public void setConcreteValueComparator(String comparator) {
		this.concreteValueComparator = comparator;
	}

	public String getConcreteValueComparator() {
		return concreteValueComparator;
	}

	public boolean isNumeric() {
		return isNumeric;
	}

	public void setIsNumeric(boolean isNumeric) {
		this.isNumeric = isNumeric;
	}
}
