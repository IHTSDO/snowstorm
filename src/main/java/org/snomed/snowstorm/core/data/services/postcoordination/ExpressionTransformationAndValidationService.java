package org.snomed.snowstorm.core.data.services.postcoordination;

import com.google.common.collect.Sets;
import io.kaicode.elasticvc.api.BranchCriteria;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.languages.scg.domain.model.DefinitionStatus;
import org.snomed.snowstorm.config.Config;
import org.snomed.snowstorm.core.data.domain.ConceptMini;
import org.snomed.snowstorm.core.data.services.ConceptService;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.snomed.snowstorm.core.data.services.pojo.ResultMapPage;
import org.snomed.snowstorm.core.data.services.postcoordination.model.ComparableAttribute;
import org.snomed.snowstorm.core.data.services.postcoordination.model.ComparableExpression;
import org.snomed.snowstorm.core.data.services.postcoordination.transformation.*;
import org.snomed.snowstorm.core.pojo.TermLangPojo;
import org.snomed.snowstorm.ecl.ECLQueryService;
import org.snomed.snowstorm.mrcm.model.AttributeDomain;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static org.snomed.snowstorm.core.util.CollectionUtils.orEmpty;

@Service
public class ExpressionTransformationAndValidationService {

	@Autowired
	private ConceptService conceptService;

	@Autowired
	private ECLQueryService eclQueryService;

	@Autowired
	private ExpressionParser expressionParser;

	@Autowired
	private ExpressionMRCMValidationService mrcmValidationService;

	private final List<ExpressionTransformation> level2Transformations;

	private static final int CONCEPT_CACHE_SIZE = 500;
	private final LinkedHashMap<String, ConceptMini> conceptMiniCache = new LinkedHashMap<>();

	private final Logger logger = LoggerFactory.getLogger(getClass());
	public ExpressionTransformationAndValidationService(@Value("#{'${postcoordination.transform.self-grouped.attributes}'.split('\\s*,\\s*')}") Set<String> selfGroupedAttributes) {
		level2Transformations = new ArrayList<>();
		// TODO: Agree that lateralisation should come before context transformations so that they can both be applied to the same expression
		// TODO: Agree that refinement must come before self-grouped - then self-grouped can fail if that type already defined
		level2Transformations.add(new RefineExistingAttributeTransformation());
		level2Transformations.add(new GroupSelfGroupedAttributeTransformation(selfGroupedAttributes));
		level2Transformations.add(new LateraliseClinicalFindingTransformation());
		level2Transformations.add(new LateraliseProcedureTransformation());
		level2Transformations.add(new AddSeverityToClinicalFindingTransformation());
		level2Transformations.add(new AddContextToClinicalFindingTransformation());
		level2Transformations.add(new AddContextToProcedureTransformation());
	}

	public ComparableExpression validateAndTransform(ComparableExpression closeToUserForm, ExpressionContext context) throws ServiceException {

		// Check all concepts are active
		Set<String> allConceptIds = closeToUserForm.getAllConceptIds();
		Set<String> conceptIdsNotActiveOrNotExist = conceptService.getConceptIdsNotActiveOrNotExist(allConceptIds, context.getBranchCriteria());
		if (!conceptIdsNotActiveOrNotExist.isEmpty()) {
			throw new TransformationException(
					String.format("Some concepts used in this expression are not active, or do not exist, in this codesystem: %s", conceptIdsNotActiveOrNotExist));
		}

		// Collect focus concept ids
		List<Long> expressionFocusConceptIds = toLongList(closeToUserForm.getFocusConcepts());
		if (expressionFocusConceptIds.isEmpty()) {
			throw new TransformationException("Expression must have at least one focus concept.");
		}

		// Initial CTU MRCM attribute 'range' validation
		// NB Attribute 'domain' validation does not happen here
		mrcmValidationService.attributeRangeValidation(closeToUserForm, context);
		context.getTimer().checkpoint("MRCM attribute range validation");

		// Clone CTU to dereference object which will avoid any modification affecting input.
		ComparableExpression candidateClassifiableExpression = expressionParser.parseExpression(closeToUserForm.toString());
		boolean level1AssertedByDefStatus = candidateClassifiableExpression.getDefinitionStatus() != null;
		if (candidateClassifiableExpression.getDefinitionStatus() == null) {
			candidateClassifiableExpression.setDefinitionStatus(DefinitionStatus.EQUIVALENT_TO);
		}
		context.getTimer().checkpoint("Clone CTU");

		ComparableExpression classifiableForm = createClassifiableFormOptionallyTransform(context, candidateClassifiableExpression, level1AssertedByDefStatus);

		mrcmValidationService.attributeDomainValidation(classifiableForm, context);

		return classifiableForm;
	}

	private ComparableExpression createClassifiableFormOptionallyTransform(ExpressionContext context, ComparableExpression candidateClassifiableExpression,
			boolean level1AssertedByDefStatus) throws ServiceException {

		// Collect focus concept ancestors
		context.setEclQueryService(eclQueryService);

		Pair<List<ComparableAttribute>, List<ComparableAttribute>> looseAttributesUngroupedOrWrongDomain = getLooseAttributes(candidateClassifiableExpression, context);

		List<ComparableAttribute> looseAttributesUngrouped = looseAttributesUngroupedOrWrongDomain.getFirst();
		List<ComparableAttribute> looseAttributesWrongDomain = looseAttributesUngroupedOrWrongDomain.getSecond();
		List<ComparableAttribute> allLooseAttributes = joinLists(looseAttributesUngroupedOrWrongDomain);
		boolean noLooseAttributes = looseAttributesUngrouped.isEmpty() && looseAttributesWrongDomain.isEmpty();
		if (level1AssertedByDefStatus || noLooseAttributes) {
			// Level 1 expression
			if (!noLooseAttributes) {
				if (!looseAttributesWrongDomain.isEmpty())
					throw new TransformationException(String.format("The following attributes are not within the MRCM domain of the given focus concept : %s. " +
									"This expression includes a definition status so transformation to a valid classifiable form was not attempted.", looseAttributesWrongDomain));

				throw new TransformationException(String.format("The expression has one or more loose attributes (%s), these are ungrouped attributes that should be grouped. " +
						"This expression includes a definition status so transformation to a valid classifiable form was not attempted.", looseAttributesUngrouped));
			}
			// Classifiable expression is the same as the close to user form
			return candidateClassifiableExpression;

		} else {
			// Level 2 or invalid

			// Assert only one focus concept
			if (candidateClassifiableExpression.getFocusConcepts().size() != 1) {
				throw new TransformationException(String.format("The expression has one or more loose attributes (%s), these are ungrouped attributes that should" +
						" either be grouped or used within a different MRCM domain. " +
						"The expression can not be transformed to a valid classifiable form because it has multiple focus concepts.", allLooseAttributes));
			}

			// Prepare for transformations
			context.setConceptService(conceptService);

			String focusConceptId = candidateClassifiableExpression.getFocusConcepts().get(0);
			context.setFocusConceptId(focusConceptId);

			// Run transformations
			int looseAttributeCount = allLooseAttributes.size();
			logger.info("Attempting transformation:{}", candidateClassifiableExpression);
			for (ExpressionTransformation transformation : level2Transformations) {
				candidateClassifiableExpression = transformation.transform(allLooseAttributes, candidateClassifiableExpression, context);
				looseAttributesUngroupedOrWrongDomain = getLooseAttributes(candidateClassifiableExpression, context);
				looseAttributesUngrouped = looseAttributesUngroupedOrWrongDomain.getFirst();
				looseAttributesWrongDomain = looseAttributesUngroupedOrWrongDomain.getSecond();
				allLooseAttributes = joinLists(looseAttributesUngroupedOrWrongDomain);
				if (looseAttributeCount != allLooseAttributes.size()) {
					logger.info("{} applied:{}", transformation.getClass().getSimpleName(), candidateClassifiableExpression);
					looseAttributeCount = allLooseAttributes.size();
				}
				if (allLooseAttributes.isEmpty()) {
					break;
				}
			}
			// Make attributes null if empty, for consistency
			if (candidateClassifiableExpression.getComparableAttributes() != null && candidateClassifiableExpression.getComparableAttributes().isEmpty()) {
				candidateClassifiableExpression.setAttributes(null);
			}
			if (!allLooseAttributes.isEmpty()) {
				if (!looseAttributesWrongDomain.isEmpty()) {
					throw new TransformationException(String.format("The expression can not be transformed to a valid classifiable form. " +
									"The following attributes are not within the MRCM domain of the given focus concept " +
									"and do not match any of the agreed level 2 transformations: %s",
							looseAttributesWrongDomain));
				}
				throw new TransformationException(String.format("The expression can not be transformed to a valid classifiable form. " +
						"The following attributes should be grouped according to MRCM rules but do not match any of the agreed level 2 transformations: %s",
						looseAttributesUngrouped));
			}
			return candidateClassifiableExpression;
		}
	}

	private <T> List<T> joinLists(Pair<List<T>, List<T>> listPair) {
		List<T> list = new ArrayList<>(listPair.getFirst());
		list.addAll(listPair.getSecond());
		return list;
	}

	/**
	 *	Loose attributes are ungrouped attributes from expression of a type that should be grouped according to the MRCM rules.
	 *  They can also be ungrouped attributes that do not match the domain of the focus concept.
	 */
	private Pair<List<ComparableAttribute>, List<ComparableAttribute>> getLooseAttributes(ComparableExpression closeToUserForm, ExpressionContext context) throws ServiceException {
		Map<String, Set<AttributeDomain>> ungroupedAttributeMap = new HashMap<>();
		for (AttributeDomain attribute : context.getMRCMUngroupedAttributes()) {
			ungroupedAttributeMap.computeIfAbsent(attribute.getReferencedComponentId(), i -> new HashSet<>()).add(attribute);
		}

		List<ComparableAttribute> shouldBeGrouped = new ArrayList<>();
		List<ComparableAttribute> wrongDomain = new ArrayList<>();
		for (ComparableAttribute attribute : orEmpty(closeToUserForm.getComparableAttributes())) {
			if (!ungroupedAttributeMap.containsKey(attribute.getAttributeId())) {
				// Should be grouped
				shouldBeGrouped.add(attribute);
			} else {
				Set<AttributeDomain> attributeDomains = ungroupedAttributeMap.get(attribute.getAttributeId());
				Set<String> domains = attributeDomains.stream().map(AttributeDomain::getDomainId).collect(Collectors.toSet());
				// Attribute used in wrong domain (focus concept ancestors do not contain attribute domain)
				String ecl = String.join(" OR ", closeToUserForm.getFocusConcepts().stream().map(id -> ">>" + id).collect(Collectors.toSet()));
				if (Sets.intersection(context.ecl(ecl), domains).isEmpty()) {
					wrongDomain.add(attribute);
				}
			}
		}
		return Pair.of(shouldBeGrouped, wrongDomain);
	}

	private List<Long> toLongList(List<String> focusConcepts) {
		return focusConcepts == null ? Collections.emptyList() : focusConcepts.stream().map(Long::parseLong).collect(Collectors.toList());
	}

	public synchronized List<String> addHumanPTsToExpressionStrings(List<String> expressionStrings, Set<String> conceptIds, BranchCriteria branchCriteria, boolean stripConceptIds) {
		if (expressionStrings.isEmpty()) {
			return Collections.emptyList();
		}
		final Map<String, ConceptMini> conceptMap = findConceptMinisWithCaching(conceptIds, branchCriteria);

		// Strip existing terms from expressionStrings
		expressionStrings = expressionStrings.stream().map(s -> s.replaceAll(" ?\\|[^|]*\\|", "")).collect(Collectors.toList());

		for (String conceptId : conceptMap.keySet()) {
			final ConceptMini conceptMini = conceptMap.get(conceptId);
			if (conceptMini != null) {
				final TermLangPojo pt = conceptMini.getPt();
				final String term = pt != null ? pt.getTerm() : "-";
				if (term != null) {
					for (int i = 0; i < expressionStrings.size(); i++) {
						String expressionString = expressionStrings.get(i);
						if (expressionString != null) {
							expressionString = expressionString.replace(conceptId, stripConceptIds ? format("|%s|", term) : format("%s |%s|", conceptId, term));
							expressionStrings.set(i, expressionString);
						}
					}
				}
			}
		}
		return expressionStrings;
	}

	// NB: Quick and dirty cache. We get away with a simple implementation here while we are only dealing with a single branch.
	private Map<String, ConceptMini> findConceptMinisWithCaching(Set<String> conceptIds, BranchCriteria branchCriteria) {
		final Map<String, ConceptMini> conceptMap = new HashMap<>();
		Set<String> toLoad = new HashSet<>(conceptIds);
		for (String id : toLoad) {
			if (conceptMiniCache.containsKey(id)) {
				conceptMap.put(id, conceptMiniCache.get(id));
			}
		}
		toLoad.removeAll(conceptMap.keySet());
		if (!toLoad.isEmpty()) {
			final ResultMapPage<String, ConceptMini> conceptMinis = conceptService.findConceptMinis(branchCriteria, conceptIds, Config.DEFAULT_LANGUAGE_DIALECTS);
			conceptMap.putAll(conceptMinis.getResultsMap());
			conceptMiniCache.putAll(conceptMap);

			// Cache eviction
			if (conceptMiniCache.size() > CONCEPT_CACHE_SIZE) {
				final int toRemove = conceptMiniCache.size() - CONCEPT_CACHE_SIZE;
				Set<String> keysToRemove = new HashSet<>();
				for (String key : conceptMiniCache.keySet()) {
					if (keysToRemove.size() < toRemove) {
						keysToRemove.add(key);
					}
				}
				logger.info("Removing {} concepts from mini cache.", keysToRemove);
				conceptMiniCache.keySet().removeAll(keysToRemove);
			}
		}
		return conceptMap;
	}
}
