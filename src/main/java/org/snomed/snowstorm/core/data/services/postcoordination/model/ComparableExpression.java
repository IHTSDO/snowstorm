package org.snomed.snowstorm.core.data.services.postcoordination.model;

import org.snomed.languages.scg.domain.model.Attribute;
import org.snomed.languages.scg.domain.model.AttributeGroup;
import org.snomed.languages.scg.domain.model.Expression;
import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.core.data.domain.Concepts;
import org.snomed.snowstorm.core.data.domain.Relationship;
import org.snomed.snowstorm.core.util.CollectionComparators;

import java.util.*;
import java.util.stream.Collectors;

import static java.util.Comparator.naturalOrder;
import static java.util.Comparator.nullsFirst;

public class ComparableExpression extends Expression implements Comparable<ComparableExpression> {

	private static final Comparator<ComparableExpression> COMPARATOR =
			Comparator.comparing(ComparableExpression::getDefinitionStatusString, nullsFirst(naturalOrder()))
					.thenComparing(ComparableExpression::getFocusConcepts, nullsFirst(CollectionComparators::compareLists))
					.thenComparing(ComparableExpression::getComparableAttributes, nullsFirst(CollectionComparators::compareSets))
					.thenComparing(ComparableExpression::getComparableAttributeGroups, nullsFirst(CollectionComparators::compareSets));

	private Long expressionId;
	private Set<String> sortedFocusConcepts;
	private Set<ComparableAttribute> comparableAttributes;
	private Set<ComparableAttributeGroup> comparableAttributeGroups;
	private final Set<String> mergedConcepts;
	private Set<Long> equivalentConcepts;
	private Set<Long> equivalentExpressions;
	private Set<Long> classificationAncestors;

	public ComparableExpression() {
		mergedConcepts = new HashSet<>();
	}

	public ComparableExpression(Expression expression) {
		this();
		setDefinitionStatus(expression.getDefinitionStatus());
		setFocusConcepts(expression.getFocusConcepts());
		setAttributes(expression.getAttributes());
		setAttributeGroups(expression.getAttributeGroups());
	}

	public ComparableExpression(String... focusConceptIds) {
		this();
		setFocusConcepts(Arrays.stream(focusConceptIds).collect(Collectors.toList()));
	}

	public void merge(ComparableExpression otherExpression) {
		for (String focusConcept : otherExpression.getFocusConcepts()) {
			addFocusConcept(focusConcept);
		}
		if (otherExpression.getComparableAttributes() != null) {
			for (ComparableAttribute attribute : otherExpression.getComparableAttributes()) {
				addAttribute(attribute);
			}
		}
		if (otherExpression.getComparableAttributeGroups() != null) {
			for (ComparableAttributeGroup group : otherExpression.getComparableAttributeGroups()) {
				addAttributeGroup(group);
			}
		}
	}

	public void merge(Concept concept) {
		final String conceptId = concept.getConceptId();
		if (!getFocusConcepts().contains(conceptId)) {
			addFocusConcept(conceptId);
		}
		Map<Integer, Set<Relationship>> relationshipGroups = new HashMap<>();
		for (Relationship relationship : concept.getActiveInferredRelationships()) {
			relationshipGroups.computeIfAbsent(relationship.getGroupId(), (id) -> new HashSet<>()).add(relationship);
		}
		for (Map.Entry<Integer, Set<Relationship>> group : relationshipGroups.entrySet()) {
			if (group.getKey() == 0) {
				for (Relationship relationship : group.getValue()) {
					if (!relationship.getTypeId().equals(Concepts.ISA)) {
						addAttribute(relationship.getTypeId(), relationship.getDestinationId());
					}
				}
			} else {
				ComparableAttributeGroup attributeGroup = new ComparableAttributeGroup(group.getKey());
				for (Relationship relationship : group.getValue()) {
					attributeGroup.addAttribute(relationship.getTypeId(), relationship.getDestinationId());
				}
				addAttributeGroup(attributeGroup);
			}
		}
		mergedConcepts.add(conceptId);
	}

	public void addFocusConcept(String conceptId) {
		if (sortedFocusConcepts == null) {
			sortedFocusConcepts = new TreeSet<>();
		}
		sortedFocusConcepts.add(conceptId);
	}

	public void addAttribute(ComparableAttribute attribute) {
		if (comparableAttributes == null) {
			comparableAttributes = new TreeSet<>();
		}
		comparableAttributes.add(attribute);
	}

	public void addAttribute(String typeId, String destinationId) {
		addAttribute(new ComparableAttribute(typeId, destinationId));
	}

	public void addAttributeGroup(ComparableAttributeGroup attributeGroup) {
		if (comparableAttributeGroups == null) {
			comparableAttributeGroups = new TreeSet<>();
		}
		comparableAttributeGroups.add(new ComparableAttributeGroup(attributeGroup));
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
		return sortedFocusConcepts == null ? null : List.copyOf(sortedFocusConcepts);
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
		return comparableAttributes == null ? null : List.copyOf(this.comparableAttributes);
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
		return comparableAttributeGroups == null ? null : Collections.unmodifiableSet(comparableAttributeGroups);
	}

	private String getDefinitionStatusString() {
		return getDefinitionStatus() != null ? getDefinitionStatus().toString() : null;
	}

	public Set<ComparableAttribute> getComparableAttributes() {
		return comparableAttributes;
	}

	public Set<ComparableAttributeGroup> getComparableAttributeGroups() {
		return comparableAttributeGroups;
	}

	public Set<Long> getEquivalentConcepts() {
		return equivalentConcepts;
	}

	public void setEquivalentConcepts(Set<Long> equivalentConcepts) {
		this.equivalentConcepts = equivalentConcepts;
	}

	public Set<Long> getEquivalentExpressions() {
		return equivalentExpressions;
	}

	public void setEquivalentExpressions(Set<Long> equivalentExpressions) {
		this.equivalentExpressions = equivalentExpressions;
	}

	@Override
	public int compareTo(ComparableExpression other) {
		return COMPARATOR.compare(this, other);
	}

	public boolean isConceptMerged(String conceptId) {
		return mergedConcepts.contains(conceptId);
	}

	public Set<String> getAllConceptIds() {
		Set<String> ids = new HashSet<>();
		getAllConceptIds(ids);
		return ids;
	}

	public String toStringCanonical() {
		return toString().replace(" ", "");
	}

	@Override
	public String toString() {
		return super.toString();
	}

	public void getAllConceptIds(Set<String> ids) {
		final List<String> focusConcepts = getFocusConcepts();
		if (focusConcepts != null) {
			ids.addAll(focusConcepts);
		}
		if (comparableAttributes != null) {
			for (ComparableAttribute attribute : comparableAttributes) {
				attribute.getAllConceptIds(ids);
			}
		}
		if (comparableAttributeGroups != null) {
			for (ComparableAttributeGroup group : comparableAttributeGroups) {
				group.getAllConceptIds(ids);
			}
		}
	}

	public Long getExpressionId() {
		return expressionId;
	}

	public ComparableExpression setExpressionId(Long expressionId) {
		this.expressionId = expressionId;
		return this;
	}

	public Set<Long> getClassificationAncestors() {
		return classificationAncestors;
	}

	public void setClassificationAncestors(Set<Long> classificationAncestors) {
		this.classificationAncestors = classificationAncestors;
	}
}
