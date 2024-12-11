package org.snomed.snowstorm.core.data.services.postcoordination.transformation;

import org.snomed.languages.scg.domain.model.Attribute;
import org.snomed.snowstorm.core.data.domain.Concepts;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.snomed.snowstorm.core.data.services.postcoordination.ExpressionContext;
import org.snomed.snowstorm.core.data.services.postcoordination.model.ComparableAttribute;
import org.snomed.snowstorm.core.data.services.postcoordination.model.ComparableAttributeGroup;
import org.snomed.snowstorm.core.data.services.postcoordination.model.ComparableAttributeValue;
import org.snomed.snowstorm.core.data.services.postcoordination.model.ComparableExpression;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.snomed.snowstorm.core.data.domain.Concepts.*;
import static org.snomed.snowstorm.core.util.CollectionUtils.orEmpty;

public class AddContextToProcedureTransformation implements ExpressionTransformation {

	public static final Set<String> PROCEDURE_CONTEXT_ATTRIBUTES = Set.of(
			Concepts.PROCEDURE_CONTEXT,
			Concepts.SUBJECT_RELATIONSHIP_CONTEXT,
			Concepts.TEMPORAL_CONTEXT);

	private static final Map<String, String> defaultAttributes = Map.of(
			Concepts.PROCEDURE_CONTEXT, "385658003",//385658003 |Done|
			Concepts.SUBJECT_RELATIONSHIP_CONTEXT, "410604004",// 410604004 |Subject of record|
			Concepts.TEMPORAL_CONTEXT, "410512000"// 410512000 |Current or specified time|
	);

	/*
	===  129125009 |Procedure with explicit context| :
     {  363589002 |Associated procedure|  =  71388002 |Procedure| ,
        408730004 |Procedure context|  =  385658003 |Done| ,
        408732007 |Subject relationship context|  =  410604004 |Subject of record|
        408731000 |Temporal context|  =  410512000 |Current or specified time|  }
	 */

	@Override
	public ComparableExpression transform(List<ComparableAttribute> looseAttributes, ComparableExpression expression, ExpressionContext context) throws ServiceException {
		if (context.getAncestorsAndSelfOrFocusConcept().contains(Concepts.PROCEDURE)) {
			List<ComparableAttribute> contextAttributes = looseAttributes.stream()
					.filter(looseAttribute -> PROCEDURE_CONTEXT_ATTRIBUTES.contains(looseAttribute.getAttributeId())).collect(Collectors.toList());

			if (!contextAttributes.isEmpty()) {
				ComparableExpression situationExpression = new ComparableExpression(PROCEDURE_WITH_EXPLICIT_CONTEXT);
				situationExpression.setDefinitionStatus(expression.getDefinitionStatus());
				expression.setDefinitionStatus(null);// Clear definition status before nesting

				ComparableAttributeGroup attributeGroup = new ComparableAttributeGroup();
				for (ComparableAttribute contextAttribute : contextAttributes) {
					expression.getComparableAttributes().remove(contextAttribute);
					attributeGroup.addAttribute(contextAttribute);
				}
				List<String> attributeGroupAttributeTypes = attributeGroup.getAttributes().stream().map(Attribute::getAttributeId).collect(Collectors.toList());
				for (Map.Entry<String, String> defaultAttribute : defaultAttributes.entrySet()) {
					if (!attributeGroupAttributeTypes.contains(defaultAttribute.getKey())) {
						attributeGroup.addAttribute(new ComparableAttribute(defaultAttribute.getKey(), defaultAttribute.getValue()));
					}
				}

				if (orEmpty(expression.getComparableAttributes()).isEmpty() || orEmpty(expression.getComparableAttributeGroups()).isEmpty()) {
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
