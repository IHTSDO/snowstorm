package org.snomed.snowstorm.core.data.services;

import com.google.common.collect.Sets;
import io.kaicode.elasticvc.api.VersionControlHelper;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.data.repositories.QueryConceptRepository;
import org.snomed.snowstorm.core.data.services.pojo.ResultMapPage;
import org.snomed.snowstorm.core.util.CollectionUtil;
import org.snomed.snowstorm.core.util.TimerUtil;
import org.snomed.snowstorm.ecl.ECLQueryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.data.util.CloseableIterator;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

import static io.kaicode.elasticvc.api.ComponentService.LARGE_PAGE;
import static java.lang.Long.parseLong;
import static org.elasticsearch.index.query.QueryBuilders.*;

@Service
public class QueryService {

	static final PageRequest PAGE_OF_ONE = PageRequest.of(0, 1);

	@Autowired
	private ElasticsearchOperations elasticsearchTemplate;

	@Autowired
	private VersionControlHelper versionControlHelper;

	@Autowired
	private QueryConceptRepository queryConceptRepository;

	@Autowired
	private ConceptService conceptService;

	@Autowired
	private ECLQueryService eclQueryService;

	@Autowired
	private ReferenceSetMemberService memberService;

	@Autowired
	private RelationshipService relationshipService;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public Page<ConceptMini> search(ConceptQueryBuilder conceptQuery, String branchPath, PageRequest pageRequest) {
		QueryBuilder branchCriteria = versionControlHelper.getBranchCriteria(branchPath);

		// Validate Lexical criteria
		String term = conceptQuery.getTermPrefix();
		boolean hasLexicalCriteria;
		if (term != null) {
			if (term.length() < 3) {
				return new PageImpl<>(Collections.emptyList());
			}
			hasLexicalCriteria = true;
		} else {
			hasLexicalCriteria = false;
		}
		boolean hasLogicalConditions = conceptQuery.hasLogicalConditions();

		Page<Long> conceptIdPage = null;
		if (hasLexicalCriteria && !hasLogicalConditions) {
			// Lexical Only
			logger.info("Lexical search {}", term);
			NativeSearchQuery query = getLexicalQuery(term, branchCriteria, pageRequest);
			final List<Long> pageOfIds = new LongArrayList();
			Page<Description> descriptionPage = elasticsearchTemplate.queryForPage(query, Description.class);
			descriptionPage.getContent().forEach(d -> pageOfIds.add(parseLong(d.getConceptId())));

			conceptIdPage = new PageImpl<>(pageOfIds, pageRequest, descriptionPage.getTotalElements());

		} else if (hasLogicalConditions && !hasLexicalCriteria) {
			// Logical Only

			Set<String> conceptIds = conceptQuery.getConceptIds();
			if (conceptIds != null && !conceptIds.isEmpty()) {
				// Concept ID pass-through
				List<Long> conceptIdList = conceptIds.stream().map(Long::parseLong).collect(Collectors.toList());
				List<Long> pageOfIds = CollectionUtil.subList(conceptIdList, pageRequest.getPageNumber(), pageRequest.getPageSize());
				conceptIdPage = new PageImpl<>(pageOfIds, pageRequest, conceptIdList.size());
			} else if (conceptQuery.getEcl() != null) {
				// ECL search
				conceptIdPage = doEclSearch(conceptQuery, branchPath, pageRequest, branchCriteria);
			} else {
				// Primitive logical search
				conceptIdPage = getSimpleLogicalSearchPage(conceptQuery, branchCriteria, pageRequest);
			}

		} else if (hasLogicalConditions) {// AND hasLexicalCriteria (it must here)
			// Logical and Lexical

			// Perform lexical search first because this probably the smaller set
			// Use term search for ordering and provide filter for logical search
			logger.info("Lexical search before logical {}", term);
			TimerUtil timer = new TimerUtil("Lexical and Logical Search");
			final List<Long> allLexicalMatchesWithOrdering = fetchAllLexicalMatches(branchCriteria, term);
			timer.checkpoint("lexical complete");

			// Fetch Logical matches
			// Have to fetch all logical matches and then create a page using the lexical ordering
			List<Long> allFilteredLogicalMatches;
			if (conceptQuery.getEcl() != null) {
				allFilteredLogicalMatches = doEclSearch(conceptQuery, branchPath, branchCriteria, allLexicalMatchesWithOrdering);
			} else {
				logger.info("Primitive Logical Search ");
				allFilteredLogicalMatches = new LongArrayList();

				Boolean activeFilter = conceptQuery.getActiveFilter();
				if (activeFilter == null || activeFilter) {
					// All QueryConcepts are active

					NativeSearchQueryBuilder logicalSearchQuery = new NativeSearchQueryBuilder()
							.withQuery(boolQuery()
									.must(branchCriteria)
									.must(conceptQuery.getRootBuilder())
							)
							.withFilter(termsQuery(QueryConcept.CONCEPT_ID_FIELD, allLexicalMatchesWithOrdering))
							.withPageable(LARGE_PAGE);

					try (CloseableIterator<QueryConcept> stream = elasticsearchTemplate.stream(logicalSearchQuery.build(), QueryConcept.class)) {
						stream.forEachRemaining(c -> allFilteredLogicalMatches.add(c.getConceptIdL()));
					}
				} else {
					// Find inactive concepts
					if (!conceptQuery.hasRelationshipConditions()) {
						NativeSearchQueryBuilder inactiveConceptQuery = new NativeSearchQueryBuilder()
								.withQuery(boolQuery()
										.must(branchCriteria)
										.must(termQuery(Concept.Fields.ACTIVE, false))
								)
								.withFilter(termsQuery(Concept.Fields.CONCEPT_ID, allLexicalMatchesWithOrdering))
								.withPageable(LARGE_PAGE);
						try (CloseableIterator<Concept> stream = elasticsearchTemplate.stream(inactiveConceptQuery.build(), Concept.class)) {
							stream.forEachRemaining(c -> allFilteredLogicalMatches.add(c.getConceptIdAsLong()));
						}
					}
				}
			}
			timer.checkpoint("filtered logical complete");

			logger.info("{} lexical results, {} logical results", allLexicalMatchesWithOrdering.size(), allFilteredLogicalMatches.size());

			// Create page of ids which is an intersection of the lexical and logical lists using the lexical ordering
			conceptIdPage = CollectionUtil.listIntersection(allLexicalMatchesWithOrdering, allFilteredLogicalMatches, pageRequest);
		}

		if (conceptIdPage != null) {
			List<Long> pageOfConceptIds = conceptIdPage.getContent();
			ResultMapPage<String, ConceptMini> conceptMinis = conceptService.findConceptMinis(branchCriteria, pageOfConceptIds);
			return new PageImpl<>(getOrderedConceptList(pageOfConceptIds, conceptMinis.getResultsMap()), pageRequest, conceptIdPage.getTotalElements());
		} else {
			// No Criteria - return all concepts
			ResultMapPage<String, ConceptMini> conceptMinis = conceptService.findConceptMinis(branchCriteria, pageRequest);
			return new PageImpl<>(new ArrayList<>(conceptMinis.getResultsMap().values()), pageRequest, conceptMinis.getTotalElements());
		}
	}

	public Map<Long, Set<Long>> findActiveRelationshipsReferencingNotActiveConcepts(String branchPath, boolean stated) {
		Map<Long, Set<Long>> relationshipToInactiveConceptMap = new Long2ObjectOpenHashMap<>();
		QueryBuilder branchCriteria = versionControlHelper.getBranchCriteria(branchPath);
		TimerUtil timer = new TimerUtil("Integrity check");
		Collection<Long> activeConcepts = conceptService.findAllActiveConcepts(branchCriteria);
		timer.checkpoint("Fetch active concepts");
		NativeSearchQueryBuilder queryBuilder = new NativeSearchQueryBuilder();
		queryBuilder
				.withQuery(boolQuery()
						.must(branchCriteria)
						.must(termsQuery(Relationship.Fields.CHARACTERISTIC_TYPE_ID,
								stated ? Sets.newHashSet(Relationship.CharacteristicType.stated.getConceptId(), Relationship.CharacteristicType.additional.getConceptId()) :
										Sets.newHashSet(Relationship.CharacteristicType.inferred.getConceptId(), Relationship.CharacteristicType.additional.getConceptId())))
						.mustNot(
								boolQuery()
									.should(termsQuery(Relationship.Fields.SOURCE_ID, activeConcepts))
									.should(termsQuery(Relationship.Fields.TYPE_ID, activeConcepts))
									.should(termsQuery(Relationship.Fields.DESTINATION_ID, activeConcepts))
						)
				)
				.withPageable(LARGE_PAGE);
		try (CloseableIterator<Relationship> relationshipStream = elasticsearchTemplate.stream(queryBuilder.build(), Relationship.class)) {
			relationshipStream.forEachRemaining(relationship -> {
				Set<Long> inactiveConcepts = new HashSet<>();
				addIfInactive(relationship.getSourceId(), activeConcepts, inactiveConcepts);
				addIfInactive(relationship.getTypeId(), activeConcepts, inactiveConcepts);
				addIfInactive(relationship.getDestinationId(), activeConcepts, inactiveConcepts);
				relationshipToInactiveConceptMap.put(parseLong(relationship.getId()), inactiveConcepts);
			});
		}
		timer.finish();
		return relationshipToInactiveConceptMap;
	}

	private void addIfInactive(String conceptId, Collection<Long> activeConcepts, Set<Long> inactiveConcepts) {
		long conceptLong = parseLong(conceptId);
		if (!activeConcepts.contains(conceptLong)) {
			inactiveConcepts.add(conceptLong);
		}
	}

	private Page<Long> getSimpleLogicalSearchPage(ConceptQueryBuilder conceptQuery, QueryBuilder branchCriteria, PageRequest pageRequest) {
		Page<Long> conceptIdPage;NativeSearchQueryBuilder logicalSearchQuery = new NativeSearchQueryBuilder()
				.withQuery(boolQuery()
						.must(branchCriteria)
						.must(conceptQuery.getRootBuilder())
				)
				.withPageable(pageRequest);
		Page<QueryConcept> pageOfConcepts = elasticsearchTemplate.queryForPage(logicalSearchQuery.build(), QueryConcept.class);

		List<Long> pageOfIds = pageOfConcepts.getContent().stream().map(QueryConcept::getConceptIdL).collect(Collectors.toList());
		conceptIdPage = new PageImpl<>(pageOfIds, pageRequest, pageOfConcepts.getTotalElements());
		return conceptIdPage;
	}

	private List<Long> fetchAllLexicalMatches(QueryBuilder branchCriteria, String term) {
		final List<Long> allLexicalMatchesWithOrdering = new LongArrayList();

		NativeSearchQuery query = getLexicalQuery(term, branchCriteria, LARGE_PAGE);
		try (CloseableIterator<Description> descriptionStream = elasticsearchTemplate.stream(query, Description.class)) {
			descriptionStream.forEachRemaining(description -> allLexicalMatchesWithOrdering.add(parseLong(description.getConceptId())));
		}

		return allLexicalMatchesWithOrdering.stream().distinct().collect(Collectors.toList());
	}

	private Page<Long> doEclSearch(ConceptQueryBuilder conceptQuery, String branchPath, PageRequest pageRequest, QueryBuilder branchCriteria) {
		String ecl = conceptQuery.getEcl();
		logger.info("ECL Search {}", ecl);
		return eclQueryService.selectConceptIds(ecl, branchCriteria, branchPath, conceptQuery.isStated(), pageRequest);
	}

	private List<Long> doEclSearch(ConceptQueryBuilder conceptQuery, String branchPath, QueryBuilder branchCriteria, List<Long> conceptIdFilter) {
		String ecl = conceptQuery.getEcl();
		logger.info("ECL Search {}", ecl);
		return eclQueryService.selectConceptIds(ecl, branchCriteria, branchPath, conceptQuery.isStated(), conceptIdFilter).getContent();
	}

	private NativeSearchQuery getLexicalQuery(String term, QueryBuilder branchCriteria, PageRequest pageable) {
		BoolQueryBuilder lexicalQuery = boolQuery()
				.must(branchCriteria)
				.must(termQuery("active", true));
		DescriptionService.addTermClauses(term, lexicalQuery);
		NativeSearchQueryBuilder queryBuilder = new NativeSearchQueryBuilder()
				.withQuery(lexicalQuery)
				.withPageable(pageable);
		NativeSearchQuery query = queryBuilder.build();
		DescriptionService.addTermSort(query);
		return query;
	}

	private List<ConceptMini> getOrderedConceptList(List<Long> termConceptIds, Map<String, ConceptMini> conceptMiniMap) {
		return termConceptIds.stream().filter(id -> conceptMiniMap.keySet().contains(id.toString())).map(id -> conceptMiniMap.get(id.toString())).collect(Collectors.toList());
	}

	public Page<QueryConcept> queryForPage(NativeSearchQuery searchQuery) {
		return elasticsearchTemplate.queryForPage(searchQuery, QueryConcept.class);
	}

	public CloseableIterator<QueryConcept> stream(NativeSearchQuery searchQuery) {
		return elasticsearchTemplate.stream(searchQuery, QueryConcept.class);
	}

	public Set<Long> retrieveAncestors(String conceptId, String path, boolean stated) {
		return retrieveAncestors(versionControlHelper.getBranchCriteria(path), path, stated, conceptId);
	}

	public Set<Long> retrieveParents(QueryBuilder branchCriteria, boolean stated, String conceptId) {
		final NativeSearchQuery searchQuery = new NativeSearchQueryBuilder()
				.withQuery(boolQuery()
						.must(branchCriteria)
						.must(termQuery(QueryConcept.Fields.CONCEPT_ID, conceptId))
						.must(termQuery("stated", stated))
				)
				.withPageable(PAGE_OF_ONE)
				.build();
		List<QueryConcept> concepts = elasticsearchTemplate.queryForList(searchQuery, QueryConcept.class);
		return concepts.isEmpty() ? Collections.emptySet() : concepts.get(0).getParents();
	}

	public Set<Long> retrieveAncestors(QueryBuilder branchCriteria, String path, boolean stated, String conceptId) {
		final NativeSearchQuery searchQuery = new NativeSearchQueryBuilder()
				.withQuery(boolQuery()
						.must(branchCriteria)
						.must(termQuery(QueryConcept.Fields.CONCEPT_ID, conceptId))
						.must(termQuery("stated", stated))
				)
				.withPageable(LARGE_PAGE)
				.build();
		final List<QueryConcept> concepts = elasticsearchTemplate.queryForPage(searchQuery, QueryConcept.class).getContent();
		if (concepts.size() > 1) {
			logger.error("More than one index concept found {}", concepts);
			throw new IllegalStateException("More than one query-index-concept found for id " + conceptId + " on branch " + path + ".");
		}
		if (concepts.isEmpty()) {
			throw new IllegalArgumentException(String.format("Concept %s not found on branch %s", conceptId, path));
		}
		return concepts.get(0).getAncestors();
	}

	public Set<Long> retrieveAllAncestors(QueryBuilder branchCriteria, boolean stated, Collection<Long> conceptId) {
		final NativeSearchQuery searchQuery = new NativeSearchQueryBuilder()
				.withQuery(boolQuery()
						.must(branchCriteria)
						.must(termsQuery(QueryConcept.Fields.CONCEPT_ID, conceptId))
						.must(termQuery("stated", stated))
				)
				.withPageable(LARGE_PAGE)
				.build();
		final List<QueryConcept> concepts = elasticsearchTemplate.queryForPage(searchQuery, QueryConcept.class).getContent();
		Set<Long> allAncestors = new HashSet<>();
		for (QueryConcept concept : concepts) {
			allAncestors.addAll(concept.getAncestors());
		}
		return allAncestors;
	}
	
	public List<Long> retrieveAllDescendants(QueryBuilder branchCriteria, boolean stated, Collection<Long> conceptIds) {
		final NativeSearchQuery searchQuery = new NativeSearchQueryBuilder()
				.withQuery(boolQuery()
						.must(branchCriteria)
						.must(termsQuery("ancestors", conceptIds))
						.must(termQuery("stated", stated))
				)
				.withPageable(LARGE_PAGE)
				.withSort(SortBuilders.fieldSort(QueryConcept.CONCEPT_ID_FIELD))// This could be anything
				.build();
		Page<QueryConcept> conceptsPage = elasticsearchTemplate.queryForPage(searchQuery, QueryConcept.class);
		List<Long> conceptIdsFound = conceptsPage.getContent().stream().map(QueryConcept::getConceptIdL).collect(Collectors.toList());
		return new PageImpl<>(conceptIdsFound, LARGE_PAGE, conceptsPage.getTotalElements()).getContent();
	}

	public Set<Long> retrieveConceptsInReferenceSet(QueryBuilder branchCriteria, String referenceSetId) {
		return memberService.findConceptsInReferenceSet(branchCriteria, referenceSetId);
	}

	public List<Long> retrieveRelationshipDestinations(Collection<Long> sourceConceptIds, List<Long> attributeTypeIds, QueryBuilder branchCriteria, boolean stated) {
		return relationshipService.retrieveRelationshipDestinations(sourceConceptIds, attributeTypeIds, branchCriteria, stated);
	}

	void deleteAll() {
		queryConceptRepository.deleteAll();
	}

	/**
	 * Creates a ConceptQueryBuilder for use with search methods.
	 *
	 * @param stated If the stated or inferred form should be used in any logical conditions.
	 * @return a new ConceptQueryBuilder
	 */
	public ConceptQueryBuilder createQueryBuilder(boolean stated) {
		return new ConceptQueryBuilder(stated);
	}

	public final class ConceptQueryBuilder {

		private final BoolQueryBuilder rootBuilder;
		private final BoolQueryBuilder logicalConditionBuilder;
		private final boolean stated;
		private Boolean activeFilter;
		private String termPrefix;
		private String ecl;
		private Set<String> conceptIds;

		private ConceptQueryBuilder(boolean stated) {
			this.stated = stated;
			rootBuilder = boolQuery();
			logicalConditionBuilder = boolQuery();
			rootBuilder.must(termQuery("stated", stated));
			rootBuilder.must(logicalConditionBuilder);
		}

		public ConceptQueryBuilder self(Long conceptId) {
			logger.info("conceptId = {}", conceptId);
			logicalConditionBuilder.should(termQuery(QueryConcept.Fields.CONCEPT_ID, conceptId));
			return this;
		}

		public ConceptQueryBuilder descendant(Long conceptId) {
			logger.info("ancestors = {}", conceptId);
			logicalConditionBuilder.should(termQuery("ancestors", conceptId));
			return this;
		}

		public ConceptQueryBuilder selfOrDescendant(Long conceptId) {
			self(conceptId);
			descendant(conceptId);
			return this;
		}

		public ConceptQueryBuilder ecl(String ecl) {
			this.ecl = ecl;
			return this;
		}

		/**
		 * Term prefix has a minimum length of 3 characters.
		 */
		public ConceptQueryBuilder termPrefix(String termPrefix) {
			if (termPrefix != null && termPrefix.isEmpty()) {
				termPrefix = null;
			}
			this.termPrefix = termPrefix;
			return this;
		}

		public ConceptQueryBuilder conceptIds(Set<String> conceptIds) {
			this.conceptIds = conceptIds;
			return this;
		}

		public ConceptQueryBuilder activeFilter(Boolean active) {
			this.activeFilter = active;
			return this;
		}

		private boolean hasLogicalConditions() {
			return hasRelationshipConditions() ||
					(conceptIds != null && !conceptIds.isEmpty()) ||
					activeFilter != null;
		}

		private boolean hasRelationshipConditions() {
			return getEcl() != null || logicalConditionBuilder.hasClauses();
		}

		private BoolQueryBuilder getRootBuilder() {
			return rootBuilder;
		}

		private String getTermPrefix() {
			return termPrefix;
		}

		private String getEcl() {
			return ecl;
		}

		private boolean isStated() {
			return stated;
		}

		private Set<String> getConceptIds() {
			return conceptIds;
		}

		private Boolean getActiveFilter() {
			return activeFilter;
		}
	}

}
