package org.snomed.snowstorm.core.data.services.postcoordination.model;

import org.snomed.languages.scg.domain.model.Attribute;
import org.snomed.languages.scg.domain.model.AttributeValue;

import java.util.Comparator;

import static java.util.Comparator.comparing;
import static java.util.Comparator.nullsFirst;

public class ComparableAttribute extends Attribute implements Comparable<ComparableAttribute> {

	private static final Comparator<ComparableAttribute> COMPARATOR =
			nullsFirst(comparing(ComparableAttribute::getAttributeId))
					.thenComparing(nullsFirst(comparing(ComparableAttribute::getAttributeValueId))
					.thenComparing(nullsFirst(comparing(ComparableAttribute::getComparableAttributeValue))));

	public ComparableAttribute(Attribute attribute) {
		setAttributeId(attribute.getAttributeId());
		setAttributeValue(attribute.getAttributeValue());
	}

	@Override
	public void setAttributeValue(AttributeValue attributeValue) {
		super.setAttributeValue(attributeValue == null ? null : new ComparableAttributeValue(attributeValue));
	}

	public ComparableAttributeValue getComparableAttributeValue() {
		return (ComparableAttributeValue) super.getAttributeValue();
	}

	@Override
	public int compareTo(ComparableAttribute other) {
		return COMPARATOR.compare(this, other);
	}
}
