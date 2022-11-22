package org.snomed.snowstorm.core.data.services;

import ch.qos.logback.classic.Level;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import io.kaicode.elasticvc.api.BranchCriteria;
import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.api.ComponentService;
import io.kaicode.elasticvc.api.VersionControlHelper;
import io.kaicode.elasticvc.domain.Branch;
import io.kaicode.elasticvc.domain.Commit;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.elasticsearch.common.Strings;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.Operator;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedStringTerms;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.sort.SortBuilders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.config.SearchLanguagesConfiguration;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.data.services.identifier.IdentifierService;
import org.snomed.snowstorm.core.data.services.pojo.DescriptionCriteria;
import org.snomed.snowstorm.core.data.services.pojo.PageWithBucketAggregations;
import org.snomed.snowstorm.core.data.services.pojo.PageWithBucketAggregationsFactory;
import org.snomed.snowstorm.core.data.services.pojo.SimpleAggregation;
import org.snomed.snowstorm.core.util.DescriptionHelper;
import org.snomed.snowstorm.core.util.TimerUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.SearchHitsIterator;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.lang.Long.parseLong;
import static org.elasticsearch.index.query.QueryBuilders.*;
import static org.snomed.snowstorm.config.Config.*;

@Service
public class DescriptionService extends ComponentService {

	@Autowired
	private SearchLanguagesConfiguration searchLanguagesConfiguration;

	@Autowired
	private VersionControlHelper versionControlHelper;

	@Autowired
	private BranchService branchService;

	@Autowired
	private ElasticsearchOperations elasticsearchTemplate;

	@Autowired
	private ConceptUpdateHelper conceptUpdateHelper;

	private final Map<String, SemanticTagCacheEntry> semanticTagAggregationCache = new ConcurrentHashMap<>();

	@Value("${search.description.aggregation.maxProcessableResultsSize}")
	private int aggregationMaxProcessableResultsSize;

	public enum SearchMode {
		STANDARD, REGEX, WHOLE_WORD
	}

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public Description findDescription(String path, String descriptionId) {
		final BranchCriteria branchCriteria = versionControlHelper.getBranchCriteria(path);
		BoolQueryBuilder query = boolQuery().must(branchCriteria.getEntityBranchCriteria(Description.class))
				.must(termsQuery("descriptionId", descriptionId));
		List<Description> descriptions = elasticsearchTemplate.search(
				new NativeSearchQueryBuilder().withQuery(query).build(), Description.class)
				.get().map(SearchHit::getContent).collect(Collectors.toList());
		if (descriptions.size() > 1) {
			String message = String.format("More than one description found for id %s on branch %s.", descriptionId, path);
			logger.error("{} {}", message, descriptions);
			throw new IllegalStateException(message);
		}
		// Join refset members
		if (!descriptions.isEmpty()) {
			Description description = descriptions.get(0);
			Map<String, Description> descriptionIdMap = Collections.singletonMap(descriptionId, description);
			joinLangRefsetMembers(branchCriteria, Collections.singleton(description.getConceptId()), descriptionIdMap);
			joinInactivationIndicatorsAndAssociations(null, descriptionIdMap, branchCriteria, null);
			return description;
		}
		return null;
	}

	/**
	 * Delete a description by id.
	 * @param description The description to delete.
	 * @param branch The branch on which to make the change.
	 * @param force Delete the description even if it has been released.
	 */
	public void deleteDescription(Description description, String branch, boolean force) {
		if (description.isReleased() && !force) {
			throw new IllegalStateException("This description is released and can not be deleted.");
		}
		try (Commit commit = branchService.openCommit(branch)) {
			description.markDeleted();
			conceptUpdateHelper.doDeleteMembersWhereReferencedComponentDeleted(Collections.singleton(description.getDescriptionId()), commit);
			conceptUpdateHelper.doSaveBatchDescriptions(Collections.singleton(description), commit);
			commit.markSuccessful();
		}
	}

	public Page<Description> findDescriptions(String branch, String exactTerm, Set<String> descriptionIds, Set<String> conceptIds, PageRequest pageRequest) {
		BranchCriteria branchCriteria = versionControlHelper.getBranchCriteria(branch);
		BoolQueryBuilder query = boolQuery().must(branchCriteria.getEntityBranchCriteria(Description.class));
		if (!CollectionUtils.isEmpty(descriptionIds)) {
			query.must(termsQuery(Description.Fields.DESCRIPTION_ID, descriptionIds));
		}
		if (!CollectionUtils.isEmpty(conceptIds)) {
			query.must(termsQuery(Description.Fields.CONCEPT_ID, conceptIds));
		}
		if (exactTerm != null && !exactTerm.isEmpty()) {
			query.must(termQuery(Description.Fields.TERM, exactTerm));
		}
		NativeSearchQuery searchQuery = new NativeSearchQueryBuilder()
				.withQuery(query)
				.withPageable(pageRequest)
				.build();
		searchQuery.setTrackTotalHits(true);
		SearchHits<Description> descriptions = elasticsearchTemplate.search(searchQuery, Description.class);
		List<Description> content = descriptions.get().map(SearchHit::getContent).collect(Collectors.toList());
		joinLangRefsetMembers(branchCriteria,
				content.stream().map(Description::getConceptId).collect(Collectors.toSet()),
				content.stream().collect(Collectors.toMap(Description::getDescriptionId, Function.identity())));
		return new PageImpl<>(content, pageRequest, descriptions.getTotalHits());
	}

	public Set<Description> findDescriptionsByConceptId(String branchPath, Set<String> conceptIds, boolean fetchLangRefsetMembers) {
		BranchCriteria branchCriteria = versionControlHelper.getBranchCriteria(branchPath);
		Map<String, Concept> conceptMap = new HashMap<>();
		for (String conceptId : conceptIds) {
			conceptMap.put(conceptId, new Concept(conceptId));
		}
		joinDescriptions(branchCriteria, conceptMap, null, null, fetchLangRefsetMembers, false);
		return conceptMap.values().stream().flatMap(c -> c.getDescriptions().stream()).collect(Collectors.toSet());
	}

	PageWithBucketAggregations<Description> findDescriptionsWithAggregations(String path, String term, PageRequest pageRequest) throws TooCostlyException {
		return findDescriptionsWithAggregations(path, term, DEFAULT_LANGUAGE_CODES, pageRequest);
	}

	PageWithBucketAggregations<Description> findDescriptionsWithAggregations(String path, String term, List<String> languageCodes, PageRequest pageRequest) throws TooCostlyException {
		return findDescriptionsWithAggregations(path, new DescriptionCriteria().term(term).searchLanguageCodes(languageCodes), pageRequest);
	}

	public PageWithBucketAggregations<Description> findDescriptionsWithAggregations(String path, DescriptionCriteria criteria, PageRequest pageRequest) throws TooCostlyException {
		TimerUtil timer = new TimerUtil("Search", Level.INFO, 5, new TimerUtil("Search DEBUG", Level.DEBUG));

		final BranchCriteria branchCriteria = versionControlHelper.getBranchCriteria(path);
		timer.checkpoint("Build branch criteria");

		// Fetch all matching description and concept ids
		// ids of concepts where all descriptions and concept criteria are met
		DescriptionMatches descriptionMatches = findDescriptionAndConceptIds(criteria, Collections.emptySet(), branchCriteria, timer);
		BoolQueryBuilder descriptionQuery = descriptionMatches.getDescriptionQuery();

		// Apply concept and acceptability filtering for final search
		BoolQueryBuilder descriptionFilter = boolQuery();
		descriptionFilter.must(termsQuery(Description.Fields.DESCRIPTION_ID, descriptionMatches.getMatchedDescriptionIds()));

		// Start fetching aggregations..
		List<Aggregation> allAggregations = new ArrayList<>();
		Set<Long> conceptIds = descriptionMatches.getMatchedConceptIds();

		// Fetch FSN semantic tag aggregation
		BoolQueryBuilder fsnClauses = boolQuery();
		String semanticTag = criteria.getSemanticTag();
		Set<String> semanticTags = criteria.getSemanticTags();
		boolean semanticTagFiltering = !Strings.isNullOrEmpty(semanticTag) || !CollectionUtils.isEmpty(semanticTags);
		Set<String> allSemanticTags = new HashSet<>();
		if (semanticTagFiltering) {
			if (!Strings.isNullOrEmpty(semanticTag)) {
				allSemanticTags.add(semanticTag);
			}
			if (!CollectionUtils.isEmpty(semanticTags)) {
				allSemanticTags.addAll(semanticTags);
			}
			fsnClauses.must(termsQuery(Description.Fields.TAG, allSemanticTags));
		}
		NativeSearchQueryBuilder fsnQueryBuilder = new NativeSearchQueryBuilder()
				.withQuery(fsnClauses
						.must(branchCriteria.getEntityBranchCriteria(Description.class))
						.must(termsQuery(Description.Fields.ACTIVE, true))
						.must(termsQuery(Description.Fields.TYPE_ID, Concepts.FSN))
						.must(termsQuery(Description.Fields.CONCEPT_ID, conceptIds))
				)
				.addAggregation(AggregationBuilders.terms("semanticTags").field(Description.Fields.TAG).size(AGGREGATION_SEARCH_SIZE));
		if (!semanticTagFiltering) {
			fsnQueryBuilder.withPageable(PAGE_OF_ONE);
			SearchHits<Description> semanticTagResults = elasticsearchTemplate.search(fsnQueryBuilder.build(), Description.class);
			allAggregations.add(semanticTagResults.getAggregations().get("semanticTags"));
			timer.checkpoint("Semantic tag aggregation");
		} else {
			// Apply semantic tag filter
			fsnQueryBuilder
					.withPageable(LARGE_PAGE)
					.withFields(Description.Fields.CONCEPT_ID);

			Set<Long> conceptSemanticTagMatches = new LongOpenHashSet();
			if (allSemanticTags.size() == 1) {
				try (SearchHitsIterator<Description> descriptionStream = elasticsearchTemplate.searchForStream(fsnQueryBuilder.build(), Description.class)) {
					descriptionStream.forEachRemaining(hit -> conceptSemanticTagMatches.add(parseLong(hit.getContent().getConceptId())));
				}
				allAggregations.add(new SimpleAggregation("semanticTags", allSemanticTags.iterator().next(), conceptSemanticTagMatches.size()));
			} else {
				SearchHits<Description> semanticTagResults = elasticsearchTemplate.search(fsnQueryBuilder.build(), Description.class);
				semanticTagResults.stream().forEach((hit -> conceptSemanticTagMatches.add(parseLong(hit.getContent().getConceptId()))));
				allAggregations.add(semanticTagResults.getAggregations().get("semanticTags"));
			}

			conceptIds = conceptSemanticTagMatches;
		}

		// Fetch concept refset membership aggregation
		SearchHits<ReferenceSetMember> membershipResults = elasticsearchTemplate.search(new NativeSearchQueryBuilder()
				.withQuery(boolQuery()
						.must(branchCriteria.getEntityBranchCriteria(ReferenceSetMember.class))
						.must(termsQuery(ReferenceSetMember.Fields.ACTIVE, true))
						.filter(termsQuery(ReferenceSetMember.Fields.REFERENCED_COMPONENT_ID, conceptIds))
				)
				.withPageable(PAGE_OF_ONE)
				.addAggregation(AggregationBuilders.terms("membership").field(ReferenceSetMember.Fields.REFSET_ID))
				.build(), ReferenceSetMember.class);
		allAggregations.add(membershipResults.getAggregations().get("membership"));
		timer.checkpoint("Concept refset membership aggregation");

		// Perform final paged description search with description property aggregations
		descriptionFilter.must(termsQuery(Description.Fields.CONCEPT_ID, conceptIds));
		final NativeSearchQueryBuilder queryBuilder = new NativeSearchQueryBuilder()
				.withQuery(descriptionQuery.filter(descriptionFilter))
				.addAggregation(AggregationBuilders.terms("module").field(Description.Fields.MODULE_ID))
				.addAggregation(AggregationBuilders.terms("language").field(Description.Fields.LANGUAGE_CODE))
				.withPageable(pageRequest);
		NativeSearchQuery aggregateQuery = addTermSort(queryBuilder.build());
		aggregateQuery.setTrackTotalHits(true);
		SearchHits<Description> descriptions = elasticsearchTemplate.search(aggregateQuery, Description.class);
		allAggregations.addAll(descriptions.getAggregations().asList());
		timer.checkpoint("Fetch descriptions including module and language aggregations");
		timer.finish();

		// Merge aggregations
		return PageWithBucketAggregationsFactory.createPage(descriptions, new Aggregations(allAggregations), pageRequest);
	}

	void joinDescriptions(BranchCriteria branchCriteria, Map<String, Concept> conceptIdMap, Map<String, ConceptMini> conceptMiniMap,
			TimerUtil timer, boolean fetchLangRefsetMembers, boolean fetchInactivationInfo) {

		final NativeSearchQueryBuilder queryBuilder = new NativeSearchQueryBuilder();

		final Set<String> allConceptIds = new HashSet<>();
		if (conceptIdMap != null) {
			allConceptIds.addAll(conceptIdMap.keySet());
		}
		if (conceptMiniMap != null) {
			allConceptIds.addAll(conceptMiniMap.keySet());
		}
		if (allConceptIds.isEmpty()) {
			return;
		}

		// Fetch Descriptions
		Map<String, Description> descriptionIdMap = new HashMap<>();
		for (List<String> conceptIds : Iterables.partition(allConceptIds, CLAUSE_LIMIT)) {
			queryBuilder.withQuery(boolQuery()
					.must(branchCriteria.getEntityBranchCriteria(Description.class))
					.must(termsQuery("conceptId", conceptIds)))
					.withPageable(LARGE_PAGE);
			try (final SearchHitsIterator<Description> descriptions = elasticsearchTemplate.searchForStream(queryBuilder.build(), Description.class)) {
				descriptions.forEachRemaining(hit -> {
					Description description = hit.getContent();
					// Join Descriptions to concepts for loading whole concepts use case.
					final String descriptionConceptId = description.getConceptId();
					if (conceptIdMap != null) {
						final Concept concept = conceptIdMap.get(descriptionConceptId);
						if (concept != null) {
							concept.addDescription(description);
						}
					}
					// Join Description to ConceptMinis for search result use case.
					if (conceptMiniMap != null) {
						final ConceptMini conceptMini = conceptMiniMap.get(descriptionConceptId);
						if (conceptMini != null && description.isActive()) {
							conceptMini.addActiveDescription(description);
						}
					}

					// Store Descriptions in a map for adding Lang Refset and inactivation members.
					descriptionIdMap.putIfAbsent(description.getDescriptionId(), description);
				});
			}
		}
		if (timer != null) timer.checkpoint("get descriptions " + getFetchCount(allConceptIds.size()));

		// Fetch Lang Refset Members
		if (fetchLangRefsetMembers) {
			joinLangRefsetMembers(branchCriteria, allConceptIds, descriptionIdMap);
			if (timer != null) timer.checkpoint("get lang refset " + getFetchCount(allConceptIds.size()));
		}

		// Fetch Inactivation Indicators and Associations
		if (fetchInactivationInfo) {
			joinInactivationIndicatorsAndAssociations(conceptIdMap, descriptionIdMap, branchCriteria, timer);
		}
	}

	public Map<String, Long> countActiveConceptsPerSemanticTag(String branch) {

		Branch branchObject = branchService.findLatest(branch);

		SemanticTagCacheEntry semanticTagCacheEntry = semanticTagAggregationCache.get(branch);
		if (semanticTagCacheEntry != null) {
			if (semanticTagCacheEntry.getBranchHeadTime() == branchObject.getHead().getTime()) {
				return semanticTagCacheEntry.getTagCounts();
			}
		}

		BranchCriteria branchCriteria = versionControlHelper.getBranchCriteria(branch);

		List<Long> activeConcepts = new LongArrayList();

		try (SearchHitsIterator<Concept> stream = elasticsearchTemplate.searchForStream(new NativeSearchQueryBuilder()
				.withQuery(boolQuery()
						.must(branchCriteria.getEntityBranchCriteria(Concept.class))
						.must(termQuery(Concept.Fields.ACTIVE, true)))
				.withFields(Concept.Fields.CONCEPT_ID)
				.withPageable(LARGE_PAGE).build(), Concept.class)) {
			stream.forEachRemaining(hit -> activeConcepts.add(hit.getContent().getConceptIdAsLong()));
		}

		SearchHits<Description> page = elasticsearchTemplate.search(new NativeSearchQueryBuilder()
				.withQuery(boolQuery()
						.must(branchCriteria.getEntityBranchCriteria(Description.class))
						.must(termQuery(Description.Fields.ACTIVE, true))
						.must(termQuery(Description.Fields.TYPE_ID, Concepts.FSN))
						.filter(termsQuery(Description.Fields.CONCEPT_ID, activeConcepts))
				)
				.addAggregation(AggregationBuilders.terms("semanticTags").field(Description.Fields.TAG).size(AGGREGATION_SEARCH_SIZE))
				.build(), Description.class);

		Map<String, Long> tagCounts = new TreeMap<>();
		if (page.hasAggregations()) {
			ParsedStringTerms semanticTags = page.getAggregations().get("semanticTags");
			List<? extends Terms.Bucket> buckets = semanticTags.getBuckets();
			for (Terms.Bucket bucket : buckets) {
				tagCounts.put(bucket.getKeyAsString(), bucket.getDocCount());
			}
		}
		// Cache result
		semanticTagAggregationCache.put(branch, new SemanticTagCacheEntry(branchObject.getHead().getTime(), tagCounts));

		return tagCounts;
	}

	private void joinInactivationIndicatorsAndAssociations(Map<String, Concept> conceptIdMap, Map<String, Description> descriptionIdMap,
			BranchCriteria branchCriteria, TimerUtil timer) {

		Set<String> componentIds;
		if (conceptIdMap != null) {
			componentIds = Sets.union(conceptIdMap.keySet(), descriptionIdMap.keySet());
		} else {
			componentIds = descriptionIdMap.keySet();
		}
		final NativeSearchQueryBuilder queryBuilder = new NativeSearchQueryBuilder();
		for (List<String> componentIdsSegment : Iterables.partition(componentIds, CLAUSE_LIMIT)) {
			queryBuilder.withQuery(boolQuery()
					.must(branchCriteria.getEntityBranchCriteria(ReferenceSetMember.class))
					.must(termsQuery("refsetId", Concepts.inactivationAndAssociationRefsets))
					.must(termsQuery("referencedComponentId", componentIdsSegment)))
					.withPageable(LARGE_PAGE);
			// Join Members
			try (final SearchHitsIterator<ReferenceSetMember> members = elasticsearchTemplate.searchForStream(queryBuilder.build(), ReferenceSetMember.class)) {
				members.forEachRemaining(hit -> {
					ReferenceSetMember member = hit.getContent();
					String referencedComponentId = member.getReferencedComponentId();
					switch (member.getRefsetId()) {
						case Concepts.CONCEPT_INACTIVATION_INDICATOR_REFERENCE_SET:
							conceptIdMap.get(referencedComponentId).addInactivationIndicatorMember(member);
							break;
						case Concepts.DESCRIPTION_INACTIVATION_INDICATOR_REFERENCE_SET:
							descriptionIdMap.get(referencedComponentId).addInactivationIndicatorMember(member);
							break;
						default:
							if (IdentifierService.isConceptId(referencedComponentId)) {
								Concept concept = conceptIdMap.get(referencedComponentId);
								if (concept != null) {
									concept.addAssociationTargetMember(member);
								} else {
									logger.warn("Association ReferenceSetMember {} references concept {} " +
											"which is not in scope.", member.getId(), referencedComponentId);
								}
							} else if (IdentifierService.isDescriptionId(referencedComponentId)) {
								Description description = descriptionIdMap.get(referencedComponentId);
								if (description != null) {
									description.addAssociationTargetMember(member);
								} else {
									logger.warn("Association ReferenceSetMember {} references description {} " +
											"which is not in scope.", member.getId(), referencedComponentId);
								}
							} else {
								logger.error("Association ReferenceSetMember {} references unexpected component type {}", member.getId(), referencedComponentId);
							}
							break;
					}
				});
			}
		}
		if (timer != null) timer.checkpoint("get inactivation refset " + getFetchCount(componentIds.size()));
	}

	private void joinLangRefsetMembers(BranchCriteria branchCriteria, Set<String> allConceptIds, Map<String, Description> descriptionIdMap) {
		NativeSearchQueryBuilder queryBuilder = new NativeSearchQueryBuilder();
		for (List<String> conceptIds : Iterables.partition(allConceptIds, CLAUSE_LIMIT)) {

			queryBuilder.withQuery(boolQuery()
					.must(branchCriteria.getEntityBranchCriteria(ReferenceSetMember.class))
					.must(termsQuery(ReferenceSetMember.LanguageFields.ACCEPTABILITY_ID_FIELD_PATH, Concepts.PREFERRED, Concepts.ACCEPTABLE))
					.must(termsQuery("conceptId", conceptIds)))
					.withPageable(LARGE_PAGE);
			// Join Lang Refset Members
			try (final SearchHitsIterator<ReferenceSetMember> langRefsetMembers = elasticsearchTemplate.searchForStream(queryBuilder.build(), ReferenceSetMember.class)) {
				langRefsetMembers.forEachRemaining(hit -> {
					ReferenceSetMember langRefsetMember = hit.getContent();
					Description description = descriptionIdMap.get(langRefsetMember.getReferencedComponentId());
					if (description != null) {
						description.addLanguageRefsetMember(langRefsetMember);
					}
				});
			}
		}
	}

	public void joinActiveDescriptions(String path, Map<String, ConceptMini> conceptMiniMap) {
		BranchCriteria branchCriteria = versionControlHelper.getBranchCriteria(path);
		NativeSearchQuery searchQuery = new NativeSearchQueryBuilder()
				.withQuery(boolQuery().must(branchCriteria.getEntityBranchCriteria(Description.class))
						.must(termQuery(SnomedComponent.Fields.ACTIVE, true))
						.must(termsQuery(Description.Fields.CONCEPT_ID, conceptMiniMap.keySet())))
				.withPageable(LARGE_PAGE)
				.build();
		Map<String, Description> descriptionIdMap = new HashMap<>();
		try (SearchHitsIterator<Description> stream = elasticsearchTemplate.searchForStream(searchQuery, Description.class)) {
			stream.forEachRemaining(hit -> {
				Description description = hit.getContent();
				conceptMiniMap.get(description.getConceptId()).addActiveDescription(description);
				descriptionIdMap.put(description.getId(), description);
			});
		}
		joinLangRefsetMembers(branchCriteria, conceptMiniMap.keySet(), descriptionIdMap);
	}

	DescriptionMatches findDescriptionAndConceptIds(DescriptionCriteria criteria, Set<Long> conceptIdsCriteria, BranchCriteria branchCriteria, TimerUtil timer) throws TooCostlyException {

		// Build up the description criteria
		final BoolQueryBuilder descriptionQuery = boolQuery();
		BoolQueryBuilder descriptionBranchCriteria = branchCriteria.getEntityBranchCriteria(Description.class);
		descriptionQuery.must(descriptionBranchCriteria);
		addTermClauses(criteria.getTerm(), criteria.getSearchLanguageCodes(), criteria.getType(), descriptionQuery, criteria.getSearchMode());

		Boolean active = criteria.getActive();
		if (active != null) {
			descriptionQuery.must(termQuery(Description.Fields.ACTIVE, active));
		}

		Collection<String> modules = criteria.getModules();
		if (!CollectionUtils.isEmpty(modules)) {
			descriptionQuery.must(termsQuery(Description.Fields.MODULE_ID, modules));
		}

		if (!CollectionUtils.isEmpty(criteria.getConceptIds())) {
			descriptionQuery.must(termsQuery(Description.Fields.CONCEPT_ID, criteria.getConceptIds()));
		}

		if (!CollectionUtils.isEmpty(conceptIdsCriteria)) {
			descriptionQuery.must(termsQuery(Description.Fields.CONCEPT_ID, conceptIdsCriteria));
		}

		// First pass search to collect all description and concept ids.
		final Map<Long, Long> descriptionToConceptMap = new Long2ObjectLinkedOpenHashMap<>();
		NativeSearchQueryBuilder searchQueryBuilder = new NativeSearchQueryBuilder()
				.withQuery(descriptionQuery)
				.withFields(Description.Fields.DESCRIPTION_ID, Description.Fields.CONCEPT_ID);

		NativeSearchQuery query = searchQueryBuilder.withPageable(PAGE_OF_ONE).build();
		query.setTrackTotalHits(true);
		long totalElements = elasticsearchTemplate.search(query, Description.class).getTotalHits();
		if (totalElements > aggregationMaxProcessableResultsSize) {
			throw new TooCostlyException(String.format("There are over %s results. Aggregating these results would be too costly.", aggregationMaxProcessableResultsSize));
		}
		timer.checkpoint("Count all check");

		NativeSearchQuery searchQuery = searchQueryBuilder.withPageable(LARGE_PAGE).build();
		addTermSort(searchQuery);
		try (SearchHitsIterator<Description> stream = elasticsearchTemplate.searchForStream(
				searchQuery, Description.class)) {
			stream.forEachRemaining(hit -> {
				Description description = hit.getContent();
				descriptionToConceptMap.put(parseLong(description.getDescriptionId()), parseLong(description.getConceptId()));
			});
		}
		timer.checkpoint("Collect all description and concept ids");

		// Second pass to apply lang refset filter
		Set<Long> preferredIn = criteria.getPreferredIn();
		Set<Long> acceptableIn = criteria.getAcceptableIn();
		Set<Long> preferredOrAcceptableIn = criteria.getPreferredOrAcceptableIn();
		Set<Long> conceptIds;
		if (!CollectionUtils.isEmpty(preferredIn) || !CollectionUtils.isEmpty(acceptableIn) || !CollectionUtils.isEmpty(preferredOrAcceptableIn)) {

			BoolQueryBuilder queryBuilder = boolQuery()
					.must(branchCriteria.getEntityBranchCriteria(ReferenceSetMember.class))
					.must(termQuery(ReferenceSetMember.Fields.ACTIVE, true));

			if (!CollectionUtils.isEmpty(preferredIn)) {
				queryBuilder
						.must(termsQuery(ReferenceSetMember.Fields.REFSET_ID, preferredIn))
						.must(termQuery(ReferenceSetMember.LanguageFields.ACCEPTABILITY_ID_FIELD_PATH, Concepts.PREFERRED));
			}
			if (!CollectionUtils.isEmpty(acceptableIn)) {
				queryBuilder
						.must(termsQuery(ReferenceSetMember.Fields.REFSET_ID, acceptableIn))
						.must(termQuery(ReferenceSetMember.LanguageFields.ACCEPTABILITY_ID_FIELD_PATH, Concepts.ACCEPTABLE));
			}
			if (!CollectionUtils.isEmpty(preferredOrAcceptableIn)) {
				queryBuilder
						.must(termsQuery(ReferenceSetMember.Fields.REFSET_ID, preferredOrAcceptableIn))
						.must(termsQuery(ReferenceSetMember.LanguageFields.ACCEPTABILITY_ID_FIELD_PATH, Sets.newHashSet(Concepts.PREFERRED, Concepts.ACCEPTABLE)));
			}

			NativeSearchQuery nativeSearchQuery = new NativeSearchQueryBuilder()
					.withQuery(queryBuilder)
					.withFilter(termsQuery(ReferenceSetMember.Fields.REFERENCED_COMPONENT_ID, descriptionToConceptMap.keySet()))
					.withFields(ReferenceSetMember.Fields.REFERENCED_COMPONENT_ID)
					.withPageable(LARGE_PAGE)
					.build();
			Set<Long> filteredDescriptionIds = new LongOpenHashSet();
			try (SearchHitsIterator<ReferenceSetMember> stream = elasticsearchTemplate.searchForStream(nativeSearchQuery, ReferenceSetMember.class)) {
				stream.forEachRemaining(hit -> filteredDescriptionIds.add(parseLong(hit.getContent().getReferencedComponentId())));
			}

			// Create new map of descriptions and concepts, keeping the original description order.
			Map<Long, Long> filteredDescriptionToConceptMap = new Long2ObjectLinkedOpenHashMap<>();
			for (Long descriptionId : descriptionToConceptMap.keySet()) {
				if (filteredDescriptionIds.contains(descriptionId)) {
					filteredDescriptionToConceptMap.put(descriptionId, descriptionToConceptMap.get(descriptionId));
				}
			}
			descriptionToConceptMap.clear();
			descriptionToConceptMap.putAll(filteredDescriptionToConceptMap);
			timer.checkpoint("Language refset filtering");
		}

		// Get unique set of concept ids keeping the order that the descriptions were found.
		conceptIds = new LongLinkedOpenHashSet(descriptionToConceptMap.values());
		if (!conceptIds.isEmpty()) {

			// Apply concept active filter
			if (criteria.getConceptActive() != null) {
				List<Long> filteredConceptIds = new LongArrayList();
				try (SearchHitsIterator<Concept> stream = elasticsearchTemplate.searchForStream(
						new NativeSearchQueryBuilder()
								.withQuery(boolQuery()
										.must(termQuery(Concept.Fields.ACTIVE, criteria.getConceptActive()))
										.filter(branchCriteria.getEntityBranchCriteria(Concept.class))
										.filter(termsQuery(Concept.Fields.CONCEPT_ID, conceptIds))
								)
								.withSort(SortBuilders.fieldSort("_doc"))
								.withFields(Concept.Fields.CONCEPT_ID)
								.withPageable(LARGE_PAGE)
								.build(), Concept.class)) {
					stream.forEachRemaining(hit -> filteredConceptIds.add(hit.getContent().getConceptIdAsLong()));
				}
				conceptIds = filterOrderedSet(conceptIds, filteredConceptIds);
				timer.checkpoint("Concept active filtering");
			}

			// Apply refset filter
			if (!Strings.isNullOrEmpty(criteria.getConceptRefset())) {
				List<Long> filteredConceptIds = new LongArrayList();
				try (SearchHitsIterator<ReferenceSetMember> stream = elasticsearchTemplate.searchForStream(
						new NativeSearchQueryBuilder()
								.withQuery(boolQuery()
										.must(termQuery(ReferenceSetMember.Fields.REFSET_ID, criteria.getConceptRefset()))
										.filter(branchCriteria.getEntityBranchCriteria(ReferenceSetMember.class))
										.filter(termsQuery(ReferenceSetMember.Fields.REFERENCED_COMPONENT_ID, conceptIds))
								)
								.withSort(SortBuilders.fieldSort("_doc"))
								.withFields(ReferenceSetMember.Fields.REFERENCED_COMPONENT_ID)
								.withPageable(LARGE_PAGE)
								.build(), ReferenceSetMember.class)) {
					stream.forEachRemaining(hit -> filteredConceptIds.add(parseLong(hit.getContent().getReferencedComponentId())));
				}
				conceptIds = filterOrderedSet(conceptIds, filteredConceptIds);
				timer.checkpoint("Concept refset filtering");
			}
		}

		Set<Long> descriptions;
		if (criteria.isGroupByConcept()) {
			descriptions = new LongLinkedOpenHashSet();
			Set<Long> uniqueConceptIds = new LongOpenHashSet();
			for (Map.Entry<Long, Long> entry : descriptionToConceptMap.entrySet()) {
				if (uniqueConceptIds.add(entry.getValue())) {
					descriptions.add(entry.getKey());
				}
			}
		} else {
			descriptions = descriptionToConceptMap.keySet();
		}

		return new DescriptionMatches(descriptions, conceptIds, descriptionQuery);
	}

	private Set<Long> filterOrderedSet(Set<Long> orderedIds, List<Long> idsToKeep) {
		Set<Long> newSet = new LongLinkedOpenHashSet();
		for (Long orderedId : orderedIds) {
			if (idsToKeep.contains(orderedId)) {
				newSet.add(orderedId);
			}
		}
		return newSet;
	}

	void addTermClauses(String term, Collection<String> languageCodes, Collection<Long> descriptionTypes, BoolQueryBuilder boolBuilder, SearchMode searchMode) {
		if (IdentifierService.isConceptId(term)) {
			boolBuilder.must(termQuery(Description.Fields.CONCEPT_ID, term));
		} else {
			if (!Strings.isNullOrEmpty(term)) {
				BoolQueryBuilder termFilter = new BoolQueryBuilder();
				boolBuilder.filter(termFilter);
				boolean allLanguages = CollectionUtils.isEmpty(languageCodes);
				if (searchMode == SearchMode.REGEX) {
					// https://www.elastic.co/guide/en/elasticsearch/reference/master/query-dsl-query-string-query.html#_regular_expressions
					if (term.startsWith("^")) {
						term = term.substring(1);
					}
					if (term.endsWith("$")) {
						term = term.substring(0, term.length()-1);
					}
					termFilter.must(regexpQuery(Description.Fields.TERM, term));
					// Must match the requested languages
					if (!allLanguages) {
						boolBuilder.must(termsQuery(Description.Fields.LANGUAGE_CODE, languageCodes));
					}
				} else {
					// Must match at least one of the following 'should' clauses:
					BoolQueryBuilder shouldClauses = boolQuery();
					// All prefixes given. Simple Query String Query: https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-simple-query-string-query.html
					// e.g. 'Clin Fin' converts to 'clin* fin*' and matches 'Clinical Finding'
					// Put search term through character folding for each requested language
					Map<String, Set<Character>> charactersNotFoldedSets = searchLanguagesConfiguration.getCharactersNotFoldedSets();
					if (allLanguages) {
						Set<String> allLanguageCodes = new HashSet<>(charactersNotFoldedSets.keySet());

						// Any language - fully folded
						allLanguageCodes.add("");

						languageCodes = allLanguageCodes;
					}
					for (String languageCode : languageCodes) {
						Set<Character> charactersNotFoldedForLanguage = charactersNotFoldedSets.getOrDefault(languageCode, Collections.emptySet());
						String foldedSearchTerm = DescriptionHelper.foldTerm(term, charactersNotFoldedForLanguage);
						foldedSearchTerm = constructSearchTerm(analyze(foldedSearchTerm, new StandardAnalyzer(CharArraySet.EMPTY_SET)));
						if (foldedSearchTerm.isEmpty()) {
							continue;
						}
						BoolQueryBuilder languageQuery = boolQuery();
						if (SearchMode.WHOLE_WORD == searchMode) {
							languageQuery.filter(matchQuery(Description.Fields.TERM_FOLDED, foldedSearchTerm).operator(Operator.AND));
						} else {
							languageQuery.filter(simpleQueryStringQuery(constructSimpleQueryString(foldedSearchTerm))
											.field(Description.Fields.TERM_FOLDED).defaultOperator(Operator.AND));
						}
						if (!languageCode.isEmpty()) {
							languageQuery.must(termQuery(Description.Fields.LANGUAGE_CODE, languageCode));
						}
						shouldClauses.should(languageQuery);
					}

					if (containingNonAlphanumeric(term)) {
						String regexString = constructRegexQuery(term);
						termFilter.must(regexpQuery(Description.Fields.TERM, regexString));
					}
					termFilter.must(shouldClauses);
				}
			}
		}
		if (descriptionTypes != null && !descriptionTypes.isEmpty()) {
			boolBuilder.must(termsQuery(Description.Fields.TYPE_ID, descriptionTypes));
		}
	}

	private List<String> analyze(String text, StandardAnalyzer analyzer) {
		List<String> result = new ArrayList<>();
		try {
			TokenStream tokenStream = analyzer.tokenStream("contents", text);
			CharTermAttribute attr = tokenStream.addAttribute(CharTermAttribute.class);
			tokenStream.reset();
			while (tokenStream.incrementToken()) {
				result.add(attr.toString());
			}
		} catch (IOException e) {
			logger.error("Failed to analyze text {}", text, e);
		}
		return result;
	}

	private String constructSimpleQueryString(String searchTerm) {
		return (searchTerm.trim().replace(" ", "* ") + "*").replace("**", "*");
	}

	private boolean containingNonAlphanumeric(String term) {
		String[] words = term.split(" ", -1);
		for (String word : words) {
			if (!StringUtils.isAlphanumeric(word)) {
				return true;
			}
		}
		return false;
	}

	private String constructRegexQuery(String term) {
		String[] words = term.split(" ", -1);
		StringBuilder regexBuilder = new StringBuilder();
		regexBuilder.append(".*");
		for (String word : words) {
			if (StringUtils.isAlphanumeric(word)) {
				if (!regexBuilder.toString().endsWith(".*")) {
					regexBuilder.append(".*");
				}
				continue;
			}
			for (char c : word.toCharArray()) {
				if (Character.isLetter(c)) {
					regexBuilder.append("[").append(Character.toLowerCase(c)).append(Character.toUpperCase(c)).append("]");
				} else if (Character.isDigit(c)){
					regexBuilder.append(c);
				} else {
					regexBuilder.append("\\").append(c);
				}
			}
			regexBuilder.append(".*");
		}
		if (!regexBuilder.toString().endsWith(".*")) {
			regexBuilder.append(".*");
		}
		return regexBuilder.toString();
	}

	private String constructSearchTerm(List<String> tokens) {
		StringBuilder builder = new StringBuilder();
		for (String token : tokens) {
			builder.append(token);
			builder.append(" ");
		}
		return builder.toString().trim();
	}

	static NativeSearchQuery addTermSort(NativeSearchQuery query) {
		query.addSort(Sort.by(Description.Fields.TERM_LEN));
		query.addSort(Sort.by("_score"));
		return query;
	}

	private static class SemanticTagCacheEntry {

		private final long branchHeadTime;
		private final Map<String, Long> tagCounts;

		SemanticTagCacheEntry(long branchHeadTime, Map<String, Long> tagCounts) {
			this.branchHeadTime = branchHeadTime;
			this.tagCounts = tagCounts;
		}

		private long getBranchHeadTime() {
			return branchHeadTime;
		}

		private Map<String, Long> getTagCounts() {
			return tagCounts;
		}
	}

	static class DescriptionMatches {

		private final Set<Long> conceptIds;
		private final Set<Long> descriptionIds;
		private final BoolQueryBuilder descriptionQuery;

		private DescriptionMatches(Set<Long> descriptionIds, Set<Long> conceptIds, BoolQueryBuilder descriptionQuery) {
			this.descriptionIds = descriptionIds;
			this.conceptIds = conceptIds;
			this.descriptionQuery = descriptionQuery;
		}

		Set<Long> getMatchedDescriptionIds() {
			return descriptionIds;
		}

		Set<Long> getMatchedConceptIds() {
			return conceptIds;
		}

		public BoolQueryBuilder getDescriptionQuery() {
			return descriptionQuery;
		}
	}
}
