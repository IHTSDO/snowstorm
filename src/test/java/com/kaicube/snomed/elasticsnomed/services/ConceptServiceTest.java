package com.kaicube.snomed.elasticsnomed.services;

import com.kaicube.snomed.elasticsnomed.App;
import com.kaicube.snomed.elasticsnomed.domain.Concept;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = App.class)
public class ConceptServiceTest {

	@Autowired
	private BranchService branchService;

	@Autowired
	private ConceptService conceptService;

	@Test
	public void testConceptBranching() {
		branchService.create("MAIN");

		Assert.assertNull("Concept 1 does not exist on MAIN.", conceptService.find(1l, "MAIN"));

		conceptService.create(new Concept(1l, "one"), "MAIN");

		final Concept c1 = conceptService.find(1l, "MAIN");
		Assert.assertNotNull("Concept 1 exists on MAIN.", c1);
		Assert.assertEquals("MAIN", c1.getFatPath());
		Assert.assertEquals("one", c1.getFsn());

		System.out.println(branchService.create("MAIN/A"));
		conceptService.create(new Concept(2l, "two"), "MAIN/A");
		Assert.assertNull("Concept 2 does not exist on MAIN.", conceptService.find(2l, "MAIN"));
		Assert.assertNotNull("Concept 2 exists on branch A.", conceptService.find(2l, "MAIN/A"));
		Assert.assertNotNull("Concept 1 is accessible on branch A because of the base time.", conceptService.find(1l, "MAIN/A"));

		conceptService.create(new Concept(3l, "three"), "MAIN");
		Assert.assertNull("Concept 3 is not accessible on branch A because created after branching.", conceptService.find(3l, "MAIN/A"));
		Assert.assertNotNull(conceptService.find(3l, "MAIN"));
	}

	@After
	public void tearDown() {
		conceptService.deleteAll();
		branchService.deleteAll();
	}
}