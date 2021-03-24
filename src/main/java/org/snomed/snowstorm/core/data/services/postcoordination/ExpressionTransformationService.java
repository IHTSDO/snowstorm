package org.snomed.snowstorm.core.data.services.postcoordination;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.languages.scg.domain.model.Attribute;
import org.snomed.languages.scg.domain.model.DefinitionStatus;
import org.snomed.snowstorm.config.Config;
import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.core.data.domain.ConceptMini;
import org.snomed.snowstorm.core.data.domain.Concepts;
import org.snomed.snowstorm.core.data.domain.Relationship;
import org.snomed.snowstorm.core.data.services.ConceptService;
import org.snomed.snowstorm.core.data.services.ReferenceSetMemberService;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.snomed.snowstorm.core.data.services.pojo.MemberSearchRequest;
import org.snomed.snowstorm.core.data.services.pojo.ResultMapPage;
import org.snomed.snowstorm.core.data.services.postcoordination.model.ComparableAttribute;
import org.snomed.snowstorm.core.data.services.postcoordination.model.ComparableAttributeGroup;
import org.snomed.snowstorm.core.data.services.postcoordination.model.ComparableAttributeValue;
import org.snomed.snowstorm.core.data.services.postcoordination.model.ComparableExpression;
import org.snomed.snowstorm.core.pojo.TermLangPojo;
import org.snomed.snowstorm.mrcm.MRCMService;
import org.snomed.snowstorm.mrcm.model.AttributeDomain;
import org.snomed.snowstorm.mrcm.model.ContentType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.lang.Long.parseLong;
import static java.lang.String.format;
import static org.snomed.otf.owltoolkit.constants.Concepts.LATERALITY;
import static org.snomed.snowstorm.config.Config.PAGE_OF_ONE;

@Service
public class ExpressionTransformationService {

	@Autowired
	private ConceptService conceptService;

	@Autowired
	private ExpressionParser expressionParser;

	@Autowired
	private MRCMService mrcmService;

	@Autowired
	private ReferenceSetMemberService memberService;

	@Value("${postcoordination.transform.self-grouped.attributes}")
	private Set<String> selfGroupedAttributes;

	final Logger logger = LoggerFactory.getLogger(getClass());

	public ComparableExpression transform(ComparableExpression closeToUserForm, ExpressionContext context) throws ServiceException {
		// Dereference input with clone of object to avoid any modification affecting input.
		closeToUserForm = expressionParser.parseExpression(closeToUserForm.toString());
		if (closeToUserForm.getDefinitionStatus() == null) {
			closeToUserForm.setDefinitionStatus(DefinitionStatus.EQUIVALENT_TO);
		}
		context.getTimer().checkpoint("Parse");

		// Collect focus concept ids
		List<Long> expressionFocusConceptIds = toLongList(closeToUserForm.getFocusConcepts());
		if (expressionFocusConceptIds.isEmpty()) {
			throw new TransformationException("No focus concepts given.");
		}

		// Load focus concepts
		List<Concept> focusConcepts = loadConcepts(expressionFocusConceptIds, context);
		Map<Long, Concept> focusConceptMap = focusConcepts.stream().collect(Collectors.toMap(Concept::getConceptIdAsLong, Function.identity()));
		context.getTimer().checkpoint("Load focus concepts");

		// Create a new expression for each focus concept, these will be merged into one once the loose attributes have been applied
		Map<Long, ComparableExpression> classifiableExpressionsMap = new HashMap<>();
		for (Long expressionFocusConceptId : expressionFocusConceptIds) {
			Concept focusConcept = focusConceptMap.get(expressionFocusConceptId);
			if (focusConcept == null) {
				throw new TransformationException(String.format("Focus concept %s not found.", expressionFocusConceptId));
			}
			if (!focusConcept.isActive()) {
				throw new TransformationException(String.format("Focus concept %s is not active.", expressionFocusConceptId));
			}

			classifiableExpressionsMap.put(expressionFocusConceptId, new ComparableExpression(expressionFocusConceptId.toString()));
		}

		// Grab ungrouped attributes from input expression
		Set<ComparableAttribute> looseAttributes = new HashSet<>();
		if (closeToUserForm.getAttributes() != null) {
			looseAttributes.addAll(closeToUserForm.getComparableAttributes());
		}
		if (!looseAttributes.isEmpty()) {
			// Load MRCM domain attributes for each focus concept
			Map<Long, Set<String>> conceptMRCMDomainAttributeIdsMap = new HashMap<>();
			for (Concept focusConcept : focusConcepts) {
				getDomainAttributes(focusConcept.getConceptIdAsLong(), conceptMRCMDomainAttributeIdsMap, context);
			}

			//# For each ungrouped attribute in input expression; use the MRCM to find the most applicable part of the model to apply them to:
			for (ComparableAttribute looseAttribute : looseAttributes) {
				//# For each focus concept, if the attribute type is within the MRCM domain:
				boolean appliedToFocusConcept = false;
				for (Concept focusConcept : focusConcepts) {
					final Set<String> focusConceptDomainAttributes = conceptMRCMDomainAttributeIdsMap.get(focusConcept.getConceptIdAsLong());
					if (focusConceptDomainAttributes.contains(looseAttribute.getAttributeId())) {
						//# Apply the Merge Procedure
						mergeAttribute(looseAttribute, focusConcept, classifiableExpressionsMap.get(focusConcept.getConceptIdAsLong()), context);
						appliedToFocusConcept = true;
					}
				}
				//# Else if no match from previous step
				if (!appliedToFocusConcept) {
					//# For each attribute value within the definition of the focus concepts,
					//  if the loose attribute type is within the MRCM domain of this attribute value:
					boolean appliedToNestedExpression = false;
					for (Concept focusConcept : focusConcepts) {
						final List<Relationship> activeInferredRelationships = focusConcept.getActiveInferredRelationships();
						for (Relationship relationship : activeInferredRelationships) {
							if (!relationship.isConcrete() && !relationship.getTypeId().equals(Concepts.ISA)) {// Transformation can not be applied to a concrete domain value.
								final Set<String> attributeValueMrcmDomainAttributes =
										getDomainAttributes(parseLong(relationship.getDestinationId()), conceptMRCMDomainAttributeIdsMap, context);
								if (attributeValueMrcmDomainAttributes.contains(looseAttribute.getAttributeId())) {

									//# Copy the attribute from the focus concept's definition into the expression
									final ComparableExpression expression = classifiableExpressionsMap.get(focusConcept.getConceptIdAsLong());

									//# Convert the attribute value to a nested expression using the original value as the focus concept
									final ComparableExpression nestedExpression = new ComparableExpression(relationship.getDestinationId());

									//# Apply the Merge Procedure
									Concept attributeTargetConcept = loadConcepts(Collections.singletonList(parseLong(relationship.getDestinationId())), context).get(0);
									mergeAttribute(looseAttribute, attributeTargetConcept, nestedExpression, context);

									final ComparableAttribute nestedAttribute = new ComparableAttribute(relationship.getTypeId(),
											new ComparableAttributeValue(nestedExpression));

									// Copy over relationship or whole group
									// TODO: should keep track of what attributes have already been copied over as this step may have been done already.
									if (relationship.isGrouped()) {
										boolean existingGroup = false;
										ComparableAttributeGroup attributeGroup = null;
										// Reuse existing group if already added
										final Set<ComparableAttributeGroup> attributeGroups = expression.getComparableAttributeGroups();
										if (attributeGroups != null) {
											attributeGroup = attributeGroups.stream()
													.filter(group -> group.getTransformationGroupId() != null && group.getTransformationGroupId().equals(relationship.getGroupId()))
													.findFirst()
													.orElse(null);
										}
										if (attributeGroup == null) {
											attributeGroup = new ComparableAttributeGroup(relationship.getGroupId());
										} else {
											existingGroup = true;
										}
										// Add other relationships in group
										final ComparableAttributeGroup finalGroup = attributeGroup;
										activeInferredRelationships.stream()
												.filter(r -> !r.getId().equals(relationship.getId()) && r.getGroupId() == relationship.getGroupId())
												.forEach(r -> finalGroup.addAttribute(r.getTypeId(), r.getDestinationId()));
										attributeGroup.addAttribute(nestedAttribute);
										if (!existingGroup) {
											expression.addAttributeGroup(attributeGroup);
										}
									} else {
										expression.addAttribute(nestedAttribute);
									}


									appliedToNestedExpression = true;
									// Continue to loop and apply this loose attribute to any other applicable focus concept attributes.
								}
							}
						}
					}
					if (!appliedToNestedExpression) {
						Set<String> ids = new HashSet<>();
						looseAttribute.getAllConceptIds(ids);
						String problemAttributeString = addHumanPTsToExpressionString(looseAttribute.toString(), ids, context);
						throw new TransformationException(String.format("Loose attribute \"%s\" is not within the MRCM domain of any of the focus concepts " +
										"or their attribute values. ", problemAttributeString));
					}
				}
			}
		}

		// Create final classifiable expression
		ComparableExpression classifiableExpression = new ComparableExpression();
		classifiableExpression.setDefinitionStatus(closeToUserForm.getDefinitionStatus());
		for (ComparableExpression focusConceptExpression : classifiableExpressionsMap.values()) {
			classifiableExpression.merge(focusConceptExpression);
		}
		// Attribute groups from input expression are added as they are.
		if (closeToUserForm.getAttributeGroups() != null) {
			for (ComparableAttributeGroup attributeGroup : closeToUserForm.getComparableAttributeGroups()) {
				classifiableExpression.addAttributeGroup(attributeGroup);
			}
		}

		return classifiableExpression;
	}

	private List<Concept> loadConcepts(List<Long> conceptIds, ExpressionContext context) throws TransformationException {
		Set<Long> conceptIdSet = new HashSet<>(conceptIds);

		Map<String, Concept> conceptMap = new HashMap<>();
		for (Long conceptId : conceptIds) {
			conceptMap.put(conceptId.toString(), new Concept(conceptId.toString()));
		}
		conceptService.joinRelationships(conceptMap, new HashMap<>(), null, context.getBranch(), context.getBranchCriteria(), context.getTimer(), true);

		if (conceptMap.size() != conceptIdSet.size()) {
			throw new TransformationException(String.format("Not all concepts could be loaded: %s", conceptIdSet));
		}
		return conceptIds.stream().map(id -> conceptMap.get(id.toString())).collect(Collectors.toList());
	}

	private Set<String> getDomainAttributes(Long conceptId, Map<Long, Set<String>> conceptMRCMDomainAttributeIdsMap, ExpressionContext context) throws ServiceException {
		if (!conceptMRCMDomainAttributeIdsMap.containsKey(conceptId)) {
			final Set<String> domainAttributeIds = mrcmService.doRetrieveDomainAttributes(ContentType.POSTCOORDINATED, false, Collections.singleton(conceptId),
					context.getBranchCriteria(), context.getBranchMRCM())
					.stream()
					.map(AttributeDomain::getReferencedComponentId).collect(Collectors.toSet());
			if (domainAttributeIds.contains(LATERALITY)) {
				if (memberService.findMembers(context.getBranch(), context.getBranchCriteria(),
						new MemberSearchRequest()
								.active(true).referenceSet(Concepts.LATERALIZABLE_BODY_STRUCTURE_REFERENCE_SET).referencedComponentId(conceptId.toString()), PAGE_OF_ONE).isEmpty()) {
					// Concept not lateralizable, remove the laterality attribute
					domainAttributeIds.remove(LATERALITY);
					logger.info("Concept {} is not lateralizable.", conceptId);
				} else {
					logger.info("Concept {} is lateralizable.", conceptId);
				}
			}
			conceptMRCMDomainAttributeIdsMap.put(conceptId, domainAttributeIds);
		}
		return conceptMRCMDomainAttributeIdsMap.get(conceptId);
	}

	private void mergeAttribute(Attribute looseAttribute, Concept concept, ComparableExpression classifiableExpression, ExpressionContext context) throws ServiceException {
		final AttributeDomain attributeDomain = getAttributeDomainOrThrow(context, looseAttribute.getAttributeId());

		//# If attribute type is marked as not groupable in the MRCM: copy into expression refinement as ungrouped
		if (!attributeDomain.isGrouped()) {
			classifiableExpression.addAttribute(new ComparableAttribute(looseAttribute));

		//# Else if attribute type is in the predefined self-grouped list: copy into expression refinement as self-grouped
		} else if (selfGroupedAttributes.contains(looseAttribute.getAttributeId())) {
			classifiableExpression.addAttributeGroup(new ComparableAttributeGroup(new ComparableAttribute(looseAttribute)));

		//# Else
		} else {
			//# Bring all groups from the provided concept into the expression
			if (!classifiableExpression.isConceptMerged(concept.getConceptId())) {
				classifiableExpression.merge(concept);
			}
			boolean addedToAnyGroup = false;
            final Set<ComparableAttributeGroup> comparableAttributeGroups = classifiableExpression.getComparableAttributeGroups();
            if (comparableAttributeGroups != null) {
                for (ComparableAttributeGroup targetGroup : comparableAttributeGroups) {
                    //# Copy the loose attribute into every group, except those that contain just an attribute from the predefined self-grouped list.
                    final List<Attribute> attributes = targetGroup.getAttributes();
                    if (attributes.size() > 1 || !selfGroupedAttributes.contains(attributes.get(0).getAttributeId())) {
                        targetGroup.addAttribute(looseAttribute);
                        addedToAnyGroup = true;
                    }
                }
            }
			if (!addedToAnyGroup) {
				// add in new group
				classifiableExpression.addAttributeGroup(new ComparableAttributeGroup(new ComparableAttribute(looseAttribute)));
			}
		}
	}

	private AttributeDomain getAttributeDomainOrThrow(ExpressionContext context, String attributeId) throws ServiceException {
		final Optional<AttributeDomain> attributeDomainOptional = context.getBranchMRCM().getAttributeDomains().stream()
				.filter(attDom -> attDom.getReferencedComponentId().equals(attributeId)).findFirst();
		if (attributeDomainOptional.isEmpty()) {
			throw new TransformationException(String.format("MRCM attribute domain for attribute %s not found.", attributeId));
		}
		return attributeDomainOptional.get();
	}

	private List<Long> toLongList(List<String> focusConcepts) {
		return focusConcepts == null ? Collections.emptyList() : focusConcepts.stream().map(Long::parseLong).collect(Collectors.toList());
	}

	public String addHumanPTsToExpressionString(String expressionString, Set<String> conceptIds, ExpressionContext context) {
		final ResultMapPage<String, ConceptMini> conceptMinis = conceptService.findConceptMinis(context.getBranchCriteria(), conceptIds, Config.DEFAULT_LANGUAGE_DIALECTS);
		final Map<String, ConceptMini> conceptMap = conceptMinis.getResultsMap();
		for (String conceptId : conceptMap.keySet()) {
			final ConceptMini conceptMini = conceptMap.get(conceptId);
			if (conceptMini != null) {
				final TermLangPojo pt = conceptMini.getPt();
				final String term = pt != null ? pt.getTerm() : "-";
				if (term != null) {
					expressionString = expressionString.replace(conceptId, format("%s |%s|", conceptId, term));
				}
			}
		}
		return expressionString;
	}
}
