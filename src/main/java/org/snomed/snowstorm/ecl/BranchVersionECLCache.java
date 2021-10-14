package org.snomed.snowstorm.ecl;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.SearchAfterPageRequest;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class BranchVersionECLCache {

	private final Date head;

	private final Map<ECLCacheEntry, Page<Long>> eclToConceptsCache;

	private final Map<Calendar, AtomicLong> dayHits = new ConcurrentHashMap<>();

	protected BranchVersionECLCache(Date branchHeadTimestamp) {
		head = branchHeadTimestamp;
		eclToConceptsCache = new ConcurrentHashMap<>();
	}

	public Date getHead() {
		return head;
	}

	public boolean isExpired(Date timepoint) {
		// Expire if timepoint different, regardless of less than or greater than comparison
		return !head.equals(timepoint);
	}

	public Page<Long> get(String ecl, boolean stated, PageRequest pageRequest) {
		return eclToConceptsCache.get(new ECLCacheEntry(ecl, stated, pageRequest));
	}

	public void put(String ecl, boolean stated, PageRequest pageRequest, Page<Long> page) {
		eclToConceptsCache.put(new ECLCacheEntry(ecl, stated, pageRequest), page);
	}

	static String normaliseEclString(String ecl) {
		return ecl.toLowerCase().replaceAll("\\|[^|]*\\|", "").replace("and", ",").replace(" ", "");
	}

	public void recordHit() {
		final Calendar today = getToday();
		AtomicLong hitCount = dayHits.get(today);
		if (hitCount == null) {
			synchronized (dayHits) {
				hitCount = new AtomicLong();
				dayHits.put(today, hitCount);
				if (dayHits.size() > 30) {
					final Calendar daysAgo30 = getPastDate(30);
					dayHits.keySet().stream().filter(calendar -> calendar.before(daysAgo30)).forEach(dayHits::remove);
				}
			}
		}
		hitCount.incrementAndGet();
	}

	public Map<String, Long> getStats() {
		Map<String, Long> stats = new HashMap<>();
		stats.put("size", (long) eclToConceptsCache.size());
		stats.put("hits-today", dayHits.getOrDefault(getToday(), new AtomicLong()).longValue());
		addStat(stats, 7);
		addStat(stats, 30);
		return stats;
	}

	private void addStat(Map<String, Long> stats, int daysInPast) {
		final Calendar daysAgo = getPastDate(daysInPast);
		stats.put(String.format("hits-last%sdays", daysInPast),
				dayHits.entrySet().stream().filter(entry -> !entry.getKey().before(daysAgo)).mapToLong(entry -> entry.getValue().get()).sum());
	}

	private Calendar getPastDate(int daysInPast) {
		final Calendar oneMonthAgo = getToday();
		oneMonthAgo.add(Calendar.DATE, -daysInPast);
		return oneMonthAgo;
	}

	private Calendar getToday() {
		final Calendar today = Calendar.getInstance();
		today.clear(Calendar.MILLISECOND);
		today.clear(Calendar.SECOND);
		today.clear(Calendar.MINUTE);
		today.clear(Calendar.HOUR_OF_DAY);
		return today;
	}

	private static final class ECLCacheEntry {

		private final String ecl;
		private final boolean stated;
		private final PageRequest pageRequest;
		private final Object[] searchAfter;

		public ECLCacheEntry(String ecl, boolean stated, PageRequest pageRequest) {
			this.ecl = ecl != null ? normaliseEclString(ecl) : "";
			this.stated = stated;
			this.pageRequest = pageRequest;
			if (pageRequest instanceof SearchAfterPageRequest) {
				SearchAfterPageRequest searchAfterPageRequest = (SearchAfterPageRequest) pageRequest;
				this.searchAfter = searchAfterPageRequest.getSearchAfter();
			} else {
				this.searchAfter = null;
			}
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;
			ECLCacheEntry that = (ECLCacheEntry) o;
			return stated == that.stated && ecl.equals(that.ecl) && Objects.equals(pageRequest, that.pageRequest) && Arrays.equals(searchAfter, that.searchAfter);
		}

		@Override
		public int hashCode() {
			int result = Objects.hash(ecl, stated, pageRequest);
			result = 31 * result + Arrays.hashCode(searchAfter);
			return result;
		}
	}

}
