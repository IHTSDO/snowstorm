package org.snomed.snowstorm.core.data.services;

import ch.qos.logback.classic.Level;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import io.kaicode.elasticvc.api.BranchCriteria;
import io.kaicode.elasticvc.api.ComponentService;
import io.kaicode.elasticvc.api.VersionControlHelper;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.elasticsearch.common.Strings;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.Operator;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.sort.SortBuilders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.data.services.identifier.IdentifierService;
import org.snomed.snowstorm.core.data.services.mapper.ConceptToConceptIdMapper;
import org.snomed.snowstorm.core.data.services.mapper.DescriptionToConceptIdGroupingMapper;
import org.snomed.snowstorm.core.data.services.mapper.DescriptionToConceptIdMapper;
import org.snomed.snowstorm.core.data.services.pojo.PageWithBucketAggregations;
import org.snomed.snowstorm.core.data.services.pojo.PageWithBucketAggregationsFactory;
import org.snomed.snowstorm.core.data.services.pojo.SimpleAggregation;
import org.snomed.snowstorm.core.util.TimerUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchResultMapper;
import org.springframework.data.elasticsearch.core.aggregation.AggregatedPage;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.data.util.CloseableIterator;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.lang.Long.parseLong;
import static org.elasticsearch.index.query.QueryBuilders.*;
import static org.snomed.snowstorm.config.Config.AGGREGATION_SEARCH_SIZE;
import static org.snomed.snowstorm.config.Config.PAGE_OF_ONE;

@Service
public class DescriptionService extends ComponentService {

	@Autowired
	private VersionControlHelper versionControlHelper;

	@Autowired
	private ElasticsearchOperations elasticsearchTemplate;

	public enum SearchMode {
		STANDARD, REGEX
	}

	private Logger logger = LoggerFactory.getLogger(getClass());

	public Description findDescription(String path, String descriptionId) {
		final BranchCriteria branchCriteria = versionControlHelper.getBranchCriteria(path);
		BoolQueryBuilder query = boolQuery().must(branchCriteria.getEntityBranchCriteria(Description.class))
				.must(termsQuery("descriptionId", descriptionId));
		List<Description> descriptions = elasticsearchTemplate.queryForList(
				new NativeSearchQueryBuilder().withQuery(query).build(), Description.class);
		if (descriptions.size() > 1) {
			String message = String.format("More than one description found for id %s on branch %s.", descriptionId, path);
			logger.error(message + " {}", descriptions);
			throw new IllegalStateException(message);
		}
		if (!descriptions.isEmpty()) {
			return descriptions.get(0);
		}
		return null;
	}

	public Page<Description> findDescriptions(String branch, String exactTerm, String concept, PageRequest pageRequest) {
		BranchCriteria branchCriteria = versionControlHelper.getBranchCriteria(branch);
		BoolQueryBuilder query = boolQuery().must(branchCriteria.getEntityBranchCriteria(Description.class));
		if (concept != null && !concept.isEmpty()) {
			query.must(termQuery(Description.Fields.CONCEPT_ID, concept));
		}
		if (exactTerm != null && !exactTerm.isEmpty()) {
			query.must(termQuery(Description.Fields.TERM, exactTerm));
		}
		Page<Description> descriptions = elasticsearchTemplate.queryForPage(new NativeSearchQueryBuilder()
				.withQuery(query)
				.withPageable(pageRequest)
				.build(), Description.class);
		List<Description> content = descriptions.getContent();
		joinLangRefsetMembers(branchCriteria,
				content.stream().map(Description::getConceptId).collect(Collectors.toSet()),
				content.stream().collect(Collectors.toMap(Description::getDescriptionId, Function.identity())));
		return descriptions;
	}

	public Set<Description> findDescriptions(String branchPath, Set<String> conceptIds) {
		BranchCriteria branchCriteria = versionControlHelper.getBranchCriteria(branchPath);
		Map<String, Concept> conceptMap = new HashMap<>();
		for (String conceptId : conceptIds) {
			conceptMap.put(conceptId, new Concept(conceptId));
		}
		joinDescriptions(branchCriteria, conceptMap, null, null, false);
		return conceptMap.values().stream().flatMap(c -> c.getDescriptions().stream()).collect(Collectors.toSet());
	}

	PageWithBucketAggregations<Description> findDescriptionsWithAggregations(String path, String term, PageRequest pageRequest) {
		return findDescriptionsWithAggregations(path, term, true, null, null, false, null, Collections.singleton("en"), pageRequest);
	}

	public PageWithBucketAggregations<Description> findDescriptionsWithAggregations(String path, String term,
			Boolean active, Boolean conceptActive, String semanticTag,
			boolean groupByConcept, SearchMode searchMode, Collection<String> languageCodes, PageRequest pageRequest) {

		TimerUtil timer = new TimerUtil("Search", Level.DEBUG);
		final BranchCriteria branchCriteria = versionControlHelper.getBranchCriteria(path);
		timer.checkpoint("Build branch criteria");
		List<Aggregation> allAggregations = new ArrayList<>();

		final BoolQueryBuilder descriptionCriteria = boolQuery();
		BoolQueryBuilder descriptionBranchCriteria = branchCriteria.getEntityBranchCriteria(Description.class);
		descriptionCriteria.must(descriptionBranchCriteria);
		addTermClauses(term, languageCodes, descriptionCriteria, searchMode);
		if (active != null) {
			descriptionCriteria.must(termQuery(Description.Fields.ACTIVE, active));
		}

		// Fetch concept semantic tag aggregation
		// Not all descriptions are FSNs so use: description -> concept -> active FSN
		Collection<Long> descriptionIdsGroupedByConcept = new LongArrayList();
		Collection<Long> conceptIds = findDescriptionConceptIds(descriptionCriteria, conceptActive, groupByConcept, descriptionIdsGroupedByConcept);
		BoolQueryBuilder descriptionFilter = boolQuery();
		if (groupByConcept) {
			descriptionFilter.must(termsQuery(Description.Fields.DESCRIPTION_ID, descriptionIdsGroupedByConcept));
		}
		timer.checkpoint("Fetch all related concept ids for semantic tag aggregation");

		BoolQueryBuilder fsnClauses = boolQuery();
		boolean semanticTagFiltering = !Strings.isNullOrEmpty(semanticTag);
		if (semanticTagFiltering) {
			fsnClauses.must(termQuery(Description.Fields.TAG, semanticTag));
		}

		NativeSearchQueryBuilder fsnQueryBuilder = new NativeSearchQueryBuilder()
				.withQuery(fsnClauses
						.must(descriptionBranchCriteria)
						.must(termsQuery(Description.Fields.ACTIVE, true))
						.must(termsQuery(Description.Fields.TYPE_ID, Concepts.FSN))
						.must(termsQuery(Description.Fields.CONCEPT_ID, conceptIds))
				)
				.addAggregation(AggregationBuilders.terms("semanticTags").field(Description.Fields.TAG).size(AGGREGATION_SEARCH_SIZE));

		if (!semanticTagFiltering) {
			fsnQueryBuilder
					.withPageable(PAGE_OF_ONE);
			AggregatedPage<Description> semanticTagResults = (AggregatedPage<Description>) elasticsearchTemplate.queryForPage(fsnQueryBuilder.build(), Description.class);
			allAggregations.add(semanticTagResults.getAggregation("semanticTags"));
			timer.checkpoint("Semantic tag aggregation");
		} else {
			// Apply semantic tag filter
			fsnQueryBuilder
					.withPageable(LARGE_PAGE)
					.withFields(Description.Fields.CONCEPT_ID);

			Set<Long> conceptSemanticTagMatches = new LongOpenHashSet();
			try (CloseableIterator<Description> descriptionStream = elasticsearchTemplate.stream(fsnQueryBuilder.build(), Description.class)) {
				descriptionStream.forEachRemaining(description -> conceptSemanticTagMatches.add(parseLong(description.getConceptId())));
			}
			conceptIds = conceptSemanticTagMatches;
			allAggregations.add(new SimpleAggregation("semanticTags", semanticTag, conceptSemanticTagMatches.size()));
		}


		// Fetch concept refset membership aggregation
		AggregatedPage<ReferenceSetMember> membershipResults = (AggregatedPage<ReferenceSetMember>) elasticsearchTemplate.queryForPage(new NativeSearchQueryBuilder()
				.withQuery(boolQuery()
						.must(branchCriteria.getEntityBranchCriteria(ReferenceSetMember.class))
						.must(termsQuery(ReferenceSetMember.Fields.ACTIVE, true))
						.must(termsQuery(ReferenceSetMember.Fields.REFERENCED_COMPONENT_ID, conceptIds))
				)
				.withPageable(PAGE_OF_ONE)
				.addAggregation(AggregationBuilders.terms("membership").field(ReferenceSetMember.Fields.REFSET_ID))
				.build(), ReferenceSetMember.class);
		allAggregations.add(membershipResults.getAggregation("membership"));
		timer.checkpoint("Concept refset membership aggregation");

		// Perform description search with description property aggregations
		descriptionFilter.must(termsQuery(Description.Fields.CONCEPT_ID, conceptIds));
		final NativeSearchQueryBuilder queryBuilder = new NativeSearchQueryBuilder()
				.withQuery(descriptionCriteria
						.filter(descriptionFilter))
				.addAggregation(AggregationBuilders.terms("module").field(Description.Fields.MODULE_ID))
				.addAggregation(AggregationBuilders.terms("language").field(Description.Fields.LANGUAGE_CODE))
				.withPageable(pageRequest);
		AggregatedPage<Description> descriptions = (AggregatedPage<Description>) elasticsearchTemplate.queryForPage(addTermSort(queryBuilder.build()), Description.class);
		allAggregations.addAll(descriptions.getAggregations().asList());
		timer.checkpoint("Fetch descriptions including module and language aggregations");
		timer.finish();

		// Merge aggregations
		return PageWithBucketAggregationsFactory.createPage(descriptions, allAggregations);
	}

	void joinDescriptions(BranchCriteria branchCriteria, Map<String, Concept> conceptIdMap, Map<String, ConceptMini> conceptMiniMap,
			TimerUtil timer, boolean fetchInactivationInfo) {

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
			try (final CloseableIterator<Description> descriptions = elasticsearchTemplate.stream(queryBuilder.build(), Description.class)) {
				descriptions.forEachRemaining(description -> {
					// Workaround - transient property sometimes persisted? FIXME
					description.setInactivationIndicator(null);

					// Join Descriptions
					final String descriptionConceptId = description.getConceptId();
					if (conceptIdMap != null) {
						final Concept concept = conceptIdMap.get(descriptionConceptId);
						if (concept != null) {
							concept.addDescription(description);
						}
					}
					if (conceptMiniMap != null) {
						final ConceptMini conceptMini = conceptMiniMap.get(descriptionConceptId);
						if (conceptMini != null && description.isActive()) {
							conceptMini.addActiveDescription(description);
						}
					}

					// Store Descriptions for Lang Refset join
					descriptionIdMap.put(description.getDescriptionId(), description);
				});
			}
		}
		if (timer != null) timer.checkpoint("get descriptions " + getFetchCount(allConceptIds.size()));

		// Fetch Inactivation Indicators and Associations
		if (fetchInactivationInfo) {
			Set<String> componentIds;
			if (conceptIdMap != null) {
				componentIds = Sets.union(conceptIdMap.keySet(), descriptionIdMap.keySet());
			} else {
				componentIds = descriptionIdMap.keySet();
			}
			for (List<String> componentIdsSegment : Iterables.partition(componentIds, CLAUSE_LIMIT)) {
				queryBuilder.withQuery(boolQuery()
						.must(branchCriteria.getEntityBranchCriteria(ReferenceSetMember.class))
						.must(termsQuery("refsetId", Concepts.inactivationAndAssociationRefsets))
						.must(termsQuery("referencedComponentId", componentIdsSegment)))
						.withPageable(LARGE_PAGE);
				// Join Members
				try (final CloseableIterator<ReferenceSetMember> members = elasticsearchTemplate.stream(queryBuilder.build(), ReferenceSetMember.class)) {
					members.forEachRemaining(member -> {
						String referencedComponentId = member.getReferencedComponentId();
						switch (member.getRefsetId()) {
							case Concepts.CONCEPT_INACTIVATION_INDICATOR_REFERENCE_SET:
								conceptIdMap.get(referencedComponentId).setInactivationIndicatorMember(member);
								break;
							case Concepts.DESCRIPTION_INACTIVATION_INDICATOR_REFERENCE_SET:
								descriptionIdMap.get(referencedComponentId).setInactivationIndicatorMember(member);
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
										logger.warn("Association ReferenceSetMember {} references concept {} " +
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

		// Fetch Lang Refset Members
		joinLangRefsetMembers(branchCriteria, allConceptIds, descriptionIdMap);
		if (timer != null) timer.checkpoint("get lang refset " + getFetchCount(allConceptIds.size()));
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
			try (final CloseableIterator<ReferenceSetMember> langRefsetMembers = elasticsearchTemplate.stream(queryBuilder.build(), ReferenceSetMember.class)) {
				langRefsetMembers.forEachRemaining(langRefsetMember -> {
					Description description = descriptionIdMap.get(langRefsetMember.getReferencedComponentId());
					if (description != null) {
						description.addLanguageRefsetMember(langRefsetMember);
					} else {
						logger.error("Description {} for lang refset member {} not found on branch {}!",
								langRefsetMember.getReferencedComponentId(), langRefsetMember.getMemberId(), branchCriteria.toString().replace("\n", ""));
					}
				});
			}
		}
	}

	public void joinActiveDescriptions(String path, Map<String, ConceptMini> conceptMiniMap) {
		NativeSearchQuery searchQuery = new NativeSearchQueryBuilder()
				.withQuery(boolQuery().must(versionControlHelper.getBranchCriteria(path).getEntityBranchCriteria(Description.class))
						.must(termQuery(SnomedComponent.Fields.ACTIVE, true))
						.must(termsQuery(Description.Fields.CONCEPT_ID, conceptMiniMap.keySet())))
				.withPageable(LARGE_PAGE)
				.build();
		try (CloseableIterator<Description> stream = elasticsearchTemplate.stream(searchQuery, Description.class)) {
			stream.forEachRemaining(description -> conceptMiniMap.get(description.getConceptId()).addActiveDescription(description));
		}
	}

	private Collection<Long> findDescriptionConceptIds(BoolQueryBuilder descriptionCriteria, Boolean conceptActive,
			boolean groupByConcept, Collection<Long> descriptionIdsGroupedByConcept) {

		Collection<Long> conceptIds;
		SearchResultMapper mapper;
		if (groupByConcept) {
			// Set used here because unique concepts needed
			Set<Long> conceptIdsSet = new LongOpenHashSet();
			conceptIds = conceptIdsSet;
			mapper = new DescriptionToConceptIdGroupingMapper(conceptIdsSet, descriptionIdsGroupedByConcept);
		} else {
			// List used here because it's faster
			conceptIds = new LongArrayList();
			mapper = new DescriptionToConceptIdMapper(conceptIds);
		}
		try (CloseableIterator<Description> stream = elasticsearchTemplate.stream(
				new NativeSearchQueryBuilder()
						.withQuery(descriptionCriteria)
						.withSort(SortBuilders.fieldSort("_doc"))
						.withStoredFields(Description.Fields.DESCRIPTION_ID, Description.Fields.CONCEPT_ID)
						.withPageable(LARGE_PAGE)
						.build(), Description.class, mapper)) {
			stream.forEachRemaining(hit -> {});
		}

		if (!conceptIds.isEmpty() && conceptActive != null) {
			// Apply concept active filter
			List<Long> conceptsWithActiveStatus = new LongArrayList();
			for (List<Long> conceptIdPartition : Iterables.partition(conceptIds, CLAUSE_LIMIT)) {
				try (CloseableIterator<Concept> stream = elasticsearchTemplate.stream(
						new NativeSearchQueryBuilder()
								.withQuery(termQuery(Concept.Fields.ACTIVE, conceptActive.booleanValue()))
								.withFilter(termsQuery(Concept.Fields.CONCEPT_ID, conceptIdPartition))
								.withSort(SortBuilders.fieldSort("_doc"))
								.withStoredFields(Concept.Fields.CONCEPT_ID)
								.withPageable(LARGE_PAGE)
								.build(), Concept.class, new ConceptToConceptIdMapper(conceptsWithActiveStatus))) {
					stream.forEachRemaining(hit -> {});
				}
			}
			return conceptsWithActiveStatus;
		}

		return conceptIds;
	}

	static void addTermClauses(String term, Collection<String> languageCodes, BoolQueryBuilder boolBuilder) {
		addTermClauses(term, languageCodes, boolBuilder, null);
	}

	static void addTermClauses(String term, Collection<String> languageCodes, BoolQueryBuilder boolBuilder, SearchMode searchMode) {
		if (IdentifierService.isConceptId(term)) {
			boolBuilder.must(termQuery(Description.Fields.CONCEPT_ID, term));
		} else {
			if (!Strings.isNullOrEmpty(term)) {
				if (searchMode == SearchMode.REGEX) {
					//https://www.elastic.co/guide/en/elasticsearch/reference/master/query-dsl-query-string-query.html#_regular_expressions
					if (term.startsWith("^")) {
						term = term.substring(1);
					}
					if (term.endsWith("$")) {
						term = term.substring(0, term.length()-1);
					}
					boolBuilder.must(regexpQuery(Description.Fields.TERM, term));
				} else {
					// Must match one of the following 'should' clauses:
					boolBuilder.must(boolQuery()

									// All given words. Match Query: https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-match-query.html
									.should(matchQuery(Description.Fields.TERM_FOLDED, term)
											.operator(Operator.AND))

									// All prefixes given. Simple Query String Query: https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-simple-query-string-query.html
									.should(simpleQueryStringQuery((term.trim().replace(" ", "* ") + "*") .replace("**", "*"))
											.field(Description.Fields.TERM_FOLDED).defaultOperator(Operator.AND))
							// e.g. 'Clin Fin' converts to 'clin* fin*' and matches 'Clinical Finding'
					);
				}
				// Must match the requested language
				boolBuilder.must(termsQuery(Description.Fields.LANGUAGE_CODE, languageCodes));
			}
		}
	}

	static NativeSearchQuery addTermSort(NativeSearchQuery query) {
		query.addSort(Sort.by("termLen"));
		query.addSort(Sort.by("_score"));
		return query;
	}
}
