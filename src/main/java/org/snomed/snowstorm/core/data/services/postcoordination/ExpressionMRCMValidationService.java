package org.snomed.snowstorm.core.data.services.postcoordination;

import ch.qos.logback.classic.Level;
import org.snomed.languages.scg.domain.model.Attribute;
import org.snomed.languages.scg.domain.model.AttributeGroup;
import org.snomed.languages.scg.domain.model.AttributeValue;
import org.snomed.languages.scg.domain.model.Expression;
import org.snomed.snowstorm.core.data.domain.ConceptMini;
import org.snomed.snowstorm.core.data.services.ConceptService;
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
import static org.snomed.snowstorm.config.Config.DEFAULT_LANGUAGE_DIALECTS;
import static org.snomed.snowstorm.core.util.CollectionUtils.orEmpty;

@Service
public class ExpressionMRCMValidationService {

	public static final ContentType CONTENT_TYPE = ContentType.POSTCOORDINATED;

	@Autowired
	private MRCMService mrcmService;

	@Autowired
	private ConceptService conceptService;

	public void attributeRangeValidation(ComparableExpression expression, ExpressionContext context) throws ServiceException {
		doAttributeRangeValidation(expression, null, context);
	}

	public void attributeDomainValidation(ComparableExpression expression, ExpressionContext context) throws ServiceException {
		List<Attribute> allAttributes = new ArrayList<>();
		allAttributes.addAll(orEmpty(expression.getAttributes()));
		allAttributes.addAll(orEmpty(expression.getAttributeGroups()).stream().flatMap(group -> group.getAttributes().stream()).toList());
		if (!allAttributes.isEmpty()) {
			// Fetch attribute domains
			Set<Long> focusConceptIds = expression.getFocusConcepts().stream().map(Long::parseLong).collect(Collectors.toSet());
			List<AttributeDomain> attributeDomains = mrcmService.doRetrieveDomainAttributes(CONTENT_TYPE, false, focusConceptIds, context.getBranchCriteria(), context.getBranchMRCM());
			Set<String> validAttributesForDomain = attributeDomains.stream().map(AttributeDomain::getReferencedComponentId).collect(Collectors.toSet());
			for (Attribute usedAttribute : allAttributes) {
				if (!validAttributesForDomain.contains(usedAttribute.getAttributeId())) {
					// Attribute used in wrong domain
					// Report correct domains in error message
					Set<String> validDomains = context.getBranchMRCM().attributeDomains().stream()
							.filter(attributeDomain -> attributeDomain.getReferencedComponentId().equals(usedAttribute.getAttributeId()))
							.map(AttributeDomain::getDomainId)
							.collect(Collectors.toSet());
					List<String> validDomainsForUsedAttribute = conceptService.findConceptMinis(context.getBranchCriteria(), validDomains, DEFAULT_LANGUAGE_DIALECTS)
							.getResultsMap().values().stream().map(ConceptMini::
									getIdAndFsnTerm).collect(Collectors.toList());
					throw new ExpressionValidationException(format("Attribute Type %s can not be used with the given focus concepts %s because the attribute can only be used " +
							"in the following MRCM domains: %s.", usedAttribute.getAttributeId(), focusConceptIds, validDomainsForUsedAttribute));
				}
			}
		}

		// Also validate nested expressions
		List<ComparableExpression> nestedExpressions = allAttributes.stream().filter(attribute -> attribute.getAttributeValue().isNested())
				.map(attribute -> (ComparableExpression) attribute.getAttributeValue().getNestedExpression()).toList();
		for (ComparableExpression nestedExpression : nestedExpressions) {
			attributeDomainValidation(nestedExpression, context);
		}
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
		Set<String> attributeDomainAttributeIds = context.getBranchMRCM().getAttributeDomainsForContentType(CONTENT_TYPE)
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
				throw new ExpressionValidationException(format("Attribute %s is not found in the MRCM rules.", conceptIdAndFsnTerm.get(attributeId)));
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
		TimerUtil timer = new TimerUtil("attribute validation", Level.INFO, 5);
		Collection<Long> longs = mrcmService.retrieveAttributeValueIds(CONTENT_TYPE, attributeId, attributeValueId,
				context.getBranch(), null, context.getBranchMRCM(), context.getMRCMBranchCriteria());
		timer.checkpoint("retrieveAttributeValueIds");
		if (longs.isEmpty()) {
			Map<String, String> conceptIdAndFsnTerm = getConceptIdAndTerm(Set.of(attributeId, attributeValueId), context);
			context.getTimer().checkpoint("getConceptIdAndTerm");
			timer.checkpoint("getConceptIdAndTerm");
			String attributeValueFromStore = conceptIdAndFsnTerm.get(attributeValueId);
			if (attributeValueFromStore == null) {
				throwConceptNotFound(attributeValueId);
			} else {
				Set<AttributeRange> mandatoryAttributeRanges = context.getBranchMRCM().getMandatoryAttributeRanges(attributeId, CONTENT_TYPE);
				timer.checkpoint("getMandatoryAttributeRanges");
				throwAttributeRangeError(attributeId, conceptIdAndFsnTerm, attributeValueFromStore, mandatoryAttributeRanges);
				return;
			}
		}
		context.getTimer().checkpoint("retrieveAttributeValues for " + attributeId + " = " + attributeValueId);
	}

	static void throwAttributeRangeError(String attributeId, Map<String, String> conceptIdAndFsnTerm, String attributeValueFromStore, Set<AttributeRange> mandatoryAttributeRanges) throws ExpressionValidationException {
		StringBuilder buffer = new StringBuilder();
		for (AttributeRange mandatoryAttributeRange : mandatoryAttributeRanges) {
			if (!buffer.isEmpty()) {
				buffer.append(" OR ");
			}
			buffer.append("(").append(mandatoryAttributeRange.getRangeConstraint()).append(")");
		}
		throw new ExpressionValidationException(format("Value %s is not within the permitted range of attribute %s - %s.",
				attributeValueFromStore, conceptIdAndFsnTerm.get(attributeId), buffer));
	}

	private Map<String, String> getConceptIdAndTerm(Set<String> conceptIds, ExpressionContext context) {
		Map<String, ConceptMini> resultsMap = conceptService.findConceptMinis(context.getBranchCriteria(), conceptIds, DEFAULT_LANGUAGE_DIALECTS).getResultsMap();
		return resultsMap.values().stream().collect(Collectors.toMap(ConceptMini::getConceptId, ConceptMini::getIdAndFsnTerm));
	}

	private void throwConceptNotFound(String concept) throws ExpressionValidationException {
		throw new ExpressionValidationException(format("Concept %s not found on this branch.", concept));
	}
}
