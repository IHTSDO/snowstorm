package org.ihtsdo.elasticsnomed.core.data.services.identifier;

import java.util.ArrayList;
import java.util.List;

import io.kaicode.elasticvc.api.BranchService;

import org.ihtsdo.elasticsnomed.TestConfig;
import org.ihtsdo.elasticsnomed.core.data.domain.*;
import org.ihtsdo.elasticsnomed.core.data.services.*;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import static org.junit.Assert.*;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = TestConfig.class)
public class IdentifierServiceTest {
	
	@Autowired 
	IdentifierService identifierService;
	
	@Autowired
	private BranchService branchService;

	@Autowired
	private ConceptService conceptService;
	
	@Before
	public void setup() {
		branchService.create("MAIN");
	}

	@Test
	public void testGetReserveBlock() throws ServiceException {
		List<Concept> testConcepts = createTestData();
		IdentifierReservedBlock block = identifierService.reserveIdentifierBlock(testConcepts);
		Assert.assertEquals(2, block.getIdsAssigned(ComponentType.Concept));
		Assert.assertEquals(4, block.getIdsAssigned(ComponentType.Description));
		Assert.assertEquals(3, block.getIdsAssigned(ComponentType.Relationship));
	}

	private List<Concept> createTestData() throws ServiceException {
		List<Concept> testData = new ArrayList<Concept>();
		//Total 2 concepts + 4 descriptions + 3 relationships = 9 identifiers
		testData.add( conceptService.create( new Concept("1")
						.addDescription(new Description("1", "one"))
						.addDescription(new Description("2", "two"))
						.addRelationship(new Relationship())
						.addRelationship(new Relationship())
				, "MAIN"));
		testData.add( conceptService.create( new Concept("2")
						.addDescription(new Description("3", "one"))
						.addDescription(new Description("4", "two"))
						.addRelationship(new Relationship())
				, "MAIN"));
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
}
