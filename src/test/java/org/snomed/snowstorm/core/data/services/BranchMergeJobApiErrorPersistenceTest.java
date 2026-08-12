package org.snomed.snowstorm.core.data.services;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.TestConfig;
import org.snomed.snowstorm.core.data.domain.BranchMergeJob;
import org.snomed.snowstorm.core.data.domain.ConceptMini;
import org.snomed.snowstorm.core.data.domain.JobStatus;
import org.snomed.snowstorm.core.data.repositories.BranchMergeJobRepository;
import org.snomed.snowstorm.core.data.services.pojo.IntegrityIssueReport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies BranchMergeJob apiError persistence against Elasticsearch 8 (Testcontainers 8.11.1).
 * Nested integrity maps must not be indexed as dynamic object fields or ES hits
 * index.mapping.total_fields.limit (default 1000).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = TestConfig.class)
class BranchMergeJobApiErrorPersistenceTest extends AbstractTest {

	/** 400 keys × 3 relationship maps = 1200 dynamic fields if nested — over the default ES limit. */
	private static final int ISSUES_PER_MAP = 400;

	@Autowired
	private BranchMergeJobRepository repository;

	@Autowired
	private ElasticsearchOperations elasticsearchOperations;

	@Test
	void saveAndLoadApiErrorWithManyIntegrityIssues() {
		IntegrityIssueReport report = buildLargeIntegrityIssueReport(ISSUES_PER_MAP);
		ApiError apiError = ApiErrorFactory.createErrorForMergeConflicts(
				"Merge failed due to integrity issues", report);

		BranchMergeJob mergeJob = new BranchMergeJob("MAIN/A", "MAIN", JobStatus.CONFLICTS);
		mergeJob.setStartDate(new Date());
		mergeJob.setEndDate(new Date());
		mergeJob.setMessage(apiError.getMessage());
		mergeJob.setApiError(apiError);

		assertNotNull(mergeJob.getApiErrorJson(), "apiError should be serialised to a single JSON string for ES");
		assertFalse(mergeJob.getApiErrorJson().isBlank());

		repository.save(mergeJob);
		elasticsearchOperations.indexOps(BranchMergeJob.class).refresh();

		BranchMergeJob loaded = repository.findById(mergeJob.getId()).orElseThrow();
		assertEquals(JobStatus.CONFLICTS, loaded.getStatus());
		assertEquals("Merge failed due to integrity issues", loaded.getMessage());
		assertNotNull(loaded.getApiErrorJson());
		assertTrue(loaded.getApiErrorJson().startsWith("{"));

		ApiError loadedError = loaded.getApiError();
		assertNotNull(loadedError);
		assertEquals("Merge failed due to integrity issues", loadedError.getMessage());
		assertEquals("The integrity check API can be used here.", loadedError.getDeveloperMessage());

		@SuppressWarnings("unchecked")
		Map<String, Object> integrityIssues =
				(Map<String, Object>) loadedError.getAdditionalInfo().get("integrityIssues");
		assertNotNull(integrityIssues);

		assertIntegrityMapSize(integrityIssues, "relationshipsWithMissingOrInactiveSource", ISSUES_PER_MAP);
		assertIntegrityMapSize(integrityIssues, "relationshipsWithMissingOrInactiveType", ISSUES_PER_MAP);
		assertIntegrityMapSize(integrityIssues, "relationshipsWithMissingOrInactiveDestination", ISSUES_PER_MAP);
		assertIntegrityMapSize(integrityIssues, "axiomsWithMissingOrInactiveReferencedConcept", 2);

		@SuppressWarnings("unchecked")
		Map<String, Object> sourceIssues =
				(Map<String, Object>) integrityIssues.get("relationshipsWithMissingOrInactiveSource");
		assertEquals(2_000_000L, ((Number) sourceIssues.get("1000000")).longValue());

		@SuppressWarnings("unchecked")
		Map<String, Object> axiomIssues =
				(Map<String, Object>) integrityIssues.get("axiomsWithMissingOrInactiveReferencedConcept");
		assertEquals(100001L, ((Number) axiomIssues.get("axiom-1")).longValue());
		assertNull(axiomIssues.get("axiom-null-concept"));
	}

	@Test
	void apiErrorJsonIsStoredAsSingleTextFieldNotNestedObject() {
		IntegrityIssueReport report = buildLargeIntegrityIssueReport(50);
		ApiError apiError = ApiErrorFactory.createErrorForMergeConflicts("Integrity failure", report);

		BranchMergeJob mergeJob = new BranchMergeJob("MAIN/B", "MAIN", JobStatus.CONFLICTS);
		mergeJob.setMessage(apiError.getMessage());
		mergeJob.setApiError(apiError);
		repository.save(mergeJob);
		elasticsearchOperations.indexOps(BranchMergeJob.class).refresh();

		IndexCoordinates index = elasticsearchOperations.getIndexCoordinatesFor(BranchMergeJob.class);
		@SuppressWarnings("unchecked")
		Map<String, Object> source = elasticsearchOperations.get(mergeJob.getId(), Map.class, index);
		assertNotNull(source, "document should exist in ES");
		assertTrue(source.get("apiErrorJson") instanceof String, "apiError must be stored as a string field");
		assertFalse(source.containsKey("apiError"), "nested apiError object must not be written to ES");

		String apiErrorJson = (String) source.get("apiErrorJson");
		assertTrue(apiErrorJson.contains("integrityIssues"));
		assertTrue(apiErrorJson.contains("relationshipsWithMissingOrInactiveSource"));
	}

	private static IntegrityIssueReport buildLargeIntegrityIssueReport(int issuesPerMap) {
		Map<Long, Long> missingSource = new Long2LongOpenHashMap();
		Map<Long, Long> missingType = new Long2LongOpenHashMap();
		Map<Long, Long> missingDestination = new Long2LongOpenHashMap();
		for (int i = 0; i < issuesPerMap; i++) {
			long relationshipId = 1_000_000L + i;
			long conceptId = 2_000_000L + i;
			missingSource.put(relationshipId, conceptId);
			missingType.put(relationshipId + 10_000_000L, conceptId);
			missingDestination.put(relationshipId + 20_000_000L, conceptId);
		}

		Map<String, ConceptMini> axioms = new HashMap<>();
		axioms.put("axiom-1", new ConceptMini("100001", Collections.emptyList()));
		axioms.put("axiom-null-concept", null);

		IntegrityIssueReport report = new IntegrityIssueReport();
		report.setRelationshipsWithMissingOrInactiveSource(missingSource);
		report.setRelationshipsWithMissingOrInactiveType(missingType);
		report.setRelationshipsWithMissingOrInactiveDestination(missingDestination);
		report.setAxiomsWithMissingOrInactiveReferencedConcept(axioms);
		return report;
	}

	@SuppressWarnings("unchecked")
	private static void assertIntegrityMapSize(Map<String, Object> integrityIssues, String field, int expectedSize) {
		Map<String, Object> map = (Map<String, Object>) integrityIssues.get(field);
		assertNotNull(map, field);
		assertEquals(expectedSize, map.size(), field);
	}
}
