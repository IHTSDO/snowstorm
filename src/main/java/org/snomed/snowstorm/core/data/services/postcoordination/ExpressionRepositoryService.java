package org.snomed.snowstorm.core.data.services.postcoordination;

import ch.qos.logback.classic.Level;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import io.kaicode.elasticvc.api.BranchCriteria;
import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.api.VersionControlHelper;
import io.kaicode.elasticvc.domain.Branch;
import io.kaicode.elasticvc.domain.Commit;
import org.apache.logging.log4j.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.languages.scg.domain.model.*;
import org.snomed.snowstorm.config.Config;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.data.services.ConceptService;
import org.snomed.snowstorm.core.data.services.QueryService;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static java.lang.Long.parseLong;
import static org.snomed.snowstorm.core.data.domain.ReferenceSetMember.AssociationFields.TARGET_COMP_ID;
import static org.snomed.snowstorm.core.util.CollectionUtils.orEmpty;

@Service
public class ExpressionRepositoryService {

	public static final String EXPRESSION_PARTITION_ID = "16";

	@Value("${postcoordination.enabled}")
	private boolean postcoordinationEnabled;

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

	@Autowired
	private QueryService queryService;

	private static final Logger logger = LoggerFactory.getLogger(ExpressionRepositoryService.class);

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
	public static final String EXPRESSION_EQUIVALENT_CONCEPTS_ASSOCIATION_METADATA_KEY = "expressionEquivalentConceptAssociationRefset";
	private static final int SNOMED_INTERNATIONAL_DEMO_NAMESPACE = 1000003;
	private static final String EXPRESSION_FIELD = ReferenceSetMember.PostcoordinatedExpressionFields.EXPRESSION;

	public Page<PostCoordinatedExpression> findAll(String branch, PageRequest pageRequest) {
		Page<ReferenceSetMember> membersPage = memberService.findMembers(branch,
				new MemberSearchRequest()
						.referenceSet(CANONICAL_CLOSE_TO_USER_FORM_EXPRESSION_REFERENCE_SET),
				pageRequest);
		return getPostCoordinatedExpressions(pageRequest, membersPage);
	}

	public Page<PostCoordinatedExpression> findByExpression(String branch, String expression, String refset, PageRequest pageRequest) {
		return getPostCoordinatedExpressions(pageRequest, findExpressionMembers(branch, expression, refset, pageRequest));
	}

	private Page<ReferenceSetMember> findExpressionMembers(String branch, String expression, String refset, PageRequest pageRequest) {
		return memberService.findMembers(branch,
				new MemberSearchRequest()
						.referenceSet(refset)
						.active(true)
						.additionalField(EXPRESSION_FIELD, expression),
				pageRequest);
	}

	private PageImpl<PostCoordinatedExpression> getPostCoordinatedExpressions(PageRequest pageRequest, Page<ReferenceSetMember> membersPage) {
		List<PostCoordinatedExpression> expressions = membersPage.getContent().stream()
				.map((ReferenceSetMember closeToUserFormMember) -> toExpression(closeToUserFormMember, new ReferenceSetMember())).collect(Collectors.toList());
		return new PageImpl<>(expressions, pageRequest, membersPage.getTotalElements());
	}

	public PostCoordinatedExpression createExpression(String closeToUserFormExpression, CodeSystem codeSystem) throws ServiceException {
		List<PostCoordinatedExpression> expressions = createExpressionsAllOrNothing(Collections.singletonList(closeToUserFormExpression), codeSystem);
		return expressions.get(0);
	}

	public synchronized List<PostCoordinatedExpression> createExpressionsAllOrNothing(List<String> closeToUserFormExpressions, CodeSystem codeSystem) throws ServiceException {
		final List<PostCoordinatedExpression> postCoordinatedExpressions = processExpressions(closeToUserFormExpressions, codeSystem, null,
				true, false, DisplayTermsRequired.CF_AND_NNF);

		if (!postCoordinatedExpressions.isEmpty() && postCoordinatedExpressions.stream().noneMatch(PostCoordinatedExpression::hasException)
				&& postCoordinatedExpressions.stream().anyMatch(PostCoordinatedExpression::hasNullId)) {

			String moduleId = codeSystem.getUriModuleId();
			int namespace = IdentifierHelper.getNamespaceFromSCTID(moduleId);
			String branch = codeSystem.getBranchPath();
			try (Commit commit = branchService.openCommit(branch)) {
				List<ReferenceSetMember> membersToSave = new ArrayList<>();
				List<Concept> conceptsToSave = new ArrayList<>();
				Map<String, String> expressionIdCache = new HashMap<>();

				String equivalentConceptAssociationRefset = commit.getBranch().getMetadata().getString(EXPRESSION_EQUIVALENT_CONCEPTS_ASSOCIATION_METADATA_KEY);
				if (Strings.isBlank(equivalentConceptAssociationRefset)) {
					logger.error("Not able to persist equivalent concept associations on {} because branch metadata item {} is missing.",
							branch, EXPRESSION_EQUIVALENT_CONCEPTS_ASSOCIATION_METADATA_KEY);
					equivalentConceptAssociationRefset = null;
				}

				BranchCriteria branchCriteria = versionControlHelper.getBranchCriteria(branch);
				for (PostCoordinatedExpression postCoordinatedExpression : postCoordinatedExpressions) {
					if (postCoordinatedExpression.getId() != null) {
						continue;
					}

					// Save NNF for ECL
					ComparableExpression nnfExpression = postCoordinatedExpression.getNecessaryNormalFormExpression();
					final String expressionId = convertToConcepts(nnfExpression, false, namespace, conceptsToSave, branchCriteria, expressionIdCache);
					postCoordinatedExpression.setId(expressionId);

					Set<Long> equivalentConcepts = nnfExpression.getEquivalentConcepts();
					if (equivalentConcepts != null && !equivalentConcepts.isEmpty() && equivalentConceptAssociationRefset != null) {
						for (Long equivalentConcept : equivalentConcepts) {
							// Save equivalent concept link
							final ReferenceSetMember equivalentConceptMember = new ReferenceSetMember(moduleId, equivalentConceptAssociationRefset, expressionId)
									.setAdditionalField(TARGET_COMP_ID, equivalentConcept.toString());
							equivalentConceptMember.markChanged();
							membersToSave.add(equivalentConceptMember);
						}
					}

					// Save close to user form
					final ReferenceSetMember closeToUserFormMember = new ReferenceSetMember(moduleId, CANONICAL_CLOSE_TO_USER_FORM_EXPRESSION_REFERENCE_SET, expressionId)
							.setAdditionalField(EXPRESSION_FIELD, postCoordinatedExpression.getCloseToUserForm().replace(" ", ""));
					closeToUserFormMember.markChanged();
					membersToSave.add(closeToUserFormMember);

					// Save classifiable form
					final ReferenceSetMember classifiableFormMember = new ReferenceSetMember(moduleId, CLASSIFIABLE_FORM_EXPRESSION_REFERENCE_SET, expressionId)
							.setAdditionalField(EXPRESSION_FIELD, postCoordinatedExpression.getClassifiableForm().replace(" ", ""));
					classifiableFormMember.markChanged();
					membersToSave.add(classifiableFormMember);

					for (Concept concept : conceptsToSave) {
						// Clear modules to pick up branch module
						concept.setModuleId(null);
						for (Description description : orEmpty(concept.getDescriptions())) {
							description.setModuleId(null);
						}
						for (Relationship relationship : orEmpty(concept.getRelationships())) {
							relationship.setModuleId(null);
						}
					}

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

	private String convertToConcepts(ComparableExpression nnfExpression, boolean nested, int namespace, List<Concept> conceptsToSave,
			BranchCriteria branchCriteria, Map<String, String> expressionIdCache) throws ServiceException {

		String expression = nnfExpression.toStringCanonical();

		// Reuse NNF of existing expressions if they exist in cache (could be nested)
		if (expressionIdCache.containsKey(expression)) {
			return expressionIdCache.get(expression);
		}

		Concept concept = new Concept();
		concept.setDefinitionStatusId(nnfExpression.getDefinitionStatus() == DefinitionStatus.EQUIVALENT_TO ? Concepts.DEFINED : Concepts.PRIMITIVE);
		for (String inferredParent : nnfExpression.getFocusConcepts()) {
			concept.addRelationship(new Relationship(Concepts.ISA, inferredParent));
		}
		for (ComparableAttribute attribute : orEmpty( nnfExpression.getComparableAttributes())) {
			String attributeValueId;
			if (attribute.getAttributeValue().isNested()) {
				attributeValueId = convertToConcepts((ComparableExpression) attribute.getComparableAttributeValue().getNestedExpression(), true, namespace, conceptsToSave,
						branchCriteria, expressionIdCache);
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
					attributeValueId = convertToConcepts((ComparableExpression) attribute.getComparableAttributeValue().getNestedExpression(), true, namespace, conceptsToSave,
							branchCriteria, expressionIdCache);
				} else {
					attributeValueId = attribute.getAttributeValueId();
				}
				concept.addRelationship(new Relationship(attribute.getAttributeId(), attributeValueId).setGroupId(groupNumber));
			}
		}

		String conceptId = null;
		if (nested) {
			// Return existing concepts for nested expressions where possible
			conceptId = queryService.findConceptWithMatchingInferredRelationships(concept.getRelationships(), branchCriteria);
		}

		if (conceptId == null) {
			conceptId = getNewId(namespace).toString();
			concept.setConceptId(conceptId);
			final String relationshipSource = conceptId;
			concept.getRelationships().forEach(r -> r.setSourceId(relationshipSource));
			conceptsToSave.add(concept);
		}

		expressionIdCache.put(conceptId, expression);

		return conceptId;
	}

	/**
	 * Processing expressions includes the following steps:
	 * - Parse to validate syntax
	 * - Lookup any existing expressions in the store, if found return other forms from the store
	 * - Validate MRCM attribute range
	 * - Transform
	 * - Classify
	 */
	public List<PostCoordinatedExpression> processExpressions(List<String> originalCloseToUserForms, CodeSystem snomedCodeSystem, CodeSystemVersion snomedCodeSystemVersion,
			boolean classify, boolean equivalenceTest, DisplayTermsRequired displayTermsRequired) {

		if (!postcoordinationEnabled) {
			return originalCloseToUserForms.stream()
					.map(string -> new PostCoordinatedExpression(string, new ServiceException("Postcoordination is disabled on this server.")))
					.collect(Collectors.toList());
		}

		String branchPath;
		String packageModule;
		Integer packageEffectiveTime;
		if (snomedCodeSystem.isPostcoordinatedNullSafe()) {
			branchPath = snomedCodeSystem.getBranchPath();
			packageModule = snomedCodeSystem.getParentUriModuleId();
			packageEffectiveTime = snomedCodeSystem.getDependantVersionEffectiveTime();
		} else {
			branchPath = snomedCodeSystemVersion.getBranchPath();
			packageModule = snomedCodeSystem.getUriModuleId();
			packageEffectiveTime = snomedCodeSystemVersion.getEffectiveDate();
		}
		String classificationPackage = String.format("%s_%s_snapshot.zip", packageModule, packageEffectiveTime.toString());

		Branch branch = branchService.findLatest(branchPath);
		String equivalentConceptAssociationRefset = branch.getMetadata().getString(EXPRESSION_EQUIVALENT_CONCEPTS_ASSOCIATION_METADATA_KEY);
		if (Strings.isBlank(equivalentConceptAssociationRefset) && snomedCodeSystem.isPostcoordinatedNullSafe()) {
			logger.error("Not able to fetch stored equivalent concept associations on {} because branch metadata item {} is missing.",
					branchPath, EXPRESSION_EQUIVALENT_CONCEPTS_ASSOCIATION_METADATA_KEY);
			equivalentConceptAssociationRefset = null;
		}

		List<PostCoordinatedExpression> expressions = new ArrayList<>();

		ExpressionContext context = new ExpressionContext(branchPath, snomedCodeSystem.isPostcoordinatedNullSafe(), snomedCodeSystem.getMaximumPostcoordinationLevelNullSafe(),
				branchService, versionControlHelper, mrcmService, displayTermsRequired);

		for (String originalCloseToUserForm : originalCloseToUserForms) {
			TimerUtil timer = new TimerUtil("exp", Level.INFO, 5);
			context.reset(timer);
			try {
				// Sort contents of expression
				ComparableExpression closeToUserFormExpression = expressionParser.parseExpression(originalCloseToUserForm);
				timer.checkpoint("Parse expression");
				String canonicalCloseToUserForm = closeToUserFormExpression.toStringCanonical();

				BranchCriteria branchCriteria = context.getBranchCriteria();
				ReferenceSetMember foundCTUMember = findCTUExpressionMember(canonicalCloseToUserForm, branchCriteria);
				if (foundCTUMember != null) {
					// Found existing expression
					String expressionId = foundCTUMember.getReferencedComponentId();
					ReferenceSetMember cfExpressionMember = findCFExpressionMember(expressionId, branchCriteria);
					String cfExpression = cfExpressionMember != null ? cfExpressionMember.getAdditionalField(EXPRESSION_FIELD) : null;

					Collection<Concept> concepts = conceptService.find(branchCriteria, branchPath, Collections.singleton(expressionId), Config.DEFAULT_LANGUAGE_DIALECTS);
					ComparableExpression nnfExpression = null;
					if (!concepts.isEmpty()) {
						nnfExpression = conceptToNNFExpression(concepts.iterator().next());
						if (equivalentConceptAssociationRefset != null) {
							MemberSearchRequest searchRequest = new MemberSearchRequest()
									.referenceSet(equivalentConceptAssociationRefset)
									.referencedComponentId(expressionId)
									.active(true);
							Page<ReferenceSetMember> equivalentAssociations = memberService.findMembers(branchCriteria, searchRequest, PageRequest.of(0, 1));
							if (!equivalentAssociations.isEmpty()) {
								nnfExpression.setEquivalentConcepts(equivalentAssociations.stream()
										.map(member -> parseLong(member.getAdditionalField(TARGET_COMP_ID)))
										.collect(Collectors.toSet()));
							}
						}
					}
					expressions.add(new PostCoordinatedExpression(expressionId, foundCTUMember.getAdditionalField(EXPRESSION_FIELD), cfExpression, nnfExpression));
				} else {

					// Validate and transform expression to classifiable form if needed.
					// This groups any 'loose' attributes
					final ComparableExpression classifiableFormExpression;
					classifiableFormExpression = transformationService.validateAndTransform(closeToUserFormExpression, context);
					timer.checkpoint("Transformation");

					// No NNF yet
					expressions.add(new PostCoordinatedExpression(UUID.randomUUID().toString(), canonicalCloseToUserForm, classifiableFormExpression.toString(), null));
				}
				timer.finish();
			} catch (ServiceException e) {
				String humanReadableCloseToUserForm;
				try {
					humanReadableCloseToUserForm = createHumanReadableExpression(originalCloseToUserForm, context);
				} catch (ServiceException e2) {
					humanReadableCloseToUserForm = originalCloseToUserForm;
				}
				expressions.add(new PostCoordinatedExpression(humanReadableCloseToUserForm, e));
			}
		}

		TimerUtil timer = new TimerUtil("exp-batch", Level.INFO, 5);

		if (classify) {
			// Assign temp identifiers to classifiable forms for classification process
			Map<PostCoordinatedExpression, ComparableExpression> classifiableFormMap = new HashMap<>();
			for (PostCoordinatedExpression expression : expressions) {
				if (expression.hasException()) {
					continue;
				}
				try {
					// Parse CF string to expression object
					ComparableExpression cf = expressionParser.parseExpression(expression.getClassifiableForm());
					// Use any existing SCTID from repo
					if (!expression.getId().contains("-")) {
						cf.setExpressionId(parseLong(expression.getId()));
					}
					// Assign SCTID to any new expressions and nested expressions for classification
					applyToExpressions(cf, exp -> {
						if (exp.getExpressionId() == null) {
							exp.setExpressionId(getNewId(SNOMED_INTERNATIONAL_DEMO_NAMESPACE));
						}
					});
					classifiableFormMap.put(expression, cf);
				} catch (ServiceException e) {
					// Error outcome
					expression.setException(e);
				}
			}

			if (!classifiableFormMap.isEmpty()) {
				// Classify
				try {
					incrementalClassificationService.classify(classifiableFormMap, equivalenceTest, classificationPackage);
					timer.checkpoint("classify");
				} catch (ServiceException e) {
					// Error outcome for all classified expressions
					for (PostCoordinatedExpression expression : classifiableFormMap.keySet()) {
						expression.setException(e);
					}
				}
			}
		}

		if (!equivalenceTest) {
			for (PostCoordinatedExpression expression : expressions) {
				if (expression.hasException()) {
					continue;
				}
				try {
					populateHumanReadableForms(expression, context);
				} catch (ServiceException e) {
					expression.setException(e);
				}
			}
			timer.checkpoint("Add human readable");
		}

		// Clear temp expression ids
		expressions.forEach(expression -> {
			if (expression.getId() != null && expression.getId().contains("-")) {
				expression.setId(null);
			}
		});

		return expressions;
	}

	private ComparableExpression conceptToNNFExpression(Concept concept) {
		ComparableExpression expression = new ComparableExpression();
		expression.setDefinitionStatus(concept.getDefinitionStatusId().equals(Concepts.DEFINED) ? DefinitionStatus.EQUIVALENT_TO : DefinitionStatus.SUBTYPE_OF);

		Map<Integer, ComparableAttributeGroup> groupMap = new HashMap<>();
		for (Relationship relationship : orEmpty(concept.getRelationships())) {
			if (relationship.getGroupId() == 0) {
				if (relationship.getTypeId().equals(Concepts.ISA)) {
					expression.addFocusConcept(relationship.getDestinationId());
				} else {
					expression.addAttribute(relationship.getTypeId(), relationship.getDestinationId());
				}
			} else {
				groupMap.computeIfAbsent(relationship.getGroupId(), i -> new ComparableAttributeGroup())
						.addAttribute(new ComparableAttribute(relationship.getTypeId(), relationship.getDestinationId()));
			}
		}
		for (ComparableAttributeGroup group : groupMap.values()) {
			expression.addAttributeGroup(group);
		}
		return expression;
	}

	private static void applyToExpressions(ComparableExpression expression, Consumer<ComparableExpression> callable) {
		for (Attribute attribute : orEmpty(expression.getAttributes())) {
			if (attribute.getAttributeValue().isNested()) {
				applyToExpressions((ComparableExpression) attribute.getAttributeValue().getNestedExpression(), callable);
			}
		}
		for (AttributeGroup group : orEmpty(expression.getAttributeGroups())) {
			for (Attribute attribute : group.getAttributes()) {
				if (attribute.getAttributeValue().isNested()) {
					applyToExpressions((ComparableExpression) attribute.getAttributeValue().getNestedExpression(), callable);
				}
			}
		}
		callable.accept(expression);
	}

	private ReferenceSetMember findCTUExpressionMember(String expression, BranchCriteria branchCriteria) {
		MemberSearchRequest ctuSearchRequest = new MemberSearchRequest()
				.active(true)
				.referenceSet(CANONICAL_CLOSE_TO_USER_FORM_EXPRESSION_REFERENCE_SET)
				.additionalField(EXPRESSION_FIELD, expression);
		List<ReferenceSetMember> foundMembers = memberService.findMembers(branchCriteria, ctuSearchRequest, PageRequest.of(0, 1)).getContent();
		return foundMembers.isEmpty() ? null : foundMembers.get(0);
	}

	private ReferenceSetMember findCFExpressionMember(String expressionId, BranchCriteria branchCriteria) {
		MemberSearchRequest searchRequest = new MemberSearchRequest()
				.active(true)
				.referenceSet(CLASSIFIABLE_FORM_EXPRESSION_REFERENCE_SET)
				.referencedComponentId(expressionId);
		List<ReferenceSetMember> content = memberService.findMembers(branchCriteria, searchRequest, PageRequest.of(0, 1)).getContent();
		return content.isEmpty() ? null : content.get(0);
	}

	private Long getNewId(int namespace) {
		try {
			return identifierSource.reserveIds(namespace, EXPRESSION_PARTITION_ID, 1).get(0);
		} catch (ServiceException e) {
			logger.error("Failed to generate expression identifier for namespace " + namespace, e);
		}
		return null;
	}

	private void populateHumanReadableForms(PostCoordinatedExpression expressionForms, ExpressionContext context) throws ServiceException {
		List<String> expressions = new ArrayList<>();
		String closeToUserForm = null;
		String classifiableForm = null;
		String necessaryNormalForm = null;
		if (context.getDisplayTermsCombination() == DisplayTermsRequired.CTU_ONLY) {
			if (expressionForms.getCloseToUserForm() != null) {
				closeToUserForm = expressionForms.getCloseToUserForm();
				expressions.add(closeToUserForm);
			}
		} else if (context.getDisplayTermsCombination() == DisplayTermsRequired.CF_AND_NNF) {
			if (expressionForms.getClassifiableForm() != null) {
				classifiableForm = expressionForms.getClassifiableForm();
				expressions.add(classifiableForm);
			}
			if (expressionForms.getNecessaryNormalForm() != null) {
				necessaryNormalForm = expressionForms.getNecessaryNormalForm();
				expressions.add(necessaryNormalForm);
			}
		}
		final List<String> humanReadableExpressions = createHumanReadableExpressions(expressions, context.getBranchCriteria());
		int i = 0;
		if (closeToUserForm != null) {
			expressionForms.setHumanReadableCloseToUserForm(humanReadableExpressions.get(i++));
		}
		if (classifiableForm != null) {
			expressionForms.setHumanReadableClassifiableForm(humanReadableExpressions.get(i++));
		}
		if (necessaryNormalForm != null) {
			expressionForms.setHumanReadableNecessaryNormalForm(humanReadableExpressions.get(i));
		}
	}

	private PostCoordinatedExpression toExpression(ReferenceSetMember closeToUserFormMember, ReferenceSetMember classifiableFormMember) {
		return new PostCoordinatedExpression(closeToUserFormMember.getReferencedComponentId(),
				closeToUserFormMember.getAdditionalField(EXPRESSION_FIELD), classifiableFormMember.getAdditionalField(EXPRESSION_FIELD), null);
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
				timer.checkpoint("getMandatoryAttributeRanges");
				ExpressionMRCMValidationService.throwAttributeRangeError(attributeId, conceptIdAndFsnTerm, attributeValueFromStore, mandatoryAttributeRanges);
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
			return createHumanReadableExpressions(Lists.newArrayList(expression), context.getBranchCriteria()).get(0);
		}
		return null;
	}

	private List<String> createHumanReadableExpressions(List<String> expressions, BranchCriteria branchCriteria) throws ServiceException {
		if (expressions.isEmpty()) {
			return Collections.emptyList();
		}
		final Set<String> allConceptIds = new HashSet<>();
		for (String expression : expressions) {
			final ComparableExpression comparableExpression = expressionParser.parseExpression(expression);
			allConceptIds.addAll(comparableExpression.getAllConceptIds());
		}
		return transformationService.addHumanPTsToExpressionStrings(expressions, allConceptIds, branchCriteria, false);
	}

	public void addHumanReadableExpressions(Map<String, ReferenceSetMember> expressionMap, BranchCriteria branchCriteria) {
		expressionMap = new LinkedHashMap<>(expressionMap);
		Set<String> expressionConcepts = new HashSet<>();
		List<String> expressionStrings = new ArrayList<>();
		for (ReferenceSetMember member : expressionMap.values()) {
			String expressionString = member.getAdditionalField(EXPRESSION_FIELD);
			try {
				ComparableExpression comparableExpression = expressionParser.parseExpression(expressionString);
				expressionConcepts.addAll(comparableExpression.getAllConceptIds());
				expressionStrings.add(expressionString);
			} catch (ServiceException e) {
				logger.info("Failed to parse persisted expression '{}'", expressionString);
				expressionStrings.add("");
			}
		}
		List<String> expressionStringsWithTerms = transformationService.addHumanPTsToExpressionStrings(expressionStrings, expressionConcepts, branchCriteria, true);
		Iterator<String> withTermsIterator = expressionStringsWithTerms.iterator();
		for (ReferenceSetMember member : expressionMap.values()) {
			String term = withTermsIterator.next();
			if (term != null && !term.isEmpty()) {
				member.setAdditionalField(ReferenceSetMember.PostcoordinatedExpressionFields.TRANSIENT_EXPRESSION_TERM, term);
			}
		}
	}
}
