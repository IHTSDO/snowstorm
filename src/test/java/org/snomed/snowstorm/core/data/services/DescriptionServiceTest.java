package org.snomed.snowstorm.core.data.services;

import com.google.common.collect.Lists;
import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.domain.Commit;
import org.elasticsearch.common.util.set.Sets;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.TestConfig;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.data.services.pojo.PageWithBucketAggregations;
import org.snomed.snowstorm.rest.ControllerHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.google.common.collect.Sets.newHashSet;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.snomed.snowstorm.core.data.domain.Concepts.ISA;
import static org.snomed.snowstorm.core.data.domain.Concepts.SNOMEDCT_ROOT;
import static org.snomed.snowstorm.core.data.services.DescriptionService.SearchMode.REGEX;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = TestConfig.class)
public class DescriptionServiceTest extends AbstractTest {

	@Autowired
	private BranchService branchService;

	@Autowired
	private ConceptService conceptService;

	@Autowired
	private ConceptUpdateHelper conceptUpdateHelper;

	@Autowired
	private DescriptionService descriptionService;

	@Autowired
	private ReferenceSetMemberService referenceSetMemberService;

	private ServiceTestUtil testUtil;

	@Before
	public void setup() {
		testUtil = new ServiceTestUtil(conceptService);
	}

	@Test
	public void testDescriptionSearch() throws ServiceException {
		testUtil.createConceptWithPathIdAndTerm("MAIN", "100001", "Heart");
		testUtil.createConceptWithPathIdAndTerm("MAIN", "100002", "Lung");
		testUtil.createConceptWithPathIdAndTerm("MAIN", "100006", "Foot cramps");
		testUtil.createConceptWithPathIdAndTerm("MAIN", "100007", "Foot cramp");
		testUtil.createConceptWithPathIdAndTerm("MAIN", "100003", "Foot bone");
		testUtil.createConceptWithPathIdAndTerm("MAIN", "100004", "Foot");
		testUtil.createConceptWithPathIdAndTerm("MAIN", "100005", "Footwear");

		List<Description> content = descriptionService.findDescriptionsWithAggregations("MAIN", "Foo cr", ServiceTestUtil.PAGE_REQUEST).getContent();
		List<String> actualTerms = content.stream().map(Description::getTerm).collect(Collectors.toList());
		assertEquals(Lists.newArrayList("Foot cramp", "Foot cramps"), actualTerms);

		content = descriptionService.findDescriptionsWithAggregations("MAIN", "Foo", ServiceTestUtil.PAGE_REQUEST).getContent();
		actualTerms = content.stream().map(Description::getTerm).collect(Collectors.toList());
		assertEquals(Lists.newArrayList("Foot", "Footwear", "Foot bone", "Foot cramp", "Foot cramps"), actualTerms);

		content = descriptionService.findDescriptionsWithAggregations("MAIN", "cramps", ServiceTestUtil.PAGE_REQUEST).getContent();
		actualTerms = content.stream().map(Description::getTerm).collect(Collectors.toList());
		assertEquals(Lists.newArrayList("Foot cramps"), actualTerms);
	}

	@Test
	public void testDescriptionSearchCharacterFolding() throws ServiceException {
		testUtil.createConceptWithPathIdAndTermWithLang("MAIN", "100001", "Heart", "en");
		testUtil.createConceptWithPathIdAndTermWithLang("MAIN", "100002", "Lung", "en");
		testUtil.createConceptWithPathIdAndTermWithLang("MAIN", "100003", "Foot cramps", "en");
		testUtil.createConceptWithPathIdAndTermWithLang("MAIN", "100004", "Région de l'Afrique", "fr");
		testUtil.createConceptWithPathIdAndTermWithLang("MAIN", "100005", "déficit de apolipoproteína", "es");

		// In the French language 'é' is NOT configured as an extra character in the alphabet so is folded to it's simpler form 'e' in the index.
		// Searching for either 'é' or 'e' should give the same match.
		assertEquals(1, descriptionService.findDescriptionsWithAggregations("MAIN", "région", newHashSet("fr"), ServiceTestUtil.PAGE_REQUEST).getTotalElements());
		assertEquals(1, descriptionService.findDescriptionsWithAggregations("MAIN", "region", newHashSet("fr"), ServiceTestUtil.PAGE_REQUEST).getTotalElements());

		// In the Spanish language 'é' IS configured as an extra character in the alphabet. It is kept as 'é' in the index.
		// Searching for 'é' should match but 'e' should not because it's considered as a different letter altogether.
		assertEquals(1, descriptionService.findDescriptionsWithAggregations("MAIN", "déficit", newHashSet("es"), ServiceTestUtil.PAGE_REQUEST).getTotalElements());
		assertEquals(0, descriptionService.findDescriptionsWithAggregations("MAIN", "deficit", newHashSet("es"), ServiceTestUtil.PAGE_REQUEST).getTotalElements());
		// Searching for both es and en language codes does not allow matching against the Spanish description with the é character
		// because when the search term is folded for the English language matches are restricted to descriptions with en language code.
		assertEquals(0, descriptionService.findDescriptionsWithAggregations("MAIN", "deficit", newHashSet("es", "en"), ServiceTestUtil.PAGE_REQUEST).getTotalElements());
	}

	@Test
	public void testDescriptionSearchAggregations() throws ServiceException {
		String path = "MAIN";
		Concept root = new Concept(SNOMEDCT_ROOT);
		Concept pizza_2 = new Concept("100002").addRelationship(new Relationship(ISA, SNOMEDCT_ROOT)).addFSN("Food (food)");
		Concept cheesePizza_3 = new Concept("100003").addRelationship(new Relationship(ISA, pizza_2.getId())).addFSN("Cheese Pizza (pizza)");
		Concept reallyCheesyPizza_4 = new Concept("100004").addRelationship(new Relationship(ISA, cheesePizza_3.getId())).addFSN("Really Cheesy Pizza (pizza)");
		Concept reallyCheesyPizza_5 = new Concept("100005").addRelationship(new Relationship(ISA, reallyCheesyPizza_4.getId())).addFSN("So Cheesy Pizza (so pizza)");
		List<Concept> concepts = Lists.newArrayList(root, pizza_2, cheesePizza_3, reallyCheesyPizza_4, reallyCheesyPizza_5);
		setModulesAndLanguage(concepts);
		conceptService.batchCreate(concepts, path);

		referenceSetMemberService.createMembers(path, Sets.newHashSet(
				new ReferenceSetMember(Concepts.CORE_MODULE, Concepts.REFSET_MRCM_DOMAIN, "100003"),
				new ReferenceSetMember(Concepts.CORE_MODULE, Concepts.REFSET_MRCM_DOMAIN, "100004"),
				new ReferenceSetMember(Concepts.CORE_MODULE, Concepts.REFSET_MRCM_ATTRIBUTE_RANGE, "100005")
		));

		PageWithBucketAggregations<Description> page = descriptionService.findDescriptionsWithAggregations(path, "food", PageRequest.of(0, 10));
		assertEquals(1, page.getTotalElements());
		assertEquals(1, page.getContent().size());
		Map<String, Map<String, Long>> foodAggs = page.getBuckets();
		assertEquals("{900000000000207008=1}", getAggregationString("module", foodAggs));
		assertEquals("{english=1}", getAggregationString("language", foodAggs));
		assertEquals("{food=1}", getAggregationString("semanticTags", foodAggs));
		assertEquals("{}", getAggregationString("membership", foodAggs));

		page = descriptionService.findDescriptionsWithAggregations(path, "pizza", PageRequest.of(0, 10));
		assertEquals(3, page.getTotalElements());
		assertEquals(3, page.getContent().size());
		Map<String, Map<String, Long>> pizzaAggs = page.getBuckets();
		assertEquals("{900000000000207008=3}", getAggregationString("module", pizzaAggs));
		assertEquals("{english=3}", getAggregationString("language", pizzaAggs));
		assertEquals("{pizza=2, so pizza=1}", getAggregationString("semanticTags", pizzaAggs));
		assertEquals("{723592007=1, 723589008=2}", getAggregationString("membership", pizzaAggs));

		page = descriptionService.findDescriptionsWithAggregations(path, "pizza", Sets.newHashSet("en"), true, null, "so pizza", true, null, false, null, PageRequest.of(0, 10));
		assertEquals(1, page.getTotalElements());
		assertEquals(1, page.getContent().size());
		Map<String, Map<String, Long>> soPizzaAggs = page.getBuckets();
		assertEquals("{900000000000207008=1}", getAggregationString("module", soPizzaAggs));
		assertEquals("{english=1}", getAggregationString("language", soPizzaAggs));
		assertEquals("{so pizza=1}", getAggregationString("semanticTags", soPizzaAggs));
		assertEquals("{723592007=1}", getAggregationString("membership", soPizzaAggs));
	}

	@Test
	public void testDescriptionSearchGroupByConcept() throws ServiceException {
		String path = "MAIN";
		Concept root = new Concept(SNOMEDCT_ROOT);
		Concept pizza_2 = new Concept("100002").addRelationship(new Relationship(ISA, SNOMEDCT_ROOT)).addFSN("Food (food)");
		Concept cheesePizza_3 = new Concept("100003").addRelationship(new Relationship(ISA, pizza_2.getId())).addFSN("Cheese Pizza (pizza)").addDescription(new Description("Cheese"));
		Concept reallyCheesyPizza_4 = new Concept("100004").addRelationship(new Relationship(ISA, cheesePizza_3.getId())).addFSN("Really Cheesy Pizza (pizza)");
		Concept reallyCheesyPizza_5 = new Concept("100005").addRelationship(new Relationship(ISA, reallyCheesyPizza_4.getId())).addFSN("So Cheesy Pizza (pizza)");
		List<Concept> concepts = Lists.newArrayList(root, pizza_2, cheesePizza_3, reallyCheesyPizza_4, reallyCheesyPizza_5);
		conceptService.batchCreate(concepts, path);

		boolean groupByConcept = false;
		assertEquals(2, descriptionService.findDescriptionsWithAggregations(path, "Cheese", Collections.singleton("en"), null, null, null, null, null, groupByConcept, null, PageRequest.of(0, 10)).getTotalElements());
		groupByConcept = true;
		assertEquals(1, descriptionService.findDescriptionsWithAggregations(path, "Cheese", Collections.singleton("en"), null, null, null, null, null, groupByConcept, null, PageRequest.of(0, 10)).getTotalElements());
	}

	@Test
	public void testDescriptionSearchWithRegex() throws ServiceException {
		String path = "MAIN";
		Concept root = new Concept(SNOMEDCT_ROOT);
		Concept pizza_2 = new Concept("100002").addRelationship(new Relationship(ISA, SNOMEDCT_ROOT)).addFSN("Food (food)");
		Concept cheesePizza_3 = new Concept("100003").addRelationship(new Relationship(ISA, pizza_2.getId())).addFSN("Cheese Pizza (pizza)").addDescription(new Description("Cheese"));
		Concept reallyCheesyPizza_4 = new Concept("100004").addRelationship(new Relationship(ISA, cheesePizza_3.getId())).addFSN("Really Cheesy Pizza (pizza)");
		Concept reallyCheesyPizza_5 = new Concept("100005").addRelationship(new Relationship(ISA, reallyCheesyPizza_4.getId())).addFSN("So Cheesy Pizza (pizza)");
		List<Concept> concepts = Lists.newArrayList(root, pizza_2, cheesePizza_3, reallyCheesyPizza_4, reallyCheesyPizza_5);
		conceptService.batchCreate(concepts, path);

		boolean groupByConcept = false;
		assertEquals(2, descriptionService.findDescriptionsWithAggregations(path, "Cheese.*", Collections.singleton("en"), null, null, null, null, null, groupByConcept, REGEX, PageRequest.of(0, 10)).getTotalElements());
		assertEquals(2, descriptionService.findDescriptionsWithAggregations(path, "Che{2}se.*", Collections.singleton("en"), null, null, null, null, null, groupByConcept, REGEX, PageRequest.of(0, 10)).getTotalElements());
		assertEquals(1, descriptionService.findDescriptionsWithAggregations(path, "^Cheese$", Collections.singleton("en"), null, null, null, null, null, groupByConcept, REGEX, PageRequest.of(0, 10)).getTotalElements());
		assertEquals(2, descriptionService.findDescriptionsWithAggregations(path, "Chees.*", Collections.singleton("en"), null, null, null, null, null, groupByConcept, REGEX, PageRequest.of(0, 10)).getTotalElements());
		assertEquals(1, descriptionService.findDescriptionsWithAggregations(path, "Chees.*piz.*", Collections.singleton("en"), null, null, null, null, null, groupByConcept, REGEX, PageRequest.of(0, 10)).getTotalElements());

		groupByConcept = true;
		assertEquals(1, descriptionService.findDescriptionsWithAggregations(path, "Chees.*", Collections.singleton("en"), null, null, null, null, null, groupByConcept, REGEX, PageRequest.of(0, 10)).getTotalElements());
	}

	@Test
	public void testDescriptionSearchAggregationsSemanticTagFilter() throws ServiceException {
		String path = "MAIN";
		Concept root = new Concept(SNOMEDCT_ROOT);
		Concept pizza_2 = new Concept("100002").addRelationship(new Relationship(ISA, SNOMEDCT_ROOT)).addFSN("Food (food)");
		Concept cheesePizza_3 = new Concept("100003").addRelationship(new Relationship(ISA, pizza_2.getId())).addFSN("Cheese Pizza (pizza)");
		Concept reallyCheesyPizza_4 = new Concept("100004").addRelationship(new Relationship(ISA, cheesePizza_3.getId())).addFSN("Really Cheesy Pizza (pizza)");
		Concept reallyCheesyPizza_5 = new Concept("100005").addRelationship(new Relationship(ISA, reallyCheesyPizza_4.getId())).addFSN("So Cheesy Pizza (pizza)");
		List<Concept> concepts = Lists.newArrayList(root, pizza_2, cheesePizza_3, reallyCheesyPizza_4, reallyCheesyPizza_5);
		setModulesAndLanguage(concepts);
		conceptService.batchCreate(concepts, path);

		referenceSetMemberService.createMembers(path, Sets.newHashSet(
				new ReferenceSetMember(Concepts.CORE_MODULE, Concepts.REFSET_MRCM_DOMAIN, "100003"),
				new ReferenceSetMember(Concepts.CORE_MODULE, Concepts.REFSET_MRCM_DOMAIN, "100004"),
				new ReferenceSetMember(Concepts.CORE_MODULE, Concepts.REFSET_MRCM_ATTRIBUTE_RANGE, "100005")
		));

		List<String> languageCodes = ControllerHelper.getLanguageCodes("en");
		Map<String, Map<String, Long>> allAggregations = descriptionService.findDescriptionsWithAggregations(path, null, languageCodes, true, null, null, null, null, false, null, PageRequest.of(0, 10)).getBuckets();
		assertEquals("{900000000000207008=4}", getAggregationString("module", allAggregations));
		assertEquals("{english=4}", getAggregationString("language", allAggregations));
		assertEquals("{pizza=3, food=1}", getAggregationString("semanticTags", allAggregations));
		assertEquals("{723592007=1, 723589008=2}", getAggregationString("membership", allAggregations));

		String semanticTag = "pizza";
		Map<String, Map<String, Long>> pizzaFilteredAggregations = descriptionService.findDescriptionsWithAggregations(path, null, languageCodes, true, null, semanticTag, null, null, false, null, PageRequest.of(0, 10)).getBuckets();
		assertEquals("{900000000000207008=3}", getAggregationString("module", pizzaFilteredAggregations));
		assertEquals("{english=3}", getAggregationString("language", pizzaFilteredAggregations));
		assertEquals("{pizza=3}", getAggregationString("semanticTags", pizzaFilteredAggregations));
		assertEquals("{723592007=1, 723589008=2}", getAggregationString("membership", pizzaFilteredAggregations));
	}

	@Test
	public void testDescriptionSearchAggregationsActiveConcept() throws ServiceException {
		List<String> languageCodes = ControllerHelper.getLanguageCodes("en");
		String path = "MAIN";
		Concept root = new Concept(SNOMEDCT_ROOT);
		Concept pizza_2 = new Concept("100002").addRelationship(new Relationship(ISA, SNOMEDCT_ROOT)).addFSN("Food (food)");
		Concept cheesePizza_3 = new Concept("100003").addRelationship(new Relationship(ISA, pizza_2.getId())).addFSN("Cheese Pizza (pizza)");
		Concept reallyCheesyPizza_4 = new Concept("100004").addRelationship(new Relationship(ISA, cheesePizza_3.getId())).addFSN("Really Cheesy Pizza (pizza)");
		Concept reallyCheesyPizza_5 = new Concept("100005").addRelationship(new Relationship(ISA, reallyCheesyPizza_4.getId())).addFSN("So Cheesy Pizza (pizza)");
		List<Concept> concepts = Lists.newArrayList(root, pizza_2, cheesePizza_3, reallyCheesyPizza_4, reallyCheesyPizza_5);
		setModulesAndLanguage(concepts);
		conceptService.batchCreate(concepts, path);

		// Make one pizza concept inactive
		reallyCheesyPizza_5.setActive(false);
		conceptService.update(reallyCheesyPizza_5, path);

		Concept concept = conceptService.find(reallyCheesyPizza_5.getId(), path);
		assertFalse(concept.isActive());

		Boolean conceptActive = null;
		assertEquals("Should find all three pizza concepts", 3, descriptionService.findDescriptionsWithAggregations(path, "pizza", languageCodes, true, null, null, conceptActive, null, false, null, PageRequest.of(0, 10)).getTotalElements());

		conceptActive = true;
		assertEquals("Should find the two active pizza concepts", 2, descriptionService.findDescriptionsWithAggregations(path, "pizza", languageCodes, true, null, null, conceptActive, null, false, null, PageRequest.of(0, 10)).getTotalElements());

		conceptActive = false;
		assertEquals("Should find the one inactive pizza concept", 1, descriptionService.findDescriptionsWithAggregations(path, "pizza", languageCodes, true, null, null, conceptActive, null, false, null, PageRequest.of(0, 10)).getTotalElements());
	}

	@Test
	public void testDescriptionSearchAggregationsRefsetFilter() throws ServiceException {
		String path = "MAIN";
		Concept root = new Concept(SNOMEDCT_ROOT);
		Concept pizza_2 = new Concept("100002").addRelationship(new Relationship(ISA, SNOMEDCT_ROOT)).addFSN("Food (food)");
		Concept cheesePizza_3 = new Concept("100003").addRelationship(new Relationship(ISA, pizza_2.getId())).addFSN("Cheese Pizza (pizza)");
		Concept reallyCheesyPizza_4 = new Concept("100004").addRelationship(new Relationship(ISA, cheesePizza_3.getId())).addFSN("Really Cheesy Pizza (pizza)");
		Concept reallyCheesyPizza_5 = new Concept("100005").addRelationship(new Relationship(ISA, reallyCheesyPizza_4.getId())).addFSN("So Cheesy Pizza (so pizza)");
		List<Concept> concepts = Lists.newArrayList(root, pizza_2, cheesePizza_3, reallyCheesyPizza_4, reallyCheesyPizza_5);
		setModulesAndLanguage(concepts);
		conceptService.batchCreate(concepts, path);

		referenceSetMemberService.createMembers(path, Sets.newHashSet(
				new ReferenceSetMember(Concepts.CORE_MODULE, Concepts.REFSET_MRCM_DOMAIN, "100003"),
				new ReferenceSetMember(Concepts.CORE_MODULE, Concepts.REFSET_MRCM_DOMAIN, "100004"),
				new ReferenceSetMember(Concepts.CORE_MODULE, Concepts.REFSET_MRCM_ATTRIBUTE_RANGE, "100005")
		));

		String conceptRefset = null;
		assertEquals(3, descriptionService.findDescriptionsWithAggregations(path, "pizza", Sets.newHashSet("en"), true, null, null, true, conceptRefset, false, null,
				PageRequest.of(0, 10)).getTotalElements());

		conceptRefset = Concepts.REFSET_MRCM_ATTRIBUTE_RANGE;
		assertEquals(1, descriptionService.findDescriptionsWithAggregations(path, "pizza", Sets.newHashSet("en"), true, null, null, true, conceptRefset, false, null,
				PageRequest.of(0, 10)).getTotalElements());
	}

	@Test
	public void testVersionControlOnChildOfMainBranch() throws ServiceException {
		branchService.create("MAIN/A");
		Concept concept = testUtil.createConceptWithPathIdAndTerm("MAIN/A", "100001", "Heart");

		assertEquals(Collections.emptySet(), branchService.findLatest("MAIN/A").getVersionsReplaced().get("Description"));

		Description description = concept.getDescriptions().iterator().next();
		description.setActive(false);
		try (Commit commit = branchService.openCommit("MAIN/A")) {
			description.markChanged();
			conceptUpdateHelper.doSaveBatchDescriptions(Collections.singleton(description), commit);
			commit.markSuccessful();
		}

		assertEquals(Collections.emptySet(), branchService.findLatest("MAIN/A").getVersionsReplaced().get("Description"));
	}

	@Test
	public void testVersionControlOnMainBranch() throws ServiceException {
		Concept concept = testUtil.createConceptWithPathIdAndTerm("MAIN", "100001", "Heart");

		assertEquals(Collections.emptySet(), branchService.findLatest("MAIN").getVersionsReplaced().get("Description"));

		Description description = concept.getDescriptions().iterator().next();
		description.setActive(false);
		try (Commit commit = branchService.openCommit("MAIN")) {
			description.markChanged();
			conceptUpdateHelper.doSaveBatchDescriptions(Collections.singleton(description), commit);
			commit.markSuccessful();
		}

		assertEquals(Collections.emptySet(), branchService.findLatest("MAIN").getVersionsReplaced().get("Description"));
	}

	private String getAggregationString(String name, Map<String, Map<String, Long>> buckets) {
		return buckets.containsKey(name) ? buckets.get(name).toString() : null;
	}

	private void setModulesAndLanguage(List<Concept> concepts) {
		concepts.forEach(c -> c.getDescriptions().forEach(d -> {
			d.setModuleId(Concepts.CORE_MODULE);
			d.setLanguageCode("en");
		}));
	}

}
