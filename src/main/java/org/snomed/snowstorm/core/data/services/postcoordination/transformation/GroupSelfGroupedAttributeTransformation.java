package org.snomed.snowstorm.core.data.services.postcoordination.transformation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.core.data.domain.Relationship;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.snomed.snowstorm.core.data.services.postcoordination.ExpressionContext;
import org.snomed.snowstorm.core.data.services.postcoordination.model.ComparableAttribute;
import org.snomed.snowstorm.core.data.services.postcoordination.model.ComparableAttributeGroup;
import org.snomed.snowstorm.core.data.services.postcoordination.model.ComparableExpression;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class GroupSelfGroupedAttributeTransformation implements ExpressionTransformation {

	private final Set<String> selfGroupedAttributes;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public GroupSelfGroupedAttributeTransformation(Set<String> selfGroupedAttributes) {
		this.selfGroupedAttributes = selfGroupedAttributes;
	}

	@Override
	public ComparableExpression transform(List<ComparableAttribute> looseAttributes, ComparableExpression expression, ExpressionContext context) throws ServiceException {
		for (ComparableAttribute looseAttribute : looseAttributes) {
			String attributeTypeId = looseAttribute.getAttributeId();
			if (selfGroupedAttributes.contains(attributeTypeId)) {
				if (looseAttributes.stream().filter(attr -> attr.getAttributeId().equals(attributeTypeId)).count() == 1) {
					logger.debug("More than one loose attribute of the same type, can not apply this transform.");
					continue;
				}

				// Check that the attribute does not already exist on the focus concept or is defined at most once
				List<Relationship> existingAttributesOfSameType = context.getFocusConceptWithActiveRelationships().getRelationships().stream()
						.filter(relationship -> relationship.getTypeId().equals(attributeTypeId)).collect(Collectors.toList());
				if (existingAttributesOfSameType.size() > 1) {
					logger.debug("Found more than one ({}) existing attributes of the same type.", existingAttributesOfSameType.size());
					continue;
				}

				if (existingAttributesOfSameType.size() == 1) {
					// Check that the existing attribute subsumes the loose attribute
					Set<String> looseAttributeTypeAncestorsAndSelf = context.ecl(">>" + looseAttribute.getAttributeId());
					String looseAttributeValueId = looseAttribute.getAttributeValue().isNested() ? looseAttribute.getAttributeValue().getNestedExpression().getFocusConcepts().get(0) :
							looseAttribute.getAttributeValueId();
					Set<String> looseAttributeValueAncestorsAndSelf = context.ecl(">>" + looseAttributeValueId);
					Relationship existingAttribute = existingAttributesOfSameType.get(0);
					if (!looseAttributeTypeAncestorsAndSelf.contains(existingAttribute.getTypeId()) ||
							!looseAttributeValueAncestorsAndSelf.contains(existingAttribute.getDestinationId())) {
						logger.debug("Existing attribute ({}:{}) does not subsume loose attribute, can not apply this transform.",
								existingAttribute.getTypeId(), existingAttribute.getDestinationId());
						continue;
					}
				}

				// All checks passed, apply transform
				expression.getComparableAttributes().remove(looseAttribute);
				// Add attribute in new attribute group
				expression.addAttributeGroup(new ComparableAttributeGroup(looseAttribute));
			}
		}
		return expression;
	}

}
