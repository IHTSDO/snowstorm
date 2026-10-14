package org.snomed.snowstorm.ecl;

import org.junit.jupiter.api.Test;
import org.snomed.snowstorm.rest.pojo.SearchAfterPageRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.*;

class ECLResultsCacheTest {

	private static final String PATH = "MAIN";
	private static final Date HEAD = new Date(1000);
	private static final PageRequest PAGE = PageRequest.of(0, 10);
	private static final Function<String, Long> AT_HEAD = path -> HEAD.getTime();

	@Test
	void test() {
		assertEquals("123, 456", ECLResultsCache.normaliseEclString("123 AND 456"));
		assertEquals("> 987840791000119102, > 969688801000119108", ECLResultsCache.normaliseEclString(
				"> 987840791000119102 | Adenosine deaminase 2 deficiency (disorder) |  AND > 969688801000119108 | Acute left-sided ulcerative colitis (disorder) |"));
		assertEquals(">987840791000119102, >969688801000119108", ECLResultsCache.normaliseEclString(
				">987840791000119102, >969688801000119108 |Wrong term here|"));
	}

	@Test
	void testQuotedStringsKeptAsWritten() {
		assertNotEquals(ECLResultsCache.normaliseEclString("^ 447562003 {{ M mapTarget = \"J45.9\" }}"),
				ECLResultsCache.normaliseEclString("^ 447562003 {{ M mapTarget = \"j45.9\" }}"));
		assertNotEquals(ECLResultsCache.normaliseEclString("< 64572001 {{ term = \"salt and pepper\" }}"),
				ECLResultsCache.normaliseEclString("< 64572001 {{ term = \"salt, pepper\" }}"));
		assertNotEquals(ECLResultsCache.normaliseEclString("< 64572001 : 1142139005 = #\"Abc\""),
				ECLResultsCache.normaliseEclString("< 64572001 : 1142139005 = #\"abc\""));
		assertEquals("< 64572001 {{ term = \"a|b\" }}", ECLResultsCache.normaliseEclString("< 64572001 |Disease| {{ TERM = \"a|b\" }}"));
		assertEquals("< 64572001 {{ term = \"say \\\"Hi\\\" and |x|\" }}",
				ECLResultsCache.normaliseEclString("< 64572001 {{ term = \"say \\\"Hi\\\" and |x|\" }}"));
	}

	@Test
	void testFilterActive() {
		assertEquals("<< 195967001 {{ d active = 0 }}", ECLResultsCache.normaliseEclString("<< 195967001 |Asthma| {{ D active = 0 }}"));
		assertEquals("<< 195967001 {{ d active = 1 }}", ECLResultsCache.normaliseEclString("<< 195967001 |Asthma| {{ D active = 1 }}"));
	}

	@Test
	void testHitRespectsKey() {
		ECLResultsCache cache = new ECLResultsCache(1_000);
		Page<Long> page = page(5);
		cache.put(PATH, HEAD, "<< 123 |Term|", false, PAGE, page);

		assertSame(page, cache.get(PATH, HEAD, "<< 123", false, PAGE));
		assertNull(cache.get(PATH, HEAD, "<< 123", true, PAGE));
		assertNull(cache.get("MAIN/A", HEAD, "<< 123", false, PAGE));
		assertNull(cache.get(PATH, HEAD, "<< 123", false, PageRequest.of(1, 10)));
		assertNull(cache.get(PATH, HEAD, "<< 123", false, null));
		assertSame(page, cache.get(PATH, HEAD, "<< 123", false, PAGE));
		assertNull(cache.get(PATH, new Date(2000), "<< 123", false, PAGE));
	}

	@Test
	void testSearchAfterKeyedByValue() {
		ECLResultsCache cache = new ECLResultsCache(1_000);
		Page<Long> page = page(5);
		cache.put(PATH, HEAD, "<< 123", false, SearchAfterPageRequest.of(new Object[]{123L}, 10, Sort.unsorted()), page);

		assertSame(page, cache.get(PATH, HEAD, "<< 123", false, SearchAfterPageRequest.of(new Object[]{123L}, 10, Sort.unsorted())));
		assertNull(cache.get(PATH, HEAD, "<< 123", false, SearchAfterPageRequest.of(new Object[]{456L}, 10, Sort.unsorted())));
		assertNull(cache.get(PATH, HEAD, "<< 123", false, PageRequest.of(0, 10)));
	}

	@Test
	void testWeightBounded() {
		ECLResultsCache cache = new ECLResultsCache(1_000);
		for (int i = 0; i < 10_000; i++) {
			cache.put(PATH, HEAD, "<< 123 MINUS " + i, false, PAGE, page(50));
		}

		assertTrue(cache.getSummary().get("weight") <= 1_000);
		assertTrue(cache.getSummary().get("evictions") > 0);
	}

	@Test
	void testEmptyResultsAndLongEclCarryWeight() {
		ECLResultsCache cache = new ECLResultsCache(1_000);
		for (int i = 0; i < 10_000; i++) {
			cache.put(PATH, HEAD, "<< " + i, false, PAGE, page(0));
		}
		assertTrue(cache.getSummary().get("entries") <= 1_000 / 12);

		cache.put(PATH, HEAD, "<< 123 OR " + "1".repeat(2_000), false, PAGE, page(0));
		assertNull(cache.get(PATH, HEAD, "<< 123 OR " + "1".repeat(2_000), false, PAGE));
	}

	@Test
	void testOversizedEntryNotCached() {
		ECLResultsCache cache = new ECLResultsCache(1_000);
		cache.put(PATH, HEAD, "<< 123", false, null, page(500));

		assertNull(cache.get(PATH, HEAD, "<< 123", false, null));
	}

	@Test
	void testBranchStats() {
		AtomicReference<LocalDate> today = new AtomicReference<>(LocalDate.of(2026, 1, 1));
		ECLResultsCache cache = new ECLResultsCache(1_000, today::get);
		Date newHead = new Date(2000);
		cache.put(PATH, HEAD, "<< 123", false, PAGE, page(5));
		assertNull(cache.get(PATH, newHead, "<< 123", false, PAGE));
		cache.put(PATH, newHead, "<< 123", false, PAGE, page(5));
		cache.put(PATH, newHead, "<< 456", false, PAGE, page(5));
		cache.put("MAIN/A", HEAD, "<< 123", false, PAGE, page(5));

		cache.get(PATH, newHead, "<< 123", false, PAGE);
		today.set(LocalDate.of(2026, 1, 20));
		cache.get(PATH, newHead, "<< 123", false, PAGE);
		today.set(LocalDate.of(2026, 1, 25));
		cache.get(PATH, newHead, "<< 123", false, PAGE);
		cache.get(PATH, newHead, "<< 456", false, PAGE);
		cache.get(PATH, newHead, "<< 789", false, PAGE);

		Map<String, Map<String, Long>> stats = cache.getStats(Map.of(PATH, newHead.getTime(), "MAIN/A", HEAD.getTime())::get);
		assertEquals(List.of("MAIN", "MAIN/A"), List.copyOf(stats.keySet()));
		assertEquals(Map.of("size", 2L, "hits-today", 2L, "hits-last7days", 3L, "hits-last30days", 4L), stats.get(PATH));
		assertEquals(Map.of("size", 1L, "hits-today", 0L, "hits-last7days", 0L, "hits-last30days", 0L), stats.get("MAIN/A"));
	}

	@Test
	void testCurrentTimepoint() {
		ECLResultsCache cache = new ECLResultsCache(1_000);
		Date newHead = new Date(2000);
		cache.put(PATH, HEAD, "<< 123", false, PAGE, page(5));
		assertFalse(cache.isCurrentTimepoint(PATH, HEAD));

		cache.get(PATH, HEAD, "<< 123", false, PAGE);
		assertTrue(cache.isCurrentTimepoint(PATH, HEAD));
		assertFalse(cache.isCurrentTimepoint(PATH, new Date(500)));
		assertFalse(cache.isCurrentTimepoint(PATH, newHead));
		assertFalse(cache.isCurrentTimepoint("MAIN/A", HEAD));

		cache.get(PATH, newHead, "<< 123", false, PAGE);
		assertTrue(cache.isCurrentTimepoint(PATH, newHead));
		cache.get(PATH, HEAD, "<< 123", false, PAGE);
		assertTrue(cache.isCurrentTimepoint(PATH, HEAD));
		assertFalse(cache.isCurrentTimepoint(PATH, newHead));

		cache.clearCache();
		assertFalse(cache.isCurrentTimepoint(PATH, HEAD));
	}

	@Test
	void testSizeFollowsHeadMovedBackByRollback() {
		ECLResultsCache cache = new ECLResultsCache(1_000);
		Date rolledBackHead = new Date(2000);
		getThenPut(cache, HEAD, "<< 123");
		assertNull(cache.get(PATH, rolledBackHead, "<< 123", false, PAGE));
		cache.put(PATH, rolledBackHead, "<< 123", false, PAGE, page(5));
		cache.put(PATH, rolledBackHead, "<< 456", false, PAGE, page(5));

		// Rolling back a completed commit restores the previous head without a commit notification.
		getThenPut(cache, HEAD, "<< 123");

		assertEquals(1, cache.getStats(AT_HEAD).get(PATH).get("size"));
		assertEquals(1, cache.getSummary().get("entries"));
	}

	@Test
	void testSizeZeroForDeletedBranch() {
		ECLResultsCache cache = new ECLResultsCache(1_000);
		cache.put(PATH, HEAD, "<< 123", false, PAGE, page(5));

		assertEquals(0, cache.getStats(path -> null).get(PATH).get("size"));
	}

	@Test
	void testNewerTimepointDropsOlderBranchResults() {
		ECLResultsCache cache = new ECLResultsCache(1_000);
		Date newHead = new Date(2000);
		getThenPut(cache, HEAD, "<< 123");
		getThenPut(cache, HEAD, "<< 456");
		cache.put("MAIN/A", HEAD, "<< 123", false, PAGE, page(5));

		assertNull(cache.get(PATH, newHead, "<< 123", false, PAGE));

		assertEquals(1, cache.getSummary().get("entries"));
		assertNotNull(cache.get("MAIN/A", HEAD, "<< 123", false, PAGE));
	}

	@Test
	void testOlderTimepointReplacesBranchResults() {
		ECLResultsCache cache = new ECLResultsCache(1_000);
		Date newHead = new Date(2000);
		getThenPut(cache, newHead, "<< 123");

		getThenPut(cache, HEAD, "<< 456");

		assertEquals(1, cache.getSummary().get("entries"));
		assertNotNull(cache.get(PATH, HEAD, "<< 456", false, PAGE));
		assertNull(cache.get(PATH, newHead, "<< 123", false, PAGE));
	}

	@Test
	void testLatePutAtOldTimepointNotCached() {
		ECLResultsCache cache = new ECLResultsCache(1_000);
		Date newHead = new Date(2000);
		assertNull(cache.get(PATH, HEAD, "<< 123", false, PAGE));
		getThenPut(cache, newHead, "<< 456");

		// A slow query that started at the previous head finishes after the head moved.
		cache.put(PATH, HEAD, "<< 123", false, PAGE, page(5));

		assertEquals(1, cache.getSummary().get("entries"));
		assertNotNull(cache.get(PATH, newHead, "<< 456", false, PAGE));
	}

	@Test
	void testSizeCountsHeadEntriesAfterEviction() {
		ECLResultsCache cache = new ECLResultsCache(1_000);
		for (int i = 0; i < 1_000; i++) {
			cache.put("MAIN/" + (i % 7), HEAD, "<< 123 MINUS " + i, false, PAGE, page(50));
		}

		Map<String, Long> summary = cache.getSummary();
		assertTrue(summary.get("evictions") > 0);
		assertEquals(summary.get("entries"), cache.getStats(AT_HEAD).values().stream().mapToLong(branchStats -> branchStats.get("size")).sum());
	}

	@Test
	void testHitsForgottenAfter30Days() {
		AtomicReference<LocalDate> today = new AtomicReference<>(LocalDate.of(2026, 1, 1));
		ECLResultsCache cache = new ECLResultsCache(1_000, today::get);
		cache.put("MAIN/A", HEAD, "<< 123", false, PAGE, page(5));
		cache.get("MAIN/A", HEAD, "<< 123", false, PAGE);
		cache.get("MAIN/A", new Date(2000), "<< 123", false, PAGE);
		assertTrue(cache.getStats(AT_HEAD).containsKey("MAIN/A"));

		today.set(LocalDate.of(2026, 2, 1));

		assertFalse(cache.getStats(AT_HEAD).containsKey("MAIN/A"));
	}

	@Test
	void testClear() {
		ECLResultsCache cache = new ECLResultsCache(1_000);
		cache.put(PATH, HEAD, "<< 123", false, PAGE, page(5));
		cache.get(PATH, HEAD, "<< 123", false, PAGE);
		cache.clearCache();

		assertNull(cache.get(PATH, HEAD, "<< 123", false, PAGE));
		assertTrue(cache.getStats(AT_HEAD).isEmpty());
	}

	@Test
	void testClearResetsSummaryCounters() {
		ECLResultsCache cache = new ECLResultsCache(1_000);
		for (int i = 0; i < 100; i++) {
			cache.put(PATH, HEAD, "<< 123 MINUS " + i, false, PAGE, page(50));
			cache.get(PATH, HEAD, "<< 123 MINUS " + i, false, PAGE);
		}
		cache.get(PATH, HEAD, "<< 456", false, PAGE);
		Map<String, Long> before = cache.getSummary();
		assertTrue(before.get("hits") > 0);
		assertTrue(before.get("misses") > 0);
		assertTrue(before.get("evictions") > 0);

		cache.clearCache();
		assertEquals(Map.of("entries", 0L, "weight", 0L, "max-weight", 1_000L, "hits", 0L, "misses", 0L, "evictions", 0L), cache.getSummary());

		cache.put(PATH, HEAD, "<< 123", false, PAGE, page(5));
		cache.get(PATH, HEAD, "<< 123", false, PAGE);
		cache.get(PATH, HEAD, "<< 456", false, PAGE);
		Map<String, Long> after = cache.getSummary();
		assertEquals(1, after.get("hits"));
		assertEquals(1, after.get("misses"));
	}

	// ECLQueryService always looks up before caching, and the lookup is what moves the branch to the query's timepoint.
	private static void getThenPut(ECLResultsCache cache, Date timepoint, String ecl) {
		assertNull(cache.get(PATH, timepoint, ecl, false, PAGE));
		cache.put(PATH, timepoint, ecl, false, PAGE, page(5));
	}

	private static Page<Long> page(int size) {
		List<Long> ids = LongStream.range(0, size).boxed().toList();
		return new PageImpl<>(ids);
	}
}
