package org.snomed.snowstorm.core.data.services.postcoordination;

import io.kaicode.elasticvc.api.BranchCriteria;
import io.kaicode.elasticvc.api.VersionControlHelper;
import org.elasticsearch.common.util.set.Sets;
import org.snomed.languages.scg.SCGException;
import org.snomed.languages.scg.SCGExpressionParser;
import org.snomed.languages.scg.SCGObjectFactory;
import org.snomed.languages.scg.domain.model.Attribute;
import org.snomed.languages.scg.domain.model.AttributeValue;
import org.snomed.languages.scg.domain.model.Expression;
import org.snomed.snowstorm.config.Config;
import org.snomed.snowstorm.core.data.domain.ConceptMini;
import org.snomed.snowstorm.core.data.domain.Concepts;
import org.snomed.snowstorm.core.data.domain.ReferenceSetMember;
import org.snomed.snowstorm.core.data.services.ConceptService;
import org.snomed.snowstorm.core.data.services.NotFoundException;
import org.snomed.snowstorm.core.data.services.ReferenceSetMemberService;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.snomed.snowstorm.core.data.services.pojo.MemberSearchRequest;
import org.snomed.snowstorm.core.data.services.pojo.ResultMapPage;
import org.snomed.snowstorm.core.data.services.postcoordination.model.ComparableExpression;
import org.snomed.snowstorm.mrcm.MRCMService;
import org.snomed.snowstorm.mrcm.model.AttributeDomain;
import org.snomed.snowstorm.mrcm.model.AttributeRange;
import org.snomed.snowstorm.mrcm.model.ContentType;
import org.snomed.snowstorm.mrcm.model.MRCM;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

import static java.lang.Long.parseLong;
import static java.lang.String.format;

@Service
public class ExpressionRepositoryService {

	private final SCGExpressionParser expressionParser;

	@Autowired
	private ReferenceSetMemberService memberService;

	@Autowired
	private MRCMService mrcmService;

	@Autowired
	private ConceptService conceptService;

	@Autowired
	private VersionControlHelper versionControlHelper;

	// Temporary workaround until postcoordinated expression reference set is created.
	private static final String ANNOTATION_REFSET = "900000000000516008";
	private static final String ANNOTATION_FIELD = "annotation";

	public ExpressionRepositoryService() {
		expressionParser = new SCGExpressionParser(new SCGObjectFactory());
	}

	public Page<PostCoordinatedExpression> findAll(String branch, PageRequest pageRequest) {
		Page<ReferenceSetMember> membersPage = memberService.findMembers(branch, new MemberSearchRequest().referenceSet(ANNOTATION_REFSET), pageRequest);
		List<PostCoordinatedExpression> expressions = membersPage.getContent().stream()
				.map(this::toExpression).collect(Collectors.toList());
		return new PageImpl<>(expressions, pageRequest, membersPage.getTotalElements());
	}

	public PostCoordinatedExpression createExpression(String branch, String closeToUserForm, String moduleId) throws ServiceException {
		try {
			Expression expression = expressionParser.parseExpression(closeToUserForm);

			// Sort contents of expression
			expression = new ComparableExpression(expression);

			// Validate expression against MRCM
			mrcmAttributeRangeValidation(expression, branch);

			ReferenceSetMember member = memberService.createMember(branch, new ReferenceSetMember(moduleId, ANNOTATION_REFSET, getFirstFocusConceptOrRoot(expression))
					.setAdditionalField(ANNOTATION_FIELD, expression.toString()));
			return toExpression(member);
		} catch (SCGException e) {
			throw new IllegalArgumentException("Failed to parse expression: " + e.getMessage(), e);
		}
	}

	private PostCoordinatedExpression toExpression(ReferenceSetMember member) {
		return new PostCoordinatedExpression(member.getAdditionalField(ANNOTATION_FIELD));
	}

	private String getFirstFocusConceptOrRoot(Expression expression) {
		List<String> focusConcepts = expression.getFocusConcepts();
		return focusConcepts != null && !focusConcepts.isEmpty() ? focusConcepts.get(0) : Concepts.SNOMEDCT_ROOT;
	}

	private void mrcmAttributeRangeValidation(Expression expression, String branch) throws ServiceException {
		BranchCriteria branchCriteria = versionControlHelper.getBranchCriteria(branch);
		MRCM branchMRCM = mrcmService.loadActiveMRCM(branch, branchCriteria);

		doMrcmAttributeRangeValidation(expression, null, branchMRCM, branch, branchCriteria);
	}

	private void doMrcmAttributeRangeValidation(Expression expression, String expressionWithinAttributeId, MRCM branchMRCM, String branch, BranchCriteria branchCriteria) throws ServiceException {

		// Check that focus concepts exist
		List<String> focusConcepts = expression.getFocusConcepts();
		checkThatConceptsExist(focusConcepts, branch);

		if (expressionWithinAttributeId != null) {
			for (String focusConcept : focusConcepts) {
				assertAttributeValueWithinRange(expressionWithinAttributeId, focusConcept, branchMRCM, branch, branchCriteria);
			}
		}

		// Check that attribute types are in MRCM
		// and that attribute values are within the attribute range
		List<Attribute> attributes = expression.getAttributes();
		if (attributes != null) {
			// Grab all attributes in MRCM for this content type
			Set<String> attributeDomainAttributeIds = branchMRCM.getAttributeDomainsForContentType(ContentType.POSTCOORDINATED)
					.stream().map(AttributeDomain::getReferencedComponentId).collect(Collectors.toSet());
			for (Attribute attribute : attributes) {
				String attributeId = attribute.getAttributeId();
				// Active attribute exists in MRCM
				if (!attributeDomainAttributeIds.contains(attributeId)) {
					Map<String, String> conceptIdAndFsnTerm = getConceptIdAndTerm(branch, Collections.singleton(attributeId));
					throw new IllegalArgumentException(format("Attribute %s is not present in the permitted concept model.", conceptIdAndFsnTerm.get(attributeId)));
				}
				AttributeValue attributeValue = attribute.getAttributeValue();
				if (!attributeValue.isNested()) {
					String attributeValueId = attributeValue.getConceptId();
					assertAttributeValueWithinRange(attributeId, attributeValueId, branchMRCM, branch, branchCriteria);
				} else {
					Expression nestedExpression = attributeValue.getNestedExpression();
					doMrcmAttributeRangeValidation(nestedExpression, attributeId, branchMRCM, branch, branchCriteria);
				}
			}
		}
	}

	private void assertAttributeValueWithinRange(String attributeId, String attributeValueId, MRCM branchMRCM, String branch, BranchCriteria branchCriteria) throws ServiceException {
		// Value within attribute range
		if (mrcmService.retrieveAttributeValues(ContentType.POSTCOORDINATED, attributeId, attributeValueId, branch, null, branchMRCM, branchCriteria).isEmpty()) {
			Map<String, String> conceptIdAndFsnTerm = getConceptIdAndTerm(branch, Sets.newHashSet(attributeId, attributeValueId));
			String attributeValueFromStore = conceptIdAndFsnTerm.get(attributeValueId);
			if (attributeValueFromStore == null) {
				throwConceptNotFound(attributeValueId);
			} else {
				Set<AttributeRange> mandatoryAttributeRanges = branchMRCM.getMandatoryAttributeRanges(attributeId, ContentType.POSTCOORDINATED);
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

	private Map<String, String> getConceptIdAndTerm(String branch, Set<String> conceptIds) {
		Map<String, ConceptMini> resultsMap = conceptService.findConceptMinis(branch, conceptIds, Config.DEFAULT_LANGUAGE_DIALECTS).getResultsMap();
		return resultsMap.values().stream().collect(Collectors.toMap(ConceptMini::getConceptId, ConceptMini::getIdAndFsnTerm));
	}

	private void checkThatConceptsExist(Collection<String> concepts, String branch) {
		if (concepts != null) {

			ResultMapPage<String, ConceptMini> conceptMinis = conceptService.findConceptMinis(branch, concepts, null);
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
