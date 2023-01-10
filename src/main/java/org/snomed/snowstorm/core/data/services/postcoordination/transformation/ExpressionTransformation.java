package org.snomed.snowstorm.core.data.services.postcoordination.transformation;

import org.snomed.snowstorm.core.data.services.QueryService;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.snomed.snowstorm.core.data.services.postcoordination.ExpressionContext;
import org.snomed.snowstorm.core.data.services.postcoordination.model.ComparableAttribute;
import org.snomed.snowstorm.core.data.services.postcoordination.model.ComparableExpression;

import java.util.List;

public interface ExpressionTransformation {

	/**
	 * Implementations of this interface may apply a transformation to the postcoordinated expression using one or more of the loose attributes
	 * as part of the process to create a Classifiable Form expression from the Close To User Form expression.
	 * Transformations will be applied conditionally depending on the domain of the expression focus concepts, loose attribute types and other conditions.
	 *
	 * @param looseAttributes the remaining loose attributes to be transformed
	 * @param expression      the in progress expression being transformed
	 * @param context         the context holder for the expression including contextual information and services to aid processing
	 * @param queryService    service to run ECL
	 * @return A transformed expression or the original expression
	 * @throws ServiceException if the transformation fails
	 */
	ComparableExpression transform(List<ComparableAttribute> looseAttributes, ComparableExpression expression, ExpressionContext context, QueryService queryService) throws ServiceException;

}
