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

public class IdentifierCacheManager implements Runnable {

	@Autowired
	IdentifierStorage identifierStorage; 
	
	//Separate cache for each namespace/partition combination configured.
	Set<IdentifierCache> identifierCaches = new HashSet<IdentifierCache>();
	
	private static final Logger logger = LoggerFactory.getLogger(IdentifierCacheManager.class);
	
	private boolean stayAlive = true;
	
	private Thread cacheDaemon;
	
	int pollingInterval = 10; // time between successive polls in minutes
	int lockWaitLimit = 5 * 1000; //Wait max 5 seconds to have access to cache
	int lockRetry = 200;
	
	final static double topUpLevel = 0.7;    //When cache storage falls below 70%, top it back up to max capacity on next poll
	final static double criticalLevel = 0.1; //When cache storage falls below 10%, request immediate top up if more than 1 id requested
	
	IdentifierCache getCache (int namespaceId, String partitionId) {
		for (IdentifierCache thisCache : identifierCaches) {
			if (thisCache.getPartitionId().equals(partitionId) && thisCache.getNamespaceId() == namespaceId) {
				return thisCache;
			}
		}
		return null;
	}
	
	public void initializeCache(int namespaceId, String partitionId, int quantity) {
		identifierCaches.add(new IdentifierCache(namespaceId, partitionId, quantity));
	}
	
	@PostConstruct
	public void startBackgroundTask() {
		if (cacheDaemon != null) {
			throw new IllegalStateException("Unable to start a second Identifier cache manager daemon");
		}
		cacheDaemon = new Thread(this, "CacheManagerDaemon");
		cacheDaemon.start();
	}
	
	public void finishBackgroundTask() {
		stayAlive = false;
		cacheDaemon.interrupt();
		cacheDaemon = null;
	}
	
	public void run() {
		logger.info("Identifier cache manager polling commencing with {} minute period.", pollingInterval);
		long pollingIntervalMs = pollingInterval * 60 * 1000;
		while (stayAlive) {
			Date timePollStarted = new Date();
			checkTopUpRequired();
			Date timePollCompleted = new Date();
			//Have we exceeded a polling interval?
			long timeTaken = timePollCompleted.getTime() - timePollStarted.getTime();
			if (timeTaken > pollingIntervalMs) {
				logger.warn("Identifier cache top ups took longer than polling interval: {}ms", timeTaken);
			} else {
				long timeRemaining = pollingIntervalMs - timeTaken;
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
			List<String> newIdentifiers = identifierStorage.reserve(cache.getNamespaceId(), cache.getPartitionId(), quantityRequired);
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
			idBlock.addAll(componentType, identifierStorage.generate(namespaceId, partitionId, quantityRequired));
		}
	}

	private void waitForLock(IdentifierCache cache) {
		boolean lockedSuccessfully = false;
		int totalWaitTime = 0;
		do {
			lockedSuccessfully = cache.lock();
			if (!lockedSuccessfully) {
				if (totalWaitTime > lockWaitLimit) {
					throw new RuntimeServiceException("Lock wait limit exceeded on identifier cache " + cache);
				}
				try {
					Thread.sleep(lockRetry);
				} catch (InterruptedException e) {
					throw new RuntimeServiceException("Lock wait interrupted",e);
				}
				totalWaitTime += lockRetry;
			}
		} while (!lockedSuccessfully);
	}
}
