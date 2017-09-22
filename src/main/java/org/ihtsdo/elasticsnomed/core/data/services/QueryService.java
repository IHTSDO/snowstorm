package org.ihtsdo.elasticsnomed.core.data.services;

import io.kaicode.elasticvc.api.VersionControlHelper;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.ihtsdo.elasticsnomed.core.data.domain.ConceptMini;
import org.ihtsdo.elasticsnomed.core.data.domain.Concepts;
import org.ihtsdo.elasticsnomed.core.data.domain.Description;
import org.ihtsdo.elasticsnomed.core.data.domain.QueryConcept;
import org.ihtsdo.elasticsnomed.core.data.repositories.QueryConceptRepository;
import org.ihtsdo.elasticsnomed.core.data.services.pojo.ResultMapPage;
import org.ihtsdo.elasticsnomed.core.util.CollectionUtil;
import org.ihtsdo.elasticsnomed.ecl.ECLQueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

	public static final PageRequest PAGE_OF_ONE = new PageRequest(0, 1);

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

		List<Long> pageOfConceptIds = null;
		long resultTotalElements = 0;

		// Fetch Logical matches
		List<Long> allLogicalMatches = null;
		if (conceptQuery.hasLogicalConditions()) {
			if (conceptQuery.getEcl() != null) {
				String ecl = conceptQuery.getEcl();
				logger.info("ECL Search {}", ecl);
				allLogicalMatches = eclQueryService.selectConceptIds(ecl, branchCriteria, branchPath, conceptQuery.isStated());
			} else {
				logger.info("Primitive Logical Search ");
				NativeSearchQueryBuilder logicalSearchQuery = new NativeSearchQueryBuilder()
						.withQuery(boolQuery()
								.must(branchCriteria)
								.must(conceptQuery.getRootBuilder())
						)
						.withPageable(LARGE_PAGE);
				final List<Long> matches = new LongArrayList();
				try (CloseableIterator<QueryConcept> stream = elasticsearchTemplate.stream(logicalSearchQuery.build(), QueryConcept.class)) {
					stream.forEachRemaining(c -> matches.add(c.getConceptId()));
				}
				allLogicalMatches = matches;
			}

			pageOfConceptIds = CollectionUtil.subList(allLogicalMatches, pageRequest.getPageNumber(), pageRequest.getPageSize());
			resultTotalElements = allLogicalMatches.size();
		}

		// Fetch ordered Lexical matches - filtered by Logical matches if criteria given
		if (hasLexicalCriteria) {
			logger.info("Lexical search {}", term);
			NativeSearchQuery query = getLexicalQuery(term, allLogicalMatches, branchCriteria, pageRequest);
			Page<Description> descriptionPage = elasticsearchTemplate.queryForPage(query, Description.class);
			final List<Long> pageOfIds = new LongArrayList();
			descriptionPage.getContent().forEach(d -> pageOfIds.add(parseLong(d.getConceptId())));

			pageOfConceptIds = pageOfIds;
			resultTotalElements = descriptionPage.getTotalElements();
		}

		if (pageOfConceptIds != null) {
			ResultMapPage<String, ConceptMini> conceptMinis = conceptService.findConceptMinis(branchCriteria, pageOfConceptIds);
			return new PageImpl<>(getOrderedConceptList(pageOfConceptIds, conceptMinis.getResultsMap()), pageRequest, resultTotalElements);
		} else {
			ResultMapPage<String, ConceptMini> conceptMinis = conceptService.findConceptMinis(branchCriteria, pageRequest);
			return new PageImpl<>(new ArrayList<>(conceptMinis.getResultsMap().values()), pageRequest, conceptMinis.getTotalElements());
		}
	}

	private NativeSearchQuery getLexicalQuery(String term, List<Long> allLogicalMatches, QueryBuilder branchCriteria, PageRequest pageable) {
		BoolQueryBuilder lexicalQuery = boolQuery()
				.must(branchCriteria)
				.must(termQuery("active", true))
				.must(termQuery("typeId", Concepts.FSN));
		DescriptionService.addTermClauses(term, lexicalQuery);
		NativeSearchQueryBuilder queryBuilder = new NativeSearchQueryBuilder()
				.withQuery(lexicalQuery)
				.withPageable(pageable);
		if (allLogicalMatches != null) {
			queryBuilder.withFilter(termsQuery(Description.Fields.CONCEPT_ID, allLogicalMatches));
		}
		NativeSearchQuery query = queryBuilder.build();
		DescriptionService.addTermSort(query);
		return query;
	}

	private List<ConceptMini> getOrderedConceptList(List<Long> termConceptIds, Map<String, ConceptMini> conceptMiniMap) {
		return termConceptIds.stream().filter(id -> conceptMiniMap.keySet().contains(id.toString())).map(id -> conceptMiniMap.get(id.toString())).collect(Collectors.toList());
	}

	public CloseableIterator<QueryConcept> stream(NativeSearchQuery searchQuery) {
		return elasticsearchTemplate.stream(searchQuery, QueryConcept.class);
	}

	public Set<Long> retrieveAncestors(String conceptId, String path, boolean stated) {
		return retrieveAncestors(versionControlHelper.getBranchCriteria(path), path, stated, conceptId);
	}

	public Set<Long> retrieveParents(QueryBuilder branchCriteria, String path, boolean stated, String conceptId) {
		final NativeSearchQuery searchQuery = new NativeSearchQueryBuilder()
				.withQuery(boolQuery()
						.must(branchCriteria)
						.must(termQuery("conceptId", conceptId))
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
						.must(termQuery("conceptId", conceptId))
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
						.must(termsQuery("conceptId", conceptId))
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

	public Set<Long> retrieveDescendants(String conceptId, QueryBuilder branchCriteria, boolean stated) {
		return retrieveAllDescendants(branchCriteria, stated, Collections.singleton(conceptId));
	}

	public Set<Long> retrieveAllDescendants(QueryBuilder branchCriteria, boolean stated, Collection<? extends Object> conceptIds) {
		final NativeSearchQuery searchQuery = new NativeSearchQueryBuilder()
				.withQuery(boolQuery()
						.must(branchCriteria)
						.must(termsQuery("ancestors", conceptIds))
						.must(termQuery("stated", stated))
				)
				.withPageable(LARGE_PAGE)
				.build();
		final List<QueryConcept> concepts = elasticsearchTemplate.queryForPage(searchQuery, QueryConcept.class).getContent();
		return concepts.stream().map(QueryConcept::getConceptId).collect(Collectors.toSet());
	}

	public void deleteAll() {
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
		private String termPrefix;
		private String ecl;

		private ConceptQueryBuilder(boolean stated) {
			this.stated = stated;
			rootBuilder = boolQuery();
			logicalConditionBuilder = boolQuery();
			rootBuilder.must(termQuery("stated", stated));
			rootBuilder.must(logicalConditionBuilder);
		}

		public ConceptQueryBuilder self(Long conceptId) {
			logger.info("conceptId = {}", conceptId);
			logicalConditionBuilder.should(termQuery("conceptId", conceptId));
			return this;
		}

		public ConceptQueryBuilder descendant(Long conceptId) {
			logger.info("ancestors = {}", conceptId);
			if (parseLong(Concepts.SNOMEDCT_ROOT) == conceptId) {
				// Ignore this criteria because it is not meaningful
				return this;
			}
			logicalConditionBuilder.should(termQuery("ancestors", conceptId));
			return this;
		}

		public ConceptQueryBuilder selfOrDescendant(Long conceptId) {
			if (parseLong(Concepts.SNOMEDCT_ROOT) == conceptId) {
				// Ignore this criteria because it is not meaningful
				return this;
			}
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
		 *
		 * @param termPrefix
		 */
		public ConceptQueryBuilder termPrefix(String termPrefix) {
			this.termPrefix = termPrefix;
			return this;
		}

		private BoolQueryBuilder getRootBuilder() {
			return rootBuilder;
		}

		private String getTermPrefix() {
			return termPrefix;
		}

		public String getEcl() {
			return ecl;
		}

		public boolean isStated() {
			return stated;
		}

		private boolean hasLogicalConditions() {
			return getEcl() != null || logicalConditionBuilder.hasClauses();
		}
	}

}