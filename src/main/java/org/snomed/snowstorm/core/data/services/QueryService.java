package org.snomed.snowstorm.core.data.services;

import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.google.common.collect.Iterables;
import io.kaicode.elasticvc.api.BranchCriteria;
import io.kaicode.elasticvc.api.VersionControlHelper;
import it.unimi.dsi.fastutil.longs.LongArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.data.services.identifier.IdentifierService;
import org.snomed.snowstorm.core.data.services.pojo.DescriptionCriteria;
import org.snomed.snowstorm.core.data.services.pojo.ResultMapPage;
import org.snomed.snowstorm.core.pojo.BranchTimepoint;
import org.snomed.snowstorm.core.pojo.LanguageDialect;
import org.snomed.snowstorm.core.util.PageHelper;
import org.snomed.snowstorm.core.util.SearchAfterPage;
import org.snomed.snowstorm.core.util.StreamUtil;
import org.snomed.snowstorm.core.util.TimerUtil;
import org.snomed.snowstorm.ecl.ECLQueryService;
import org.snomed.snowstorm.ecl.domain.expressionconstraint.SExpressionConstraint;
import org.snomed.snowstorm.ecl.domain.expressionconstraint.SSubExpressionConstraint;
import org.snomed.snowstorm.rest.pojo.SearchAfterPageRequest;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.core.*;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.query.FetchSourceFilter;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static io.kaicode.elasticvc.api.ComponentService.CLAUSE_LIMIT;
import static io.kaicode.elasticvc.api.ComponentService.LARGE_PAGE;
import static co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders.*;
import static io.kaicode.elasticvc.helper.QueryHelper.*;
import static org.snomed.snowstorm.config.Config.DEFAULT_LANGUAGE_DIALECTS;
import static org.snomed.snowstorm.core.util.SearchAfterQueryHelper.updateQueryWithSearchAfter;
import static org.snomed.snowstorm.ecl.ConceptSelectorHelper.CONCEPT_ID_SEARCH_AFTER_EXTRACTOR;
import static org.snomed.snowstorm.ecl.ConceptSelectorHelper.getDefaultSortForConcept;

/**
 * High level service to query SNOMED CT content using terms, ECL or a combination of both.
 */
@Service
public class QueryService implements ApplicationContextAware {

	static final PageRequest PAGE_OF_ONE = PageRequest.of(0, 1);

	@Autowired
	private ElasticsearchOperations elasticsearchOperations;

	@Autowired
	private VersionControlHelper versionControlHelper;

	@Autowired
	private ECLQueryService eclQueryService;

	@Autowired
	private DescriptionService descriptionService;

	private ConceptService conceptService;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public Page<ConceptMini> eclSearch(String ecl, boolean stated, String branchPath, PageRequest pageRequest) {
		return search(createQueryBuilder(stated).ecl(ecl), branchPath, pageRequest);
	}

	public Page<ConceptMini> search(ConceptQueryBuilder conceptQuery, String branchPath, PageRequest pageRequest) {

		BranchCriteria branchCriteria = versionControlHelper.getBranchCriteria(branchPath);

		String ecl = conceptQuery.getEcl();
		if (ecl != null) {

			// Add concept query params to ECL
			if (conceptQuery.activeFilter != null) {
				ecl = String.format("(%s) {{ C active = %s }}", ecl, conceptQuery.activeFilter ? "true" : "false");
				conceptQuery.ecl = ecl;
			}

			SExpressionConstraint expressionConstraint = (SExpressionConstraint) eclQueryService.createQuery(ecl);
			if (ECLQueryService.isMemberFieldsSearch(expressionConstraint)) {
				pageRequest = updatePageRequestSort(pageRequest, Sort.sort(ReferenceSetMember.class).by(ReferenceSetMember::getMemberId).descending());
				SearchAfterPage<ReferenceSetMember> members = eclQueryService.findReferenceSetMembersWithSpecificFields(ecl, conceptQuery.stated, branchCriteria, pageRequest);
				SSubExpressionConstraint subExpressionConstraint = (SSubExpressionConstraint) expressionConstraint;
				List<String> memberFieldsToReturn = subExpressionConstraint.getMemberFieldsToReturn();

				List<ConceptMini> minis = new ArrayList<>();
				if (memberFieldsToReturn != null) {
					for (ReferenceSetMember member : members) {
						ConceptMini mini = new ConceptMini();
						mini.setActive(member.isActive());
						Map<String, Object> fields = new LinkedHashMap<>();
						for (String fieldName : memberFieldsToReturn) {
							if (fieldName.equals(ReferenceSetMember.Fields.REFERENCED_COMPONENT_ID)) {
								fields.put(fieldName, member.getReferencedComponentId());
							} else {
								fields.put(fieldName, member.getAdditionalField(fieldName));
							}
						}
						fields.put("fields", memberFieldsToReturn);
						mini.setExtraFields(fields);
						minis.add(mini);
					}
				} else {
					List<String> fieldNames = new ArrayList<>();
					Set<String> allFieldNames = new HashSet<>();
					for (ReferenceSetMember member : members) {
						ConceptMini mini = new ConceptMini();
						mini.setActive(member.isActive());
						Map<String, Object> fields = new LinkedHashMap<>();
						fields.put("fields", fieldNames);// Same list for all minis
						allFieldNames.addAll(member.getAdditionalFields().keySet().stream().sorted().toList());
						fields.put(ReferenceSetMember.Fields.REFERENCED_COMPONENT_ID, member.getReferencedComponentId());
						fields.putAll(member.getAdditionalFields());
						mini.setExtraFields(fields);
						minis.add(mini);
					}
					fieldNames.addAll(allFieldNames);
					fieldNames.sort(null);
					fieldNames.add(0, ReferenceSetMember.Fields.REFERENCED_COMPONENT_ID);
				}

				return PageHelper.toSearchAfterPage(minis, members);
			}
		}

		Optional<SearchAfterPage<Long>> conceptIdPageOptional = doSearchForIds(conceptQuery, branchCriteria, pageRequest);

		if (conceptIdPageOptional.isPresent()) {
			SearchAfterPage<Long> conceptIdPage = conceptIdPageOptional.get();
			ResultMapPage<String, ConceptMini> conceptMinis = conceptService.findConceptMinis(branchCriteria, conceptIdPage.getContent(), conceptQuery.getResultLanguageDialects());
			List<ConceptMini> conceptMinisSorted = sortConceptMinisByTermOrder(conceptIdPage.getContent(), conceptMinis.getResultsMap());
			return PageHelper.toSearchAfterPage(conceptMinisSorted, conceptIdPage);
		} else {
			// No ids - return page of all concepts
			ResultMapPage<String, ConceptMini> conceptMinis = conceptService.findConceptMinis(branchCriteria, conceptQuery.getResultLanguageDialects(), pageRequest);
			return new PageImpl<>(new ArrayList<>(conceptMinis.getResultsMap().values()), pageRequest, conceptMinis.getTotalElements());
		}
	}

	private PageRequest updatePageRequestSort(PageRequest pageRequest, Sort newSort) {
		if (pageRequest instanceof SearchAfterPageRequest searchAfterPageRequest) {
			return SearchAfterPageRequest.of(searchAfterPageRequest.getSearchAfter(), searchAfterPageRequest.getPageSize(), newSort);
		}
		return PageRequest.of(pageRequest.getPageNumber(), pageRequest.getPageSize(), newSort);
	}

	public SearchAfterPage<Long> searchForIds(ConceptQueryBuilder conceptQuery, String branchPath, PageRequest pageRequest) {
		BranchCriteria branchCriteria = versionControlHelper.getBranchCriteria(branchPath);
		return searchForIds(conceptQuery, branchCriteria, pageRequest);
	}

	public SearchAfterPage<Long> searchForIds(ConceptQueryBuilder conceptQuery, BranchCriteria branchCriteria, PageRequest pageRequest) {
		Optional<SearchAfterPage<Long>> conceptIdPageOptional = doSearchForIds(conceptQuery, branchCriteria, pageRequest);
		return conceptIdPageOptional.orElseGet(() -> {
			// No ids - return page of all concept ids
			NativeQuery query = new NativeQueryBuilder()
					.withQuery(branchCriteria.getEntityBranchCriteria(Concept.class))
					.withPageable(pageRequest)
					.build();
			query.setTrackTotalHits(true);
			updateQueryWithSearchAfter(query, pageRequest);
			SearchHits<Concept> searchHits = elasticsearchOperations.search(query, Concept.class);
			return PageHelper.toSearchAfterPage(searchHits, Concept::getConceptIdAsLong, pageRequest);
		});
	}

	private Optional<SearchAfterPage<Long>> doSearchForIds(ConceptQueryBuilder conceptQuery, BranchCriteria branchCriteria, PageRequest pageRequest) {

		// Validate Lexical criteria
		DescriptionCriteria descriptionCriteria = conceptQuery.getDescriptionCriteria();
		String term = descriptionCriteria.getTerm();
		if (IdentifierService.isConceptId(term)) {
			conceptQuery.conceptIds(Collections.singleton(term));
			term = null;
			descriptionCriteria.term(null);
		}
		boolean hasLexicalCriteria = descriptionCriteria.hasDescriptionCriteria();
		boolean hasLogicalConditions = conceptQuery.hasLogicalConditions();

		if (!hasLogicalConditions && !hasLexicalCriteria) {
			return Optional.empty();
		}

		SearchAfterPage<Long> conceptIdPage;
		if (hasLogicalConditions && !hasLexicalCriteria) {
			// Logical Only

			if (conceptQuery.getEcl() != null) {
				// ECL search
				conceptIdPage = doEclSearchAndConceptPropertyFilters(conceptQuery, pageRequest, branchCriteria);
			} else {
				// Concept id search
				Query conceptBoolQuery = getSearchByConceptIdQuery(conceptQuery, branchCriteria);
				pageRequest = updatePageRequestSort(pageRequest, Sort.sort(Concept.class).by(Concept::getConceptId).descending());
				NativeQuery query = new NativeQueryBuilder()
						.withQuery(conceptBoolQuery)
						.withSourceFilter(new FetchSourceFilter(new String[]{Concept.Fields.CONCEPT_ID}, null))
						.withPageable(pageRequest)
						.build();
				query.setTrackTotalHits(true);
				updateQueryWithSearchAfter(query, pageRequest);
				SearchHits<Concept> searchHits = elasticsearchOperations.search(query, Concept.class);
				conceptIdPage = PageHelper.toSearchAfterPage(searchHits, Concept::getConceptIdAsLong, pageRequest);
			}

		} else {
			// Logical and Lexical

			// Perform lexical search first because this probably the smaller set.
			// We fetch all lexical results then use them to filter the logical matches and for ordering of the final results.
			logger.info("Lexical search before logical {}", term);
			TimerUtil timer = new TimerUtil("Lexical and Logical Search");
			// Convert Set of String to set of Long
			Set<Long> conceptIds = Collections.emptySet();
			if (!CollectionUtils.isEmpty(conceptQuery.getConceptIds())) {
				conceptIds = conceptQuery.getConceptIds().stream()
						.map(Long::parseLong)
						.collect(Collectors.toSet());
			}
			final Collection<Long> allConceptIdsSortedByTermOrder = descriptionService.findDescriptionAndConceptIds(descriptionCriteria, conceptIds, branchCriteria, timer).getMatchedConceptIds();
			timer.checkpoint("lexical complete");

			// Fetch Logical matches
			// ECL, QueryConcept and Concept searches are filtered by the conceptIds gathered from the lexical search
			List<Long> allFilteredLogicalMatches;
			if (conceptQuery.getEcl() != null) {
				List<Long> eclMatches = doEclSearch(conceptQuery, branchCriteria, allConceptIdsSortedByTermOrder);
				allFilteredLogicalMatches = applyConceptPropertyFilters(eclMatches, conceptQuery, branchCriteria, new LongArrayList());
			} else {
				allFilteredLogicalMatches = new LongArrayList();
				Query conceptBoolQuery = getSearchByConceptIdQuery(conceptQuery, branchCriteria);
				try (SearchHitsIterator<Concept> stream = elasticsearchOperations.searchForStream(new NativeQueryBuilder()
						.withQuery(conceptBoolQuery)
						.withFilter(termsQuery(Concept.Fields.CONCEPT_ID, allConceptIdsSortedByTermOrder))
						.withSourceFilter(new FetchSourceFilter(new String[]{Concept.Fields.CONCEPT_ID}, null))
						.withPageable(LARGE_PAGE)
						.build(), Concept.class)) {
					stream.forEachRemaining(hit -> allFilteredLogicalMatches.add(hit.getContent().getConceptIdAsLong()));
				}
			}

			timer.checkpoint("filtered logical complete");

			logger.info("{} lexical results, {} logical results", allConceptIdsSortedByTermOrder.size(), allFilteredLogicalMatches.size());

			// Create page of ids which is an intersection of the lexical and logical lists using the lexical ordering
			conceptIdPage = PageHelper.listIntersection(new ArrayList<>(allConceptIdsSortedByTermOrder), allFilteredLogicalMatches, pageRequest, CONCEPT_ID_SEARCH_AFTER_EXTRACTOR);
		}

		if (conceptIdPage != null) {
			return Optional.of(conceptIdPage);
		} else {
			return Optional.empty();
		}
	}

	private Query getSearchByConceptIdQuery(ConceptQueryBuilder conceptQuery, BranchCriteria branchCriteria) {
		BoolQuery.Builder queryBuilder = bool()
				.must(branchCriteria.getEntityBranchCriteria(Concept.class));

		Set<String> conceptIds = conceptQuery.getConceptIds();
		if (conceptIds != null && !conceptIds.isEmpty()) {
			queryBuilder.filter(termsQuery(Concept.Fields.CONCEPT_ID, conceptIds));
		}
		if (conceptQuery.getActiveFilter() != null) {
			queryBuilder.must(termQuery(SnomedComponent.Fields.ACTIVE, conceptQuery.getActiveFilter()));
		}
		if (conceptQuery.getDefinitionStatusFilter() != null) {
			queryBuilder.must(termQuery(Concept.Fields.DEFINITION_STATUS_ID, conceptQuery.getDefinitionStatusFilter()));
		}
		if (conceptQuery.getModule() != null) {
			queryBuilder.must(termsQuery(SnomedComponent.Fields.MODULE_ID, conceptQuery.getModule()));
		}
		if (conceptQuery.getEffectiveTime() !=null) {
			queryBuilder.must(termQuery(SnomedComponent.Fields.EFFECTIVE_TIME, conceptQuery.getEffectiveTime()));
		}
		if (conceptQuery.isNullEffectiveTime() !=null) {
			if (conceptQuery.isNullEffectiveTime()) {
				queryBuilder.mustNot(existsQuery(SnomedComponent.Fields.EFFECTIVE_TIME));
			} else {
				queryBuilder.must(existsQuery(SnomedComponent.Fields.EFFECTIVE_TIME));
			}
		}
		if (conceptQuery.isReleased() != null) {
			queryBuilder.must(termQuery(SnomedComponent.Fields.RELEASED, conceptQuery.isReleased()));
		}
		return queryBuilder.build()._toQuery();
	}

	private <C extends Collection<Long>> C applyConceptPropertyFilters(C conceptIds, ConceptQueryBuilder queryBuilder, BranchCriteria branchCriteria, C filteredConceptIds) {
		if (!queryBuilder.hasPropertyFilter()) {
			filteredConceptIds.addAll(conceptIds);
			return filteredConceptIds;
		}

		for (List<Long> batch : Iterables.partition(conceptIds, CLAUSE_LIMIT)) {
			final BoolQuery.Builder conceptClauses = bool();
			queryBuilder.applyConceptClauses(conceptClauses);
			NativeQueryBuilder conceptDefinitionQuery = new NativeQueryBuilder()
					.withQuery(bool(bq -> bq
							.must(branchCriteria.getEntityBranchCriteria(Concept.class))
							.must(conceptClauses.build()._toQuery())))
					.withFilter(termsQuery(Concept.Fields.CONCEPT_ID, batch))
					.withSourceFilter(new FetchSourceFilter(new String[]{Concept.Fields.CONCEPT_ID}, null))
					.withSort(getDefaultSortForConcept())
					.withPageable(LARGE_PAGE);

			try (SearchHitsIterator<Concept> stream = elasticsearchOperations.searchForStream(conceptDefinitionQuery.build(), Concept.class)) {
				stream.forEachRemaining(hit -> filteredConceptIds.add(hit.getContent().getConceptIdAsLong()));
			}
		}
		return filteredConceptIds;
	}

	private SearchAfterPage<Long> doEclSearchAndConceptPropertyFilters(ConceptQueryBuilder conceptQuery, PageRequest pageRequest, BranchCriteria branchCriteria) {
		String ecl = conceptQuery.getEcl();
		logger.debug("ECL Search {}", ecl);
		Collection<Long> conceptIdFilter = null;
		if (conceptQuery.conceptIds != null && !conceptQuery.conceptIds.isEmpty()) {
			conceptIdFilter = conceptQuery.conceptIds.stream().map(Long::valueOf).collect(Collectors.toSet());
		}
		if (conceptQuery.hasPropertyFilter()) {
			Page<Long> allConceptIds = eclQueryService.selectConceptIds(ecl, branchCriteria, conceptQuery.isStated(), conceptIdFilter, null);
			List<Long> filteredConceptIds = applyConceptPropertyFilters(allConceptIds.getContent(), conceptQuery, branchCriteria, new LongArrayList());
			return PageHelper.fullListToPage(filteredConceptIds, pageRequest, CONCEPT_ID_SEARCH_AFTER_EXTRACTOR);
		} else {
			Page<Long> conceptIds = eclQueryService.selectConceptIds(ecl, branchCriteria, conceptQuery.isStated(), conceptIdFilter, pageRequest);
			return PageHelper.toSearchAfterPage(conceptIds, CONCEPT_ID_SEARCH_AFTER_EXTRACTOR);
		}
	}

	private List<Long> doEclSearch(ConceptQueryBuilder conceptQuery, BranchCriteria branchCriteria, Collection<Long> conceptIdFilter) {
		String ecl = conceptQuery.getEcl();
		logger.debug("ECL Search {}", ecl);
		return eclQueryService.selectConceptIds(ecl, branchCriteria, conceptQuery.isStated(), conceptIdFilter).getContent();
	}

	private List<ConceptMini> sortConceptMinisByTermOrder(List<Long> termConceptIds, Map<String, ConceptMini> conceptMiniMap) {
		return termConceptIds.stream().filter(id -> conceptMiniMap.containsKey(id.toString())).map(id -> conceptMiniMap.get(id.toString())).collect(Collectors.toList());
	}

	public Set<Long> findAncestorIds(String conceptId, String path, boolean stated) {
		return findAncestorIds(versionControlHelper.getBranchCriteria(path), path, stated, conceptId);
	}

	public Set<Long> findAncestorIds(BranchCriteria branchCriteria, String path, boolean stated, String conceptId) {
		final NativeQuery searchQuery = new NativeQueryBuilder()
				.withQuery(bool(b -> b
						.must(branchCriteria.getEntityBranchCriteria(QueryConcept.class))
						.must(termQuery(QueryConcept.Fields.CONCEPT_ID, conceptId))
						.must(termQuery(QueryConcept.Fields.STATED, stated)))
				)
				.withPageable(LARGE_PAGE)
				.build();
		final List<QueryConcept> concepts = elasticsearchOperations.search(searchQuery, QueryConcept.class)
				.stream().map(SearchHit::getContent).collect(Collectors.toList());
		if (concepts.size() > 1) {
			logger.error("More than one index concept found {}", concepts);
			throw new IllegalStateException("More than one query-index-concept found for id " + conceptId + " on branch " + path + ".");
		}
		return !concepts.isEmpty() ? concepts.get(0).getAncestors() : Collections.emptySet();
	}

	public Set<Long> findAncestorIdsAsUnion(BranchCriteria branchCriteria, boolean stated, Collection<Long> conceptId) {
		final NativeQuery searchQuery = new NativeQueryBuilder()
				.withQuery(bool(b -> b
						.must(branchCriteria.getEntityBranchCriteria(QueryConcept.class))
						.must(termsQuery(QueryConcept.Fields.CONCEPT_ID, conceptId))
						.must(termQuery(QueryConcept.Fields.STATED, stated)))
				)
				.withPageable(LARGE_PAGE)
				.build();
		try (SearchHitsIterator<QueryConcept> stream = elasticsearchOperations.searchForStream(searchQuery, QueryConcept.class)) {
			final List<QueryConcept> concepts = stream.stream().map(SearchHit::getContent).toList();
			Set<Long> allAncestors = new HashSet<>();
			for (QueryConcept concept : concepts) {
				allAncestors.addAll(concept.getAncestors());
			}
			return allAncestors;
		}
	}

	public Set<Long> findParentIdsAsUnion(BranchCriteria branchCriteria, boolean stated, Collection<Long> conceptId) {
		final NativeQuery searchQuery = new NativeQueryBuilder()
				.withQuery(bool(b -> b
						.must(branchCriteria.getEntityBranchCriteria(QueryConcept.class))
						.must(termsQuery(QueryConcept.Fields.CONCEPT_ID, conceptId))
						.must(termQuery(QueryConcept.Fields.STATED, stated)))
				)
				.withPageable(LARGE_PAGE)
				.build();
		final List<QueryConcept> concepts = elasticsearchOperations.search(searchQuery, QueryConcept.class)
				.stream().map(SearchHit::getContent).toList();
		Set<Long> allParents = new HashSet<>();
		for (QueryConcept concept : concepts) {
			allParents.addAll(concept.getParents());
		}
		return allParents;
	}

	public Set<Long> findDescendantIdsAsUnion(BranchCriteria branchCriteria, boolean stated, Collection<Long> conceptIds) {
		final NativeQuery searchQuery = new NativeQueryBuilder()
				.withQuery(bool(b -> b
						.must(branchCriteria.getEntityBranchCriteria(QueryConcept.class))
						.must(termsQuery(QueryConcept.Fields.ANCESTORS, conceptIds))
						.must(termQuery(QueryConcept.Fields.STATED, stated)))
				)
				.withSourceFilter(new FetchSourceFilter(new String[]{QueryConcept.Fields.CONCEPT_ID}, null))
				.withPageable(LARGE_PAGE)
				.withSort(SortOptions.of(sb -> sb.field(fs -> fs.field(QueryConcept.Fields.CONCEPT_ID))))// This could be anything
				.build();
		try (SearchHitsIterator<QueryConcept> searchHits = elasticsearchOperations.searchForStream(searchQuery, QueryConcept.class)) {
			return searchHits.stream().map(SearchHit::getContent).map(QueryConcept::getConceptIdL).collect(Collectors.toSet());
		}
	}

	public Set<Long> findChildrenIdsAsUnion(BranchCriteria branchCriteria, boolean stated, Collection<Long> conceptIds) {
		final NativeQuery searchQuery = new NativeQueryBuilder()
				.withQuery(bool(b -> b
						.must(branchCriteria.getEntityBranchCriteria(QueryConcept.class))
						.must(termsQuery(QueryConcept.Fields.PARENTS, conceptIds))
						.must(termQuery(QueryConcept.Fields.STATED, stated)))
				)
				.withSourceFilter(new FetchSourceFilter(new String[]{QueryConcept.Fields.CONCEPT_ID}, null))
				.withPageable(LARGE_PAGE)
				.withSort(SortOptions.of(sb -> sb.field(fs -> fs.field(QueryConcept.Fields.CONCEPT_ID))))// This could be anything
				.build();
		try (SearchHitsIterator<QueryConcept> searchHits = elasticsearchOperations.searchForStream(searchQuery, QueryConcept.class)) {
			return searchHits.stream().map(SearchHit::getContent).map(QueryConcept::getConceptIdL).collect(Collectors.toSet());
		}
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

	public void joinIsLeafFlag(List<ConceptMini> concepts, Relationship.CharacteristicType form, BranchCriteria branchCriteria, String branch) throws ServiceException {
		if (concepts.isEmpty()) {
			return;
		}
		Map<Long, ConceptMini> conceptMap;
		try {
			conceptMap = concepts.stream().map(mini -> mini.setLeaf(form, true))
					.collect(Collectors.toMap(mini -> Long.parseLong(mini.getConceptId()), Function.identity(), StreamUtil.MERGE_FUNCTION));
		} catch (IllegalStateException e) {
			throw new ServiceException(String.format("Multiple documents found with the same conceptId '%s' on branch %s", e.getMessage(), branch), e);
		}
		Set<Long> conceptIdsToFind = new HashSet<>(conceptMap.keySet());
		NativeQueryBuilder queryBuilder = new NativeQueryBuilder()
				.withQuery(bool(b -> b
						.must(branchCriteria.getEntityBranchCriteria(QueryConcept.class))
						.must(termQuery(QueryConcept.Fields.STATED, form == Relationship.CharacteristicType.stated))
						.must(termsQuery(QueryConcept.Fields.PARENTS, conceptIdsToFind))))
				.withPageable(LARGE_PAGE);
		try (SearchHitsIterator<QueryConcept> children = elasticsearchOperations.searchForStream(queryBuilder.build(), QueryConcept.class)) {
			children.forEachRemaining(hit -> {
				if (conceptIdsToFind.isEmpty()) {
					return;
				}
				Set<Long> parents = hit.getContent().getParents();
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

	public void joinDescendantCountAndLeafFlag(Collection<ConceptMini> concepts, Relationship.CharacteristicType form, BranchCriteria branchCriteria) {
		if (concepts.isEmpty()) {
			return;
		}

		for (ConceptMini concept : concepts) {
			SearchAfterPage<Long> page = searchForIds(createQueryBuilder(form).ecl("<" + concept.getId()), branchCriteria, PAGE_OF_ONE);
			concept.setDescendantCount(page.getTotalElements());
			concept.setLeaf(form, page.getTotalElements() == 0);
		}
	}

	public void joinDescendantCount(Concept concept, Relationship.CharacteristicType form, List<LanguageDialect> languageDialects, BranchTimepoint branchTimepoint) {
		if (concept == null) {
			return;
		}
		BranchCriteria branchCriteria = conceptService.getBranchCriteria(branchTimepoint);
		ConceptMini mini = new ConceptMini(concept, languageDialects);
		joinDescendantCountAndLeafFlag(Collections.singleton(mini), form, branchCriteria);
		concept.setDescendantCount(mini.getDescendantCount());
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		conceptService = applicationContext.getBean(ConceptService.class);
	}

	public static final class ConceptQueryBuilder {

		private final boolean stated;
		private Boolean activeFilter;
		private String definitionStatusFilter;
		private Set<Long> module;
		private List<LanguageDialect> resultLanguageDialects = DEFAULT_LANGUAGE_DIALECTS;
		private String ecl;
		private Set<String> conceptIds;
		private final DescriptionCriteria descriptionCriteria;
		private Integer effectiveTime;
		private Boolean isNullEffectiveTime;
		private Boolean isReleased;

		private ConceptQueryBuilder(boolean stated) {
			this.stated = stated;
			this.descriptionCriteria = new DescriptionCriteria();
		}

		public boolean hasPropertyFilter() {
			return definitionStatusFilter != null || module != null || effectiveTime != null
					|| isNullEffectiveTime != null || isReleased != null;
		}

		private boolean hasLogicalConditions() {
			return ecl != null || activeFilter != null || effectiveTime != null || isReleased != null || definitionStatusFilter != null || conceptIds != null || module != null;
		}

		public ConceptQueryBuilder ecl(String ecl) {
			this.ecl = ecl;
			return this;
		}

		public ConceptQueryBuilder effectiveTime(Integer effectiveTime) {
			this.effectiveTime = effectiveTime;
			return this;
		}
		
		public ConceptQueryBuilder isNullEffectiveTime(Boolean isNullEffectiveTime) {
			this.isNullEffectiveTime = isNullEffectiveTime;
			return this;
		}
		
		public ConceptQueryBuilder isReleased(Boolean isReleased) {
			this.isReleased = isReleased;
			return this;
		}
		
		public ConceptQueryBuilder conceptIds(Set<String> conceptIds) {
			if (conceptIds != null && !conceptIds.isEmpty()) {
				this.conceptIds = conceptIds;
			}
			return this;
		}

		public ConceptQueryBuilder activeFilter(Boolean active) {
			this.activeFilter = active;
			return this;
		}

		public ConceptQueryBuilder module(Long module) {
			if (module != null) {
				this.module(Collections.singleton(module));
			} else {
				this.module = null;
			}
			return this;
		}

		public ConceptQueryBuilder module(Collection<Long> module) {
			if (module != null) {
				this.module = new HashSet<>(module);
			} else {
				this.module = null;
			}
			return this;
		}

		public ConceptQueryBuilder resultLanguageDialects(List<LanguageDialect> resultLanguageDialects) {
			this.resultLanguageDialects = resultLanguageDialects;
			return this;
		}

		public ConceptQueryBuilder definitionStatusFilter(String definitionStatusFilter) {
			this.definitionStatusFilter = definitionStatusFilter;
			return this;
		}

		public ConceptQueryBuilder descriptionCriteria(Consumer<DescriptionCriteria> descriptionCriteriaUpdater) {
			descriptionCriteriaUpdater.accept(descriptionCriteria);
			return this;
		}

		ConceptQueryBuilder descriptionTerm(String term) {
			descriptionCriteria.term(term);
			return this;
		}

		private String getEcl() {
			return ecl;
		}

		private Set<String> getConceptIds() {
			return conceptIds;
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

		public Set<Long> getModule() {
			return module;
		}

		private List<LanguageDialect> getResultLanguageDialects() {
			return resultLanguageDialects;
		}

		public DescriptionCriteria getDescriptionCriteria() {
			return descriptionCriteria;
		}
		
		public Integer getEffectiveTime() {
			return this.effectiveTime;
		}
		
		public Boolean isNullEffectiveTime() {
			return this.isNullEffectiveTime;
		}
		
		public Boolean isReleased() {
			return isReleased;
		}

		public void applyConceptClauses(BoolQuery.Builder conceptClauses) {
			if (activeFilter != null) {
				conceptClauses.must(termQuery(Concept.Fields.ACTIVE, activeFilter));
			}
			if (definitionStatusFilter != null) {
				conceptClauses.must(termQuery(Concept.Fields.DEFINITION_STATUS_ID, definitionStatusFilter));
			}
			if (module != null) {
				conceptClauses.must(termsQuery(SnomedComponent.Fields.MODULE_ID, module));
			}
			if (effectiveTime != null) {
				conceptClauses.must(termQuery(SnomedComponent.Fields.EFFECTIVE_TIME, effectiveTime));
			}
			if (isNullEffectiveTime != null) {
				if (isNullEffectiveTime) {
					conceptClauses.mustNot(existsQuery(SnomedComponent.Fields.EFFECTIVE_TIME));
				} else {
					conceptClauses.must(existsQuery(SnomedComponent.Fields.EFFECTIVE_TIME));
				}
			}
			if (isReleased != null) {
				conceptClauses.must(termQuery(SnomedComponent.Fields.RELEASED, isReleased));
			}
		}

		@Override
		public String toString() {
			return "ConceptQueryBuilder{" +
					"stated=" + stated +
					", activeFilter=" + activeFilter +
					", definitionStatusFilter='" + definitionStatusFilter + '\'' +
					", module=" + module +
					", resultLanguageDialects=" + resultLanguageDialects +
					", ecl='" + ecl + '\'' +
					", conceptIds=" + conceptIds +
					", descriptionCriteria=" + descriptionCriteria +
					", effectiveTime=" + effectiveTime +
					", isNullEffectiveTime=" + isNullEffectiveTime +
					", isReleased=" + isReleased +
					'}';
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;
			ConceptQueryBuilder that = (ConceptQueryBuilder) o;
			return stated == that.stated &&
					Objects.equals(activeFilter, that.activeFilter) &&
					Objects.equals(definitionStatusFilter, that.definitionStatusFilter) &&
					Objects.equals(module, that.module) &&
					Objects.equals(resultLanguageDialects, that.resultLanguageDialects) &&
					Objects.equals(ecl, that.ecl) &&
					Objects.equals(conceptIds, that.conceptIds) &&
					Objects.equals(descriptionCriteria, that.descriptionCriteria) &&
					Objects.equals(effectiveTime, that.effectiveTime) &&
					Objects.equals(isNullEffectiveTime, that.isNullEffectiveTime) &&
					Objects.equals(isReleased, that.isReleased);
		}

		@Override
		public int hashCode() {
			return Objects.hash(stated, activeFilter, definitionStatusFilter, module, resultLanguageDialects, ecl,
					conceptIds, descriptionCriteria, effectiveTime, isNullEffectiveTime, isReleased);
		}
	}

}
