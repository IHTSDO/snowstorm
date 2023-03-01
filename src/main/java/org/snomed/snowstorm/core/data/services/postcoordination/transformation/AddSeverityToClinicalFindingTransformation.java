package org.snomed.snowstorm.core.data.services.postcoordination.transformation;

import org.snomed.snowstorm.core.data.domain.Concepts;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.snomed.snowstorm.core.data.services.postcoordination.ExpressionContext;
import org.snomed.snowstorm.core.data.services.postcoordination.TransformationException;
import org.snomed.snowstorm.core.data.services.postcoordination.model.ComparableAttribute;
import org.snomed.snowstorm.core.data.services.postcoordination.model.ComparableAttributeGroup;
import org.snomed.snowstorm.core.data.services.postcoordination.model.ComparableExpression;

import java.util.List;

import static java.lang.String.format;

public class AddSeverityToClinicalFindingTransformation implements ExpressionTransformation {

	public static final String SEVERITY = "246112005";

	@Override
	public ComparableExpression transform(List<ComparableAttribute> looseAttributes, ComparableExpression expression, ExpressionContext context) throws ServiceException {
		for (ComparableAttribute looseAttribute : looseAttributes) {
			if (looseAttribute.getAttributeId().equals(SEVERITY) && context.getAncestorsAndSelfOrFocusConcept().contains(Concepts.CLINICAL_FINDING)) {
				String focusConcept = expression.getFocusConcepts().get(0);
				if (!context.ecl(format("(%s.246112005|Severity|) MINUS >>%s", focusConcept, looseAttribute.getAttributeValueId())).isEmpty()) {
					throw new TransformationException(format("Clinical finding expression can not be transformed to classifiable form because it does not match the " +
							"level 2 safety criteria: %s", "The focus concept already has a different severity attribute."));
				}
				expression.getComparableAttributes().remove(looseAttribute);
				expression.addAttributeGroup(new ComparableAttributeGroup(looseAttribute));
			}
		}
		return expression;
	}
}
