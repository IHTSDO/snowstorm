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
import static org.snomed.snowstorm.core.data.domain.Concepts.FINDING_SITE;
import static org.snomed.snowstorm.core.data.domain.Concepts.LATERALITY;

public class LateraliseClinicalFindingTransformation implements ExpressionTransformation {

	@Override
	public ComparableExpression transform(List<ComparableAttribute> looseAttributes, ComparableExpression expression, ExpressionContext context) throws ServiceException {
		for (ComparableAttribute looseAttribute : looseAttributes) {
			if (looseAttribute.getAttributeId().equals(LATERALITY) && context.getAncestorsAndSelfOrFocusConcept().contains(Concepts.CLINICAL_FINDING)) {

				// Transformation is allowed when:
				// - The focus concept
				//   - includes only one role group with a |Finding site| attribute OR
				//   - includes two or more role groups with a |Finding site| attribute WHERE
				//     the value of the finding site attribute in each role group is the same concept
				//   - includes no attributes with values that are lateralized anatomical structures
				// - The value of the finding site attribute is a member of the 723264001 | Lateralizable body structure reference set|
				//   - i.e. the associated body structure is lateralizable, and
				//   - the associated body structure does not already state a laterality

				// TODO: Can other attributes have a body structure that is lateralisable?
				// Otherwise the short version is: Finding site attributes contain exactly one unique body structure AND that is in the Lateralisable refset.
				// No other attributes point to any other body structure

				// Count existing finding sites
				String focusConceptId = expression.getFocusConcepts().get(0);
				Set<String> existingUniqueFindingSites = new HashSet<>(context.ecl(format("%s.<<363698007 |Finding site (attribute)|", focusConceptId)));
				if (existingUniqueFindingSites.size() > 1) {
					throwCriteriaNotMetWithReason("The focus concept has multiple finding sites.");
				}
				if (existingUniqueFindingSites.size() < 1) {
					throwCriteriaNotMetWithReason("The focus concept has no finding site.");
				}
				// Check existing finding site is lateralizable
				String existingFindingSite = existingUniqueFindingSites.iterator().next();
				if (context.ecl(format("%s AND ^723264001 |Lateralisable body structure reference set|", existingFindingSite)).isEmpty()) {
					throwCriteriaNotMetWithReason("The focus concept has a finding site that is not lateralizable.");
					break;
				}
				// No other attributes point to other body structures
				Set<String> bodyStructuresFromAllAttributes = new HashSet<>(context.ecl(format("(%s.*) AND " +
						"<< 442083009 |Anatomical or acquired body structure (body structure)|", focusConceptId)));
				if (bodyStructuresFromAllAttributes.size() != 1) {
					throwCriteriaNotMetWithReason("The focus concept has one or more attributes using a different body structure.");
					break;
				}

				Concept concept = context.getFocusConceptWithActiveRelationships();
				Set<Integer> groupsToExtract = concept.getRelationships().stream()
						.filter(relationship -> relationship.getTypeId().equals(Concepts.FINDING_SITE))
						.map(Relationship::getGroupId)
						.collect(Collectors.toSet());
				Map<Integer, ComparableAttributeGroup> expressionGroups = new HashMap<>();
				for (Relationship relationship : concept.getRelationships()) {
					if (groupsToExtract.contains(relationship.getGroupId())) {
						ComparableAttributeGroup expressionGroup = expressionGroups.computeIfAbsent(relationship.getGroupId(), (group) -> new ComparableAttributeGroup());
						String typeId = relationship.getTypeId();
						if (typeId.equals(FINDING_SITE)) {
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
