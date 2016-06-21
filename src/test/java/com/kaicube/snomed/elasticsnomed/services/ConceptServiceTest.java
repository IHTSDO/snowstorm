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
		Assert.assertNull(conceptService.find(1l, "MAIN"));

		conceptService.create(new Concept(1l, "one"), "MAIN");

		final Concept c1 = conceptService.find(1l, "MAIN");
		Assert.assertNotNull(c1);
		Assert.assertEquals("MAIN", c1.getPath());
		Assert.assertEquals("one", c1.getFsn());

		conceptService.create(new Concept(2l, "two"), "MAIN_A");
		Assert.assertNull(conceptService.find(2l, "MAIN"));
		Assert.assertNotNull(conceptService.find(2l, "MAIN_A"));
		Assert.assertNotNull(conceptService.find(1l, "MAIN_A"));

		conceptService.create(new Concept(3l, "three"), "MAIN");
		Assert.assertNull(conceptService.find(3l, "MAIN_A"));
		Assert.assertNotNull(conceptService.find(3l, "MAIN"));
	}

	@After
	public void tearDown() {
		conceptService.deleteAll();
		branchService.deleteAll();
	}
}