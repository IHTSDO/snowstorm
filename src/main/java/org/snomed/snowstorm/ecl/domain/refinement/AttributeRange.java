package org.snomed.snowstorm.ecl.domain.refinement;

import org.snomed.langauges.ecl.domain.filter.TypedSearchTerm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

class AttributeRange {

	private final boolean attributeTypeWildcard;
	private final List<Long> attributeTypeIds;
	private final Set<String> possibleAttributeTypes;
	private final Integer cardinalityMin;
	private final Integer cardinalityMax;
	private final String operator;
	private List<String> possibleAttributeValues;
	private boolean isConcrete;
	private boolean isNumeric;
	private boolean isSearchTerm;
	private Set<String> concreteStringValues;
	private Float concreteNumberValue;

	private AttributeRange(boolean attributeTypeWildcard, List<Long> attributeTypeIds, Set<String> possibleAttributeTypes,
				String operator, Integer cardinalityMin, Integer cardinalityMax) {

		this.attributeTypeWildcard = attributeTypeWildcard;
		this.attributeTypeIds = attributeTypeIds;
		this.possibleAttributeTypes = possibleAttributeTypes;
		this.operator = operator;
		this.cardinalityMin = cardinalityMin;
		this.cardinalityMax = cardinalityMax;
	}

	public static AttributeRange newConceptRange(boolean attributeTypeWildcard, List<Long> attributeTypeIds, Set<String> attributeTypeFields, String operator,
				List<String> possibleAttributeValues, Integer cardinalityMin, Integer cardinalityMax) {

		final AttributeRange range = new AttributeRange(attributeTypeWildcard, attributeTypeIds, attributeTypeFields, operator, cardinalityMin, cardinalityMax);
		range.possibleAttributeValues = possibleAttributeValues;
		return range;
	}

	public static AttributeRange newConcreteNumberRange(boolean attributeTypeWildcard, List<Long> attributeTypeIds, Set<String> attributeTypeFields,
				String operator, String concreteNumberValue, Integer cardinalityMin, Integer cardinalityMax) {

		final AttributeRange range = new AttributeRange(attributeTypeWildcard, attributeTypeIds, attributeTypeFields, operator, cardinalityMin, cardinalityMax);
		range.isConcrete = true;
		range.isNumeric = true;
		range.concreteNumberValue = Float.parseFloat(concreteNumberValue);
		range.possibleAttributeValues = Collections.singletonList(concreteNumberValue);
		return range;
	}

	public static AttributeRange newConcreteStringRange(boolean attributeTypeWildcard, List<Long> attributeTypeIds, Set<String> attributeTypeFields,
				String operator, List<TypedSearchTerm> typedSearchTerms, Integer cardinalityMin, Integer cardinalityMax) {

		final AttributeRange range = new AttributeRange(attributeTypeWildcard, attributeTypeIds, attributeTypeFields, operator, cardinalityMin, cardinalityMax);
		range.isConcrete = true;
		range.isSearchTerm = true;
		range.concreteStringValues = typedSearchTerms.stream().map(TypedSearchTerm::getTerm).collect(Collectors.toSet());
		range.possibleAttributeValues = new ArrayList<>(range.concreteStringValues);
		return range;
	}

	boolean isTypeWithinRange(String conceptId) {
		return attributeTypeWildcard || possibleAttributeTypes.contains(conceptId);
	}

	boolean isValueWithinRange(Object conceptAttributeValue) {
		if (!isConcrete) {
			return operator.equals("=") == ( possibleAttributeValues == null || possibleAttributeValues.contains(conceptAttributeValue.toString()));
		} else {
			if (isNumeric) {
				if (!(conceptAttributeValue instanceof String)) {
					final float attributeValue = Float.parseFloat(conceptAttributeValue.toString());
					final int i = Float.compare(attributeValue, concreteNumberValue);
					switch (operator) {
						case "=":
							return i == 0;
						case "!=":
							return i != 0;
						case ">=":
							return i == 0 || i > 0;
						case ">":
							return i > 0;
						case "<=":
							return i == 0 || i < 0;
						case "<":
							return i < 0;
					}
				}
				return false;
			} else {
				if (conceptAttributeValue instanceof String) {
					return concreteStringValues.contains(conceptAttributeValue);
				}
				return false;
			}
		}
	}

	List<Long> getAttributeTypeIds() {
		return attributeTypeIds;
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

	public String getOperator() {
		return operator;
	}

	public boolean isNumericQuery() {
		return isNumeric;
	}

}
