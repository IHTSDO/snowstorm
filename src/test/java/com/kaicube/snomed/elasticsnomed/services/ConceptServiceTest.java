package com.kaicube.snomed.elasticsnomed.services;

import com.google.common.collect.Sets;
import com.kaicube.snomed.elasticsnomed.App;
import com.kaicube.snomed.elasticsnomed.TestConfig;
import com.kaicube.snomed.elasticsnomed.domain.Concept;
import com.kaicube.snomed.elasticsnomed.domain.Concepts;
import com.kaicube.snomed.elasticsnomed.domain.Description;
import com.kaicube.snomed.elasticsnomed.domain.LanguageReferenceSetMember;
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

import java.util.*;
import java.util.stream.Collectors;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {App.class, TestConfig.class})
public class ConceptServiceTest {

	public static final PageRequest PAGE_REQUEST = new PageRequest(0, 100);

	@Autowired
	private BranchService branchService;

	@Autowired
	private ConceptService conceptService;

	final Logger logger = LoggerFactory.getLogger(getClass());

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
	public void testFindConceptOnParentBranchUsingBaseVersion() throws InterruptedException {
		conceptService.create(new Concept("1", "one"), "MAIN");
		conceptService.update(new Concept("1", "one1"), "MAIN");

		branchService.create("MAIN/A");

		conceptService.update(new Concept("1", "one2"), "MAIN");

		Assert.assertEquals("one2", conceptService.find("1", "MAIN").getModuleId());
		Assert.assertEquals("one1", conceptService.find("1", "MAIN/A").getModuleId());

		branchService.create("MAIN/A/A1");

		Assert.assertEquals("one1", conceptService.find("1", "MAIN/A/A1").getModuleId());

		conceptService.update(new Concept("1", "one3"), "MAIN/A");

		Assert.assertEquals("one1", conceptService.find("1", "MAIN/A/A1").getModuleId());
	}

	@Test
	public void testSaveConceptWithDescription() {
		final Concept concept = new Concept("50960005", "20020131", true, "900000000000207008", "900000000000074008");
		concept.addDescription(new Description("84923010", "20020131", true, "900000000000207008", "50960005", "en", "900000000000013009", "Bleeding", "900000000000020002"));
		conceptService.create(concept, "MAIN");

		final Concept savedConcept = conceptService.find("50960005", "MAIN");
		Assert.assertNotNull(savedConcept);
		Assert.assertEquals(1, savedConcept.getDescriptions().size());
		final Description description = savedConcept.getDescriptions().iterator().next();
		Assert.assertEquals("84923010", description.getDescriptionId());
		Assert.assertEquals(0, description.getAcceptabilityMap().size());
	}

	@Test
	public void testSaveConceptWithDescriptionAndAcceptability() {
		final Concept concept = new Concept("50960005", "20020131", true, "900000000000207008", "900000000000074008");
		concept.addDescription(new Description("84923010", "20020131", true, "900000000000207008", "50960005", "en", "900000000000013009", "Bleeding", "900000000000020002"));
		conceptService.create(concept, "MAIN");
		conceptService.create(new LanguageReferenceSetMember(UUID.randomUUID().toString(), "20020131", true, "900000000000207008", "900000000000509007", "84923010", Concepts.PREFERRED), "MAIN");

		final Concept savedConcept = conceptService.find("50960005", "MAIN");
		Assert.assertNotNull(savedConcept);
		Assert.assertEquals(1, savedConcept.getDescriptions().size());
		final Description description = savedConcept.getDescriptions().iterator().next();
		Assert.assertEquals("84923010", description.getDescriptionId());
		Assert.assertEquals(1, description.getAcceptabilityMap().size());
		Assert.assertEquals(Concepts.PREFERRED, description.getAcceptabilityMap().get("900000000000509007"));
	}

	@Test
	public void testDescriptionSearch() {
		createConceptWithPathIdAndTerms("MAIN", "1", "Heart");
		createConceptWithPathIdAndTerms("MAIN", "2", "Lung");
		createConceptWithPathIdAndTerms("MAIN", "6", "Foot cramps");
		createConceptWithPathIdAndTerms("MAIN", "7", "Foot cramp");
		createConceptWithPathIdAndTerms("MAIN", "3", "Foot bone");
		createConceptWithPathIdAndTerms("MAIN", "4", "Foot");
		createConceptWithPathIdAndTerms("MAIN", "5", "Footwear");

		final List<Description> content = conceptService.findDescriptions("MAIN", "Foo* cr*", PAGE_REQUEST).getContent();
		assertSearchResults(content, 5, "Foot cramp", "Foot cramps");

		final List<Description> fooMatches = conceptService.findDescriptions("MAIN", "Foo*", PAGE_REQUEST).getContent();
		assertSearchResults(fooMatches, 5);

		branchService.create("MAIN/A");

		createConceptWithPathIdAndTerms("MAIN/A", "8", "Foot care");

		final List<Description> footOnMain = conceptService.findDescriptions("MAIN", "Foot", PAGE_REQUEST).getContent();
		Assert.assertEquals(4, footOnMain.size());

		final List<Description> footOnA = conceptService.findDescriptions("MAIN/A", "Foot", PAGE_REQUEST).getContent();
		Assert.assertEquals(5, footOnA.size());
		Assert.assertTrue(toTermSet(footOnA).contains("Foot care"));
	}

	@Test
	public void testLatestVersionMatch() {
		createConceptWithPathIdAndTerms("MAIN", "1", "Heart");

		Assert.assertEquals(1, conceptService.findDescriptions("MAIN", "Heart", PAGE_REQUEST).getNumberOfElements());
		Assert.assertEquals(0, conceptService.findDescriptions("MAIN", "Bone", PAGE_REQUEST).getNumberOfElements());

		// Create branch (base point is now)
		branchService.create("MAIN/A");

		// Make further changes ahead of A's base point on MAIN
		final Concept concept = conceptService.find("1", "MAIN");
		concept.getDescriptions().iterator().next().setTerm("Bone");
		conceptService.update(concept, "MAIN");

		Assert.assertEquals(0, conceptService.findDescriptions("MAIN", "Heart", PAGE_REQUEST).getNumberOfElements());
		Assert.assertEquals(1, conceptService.findDescriptions("MAIN", "Bone", PAGE_REQUEST).getNumberOfElements());

		printAllDescriptions("MAIN");
		printAllDescriptions("MAIN/A");

		Assert.assertEquals("Branch A should see old version of concept because of old base point.", 1, conceptService.findDescriptions("MAIN/A", "Heart", PAGE_REQUEST).getNumberOfElements());
		Assert.assertEquals("Branch A should not see new version of concept because of old base point.", 0, conceptService.findDescriptions("MAIN/A", "Bone", PAGE_REQUEST).getNumberOfElements());

		final Concept concept1 = conceptService.find("1", "MAIN");
		Assert.assertEquals(1, concept1.getDescriptions().size());
	}

	private void printAllDescriptions(String path) {
		final Page<Description> descriptions = conceptService.findDescriptions(path, null, PAGE_REQUEST);
		logger.info("Description on " + path);
		for (Description description : descriptions) {
			logger.info("{}", description);
		}
	}

	private void assertSearchResults(List<Description> content, int expectedSize, String... topHitsUnordered) {
		try {
			Assert.assertEquals(expectedSize, content.size());
			final HashSet<String> topHitSet = Sets.newHashSet(topHitsUnordered);
			for (int i = 0; i < topHitsUnordered.length; i++) {
				final String term = content.get(i++).getTerm();
				Assert.assertTrue("Hit " + i + " '" + term + "' within expected top hits.", topHitSet.contains(term));
			}
		} catch (AssertionError e) {
			printAll(content);
			throw e;
		}
	}

	private void createConceptWithPathIdAndTerms(String path, String conceptId, String... terms) {
		final Concept concept = new Concept(conceptId);
		for (String term : terms) {
			final Description description = new Description(term);
			description.setDescriptionId(UUID.randomUUID().toString());
			concept.addDescription(description);
		}
		conceptService.create(concept, path);
	}

	private void printAll(List<Description> content) {
		logger.info(content.size() + " descriptions:");
		for (Description description : content) {
			logger.info(description.getTerm());
		}
	}

	private Set<String> toTermSet(Collection<Description> descriptions) {
		return descriptions.stream().map(Description::getTerm).collect(Collectors.toSet());
	}

	@After
	public void tearDown() {
		conceptService.deleteAll();
		branchService.deleteAll();
	}
}