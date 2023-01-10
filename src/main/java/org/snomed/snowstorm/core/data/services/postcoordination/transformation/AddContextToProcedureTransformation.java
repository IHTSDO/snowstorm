package org.snomed.snowstorm.core.data.services.postcoordination.transformation;

import org.snomed.snowstorm.core.data.domain.Concepts;
import org.snomed.snowstorm.core.data.services.QueryService;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.snomed.snowstorm.core.data.services.postcoordination.ExpressionContext;
import org.snomed.snowstorm.core.data.services.postcoordination.model.ComparableAttribute;
import org.snomed.snowstorm.core.data.services.postcoordination.model.ComparableAttributeGroup;
import org.snomed.snowstorm.core.data.services.postcoordination.model.ComparableAttributeValue;
import org.snomed.snowstorm.core.data.services.postcoordination.model.ComparableExpression;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.snomed.snowstorm.core.data.domain.Concepts.ASSOCIATED_PROCEDURE;
import static org.snomed.snowstorm.core.data.domain.Concepts.SITUATION_WITH_EXPLICIT_CONTEXT;

public class AddContextToProcedureTransformation implements ExpressionTransformation {

	public static final Set<String> PROCEDURE_CONTEXT_ATTRIBUTES = Set.of(Concepts.PROCEDURE_CONTEXT, Concepts.TEMPORAL_CONTEXT, Concepts.SUBJECT_RELATIONSHIP_CONTEXT);

	@Override
	public ComparableExpression transform(List<ComparableAttribute> looseAttributes, ComparableExpression expression, ExpressionContext context, QueryService queryService) throws ServiceException {
		if (context.getAncestorsAndSelf().contains(Concepts.PROCEDURE)) {
			List<ComparableAttribute> contextAttributes = looseAttributes.stream()
					.filter(looseAttribute -> PROCEDURE_CONTEXT_ATTRIBUTES.contains(looseAttribute.getAttributeId())).collect(Collectors.toList());

			if (!contextAttributes.isEmpty()) {
				ComparableExpression situationExpression = new ComparableExpression(SITUATION_WITH_EXPLICIT_CONTEXT);
				situationExpression.setDefinitionStatus(expression.getDefinitionStatus());

				ComparableAttributeGroup attributeGroup = new ComparableAttributeGroup();
				for (ComparableAttribute contextAttribute : contextAttributes) {
					expression.getComparableAttributes().remove(contextAttribute);
					attributeGroup.addAttribute(contextAttribute);
				}

				if (expression.getComparableAttributes().isEmpty() || expression.getComparableAttributeGroups().isEmpty()) {
					attributeGroup.addAttribute(new ComparableAttribute(ASSOCIATED_PROCEDURE, expression.getFocusConcepts().get(0)));
				} else {
					// Move the whole of the original expression into a nested expression
					attributeGroup.addAttribute(new ComparableAttribute(ASSOCIATED_PROCEDURE, new ComparableAttributeValue(expression)));
				}
				situationExpression.addAttributeGroup(attributeGroup);

				return situationExpression;
			}
		}
		return expression;
	}

}
