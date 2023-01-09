package org.snomed.snowstorm.core.data.services.postcoordination.transformation;

import org.snomed.snowstorm.core.data.domain.Concepts;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.snomed.snowstorm.core.data.services.postcoordination.ExpressionContext;
import org.snomed.snowstorm.core.data.services.postcoordination.model.ComparableAttribute;
import org.snomed.snowstorm.core.data.services.postcoordination.model.ComparableAttributeGroup;
import org.snomed.snowstorm.core.data.services.postcoordination.model.ComparableExpression;

import java.util.List;

public class AddSeverityToClinicalFindingTransformation implements ExpressionTransformation {

	public static final String SEVERITY = "246112005";

	@Override
	public ComparableExpression transform(List<ComparableAttribute> looseAttributes, ComparableExpression expression, ExpressionContext context) throws ServiceException {
		for (ComparableAttribute looseAttribute : looseAttributes) {
			if (looseAttribute.getAttributeId().equals(SEVERITY) && context.getAncestorIds().contains(Concepts.CLINICAL_FINDING)) {
				expression.getComparableAttributes().remove(looseAttribute);
				expression.addAttributeGroup(new ComparableAttributeGroup(looseAttribute));
			}
		}
		return expression;
	}
}
