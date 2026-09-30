package org.snomed.snowstorm.core.util;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.snomed.snowstorm.core.util.ElasticsearchErrorTestData.*;

class ElasticsearchErrorUtilTest {

	@Test
	void findsErrorResponseAnywhereInCauseChain() {
		RuntimeException wrapped = new RuntimeException("Import failed", new IllegalStateException("Rebase failed", searchPhaseFailure()));

		assertEquals(400, ElasticsearchErrorUtil.findErrorResponse(wrapped).orElseThrow().status());
		assertEquals(Optional.of(ROOT_CAUSE_REASON), ElasticsearchErrorUtil.getFirstRootCauseReason(wrapped));
	}

	@Test
	void describesDistinctRootCauses() {
		assertEquals("[query_shard_exception] " + ROOT_CAUSE_REASON, ElasticsearchErrorUtil.describeRootCauses(searchPhaseFailure()));
	}

	@Test
	void handlesErrorWithoutRootCauses() {
		assertTrue(ElasticsearchErrorUtil.getRootCauses(failureWithoutRootCause()).isEmpty());
		assertTrue(ElasticsearchErrorUtil.getFirstRootCauseReason(failureWithoutRootCause()).isEmpty());
		assertEquals("", ElasticsearchErrorUtil.describeRootCauses(failureWithoutRootCause()));
	}

	@Test
	void fallsBackToDeepestCausedByWithoutRootCauses() {
		assertEquals("[too_many_clauses] " + CAUSED_BY_REASON, ElasticsearchErrorUtil.describeRootCauses(failureWithCausedByChain()));
		assertEquals(Optional.of(CAUSED_BY_REASON), ElasticsearchErrorUtil.getFirstRootCauseReason(failureWithCausedByChain()));
	}

	@Test
	void handlesNonElasticsearchThrowables() {
		assertTrue(ElasticsearchErrorUtil.findErrorResponse(null).isEmpty());
		assertTrue(ElasticsearchErrorUtil.findErrorResponse(new RuntimeException("plain")).isEmpty());
		assertEquals("", ElasticsearchErrorUtil.describeRootCauses(new RuntimeException("plain")));
	}

	@Test
	void stopsOnCyclicCauseChain() {
		RuntimeException first = new RuntimeException("first");
		RuntimeException second = new RuntimeException("second", first);
		first.initCause(second);

		assertTrue(ElasticsearchErrorUtil.findErrorResponse(first).isEmpty());
	}
}
