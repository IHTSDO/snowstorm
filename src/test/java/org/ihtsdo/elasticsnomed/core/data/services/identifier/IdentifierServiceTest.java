package org.ihtsdo.elasticsnomed.core.data.services.identifier;

import java.util.ArrayList;
import java.util.List;

import org.ihtsdo.elasticsnomed.AbstractTest;
import org.ihtsdo.elasticsnomed.TestConfig;
import org.ihtsdo.elasticsnomed.core.data.domain.*;
import org.ihtsdo.elasticsnomed.core.data.services.*;
import org.ihtsdo.elasticsnomed.core.data.services.identifier.IdentifierReservedBlock;
import org.ihtsdo.elasticsnomed.core.data.services.identifier.IdentifierService;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = TestConfig.class)
public class IdentifierServiceTest extends AbstractTest {
	
	private final Logger logger = LoggerFactory.getLogger(this.getClass());

	@Autowired 
	private IdentifierService identifierService;
	
	@Autowired
	private IdentifierCacheManager cacheManager;
	
	@Test
	public void testGetReserveBlock() throws ServiceException {
		List<Concept> testConcepts = createTestData();
		IdentifierReservedBlock block = identifierService.reserveIdentifierBlock(testConcepts);
		Assert.assertEquals(2, block.size(ComponentType.Concept));
		Assert.assertEquals(4, block.size(ComponentType.Description));
		Assert.assertEquals(3, block.size(ComponentType.Relationship));
	}
	
	@BeforeClass
	public static void setup() {
		//Need to suspend this process or it will try and register the ids we're using during testing
		IdentifierService.suspendRegistrationProcess(true);
	}
	
	@AfterClass
	public static void tearDown() {
		//And set it going again.
		IdentifierService.suspendRegistrationProcess(false);
	}
	
	/**
	 * Creating 250 concepts puts the requirement for ids above the configured cache
	 * size, so it will satisfy the request by going directly to the identifier store
	 * @throws ServiceException
	 */
	@Test
	public void testGetReserveBlockLarge() throws ServiceException {
		List<Concept> testConcepts = createTestDataLarge(250);
		IdentifierReservedBlock block = identifierService.reserveIdentifierBlock(testConcepts);
		Assert.assertEquals(250, block.size(ComponentType.Concept));
	}
	
	@Test
	public void testRegistration() throws ServiceException, InterruptedException {
		while (cacheManager.topUpInProgress()) {
			logger.warn("IDService unit test blocked as cache top up in progress");
			Thread.sleep(5000);
		}
		
		List<Concept> testConcepts = createTestDataLarge(5);
		IdentifierReservedBlock block = identifierService.reserveIdentifierBlock(testConcepts);
		for (Concept c : testConcepts) {
			Long sctId = block.getId(ComponentType.Concept);
			c.setConceptId(sctId.toString());
		}
		identifierService.registerAssignedIds(block);
		identifierService.registerIdentifiers();
	}

	private List<Concept> createTestData() throws ServiceException {
		List<Concept> testData = new ArrayList<Concept>();
		//Total 2 concepts + 4 descriptions + 3 relationships = 9 identifiers
		//NB Relationship must be given type/destination or the set that holds it will see a duplicate
		testData.add( new Concept(null)
						.addDescription(new Description(null, "one"))
						.addDescription(new Description(null, "two"))
						.addRelationship(new Relationship("123","456"))
						.addRelationship(new Relationship("789", "012")));
		testData.add( new Concept(null)
						.addDescription(new Description(null, "one"))
						.addDescription(new Description(null, "two"))
						.addRelationship(new Relationship("345", "678")));
		return testData;
	}
	
	private List<Concept> createTestDataLarge(int size) throws ServiceException {
		List<Concept> testData = new ArrayList<Concept>();
		for (int x=0; x<size; x++)
			testData.add(new Concept());
		return testData;
	}

	@Test
	public void testIsConceptId() {
		Assert.assertTrue(IdentifierService.isConceptId("1234101"));
		Assert.assertTrue(IdentifierService.isConceptId("1234001"));
		
		Assert.assertFalse(IdentifierService.isConceptId(null));
		Assert.assertFalse(IdentifierService.isConceptId(""));
		Assert.assertFalse(IdentifierService.isConceptId("123123"));
		Assert.assertFalse(IdentifierService.isConceptId("123120"));
		Assert.assertFalse(IdentifierService.isConceptId("101"));
		Assert.assertFalse(IdentifierService.isConceptId("a123101"));
		Assert.assertFalse(IdentifierService.isConceptId("12 3101"));
	}
	
	@Test
	public void testIsDescriptionId() {
		Assert.assertTrue(IdentifierService.isDescriptionId("1234110"));
		Assert.assertTrue(IdentifierService.isDescriptionId("1234013"));
		
		Assert.assertFalse(IdentifierService.isDescriptionId(null));
		Assert.assertFalse(IdentifierService.isDescriptionId(""));
		Assert.assertFalse(IdentifierService.isDescriptionId("123120"));
		Assert.assertFalse(IdentifierService.isDescriptionId("101"));
		Assert.assertFalse(IdentifierService.isDescriptionId("a123101"));
		Assert.assertFalse(IdentifierService.isDescriptionId("12 3101"));
	}
	
	@Test
	public void testIsValid() {
		String errMsg = IdentifierService.isValidId("999480551000087103", ComponentType.Concept);
		Assert.assertNull(errMsg);
		
		errMsg = IdentifierService.isValidId("900000000001211010", ComponentType.Description);
		Assert.assertNull(errMsg);
		
		errMsg = IdentifierService.isValidId("934530801000132110", ComponentType.Description);
		Assert.assertNull(errMsg);
		
		errMsg = IdentifierService.isValidId("100022", ComponentType.Relationship);
		Assert.assertNull(errMsg);
		
		errMsg = IdentifierService.isValidId("900000000000723024", ComponentType.Relationship);
		Assert.assertNull(errMsg);
		
		//Get component type wrong
		errMsg = IdentifierService.isValidId("100022", ComponentType.Concept);
		Assert.assertNotNull(errMsg);
		
		//Get check digit wrong
		errMsg = IdentifierService.isValidId("100029", ComponentType.Relationship);
		Assert.assertNotNull(errMsg);
	}
	
}
