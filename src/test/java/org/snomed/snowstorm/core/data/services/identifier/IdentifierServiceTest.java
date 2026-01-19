package org.snomed.snowstorm.core.data.services.identifier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.core.data.domain.ComponentType;
import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.core.data.domain.Description;
import org.snomed.snowstorm.core.data.domain.Relationship;
import org.snomed.snowstorm.core.data.domain.jobs.IdentifiersForRegistration;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
class IdentifierServiceTest extends AbstractTest {
	
	private final Logger logger = LoggerFactory.getLogger(this.getClass());

	@Autowired 
	private IdentifierService identifierService;
	
	@Autowired
	private IdentifierCacheManager cacheManager;
	
	@Test
	void testGetReserveBlock() throws ServiceException {
		List<Concept> testConcepts = createTestData();
		IdentifierReservedBlock block = identifierService.reserveIdentifierBlock(testConcepts, null);
		assertEquals(2, block.size(ComponentType.Concept));
		assertEquals(4, block.size(ComponentType.Description));
		assertEquals(3, block.size(ComponentType.Relationship));
	}
	
	@Test
	void testNamespaceIdentifierMap() {
		List<IdentifiersForRegistration> ifr = new ArrayList<>();
		ifr.add(new IdentifiersForRegistration(0, Collections.singletonList(123456L)));
		ifr.add(new IdentifiersForRegistration(1000003, Collections.singletonList(336331000003125L)));
		Map<Integer, Set<Long>> namespaceIdentifierMap = identifierService.getNamespaceIdentifierMap(ifr);
		assertEquals(2, namespaceIdentifierMap.size());
	}
	
	/**
	 * Creating 250 concepts puts the requirement for ids above the configured cache
	 * size, so it will satisfy the request by going directly to the identifier store
	 * @throws ServiceException
	 */
	@Test
	void testGetReserveBlockLarge() throws ServiceException {
		List<Concept> testConcepts = createTestDataLarge(250);
		IdentifierReservedBlock block = identifierService.reserveIdentifierBlock(testConcepts, null);
		assertEquals(250, block.size(ComponentType.Concept));
	}
	
	@Test
	void testRegistration() throws ServiceException, InterruptedException {
		while (cacheManager.topUpInProgress()) {
			logger.warn("IDService unit test blocked as cache top up in progress");
			Thread.sleep(5000);
		}
		
		List<Concept> testConcepts = createTestDataLarge(5);
		IdentifierReservedBlock block = identifierService.reserveIdentifierBlock(testConcepts, null);
		for (Concept c : testConcepts) {
			Long sctId = block.getNextId(ComponentType.Concept);
			c.setConceptId(sctId.toString());
		}
		assertAll(() -> identifierService.persistAssignedIdsForRegistration(block));
		assertAll(() -> identifierService.registerIdentifiers());

	}

	private List<Concept> createTestData() {
		List<Concept> testData = new ArrayList<>();
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
		List<Concept> testData = new ArrayList<>();
		for (int x=0; x<size; x++)
			testData.add(new Concept());
		return testData;
	}

	@Test
	void testIsConceptId() {
		assertTrue(IdentifierService.isConceptId("1234101"));
		assertTrue(IdentifierService.isConceptId("1234001"));
		
		assertFalse(IdentifierService.isConceptId(null));
		assertFalse(IdentifierService.isConceptId(""));
		assertFalse(IdentifierService.isConceptId("123123"));
		assertFalse(IdentifierService.isConceptId("123120"));
		assertFalse(IdentifierService.isConceptId("101"));
		assertFalse(IdentifierService.isConceptId("a123101"));
		assertFalse(IdentifierService.isConceptId("12 3101"));
	}
	
	@Test
	void testIsDescriptionId() {
		assertTrue(IdentifierService.isDescriptionId("1234110"));
		assertTrue(IdentifierService.isDescriptionId("1234013"));
		
		assertFalse(IdentifierService.isDescriptionId(null));
		assertFalse(IdentifierService.isDescriptionId(""));
		assertFalse(IdentifierService.isDescriptionId("123120"));
		assertFalse(IdentifierService.isDescriptionId("101"));
		assertFalse(IdentifierService.isDescriptionId("a123101"));
		assertFalse(IdentifierService.isDescriptionId("12 3101"));
	}
	
	@Test
	void testIsValid() {
		String errMsg = IdentifierService.isValidId("999480551000087103", ComponentType.Concept);
		assertNull(errMsg);
		
		errMsg = IdentifierService.isValidId("900000000001211010", ComponentType.Description);
		assertNull(errMsg);
		
		errMsg = IdentifierService.isValidId("934530801000132110", ComponentType.Description);
		assertNull(errMsg);
		
		errMsg = IdentifierService.isValidId("100022", ComponentType.Relationship);
		assertNull(errMsg);
		
		errMsg = IdentifierService.isValidId("900000000000723024", ComponentType.Relationship);
		assertNull(errMsg);
		
		//Get component type wrong
		errMsg = IdentifierService.isValidId("100022", ComponentType.Concept);
		assertNotNull(errMsg);
		
		//Get check digit wrong
		errMsg = IdentifierService.isValidId("100029", ComponentType.Relationship);
		assertNotNull(errMsg);
	}
	
}
