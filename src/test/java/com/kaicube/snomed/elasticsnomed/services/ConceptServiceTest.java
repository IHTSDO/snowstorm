package com.kaicube.snomed.elasticsnomed.services;

import com.kaicube.snomed.elasticsnomed.App;
import com.kaicube.snomed.elasticsnomed.TestConfig;
import com.kaicube.snomed.elasticsnomed.domain.Concept;
import com.kaicube.snomed.elasticsnomed.domain.Description;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.List;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = TestConfig.class)
public class ConceptServiceTest {

	public static final PageRequest PAGE_REQUEST = new PageRequest(0, 100);

	@Autowired
	private BranchService branchService;

	@Autowired
	private ConceptService conceptService;

	@Before
	public void setup() {
		branchService.create("MAIN");
	}

	@Test
	public void testConceptCreationBranchingVisibility() {
		Assert.assertNull("Concept 1 does not exist on MAIN.", conceptService.find("1", "MAIN"));

		conceptService.create(new Concept("1", "one"), "MAIN");

		final Concept c1 = conceptService.find("1", "MAIN");
		Assert.assertNotNull("Concept 1 exists on MAIN.", c1);
		Assert.assertEquals("MAIN", c1.getFatPath());
		Assert.assertEquals("one", c1.getModuleId());

		branchService.create("MAIN/A");
		conceptService.create(new Concept("2", "two"), "MAIN/A");
		Assert.assertNull("Concept 2 does not exist on MAIN.", conceptService.find("2", "MAIN"));
		Assert.assertNotNull("Concept 2 exists on branch A.", conceptService.find("2", "MAIN/A"));
		Assert.assertNotNull("Concept 1 is accessible on branch A because of the base time.", conceptService.find("1", "MAIN/A"));

		conceptService.create(new Concept("3", "three"), "MAIN");
		Assert.assertNull("Concept 3 is not accessible on branch A because created after branching.", conceptService.find("3", "MAIN/A"));
		Assert.assertNotNull(conceptService.find("3", "MAIN"));
	}

	@Test
	public void testMultipleConceptVersionsOnOneBranch() {
		conceptService.create(new Concept("1", "one"), "MAIN");

		final Concept concept1 = conceptService.find("1", "MAIN");
		Assert.assertEquals("one", concept1.getModuleId());

		conceptService.update(new Concept("1", "oneee"), "MAIN");

		final Concept concept1Version2 = conceptService.find("1", "MAIN");
		Assert.assertEquals("oneee", concept1Version2.getModuleId());
	}

	@Test
	public void testUpdateExistingConceptOnNewBranch() throws InterruptedException {
		conceptService.create(new Concept("1", "one"), "MAIN");

		branchService.create("MAIN/A");

		conceptService.update(new Concept("1", "one1"), "MAIN/A");

		Assert.assertEquals("one", conceptService.find("1", "MAIN").getModuleId());
		Assert.assertEquals("one1", conceptService.find("1", "MAIN/A").getModuleId());
	}

	@Test
	public void testSaveConceptWithDescription() {
		final Concept concept = new Concept("50960005", "20020131", true, "900000000000207008", "900000000000074008");
		concept.addDescription(new Description("84923010", "20020131", true, "900000000000207008", "50960005", "en", "900000000000013009", "Bleeding", "900000000000020002"));
		conceptService.create(concept, "MAIN");

		final Concept savedConcept = conceptService.find("50960005", "MAIN");
		Assert.assertNotNull(savedConcept);
		Assert.assertEquals(1, savedConcept.getDescriptions().size());
		Assert.assertEquals("84923010", savedConcept.getDescriptions().iterator().next().getDescriptionId());
	}

	@Test
	// TODO: Test sort order for relevance and consistency
	public void testDescriptionSearch() {
		createConceptWithPathIdAndTerms("MAIN", "1", "Heart");
		createConceptWithPathIdAndTerms("MAIN", "2", "Lung");
		createConceptWithPathIdAndTerms("MAIN", "3", "Foot bone");
		createConceptWithPathIdAndTerms("MAIN", "4", "Foot");
		createConceptWithPathIdAndTerms("MAIN", "5", "Footwear");
		createConceptWithPathIdAndTerms("MAIN", "6", "Foot cramps");

		Assert.assertEquals(3, conceptService.findDescriptions("MAIN", "Foot", PAGE_REQUEST).getContent().size());

		Assert.assertEquals(4, conceptService.findDescriptions("MAIN", "Foo*", PAGE_REQUEST).getContent().size());

		branchService.create("MAIN/A");
		createConceptWithPathIdAndTerms("MAIN/A", "7", "Foot care");
		Assert.assertEquals(3, conceptService.findDescriptions("MAIN", "Foot", PAGE_REQUEST).getContent().size());

		Assert.assertEquals(4, conceptService.findDescriptions("MAIN/A", "Foot", PAGE_REQUEST).getContent().size());
	}

	private void createConceptWithPathIdAndTerms(String path, String conceptId, String... terms) {
		final Concept concept = new Concept(conceptId);
		for (String term : terms) {
			concept.addDescription(new Description(term));
		}
		conceptService.create(concept, path);
	}

	@After
	public void tearDown() {
		conceptService.deleteAll();
		branchService.deleteAll();
	}
}