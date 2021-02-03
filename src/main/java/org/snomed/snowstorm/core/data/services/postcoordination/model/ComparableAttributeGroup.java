package org.snomed.snowstorm.core.data.services.postcoordination.model;

import org.snomed.languages.scg.domain.model.Attribute;
import org.snomed.languages.scg.domain.model.AttributeGroup;
import org.snomed.languages.scg.domain.model.AttributeValue;
import org.snomed.snowstorm.core.util.CollectionComparators;

import java.util.*;

public class ComparableAttributeGroup extends AttributeGroup implements Comparable<ComparableAttributeGroup> {

	private Set<ComparableAttribute> sortedAttributes;

	public ComparableAttributeGroup() {
	}

	public ComparableAttributeGroup(AttributeGroup attributeGroup) {
		setAttributes(attributeGroup.getAttributes());
	}

	public void addAttribute(String typeId, String destinationId) {
		if (sortedAttributes == null) {
			sortedAttributes = new TreeSet<>();
		}
		Attribute attribute = new Attribute();
		attribute.setAttributeId(typeId);
		AttributeValue attributeValue = new AttributeValue();
		attributeValue.setConceptId(destinationId);
		attribute.setAttributeValue(attributeValue);
		sortedAttributes.add(new ComparableAttribute(attribute));
	}

	public void addAttribute(Attribute attribute) {
		if (sortedAttributes == null) {
			sortedAttributes = new TreeSet<>();
		}
		sortedAttributes.add(new ComparableAttribute(attribute));
	}

	@Override
	public void setAttributes(List<Attribute> attributes) {
		if (attributes == null) {
			sortedAttributes = null;
		} else {
			sortedAttributes = new TreeSet<>();
			for (Attribute attribute : attributes) {
				sortedAttributes.add(new ComparableAttribute(attribute));
			}
		}
	}

	@Override
	public List<Attribute> getAttributes() {
		return sortedAttributes == null ? null : Collections.unmodifiableList(new ArrayList<>(sortedAttributes));
	}

	private Set<ComparableAttribute> getSortedAttributes() {
		return sortedAttributes;
	}

	@Override
	public int compareTo(ComparableAttributeGroup other) {
		return CollectionComparators.compareSets(getSortedAttributes(), other.getSortedAttributes());
	}
}
