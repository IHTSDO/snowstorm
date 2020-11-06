package org.snomed.snowstorm.core.data.services.postcoordination.model;

import org.snomed.languages.scg.domain.model.Attribute;
import org.snomed.languages.scg.domain.model.AttributeGroup;
import org.snomed.languages.scg.domain.model.Expression;
import org.snomed.snowstorm.core.util.CollectionComparators;

import java.util.*;

import static java.util.Comparator.comparing;
import static java.util.Comparator.nullsFirst;

public class ComparableExpression extends Expression implements Comparable<ComparableExpression> {

	private static final Comparator<ComparableExpression> COMPARATOR =
			nullsFirst(comparing(ComparableExpression::getDefinitionStatusString)
					.thenComparing(nullsFirst(comparing(ComparableExpression::getFocusConcepts, CollectionComparators::compareLists)))
					.thenComparing(nullsFirst(comparing(ComparableExpression::getComparableAttributes, CollectionComparators::compareSets)))
					.thenComparing(nullsFirst(comparing(ComparableExpression::getComparableAttributeGroups, CollectionComparators::compareSets))));

	private Set<String> sortedFocusConcepts;
	private Set<ComparableAttribute> comparableAttributes;
	private Set<ComparableAttributeGroup> comparableAttributeGroups;

	public ComparableExpression(Expression expression) {
		setDefinitionStatus(expression.getDefinitionStatus());
		setFocusConcepts(expression.getFocusConcepts());
		setAttributes(expression.getAttributes());
		setAttributeGroups(expression.getAttributeGroups());
	}

	@Override
	public void setFocusConcepts(List<String> focusConcepts) {
		if (focusConcepts == null) {
			this.sortedFocusConcepts = null;
		} else {
			this.sortedFocusConcepts = new TreeSet<>(focusConcepts);
		}
	}

	@Override
	public List<String> getFocusConcepts() {
		return sortedFocusConcepts == null ? null : Collections.unmodifiableList(new ArrayList<>(sortedFocusConcepts));
	}

	@Override
	public void setAttributes(List<Attribute> attributes) {
		if (attributes == null) {
			comparableAttributes = null;
		} else {
			this.comparableAttributes = new TreeSet<>();
			for (Attribute attribute : attributes) {
				this.comparableAttributes.add(new ComparableAttribute(attribute));
			}
		}
	}

	@Override
	public List<Attribute> getAttributes() {
		return comparableAttributes == null ? null : Collections.unmodifiableList(new ArrayList<>(this.comparableAttributes));
	}

	@Override
	public void setAttributeGroups(Set<AttributeGroup> groups) {
		if (groups == null) {
			comparableAttributeGroups = null;
		} else {
			this.comparableAttributeGroups = new TreeSet<>();
			for (AttributeGroup group : groups) {
				this.comparableAttributeGroups.add(new ComparableAttributeGroup(group));
			}
		}
	}

	@Override
	public Set<AttributeGroup> getAttributeGroups() {
		return comparableAttributeGroups == null ? null : Collections.unmodifiableSet(new HashSet<>(comparableAttributeGroups));
	}

	private String getDefinitionStatusString() {
		return getDefinitionStatus().toString();
	}

	private Set<ComparableAttribute> getComparableAttributes() {
		return comparableAttributes;
	}

	private Set<ComparableAttributeGroup> getComparableAttributeGroups() {
		return comparableAttributeGroups;
	}

	@Override
	public int compareTo(ComparableExpression other) {
		return COMPARATOR.compare(this, other);
	}
}
