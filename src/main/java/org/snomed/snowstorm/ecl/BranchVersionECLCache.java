package org.snomed.snowstorm.ecl;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.SearchAfterPageRequest;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BranchVersionECLCache {

	private final Date head;

	private final Map<ECLCacheEntry, Page<Long>> eclToConceptsCache;

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

	private static final class ECLCacheEntry {

		private final String ecl;
		private final boolean stated;
		private final PageRequest pageRequest;
		private final Object[] searchAfter;

		public ECLCacheEntry(String ecl, boolean stated, PageRequest pageRequest) {
			this.ecl = ecl;
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
