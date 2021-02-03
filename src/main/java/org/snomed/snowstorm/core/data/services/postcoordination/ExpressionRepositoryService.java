package org.snomed.snowstorm.core.data.services.postcoordination;

import io.kaicode.elasticvc.api.VersionControlHelper;
import org.elasticsearch.common.util.set.Sets;
import org.snomed.languages.scg.SCGException;
import org.snomed.languages.scg.domain.model.Attribute;
import org.snomed.languages.scg.domain.model.AttributeValue;
import org.snomed.languages.scg.domain.model.Expression;
import org.snomed.snowstorm.config.Config;
import org.snomed.snowstorm.core.data.domain.ConceptMini;
import org.snomed.snowstorm.core.data.domain.ReferenceSetMember;
import org.snomed.snowstorm.core.data.services.ConceptService;
import org.snomed.snowstorm.core.data.services.NotFoundException;
import org.snomed.snowstorm.core.data.services.ReferenceSetMemberService;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.snomed.snowstorm.core.data.services.identifier.LocalRandomIdentifierSource;
import org.snomed.snowstorm.core.data.services.pojo.MemberSearchRequest;
import org.snomed.snowstorm.core.data.services.pojo.ResultMapPage;
import org.snomed.snowstorm.core.data.services.postcoordination.model.ComparableExpression;
import org.snomed.snowstorm.mrcm.MRCMService;
import org.snomed.snowstorm.mrcm.model.AttributeDomain;
import org.snomed.snowstorm.mrcm.model.AttributeRange;
import org.snomed.snowstorm.mrcm.model.ContentType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

import static java.lang.String.format;

@Service
public class ExpressionRepositoryService {

	@Autowired
	private ExpressionParser expressionParser;

	@Autowired
	private ExpressionTransformationService transformationService;

	@Autowired
	private ReferenceSetMemberService memberService;

	@Autowired
	private MRCMService mrcmService;

	@Autowired
	private ConceptService conceptService;

	@Autowired
	private VersionControlHelper versionControlHelper;

	@Autowired
	private LocalRandomIdentifierSource identifierSource;

	// 1119435002 | Canonical close to user form expression reference set (foundation metadata concept) |
	// referencedComponentId - a generated SCTID for expression
	// expression - the close to user form expression
	// substrate - the URI of the SNOMED CT Edition and release the expression was authored against

	// 1119468009 | Classifiable form expression reference set (foundation metadata concept) |
	// referencedComponentId - the SCTID matching the close-to-user form expression
	// expression - the classifiable form expression, created by transforming the close-to-user form expression.
	// substrate - the URI of the SNOMED CT Edition and release that was used to transform close-to-user form expression to the classifiable form expression.

	private static final String CANONICAL_CLOSE_TO_USER_FORM_EXPRESSION_REFERENCE_SET = "1119435002";
	private static final String CLASSIFIABLE_FORM_EXPRESSION_REFERENCE_SET = "1119468009";
	private static final String EXPRESSION_FIELD = "expression";
	private static final String SUBSTRATE_FIELD = "substrate";

	public Page<PostCoordinatedExpression> findAll(String branch, PageRequest pageRequest) {
		Page<ReferenceSetMember> membersPage = memberService.findMembers(branch,
				new MemberSearchRequest()
						.referenceSet(CANONICAL_CLOSE_TO_USER_FORM_EXPRESSION_REFERENCE_SET),
				pageRequest);
		return getPostCoordinatedExpressions(pageRequest, membersPage);
	}

	public Page<PostCoordinatedExpression> findByCanonicalCloseToUserForm(String branch, String expression, PageRequest pageRequest) {
		return getPostCoordinatedExpressions(pageRequest, memberService.findMembers(branch,
				new MemberSearchRequest()
						.referenceSet(CANONICAL_CLOSE_TO_USER_FORM_EXPRESSION_REFERENCE_SET)
						.additionalField(EXPRESSION_FIELD, expression),
				pageRequest));
	}

	private PageImpl<PostCoordinatedExpression> getPostCoordinatedExpressions(PageRequest pageRequest, Page<ReferenceSetMember> membersPage) {
		List<PostCoordinatedExpression> expressions = membersPage.getContent().stream()
				.map(this::toExpression).collect(Collectors.toList());
		return new PageImpl<>(expressions, pageRequest, membersPage.getTotalElements());
	}

	public PostCoordinatedExpression createExpression(String branch, String closeToUserForm, String moduleId) throws ServiceException {
		try {
			// Sort contents of expression
			ComparableExpression expression = expressionParser.parseExpression(closeToUserForm);

			ExpressionContext context = new ExpressionContext(branch, versionControlHelper, mrcmService);

			// Validate expression against MRCM
			mrcmAttributeRangeValidation(expression, context);

			try {
				transformationService.transform(expression, context);
			} catch (TransformationException e) {
				throw new TransformationException(String.format("Expression \"%s\" could not be transformed because: %s", expression.toString(), e.getMessage()), e);
			}

			List<Long> expressionIds = identifierSource.reserveIds(0, LocalRandomIdentifierSource.POSTCOORDINATED_EXPRESSION_PARTITION_ID, 1);
			Long expressionId = expressionIds.get(0);
			ReferenceSetMember member = memberService.createMember(branch,
					new ReferenceSetMember(moduleId, CANONICAL_CLOSE_TO_USER_FORM_EXPRESSION_REFERENCE_SET, expressionId.toString())
							.setAdditionalField(EXPRESSION_FIELD, expression.toString()));

			return toExpression(member);
		} catch (SCGException e) {
			throw new IllegalArgumentException("Failed to parse expression: " + e.getMessage(), e);
		}
	}

	private PostCoordinatedExpression toExpression(ReferenceSetMember member) {
		return new PostCoordinatedExpression(member.getReferencedComponentId(), member.getAdditionalField(EXPRESSION_FIELD));
	}

	private void mrcmAttributeRangeValidation(ComparableExpression expression, ExpressionContext context) throws ServiceException {
		doMrcmAttributeRangeValidation(expression, null, context);
	}

	private void doMrcmAttributeRangeValidation(Expression expression, String expressionWithinAttributeId, ExpressionContext context) throws ServiceException {

		// Check that focus concepts exist
		List<String> focusConcepts = expression.getFocusConcepts();
		checkThatConceptsExist(focusConcepts, context);

		if (expressionWithinAttributeId != null) {
			for (String focusConcept : focusConcepts) {
				assertAttributeValueWithinRange(expressionWithinAttributeId, focusConcept, context);
			}
		}

		// Check that attribute types are in MRCM
		// and that attribute values are within the attribute range
		List<Attribute> attributes = expression.getAttributes();
		if (attributes != null) {
			// Grab all attributes in MRCM for this content type
			Set<String> attributeDomainAttributeIds = context.getBranchMRCM().getAttributeDomainsForContentType(ContentType.POSTCOORDINATED)
					.stream().map(AttributeDomain::getReferencedComponentId).collect(Collectors.toSet());
			for (Attribute attribute : attributes) {
				String attributeId = attribute.getAttributeId();
				// Active attribute exists in MRCM
				if (!attributeDomainAttributeIds.contains(attributeId)) {
					Map<String, String> conceptIdAndFsnTerm = getConceptIdAndTerm(Collections.singleton(attributeId), context);
					throw new IllegalArgumentException(format("Attribute %s is not present in the permitted concept model.", conceptIdAndFsnTerm.get(attributeId)));
				}
				AttributeValue attributeValue = attribute.getAttributeValue();
				if (!attributeValue.isNested()) {
					String attributeValueId = attributeValue.getConceptId();
					assertAttributeValueWithinRange(attributeId, attributeValueId, context);
				} else {
					Expression nestedExpression = attributeValue.getNestedExpression();
					doMrcmAttributeRangeValidation(nestedExpression, attributeId, context);
				}
			}
		}
	}

	private void assertAttributeValueWithinRange(String attributeId, String attributeValueId, ExpressionContext context) throws ServiceException {
		// Value within attribute range
		if (mrcmService.retrieveAttributeValues(ContentType.POSTCOORDINATED, attributeId, attributeValueId,
				context.getBranch(), null, context.getBranchMRCM(), context.getBranchCriteria()).isEmpty()) {

			Map<String, String> conceptIdAndFsnTerm = getConceptIdAndTerm(Sets.newHashSet(attributeId, attributeValueId), context);
			String attributeValueFromStore = conceptIdAndFsnTerm.get(attributeValueId);
			if (attributeValueFromStore == null) {
				throwConceptNotFound(attributeValueId);
			} else {
				Set<AttributeRange> mandatoryAttributeRanges = context.getBranchMRCM().getMandatoryAttributeRanges(attributeId, ContentType.POSTCOORDINATED);
				StringBuffer buffer = new StringBuffer();
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
	}

	private Map<String, String> getConceptIdAndTerm(Set<String> conceptIds, ExpressionContext context) {
		Map<String, ConceptMini> resultsMap = conceptService.findConceptMinis(context.getBranchCriteria(), conceptIds, Config.DEFAULT_LANGUAGE_DIALECTS).getResultsMap();
		return resultsMap.values().stream().collect(Collectors.toMap(ConceptMini::getConceptId, ConceptMini::getIdAndFsnTerm));
	}

	private void checkThatConceptsExist(Collection<String> concepts, ExpressionContext context) {
		if (concepts != null) {
			ResultMapPage<String, ConceptMini> conceptMinis = conceptService.findConceptMinis(context.getBranchCriteria(), concepts, null);
			if (conceptMinis.getTotalElements() < concepts.size()) {
				for (String concept : concepts) {
					if (!conceptMinis.getResultsMap().containsKey(concept)) {
						throwConceptNotFound(concept);
					}
				}
			}
		}
	}

	private void throwConceptNotFound(String concept) {
		throw new NotFoundException(format("Concept %s not found on this branch.", concept));
	}

}
