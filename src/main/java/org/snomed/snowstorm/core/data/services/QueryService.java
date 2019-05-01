package org.snomed.snowstorm.core.data.services;

import io.kaicode.elasticvc.api.BranchCriteria;
import io.kaicode.elasticvc.api.VersionControlHelper;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongArraySet;
import it.unimi.dsi.fastutil.longs.LongComparators;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.data.services.pojo.ResultMapPage;
import org.snomed.snowstorm.core.util.PageHelper;
import org.snomed.snowstorm.core.util.TimerUtil;
import org.snomed.snowstorm.ecl.ECLQueryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.snomed.snowstorm.rest.converter.SearchAfterHelper;
import org.springframework.data.elasticsearch.core.SearchAfterPage;
import org.springframework.data.elasticsearch.core.aggregation.impl.AggregatedPageImpl;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.data.util.CloseableIterator;
import org.springframework.stereotype.Service;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static io.kaicode.elasticvc.api.ComponentService.LARGE_PAGE;
import static java.lang.Long.parseLong;
import static org.elasticsearch.index.query.QueryBuilders.*;
import static org.snomed.snowstorm.config.Config.DEFAULT_LANGUAGE_CODES;
import static org.snomed.snowstorm.ecl.ConceptSelectorHelper.getDefaultSortForQueryConcept;

@Service
public class QueryService {

	static final PageRequest PAGE_OF_ONE = PageRequest.of(0, 1);

	@Autowired
	private ElasticsearchOperations elasticsearchTemplate;

	@Autowired
	private VersionControlHelper versionControlHelper;

	@Autowired
	private ConceptService conceptService;

	@Autowired
	private ECLQueryService eclQueryService;

	@Autowired
	private ReferenceSetMemberService memberService;

	@Autowired
	private RelationshipService relationshipService;

	private static final Function<Long, Object[]> CONCEPT_ID_SEARCH_AFTER_EXTRACTOR =
			conceptId -> conceptId == null ? null : SearchAfterHelper.convertToTokenAndBack(new Object[]{conceptId});

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public Page<ConceptMini> search(ConceptQueryBuilder conceptQuery, String branchPath, PageRequest pageRequest) {
		BranchCriteria branchCriteria = versionControlHelper.getBranchCriteria(branchPath);
		Optional<SearchAfterPage<Long>> conceptIdPageOptional = doSearchForIds(conceptQuery, branchPath, branchCriteria, pageRequest);

		if (conceptIdPageOptional.isPresent()) {
			SearchAfterPage<Long> conceptIdPage = conceptIdPageOptional.get();
			ResultMapPage<String, ConceptMini> conceptMinis = conceptService.findConceptMinis(branchCriteria, conceptIdPage.getContent(), conceptQuery.getLanguageCodes());
			List<ConceptMini> conceptMinis1 = sortConceptMinisByTermOrder(conceptIdPage.getContent(), conceptMinis.getResultsMap());
			return PageHelper.toSearchAfterPage(conceptMinis1, conceptIdPage);
		} else {
			// No ids - return page of all concepts
			ResultMapPage<String, ConceptMini> conceptMinis = conceptService.findConceptMinis(branchCriteria, conceptQuery.getLanguageCodes(), pageRequest);
			return new PageImpl<>(new ArrayList<>(conceptMinis.getResultsMap().values()), pageRequest, conceptMinis.getTotalElements());
		}
	}

	public SearchAfterPage<Long> searchForIds(ConceptQueryBuilder conceptQuery, String branchPath, PageRequest pageRequest) {
		BranchCriteria branchCriteria = versionControlHelper.getBranchCriteria(branchPath);
		Optional<SearchAfterPage<Long>> conceptIdPageOptional = doSearchForIds(conceptQuery, branchPath, branchCriteria, pageRequest);
		return conceptIdPageOptional.orElseGet(() -> {
			// No ids - return page of all concept ids
			NativeSearchQueryBuilder queryBuilder = new NativeSearchQueryBuilder()
					.withQuery(boolQuery().must(branchCriteria.getEntityBranchCriteria(Concept.class)))
					.withSort(SortBuilders.fieldSort(Concept.Fields.CONCEPT_ID))
					.withPageable(pageRequest);
			Page<Concept> conceptPage = elasticsearchTemplate.queryForPage(queryBuilder.build(), Concept.class);
			return PageHelper.mapToSearchAfterPage(conceptPage, Concept::getConceptIdAsLong, CONCEPT_ID_SEARCH_AFTER_EXTRACTOR);
		});
	}

	private Optional<SearchAfterPage<Long>> doSearchForIds(ConceptQueryBuilder conceptQuery, String branchPath, BranchCriteria branchCriteria, PageRequest pageRequest) {

		// Validate Lexical criteria
		String term = conceptQuery.getTermPrefix();
		Collection<String> languageCodes = conceptQuery.getLanguageCodes();
		boolean hasLexicalCriteria;
		if (term != null) {
			if (term.length() < 3) {
				return Optional.of(new AggregatedPageImpl<>(Collections.emptyList()));
			}
			hasLexicalCriteria = true;
		} else {
			hasLexicalCriteria = false;
		}
		boolean hasLogicalConditions = conceptQuery.hasLogicalConditions();

		SearchAfterPage<Long> conceptIdPage = null;
		if (hasLexicalCriteria && !hasLogicalConditions) {
			// Lexical Only
			logger.info("Lexical search {}", term);
			NativeSearchQuery descriptionQuery = getLexicalQuery(term, languageCodes, branchCriteria, pageRequest);
			descriptionQuery.addFields(Description.Fields.CONCEPT_ID);
			final List<Long> pageOfIds = new LongArrayList();
			Page<Description> descriptionPage = elasticsearchTemplate.queryForPage(descriptionQuery, Description.class);
			descriptionPage.getContent().forEach(d -> pageOfIds.add(parseLong(d.getConceptId())));

			conceptIdPage = new AggregatedPageImpl<>(pageOfIds, pageRequest, descriptionPage.getTotalElements(), ((SearchAfterPage)descriptionPage).getSearchAfter());

		} else if (hasLogicalConditions && !hasLexicalCriteria) {
			// Logical Only

			if (conceptQuery.getEcl() != null) {
				// ECL search
				conceptIdPage = doEclSearchAndDefinitionFilter(conceptQuery, branchPath, pageRequest, branchCriteria);
			} else {
				// Primitive logical search
				conceptIdPage = getSimpleLogicalSearchPage(conceptQuery, branchCriteria, pageRequest);
			}

		} else if (hasLogicalConditions) {
			// Logical and Lexical

			// Perform lexical search first because this probably the smaller set.
			// We fetch all lexical results then use them to filter the logical matches and for ordering of the final results.
			logger.info("Lexical search before logical {}", term);
			TimerUtil timer = new TimerUtil("Lexical and Logical Search");
			final List<Long> allLexicalMatchesWithOrdering = findLexicalMatchDescriptionConceptIds(branchCriteria, term, languageCodes);
			timer.checkpoint("lexical complete");

			// Fetch Logical matches
			// ECL, QueryConcept and Concept searches are filtered by the conceptIds gathered from the lexical search
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
									.must(branchCriteria.getEntityBranchCriteria(QueryConcept.class))
									.must(conceptQuery.getRootBuilder())
							)
							.withFilter(termsQuery(QueryConcept.Fields.CONCEPT_ID, allLexicalMatchesWithOrdering))
							.withFields(QueryConcept.Fields.CONCEPT_ID)
							.withPageable(LARGE_PAGE);

					try (CloseableIterator<QueryConcept> stream = elasticsearchTemplate.stream(logicalSearchQuery.build(), QueryConcept.class)) {
						stream.forEachRemaining(c -> allFilteredLogicalMatches.add(c.getConceptIdL()));
					}
				} else {
					// Find inactive concepts
					if (!conceptQuery.hasRelationshipConditions()) {
						NativeSearchQueryBuilder inactiveConceptQuery = new NativeSearchQueryBuilder()
								.withQuery(boolQuery()
										.must(branchCriteria.getEntityBranchCriteria(Concept.class))
										.must(termQuery(Concept.Fields.ACTIVE, false))
								)
								.withFilter(termsQuery(Concept.Fields.CONCEPT_ID, allLexicalMatchesWithOrdering))
								.withFields(Concept.Fields.CONCEPT_ID)
								.withPageable(LARGE_PAGE);
						try (CloseableIterator<Concept> stream = elasticsearchTemplate.stream(inactiveConceptQuery.build(), Concept.class)) {
							stream.forEachRemaining(c -> allFilteredLogicalMatches.add(c.getConceptIdAsLong()));
						}
					}
				}
			}
			List<Long> allFilteredLogicalMatchesFinal = filterByDefinitionStatus(allFilteredLogicalMatches, conceptQuery.getDefinitionStatusFilter(), branchCriteria);

			timer.checkpoint("filtered logical complete");

			logger.info("{} lexical results, {} logical results", allLexicalMatchesWithOrdering.size(), allFilteredLogicalMatchesFinal.size());

			// Create page of ids which is an intersection of the lexical and logical lists using the lexical ordering
			conceptIdPage = PageHelper.listIntersection(allLexicalMatchesWithOrdering, allFilteredLogicalMatchesFinal, pageRequest, CONCEPT_ID_SEARCH_AFTER_EXTRACTOR);
		}

		if (conceptIdPage != null) {
			return Optional.of(conceptIdPage);
		} else {
			return Optional.empty();
		}
	}

	private List<Long> filterByDefinitionStatus(List<Long> conceptIds, @Nullable String definitionStatus, BranchCriteria branchCriteria) {
		if (definitionStatus == null || definitionStatus.isEmpty()) {
			return conceptIds;
		}

		List<Long> filteredConceptIds = new LongArrayList();

		NativeSearchQueryBuilder conceptDefinitionQuery = new NativeSearchQueryBuilder()
				.withQuery(boolQuery()
						.must(branchCriteria.getEntityBranchCriteria(Concept.class))
						.must(termQuery(Concept.Fields.DEFINITION_STATUS_ID, definitionStatus))
				)
				.withFilter(termsQuery(Concept.Fields.CONCEPT_ID, conceptIds))
				.withFields(Concept.Fields.CONCEPT_ID)
				.withSort(getDefaultSortForQueryConcept())
				.withPageable(LARGE_PAGE);

		try (CloseableIterator<Concept> stream = elasticsearchTemplate.stream(conceptDefinitionQuery.build(), Concept.class)) {
			stream.forEachRemaining(concept -> filteredConceptIds.add(concept.getConceptIdAsLong()));
		}

		return filteredConceptIds;
	}

	private SearchAfterPage<Long> getSimpleLogicalSearchPage(ConceptQueryBuilder conceptQuery, BranchCriteria branchCriteria, PageRequest pageRequest) {
		NativeSearchQueryBuilder logicalSearchQuery = new NativeSearchQueryBuilder()
				.withQuery(boolQuery()
						.must(branchCriteria.getEntityBranchCriteria(QueryConcept.class))
						.must(conceptQuery.getRootBuilder())
				)
				.withFields(QueryConcept.Fields.CONCEPT_ID)
				.withSort(getDefaultSortForQueryConcept())
				.withPageable(pageRequest);
		Page<QueryConcept> pageOfConcepts = elasticsearchTemplate.queryForPage(logicalSearchQuery.build(), QueryConcept.class);

		List<Long> pageOfIds = pageOfConcepts.getContent().stream().map(QueryConcept::getConceptIdL).collect(Collectors.toList());
		return PageHelper.fullListToPage(pageOfIds, pageRequest, CONCEPT_ID_SEARCH_AFTER_EXTRACTOR);
	}

	private List<Long> findLexicalMatchDescriptionConceptIds(BranchCriteria branchCriteria, String term, Collection<String> languageCodes) {
		final List<Long> allLexicalMatchesWithOrdering = new LongArrayList();

		NativeSearchQuery query = getLexicalQuery(term, languageCodes, branchCriteria, LARGE_PAGE);
		query.addFields(Description.Fields.CONCEPT_ID);
		try (CloseableIterator<Description> descriptionStream = elasticsearchTemplate.stream(query, Description.class)) {
			descriptionStream.forEachRemaining(description -> allLexicalMatchesWithOrdering.add(parseLong(description.getConceptId())));
		}

		return allLexicalMatchesWithOrdering.stream().distinct().collect(Collectors.toList());
	}

	private SearchAfterPage<Long> doEclSearchAndDefinitionFilter(ConceptQueryBuilder conceptQuery, String branchPath, PageRequest pageRequest, BranchCriteria branchCriteria) {
		String ecl = conceptQuery.getEcl();
		logger.info("ECL Search {}", ecl);

		String definitionStatusFilter = conceptQuery.definitionStatusFilter;
		if (definitionStatusFilter != null && !definitionStatusFilter.isEmpty()) {
			Page<Long> allConceptIds = eclQueryService.selectConceptIds(ecl, branchCriteria, branchPath, conceptQuery.isStated(), null, null);
			List<Long> filteredConceptIds = filterByDefinitionStatus(allConceptIds.getContent(), conceptQuery.definitionStatusFilter, branchCriteria);
			return PageHelper.fullListToPage(filteredConceptIds, pageRequest, CONCEPT_ID_SEARCH_AFTER_EXTRACTOR);
		} else {
			return PageHelper.toSearchAfterPage(eclQueryService.selectConceptIds(ecl, branchCriteria, branchPath, conceptQuery.isStated(), pageRequest),
					CONCEPT_ID_SEARCH_AFTER_EXTRACTOR);
		}
	}

	private List<Long> doEclSearch(ConceptQueryBuilder conceptQuery, String branchPath, BranchCriteria branchCriteria, List<Long> conceptIdFilter) {
		String ecl = conceptQuery.getEcl();
		logger.info("ECL Search {}", ecl);
		return eclQueryService.selectConceptIds(ecl, branchCriteria, branchPath, conceptQuery.isStated(), conceptIdFilter).getContent();
	}

	private NativeSearchQuery getLexicalQuery(String term, Collection<String> languageCodes, BranchCriteria branchCriteria, PageRequest pageable) {
		BoolQueryBuilder lexicalQuery = boolQuery()
				.must(branchCriteria.getEntityBranchCriteria(Description.class))
				.must(termQuery("active", true));
		DescriptionService.addTermClauses(term, languageCodes, lexicalQuery);
		NativeSearchQueryBuilder queryBuilder = new NativeSearchQueryBuilder()
				.withQuery(lexicalQuery)
				.withPageable(pageable);
		NativeSearchQuery query = queryBuilder.build();
		DescriptionService.addTermSort(query);
		return query;
	}

	private List<ConceptMini> sortConceptMinisByTermOrder(List<Long> termConceptIds, Map<String, ConceptMini> conceptMiniMap) {
		return termConceptIds.stream().filter(id -> conceptMiniMap.keySet().contains(id.toString())).map(id -> conceptMiniMap.get(id.toString())).collect(Collectors.toList());
	}

	public Page<QueryConcept> queryForPage(NativeSearchQuery searchQuery) {
		return elasticsearchTemplate.queryForPage(searchQuery, QueryConcept.class);
	}

	public CloseableIterator<QueryConcept> streamQueryResults(NativeSearchQuery searchQuery) {
		return elasticsearchTemplate.stream(searchQuery, QueryConcept.class);
	}

	public Set<Long> findAncestorIds(String conceptId, String path, boolean stated) {
		return findAncestorIds(versionControlHelper.getBranchCriteria(path), path, stated, conceptId);
	}

	public Set<Long> findParentIds(BranchCriteria branchCriteria, boolean stated, String conceptId) {
		final NativeSearchQuery searchQuery = new NativeSearchQueryBuilder()
				.withQuery(boolQuery()
						.must(branchCriteria.getEntityBranchCriteria(QueryConcept.class))
						.must(termQuery(QueryConcept.Fields.CONCEPT_ID, conceptId))
						.must(termQuery("stated", stated))
				)
				.withPageable(PAGE_OF_ONE)
				.build();
		List<QueryConcept> concepts = elasticsearchTemplate.queryForList(searchQuery, QueryConcept.class);
		return concepts.isEmpty() ? Collections.emptySet() : concepts.get(0).getParents();
	}

	public Set<Long> findAncestorIds(BranchCriteria branchCriteria, String path, boolean stated, String conceptId) {
		final NativeSearchQuery searchQuery = new NativeSearchQueryBuilder()
				.withQuery(boolQuery()
						.must(branchCriteria.getEntityBranchCriteria(QueryConcept.class))
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

	public Set<Long> findAncestorIdsAsUnion(BranchCriteria branchCriteria, boolean stated, Collection<Long> conceptId) {
		final NativeSearchQuery searchQuery = new NativeSearchQueryBuilder()
				.withQuery(boolQuery()
						.must(branchCriteria.getEntityBranchCriteria(QueryConcept.class))
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
	
	public List<Long> findDescendantIdsAsUnion(BranchCriteria branchCriteria, boolean stated, Collection<Long> conceptIds) {
		final NativeSearchQuery searchQuery = new NativeSearchQueryBuilder()
				.withQuery(boolQuery()
						.must(branchCriteria.getEntityBranchCriteria(QueryConcept.class))
						.must(termsQuery("ancestors", conceptIds))
						.must(termQuery("stated", stated))
				)
				.withFields(QueryConcept.Fields.CONCEPT_ID)
				.withPageable(LARGE_PAGE)
				.withSort(SortBuilders.fieldSort(QueryConcept.Fields.CONCEPT_ID))// This could be anything
				.build();
		Page<QueryConcept> conceptsPage = elasticsearchTemplate.queryForPage(searchQuery, QueryConcept.class);
		List<Long> conceptIdsFound = conceptsPage.getContent().stream().map(QueryConcept::getConceptIdL).collect(Collectors.toList());
		return new PageImpl<>(conceptIdsFound, LARGE_PAGE, conceptsPage.getTotalElements()).getContent();
	}

	public Set<Long> findConceptIdsInReferenceSet(BranchCriteria branchCriteria, String referenceSetId) {
		return memberService.findConceptsInReferenceSet(branchCriteria, referenceSetId);
	}

	public List<Long> findRelationshipDestinationIds(Collection<Long> sourceConceptIds, List<Long> attributeTypeIds, BranchCriteria branchCriteria, boolean stated) {
		if (!stated) {
			// Use relationships - it's faster
			return relationshipService.findRelationshipDestinationIds(sourceConceptIds, attributeTypeIds, branchCriteria, false);
		}

		// For the stated view we'll use the semantic index to access relationships from both stated relationships or axioms.

		if (attributeTypeIds != null && attributeTypeIds.isEmpty()) {
			return Collections.emptyList();
		}

		BoolQueryBuilder boolQuery = boolQuery()
				.must(branchCriteria.getEntityBranchCriteria(QueryConcept.class))
				.must(termsQuery(QueryConcept.Fields.STATED, stated));

		if (attributeTypeIds != null) {
			BoolQueryBuilder shoulds = boolQuery();
			boolQuery.must(shoulds);
			for (Long attributeTypeId : attributeTypeIds) {
				if (!attributeTypeId.equals(Concepts.IS_A_LONG)) {
					shoulds.should(existsQuery(QueryConcept.Fields.ATTR + "." + attributeTypeId));
				}
			}
		}

		if (sourceConceptIds != null) {
			boolQuery.must(termsQuery(QueryConcept.Fields.CONCEPT_ID, sourceConceptIds));
		}

		NativeSearchQuery query = new NativeSearchQueryBuilder()
				.withQuery(boolQuery)
				.withPageable(LARGE_PAGE)
				.build();

		Set<Long> destinationIds = new LongArraySet();
		try (CloseableIterator<QueryConcept> stream = elasticsearchTemplate.stream(query, QueryConcept.class)) {
			stream.forEachRemaining(queryConcept -> {
				if (attributeTypeIds != null) {
					for (Long attributeTypeId : attributeTypeIds) {
						if (attributeTypeId.equals(Concepts.IS_A_LONG)) {
							destinationIds.addAll(queryConcept.getParents());
						} else {
							queryConcept.getAttr().getOrDefault(attributeTypeId.toString(), Collections.emptySet()).forEach(id -> destinationIds.add(parseLong(id)));
						}
					}
				} else {
					queryConcept.getAttr().values().forEach(destinationSet -> destinationSet.forEach(destinationId -> destinationIds.add(parseLong(destinationId))));
				}
			});
		}

		// Stream search doesn't sort for us
		// Sorting meaningless but supports deterministic pagination
		List<Long> sortedIds = new LongArrayList(destinationIds);
		sortedIds.sort(LongComparators.OPPOSITE_COMPARATOR);
		return sortedIds;
	}

	Page<ConceptMini> findDescendantsAsConceptMinis(String conceptId, String path, Relationship.CharacteristicType form, PageRequest pageRequest) {
		ConceptQueryBuilder queryBuilder = createQueryBuilder(form == Relationship.CharacteristicType.stated);
		queryBuilder.ecl("<" + conceptId);
		return search(queryBuilder, path, pageRequest);
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

	public ConceptQueryBuilder createQueryBuilder(Relationship.CharacteristicType form) {
		return new ConceptQueryBuilder(form == Relationship.CharacteristicType.stated);
	}

	public void joinIsLeafFlag(List<ConceptMini> concepts, String branchPath, Relationship.CharacteristicType form) {
		if (concepts.isEmpty()) {
			return;
		}
		BranchCriteria branchCriteria = versionControlHelper.getBranchCriteria(branchPath);
		Map<Long, ConceptMini> conceptMap = concepts.stream().map(mini -> mini.setLeaf(form, true)).collect(Collectors.toMap(mini -> Long.parseLong(mini.getConceptId()), Function.identity()));
		Set<Long> conceptIdsToFind = new HashSet<>(conceptMap.keySet());
		NativeSearchQueryBuilder queryBuilder = new NativeSearchQueryBuilder()
				.withQuery(new BoolQueryBuilder()
						.must(branchCriteria.getEntityBranchCriteria(QueryConcept.class))
						.must(termQuery(QueryConcept.Fields.STATED, form == Relationship.CharacteristicType.stated))
						.must(termsQuery(QueryConcept.Fields.PARENTS, conceptIdsToFind)))
				.withPageable(LARGE_PAGE);
		try (CloseableIterator<QueryConcept> children = elasticsearchTemplate.stream(queryBuilder.build(), QueryConcept.class)) {
			children.forEachRemaining(child -> {
				if (conceptIdsToFind.isEmpty()) {
					return;
				}
				Set<Long> parents = child.getParents();
				for (Long parent : parents) {
					if (conceptIdsToFind.contains(parent)) {
						// Concept has at least one child in this form - mark as not a leaf.
						conceptMap.get(parent).setLeaf(form, false);
						// We don't need to check this one again
						conceptIdsToFind.remove(parent);
					}
				}
			});
		}
	}

	public final class ConceptQueryBuilder {

		private final BoolQueryBuilder rootBuilder;
		private final BoolQueryBuilder logicalConditionBuilder;
		private final boolean stated;
		private Boolean activeFilter;
		private String definitionStatusFilter;
		private String termMatch;
		private List<String> languageCodes;
		private String ecl;

		private ConceptQueryBuilder(boolean stated) {
			this.stated = stated;
			rootBuilder = boolQuery();
			logicalConditionBuilder = boolQuery();
			rootBuilder.must(termQuery("stated", stated));
			rootBuilder.must(logicalConditionBuilder);
			languageCodes = DEFAULT_LANGUAGE_CODES;
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
		public ConceptQueryBuilder termMatch(String termMatch) {
			if (termMatch != null && termMatch.isEmpty()) {
				termMatch = null;
			}
			this.termMatch = termMatch;
			return this;
		}

		public ConceptQueryBuilder languageCodes(List<String> languageCodes) {
			this.languageCodes = languageCodes;
			return this;
		}

		public ConceptQueryBuilder conceptIds(Set<String> conceptIds) {
			if (conceptIds != null && !conceptIds.isEmpty()) {
				logicalConditionBuilder.should(termsQuery(QueryConcept.Fields.CONCEPT_ID, conceptIds));
			}
			return this;
		}

		public ConceptQueryBuilder activeFilter(Boolean active) {
			this.activeFilter = active;
			return this;
		}

		public ConceptQueryBuilder definitionStatusFilter(String definitionStatusFilter) {
			this.definitionStatusFilter = definitionStatusFilter;
			return this;
		}

		private boolean hasLogicalConditions() {
			return hasRelationshipConditions() ||
					activeFilter != null || definitionStatusFilter != null;
		}

		private boolean hasRelationshipConditions() {
			return getEcl() != null || logicalConditionBuilder.hasClauses();
		}

		private BoolQueryBuilder getRootBuilder() {
			return rootBuilder;
		}

		private String getTermPrefix() {
			return termMatch;
		}

		private List<String> getLanguageCodes() {
			return languageCodes;
		}

		private String getEcl() {
			return ecl;
		}

		private boolean isStated() {
			return stated;
		}

		private Boolean getActiveFilter() {
			return activeFilter;
		}

		private String getDefinitionStatusFilter() {
			return definitionStatusFilter;
		}
	}

}
