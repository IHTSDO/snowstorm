package org.snomed.snowstorm.ecl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ECLResultsCache {

	private final Map<String, BranchVersionECLCache> cacheMap;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public ECLResultsCache() {
		cacheMap = new ConcurrentHashMap<>();
	}

	public BranchVersionECLCache getOrCreateBranchVersionCache(String path, Date timepoint) {
		BranchVersionECLCache branchVersionCache = cacheMap.get(path);
		if (branchVersionCache == null || branchVersionCache.isExpired(timepoint)) {

			if (branchVersionCache != null) {
				logger.info("ECL cache expired {}@{}", path, timepoint.getTime());
			}

			branchVersionCache = new BranchVersionECLCache(timepoint);

			// Replacing the existing item will allow the old cache entry to be garbage collected
			cacheMap.put(path, branchVersionCache);
		}
		return branchVersionCache;
	}
}
