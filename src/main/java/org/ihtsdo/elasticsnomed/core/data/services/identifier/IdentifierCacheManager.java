package org.ihtsdo.elasticsnomed.core.data.services.identifier;

import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.PostConstruct;

import org.ihtsdo.elasticsnomed.core.data.domain.ComponentType;
import org.ihtsdo.elasticsnomed.core.data.services.RuntimeServiceException;
import org.ihtsdo.elasticsnomed.core.data.services.ServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
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

	// Separate cache for each namespace/partition combination configured.
	private Set<IdentifierCache> identifierCaches = new HashSet<>();
	private Thread cacheDaemon;
	private boolean stayAlive = true;

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
	
	public void run() {
		logger.info("Identifier cache manager polling commencing with {} minute period.", pollingIntervalMinutes);
		long pollingIntervalMillis = pollingIntervalMinutes * 60 * 1000;
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
					Thread.sleep(timeRemaining);
				} catch (InterruptedException e) {
					logger.info("Identifier cache manager polling interrupted, shutting down");
					stayAlive = false;
				}
			}
		}
		logger.info("Identifier cache manager polling stopped.");
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
		try {
			int quantityRequired = cache.getMaxCapacity() - cache.identifiersAvailable() + extraRequired;
			logger.info("Topping up {} by {}", cache, quantityRequired);
			List<String> newIdentifiers = identifierSource.reserve(cache.getNamespaceId(), cache.getPartitionId(), quantityRequired);
			cache.topUp(newIdentifiers);
		} catch (Exception e) {
			logger.error("Failed to top-up {}",cache,e);
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
			if (cache.identifiersAvailable() < (double)cache.getMaxCapacity() * criticalLevel && quantityRequired > 1) {
				topUp(cache, quantityRequired);
			} else if (cache.identifiersAvailable() == 0) {
				logger.warn("{} has run dry. Increase polling rate.", cache);
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
		}
		
		if (!requestSatisfied) {
			//If we don't have the right cache, or it doesn't have sufficient availability, then call storage directly
			idBlock.addAll(componentType, identifierSource.generate(namespaceId, partitionId, quantityRequired));
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
	}


}
