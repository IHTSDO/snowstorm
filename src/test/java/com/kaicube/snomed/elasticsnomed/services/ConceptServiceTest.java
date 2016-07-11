package com.kaicube.snomed.elasticsnomed.services;

import com.google.common.collect.Sets;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.*;

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
	// TODO: Test sort order for relevance and consistency
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
		System.out.println(content.size() + " descriptions:");
		for (Description description : content) {
			System.out.println(description.getTerm());
		}
		System.out.println();
	}

	private Set<String> toTermSet(Collection<Description> descriptions) {
		Set<String> terms = new HashSet<>();
		for (Description description : descriptions) {
			terms.add(description.getTerm());
		}
		return terms;
	}

	@After
	public void tearDown() {
		conceptService.deleteAll();
		branchService.deleteAll();
	}
}