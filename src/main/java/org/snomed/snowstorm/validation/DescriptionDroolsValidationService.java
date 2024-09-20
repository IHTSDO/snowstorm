package org.snomed.snowstorm.validation;

import com.google.common.collect.Sets;
import io.kaicode.elasticvc.api.BranchCriteria;
import org.ihtsdo.drools.domain.Constants;
import org.ihtsdo.drools.helper.DescriptionHelper;
import org.ihtsdo.drools.service.TestResourceProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.core.data.domain.Concepts;
import org.snomed.snowstorm.core.data.domain.Description;
import org.snomed.snowstorm.core.data.services.DescriptionService;
import org.snomed.snowstorm.validation.domain.DroolsDescription;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.stream.Collectors;

import static co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders.*;
import static io.kaicode.elasticvc.helper.QueryHelper.*;

public class DescriptionDroolsValidationService implements org.ihtsdo.drools.service.DescriptionService {

	private final String branchPath;
	private final BranchCriteria branchCriteria;
	private final ElasticsearchOperations elasticsearchOperations;
	private final DescriptionService descriptionService;
	private final DisposableQueryService queryService;
	private final TestResourceProvider testResourceProvider;
	private final Map<String, String> statedHierarchyRootIdCache = Collections.synchronizedMap(new HashMap<>());
	private final Set<String> inferredTopLevelHierarchies;
	private static final Logger LOGGER = LoggerFactory.getLogger(DescriptionDroolsValidationService.class);

	DescriptionDroolsValidationService(String branchPath,
			BranchCriteria branchCriteria,
			ElasticsearchOperations elasticsearchOperations,
			DescriptionService descriptionService,
			DisposableQueryService queryService,
			TestResourceProvider testResourceProvider,
			Set<String> inferredTopLevelHierarchies) {

		this.branchPath = branchPath;
		this.branchCriteria = branchCriteria;
		this.elasticsearchOperations = elasticsearchOperations;
		this.descriptionService = descriptionService;
		this.queryService = queryService;
		this.testResourceProvider = testResourceProvider;
		this.inferredTopLevelHierarchies = inferredTopLevelHierarchies;
	}

	@Override
	public Set<String> getFSNs(Set<String> conceptIds, String... languageRefsetIds) {
		return descriptionService.findDescriptionsByConceptId(branchPath, conceptIds, languageRefsetIds.length > 0 ? true : false).stream()
				.filter(d -> d.isActive() && d.getTypeId().equals(Concepts.FSN) && (languageRefsetIds.length == 0 || d.getLangRefsetMembersMap().keySet().stream().anyMatch(k -> Arrays.asList(languageRefsetIds).contains(k))))
				.map(org.snomed.snowstorm.core.data.domain.Description::getTerm)
				.collect(Collectors.toSet());
	}

	@Override
	public Set<org.ihtsdo.drools.domain.Description> findActiveDescriptionByExactTerm(String exactTerm) {
		return findDescriptionByExactTerm(exactTerm, true);
	}

	@Override
	public Set<org.ihtsdo.drools.domain.Description> findInactiveDescriptionByExactTerm(String exactTerm) {
		return findDescriptionByExactTerm(exactTerm, false);
	}

	private Set<org.ihtsdo.drools.domain.Description> findDescriptionByExactTerm(String exactTerm, boolean active) {
		NativeQuery query = new NativeQueryBuilder()
				.withQuery(bool(b -> b
						.must(branchCriteria.getEntityBranchCriteria(Description.class))
						.must(termQuery("active", active))
						.must(termQuery("term", exactTerm)))
				)
				.build();
		List<Description> matches = elasticsearchOperations.search(query, Description.class).get().map(SearchHit::getContent).toList();
		return matches.stream()
				.filter(description -> description.getTerm().equals(exactTerm))
				.map(DroolsDescription::new).collect(Collectors.toSet());
	}

	@Override
	public Set<org.ihtsdo.drools.domain.Description> findMatchingDescriptionInHierarchy(org.ihtsdo.drools.domain.Concept concept, org.ihtsdo.drools.domain.Description description) {
		try {
			Set<org.ihtsdo.drools.domain.Description> matchingDescriptions = findActiveDescriptionByExactTerm(description.getTerm())
					.stream().filter(d -> d.getLanguageCode().equals(description.getLanguageCode())).collect(Collectors.toSet());

			if (!matchingDescriptions.isEmpty()) {
				// Filter matching descriptions by hierarchy

				// Find root for this concept
				String conceptHierarchyRootId = findStatedHierarchyRootId(concept);
				if (conceptHierarchyRootId != null) {
					return matchingDescriptions.stream().filter(d -> {
						Set<String> statedAncestors = queryService.findAncestorIds(true, d.getConceptId());
						return statedAncestors.contains(conceptHierarchyRootId);
					}).collect(Collectors.toSet());
				}
			}
		} catch (IllegalArgumentException e) {
			LOGGER.error("Drools rule failed.", e);
		}
		return Collections.emptySet();
	}

	@Override
	public String getLanguageSpecificErrorMessage(org.ihtsdo.drools.domain.Description description) {
		if (description == null || description.getAcceptabilityMap() == null || description.getTerm() == null) {
			return "";
		}

		return DescriptionHelper.getLanguageSpecificErrorMessage(description, testResourceProvider.getUsToGbTermMap());
	}

	@Override
	public String getCaseSensitiveWordsErrorMessage(org.ihtsdo.drools.domain.Description description) {
		if (description == null || description.getTerm() == null) {
			return "";
		}

		return DescriptionHelper.getCaseSensitiveWordsErrorMessage(description, testResourceProvider.getCaseSignificantWords());
	}

	@Override
	public Set<String> findParentsNotContainingSemanticTag(org.ihtsdo.drools.domain.Concept concept, String termSemanticTag, String... languageRefsetIds) {
		Set<String> statedParents = new HashSet<>();
		for (org.ihtsdo.drools.domain.Relationship relationship : concept.getRelationships()) {
			if (Constants.IS_A.equals(relationship.getTypeId())
					&& relationship.isActive()
					&& Constants.STATED_RELATIONSHIP.equals(relationship.getCharacteristicTypeId())) {
				statedParents.add(relationship.getDestinationId());
			}
		}

		return descriptionService.findDescriptionsByConceptId(branchPath, statedParents, languageRefsetIds.length > 0 ? true : false).stream()
				.filter(d -> d.isActive() && d.getTypeId().equals(Concepts.FSN) && !d.getTag().equals(termSemanticTag) && (languageRefsetIds.length == 0 || d.getLangRefsetMembersMap().keySet().stream().anyMatch(k -> Arrays.asList(languageRefsetIds).contains(k))))
				.map(Description::getConceptId)
				.collect(Collectors.toSet());
	}

	@Override
	public boolean isRecognisedSemanticTag(String semanticTag, String language) {
		return semanticTag != null && !semanticTag.isEmpty() && testResourceProvider.getSemanticTagsByLanguage(Collections.singleton(language)).contains(semanticTag);
	}

	@Override
	public boolean isSemanticTagCompatibleWithinHierarchy(String term, Set <String> topLevelSemanticTags) {
		String tag = DescriptionHelper.getTag(term);
		Map <String, Set <String>> semanticTagMap = testResourceProvider.getSemanticHierarchyMap();
		if (tag != null) {
			for (String topLevelSemanticTag : topLevelSemanticTags) {
				Set<String> compatibleSemanticTags = semanticTagMap.get(topLevelSemanticTag);
				if (!CollectionUtils.isEmpty(compatibleSemanticTags) && compatibleSemanticTags.contains(tag)) {
					return true;
				}
			}
		}

		return false;
	}

	private String findStatedHierarchyRootId(org.ihtsdo.drools.domain.Concept concept) {
		String conceptId = concept.getId();
		if (!statedHierarchyRootIdCache.containsKey(conceptId)) {
			statedHierarchyRootIdCache.put(conceptId, doFindStatedHierarchyRootId(concept));
		}
		return statedHierarchyRootIdCache.get(conceptId);
	}

	private String doFindStatedHierarchyRootId(org.ihtsdo.drools.domain.Concept concept) {
		Set<String> statedIsARelationships = concept.getRelationships().stream().filter(r -> r.isActive()
				&& Concepts.STATED_RELATIONSHIP.equals(r.getCharacteristicTypeId())
				&& Concepts.ISA.equals(r.getTypeId())).map(org.ihtsdo.drools.domain.Relationship :: getDestinationId).collect(Collectors.toSet());

		if (statedIsARelationships.isEmpty()) {
			return null;
		}

		Sets.SetView<String> statedHierarchyRoot = Sets.intersection(inferredTopLevelHierarchies, statedIsARelationships);
		if (!statedHierarchyRoot.isEmpty()) {
			return statedHierarchyRoot.iterator().next();
		}

		// Search ancestors of stated is-a relationships
		String firstStatedParentId = statedIsARelationships.iterator().next();
		Set<String> statedAncestors = queryService.findAncestorIds(true, firstStatedParentId);
		statedHierarchyRoot = Sets.intersection(inferredTopLevelHierarchies, statedAncestors);
		if (!statedHierarchyRoot.isEmpty()) {
			return statedHierarchyRoot.iterator().next();
		}

		return null;
	}

}
