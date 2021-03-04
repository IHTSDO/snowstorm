package org.snomed.snowstorm.core.data.services.postcoordination.model;

import org.snomed.languages.scg.domain.model.AttributeValue;

import java.util.Comparator;
import java.util.Set;

import static java.util.Comparator.comparing;
import static java.util.Comparator.nullsFirst;

public class ComparableAttributeValue extends AttributeValue implements Comparable<ComparableAttributeValue> {

	private static final Comparator<ComparableAttributeValue> COMPARATOR =
			comparing(ComparableAttributeValue::getConceptId, nullsFirst(String::compareTo))
					.thenComparing(ComparableAttributeValue::getComparableNestedExpression, nullsFirst(ComparableExpression::compareTo));

	public ComparableAttributeValue(AttributeValue attributeValue) {
		setConceptId(attributeValue.getConceptId());
		if (attributeValue.getNestedExpression() != null) {
			setNestedExpression(new ComparableExpression(attributeValue.getNestedExpression()));
		}
	}

	public ComparableAttributeValue(String destinationId) {
		setConceptId(destinationId);
	}

	public ComparableAttributeValue(ComparableExpression nestedExpression) {
		setNestedExpression(nestedExpression);
	}

	private ComparableExpression getComparableNestedExpression() {
		return (ComparableExpression) super.getNestedExpression();
	}

	@Override
	public int compareTo(ComparableAttributeValue other) {
		try {
			return COMPARATOR.compare(this, other);
		} catch (NullPointerException e) {
			throw e;
		}
	}

	public void getAllConceptIds(Set<String> ids) {
		if (getConceptId() != null) {
			ids.add(getConceptId());
		}
		final ComparableExpression nested = getComparableNestedExpression();
		if (nested != null) {
			nested.getAllConceptIds(ids);
		}
	}
}
