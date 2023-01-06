package org.snomed.snowstorm.core.data.services.postcoordination;

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
import org.snomed.snowstorm.core.data.services.postcoordination.transformation.ExpressionTransformation;
import org.snomed.snowstorm.core.data.services.postcoordination.transformation.GroupSelfGroupedAttribute;
import org.snomed.snowstorm.core.pojo.TermLangPojo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

import static java.lang.String.format;

@Service
public class ExpressionTransformationAndValidationService {

	@Autowired
	private ConceptService conceptService;

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
		level2Transformations.add(new GroupSelfGroupedAttribute(selfGroupedAttributes));
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

		// Initial CTU MRCM attribute range validation
		// NB Attribute domain validation does not happen here
		mrcmValidationService.attributeRangeValidation(closeToUserForm, context);
		context.getTimer().checkpoint("MRCM attribute range validation");

		// Clone CTU to dereference object which will avoid any modification affecting input.
		// TODO: Should the presence of a definition status in the CTU force evaluation against transformation Level 1?
		ComparableExpression candidateClassifiableExpression = expressionParser.parseExpression(closeToUserForm.toString());
		if (candidateClassifiableExpression.getDefinitionStatus() == null) {
			candidateClassifiableExpression.setDefinitionStatus(DefinitionStatus.EQUIVALENT_TO);
		}
		context.getTimer().checkpoint("Clone CTU");

		ComparableExpression classifiableForm = createClassifiableForm(context, candidateClassifiableExpression);

		// TODO: Apply MRCM Attribute Domain validation here
		// TODO: Apply MRCM attribute cardinality validation after classification

		return classifiableForm;
	}

	private ComparableExpression createClassifiableForm(ExpressionContext context, ComparableExpression candidateClassifiableExpression) throws ServiceException {
		List<ComparableAttribute> looseAttributes = getLooseAttributes(candidateClassifiableExpression, context);

		if (looseAttributes.isEmpty()) {
			// Level 1 expression
			// Classifiable expression is the same as the close to user form
			return candidateClassifiableExpression;

		} else {
			// Level 2 or invalid
			List<ComparableAttribute> remainingLooseAttributes = new ArrayList<>();
			for (ComparableAttribute looseAttribute : looseAttributes) {
				boolean transformed = false;
				for (ExpressionTransformation transformation : level2Transformations) {
					if (transformation.transform(looseAttribute, candidateClassifiableExpression, context)) {
						// This transformation was able to transform the loose attribute
						transformed = true;

						// Make attributes null if empty, for consistency
						if (candidateClassifiableExpression.getComparableAttributes() != null && candidateClassifiableExpression.getComparableAttributes().isEmpty()) {
							candidateClassifiableExpression.setAttributes(null);
						}
						break;
					}
				}
				if (!transformed) {
					remainingLooseAttributes.add(looseAttribute);
				}
			}
			if (!remainingLooseAttributes.isEmpty()) {
					throw new TransformationException(String.format("The expression can not be transformed to a valid classifiable form. " +
							"The following attributes should be grouped according to MRCM rules but do not match any of the agreed level 2 transformations: %s",
							remainingLooseAttributes));
			}
			return candidateClassifiableExpression;
		}
	}

	/**
	 *	Loose attributes are ungrouped attributes from expression of a type that should be grouped according to the MRCM rules.
	 */
	private List<ComparableAttribute> getLooseAttributes(ComparableExpression closeToUserForm, ExpressionContext context) throws ServiceException {
		Set<String> ungroupedAttributes = context.getMRCMUngroupedAttributes();
		Set<ComparableAttribute> comparableAttributes = closeToUserForm.getComparableAttributes();
		if (comparableAttributes == null) {
			return Collections.emptyList();
		}
		return comparableAttributes.stream()
				.filter(attribute -> !ungroupedAttributes.contains(attribute.getAttributeId()))
				.collect(Collectors.toList());
	}

	private List<Long> toLongList(List<String> focusConcepts) {
		return focusConcepts == null ? Collections.emptyList() : focusConcepts.stream().map(Long::parseLong).collect(Collectors.toList());
	}

	public synchronized List<String> addHumanPTsToExpressionStrings(List<String> expressionStrings, Set<String> conceptIds, ExpressionContext context) {
		if (expressionStrings.isEmpty()) {
			return Collections.emptyList();
		}
		final Map<String, ConceptMini> conceptMap = findConceptMinisWithCaching(conceptIds, context);

		for (String conceptId : conceptMap.keySet()) {
			final ConceptMini conceptMini = conceptMap.get(conceptId);
			if (conceptMini != null) {
				final TermLangPojo pt = conceptMini.getPt();
				final String term = pt != null ? pt.getTerm() : "-";
				if (term != null) {
					for (int i = 0; i < expressionStrings.size(); i++) {
						String expressionString = expressionStrings.get(i);
						if (expressionString != null) {
							expressionString = expressionString.replace(conceptId, format("%s |%s|", conceptId, term));
							expressionStrings.set(i, expressionString);
						}
					}
				}
			}
		}
		return expressionStrings;
	}

	// NB: Quick and dirty cache. We get away with a simple implementation here while we are only dealing with a single branch.
	private Map<String, ConceptMini> findConceptMinisWithCaching(Set<String> conceptIds, ExpressionContext context) {
		final Map<String, ConceptMini> conceptMap = new HashMap<>();
		Set<String> toLoad = new HashSet<>(conceptIds);
		for (String id : toLoad) {
			if (conceptMiniCache.containsKey(id)) {
				conceptMap.put(id, conceptMiniCache.get(id));
			}
		}
		toLoad.removeAll(conceptMap.keySet());
		if (!toLoad.isEmpty()) {
			final ResultMapPage<String, ConceptMini> conceptMinis = conceptService.findConceptMinis(context.getBranchCriteria(), conceptIds, Config.DEFAULT_LANGUAGE_DIALECTS);
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
