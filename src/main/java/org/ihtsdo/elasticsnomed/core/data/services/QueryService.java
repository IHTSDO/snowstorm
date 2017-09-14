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
import org.ihtsdo.elasticsnomed.core.util.CollectionUtil;
import org.ihtsdo.elasticsnomed.ecl.ECLQueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
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

	public List<ConceptMini> search(ConceptQueryBuilder conceptQuery, String branchPath, int pageSize) {
		QueryBuilder branchCriteria = versionControlHelper.getBranchCriteria(branchPath);

		boolean logicalCriteria = conceptQuery.hasLogicalConditions();

		String term = conceptQuery.getTermPrefix();
		boolean lexicalCriteria;
		if (term != null) {
			if (term.length() < 3) {
				return Collections.emptyList();
			}
			lexicalCriteria = true;
		} else {
			lexicalCriteria = false;
		}

		if (lexicalCriteria && !logicalCriteria) {
			// Simple term search.
			// Return a page of results straight away
			logger.info("Lexical only search {}", term);
			NativeSearchQuery query = new NativeSearchQueryBuilder()
					.withQuery(getLexicalQuery(branchCriteria, term))
					.withPageable(new PageRequest(0, pageSize))
					.build();
			DescriptionService.addTermSort(query);

			List<Long> conceptIds = new ArrayList<>();
			List<Description> descriptions = elasticsearchTemplate.queryForPage(query, Description.class).getContent();
			descriptions.forEach(d -> conceptIds.add(parseLong(d.getConceptId())));
			logger.info("Gather minis");
			Map<String, ConceptMini> conceptMiniMap = conceptService.findConceptMinis(branchCriteria, conceptIds);
			return getOrderedConceptList(conceptIds, conceptMiniMap);
		}

		final List<Long> allLexicalMatchesWithOrdering = new LongArrayList();
		if (lexicalCriteria) {
			// Perform term search to get results ordering.
			// Iterate through Pages rather than opening Stream because Stream has no ordering.
			logger.info("Lexical search before logical {}", term);
			Page<Description> page;
			int pageNumber = 0;
			do {
				NativeSearchQuery query = new NativeSearchQueryBuilder()
						.withQuery(getLexicalQuery(branchCriteria, term))
						.withPageable(new PageRequest(pageNumber, LARGE_PAGE.getPageSize()))
						.build();
				DescriptionService.addTermSort(query);

				page = elasticsearchTemplate.queryForPage(query, Description.class);
				allLexicalMatchesWithOrdering.addAll(page.getContent().stream().map(d -> parseLong(d.getConceptId())).collect(Collectors.toList()));
				pageNumber++;
			} while (!page.isLast());
		}

		if (logicalCriteria) {
			// Perform logical search. If we have a lexical criteria filter logical results by term matches. Sort order will be restored at the end.
			List<Long> allLogicalMatches;

			if (conceptQuery.getEcl() != null) {
				String ecl = conceptQuery.getEcl();
				logger.info("ECL Search {}", ecl);
				List<Long> conceptIdFilter = lexicalCriteria ? allLexicalMatchesWithOrdering : null;
				allLogicalMatches = eclQueryService.selectConceptIds(ecl, branchCriteria, branchPath, conceptQuery.isStated(), conceptIdFilter);
			} else {
				logger.info("Primitive Logical Search ");
				NativeSearchQueryBuilder logicalSearchQuery = new NativeSearchQueryBuilder()
						.withQuery(boolQuery()
								.must(branchCriteria)
								.must(conceptQuery.getRootBuilder())
						)
						.withPageable(LARGE_PAGE);
				if (lexicalCriteria) {
					logicalSearchQuery.withFilter(boolQuery().must(termsQuery("conceptId", allLexicalMatchesWithOrdering)));
				}
				allLogicalMatches = new ArrayList<>();
				try (CloseableIterator<QueryConcept> stream = elasticsearchTemplate.stream(logicalSearchQuery.build(), QueryConcept.class)) {
					stream.forEachRemaining(c -> allLogicalMatches.add(c.getConceptId()));
				}
			}

			if (!lexicalCriteria) {
				// Return a page of unordered results from logical search
				List<Long> pageOfMatches = CollectionUtil.subList(allLogicalMatches, pageSize);
				logger.info("Gather minis");
				return new ArrayList<>(conceptService.findConceptMinis(branchCriteria, pageOfMatches).values());
			} else {
				List<Long> combinedMatchesWithOrdering = CollectionUtil.listIntersection(allLexicalMatchesWithOrdering, allLogicalMatches);
				List<Long> pageOfMatches = CollectionUtil.subList(combinedMatchesWithOrdering, pageSize);
				Map<String, ConceptMini> conceptMiniMap = conceptService.findConceptMinis(branchCriteria, pageOfMatches);
				// Recreate term score ordering
				return getOrderedConceptList(pageOfMatches, conceptMiniMap);
			}
		}
		return Collections.emptyList();
	}

	private BoolQueryBuilder getLexicalQuery(QueryBuilder branchCriteria, String term) {
		BoolQueryBuilder lexicalQuery = boolQuery()
				.must(branchCriteria)
				.must(termQuery("active", true))
				.must(termQuery("typeId", Concepts.FSN));
		DescriptionService.addTermClauses(term, lexicalQuery);
		return lexicalQuery;
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