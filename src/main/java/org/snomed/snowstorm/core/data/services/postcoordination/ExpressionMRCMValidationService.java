package org.snomed.snowstorm.core.data.services.postcoordination;

import org.elasticsearch.common.util.set.Sets;
import org.snomed.languages.scg.domain.model.Attribute;
import org.snomed.languages.scg.domain.model.AttributeGroup;
import org.snomed.languages.scg.domain.model.AttributeValue;
import org.snomed.languages.scg.domain.model.Expression;
import org.snomed.snowstorm.config.Config;
import org.snomed.snowstorm.core.data.domain.ConceptMini;
import org.snomed.snowstorm.core.data.services.ConceptService;
import org.snomed.snowstorm.core.data.services.NotFoundException;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.snomed.snowstorm.core.data.services.postcoordination.model.ComparableExpression;
import org.snomed.snowstorm.core.util.TimerUtil;
import org.snomed.snowstorm.mrcm.MRCMService;
import org.snomed.snowstorm.mrcm.model.AttributeDomain;
import org.snomed.snowstorm.mrcm.model.AttributeRange;
import org.snomed.snowstorm.mrcm.model.ContentType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static org.snomed.snowstorm.core.util.CollectionUtils.orEmpty;

@Service
public class ExpressionMRCMValidationService {

	@Autowired
	private MRCMService mrcmService;

	@Autowired
	private ConceptService conceptService;

	public void attributeRangeValidation(ComparableExpression expression, ExpressionContext context) throws ServiceException {
		doAttributeRangeValidation(expression, null, context);
	}

	private void doAttributeRangeValidation(Expression expression, String expressionIsNestedWithinAttribute, ExpressionContext context) throws ServiceException {

		List<String> focusConcepts = expression.getFocusConcepts();

		if (expressionIsNestedWithinAttribute != null) {
			for (String focusConcept : focusConcepts) {
				assertAttributeValueWithinRange(expressionIsNestedWithinAttribute, focusConcept, context);
			}
		}
		context.getTimer().checkpoint("Attributes within range");

		// Check that attribute types are in MRCM
		// and that attribute values are within the attribute range
		// Grab all attributes in MRCM for this content type
		Set<String> attributeDomainAttributeIds = context.getBranchMRCM().getAttributeDomainsForContentType(ContentType.POSTCOORDINATED)
				.stream().map(AttributeDomain::getReferencedComponentId).collect(Collectors.toSet());
		context.getTimer().checkpoint("Load MRCM attribute");

		List<Attribute> attributesToValidate = new ArrayList<>(orEmpty(expression.getAttributes()));
		for (AttributeGroup attributeGroup : orEmpty(expression.getAttributeGroups())) {
			attributesToValidate.addAll(attributeGroup.getAttributes());
		}

		for (Attribute attribute : attributesToValidate) {
			String attributeId = attribute.getAttributeId();
			// Active attribute exists in MRCM
			if (!attributeDomainAttributeIds.contains(attributeId)) {
				Map<String, String> conceptIdAndFsnTerm = getConceptIdAndTerm(Collections.singleton(attributeId), context);
				throw new IllegalArgumentException(format("Attribute %s is not found in the MRCM rules.", conceptIdAndFsnTerm.get(attributeId)));
			}
			AttributeValue attributeValue = attribute.getAttributeValue();
			if (!attributeValue.isNested()) {
				String attributeValueId = attributeValue.getConceptId();
				assertAttributeValueWithinRange(attributeId, attributeValueId, context);
			} else {
				Expression nestedExpression = attributeValue.getNestedExpression();
				doAttributeRangeValidation(nestedExpression, attributeId, context);
			}
		}
		context.getTimer().checkpoint("Attribute validation");
	}

	private void assertAttributeValueWithinRange(String attributeId, String attributeValueId, ExpressionContext context) throws ServiceException {
		// Value within attribute range
		TimerUtil timer = new TimerUtil("attribute validation");
		Collection<Long> longs = mrcmService.retrieveAttributeValueIds(ContentType.POSTCOORDINATED, attributeId, attributeValueId,
				context.getBranch(), null, context.getBranchMRCM(), context.getDependantReleaseBranchCriteria());
		timer.checkpoint("retrieveAttributeValueIds");
		if (longs.isEmpty()) {
			Map<String, String> conceptIdAndFsnTerm = getConceptIdAndTerm(Sets.newHashSet(attributeId, attributeValueId), context);
			context.getTimer().checkpoint("getConceptIdAndTerm");
			timer.checkpoint("getConceptIdAndTerm");
			String attributeValueFromStore = conceptIdAndFsnTerm.get(attributeValueId);
			if (attributeValueFromStore == null) {
				throwConceptNotFound(attributeValueId);
			} else {
				Set<AttributeRange> mandatoryAttributeRanges = context.getBranchMRCM().getMandatoryAttributeRanges(attributeId, ContentType.POSTCOORDINATED);
				timer.checkpoint("getMandatoryAttributeRanges");

				StringBuilder buffer = new StringBuilder();
				for (AttributeRange mandatoryAttributeRange : mandatoryAttributeRanges) {
					if (buffer.length() > 0) {
						buffer.append(" OR ");
					}
					buffer.append("(").append(mandatoryAttributeRange.getRangeConstraint()).append(")");
				}
				throw new IllegalArgumentException(format("Value %s is not within the permitted range of attribute %s - %s.",
						attributeValueFromStore, conceptIdAndFsnTerm.get(attributeId), buffer));
			}
		}
		context.getTimer().checkpoint("retrieveAttributeValues for " + attributeId + " = " + attributeValueId);
	}

	private Map<String, String> getConceptIdAndTerm(Set<String> conceptIds, ExpressionContext context) {
		Map<String, ConceptMini> resultsMap = conceptService.findConceptMinis(context.getBranchCriteria(), conceptIds, Config.DEFAULT_LANGUAGE_DIALECTS).getResultsMap();
		return resultsMap.values().stream().collect(Collectors.toMap(ConceptMini::getConceptId, ConceptMini::getIdAndFsnTerm));
	}

	private void throwConceptNotFound(String concept) {
		throw new NotFoundException(format("Concept %s not found on this branch.", concept));
	}

}
