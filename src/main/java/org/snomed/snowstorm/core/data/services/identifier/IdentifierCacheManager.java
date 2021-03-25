package org.snomed.snowstorm.core.data.services.identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.core.data.domain.ComponentType;
import org.snomed.snowstorm.core.data.services.RuntimeServiceException;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.*;

public class IdentifierCacheManager implements Runnable {

	// Time between successive polls in minutes
	private int pollingIntervalMinutes = 10;

	// Wait max to have access to cache
	private int lockWaitLimitMilliseconds = 5 * 1000;
	private int lockRetryMilliseconds = 200;

	// Proportion below which cache will be topped up on next poll
	final static double topUpLevel = 0.7;

	// Proportion below which cache will be topped up during next bulk request
	private final static double criticalLevel = 0.1;

	@Autowired
	private IdentifierSource identifierSource;

	@Value("${cis.cache.concept-prefetch-count}")
	private int conceptIdPrefetchCount;

	// Separate cache for each namespace/partition combination configured.
	private Set<IdentifierCache> identifierCaches = Collections.synchronizedSet(new HashSet<>());
	private Thread cacheDaemon;
	private boolean stayAlive = true;
	boolean isSleeping = false;

	private static final Logger logger = LoggerFactory.getLogger(IdentifierCacheManager.class);

	public void addCache(int namespaceId, String partitionId, int quantity) {
		identifierCaches.add(new IdentifierCache(namespaceId, partitionId, quantity));
	}

	@PostConstruct
	public void startBackgroundTask() {
		if (cacheDaemon != null) {
			throw new IllegalStateException("Unable to start a second Identifier cache manager daemon");
		}
		cacheDaemon = new Thread(this, "IdentifierCacheManagerDaemon");
		cacheDaemon.start();
	}

	@PreDestroy
	public void shutdownPolling() {
		stopBackgroundTask();
	}

	public void run() {
		logger.info("Identifier cache manager polling commencing with {} second period.", pollingIntervalMinutes);
		long pollingIntervalMillis = pollingIntervalMinutes * (long)1000;
		while (stayAlive) {
			Date timePollStarted = new Date();
			checkTopUpRequired();
			Date timePollCompleted = new Date();
			//Have we exceeded a polling interval?
			long timeTaken = timePollCompleted.getTime() - timePollStarted.getTime();
			if (timeTaken > pollingIntervalMillis) {
				logger.warn("Identifier cache top ups took longer than polling interval: {}ms", timeTaken);
			} else {
				long timeRemaining = pollingIntervalMillis - timeTaken;
				try {
					isSleeping = true;
					//Don't mind being interrupted while sleeping.
					Thread.sleep(timeRemaining);
					isSleeping = false;
				} catch (InterruptedException e) {
					logger.info("Identifier cache manager sleep interrupted.");
				}
			}
		}
		logger.info("Identifier cache manager polling stopped.");
	}
	
	public boolean topUpInProgress() {
		for (IdentifierCache thisCache : identifierCaches) {
			if (thisCache.isTopUpInProgress()) {
				return true;
			}
		}
		return false;
	}

	void checkTopUpRequired() {
		try {
			//Work through each cache and see if number of identifiers is below top up level
			for (IdentifierCache thisCache : identifierCaches) {
				if ((double)thisCache.identifiersAvailable() < (double)thisCache.getMaxCapacity() * topUpLevel) {
					topUp(thisCache, 0);
				}
			}
		} catch (Exception e) {
			logger.error("Exception during identifier cache top-up",e);
		}
	}

	void topUp(IdentifierCache cache, int extraRequired) {
		if (cache.isTopUpInProgress()) {
			logger.warn("Top-up already in progress for {}", cache);
			return;
		}
		cache.setTopUpInProgress(true);
		int quantityRequired = cache.getMaxCapacity() - cache.identifiersAvailable() + extraRequired;
		try {
			logger.info("Topping up {} by {}", cache, quantityRequired);
			List<Long> newIdentifiers = identifierSource.reserveIds(cache.getNamespaceId(), cache.getPartitionId(), quantityRequired);
			cache.topUp(newIdentifiers);
			logger.info("Top up of {} by {} complete",cache, quantityRequired);
		} catch (Exception e) {
			logger.error("Failed to top-up {} with {} identifiers ",cache, quantityRequired,e);
		} finally {
			cache.setTopUpInProgress(false);
		}
	}

	//Attempt to fill reserved block from cache, or directly from store if insufficient cached ids available
	public void populateIdBlock(IdentifierReservedBlock idBlock, int quantityRequired, int namespaceId, String partitionId) throws ServiceException {
		//Did we in fact request any at all?
		if (quantityRequired == 0) {
			return;
		}
		
		//Do we have a cache for this namespace/partition?
		IdentifierCache cache = getCache(namespaceId, partitionId);
		ComponentType componentType = ComponentType.getTypeFromPartition(partitionId);
		boolean requestSatisfied = false;
		if (cache != null) {
			//Does cache need topping up anyway?
			if (cache.identifiersAvailable() == 0 || 
					( cache.identifiersAvailable() < (double)cache.getMaxCapacity() * criticalLevel 
							&& quantityRequired > 5)
					){
				topUp(cache, quantityRequired);
			}

			//Does it have enough available?
			if (cache.identifiersAvailable() > quantityRequired) {
				waitForLock(cache);
				for (int i=0; i < quantityRequired; i++) {
					idBlock.addId(componentType, cache.getIdentifier());
				}
				cache.unlock();
				requestSatisfied = true;
			}
		} else {
			//If no cache available & not requesting for International (as already prefetched),
			//then prefetch additional identifiers for subsequent requests.
			int prefetchQuantity;
			switch (componentType) {
				case Concept:
					prefetchQuantity = conceptIdPrefetchCount;
					break;
				case Description:
					prefetchQuantity = conceptIdPrefetchCount * 2;
					break;
				case Relationship:
					prefetchQuantity = conceptIdPrefetchCount * 4;
					break;
				default:
					throw new IllegalArgumentException("Cache does not support prefetching identifiers for " + componentType);
			}
			addCache(namespaceId, partitionId, prefetchQuantity);
		}
		
		if (!requestSatisfied) {
			//If we don't have the right cache, or it doesn't have sufficient availability, then call storage directly
			idBlock.addAll(componentType, identifierSource.reserveIds(namespaceId, partitionId, quantityRequired));
		}
	}

	private void waitForLock(IdentifierCache cache) {
		boolean lockedSuccessfully = false;
		int totalWaitTime = 0;
		do {
			lockedSuccessfully = cache.lock();
			if (!lockedSuccessfully) {
				if (totalWaitTime > lockWaitLimitMilliseconds) {
					throw new RuntimeServiceException("Lock wait limit exceeded on identifier cache " + cache);
				}
				try {
					Thread.sleep(lockRetryMilliseconds);
				} catch (InterruptedException e) {
					throw new RuntimeServiceException("Lock wait interrupted",e);
				}
				totalWaitTime += lockRetryMilliseconds;
			}
		} while (!lockedSuccessfully);
	}

	IdentifierCache getCache(int namespaceId, String partitionId) {
		for (IdentifierCache thisCache : identifierCaches) {
			if (thisCache.getPartitionId().equals(partitionId) && thisCache.getNamespaceId() == namespaceId) {
				return thisCache;
			}
		}
		return null;
	}

	public void stopBackgroundTask() {
		stayAlive = false;
		cacheDaemon.interrupt();
		cacheDaemon = null;
		identifierCaches.clear();
	}


}
