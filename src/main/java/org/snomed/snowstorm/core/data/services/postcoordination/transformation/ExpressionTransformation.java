package org.snomed.snowstorm.core.data.services.postcoordination.transformation;

import org.snomed.snowstorm.core.data.services.ServiceException;
import org.snomed.snowstorm.core.data.services.postcoordination.ExpressionContext;
import org.snomed.snowstorm.core.data.services.postcoordination.model.ComparableAttribute;
import org.snomed.snowstorm.core.data.services.postcoordination.model.ComparableExpression;

public interface ExpressionTransformation {

	boolean transform(ComparableAttribute looseAttribute, ComparableExpression expression, ExpressionContext context) throws ServiceException;

}
