package org.snomed.snowstorm.core.data.services.postcoordination.transformation;

import org.snomed.snowstorm.core.data.services.ServiceException;
import org.snomed.snowstorm.core.data.services.postcoordination.ExpressionContext;
import org.snomed.snowstorm.core.data.services.postcoordination.model.ComparableAttribute;
import org.snomed.snowstorm.core.data.services.postcoordination.model.ComparableAttributeGroup;
import org.snomed.snowstorm.core.data.services.postcoordination.model.ComparableExpression;

import java.util.List;
import java.util.Set;

public class GroupSelfGroupedAttributeTransformation implements ExpressionTransformation {

	private final Set<String> selfGroupedAttributes;

	public GroupSelfGroupedAttributeTransformation(Set<String> selfGroupedAttributes) {
		this.selfGroupedAttributes = selfGroupedAttributes;
	}

	@Override
	public ComparableExpression transform(List<ComparableAttribute> looseAttributes, ComparableExpression expression, ExpressionContext context) throws ServiceException {
		for (ComparableAttribute looseAttribute : looseAttributes) {
			if (selfGroupedAttributes.contains(looseAttribute.getAttributeId())) {
				expression.getComparableAttributes().remove(looseAttribute);
				// Add attribute in new attribute group
				expression.addAttributeGroup(new ComparableAttributeGroup(looseAttribute));
			}
		}
		return expression;
	}

}
