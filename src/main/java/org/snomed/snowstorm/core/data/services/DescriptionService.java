package org.snomed.snowstorm.core.data.services;

import ch.qos.logback.classic.Level;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.google.common.base.Strings;
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

import co.elastic.clients.elasticsearch._types.aggregations.AggregationBuilders;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.langauges.ecl.domain.ConceptReference;
import org.snomed.langauges.ecl.domain.expressionconstraint.SubExpressionConstraint;
import org.snomed.langauges.ecl.domain.filter.*;
import org.snomed.snowstorm.config.SearchLanguagesConfiguration;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.data.services.identifier.IdentifierService;
import org.snomed.snowstorm.core.data.services.pojo.DescriptionCriteria;
import org.snomed.snowstorm.core.data.services.pojo.PageWithBucketAggregations;
import org.snomed.snowstorm.core.data.services.pojo.PageWithBucketAggregationsFactory;
import org.snomed.snowstorm.core.data.services.pojo.SimpleAggregation;
import org.snomed.snowstorm.core.util.DescriptionHelper;
import org.snomed.snowstorm.core.util.TimerUtil;
import org.snomed.snowstorm.ecl.ECLQueryService;
import org.snomed.snowstorm.ecl.domain.expressionconstraint.SExpressionConstraint;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.client.elc.*;
import org.springframework.data.elasticsearch.core.*;
import org.springframework.data.elasticsearch.core.query.FetchSourceFilter;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.lang.Long.parseLong;
import static co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders.*;
import static io.kaicode.elasticvc.helper.QueryHelper.*;
import static org.snomed.snowstorm.config.Config.*;
import static org.snomed.snowstorm.core.data.domain.ReferenceSetMember.Fields.REFSET_ID;
import static org.snomed.snowstorm.core.data.domain.ReferenceSetMember.LanguageFields.ACCEPTABILITY_ID_FIELD_PATH;
import static org.snomed.snowstorm.core.util.AggregationUtils.getAggregations;


@Service
public class DescriptionService extends ComponentService {

	// Query value used to prevent matching
	private static final String NO_MATCH = "no-match";

	@Autowired
	private SearchLanguagesConfiguration searchLanguagesConfiguration;

	@Autowired
	private VersionControlHelper versionControlHelper;

	@Autowired
	private BranchService branchService;

	@Autowired
	private ElasticsearchOperations elasticsearchOperations;

	@Autowired
	private ConceptUpdateHelper conceptUpdateHelper;

	@Autowired
	private DialectConfigurationService dialectConfigurationService;

	@Value("${search.refset.aggregation.size}")
	private int refsetAggregationSearchSize;


	@Value("${search.description.semantic.tag.aggregation.size}")
	private int semanticTagAggregationSearchSize;

	private final Map<String, SemanticTagCacheEntry> semanticTagAggregationCache = new ConcurrentHashMap<>();

	@Value("${search.description.aggregation.maxProcessableResultsSize}")
	private int aggregationMaxProcessableResultsSize;

	public enum SearchMode {
		STANDARD, REGEX, WHOLE_WORD, WILDCARD
	}

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public Description findDescription(String path, String descriptionId) {
		final BranchCriteria branchCriteria = versionControlHelper.getBranchCriteria(path);
		Query query = bool(bq -> bq
				.must(branchCriteria.getEntityBranchCriteria(Description.class))
				.must(termQuery(Description.Fields.DESCRIPTION_ID, descriptionId)));
		List<Description> descriptions = elasticsearchOperations.search(
						new NativeQueryBuilder().withQuery(query).build(), Description.class)
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
		BoolQuery.Builder queryBuilder = bool().must(branchCriteria.getEntityBranchCriteria(Description.class));
		if (!CollectionUtils.isEmpty(descriptionIds)) {
			queryBuilder.must(termsQuery(Description.Fields.DESCRIPTION_ID, descriptionIds));
		}
		if (!CollectionUtils.isEmpty(conceptIds)) {
			queryBuilder.must(termsQuery(Description.Fields.CONCEPT_ID, conceptIds));
		}
		if (exactTerm != null && !exactTerm.isEmpty()) {
			queryBuilder.must(termQuery(Description.Fields.TERM, exactTerm));
		}
		NativeQuery searchQuery = new NativeQueryBuilder()
				.withQuery(queryBuilder.build()._toQuery())
				.withPageable(pageRequest)
				.build();
		searchQuery.setTrackTotalHits(true);
		SearchHits<Description> descriptions = elasticsearchOperations.search(searchQuery, Description.class);
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
		Query descriptionQuery = descriptionMatches.getDescriptionQuery();

		// Apply concept and acceptability filtering for final search
		BoolQuery.Builder descriptionFilter = bool();
		descriptionFilter.must(termsQuery(Description.Fields.DESCRIPTION_ID, descriptionMatches.getMatchedDescriptionIds()));

		// Start fetching aggregations..
		List<Aggregation> allAggregations = new ArrayList<>();
		Set<Long> conceptIds = descriptionMatches.getMatchedConceptIds();

		// Fetch FSN semantic tag aggregation
		BoolQuery.Builder fsnClauses = bool();
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
		int searchSize = allSemanticTags.isEmpty() ? semanticTagAggregationSearchSize : allSemanticTags.size();
		NativeQueryBuilder fsnQueryBuilder = new NativeQueryBuilder()
				.withQuery(fsnClauses
						.must(branchCriteria.getEntityBranchCriteria(Description.class))
						.must(termQuery(Description.Fields.ACTIVE, true))
						.must(termQuery(Description.Fields.TYPE_ID, Concepts.FSN))
						.must(termsQuery(Description.Fields.CONCEPT_ID, conceptIds)).build()._toQuery()
				)
				.withAggregation("semanticTags", AggregationBuilders.terms().field(Description.Fields.TAG).size(searchSize).build()._toAggregation());
		if (!semanticTagFiltering) {
			fsnQueryBuilder.withPageable(PAGE_OF_ONE);
			SearchHits<Description> semanticTagResults = elasticsearchOperations.search(fsnQueryBuilder.build(), Description.class);
			if (semanticTagResults.getAggregations() != null) {
				allAggregations.addAll(getAggregations(semanticTagResults.getAggregations(), "semanticTags"));
			}
			timer.checkpoint("Semantic tag aggregation");
		} else {
			// Apply semantic tag filter
			fsnQueryBuilder
					.withPageable(LARGE_PAGE)
					.withSourceFilter(new FetchSourceFilter(new String[]{Description.Fields.CONCEPT_ID}, null));

			Set<Long> conceptSemanticTagMatches = new LongOpenHashSet();
			if (allSemanticTags.size() == 1) {
				try (SearchHitsIterator<Description> descriptionStream = elasticsearchOperations.searchForStream(fsnQueryBuilder.build(), Description.class)) {
					descriptionStream.forEachRemaining(hit -> conceptSemanticTagMatches.add(parseLong(hit.getContent().getConceptId())));
				}
				allAggregations.add(new SimpleAggregation("semanticTags", allSemanticTags.iterator().next(), conceptSemanticTagMatches.size()));
			} else {
				SearchHits<Description> semanticTagResults = elasticsearchOperations.search(fsnQueryBuilder.build(), Description.class);
				semanticTagResults.stream().forEach((hit -> conceptSemanticTagMatches.add(parseLong(hit.getContent().getConceptId()))));
				if (semanticTagResults.hasAggregations()) {
					allAggregations.addAll(getAggregations(semanticTagResults.getAggregations(), "semanticTags"));
				}
			}

			conceptIds = conceptSemanticTagMatches;
		}

		// Fetch concept refset membership aggregation
		SearchHits<ReferenceSetMember> membershipResults = elasticsearchOperations.search(new NativeQueryBuilder()
				.withQuery(bool()
						.must(branchCriteria.getEntityBranchCriteria(ReferenceSetMember.class))
						.must(termQuery(ReferenceSetMember.Fields.ACTIVE, true))
						.filter(termsQuery(ReferenceSetMember.Fields.REFERENCED_COMPONENT_ID, conceptIds)).build()._toQuery()
				)
				.withPageable(PAGE_OF_ONE)
				.withAggregation("membership", AggregationBuilders.terms().field(REFSET_ID).size(refsetAggregationSearchSize).build()._toAggregation())
				.build(), ReferenceSetMember.class);
		if (membershipResults.hasAggregations()) {
			allAggregations.addAll(getAggregations(membershipResults.getAggregations(), "membership"));
		}

		timer.checkpoint("Concept refset membership aggregation");

		// Perform final paged description search with description property aggregations
		descriptionFilter.must(termsQuery(Description.Fields.CONCEPT_ID, conceptIds));
		final NativeQueryBuilder queryBuilder = new NativeQueryBuilder()
				.withQuery(bool(b -> b.must(descriptionQuery).filter(descriptionFilter.build()._toQuery())))
				.withAggregation("module", AggregationBuilders.terms(ta -> ta.field(Description.Fields.MODULE_ID).size(50)))
				.withAggregation("language", AggregationBuilders.terms(ta -> ta.field(Description.Fields.LANGUAGE_CODE).size(20)))
				.withPageable(pageRequest);
		NativeQuery aggregateQuery = addTermSort(queryBuilder.build());
		aggregateQuery.setTrackTotalHits(true);
		SearchHits<Description> descriptionSearchResults = elasticsearchOperations.search(aggregateQuery, Description.class);
		if (descriptionSearchResults.hasAggregations()) {
			allAggregations.addAll(getAggregations(descriptionSearchResults.getAggregations()).values());
		}
		timer.checkpoint("Fetch descriptions including module and language aggregations");
		timer.finish();

		// Merge aggregations
		return PageWithBucketAggregationsFactory.createPage(descriptionSearchResults, allAggregations, pageRequest);
	}


	void joinDescriptions(BranchCriteria branchCriteria, Map<String, Concept> conceptIdMap, Map<String, ConceptMini> conceptMiniMap,
			TimerUtil timer, boolean fetchLangRefsetMembers, boolean fetchInactivationInfo) {

		final NativeQueryBuilder queryBuilder = new NativeQueryBuilder();

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
			queryBuilder.withQuery(bool(bq -> bq
							.must(branchCriteria.getEntityBranchCriteria(Description.class))
							.must(termsQuery("conceptId", conceptIds))))
					.withPageable(LARGE_PAGE);
			try (final SearchHitsIterator<Description> descriptions = elasticsearchOperations.searchForStream(queryBuilder.build(), Description.class)) {
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
			if (semanticTagCacheEntry.branchHeadTime() == branchObject.getHead().getTime()) {
				return semanticTagCacheEntry.tagCounts();
			}
		}

		BranchCriteria branchCriteria = versionControlHelper.getBranchCriteria(branch);

		List<Long> activeConcepts = new LongArrayList();

		try (SearchHitsIterator<Concept> stream = elasticsearchOperations.searchForStream(new NativeQueryBuilder()
				.withQuery(bool(bq -> bq
						.must(branchCriteria.getEntityBranchCriteria(Concept.class))
						.must(termQuery(Concept.Fields.ACTIVE, true))))
				.withSourceFilter(new FetchSourceFilter(new String[]{Concept.Fields.CONCEPT_ID}, null))
				.withPageable(LARGE_PAGE).build(), Concept.class)) {
			stream.forEachRemaining(hit -> activeConcepts.add(hit.getContent().getConceptIdAsLong()));
		}

		SearchHits<Description> page = elasticsearchOperations.search(new NativeQueryBuilder()
				.withQuery(bool(bq -> bq
						.must(branchCriteria.getEntityBranchCriteria(Description.class))
						.must(termQuery(Description.Fields.ACTIVE, true))
						.must(termQuery(Description.Fields.TYPE_ID, Concepts.FSN))
						.filter(termsQuery(Description.Fields.CONCEPT_ID, activeConcepts)))
				)
				.withAggregation("semanticTags", AggregationBuilders.terms(a -> a.field(Description.Fields.TAG).size(semanticTagAggregationSearchSize)))
				.build(), Description.class);

		Map<String, Long> tagCounts = new TreeMap<>();
		if (page.hasAggregations()) {
			Aggregation semanticTags = getAggregations(page.getAggregations()).get("semanticTags");
			if (semanticTags != null) {
				for (StringTermsBucket bucket : semanticTags.getAggregate().sterms().buckets().array()) {
					tagCounts.put(bucket.key().stringValue(), bucket.docCount());
				}
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
		final NativeQueryBuilder queryBuilder = new NativeQueryBuilder();
		for (List<String> componentIdsSegment : Iterables.partition(componentIds, CLAUSE_LIMIT)) {
			queryBuilder.withQuery(bool(bq -> bq
							.must(branchCriteria.getEntityBranchCriteria(ReferenceSetMember.class))
							.must(termsQuery("refsetId", Concepts.inactivationAndAssociationRefsets))
							.must(termsQuery("referencedComponentId", componentIdsSegment))))
					.withPageable(LARGE_PAGE);
			// Join Members
			try (final SearchHitsIterator<ReferenceSetMember> members = elasticsearchOperations.searchForStream(queryBuilder.build(), ReferenceSetMember.class)) {
				members.forEachRemaining(hit -> {
					ReferenceSetMember member = hit.getContent();
					String referencedComponentId = member.getReferencedComponentId();
                    switch (member.getRefsetId()) {
                        case Concepts.CONCEPT_INACTIVATION_INDICATOR_REFERENCE_SET ->
                                conceptIdMap.get(referencedComponentId).addInactivationIndicatorMember(member);
                        case Concepts.DESCRIPTION_INACTIVATION_INDICATOR_REFERENCE_SET ->
                                descriptionIdMap.get(referencedComponentId).addInactivationIndicatorMember(member);
                        default -> {
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
                        }
                    }
				});
			}
		}
		if (timer != null) timer.checkpoint("get inactivation refset " + getFetchCount(componentIds.size()));
	}

	private void joinLangRefsetMembers(BranchCriteria branchCriteria, Set<String> allConceptIds, Map<String, Description> descriptionIdMap) {
		NativeQueryBuilder queryBuilder = new NativeQueryBuilder();
		for (List<String> conceptIds : Iterables.partition(allConceptIds, CLAUSE_LIMIT)) {

			queryBuilder.withQuery(bool(bq -> bq
							.must(branchCriteria.getEntityBranchCriteria(ReferenceSetMember.class))
							.must(termsQuery(ACCEPTABILITY_ID_FIELD_PATH, List.of(Concepts.PREFERRED, Concepts.ACCEPTABLE)))
							.must(termsQuery("conceptId", conceptIds))))
					.withPageable(LARGE_PAGE);
			// Join Lang Refset Members
			try (final SearchHitsIterator<ReferenceSetMember> langRefsetMembers = elasticsearchOperations.searchForStream(queryBuilder.build(), ReferenceSetMember.class)) {
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
		NativeQuery searchQuery = new NativeQueryBuilder()
				.withQuery(bool(bq ->bq
						.must(branchCriteria.getEntityBranchCriteria(Description.class))
						.must(termQuery(SnomedComponent.Fields.ACTIVE, true))
						.must(termsQuery(Description.Fields.CONCEPT_ID, conceptMiniMap.keySet()))))
				.withPageable(LARGE_PAGE)
				.build();
		Map<String, Description> descriptionIdMap = new HashMap<>();
		try (SearchHitsIterator<Description> stream = elasticsearchOperations.searchForStream(searchQuery, Description.class)) {
			stream.forEachRemaining(hit -> {
				Description description = hit.getContent();
				conceptMiniMap.get(description.getConceptId()).addActiveDescription(description);
				descriptionIdMap.put(description.getId(), description);
			});
		}
		joinLangRefsetMembers(branchCriteria, conceptMiniMap.keySet(), descriptionIdMap);
	}

	public SortedMap<Long, Long> applyDescriptionFilter(Collection<Long> conceptIds, List<TermFilter> termFilters, List<LanguageFilter> languageFilters,
			List<DescriptionTypeFilter> descriptionTypeFilters, List<DialectFilter> dialectFilters,
			BranchCriteria branchCriteria, ECLQueryService eclQueryService, BoolQuery.Builder masterDescriptionQuery) {

		for (TermFilter termFilter : termFilters) {
			BoolQuery.Builder termFilterQuery = bool();
			boolean termEquals = isEquals(termFilter.getBooleanComparisonOperator());
			for (TypedSearchTerm typedSearchTerm : termFilter.getTypedSearchTermSet()) {
				BoolQuery.Builder typedSearchTermQuery = bool();
				SearchMode searchMode = typedSearchTerm.getType() == SearchType.WILDCARD ? SearchMode.WILDCARD : SearchMode.STANDARD;
				addTermClauses(typedSearchTerm.getTerm(), searchMode, typedSearchTermQuery);
				termFilterQuery.should(typedSearchTermQuery.build()._toQuery());// Logical OR
			}
			addClause(termFilterQuery.build()._toQuery(), masterDescriptionQuery, termEquals);
		}

		for (LanguageFilter languageFilter : languageFilters) {
			addClause(termsQuery(Description.Fields.LANGUAGE_CODE, languageFilter.getLanguageCodes().stream().map(String::toLowerCase).collect(Collectors.toList())),
					masterDescriptionQuery, isEquals(languageFilter.getBooleanComparisonOperator()));
		}

		for (DescriptionTypeFilter descriptionTypeFilter : descriptionTypeFilters) {
			Set<String> typeIds;
			if (descriptionTypeFilter.getSubExpressionConstraint() != null) {
				typeIds = runExpressionConstraint(branchCriteria, eclQueryService, descriptionTypeFilter.getSubExpressionConstraint());
				if (typeIds.isEmpty()) {
					typeIds.add(NO_MATCH);
				}
			} else {
				typeIds = descriptionTypeFilter.getTypes().stream().map(DescriptionType::getTypeId).collect(Collectors.toSet());
			}
			addClause(termsQuery(Description.Fields.TYPE_ID, typeIds), masterDescriptionQuery, isEquals(descriptionTypeFilter.getBooleanComparisonOperator()));
		}

		BoolQuery.Builder criteria = bool().must(branchCriteria.getEntityBranchCriteria(Description.class))
				.filter(termsQuery(Description.Fields.CONCEPT_ID, conceptIds))
				.must(masterDescriptionQuery.build()._toQuery());

		final SortedMap<Long, Long> descriptionToConceptMap = new Long2ObjectLinkedOpenHashMap<>();
		NativeQueryBuilder searchQueryBuilder = new NativeQueryBuilder()
				.withQuery(criteria.build()._toQuery())
				.withSourceFilter(new FetchSourceFilter(new String[]{Description.Fields.DESCRIPTION_ID, Description.Fields.CONCEPT_ID}, null))
				.withPageable(LARGE_PAGE);
		try (SearchHitsIterator<Description> stream = elasticsearchOperations.searchForStream(searchQueryBuilder.build(), Description.class)) {
			stream.forEachRemaining(hit -> {
				Description description = hit.getContent();
				descriptionToConceptMap.put(Long.parseLong(description.getDescriptionId()), Long.parseLong(description.getConceptId()));
			});
		}

		if (!descriptionToConceptMap.isEmpty() && !dialectFilters.isEmpty()) {
			for (DialectFilter dialectFilter : dialectFilters) {
				BoolQuery.Builder masterLangRefsetQuery = bool().must(branchCriteria.getEntityBranchCriteria(ReferenceSetMember.class));
				masterLangRefsetQuery.must(termQuery(SnomedComponent.Fields.ACTIVE, true));
				masterLangRefsetQuery.filter(termsQuery(ReferenceSetMember.Fields.REFERENCED_COMPONENT_ID, descriptionToConceptMap.keySet()));

				boolean equals = isEquals(dialectFilter.getBooleanComparisonOperator());
				BoolQuery.Builder acceptabilityQuery = bool();
				if (dialectFilter.getSubExpressionConstraint() != null) {
					Set<String> dialects = runExpressionConstraint(branchCriteria, eclQueryService, dialectFilter.getSubExpressionConstraint());
					if (dialects.isEmpty()) {
						dialects.add(NO_MATCH);
					}
					acceptabilityQuery.must(termsQuery(REFSET_ID, dialects));
				} else {
					Map<String, Set<String>> acceptabilityMap = dialectAcceptabilitiesToMap(dialectFilter.getDialectAcceptabilities(), branchCriteria, eclQueryService);
					for (Map.Entry<String, Set<String>> stringSetEntry : acceptabilityMap.entrySet()) {
						BoolQuery.Builder langRefsetQuery = bool();
						langRefsetQuery.must(termQuery(REFSET_ID, stringSetEntry.getKey()));
						if (!stringSetEntry.getValue().isEmpty()) {
							langRefsetQuery.must(termsQuery(ACCEPTABILITY_ID_FIELD_PATH, stringSetEntry.getValue()));
						}
						acceptabilityQuery.should(langRefsetQuery.build()._toQuery());
					}
				}
				addClause(acceptabilityQuery.build()._toQuery(), masterLangRefsetQuery, equals);

				NativeQueryBuilder langRefsetSearch = new NativeQueryBuilder()
						.withQuery(masterLangRefsetQuery.build()._toQuery())
						.withSourceFilter(new FetchSourceFilter(new String[]{ReferenceSetMember.Fields.REFERENCED_COMPONENT_ID}, null))
						.withPageable(LARGE_PAGE);
				Set<Long> acceptableDescriptions = new LongOpenHashSet();
				try (SearchHitsIterator<ReferenceSetMember> stream = elasticsearchOperations.searchForStream(langRefsetSearch.build(), ReferenceSetMember.class)) {
					stream.forEachRemaining(hit -> acceptableDescriptions.add(Long.parseLong(hit.getContent().getReferencedComponentId())));
				}

				Set<Long> notAcceptableDescriptions =
						descriptionToConceptMap.keySet().stream().filter(Predicate.not(acceptableDescriptions::contains)).collect(Collectors.toSet());
				for (Long notAcceptableDescription : notAcceptableDescriptions) {
					descriptionToConceptMap.remove(notAcceptableDescription);
				}
			}
		}

		return descriptionToConceptMap;
	}

	private boolean isEquals(String booleanComparisonOperator) {
		return booleanComparisonOperator.equals("=");
	}

	private Map<String, Set<String>> dialectAcceptabilitiesToMap(List<DialectAcceptability> dialectAcceptabilities, BranchCriteria branchCriteria, ECLQueryService eclQueryService) {
		Map<String, Set<String>> acceptabilityMap = new HashMap<>();
		for (DialectAcceptability dialectAcceptability : dialectAcceptabilities) {
			Set<String> dialectIds;
			if (dialectAcceptability.getSubExpressionConstraint() != null) {
				dialectIds = runExpressionConstraint(branchCriteria, eclQueryService, dialectAcceptability.getSubExpressionConstraint());
				if (dialectIds.isEmpty()) {
					dialectIds.add(NO_MATCH);
				}
			} else {
				String dialectAlias = dialectAcceptability.getDialectAlias();
				Long refsetForDialect = dialectConfigurationService.findRefsetForDialect(dialectAlias);
				if (refsetForDialect == null) {
					throw new IllegalArgumentException(String.format("Dialect alias not recognised '%s'", dialectAlias));
				}
				dialectIds = Collections.singleton(refsetForDialect.toString());
			}
			Set<String> acceptability = new HashSet<>();

			for (ConceptReference conceptReference : orEmpty(dialectAcceptability.getAcceptabilityIdSet())) {
				acceptability.add(conceptReference.getConceptId());
			}
			for (Acceptability acceptabilityToken : orEmpty(dialectAcceptability.getAcceptabilityTokenSet())) {
				acceptability.add(acceptabilityToken.getAcceptabilityId());
			}

			for (String dialectId : dialectIds) {
				acceptabilityMap.put(dialectId, acceptability);
			}
		}
		return acceptabilityMap;
	}

	private Set<String> runExpressionConstraint(BranchCriteria branchCriteria, ECLQueryService eclQueryService, SubExpressionConstraint subExpressionConstraint) {
		return eclQueryService.doSelectConceptIds((SExpressionConstraint) subExpressionConstraint, branchCriteria, false, null, null)
				.stream().map(Object::toString).collect(Collectors.toSet());
	}

	private <T> Collection<T> orEmpty(Collection<T> collection) {
		return collection != null ? collection : Collections.emptySet();
	}

	DescriptionMatches findDescriptionAndConceptIds(DescriptionCriteria criteria, Set<Long> conceptIdsCriteria, BranchCriteria branchCriteria, TimerUtil timer) throws TooCostlyException {

		// Build up the description criteria
		final BoolQuery.Builder descriptionQueryBuilder = bool();
		descriptionQueryBuilder.must(branchCriteria.getEntityBranchCriteria(Description.class));
		addTermClauses(criteria.getTerm(), criteria.getSearchMode(), criteria.getSearchLanguageCodes(), criteria.getType(), descriptionQueryBuilder);

		Boolean active = criteria.getActive();
		if (active != null) {
			descriptionQueryBuilder.must(termQuery(Description.Fields.ACTIVE, active));
		}

		Collection<String> modules = criteria.getModules();
		if (!CollectionUtils.isEmpty(modules)) {
			descriptionQueryBuilder.must(termsQuery(Description.Fields.MODULE_ID, modules));
		}

		if (!CollectionUtils.isEmpty(conceptIdsCriteria)) {
			descriptionQueryBuilder.must(termsQuery(Description.Fields.CONCEPT_ID, conceptIdsCriteria));
		}

		// First pass search to collect all description and concept ids.
		final Map<Long, Long> descriptionToConceptMap = new Long2ObjectLinkedOpenHashMap<>();
		Query descriptionQuery = descriptionQueryBuilder.build()._toQuery();
		NativeQueryBuilder searchQueryBuilder = new NativeQueryBuilder()
				.withQuery(descriptionQuery)
				.withSourceFilter(new FetchSourceFilter(new String[]{Description.Fields.DESCRIPTION_ID, Description.Fields.CONCEPT_ID}, null));

		NativeQuery query = searchQueryBuilder.withPageable(PAGE_OF_ONE).build();
		query.setTrackTotalHits(true);
		long totalElements = elasticsearchOperations.search(query, Description.class).getTotalHits();
		if (totalElements > aggregationMaxProcessableResultsSize) {
			throw new TooCostlyException(String.format("There are over %s results. Aggregating these results would be too costly.", aggregationMaxProcessableResultsSize));
		}
		timer.checkpoint("Count all check");

		NativeQuery searchQuery = searchQueryBuilder.withPageable(LARGE_PAGE).build();
		addTermSort(searchQuery);
		try (SearchHitsIterator<Description> stream = elasticsearchOperations.searchForStream(
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
		if (!CollectionUtils.isEmpty(preferredIn) || !CollectionUtils.isEmpty(acceptableIn)
				|| !CollectionUtils.isEmpty(preferredOrAcceptableIn) || !CollectionUtils.isEmpty(criteria.getDisjunctionAcceptabilityCriteria())) {

			BoolQuery.Builder queryBuilder = bool()
					.must(branchCriteria.getEntityBranchCriteria(ReferenceSetMember.class))
					.must(termQuery(ReferenceSetMember.Fields.ACTIVE, true));

			if (!CollectionUtils.isEmpty(preferredIn)) {
				queryBuilder
						.must(termsQuery(REFSET_ID, preferredIn))
						.must(termQuery(ACCEPTABILITY_ID_FIELD_PATH, Concepts.PREFERRED));
			}
			if (!CollectionUtils.isEmpty(acceptableIn)) {
				queryBuilder
						.must(termsQuery(REFSET_ID, acceptableIn))
						.must(termQuery(ACCEPTABILITY_ID_FIELD_PATH, Concepts.ACCEPTABLE));
			}
			if (!CollectionUtils.isEmpty(preferredOrAcceptableIn)) {
				queryBuilder
						.must(termsQuery(REFSET_ID, preferredOrAcceptableIn))
						.must(termsQuery(ACCEPTABILITY_ID_FIELD_PATH, Sets.newHashSet(Concepts.PREFERRED, Concepts.ACCEPTABLE)));
			}
			// processing DisjunctionAcceptabilityCriteria
			if (criteria.getDisjunctionAcceptabilityCriteria() != null) {
				for (DescriptionCriteria.DisjunctionAcceptabilityCriteria disjunctionCriteria : criteria.getDisjunctionAcceptabilityCriteria()) {
					BoolQuery.Builder shouldClause = bool();
					if (!CollectionUtils.isEmpty(disjunctionCriteria.preferred())) {
						disjunctionCriteria.preferred().forEach(refsetId -> shouldClause.should(bool(b -> b
								.must(termQuery(REFSET_ID, refsetId))
								.must(termQuery(ACCEPTABILITY_ID_FIELD_PATH, Concepts.PREFERRED)))));
					}
					if (!CollectionUtils.isEmpty(acceptableIn)) {
						disjunctionCriteria.preferred().forEach(refsetId -> shouldClause.should(bool(b -> b
								.must(termQuery(REFSET_ID, refsetId))
								.must(termQuery(ACCEPTABILITY_ID_FIELD_PATH, Concepts.ACCEPTABLE)))));
					}
					if (!CollectionUtils.isEmpty(preferredOrAcceptableIn)) {
						shouldClause.should(bool(b -> b
								.must(termsQuery(REFSET_ID, preferredOrAcceptableIn))
								.must(termsQuery(ACCEPTABILITY_ID_FIELD_PATH, Sets.newHashSet(Concepts.PREFERRED, Concepts.ACCEPTABLE)))));
					}
					if (!shouldClause.build().should().isEmpty()) {
						queryBuilder.must(shouldClause.build()._toQuery());
					}
				}
			}

			NativeQuery nativeSearchQuery = new NativeQueryBuilder()
					.withQuery(queryBuilder.build()._toQuery())
					.withFilter(termsQuery(ReferenceSetMember.Fields.REFERENCED_COMPONENT_ID, descriptionToConceptMap.keySet()))
					.withSourceFilter(new FetchSourceFilter(new String[]{ReferenceSetMember.Fields.REFERENCED_COMPONENT_ID}, null))
					.withPageable(LARGE_PAGE)
					.build();
			Set<Long> filteredDescriptionIds = new LongOpenHashSet();
			try (SearchHitsIterator<ReferenceSetMember> stream = elasticsearchOperations.searchForStream(nativeSearchQuery, ReferenceSetMember.class)) {
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
				Set<Long> conceptIdsToSearch = conceptIds;
				try (SearchHitsIterator<Concept> stream = elasticsearchOperations.searchForStream(
						new NativeQueryBuilder()
								.withQuery(bool(bq -> bq
										.must(termQuery(Concept.Fields.ACTIVE, criteria.getConceptActive()))
										.filter(branchCriteria.getEntityBranchCriteria(Concept.class))
										.filter(termsQuery(Concept.Fields.CONCEPT_ID, conceptIdsToSearch)))
								)
								.withSort(SortOptions.of(sb -> sb.field(fs -> fs.field("_doc"))))
								.withSourceFilter(new FetchSourceFilter(new String[]{Concept.Fields.CONCEPT_ID}, null))
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
				Set<Long> conceptIdsToSearch = conceptIds;
				try (SearchHitsIterator<ReferenceSetMember> stream = elasticsearchOperations.searchForStream(
						new NativeQueryBuilder()
								.withQuery(bool(bq -> bq
										.must(termQuery(REFSET_ID, criteria.getConceptRefset()))
										.filter(branchCriteria.getEntityBranchCriteria(ReferenceSetMember.class))
										.filter(termsQuery(ReferenceSetMember.Fields.REFERENCED_COMPONENT_ID, conceptIdsToSearch)))
								)
								.withSort(SortOptions.of(sb -> sb.field(fs -> fs.field("_doc"))))
								.withSourceFilter(new FetchSourceFilter(new String[]{ReferenceSetMember.Fields.REFERENCED_COMPONENT_ID}, null))
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

	void addTermClauses(String term, SearchMode searchMode, Collection<String> languageCodes, Collection<Long> descriptionTypes, BoolQuery.Builder boolBuilder) {
		if (term != null) {
			addTermClauses(term, searchMode, boolBuilder);
		}

		if (!CollectionUtils.isEmpty(languageCodes)) {
			boolBuilder.must(termsQuery(Description.Fields.LANGUAGE_CODE, languageCodes));
		}
		if (descriptionTypes != null && !descriptionTypes.isEmpty()) {
			boolBuilder.must(termsQuery(Description.Fields.TYPE_ID, descriptionTypes));
		}
	}

	void addTermClauses(String term, SearchMode searchMode, BoolQuery.Builder typedSearchTermQuery) {
		if (IdentifierService.isConceptId(term)) {
			typedSearchTermQuery.must(termQuery(Description.Fields.CONCEPT_ID, term));
		} else if (IdentifierService.isDescriptionId(term)) {
			typedSearchTermQuery.must(termQuery(Description.Fields.DESCRIPTION_ID, term));
		} else {
			BoolQuery.Builder termFilter = bool();
			if (searchMode == SearchMode.REGEX) {
				// https://www.elastic.co/guide/en/elasticsearch/reference/master/query-dsl-query-string-query.html#_regular_expressions
				if (term.startsWith("^")) {
					term = term.substring(1);
				}
				if (term.endsWith("$")) {
					term = term.substring(0, term.length()-1);
				}
				termFilter.must(regexpQuery(Description.Fields.TERM, term));
			} else {
				Map<String, Set<Character>> charactersNotFoldedSets = searchLanguagesConfiguration.getCharactersNotFoldedSets();
				Set<String> languageFoldingStrategies = new HashSet<>(charactersNotFoldedSets.keySet());
				languageFoldingStrategies.add("");

				// All prefixes given. Simple Query String Query: https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-simple-query-string-query.html
				// e.g. 'Clin Fin' converts to 'clin* fin*' and matches 'Clinical Finding'
				// Put search term through character folding for each requested language
				BoolQuery.Builder foldedTermsQueryBuilder = bool();
				Set<BoolQuery.Builder> uniqueTermQueries = new HashSet<>();
				for (String languageFoldingStrategy : languageFoldingStrategies) {
					BoolQuery.Builder termQuery = getTermQuery(term, searchMode, charactersNotFoldedSets, languageFoldingStrategy);
					uniqueTermQueries.add(termQuery);
				}
				for (BoolQuery.Builder uniqueTermQuery : uniqueTermQueries) {
					foldedTermsQueryBuilder.should(uniqueTermQuery.build()._toQuery());// Logical OR
				}
				termFilter.must(foldedTermsQueryBuilder.build()._toQuery());

				if (SearchMode.WILDCARD == searchMode) {
					String replace = DescriptionHelper.wildcardToCaseInsensitiveRegex(term);
					termFilter.must(regexpQuery(Description.Fields.TERM, replace));
				} else if (containingNonAlphanumeric(term)) {
					// Apply second constraint against non-folded term
					String regexString = constructRegexQuery(term);
					termFilter.must(regexpQuery(Description.Fields.TERM, regexString));
				}
			}
			typedSearchTermQuery.filter(termFilter.build()._toQuery());
		}
	}

	private BoolQuery.Builder getTermQuery(String term, SearchMode searchMode, Map<String, Set<Character>> charactersNotFoldedSets, String languageCodeToMatch) {
		BoolQuery.Builder termQuery = bool();
		Set<Character> charactersNotFoldedForLanguage = charactersNotFoldedSets.getOrDefault(languageCodeToMatch, Collections.emptySet());
		String foldedSearchTerm = DescriptionHelper.foldTerm(term, charactersNotFoldedForLanguage);
		if (searchMode != SearchMode.WILDCARD) {
			foldedSearchTerm = constructSearchTerm(analyze(foldedSearchTerm, new StandardAnalyzer()));
		}
		if (foldedSearchTerm.isEmpty()) {
			return termQuery;
		}
		if (SearchMode.WHOLE_WORD == searchMode) {
			termQuery.filter(Queries.matchQuery(Description.Fields.TERM_FOLDED, foldedSearchTerm, Operator.And, 2.0f)._toQuery());
		} else if (SearchMode.WILDCARD == searchMode) {
			for (String part : foldedSearchTerm.replaceAll("(.)\\*(.)", "$1* *$2").split(" ")) {
				if (!part.isEmpty() && !part.equals("*")) {
					termQuery.filter(Queries.wildcardQuery(Description.Fields.TERM_FOLDED, part)._toQuery());
				}
			}
		} else {
			termQuery.filter(Queries.queryStringQuery(Description.Fields.TERM_FOLDED, constructSimpleQueryString(foldedSearchTerm), Operator.And, 2.0f)._toQuery());
		}
		return termQuery;
	}

	private void addClause(Query queryClause, BoolQuery.Builder boolBuilder, boolean equals) {
		if (equals) {
			boolBuilder.must(queryClause);
		} else {
			boolBuilder.mustNot(queryClause);
		}
	}

	public static List<String> analyze(String text, StandardAnalyzer analyzer) {
		List<String> result = new ArrayList<>();
		try {
			TokenStream tokenStream = analyzer.tokenStream("contents", text);
			CharTermAttribute attr = tokenStream.addAttribute(CharTermAttribute.class);
			tokenStream.reset();
			while (tokenStream.incrementToken()) {
				result.add(attr.toString());
			}
		} catch (IOException e) {
			LoggerFactory.getLogger(DescriptionService.class)
					.error("Failed to analyze text {}", text, e);
		}
		return result;
	}

	public static String constructSimpleQueryString(String searchTerm) {
		return (searchTerm.trim().replace(" ", "* ") + "*").replace("**", "*");
	}

	private boolean containingNonAlphanumeric(String term) {
		String[] words = term.split(" ");
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

	public static String constructSearchTerm(List<String> tokens) {
		StringBuilder builder = new StringBuilder();
		for (String token : tokens) {
			builder.append(token);
			builder.append(" ");
		}
		return builder.toString().trim();
	}

	static NativeQuery addTermSort(NativeQuery query) {
		query.addSort(Sort.by(Description.Fields.TERM_LEN));
		query.addSort(Sort.by("_score"));
		return query;
	}

	private record SemanticTagCacheEntry(long branchHeadTime, Map<String, Long> tagCounts) {
	}

	static class DescriptionMatches {

		private final Set<Long> conceptIds;
		private final Set<Long> descriptionIds;
		private final Query descriptionQuery;

		private DescriptionMatches(Set<Long> descriptionIds, Set<Long> conceptIds, Query descriptionQuery) {
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

		public Query getDescriptionQuery() {
			return descriptionQuery;
		}
	}
}
