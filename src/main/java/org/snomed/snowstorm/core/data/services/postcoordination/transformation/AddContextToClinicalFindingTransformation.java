package org.snomed.snowstorm.core.data.services.postcoordination.transformation;

import org.snomed.snowstorm.core.data.domain.Concepts;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.snomed.snowstorm.core.data.services.postcoordination.ExpressionContext;
import org.snomed.snowstorm.core.data.services.postcoordination.model.ComparableAttribute;
import org.snomed.snowstorm.core.data.services.postcoordination.model.ComparableAttributeGroup;
import org.snomed.snowstorm.core.data.services.postcoordination.model.ComparableAttributeValue;
import org.snomed.snowstorm.core.data.services.postcoordination.model.ComparableExpression;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.snomed.snowstorm.core.util.CollectionUtils.orEmpty;

public class AddContextToClinicalFindingTransformation implements ExpressionTransformation {

	public static final Set<String> FINDING_CONTEXT_ATTRIBUTES = Set.of(Concepts.FINDING_CONTEXT, Concepts.TEMPORAL_CONTEXT, Concepts.SUBJECT_RELATIONSHIP_CONTEXT);

	@Override
	public ComparableExpression transform(List<ComparableAttribute> looseAttributes, ComparableExpression expression, ExpressionContext context) throws ServiceException {
		if (context.getAncestorsAndSelfOrFocusConcept().contains(Concepts.CLINICAL_FINDING)) {
			List<ComparableAttribute> contextAttributes = looseAttributes.stream()
					.filter(looseAttribute -> FINDING_CONTEXT_ATTRIBUTES.contains(looseAttribute.getAttributeId())).collect(Collectors.toList());

			if (!contextAttributes.isEmpty()) {
				ComparableExpression situationExpression = new ComparableExpression(Concepts.FINDING_WITH_EXPLICIT_CONTEXT);
				situationExpression.setDefinitionStatus(expression.getDefinitionStatus());
				expression.setDefinitionStatus(null);// Clear definition status before nesting

				ComparableAttributeGroup attributeGroup = new ComparableAttributeGroup();
				for (ComparableAttribute contextAttribute : contextAttributes) {
					expression.getComparableAttributes().remove(contextAttribute);
					attributeGroup.addAttribute(contextAttribute);
				}

				if (orEmpty(expression.getComparableAttributes()).isEmpty() && orEmpty(expression.getComparableAttributeGroups()).isEmpty()) {
					attributeGroup.addAttribute(new ComparableAttribute(Concepts.ASSOCIATED_FINDING, expression.getFocusConcepts().get(0)));
				} else {
					// Move the whole of the original expression into a nested expression
					attributeGroup.addAttribute(new ComparableAttribute(Concepts.ASSOCIATED_FINDING, new ComparableAttributeValue(expression)));
				}
				situationExpression.addAttributeGroup(attributeGroup);

				return situationExpression;
			}
		}
		return expression;
	}

}
