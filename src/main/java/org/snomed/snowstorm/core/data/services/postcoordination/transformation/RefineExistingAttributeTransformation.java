package org.snomed.snowstorm.core.data.services.postcoordination.transformation;

import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.core.data.domain.Relationship;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.snomed.snowstorm.core.data.services.postcoordination.ExpressionContext;
import org.snomed.snowstorm.core.data.services.postcoordination.model.ComparableAttribute;
import org.snomed.snowstorm.core.data.services.postcoordination.model.ComparableAttributeGroup;
import org.snomed.snowstorm.core.data.services.postcoordination.model.ComparableExpression;

import java.util.*;
import java.util.stream.Collectors;

public class RefineExistingAttributeTransformation implements ExpressionTransformation {

	@Override
	public ComparableExpression transform(List<ComparableAttribute> looseAttributes, ComparableExpression expression, ExpressionContext context) throws ServiceException {
		for (ComparableAttribute looseAttribute : looseAttributes) {
			Set<String> attributeTypeAncestorsAndSelf = context.ecl(">>" + looseAttribute.getAttributeId());
			String attributeValueId = looseAttribute.getAttributeValue().isNested() ? looseAttribute.getAttributeValue().getNestedExpression().getFocusConcepts().get(0) :
					looseAttribute.getAttributeValueId();
			Set<String> attributeValueAncestorsAndSelf = context.ecl(">>" + attributeValueId);

			// Collect existing attributes that could be refined
			Concept concept = context.getFocusConceptWithActiveRelationships();
			List<Relationship> refinedRelationshipCandidates = new ArrayList<>();
			for (Relationship relationship : concept.getRelationships()) {
				if (attributeTypeAncestorsAndSelf.contains(relationship.getTypeId()) && attributeValueAncestorsAndSelf.contains(relationship.getDestinationId())) {
					refinedRelationshipCandidates.add(relationship);
				}
			}

			if (refinedRelationshipCandidates.isEmpty()) {
				// No existing relationships matched for refinement
				continue;
			}
			if (refinedRelationshipCandidates.size() > 1) {
				// Check that refined relationships have the same type and value when from different role groups
				String typeId = refinedRelationshipCandidates.get(0).getTypeId();
				String destinationId = refinedRelationshipCandidates.get(0).getDestinationId();
				boolean notEqual = false;
				for (Relationship candidate : refinedRelationshipCandidates) {
					if (!candidate.getTypeId().equals(typeId) || !candidate.getDestinationId().equals(destinationId)) {
						// Not all matched relationships have the same type and value
						notEqual = true;
						break;
					}
				}
				if (notEqual) {
					continue;
				}
			}

			// Refine matched relationships by copying their groups into the classifiable expression
			Set<Integer> groupsToCopy = refinedRelationshipCandidates.stream().map(Relationship::getGroupId).collect(Collectors.toSet());
			Map<Integer, ComparableAttributeGroup> groupsMap = new HashMap<>();
			for (Relationship relationship : concept.getRelationships()) {
				int groupId = relationship.getGroupId();
				if (groupsToCopy.contains(groupId)) {
					if (groupId == 0) {
						// Don't copy group 0 (ungrouped) relationships other than those being refined
						if (refinedRelationshipCandidates.contains(relationship)) {
							expression.addAttribute(looseAttribute);
						}
					} else {
						// Copy all relationships where one relationship within the group is being refined
						ComparableAttributeGroup expressionGroup = groupsMap.computeIfAbsent(groupId, id -> new ComparableAttributeGroup());
						if (refinedRelationshipCandidates.contains(relationship)) {
							expressionGroup.addAttribute(looseAttribute);
						} else {
							expressionGroup.addAttribute(relationship.getTypeId(), relationship.getDestinationId());
						}
					}
				}
			}
			for (ComparableAttributeGroup group : groupsMap.values()) {
				expression.addAttributeGroup(group);
			}
			expression.getComparableAttributes().remove(looseAttribute);
		}
		return expression;
	}

}
