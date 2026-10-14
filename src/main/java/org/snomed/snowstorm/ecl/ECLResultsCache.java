package org.snomed.snowstorm.ecl;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.rest.pojo.SearchAfterPageRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;

public class ECLResultsCache {

	private final Cache<ECLCacheKey, Page<Long>> cache;

	private final long maxWeight;

	private final long maxEntryWeight;

	// Caffeine's counters cannot be reset, so the summary reports them relative to the last clear.
	private volatile CacheStats statsAtLastClear = CacheStats.empty();

	// Each branch holds results for one timepoint; querying it at another drops them.
	private final Map<String, Long> currentTimepoints = new ConcurrentHashMap<>();

	private final Map<String, Map<LocalDate, AtomicLong>> branchDayHits = new ConcurrentHashMap<>();

	private final AtomicReference<LocalDate> lastHitsPruneDay = new AtomicReference<>();

	private final Supplier<LocalDate> today;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public ECLResultsCache(long maxWeight) {
		this(maxWeight, LocalDate::now);
	}

	ECLResultsCache(long maxWeight, Supplier<LocalDate> today) {
		this.maxWeight = maxWeight;
		this.today = today;
		maxEntryWeight = maxWeight / 10;
		cache = Caffeine.newBuilder()
				.maximumWeight(maxWeight)
				.weigher((ECLCacheKey key, Page<Long> page) -> weigh(key, page))
				// Evict on the writing thread so the bound holds even under a flood of requests.
				.executor(Runnable::run)
				.recordStats()
				.build();
	}

	public Page<Long> get(String path, Date timepoint, String ecl, boolean stated, PageRequest pageRequest) {
		ECLCacheKey key = createKey(path, timepoint, ecl, stated, pageRequest);
		useTimepoint(path, key.timepoint());
		Page<Long> page = cache.getIfPresent(key);
		if (page != null) {
			recordHit(path);
		}
		return page;
	}

	public void put(String path, Date timepoint, String ecl, boolean stated, PageRequest pageRequest, Page<Long> page) {
		ECLCacheKey key = createKey(path, timepoint, ecl, stated, pageRequest);
		Long current = currentTimepoints.get(path);
		// A query that finishes after the branch moved to another timepoint is not cached.
		if ((current != null && current != key.timepoint()) || weigh(key, page) > maxEntryWeight) {
			return;
		}
		cache.put(key, page);
	}

	public boolean isCurrentTimepoint(String path, Date timepoint) {
		Long current = currentTimepoints.get(path);
		return current != null && current == timepoint.getTime();
	}

	/**
	 * @param headTimestamp current head of a branch, or null if it no longer exists. Looked up rather than tracked from commits
	 *                      because rolling back a completed commit moves the head back without notifying commit listeners.
	 */
	public Map<String, Map<String, Long>> getStats(Function<String, Long> headTimestamp) {
		LocalDate today = this.today.get();
		pruneHits(today);
		Map<String, Map<Long, Long>> entriesByBranchAndTimepoint = new HashMap<>();
		for (ECLCacheKey key : cache.asMap().keySet()) {
			entriesByBranchAndTimepoint.computeIfAbsent(key.path(), path -> new HashMap<>()).merge(key.timepoint(), 1L, Long::sum);
		}
		Set<String> branches = new TreeSet<>(entriesByBranchAndTimepoint.keySet());
		branches.addAll(branchDayHits.keySet());
		Map<String, Map<String, Long>> stats = new LinkedHashMap<>();
		for (String branch : branches) {
			Map<String, Long> branchStats = new LinkedHashMap<>();
			Map<Long, Long> entriesByTimepoint = entriesByBranchAndTimepoint.getOrDefault(branch, Map.of());
			Long head = entriesByTimepoint.isEmpty() ? null : headTimestamp.apply(branch);
			branchStats.put("size", head != null ? entriesByTimepoint.getOrDefault(head, 0L) : 0L);
			Map<LocalDate, AtomicLong> dayHits = branchDayHits.getOrDefault(branch, Map.of());
			branchStats.put("hits-today", sumHitsSince(dayHits, today));
			branchStats.put("hits-last7days", sumHitsSince(dayHits, today.minusDays(7)));
			branchStats.put("hits-last30days", sumHitsSince(dayHits, today.minusDays(30)));
			stats.put(branch, branchStats);
		}
		return stats;
	}

	public Map<String, Long> getSummary() {
		cache.cleanUp();
		CacheStats stats = cache.stats().minus(statsAtLastClear);
		Map<String, Long> map = new LinkedHashMap<>();
		map.put("entries", cache.estimatedSize());
		map.put("weight", cache.policy().eviction().map(eviction -> eviction.weightedSize().orElse(0)).orElse(0L));
		map.put("max-weight", maxWeight);
		map.put("hits", stats.hitCount());
		map.put("misses", stats.missCount());
		map.put("evictions", stats.evictionCount());
		return map;
	}

	public void clearCache() {
		currentTimepoints.clear();
		cache.invalidateAll();
		statsAtLastClear = cache.stats();
		branchDayHits.clear();
		logger.info("ECL cache cleared.");
	}

	// Dropping a branch's other results frees space sooner than eviction, which favours frequently hit keys. The scan covers the
	// whole cache (~45ms at 700k entries) but runs only when a branch switches timepoint.
	private void useTimepoint(String path, long timepoint) {
		Long current = currentTimepoints.get(path);
		if (current != null && current == timepoint) {
			return;
		}
		boolean[] switched = {false};
		currentTimepoints.compute(path, (p, previous) -> {
			switched[0] = previous != null && previous != timepoint;
			return timepoint;
		});
		// Only the thread that switched the timepoint scans. A put at another timepoint racing the scan is left to eviction.
		if (switched[0]) {
			cache.asMap().keySet().removeIf(key -> key.path().equals(path) && key.timepoint() != timepoint);
			logger.info("ECL cache expired {}@{}", path, timepoint);
		}
	}

	private void recordHit(String path) {
		LocalDate today = this.today.get();
		pruneHits(today);
		branchDayHits.computeIfAbsent(path, p -> new ConcurrentHashMap<>())
				.computeIfAbsent(today, day -> new AtomicLong()).incrementAndGet();
	}

	// Runs at most once a day; branches with no hits in the last 30 days are forgotten.
	private void pruneHits(LocalDate today) {
		LocalDate lastPruned = lastHitsPruneDay.get();
		if (today.equals(lastPruned) || !lastHitsPruneDay.compareAndSet(lastPruned, today)) {
			return;
		}
		LocalDate cutoff = today.minusDays(30);
		branchDayHits.forEach((branch, dayHits) -> {
			dayHits.keySet().removeIf(day -> day.isBefore(cutoff));
			if (dayHits.isEmpty()) {
				branchDayHits.remove(branch, dayHits);
			}
		});
	}

	private static long sumHitsSince(Map<LocalDate, AtomicLong> dayHits, LocalDate since) {
		return dayHits.entrySet().stream().filter(entry -> !entry.getKey().isBefore(since)).mapToLong(entry -> entry.getValue().get()).sum();
	}

	// PageRequest.equals ignores searchAfter, so it is held separately as a list for value equality.
	private static ECLCacheKey createKey(String path, Date timepoint, String ecl, boolean stated, PageRequest pageRequest) {
		List<Object> searchAfter = pageRequest instanceof SearchAfterPageRequest searchAfterPageRequest && searchAfterPageRequest.getSearchAfter() != null
				? Arrays.asList(searchAfterPageRequest.getSearchAfter()) : null;
		return new ECLCacheKey(path, timepoint.getTime(), normaliseEclString(ecl), stated, pageRequest, searchAfter);
	}

	// Units of roughly one cached id (~25 bytes). An entry's fixed overhead (key, page, cache node) is a few hundred bytes,
	// so it is charged even for empty results, otherwise a flood of distinct empty queries escapes the bound.
	private static final int ENTRY_OVERHEAD_WEIGHT = 14;

	private static int weigh(ECLCacheKey key, Page<Long> page) {
		int searchAfterWeight = key.searchAfter() != null ? key.searchAfter().toString().length() / 16 : 0;
		return ENTRY_OVERHEAD_WEIGHT + page.getNumberOfElements() + key.ecl().length() / 16 + searchAfterWeight;
	}

	private static final Pattern WHITESPACE = Pattern.compile("\\s+");

	// Quoted strings (term filters, concrete values, member field values) are kept as written because they can be case-sensitive.
	static String normaliseEclString(String ecl) {
		if (ecl == null) {
			return "";
		}
		StringBuilder normalised = new StringBuilder(ecl.length());
		StringBuilder unquoted = new StringBuilder();
		int i = 0;
		while (i < ecl.length()) {
			char c = ecl.charAt(i);
			if (c == '|') {
				int termEnd = ecl.indexOf('|', i + 1);
				i = termEnd == -1 ? ecl.length() : termEnd + 1;
			} else if (c == '"') {
				normalised.append(normaliseUnquoted(unquoted));
				unquoted.setLength(0);
				int quoteEnd = closingQuoteEnd(ecl, i + 1);
				normalised.append(ecl, i, quoteEnd);
				i = quoteEnd;
			} else {
				unquoted.append(c);
				i++;
			}
		}
		normalised.append(normaliseUnquoted(unquoted));
		return normalised.toString().trim();
	}

	private static String normaliseUnquoted(CharSequence unquoted) {
		return WHITESPACE.matcher(unquoted.toString().toLowerCase(Locale.ROOT)).replaceAll(" ").replace(" and ", ", ");
	}

	private static int closingQuoteEnd(String ecl, int from) {
		for (int i = from; i < ecl.length(); i++) {
			char c = ecl.charAt(i);
			if (c == '\\') {
				i++;
			} else if (c == '"') {
				return i + 1;
			}
		}
		return ecl.length();
	}

	private record ECLCacheKey(String path, long timepoint, String ecl, boolean stated, PageRequest pageRequest, List<Object> searchAfter) {
	}
}
