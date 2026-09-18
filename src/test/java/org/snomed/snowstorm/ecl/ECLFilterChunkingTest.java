package org.snomed.snowstorm.ecl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.ErrorCause;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.snomed.snowstorm.TestConfig;
import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.core.data.domain.ConceptMini;
import org.snomed.snowstorm.core.data.domain.Description;
import org.snomed.snowstorm.core.data.domain.QueryConcept;
import org.snomed.snowstorm.core.data.domain.ReferenceSetMember;
import org.snomed.snowstorm.core.data.services.DescriptionService;
import org.snomed.snowstorm.core.data.services.QueryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static io.kaicode.elasticvc.domain.Branch.MAIN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.snomed.snowstorm.config.ElasticsearchConfig.INDEX_MAX_TERMS_COUNT;

/**
 * Covers the id chunking in {@link DescriptionService#executeDescriptionQuery},
 * {@link DescriptionService#resolveAcceptableDescriptions} and {@link ECLContentService#applyConceptFilters}, added
 * under MAINT-3040.
 * <p>
 * A sub-expression carrying a filter feeds the ids it selects into a single Elasticsearch terms query. Past roughly
 * half a million ids that breaches {@code index.max_terms_count} and the request fails with a 500, so all three
 * queries now run in batches of {@code CLAUSE_LIMIT} (65,000).
 * <p>
 * A bare wildcard mostly no longer reaches that code: when the filters are the only constraint there is no concept id
 * set for them to narrow, so {@link org.snomed.snowstorm.ecl.domain.expressionconstraint.SSubExpressionConstraint}
 * runs the filter query on its own. The chunking tests therefore constrain by the root rather than using a wildcard,
 * and the wildcard tests below cover the no-prefetch path instead, including the fallback to prefetching when the
 * filters match more concepts than the semantic index will take in a terms clause.
 * <p>
 * The fixture holds a few dozen concepts, far below that limit, so the production batch size would always produce a
 * single batch and leave the loop untested. The batch size is therefore lowered on the live beans for the duration of
 * each test and restored afterwards, in the same way {@link ECLQueryService#setEclCacheEnabled} exists for tests.
 * Overriding it via {@code @TestPropertySource} would spin up a second Spring context and corrupt the Elasticsearch
 * indices shared with the other ECL tests.
 * <p>
 * Most tests here compare the batched result against the unbatched result of the same ECL rather than against a hard
 * coded set, since the property under test is that chunking does not change what the query returns. The ECL results
 * cache is cleared between the two runs, otherwise the second one is served from cache and never reaches the code
 * under test.
 * <p>
 * The two {@code ExceedsMaxTermsCount} tests go further and reproduce the original XDS-183 failure: they drop
 * {@code index.max_terms_count} below the fixture's concept count, confirm the unbatched query is rejected by
 * Elasticsearch, then confirm the batched query succeeds against the same limit. Index settings survive the per-test
 * document cleanup, so every index touched is restored afterwards.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {TestConfig.class, ECLQueryServiceFilterTestConfig.class})
// This class and ECLQueryServiceFilterTest share a context cache key, so the second to run would otherwise be
// given the cached context without a second @PostConstruct. The fixture only populates its data in
// @PostConstruct, and every other fixture's deleteAll wipes the shared Elasticsearch instance in between, so
// that reused context would find an empty index. Dirtying forces a rebuild, and with it a fresh fixture.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ECLFilterChunkingTest {

	// Small enough to force many batches over the fixture, and unlikely to divide the concept count exactly, so the
	// final partial batch is exercised too.
	private static final int TEST_BATCH_SIZE = 3;

	private static final PageRequest PAGE_REQUEST = PageRequest.of(0, 10000);

	// Below the fixture's concept count, so an unbatched terms query breaches it, but comfortably above the number of
	// concepts these ECL queries return, so loading the result ConceptMinis stays under the same limit.
	private static final int LOW_MAX_TERMS_COUNT = 10;

	// Wildcards. Nothing constrains these but the filter itself, so SSubExpressionConstraint runs the filter query on
	// its own and never prefetches concept ids.
	private static final String DESCRIPTION_FILTER_ECL = "* {{ D term = \"heart\" }}";
	private static final String CONCEPT_FILTER_ECL = "* {{ C definitionStatus = primitive }}";

	// Matches only the one defined concept in the fixture. The max terms tests need an ECL whose result set stays
	// under LOW_MAX_TERMS_COUNT, unlike CONCEPT_FILTER_ECL which matches nearly everything.
	private static final String NARROW_CONCEPT_FILTER_ECL = "* {{ C definitionStatus = defined }}";

	// Constrained by the root instead of a wildcard, so the filters still receive a prefetched concept id set and the
	// chunking stays under test. Every concept in the fixture descends from the root, so these select the same
	// concepts as the wildcard forms above, which is what the equivalence tests rely on.
	private static final String ROOT = "<< 138875005 |SNOMED CT Concept|";
	private static final String ROOT_DESCRIPTION_FILTER_ECL = ROOT + " {{ D term = \"heart\" }}";
	private static final String ROOT_CONCEPT_FILTER_ECL = ROOT + " {{ C definitionStatus = primitive }}";
	private static final String ROOT_BOTH_FILTERS_ECL = ROOT + " {{ C definitionStatus = primitive }} {{ D term = \"heart\" }}";
	private static final String ROOT_NARROW_CONCEPT_FILTER_ECL = ROOT + " {{ C definitionStatus = defined }}";

	// Dialect filters are resolved from the description ids matched so far rather than from concept ids, against the
	// reference set member index, so they chunk separately from the two queries above. No term filter, so every
	// description in the subtree reaches the acceptability query and the batch loop has work to do.
	private static final String DIALECT_FILTER_ECL = "< 64572001 |Disease| {{ dialectId = 46011000052107 }}";

	// The fixture holds only a handful of descriptions, far fewer than concepts, so the dialect filter test needs a
	// limit below that count rather than LOW_MAX_TERMS_COUNT. Still above the single term every other reference set
	// member clause sends, so the rejection it asserts can only come from the acceptability query.
	private static final int LOW_DESCRIPTION_MAX_TERMS_COUNT = 5;

	@Autowired
	private QueryService queryService;

	@Autowired
	private ECLQueryService eclQueryService;

	@Autowired
	private DescriptionService descriptionService;

	@Autowired
	private ECLContentService eclContentService;

	@Autowired
	private ElasticsearchOperations elasticsearchOperations;

	@Autowired
	private ElasticsearchClient elasticsearchClient;

	@Autowired
	private SECLObjectFactory eclObjectFactory;

	@Value("${elasticsearch.index.max.terms.count}")
	private int configuredMaxTermsCount;

	private int originalDescriptionBatchSize;
	private int originalConceptBatchSize;
	private int originalEclMaxTermsCount;
	private final Set<Class<?>> indicesWithLoweredMaxTerms = new HashSet<>();

	@BeforeEach
	void captureBatchSizes() {
		originalDescriptionBatchSize = descriptionService.getTermsBatchSize();
		originalConceptBatchSize = eclContentService.getConceptIdBatchSize();
		originalEclMaxTermsCount = eclObjectFactory.getMaxTermsCount();
	}

	@AfterEach
	void restoreBatchSizesAndIndexSettings() throws IOException {
		descriptionService.setTermsBatchSize(originalDescriptionBatchSize);
		eclContentService.setConceptIdBatchSize(originalConceptBatchSize);
		eclObjectFactory.setMaxTermsCount(originalEclMaxTermsCount);
		eclQueryService.clearCache();

		// Index settings outlive the test fixture, which is created once and shared via the cached Spring context,
		// so anything left lowered here would break every test that runs afterwards against the same indices.
		for (Class<?> domainEntityClass : indicesWithLoweredMaxTerms) {
			setMaxTermsCount(domainEntityClass, configuredMaxTermsCount);
		}
		indicesWithLoweredMaxTerms.clear();
	}

	@Test
	void descriptionFilterReturnsSameConceptsWhenConceptIdsAreChunked() {
		assertChunkingPreservesResults(ROOT_DESCRIPTION_FILTER_ECL);
	}

	@Test
	void conceptFilterReturnsSameConceptsWhenConceptIdsAreChunked() {
		assertChunkingPreservesResults(ROOT_CONCEPT_FILTER_ECL);
	}

	@Test
	void conceptAndDescriptionFiltersReturnSameConceptsWhenConceptIdsAreChunked() {
		// Concept filters run before description filters and both chunk, so this covers the two loops in one pass.
		assertChunkingPreservesResults(ROOT_BOTH_FILTERS_ECL);
	}

	@Test
	void dialectFilterReturnsSameConceptsWhenDescriptionIdsAreChunked() {
		assertChunkingPreservesResults(DIALECT_FILTER_ECL);
	}

	/**
	 * A batch size of one puts each concept id in its own query, which is the strongest check that results are merged
	 * rather than overwritten, and that a trailing batch is not dropped.
	 */
	@ParameterizedTest
	@ValueSource(ints = {1, 2, 3, 7})
	void resultsAreIndependentOfBatchSize(int batchSize) {
		Set<String> expected = selectWithFreshCache(ROOT_DESCRIPTION_FILTER_ECL);
		assertFalse(expected.isEmpty(), "The ECL must match something, otherwise this test passes vacuously.");

		setBatchSizes(batchSize);

		assertEquals(expected, selectWithFreshCache(ROOT_DESCRIPTION_FILTER_ECL),
				"Batch size " + batchSize + " changed the result set.");
	}

	/**
	 * The XDS-183 reproduction for the description filter: without chunking the terms query carries every concept id
	 * on the branch and Elasticsearch rejects it outright.
	 */
	@Test
	void descriptionFilterExceedsMaxTermsCountWithoutChunking() throws IOException {
		// Read this before lowering the limit, since loading every concept mini would itself breach it.
		int conceptCount = countAllConcepts();
		assertTrue(conceptCount > LOW_MAX_TERMS_COUNT,
				"The fixture must hold more concepts than the lowered limit, otherwise the unbatched query would not "
						+ "breach it. Concepts: " + conceptCount + ", limit: " + LOW_MAX_TERMS_COUNT);

		lowerMaxTermsCount(Description.class);

		// Production batch size, so all concept ids go into a single terms query, exactly as before this fix.
		Exception rejected = assertThrows(Exception.class, () -> selectWithFreshCache(ROOT_DESCRIPTION_FILTER_ECL),
				"The unbatched description query should breach index.max_terms_count.");
		assertTrue(describeCauseChain(rejected).contains(INDEX_MAX_TERMS_COUNT),
				"Expected an index.max_terms_count rejection but got: " + describeCauseChain(rejected));

		// Same query and same limit, only now the concept ids are chunked below it.
		setBatchSizes(TEST_BATCH_SIZE);
		assertFalse(selectWithFreshCache(ROOT_DESCRIPTION_FILTER_ECL).isEmpty(),
				"The batched description query should succeed under the same limit that rejected the unbatched one.");
	}

	/**
	 * The same reproduction for the concept filter, which runs before the description filter and receives the same
	 * unnarrowed set of concept ids.
	 */
	@Test
	void conceptFilterExceedsMaxTermsCountWithoutChunking() throws IOException {
		int conceptCount = countAllConcepts();
		assertTrue(conceptCount > LOW_MAX_TERMS_COUNT,
				"The fixture must hold more concepts than the lowered limit, otherwise the unbatched query would not "
						+ "breach it. Concepts: " + conceptCount + ", limit: " + LOW_MAX_TERMS_COUNT);

		lowerMaxTermsCount(Concept.class);

		Exception rejected = assertThrows(Exception.class, () -> selectWithFreshCache(ROOT_NARROW_CONCEPT_FILTER_ECL),
				"The unbatched concept filter query should breach index.max_terms_count.");
		assertTrue(describeCauseChain(rejected).contains(INDEX_MAX_TERMS_COUNT),
				"Expected an index.max_terms_count rejection but got: " + describeCauseChain(rejected));

		setBatchSizes(TEST_BATCH_SIZE);
		assertFalse(selectWithFreshCache(ROOT_NARROW_CONCEPT_FILTER_ECL).isEmpty(),
				"The batched concept filter query should succeed under the same limit that rejected the unbatched one.");
	}

	/**
	 * The same failure for dialect filters, which resolve acceptability from the description ids matched so far rather
	 * than from concept ids and so need their own chunking.
	 */
	@Test
	void dialectFilterExceedsMaxTermsCountWhenDescriptionIdsAreNotChunked() throws IOException {
		int descriptionCount = countDescriptionsUnderTheDialectFilter();
		assertTrue(descriptionCount > LOW_DESCRIPTION_MAX_TERMS_COUNT,
				"The filter must reach more descriptions than the lowered limit, otherwise this passes vacuously. "
						+ "Descriptions: " + descriptionCount + ", limit: " + LOW_DESCRIPTION_MAX_TERMS_COUNT);

		lowerMaxTermsCount(ReferenceSetMember.class, LOW_DESCRIPTION_MAX_TERMS_COUNT);

		Exception rejected = assertThrows(Exception.class, () -> selectWithFreshCache(DIALECT_FILTER_ECL),
				"The unbatched acceptability query should breach index.max_terms_count.");
		assertTrue(describeCauseChain(rejected).contains(INDEX_MAX_TERMS_COUNT),
				"Expected an index.max_terms_count rejection but got: " + describeCauseChain(rejected));

		setBatchSizes(TEST_BATCH_SIZE);
		assertFalse(selectWithFreshCache(DIALECT_FILTER_ECL).isEmpty(),
				"The batched acceptability query should succeed under the same limit that rejected the unbatched one.");
	}

	/**
	 * The optimisation itself. A wildcard carrying only a filter used to prefetch every concept id on the branch and
	 * pass them to the description query as a terms clause, which is what breached the limit above. The filter query
	 * now stands alone, so the same ECL succeeds against a limit far below the branch's concept count.
	 */
	@Test
	void filteredWildcardSendsNoConceptIdsToTheDescriptionQuery() throws IOException {
		int conceptCount = countAllConcepts();
		assertTrue(conceptCount > LOW_MAX_TERMS_COUNT,
				"The fixture must hold more concepts than the lowered limit, otherwise this passes vacuously. Concepts: "
						+ conceptCount + ", limit: " + LOW_MAX_TERMS_COUNT);

		lowerMaxTermsCount(Description.class);

		// Production batch size. Chunking is not what saves this query, the absence of a concept id clause is.
		assertFalse(selectWithFreshCache(DESCRIPTION_FILTER_ECL).isEmpty(),
				"A wildcard with only a description filter should not send concept ids at all, so it should succeed "
						+ "under a limit that rejects the constrained form.");
	}

	@Test
	void filteredWildcardSendsNoConceptIdsToTheConceptQuery() throws IOException {
		int conceptCount = countAllConcepts();
		assertTrue(conceptCount > LOW_MAX_TERMS_COUNT,
				"The fixture must hold more concepts than the lowered limit, otherwise this passes vacuously. Concepts: "
						+ conceptCount + ", limit: " + LOW_MAX_TERMS_COUNT);

		lowerMaxTermsCount(Concept.class);

		assertFalse(selectWithFreshCache(NARROW_CONCEPT_FILTER_ECL).isEmpty(),
				"A wildcard with only a concept filter should not send concept ids at all, so it should succeed under "
						+ "a limit that rejects the constrained form.");
	}

	/**
	 * The guard on that optimisation. Skipping the prefetch moves the concept ids from the filter query onto the
	 * semantic index query, where they cannot be chunked because that is the query being paged. Past the terms limit
	 * the wildcard path therefore has to go back to prefetching, otherwise it trades a rejected filter query for a
	 * rejected semantic index one.
	 */
	@Test
	void filteredWildcardPrefetchesAgainWhenTooManyConceptsMatchForATermsClause() throws IOException {
		Set<String> expected = selectWithFreshCache(CONCEPT_FILTER_ECL);
		assertTrue(expected.size() > LOW_MAX_TERMS_COUNT,
				"The filter must match more concepts than the lowered limit, otherwise the fallback never triggers. "
						+ "Matched: " + expected.size() + ", limit: " + LOW_MAX_TERMS_COUNT);

		// The semantic index is the index the concept ids would land on, and the parser bound mirrors its setting.
		lowerMaxTermsCount(QueryConcept.class);
		eclObjectFactory.setMaxTermsCount(LOW_MAX_TERMS_COUNT);

		assertEquals(expected, selectWithFreshCache(CONCEPT_FILTER_ECL),
				"A wildcard whose filter matches more concepts than the terms limit should fall back to prefetching "
						+ "rather than sending them all to the semantic index.");
	}

	/**
	 * Guards the semantics of the optimisation. Skipping the prefetch means the filter queries run unconstrained, so
	 * they can match descriptions of concepts that are inactive or absent from this branch's semantic index. Those must
	 * still be excluded, which is why the wildcard path leaves the prefetch callback unset and lets the enclosing
	 * semantic index query apply the branch, stated and active criteria.
	 */
	@ParameterizedTest
	@ValueSource(strings = {"{{ D term = \"heart\" }}", "{{ C definitionStatus = primitive }}",
			"{{ C definitionStatus = defined }}", "{{ C definitionStatus = primitive }} {{ D term = \"heart\" }}"})
	void filteredWildcardReturnsSameConceptsAsConstrainedEquivalent(String filter) {
		// Every concept in the fixture descends from the root, so the two operands select the same concepts and any
		// difference in the filtered results comes from the filtering path rather than the operand.
		assertEquals(selectWithFreshCache("*"), selectWithFreshCache(ROOT),
				"Precondition: the wildcard and the root operand must select the same concepts in this fixture.");

		Set<String> constrained = selectWithFreshCache(ROOT + " " + filter);
		assertFalse(constrained.isEmpty(), "The filter must match something, otherwise this passes vacuously.");

		assertEquals(constrained, selectWithFreshCache("* " + filter),
				"The wildcard path, which no longer prefetches concept ids, returned different concepts from the "
						+ "constrained path, which still does. Filter: " + filter);
	}

	private void lowerMaxTermsCount(Class<?> domainEntityClass) throws IOException {
		lowerMaxTermsCount(domainEntityClass, LOW_MAX_TERMS_COUNT);
	}

	private void lowerMaxTermsCount(Class<?> domainEntityClass, int maxTermsCount) throws IOException {
		setMaxTermsCount(domainEntityClass, maxTermsCount);
		indicesWithLoweredMaxTerms.add(domainEntityClass);
	}

	private void setMaxTermsCount(Class<?> domainEntityClass, int maxTermsCount) throws IOException {
		String indexName = elasticsearchOperations.getIndexCoordinatesFor(domainEntityClass).getIndexName();
		elasticsearchClient.indices().putSettings(request -> request
				.index(indexName)
				.settings(settings -> settings.maxTermsCount(maxTermsCount)));
	}

	/**
	 * The Elasticsearch rejection is wrapped by the client and again by Spring Data, and the limit is only named deep
	 * in the chain, so the assertion matches on the whole chain rather than a specific exception type.
	 */
	private String describeCauseChain(Throwable throwable) {
		StringBuilder messages = new StringBuilder();
		for (Throwable current = throwable; current != null; current = current.getCause()) {
			messages.append(current.getClass().getSimpleName()).append(": ").append(current.getMessage()).append('\n');
			if (current instanceof ElasticsearchException elasticsearchException && elasticsearchException.response() != null) {
				describeErrorCause(elasticsearchException.error(), messages);
			}
			if (current.getCause() == current) {
				break;
			}
		}
		return messages.toString();
	}

	/**
	 * The exception message only reaches "all shards failed". Elasticsearch names the breached setting in the
	 * structured error body instead, under the per shard root cause, so the whole tree is flattened for the assertion.
	 */
	private void describeErrorCause(ErrorCause errorCause, StringBuilder messages) {
		if (errorCause == null) {
			return;
		}
		messages.append(errorCause.type()).append(": ").append(errorCause.reason()).append('\n');
		errorCause.rootCause().forEach(cause -> describeErrorCause(cause, messages));
		errorCause.suppressed().forEach(cause -> describeErrorCause(cause, messages));
		describeErrorCause(errorCause.causedBy(), messages);
	}

	private void assertChunkingPreservesResults(String ecl) {
		int conceptCount = countAllConcepts();
		assertTrue(conceptCount > TEST_BATCH_SIZE,
				"The fixture must hold more concepts than the test batch size, otherwise chunking is never exercised. "
						+ "Concepts: " + conceptCount + ", batch size: " + TEST_BATCH_SIZE);

		Set<String> unchunked = selectWithFreshCache(ecl);
		assertFalse(unchunked.isEmpty(), "The ECL must match something, otherwise this test passes vacuously.");

		setBatchSizes(TEST_BATCH_SIZE);

		assertEquals(unchunked, selectWithFreshCache(ecl),
				"Chunking concept ids into batches of " + TEST_BATCH_SIZE + " changed the result of: " + ecl);
	}

	private void setBatchSizes(int batchSize) {
		descriptionService.setTermsBatchSize(batchSize);
		eclContentService.setConceptIdBatchSize(batchSize);
	}

	private int countAllConcepts() {
		return selectWithFreshCache("*").size();
	}

	/**
	 * The descriptions the acceptability query receives: every description of the concepts the operand selects, since
	 * the filter carries no term or type sub-filter to narrow them first.
	 */
	private int countDescriptionsUnderTheDialectFilter() {
		return descriptionService.findDescriptionsByConceptId(MAIN, selectWithFreshCache("< 64572001 |Disease|"), false).size();
	}

	/**
	 * The ECL results cache is keyed by branch, commit and page, so running the same ECL twice within one test would
	 * return the cached page without touching the batching code.
	 */
	private Set<String> selectWithFreshCache(String ecl) {
		eclQueryService.clearCache();
		return queryService.eclSearch(ecl, false, MAIN, PAGE_REQUEST)
				.getContent().stream().map(ConceptMini::getConceptId).collect(Collectors.toSet());
	}
}
