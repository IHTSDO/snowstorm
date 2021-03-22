package org.snomed.snowstorm.core.data.services.identifier;

import org.junit.Assert;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.TestConfig;
import org.snomed.snowstorm.core.data.domain.ComponentType;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = TestConfig.class)
class IdentifierCacheManagerTest extends AbstractTest {
	
	private static final int TEST_NAMESPACE = 1234500;
	private static final String TEST_PARTITION = "10";
	private static final int TEST_CAPACITY = 100;
	private static final int TEST_DEMAND = 10;

	@Autowired 
	private IdentifierCacheManager cacheManager;

	private IdentifierCache testCache;
	
	@BeforeEach
	void stopCacheManager() {
		//Stop the background task so it's not topping up while we're working with the data
		cacheManager.stopBackgroundTask();
		cacheManager.addCache(TEST_NAMESPACE, TEST_PARTITION, TEST_CAPACITY);
		testCache = cacheManager.getCache(TEST_NAMESPACE, TEST_PARTITION);
	}
	
	@Test
	void testTopUp() throws ServiceException, InterruptedException {
		Assert.assertEquals(0, testCache.identifiersAvailable());
		
		//Since the cache is below critical level, asking for identifiers will trigger a top up + additional required
		IdentifierReservedBlock reservedBlock = new IdentifierReservedBlock(0);
		cacheManager.populateIdBlock(reservedBlock, TEST_DEMAND, TEST_NAMESPACE, TEST_PARTITION);
		Assert.assertEquals(TEST_DEMAND, reservedBlock.size(ComponentType.Concept));
		Assert.assertEquals(TEST_CAPACITY, testCache.identifiersAvailable());
		
		//Now take us down to above top up level and prove it remains constant
		int reduction = TEST_CAPACITY - (int)(TEST_CAPACITY * IdentifierCacheManager.topUpLevel);
		cacheManager.populateIdBlock(reservedBlock, reduction, TEST_NAMESPACE, TEST_PARTITION);
		Assert.assertEquals(reduction + TEST_DEMAND, reservedBlock.size(ComponentType.Concept));
		int expectedLevel = TEST_CAPACITY - reduction;
		Assert.assertEquals(expectedLevel, testCache.identifiersAvailable());
		
		//Now drop below top up level and check we top up to capacity
		reduction = TEST_DEMAND + testCache.identifiersAvailable() - (int)(TEST_CAPACITY * IdentifierCacheManager.topUpLevel);
		cacheManager.populateIdBlock(reservedBlock, reduction, TEST_NAMESPACE, TEST_PARTITION);
		expectedLevel -= reduction;
		Assert.assertEquals(expectedLevel, testCache.identifiersAvailable());
		cacheManager.checkTopUpRequired();
		Assert.assertEquals(TEST_CAPACITY, testCache.identifiersAvailable());
		
		//And check we've got a valid concept id.  
		//The dummy service does know how to work with partition ids and check digits
		Long sctid = reservedBlock.getNextId(ComponentType.Concept);
		Assert.assertNull(IdentifierService.isValidId(sctid.toString(), ComponentType.Concept));
	}

	@Test
	void populateIdBlock_ShouldReadFromCache_WhenRequestingConceptIdentifierWithNonInternationalNamespace() throws ServiceException {
		//given
		int namespace = 12345;
		IdentifierReservedBlock identifierReservedBlock = new IdentifierReservedBlock(namespace);
		cacheManager.checkTopUpRequired();
		cacheManager.populateIdBlock(identifierReservedBlock, 1, namespace, "10"); //first request, no cache
		IdentifierCache cache = cacheManager.getCache(namespace, "10");
		int firstRunIdentifiersAvailable = cache.identifiersAvailable();
		cacheManager.topUp(cache, 1);
		int secondRunIdentifiersAvailableBefore = cache.identifiersAvailable();

		//when
		cacheManager.populateIdBlock(identifierReservedBlock, 1, namespace, "10"); //second request, cache
		int secondRunIdentifiersAvailableAfter = cache.identifiersAvailable();

		//then
		Assert.assertEquals(firstRunIdentifiersAvailable, 0); //Cache is empty on first run
		Assert.assertTrue(secondRunIdentifiersAvailableBefore > firstRunIdentifiersAvailable && secondRunIdentifiersAvailableAfter > firstRunIdentifiersAvailable); //Cache is populated
		Assert.assertEquals(secondRunIdentifiersAvailableAfter, secondRunIdentifiersAvailableBefore - 1); //Cache has had identifier removed for this test
	}

	@Test
	void populateIdBlock_ShouldReadFromCache_WhenRequestingDescriptionIdentifierWithNonInternationalNamespace() throws ServiceException {
		//given
		int namespace = 123456;
		IdentifierReservedBlock identifierReservedBlock = new IdentifierReservedBlock(namespace);
		cacheManager.checkTopUpRequired();
		cacheManager.populateIdBlock(identifierReservedBlock, 1, namespace, "11"); //first request, no cache
		IdentifierCache cache = cacheManager.getCache(namespace, "11");
		int firstRunIdentifiersAvailable = cache.identifiersAvailable();
		cacheManager.topUp(cache, 1);
		int secondRunIdentifiersAvailableBefore = cache.identifiersAvailable();

		//when
		cacheManager.populateIdBlock(identifierReservedBlock, 1, namespace, "11"); //second request, cache
		int secondRunIdentifiersAvailableAfter = cache.identifiersAvailable();

		//then
		Assert.assertEquals(firstRunIdentifiersAvailable, 0); //Cache is empty on first run
		Assert.assertTrue(secondRunIdentifiersAvailableBefore > firstRunIdentifiersAvailable && secondRunIdentifiersAvailableAfter > firstRunIdentifiersAvailable); //Cache is populated
		Assert.assertEquals(secondRunIdentifiersAvailableAfter, secondRunIdentifiersAvailableBefore - 1); //Cache has had identifier removed for this test
	}

	@Test
	void populateIdBlock_ShouldReadFromCache_WhenRequestingRelationshipIdentifierWithNonInternationalNamespace() throws ServiceException {
		//given
		int namespace = 1234567;
		IdentifierReservedBlock identifierReservedBlock = new IdentifierReservedBlock(namespace);
		cacheManager.checkTopUpRequired();
		cacheManager.populateIdBlock(identifierReservedBlock, 1, namespace, "12"); //first request, no cache
		IdentifierCache cache = cacheManager.getCache(namespace, "12");
		int firstRunIdentifiersAvailable = cache.identifiersAvailable();
		cacheManager.topUp(cache, 1);
		int secondRunIdentifiersAvailableBefore = cache.identifiersAvailable();

		//when
		cacheManager.populateIdBlock(identifierReservedBlock, 1, namespace, "12"); //second request, cache
		int secondRunIdentifiersAvailableAfter = cache.identifiersAvailable();

		//then
		Assert.assertEquals(firstRunIdentifiersAvailable, 0); //Cache is empty on first run
		Assert.assertTrue(secondRunIdentifiersAvailableBefore > firstRunIdentifiersAvailable && secondRunIdentifiersAvailableAfter > firstRunIdentifiersAvailable); //Cache is populated
		Assert.assertEquals(secondRunIdentifiersAvailableAfter, secondRunIdentifiersAvailableBefore - 1); //Cache has had identifier removed for this test
	}

	@Test
	public void populateIdBlock_ShouldThrowException_WhenRequestingIdentifierForUnsupportedPartition() {
		//given
		int namespace = 12345678;
		IdentifierReservedBlock identifierReservedBlock = new IdentifierReservedBlock(namespace);
		cacheManager.checkTopUpRequired();

		//then
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			//when
			cacheManager.populateIdBlock(identifierReservedBlock, 1, namespace, "13");
		});
	}
	
	@AfterEach
	void restartCacheManager() {
		cacheManager.startBackgroundTask();
	}
}
