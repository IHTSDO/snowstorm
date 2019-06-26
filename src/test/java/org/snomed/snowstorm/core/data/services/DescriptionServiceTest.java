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
import org.snomed.snowstorm.rest.ControllerHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.snomed.snowstorm.core.data.domain.Concepts.ISA;
import static org.snomed.snowstorm.core.data.domain.Concepts.SNOMEDCT_ROOT;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = TestConfig.class)
public class DescriptionServiceTest extends AbstractTest {

	public static final String REGEX = "regex";

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
		branchService.create("MAIN");
		testUtil = new ServiceTestUtil(conceptService);
	}

	@Test
	public void testDescriptionSearch() throws ServiceException {
		testUtil.createConceptWithPathIdAndTerms("MAIN", "100001", "Heart");
		testUtil.createConceptWithPathIdAndTerms("MAIN", "100002", "Lung");
		testUtil.createConceptWithPathIdAndTerms("MAIN", "100006", "Foot cramps");
		testUtil.createConceptWithPathIdAndTerms("MAIN", "100007", "Foot cramp");
		testUtil.createConceptWithPathIdAndTerms("MAIN", "100003", "Foot bone");
		testUtil.createConceptWithPathIdAndTerms("MAIN", "100004", "Foot");
		testUtil.createConceptWithPathIdAndTerms("MAIN", "100005", "Footwear");

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
	public void testDescriptionSearchAggregations() throws ServiceException {
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

		Map<String, Map<String, Long>> foodAggs = descriptionService.findDescriptionsWithAggregations(path, "food", PageRequest.of(0, 10)).getBuckets();
		assertEquals("{900000000000207008=1}", getAggregationString("module", foodAggs));
		assertEquals("{english=1}", getAggregationString("language", foodAggs));
		assertEquals("{food=1}", getAggregationString("semanticTags", foodAggs));
		assertEquals("{}", getAggregationString("membership", foodAggs));

		Map<String, Map<String, Long>> pizzaAggs = descriptionService.findDescriptionsWithAggregations(path, "pizza", PageRequest.of(0, 10)).getBuckets();
		assertEquals("{900000000000207008=3}", getAggregationString("module", pizzaAggs));
		assertEquals("{english=3}", getAggregationString("language", pizzaAggs));
		assertEquals("{pizza=3}", getAggregationString("semanticTags", pizzaAggs));
		assertEquals("{723592007=1, 723589008=2}", getAggregationString("membership", pizzaAggs));
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
		assertEquals(2, descriptionService.findDescriptionsWithAggregations(path, "Cheese", null, null, null, groupByConcept, null, Collections.singleton("en"), PageRequest.of(0, 10)).getTotalElements());
		groupByConcept = true;
		assertEquals(1, descriptionService.findDescriptionsWithAggregations(path, "Cheese", null, null, null, groupByConcept, null, Collections.singleton("en"), PageRequest.of(0, 10)).getTotalElements());
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
		assertEquals(2, descriptionService.findDescriptionsWithAggregations(path, "Cheese.*", null, null, null, groupByConcept, REGEX, Collections.singleton("en"), PageRequest.of(0, 10)).getTotalElements());
		assertEquals(2, descriptionService.findDescriptionsWithAggregations(path, "Che{2}se.*", null, null, null, groupByConcept, REGEX, Collections.singleton("en"), PageRequest.of(0, 10)).getTotalElements());
		assertEquals(1, descriptionService.findDescriptionsWithAggregations(path, "^Cheese$", null, null, null, groupByConcept, REGEX, Collections.singleton("en"), PageRequest.of(0, 10)).getTotalElements());
		assertEquals(2, descriptionService.findDescriptionsWithAggregations(path, "Chees.*", null, null, null, groupByConcept, REGEX, Collections.singleton("en"), PageRequest.of(0, 10)).getTotalElements());
		//term is indexed as type of text not keyword therefore  below regex will not match anything.
		assertEquals(1, descriptionService.findDescriptionsWithAggregations(path, "Chees.*piz.*", null, null, null, groupByConcept, REGEX, Collections.singleton("en"), PageRequest.of(0, 10)).getTotalElements());

		groupByConcept = true;
		assertEquals(1, descriptionService.findDescriptionsWithAggregations(path, "Chees.*", null, null, null, groupByConcept, REGEX, Collections.singleton("en"), PageRequest.of(0, 10)).getTotalElements());
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
		Map<String, Map<String, Long>> allAggregations = descriptionService.findDescriptionsWithAggregations(path, null, true, null, null, false, null, languageCodes, PageRequest.of(0, 10)).getBuckets();
		assertEquals("{900000000000207008=4}", getAggregationString("module", allAggregations));
		assertEquals("{english=4}", getAggregationString("language", allAggregations));
		assertEquals("{pizza=3, food=1}", getAggregationString("semanticTags", allAggregations));
		assertEquals("{723592007=1, 723589008=2}", getAggregationString("membership", allAggregations));

		String semanticTag = "pizza";
		Map<String, Map<String, Long>> pizzaFilteredAggregations = descriptionService.findDescriptionsWithAggregations(path, null, true, null, semanticTag, false, null, languageCodes, PageRequest.of(0, 10)).getBuckets();
		assertEquals("{900000000000207008=4}", getAggregationString("module", pizzaFilteredAggregations));
		assertEquals("{english=4}", getAggregationString("language", pizzaFilteredAggregations));
		assertEquals("{pizza=3}", getAggregationString("semanticTags", pizzaFilteredAggregations));
		assertEquals("{723592007=1, 723589008=2}", getAggregationString("membership", pizzaFilteredAggregations));
	}

	@Test
	public void testVersionControlOnChildOfMainBranch() throws ServiceException {
		branchService.create("MAIN/A");
		Concept concept = testUtil.createConceptWithPathIdAndTerms("MAIN/A", "100001", "Heart");

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
		Concept concept = testUtil.createConceptWithPathIdAndTerms("MAIN", "100001", "Heart");

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
