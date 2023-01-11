package org.snomed.snowstorm.core.data.services.postcoordination.transformation;

import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.core.data.domain.Concepts;
import org.snomed.snowstorm.core.data.domain.Relationship;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.snomed.snowstorm.core.data.services.postcoordination.ExpressionContext;
import org.snomed.snowstorm.core.data.services.postcoordination.TransformationException;
import org.snomed.snowstorm.core.data.services.postcoordination.model.ComparableAttribute;
import org.snomed.snowstorm.core.data.services.postcoordination.model.ComparableAttributeGroup;
import org.snomed.snowstorm.core.data.services.postcoordination.model.ComparableAttributeValue;
import org.snomed.snowstorm.core.data.services.postcoordination.model.ComparableExpression;

import java.util.*;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static org.snomed.snowstorm.core.data.domain.Concepts.LATERALITY;

public class LateraliseProcedureTransformation implements ExpressionTransformation {

	@Override
	public ComparableExpression transform(List<ComparableAttribute> looseAttributes, ComparableExpression expression, ExpressionContext context) throws ServiceException {
		for (ComparableAttribute looseAttribute : looseAttributes) {
			if (looseAttribute.getAttributeId().equals(LATERALITY) && context.getAncestorsAndSelfOrFocusConcept().contains(Concepts.PROCEDURE)) {

				// TODO: Can other attributes have a body structure that is lateralisable?
				// Otherwise the short version is: Finding site attributes contain exactly one unique body structure AND that is in the Lateralisable refset.
				// No other attributes point to any other body structure

				// Count existing finding sites
				String focusConceptId = expression.getFocusConcepts().get(0);
				Set<String> existingUniqueProcedureSites = new HashSet<>(context.ecl(format("%s.<<363704007 |Procedure site (attribute)|", focusConceptId)));
				if (existingUniqueProcedureSites.size() > 1) {
					throwCriteriaNotMetWithReason("The focus concept has multiple procedure sites.");
				}
				if (existingUniqueProcedureSites.size() < 1) {
					throwCriteriaNotMetWithReason("The focus concept has no procedure site.");
				}
				// Check existing procedure site is lateralizable
				String existingFindingSite = existingUniqueProcedureSites.iterator().next();
				if (context.ecl(format("%s AND ^723264001 |Lateralisable body structure reference set|", existingFindingSite)).isEmpty()) {
					throwCriteriaNotMetWithReason("The focus concept has a procedure site that is not lateralizable.");
					break;
				}
				// No other attributes point to other body structures
				Set<String> bodyStructuresFromAllAttributes = new HashSet<>(context.ecl(format("(%s.*) AND " +
						"<< 442083009 |Anatomical or acquired body structure (body structure)|", focusConceptId)));
				if (bodyStructuresFromAllAttributes.size() != 1) {
					throwCriteriaNotMetWithReason("The focus concept has one or more attributes using a different body structure.");
					break;
				}

				Set<String> procedureSiteAttributes = context.ecl("<<363704007 |Procedure site (attribute)|");
				Concept concept = context.getFocusConceptWithActiveRelationships();
				Set<Integer> groupsToExtract = concept.getRelationships().stream()
						.filter(relationship -> procedureSiteAttributes.contains(relationship.getTypeId()))
						.map(Relationship::getGroupId)
						.collect(Collectors.toSet());
				Map<Integer, ComparableAttributeGroup> expressionGroups = new HashMap<>();
				for (Relationship relationship : concept.getRelationships()) {
					if (groupsToExtract.contains(relationship.getGroupId())) {
						ComparableAttributeGroup expressionGroup = expressionGroups.computeIfAbsent(relationship.getGroupId(), (group) -> new ComparableAttributeGroup());
						String typeId = relationship.getTypeId();
						if (procedureSiteAttributes.contains(typeId)) {
							ComparableExpression nestedExpression = new ComparableExpression(relationship.getDestinationId());
							nestedExpression.addAttribute(LATERALITY, looseAttribute.getAttributeValueId());
							expressionGroup.addAttribute(new ComparableAttribute(typeId, new ComparableAttributeValue(nestedExpression)));
						} else {
							expressionGroup.addAttribute(typeId, relationship.getDestinationId());
						}
					}
				}
				expression.getComparableAttributes().remove(looseAttribute);
				for (ComparableAttributeGroup expressionGroup : expressionGroups.values()) {
					expression.addAttributeGroup(expressionGroup);
				}
			}
		}
		return expression;
	}

	private static void throwCriteriaNotMetWithReason(String reason) throws TransformationException {
		throw new TransformationException(format("Lateralised clinical finding expression can not be transformed to classifiable form because it does not match the " +
				"level 2 safety criteria: %s", reason));
	}

}
