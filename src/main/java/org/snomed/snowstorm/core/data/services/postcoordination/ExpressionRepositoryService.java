package org.snomed.snowstorm.core.data.services.postcoordination;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.api.VersionControlHelper;
import io.kaicode.elasticvc.domain.Commit;
import org.snomed.languages.scg.domain.model.Attribute;
import org.snomed.languages.scg.domain.model.AttributeValue;
import org.snomed.languages.scg.domain.model.DefinitionStatus;
import org.snomed.languages.scg.domain.model.Expression;
import org.snomed.snowstorm.config.Config;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.data.services.ConceptService;
import org.snomed.snowstorm.core.data.services.ReferenceSetMemberService;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.snomed.snowstorm.core.data.services.identifier.IdentifierHelper;
import org.snomed.snowstorm.core.data.services.identifier.IdentifierSource;
import org.snomed.snowstorm.core.data.services.pojo.MemberSearchRequest;
import org.snomed.snowstorm.core.data.services.postcoordination.model.ComparableAttribute;
import org.snomed.snowstorm.core.data.services.postcoordination.model.ComparableAttributeGroup;
import org.snomed.snowstorm.core.data.services.postcoordination.model.ComparableExpression;
import org.snomed.snowstorm.core.data.services.postcoordination.model.PostCoordinatedExpression;
import org.snomed.snowstorm.core.util.TimerUtil;
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
import static org.snomed.snowstorm.core.util.CollectionUtils.orEmpty;

@Service
public class ExpressionRepositoryService {

	@Autowired
	private ExpressionParser expressionParser;

	@Autowired
	private ExpressionTransformationAndValidationService transformationService;

	@Autowired
	private ReferenceSetMemberService memberService;

	@Autowired
	private MRCMService mrcmService;

	@Autowired
	private VersionControlHelper versionControlHelper;

	@Autowired
	private BranchService branchService;

	@Autowired
	private ConceptService conceptService;

	@Autowired
	private IdentifierSource identifierSource;

	@Autowired
	private IncrementalClassificationService incrementalClassificationService;

	// 1119435002 | Canonical close to user form expression reference set (foundation metadata concept) |
	// referencedComponentId - a generated SCTID for expression
	// expression - the close to user form expression
	// substrate - the URI of the SNOMED CT Edition and release the expression was authored against

	// 1119468009 | Classifiable form expression reference set (foundation metadata concept) |
	// referencedComponentId - the SCTID matching the close-to-user form expression
	// expression - the classifiable form expression, created by transforming the close-to-user form expression.
	// substrate - the URI of the SNOMED CT Edition and release that was used to transform close-to-user form expression to the classifiable form expression.

	public static final String CANONICAL_CLOSE_TO_USER_FORM_EXPRESSION_REFERENCE_SET = "1119435002";
	public static final String CLASSIFIABLE_FORM_EXPRESSION_REFERENCE_SET = "1119468009";
	private static final int SNOMED_INTERNATIONAL_DEMO_NAMESPACE = 1000003;

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
						.additionalField(ReferenceSetMember.PostcoordinatedExpressionFields.EXPRESSION, expression),
				pageRequest));
	}

	private PageImpl<PostCoordinatedExpression> getPostCoordinatedExpressions(PageRequest pageRequest, Page<ReferenceSetMember> membersPage) {
		List<PostCoordinatedExpression> expressions = membersPage.getContent().stream()
				.map((ReferenceSetMember closeToUserFormMember) -> toExpression(closeToUserFormMember, new ReferenceSetMember())).collect(Collectors.toList());
		return new PageImpl<>(expressions, pageRequest, membersPage.getTotalElements());
	}

	public PostCoordinatedExpression createExpression(String closeToUserFormExpression, String branch, String moduleId, String classificationPackage) throws ServiceException {
		List<PostCoordinatedExpression> expressions = createExpressionsAllOrNothing(Collections.singletonList(closeToUserFormExpression), branch, moduleId, classificationPackage);
		return expressions.get(0);
	}

	public List<PostCoordinatedExpression> createExpressionsAllOrNothing(List<String> closeToUserFormExpressions, String branch, String moduleId, String classificationPackage) throws ServiceException {
		int namespace = IdentifierHelper.getNamespaceFromSCTID(moduleId);
		final List<PostCoordinatedExpression> postCoordinatedExpressions = parseValidateTransformAndClassifyExpressions(closeToUserFormExpressions, branch, classificationPackage);

		if (!postCoordinatedExpressions.isEmpty() && postCoordinatedExpressions.stream().noneMatch(PostCoordinatedExpression::hasException)) {
			try (Commit commit = branchService.openCommit(branch)) {
				List<ReferenceSetMember> membersToSave = new ArrayList<>();
				List<Concept> conceptsToSave = new ArrayList<>();
				for (PostCoordinatedExpression postCoordinatedExpression : postCoordinatedExpressions) {

					// Save NNF for ECL
					final String expressionId = convertToConcepts(postCoordinatedExpression.getNecessaryNormalFormExpression(), namespace, conceptsToSave);
					postCoordinatedExpression.setId(expressionId);

					// Save refset members
					final ReferenceSetMember closeToUserFormMember = new ReferenceSetMember(moduleId, CANONICAL_CLOSE_TO_USER_FORM_EXPRESSION_REFERENCE_SET, expressionId)
							.setAdditionalField(ReferenceSetMember.PostcoordinatedExpressionFields.EXPRESSION, postCoordinatedExpression.getCloseToUserForm().replace(" ", ""));
					closeToUserFormMember.markChanged();
					membersToSave.add(closeToUserFormMember);

					final ReferenceSetMember classifiableFormMember = new ReferenceSetMember(moduleId, CLASSIFIABLE_FORM_EXPRESSION_REFERENCE_SET, expressionId)
							.setAdditionalField(ReferenceSetMember.PostcoordinatedExpressionFields.EXPRESSION, postCoordinatedExpression.getClassifiableForm().replace(" ", ""));
					classifiableFormMember.markChanged();
					membersToSave.add(classifiableFormMember);

					if (conceptsToSave.size() >= 100) {
						memberService.doSaveBatchMembers(membersToSave, commit);
						membersToSave.clear();
						conceptService.updateWithinCommit(conceptsToSave, commit);
						conceptsToSave.clear();
					}
				}
				if (!conceptsToSave.isEmpty()) {
					memberService.doSaveBatchMembers(membersToSave, commit);
					conceptService.updateWithinCommit(conceptsToSave, commit);
				}
				commit.markSuccessful();
			}
		}

		return postCoordinatedExpressions;
	}

	private String convertToConcepts(ComparableExpression nnfExpression, int namespace, List<Concept> conceptsToSave) throws ServiceException {
		Concept concept = new Concept(getNewId(namespace).toString());
		concept.setDefinitionStatusId(nnfExpression.getDefinitionStatus() == DefinitionStatus.EQUIVALENT_TO ? Concepts.DEFINED : Concepts.PRIMITIVE);
		for (String inferredParent : nnfExpression.getFocusConcepts()) {
			concept.addRelationship(new Relationship(Concepts.ISA, inferredParent));
		}
		for (ComparableAttribute attribute : orEmpty( nnfExpression.getComparableAttributes())) {
			String attributeValueId;
			if (attribute.getAttributeValue().isNested()) {
				// TODO: Search for existing expression rather than creating new nested concept every time?
				attributeValueId = convertToConcepts((ComparableExpression) attribute.getComparableAttributeValue().getNestedExpression(), namespace, conceptsToSave);
			} else {
				attributeValueId = attribute.getAttributeValueId();
			}
			concept.addRelationship(new Relationship(attribute.getAttributeId(), attributeValueId));
		}
		int groupNumber = 0;
		for (ComparableAttributeGroup group : orEmpty(nnfExpression.getComparableAttributeGroups())) {
			groupNumber++;
			for (ComparableAttribute attribute : group.getComparableAttributes()) {
				String attributeValueId;
				if (attribute.getAttributeValue().isNested()) {
					// TODO: Search for existing expression rather than creating new nested concept every time?
					attributeValueId = convertToConcepts((ComparableExpression) attribute.getComparableAttributeValue().getNestedExpression(), namespace, conceptsToSave);
				} else {
					attributeValueId = attribute.getAttributeValueId();
				}
				concept.addRelationship(new Relationship(attribute.getAttributeId(), attributeValueId).setGroupId(groupNumber));
			}
		}
		conceptsToSave.add(concept);
		return concept.getConceptId();
	}

	public List<PostCoordinatedExpression> parseValidateTransformAndClassifyExpressions(List<String> originalCloseToUserForms, String branch, String classificationPackage) {
		List<PostCoordinatedExpression> expressionOutcomes = new ArrayList<>();

		for (String originalCloseToUserForm : originalCloseToUserForms) {
			TimerUtil timer = new TimerUtil("exp");
			ExpressionContext context = new ExpressionContext(branch, branchService, versionControlHelper, mrcmService, timer);
			try {
				// Sort contents of expression
				ComparableExpression closeToUserFormExpression = expressionParser.parseExpression(originalCloseToUserForm);
				timer.checkpoint("Parse expression");

				// Validate and transform expression to classifiable form if needed.
				// This groups any 'loose' attributes
				final ComparableExpression classifiableFormExpression;
				classifiableFormExpression = transformationService.validateAndTransform(closeToUserFormExpression, context);
				timer.checkpoint("Transformation");

				// Classify
				// Assign temp identifier for classification process
				if (classifiableFormExpression.getExpressionId() == null) {
					classifiableFormExpression.setExpressionId(getNewId(SNOMED_INTERNATIONAL_DEMO_NAMESPACE));
				}
				ComparableExpression necessaryNormalForm;
				boolean skipClassification = false;
				if (skipClassification) {
					necessaryNormalForm = classifiableFormExpression;
				} else {
					necessaryNormalForm = incrementalClassificationService.classify(classifiableFormExpression, classificationPackage);
					timer.checkpoint("Classify");
				}
				classifiableFormExpression.setExpressionId(null);

				final PostCoordinatedExpression pce = new PostCoordinatedExpression(null, closeToUserFormExpression.toString(),
						classifiableFormExpression.toString(), necessaryNormalForm);

				populateHumanReadableForms(pce, context);
				timer.checkpoint("Add human readable");
				expressionOutcomes.add(pce);
				timer.finish();
			} catch (ServiceException e) {
				String humanReadableCloseToUserForm;
				try {
					humanReadableCloseToUserForm = createHumanReadableExpression(originalCloseToUserForm, context);
				} catch (ServiceException e2) {
					humanReadableCloseToUserForm = originalCloseToUserForm;
				}
				expressionOutcomes.add(new PostCoordinatedExpression(humanReadableCloseToUserForm, e));
			}
		}
		return expressionOutcomes;
	}

	private Long getNewId(int namespace) throws ServiceException {
		return identifierSource.reserveIds(namespace, "16", 1).get(0);
	}

	private void populateHumanReadableForms(PostCoordinatedExpression expressionForms, ExpressionContext context) throws ServiceException {
		final List<String> humanReadableExpressions = createHumanReadableExpressions(
				Lists.newArrayList(expressionForms.getClassifiableForm(), expressionForms.getNecessaryNormalForm()), context);
		expressionForms.setHumanReadableClassifiableForm(humanReadableExpressions.get(0));
		expressionForms.setHumanReadableNecessaryNormalForm(humanReadableExpressions.get(1));
	}

	private PostCoordinatedExpression toExpression(ReferenceSetMember closeToUserFormMember, ReferenceSetMember classifiableFormMember) {
		return new PostCoordinatedExpression(closeToUserFormMember.getReferencedComponentId(),
				closeToUserFormMember.getAdditionalField(ReferenceSetMember.PostcoordinatedExpressionFields.EXPRESSION), classifiableFormMember.getAdditionalField(ReferenceSetMember.PostcoordinatedExpressionFields.EXPRESSION), null);
	}

	private void mrcmAttributeRangeValidation(ComparableExpression expression, ExpressionContext context) throws ServiceException {
		doMrcmAttributeRangeValidation(expression, null, context);
	}

	private void doMrcmAttributeRangeValidation(Expression expression, String expressionWithinAttributeId, ExpressionContext context) throws ServiceException {

		// Check that focus concepts exist
		List<String> focusConcepts = expression.getFocusConcepts();
		checkThatConceptsExist(focusConcepts, context);
		context.getTimer().checkpoint("Focus concepts exist");

		if (expressionWithinAttributeId != null) {
			for (String focusConcept : focusConcepts) {
				assertAttributeValueWithinRange(expressionWithinAttributeId, focusConcept, context);
			}
		}
		context.getTimer().checkpoint("Attributes within range");

		// Check that attribute types are in MRCM
		// and that attribute values are within the attribute range
		List<Attribute> attributes = expression.getAttributes();
		if (attributes != null) {
			// Grab all attributes in MRCM for this content type
			Set<String> attributeDomainAttributeIds = context.getBranchMRCM().getAttributeDomainsForContentType(ContentType.POSTCOORDINATED)
					.stream().map(AttributeDomain::getReferencedComponentId).collect(Collectors.toSet());
			context.getTimer().checkpoint("Load MRCM attribute");
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
			context.getTimer().checkpoint("Attribute validation");
		}
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
				StringBuilder builder = new StringBuilder();
				timer.checkpoint("getMandatoryAttributeRanges");
				for (AttributeRange mandatoryAttributeRange : mandatoryAttributeRanges) {
					if (!builder.isEmpty()) {
						builder.append(" OR ");
					}
					builder.append("(").append(mandatoryAttributeRange.getRangeConstraint()).append(")");
				}
				throw new IllegalArgumentException(format("Value %s is not within the permitted range of attribute %s - %s.",
						attributeValueFromStore, conceptIdAndFsnTerm.get(attributeId), builder));
			}
		}
		context.getTimer().checkpoint("retrieveAttributeValues for " + attributeId + " = " + attributeValueId);
	}

	private void throwConceptNotFound(String conceptId) {
		throw new IllegalArgumentException(format("Concept %s not found.", conceptId));
	}

	private Map<String, String> getConceptIdAndTerm(Set<String> conceptIds, ExpressionContext context) {
		Map<String, ConceptMini> resultsMap = conceptService.findConceptMinis(context.getBranchCriteria(), conceptIds, Config.DEFAULT_LANGUAGE_DIALECTS).getResultsMap();
		return resultsMap.values().stream().collect(Collectors.toMap(ConceptMini::getConceptId, ConceptMini::getIdAndFsnTerm));
	}

	private void checkThatConceptsExist(Collection<String> concepts, ExpressionContext context) {
		if (concepts != null) {
			final Collection<String> nonExistentConceptIds = conceptService.getNonExistentConceptIds(concepts, context.getBranchCriteria());
			if (!nonExistentConceptIds.isEmpty()) {
				throwConceptNotFound(nonExistentConceptIds.iterator().next());
			}
		}
	}

	private String createHumanReadableExpression(String expression, ExpressionContext context) throws ServiceException {
		if (expression != null) {
			return createHumanReadableExpressions(Lists.newArrayList(expression), context).get(0);
		}
		return null;
	}

	private List<String> createHumanReadableExpressions(List<String> expressions, ExpressionContext context) throws ServiceException {
		final Set<String> allConceptIds = new HashSet<>();
		for (String expression : expressions) {
			final ComparableExpression comparableExpression = expressionParser.parseExpression(expression);
			allConceptIds.addAll(comparableExpression.getAllConceptIds());
		}
		return transformationService.addHumanPTsToExpressionStrings(expressions, allConceptIds, context);
	}

}
