package org.snomed.snowstorm.core.data.services.postcoordination.model;

import org.snomed.languages.scg.domain.model.Attribute;
import org.snomed.languages.scg.domain.model.AttributeValue;

import java.util.Comparator;
import java.util.Set;

import static java.util.Comparator.*;

public class ComparableAttribute extends Attribute implements Comparable<ComparableAttribute> {

	private static final Comparator<ComparableAttribute> COMPARATOR =
			nullsFirst(comparing(ComparableAttribute::getAttributeId))
					.thenComparing(ComparableAttribute::getAttributeValueId, Comparator.nullsLast(naturalOrder()))
					.thenComparing(ComparableAttribute::getComparableAttributeValue, Comparator.nullsLast(naturalOrder()));

	public ComparableAttribute(Attribute attribute) {
		setAttributeId(attribute.getAttributeId());
		setAttributeValue(attribute.getAttributeValue());
	}

	public ComparableAttribute(String typeId, String destinationId) {
		setAttributeId(typeId);
		setAttributeValue(new ComparableAttributeValue(destinationId));
	}

	public ComparableAttribute(String typeId, ComparableAttributeValue value) {
		setAttributeId(typeId);
		setAttributeValue(value);
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

	public void getAllConceptIds(Set<String> ids) {
		ids.add(getAttributeId());
		if (getAttributeValueId() != null) {
			ids.add(getAttributeValueId());
		}
		final ComparableAttributeValue value = getComparableAttributeValue();
		if (value != null) {
			value.getAllConceptIds(ids);
		}
	}
}
