package org.snomed.snowstorm.core.data.services.identifier;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.TestConfig;
import org.snomed.snowstorm.core.data.domain.ComponentType;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = TestConfig.class)
public class IdentifierCacheManagerTest extends AbstractTest {
	
	private static final int TEST_NAMESPACE = 1234500;
	private static final String TEST_PARTITION = "10";
	private static final int TEST_CAPACITY = 100;
	private static final int TEST_DEMAND = 10;

	@Autowired 
	private IdentifierCacheManager cacheManager;

	private IdentifierCache testCache;
	
	@Before
	public void stopCacheManager() {
		//Stop the background task so it's not topping up while we're working with the data
		cacheManager.stopBackgroundTask();
		cacheManager.addCache(TEST_NAMESPACE, TEST_PARTITION, TEST_CAPACITY);
		testCache = cacheManager.getCache(TEST_NAMESPACE, TEST_PARTITION);
	}
	
	@Test
	public void testTopUp() throws ServiceException, InterruptedException {
		Assert.assertEquals(0, testCache.identifiersAvailable());
		
		//Since the cache is below critical level, asking for identifiers will trigger a top up + additional required
		IdentifierReservedBlock reservedBlock = new IdentifierReservedBlock();
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
		Long sctid = reservedBlock.getId(ComponentType.Concept);
		Assert.assertNull(IdentifierService.isValidId(sctid.toString(), ComponentType.Concept));
	}
	
	@After
	public void restartCacheManager() {
		cacheManager.startBackgroundTask();
	}
}
