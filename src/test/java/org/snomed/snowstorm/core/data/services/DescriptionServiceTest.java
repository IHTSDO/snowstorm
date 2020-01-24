package org.snomed.snowstorm.core.data.services;

import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.domain.Commit;
import org.elasticsearch.common.util.set.Sets;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.TestConfig;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.data.services.pojo.DescriptionCriteria;
import org.snomed.snowstorm.core.data.services.pojo.PageWithBucketAggregations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.*;
import java.util.stream.Collectors;

import static com.google.common.collect.Lists.newArrayList;
import static java.lang.Long.parseLong;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.snomed.snowstorm.core.data.domain.Concepts.*;
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
		testUtil.createConceptWithPathIdAndTerms("MAIN", "100006", "Foot cramps", "Foot cramp");
		testUtil.createConceptWithPathIdAndTerm("MAIN", "100003", "Foot bone");
		testUtil.createConceptWithPathIdAndTerm("MAIN", "100004", "Foot");
		testUtil.createConceptWithPathIdAndTerm("MAIN", "100005", "Footwear");

		List<Description> content = descriptionService.findDescriptionsWithAggregations("MAIN", "Foo cr", ServiceTestUtil.PAGE_REQUEST).getContent();
		List<String> actualTerms = content.stream().map(Description::getTerm).collect(Collectors.toList());
		assertEquals(newArrayList("Foot cramp", "Foot cramps"), actualTerms);

		content = descriptionService.findDescriptionsWithAggregations("MAIN", "Foo", ServiceTestUtil.PAGE_REQUEST).getContent();
		actualTerms = content.stream().map(Description::getTerm).collect(Collectors.toList());
		assertEquals(newArrayList("Foot", "Footwear", "Foot bone", "Foot cramp", "Foot cramps"), actualTerms);

		content = descriptionService.findDescriptionsWithAggregations("MAIN",
				new DescriptionCriteria()
						.term("Foo")
						.active(true)
						.groupByConcept(true),
				ServiceTestUtil.PAGE_REQUEST).getContent();
		actualTerms = content.stream().map(Description::getTerm).collect(Collectors.toList());
		assertEquals(newArrayList("Foot", "Footwear", "Foot bone", "Foot cramp"), actualTerms);

		content = descriptionService.findDescriptionsWithAggregations("MAIN", "cramps", ServiceTestUtil.PAGE_REQUEST).getContent();
		actualTerms = content.stream().map(Description::getTerm).collect(Collectors.toList());
		assertEquals(newArrayList("Foot cramps"), actualTerms);
	}

	@Test
	public void testDescriptionSearchCharacterFolding() throws ServiceException {
		testUtil.createConceptWithPathIdAndTermWithLang("MAIN", "100001", "Heart", "en");
		testUtil.createConceptWithPathIdAndTermWithLang("MAIN", "100002", "Lung", "en");
		testUtil.createConceptWithPathIdAndTermWithLang("MAIN", "100003", "Foot cramps", "en");
		testUtil.createConceptWithPathIdAndTermWithLang("MAIN", "100004", "Région de l'Afrique", "fr");
		testUtil.createConceptWithPathIdAndTermWithLang("MAIN", "100005", "déficit de apolipoproteína", "es");
		testUtil.createConceptWithPathIdAndTermWithLang("MAIN", "100006", "thing", "zz");

		// In the French language 'é' is NOT configured as an extra character in the alphabet so is folded to it's simpler form 'e' in the index.
		// Searching for either 'é' or 'e' should give the same match.
		assertEquals(1, descriptionService.findDescriptionsWithAggregations("MAIN", "région", newArrayList("fr"), ServiceTestUtil.PAGE_REQUEST).getTotalElements());
		assertEquals(1, descriptionService.findDescriptionsWithAggregations("MAIN", "region", newArrayList("fr"), ServiceTestUtil.PAGE_REQUEST).getTotalElements());

		// In the Spanish language 'é' IS configured as an extra character in the alphabet. It is kept as 'é' in the index.
		// Searching for 'é' should match but 'e' should not because it's considered as a different letter altogether.
		assertEquals(1, descriptionService.findDescriptionsWithAggregations("MAIN", "déficit", newArrayList("es"), ServiceTestUtil.PAGE_REQUEST).getTotalElements());
		assertEquals(0, descriptionService.findDescriptionsWithAggregations("MAIN", "deficit", newArrayList("es"), ServiceTestUtil.PAGE_REQUEST).getTotalElements());
		// Searching for both es and en language codes does not allow matching against the Spanish description with the é character
		// because when the search term is folded for the English language matches are restricted to descriptions with en language code.
		assertEquals(0, descriptionService.findDescriptionsWithAggregations("MAIN", "deficit", newArrayList("es", "en"), ServiceTestUtil.PAGE_REQUEST).getTotalElements());

		// Search all languages
		List<String> languageCodes = null;
		assertEquals(1, descriptionService.findDescriptionsWithAggregations("MAIN", "région", languageCodes, ServiceTestUtil.PAGE_REQUEST).getTotalElements());
		assertEquals(1, descriptionService.findDescriptionsWithAggregations("MAIN", "déficit", languageCodes, ServiceTestUtil.PAGE_REQUEST).getTotalElements());
		assertEquals(1, descriptionService.findDescriptionsWithAggregations("MAIN", "thing", languageCodes, ServiceTestUtil.PAGE_REQUEST).getTotalElements());
	}

	@Test
	public void testDescriptionSearchWithNonAlphanumericCharacters() throws ServiceException {
		testUtil.createConceptWithPathIdAndTermWithLang("MAIN", "100001", "Urine micr.: leucs - % polys", "en");
		testUtil.createConceptWithPathIdAndTermWithLang("MAIN", "100002", "Spinal fusion of atlas-axis,test (procedure)", "en");
		testUtil.createConceptWithPathIdAndTermWithLang("MAIN", "100003", "test procedure", "en");

		assertEquals(1, descriptionService.findDescriptionsWithAggregations("MAIN", "urine micr.: leucs - % polys", newArrayList("en"), ServiceTestUtil.PAGE_REQUEST).getTotalElements());
		assertEquals(1, descriptionService.findDescriptionsWithAggregations("MAIN", "Urine Micr.: Leucs - % Polys", newArrayList("en"), ServiceTestUtil.PAGE_REQUEST).getTotalElements());
		assertEquals(1, descriptionService.findDescriptionsWithAggregations("MAIN", "Urine micr leucs - % Polys", newArrayList("en"), ServiceTestUtil.PAGE_REQUEST).getTotalElements());
		assertEquals(1, descriptionService.findDescriptionsWithAggregations("MAIN", "urine micr leucs polys", newArrayList("en"), ServiceTestUtil.PAGE_REQUEST).getTotalElements());
		assertEquals(0, descriptionService.findDescriptionsWithAggregations("MAIN", "Urine micrr.: leucs", newArrayList("en"), ServiceTestUtil.PAGE_REQUEST).getTotalElements());
		assertEquals(0, descriptionService.findDescriptionsWithAggregations("MAIN", "Urine mic.: leucs", newArrayList("en"), ServiceTestUtil.PAGE_REQUEST).getTotalElements());
		assertEquals(1, descriptionService.findDescriptionsWithAggregations("MAIN", "Urine mic leucs", newArrayList("en"), ServiceTestUtil.PAGE_REQUEST).getTotalElements());

		assertEquals(1, descriptionService.findDescriptionsWithAggregations("MAIN", "atlas-axis,", newArrayList("en"), ServiceTestUtil.PAGE_REQUEST).getTotalElements());
		assertEquals(0, descriptionService.findDescriptionsWithAggregations("MAIN", "atla-axis,", newArrayList("en"), ServiceTestUtil.PAGE_REQUEST).getTotalElements());
		assertEquals(1, descriptionService.findDescriptionsWithAggregations("MAIN", "Spinal fusion of atlas-axis", newArrayList("en"), ServiceTestUtil.PAGE_REQUEST).getTotalElements());
		assertEquals(1, descriptionService.findDescriptionsWithAggregations("MAIN", "atlas axis", newArrayList("en"), ServiceTestUtil.PAGE_REQUEST).getTotalElements());

		assertEquals(2, descriptionService.findDescriptionsWithAggregations("MAIN", "procedure", newArrayList("en"), ServiceTestUtil.PAGE_REQUEST).getTotalElements());
		assertEquals(1, descriptionService.findDescriptionsWithAggregations("MAIN", "(procedure)", newArrayList("en"), ServiceTestUtil.PAGE_REQUEST).getTotalElements());
		assertEquals(2, descriptionService.findDescriptionsWithAggregations("MAIN", "test procedure", newArrayList("en"), ServiceTestUtil.PAGE_REQUEST).getTotalElements());
		assertEquals(1, descriptionService.findDescriptionsWithAggregations("MAIN", "test procedure)", newArrayList("en"), ServiceTestUtil.PAGE_REQUEST).getTotalElements());

	}

	@Test
	public void testDescriptionSearchWithEdgeCases() throws ServiceException {
		testUtil.createConceptWithPathIdAndTermWithLang("MAIN", "100001", "Man (person)", "en");
		testUtil.createConceptWithPathIdAndTermWithLang("MAIN", "100002", "Elderly man (person)", "en");
		testUtil.createConceptWithPathIdAndTermWithLang("MAIN", "100003", "1.5%/epinephrine substance", "en");
		testUtil.createConceptWithPathIdAndTermWithLang("MAIN", "100004", "drugs 1.5%/epinephrine (substance)", "en");
		testUtil.createConceptWithPathIdAndTermWithLang("MAIN", "100005", "drugs with 1.5%/epinephrine", "en");

		// testing stop words
		assertEquals(0, descriptionService.findDescriptionsWithAggregations("MAIN", "an man person not", newArrayList("en"), ServiceTestUtil.PAGE_REQUEST).getTotalElements());

		// Combining Regex search and simple query as term containing non-alphanumeric
		assertEquals(2, descriptionService.findDescriptionsWithAggregations("MAIN", "Man (person)", newArrayList("en"), ServiceTestUtil.PAGE_REQUEST).getTotalElements());
		assertEquals(3, descriptionService.findDescriptionsWithAggregations("MAIN", "1.5%/epine", newArrayList("en"), ServiceTestUtil.PAGE_REQUEST).getTotalElements());
		assertEquals(1, descriptionService.findDescriptionsWithAggregations("MAIN", "1.5%/epinephrine (", newArrayList("en"), ServiceTestUtil.PAGE_REQUEST).getTotalElements());
		assertEquals(2, descriptionService.findDescriptionsWithAggregations("MAIN", "1.5%/epinephrine substance", newArrayList("en"), ServiceTestUtil.PAGE_REQUEST).getTotalElements());
		assertEquals(2, descriptionService.findDescriptionsWithAggregations("MAIN", "d 1.5%/epinephrine", newArrayList("en"), ServiceTestUtil.PAGE_REQUEST).getTotalElements());

		// These two tests are to highlight that ElasticSearch simple query doesn't check cardinality
		assertEquals(2, descriptionService.findDescriptionsWithAggregations("MAIN", "Man (person) p", newArrayList("en"), ServiceTestUtil.PAGE_REQUEST).getTotalElements());
		assertEquals(2, descriptionService.findDescriptionsWithAggregations("MAIN", "man person p", newArrayList("en"), ServiceTestUtil.PAGE_REQUEST).getTotalElements());
	}

	@Test
	public void testDescriptionSearchWithExtendedCharacters() throws ServiceException {
		testUtil.createConceptWithPathIdAndTermWithLang("MAIN", "100001", "Tübingen", "en");
		testUtil.createConceptWithPathIdAndTermWithLang("MAIN", "100002", "Tubingen", "en");
		testUtil.createConceptWithPathIdAndTermWithLang("MAIN", "100003", "Köln, Löwchen", "en");

		testUtil.createConceptWithPathIdAndTermWithLang("MAIN", "100004", "Ménière", "en");
		testUtil.createConceptWithPathIdAndTermWithLang("MAIN", "100005", "Meniere", "en");

		testUtil.createConceptWithPathIdAndTermWithLang("MAIN", "100006", "Alzheimer's", "en");

		testUtil.createConceptWithPathIdAndTermWithLang("MAIN", "100007", "phospho-2-dehydro-3-deoxygluconate aldolase", "en");
		testUtil.createConceptWithPathIdAndTermWithLang("MAIN", "100008", "Salmonella II 43:g,t:[1,5] (organism)", "en");
		testUtil.createConceptWithPathIdAndTermWithLang("MAIN", "100009", "lidocaine hydrochloride 1.5%/epinephrine 1:200,000 injection solution vial (product)", "en");
		testUtil.createConceptWithPathIdAndTermWithLang("MAIN", "1000010", "pT3: tumor invades adventitia (esophagus)", "en");
		testUtil.createConceptWithPathIdAndTermWithLang("MAIN", "1000011", "Technetium Tc^99c^ medronate (substance)", "en");

		testUtil.createConceptWithPathIdAndTermWithLang("MAIN", "1000012", "Minnesota pig #1", "en");
		testUtil.createConceptWithPathIdAndTermWithLang("MAIN", "1000013", "Lidocaine hydrochloride 1.5%/epinephrine", "en");
		testUtil.createConceptWithPathIdAndTermWithLang("MAIN", "1000014", "Hypodermic needles & syringes", "en");

		assertEquals(2, descriptionService.findDescriptionsWithAggregations("MAIN", "Tübingen", newArrayList("en"), ServiceTestUtil.PAGE_REQUEST).getTotalElements());
		assertEquals(2, descriptionService.findDescriptionsWithAggregations("MAIN", "Tubingen", newArrayList("en"), ServiceTestUtil.PAGE_REQUEST).getTotalElements());
		assertEquals(1, descriptionService.findDescriptionsWithAggregations("MAIN", "Köln, Löwchen", newArrayList("en"), ServiceTestUtil.PAGE_REQUEST).getTotalElements());

		assertEquals(2, descriptionService.findDescriptionsWithAggregations("MAIN", "Ménière", newArrayList("en"), ServiceTestUtil.PAGE_REQUEST).getTotalElements());
		assertEquals(2, descriptionService.findDescriptionsWithAggregations("MAIN", "Meniere", newArrayList("en"), ServiceTestUtil.PAGE_REQUEST).getTotalElements());

		assertEquals(1, descriptionService.findDescriptionsWithAggregations("MAIN", "Alzheimer's", newArrayList("en"), ServiceTestUtil.PAGE_REQUEST).getTotalElements());

		assertEquals(1, descriptionService.findDescriptionsWithAggregations("MAIN", "phospho-2-dehydro-3-deoxygluconate aldolase", newArrayList("en"), ServiceTestUtil.PAGE_REQUEST).getTotalElements());
		assertEquals(1, descriptionService.findDescriptionsWithAggregations("MAIN", "Salmonella II 43:g,t:[1,5] (organism)", newArrayList("en"), ServiceTestUtil.PAGE_REQUEST).getTotalElements());
		assertEquals(1, descriptionService.findDescriptionsWithAggregations("MAIN", "lidocaine hydrochloride 1.5%/epinephrine 1:200,000 injection solution vial (product)", newArrayList("en"), ServiceTestUtil.PAGE_REQUEST).getTotalElements());
		assertEquals(1, descriptionService.findDescriptionsWithAggregations("MAIN", "pT3: tumor invades adventitia (esophagus)", newArrayList("en"), ServiceTestUtil.PAGE_REQUEST).getTotalElements());
		assertEquals(1, descriptionService.findDescriptionsWithAggregations("MAIN", "Technetium Tc^99c^ medronate (substance)", newArrayList("en"), ServiceTestUtil.PAGE_REQUEST).getTotalElements());

		assertEquals(1, descriptionService.findDescriptionsWithAggregations("MAIN", "Minnesota pig #1", newArrayList("en"), ServiceTestUtil.PAGE_REQUEST).getTotalElements());
		assertEquals(2, descriptionService.findDescriptionsWithAggregations("MAIN", "Lidocaine hydrochloride 1.5%/epinephrine", newArrayList("en"), ServiceTestUtil.PAGE_REQUEST).getTotalElements());
		assertEquals(1, descriptionService.findDescriptionsWithAggregations("MAIN", "Hypodermic needles & syringes", newArrayList("en"), ServiceTestUtil.PAGE_REQUEST).getTotalElements());
	}

	@Test
	public void testDescriptionSearchAggregations() throws ServiceException {
		String path = "MAIN";
		Concept root = new Concept(SNOMEDCT_ROOT);
		Concept pizza_2 = new Concept("100002").addRelationship(new Relationship(ISA, SNOMEDCT_ROOT)).addFSN("Food (food)");
		Concept cheesePizza_3 = new Concept("100003").addRelationship(new Relationship(ISA, pizza_2.getId())).addFSN("Cheese Pizza (pizza)");
		Concept reallyCheesyPizza_4 = new Concept("100004").addRelationship(new Relationship(ISA, cheesePizza_3.getId())).addFSN("Really Cheesy Pizza (pizza)");
		Concept reallyCheesyPizza_5 = new Concept("100005").addRelationship(new Relationship(ISA, reallyCheesyPizza_4.getId())).addFSN("So Cheesy Pizza (so pizza)");
		List<Concept> concepts = newArrayList(root, pizza_2, cheesePizza_3, reallyCheesyPizza_4, reallyCheesyPizza_5);
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
		assertEquals("{en=1}", getAggregationString("language", foodAggs));
		assertEquals("{food=1}", getAggregationString("semanticTags", foodAggs));
		assertEquals("{}", getAggregationString("membership", foodAggs));

		page = descriptionService.findDescriptionsWithAggregations(path, "pizza", PageRequest.of(0, 10));
		assertEquals(3, page.getTotalElements());
		assertEquals(3, page.getContent().size());
		Map<String, Map<String, Long>> pizzaAggs = page.getBuckets();
		assertEquals("{900000000000207008=3}", getAggregationString("module", pizzaAggs));
		assertEquals("{en=3}", getAggregationString("language", pizzaAggs));
		assertEquals("{pizza=2, so pizza=1}", getAggregationString("semanticTags", pizzaAggs));
		assertEquals("{723592007=1, 723589008=2}", getAggregationString("membership", pizzaAggs));

		page = descriptionService.findDescriptionsWithAggregations(path,
				new DescriptionCriteria()
						.term("pizza")
						.active(true)
						.semanticTag("so pizza")
						.conceptActive(true),
				PageRequest.of(0, 10));
		assertEquals(1, page.getTotalElements());
		assertEquals(1, page.getContent().size());
		Map<String, Map<String, Long>> soPizzaAggs = page.getBuckets();
		assertEquals("{900000000000207008=1}", getAggregationString("module", soPizzaAggs));
		assertEquals("{en=1}", getAggregationString("language", soPizzaAggs));
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
		List<Concept> concepts = newArrayList(root, pizza_2, cheesePizza_3, reallyCheesyPizza_4, reallyCheesyPizza_5);
		conceptService.batchCreate(concepts, path);

		DescriptionCriteria descriptionCriteria = new DescriptionCriteria()
				.term("Cheese")
				.groupByConcept(false);
		assertEquals(2, descriptionService.findDescriptionsWithAggregations(path, descriptionCriteria, PageRequest.of(0, 10)).getTotalElements());

		descriptionCriteria.groupByConcept(true);
		assertEquals(1, descriptionService.findDescriptionsWithAggregations(path, descriptionCriteria, PageRequest.of(0, 10)).getTotalElements());
	}

	@Test
	public void testDescriptionSearchWithRegex() throws ServiceException {
		String path = "MAIN";
		Concept root = new Concept(SNOMEDCT_ROOT);
		Concept pizza_2 = new Concept("100002").addRelationship(new Relationship(ISA, SNOMEDCT_ROOT)).addFSN("Food (food)");
		Concept cheesePizza_3 = new Concept("100003").addRelationship(new Relationship(ISA, pizza_2.getId())).addFSN("Cheese Pizza (pizza)").addDescription(new Description("Cheese"));
		Concept reallyCheesyPizza_4 = new Concept("100004").addRelationship(new Relationship(ISA, cheesePizza_3.getId())).addFSN("Really Cheesy Pizza (pizza)");
		Concept reallyCheesyPizza_5 = new Concept("100005").addRelationship(new Relationship(ISA, reallyCheesyPizza_4.getId())).addFSN("So Cheesy Pizza (pizza)");
		List<Concept> concepts = newArrayList(root, pizza_2, cheesePizza_3, reallyCheesyPizza_4, reallyCheesyPizza_5);
		conceptService.batchCreate(concepts, path);

		DescriptionCriteria descriptionCriteria = new DescriptionCriteria()
				.searchMode(REGEX)
				.groupByConcept(false);

		assertEquals(2, descriptionService.findDescriptionsWithAggregations(path, descriptionCriteria.term("Cheese.*"), PageRequest.of(0, 10)).getTotalElements());
		assertEquals(2, descriptionService.findDescriptionsWithAggregations(path, descriptionCriteria.term("Che{2}se.*"), PageRequest.of(0, 10)).getTotalElements());
		assertEquals(1, descriptionService.findDescriptionsWithAggregations(path, descriptionCriteria.term("^Cheese$"), PageRequest.of(0, 10)).getTotalElements());
		assertEquals(2, descriptionService.findDescriptionsWithAggregations(path, descriptionCriteria.term("Chees.*"), PageRequest.of(0, 10)).getTotalElements());
		assertEquals(1, descriptionService.findDescriptionsWithAggregations(path, descriptionCriteria.term("Chees.*piz.*"), PageRequest.of(0, 10)).getTotalElements());

		descriptionCriteria.groupByConcept(true);
		assertEquals(1, descriptionService.findDescriptionsWithAggregations(path, descriptionCriteria.term("Chees.*"), PageRequest.of(0, 10)).getTotalElements());
	}

	@Test
	public void testDescriptionSearchAggregationsSemanticTagFilter() throws ServiceException {
		String path = "MAIN";
		Concept root = new Concept(SNOMEDCT_ROOT);
		Concept food_1 = new Concept("100002").addRelationship(new Relationship(ISA, SNOMEDCT_ROOT)).addFSN("Food (food)");
		Concept food_2 = new Concept("100006").addRelationship(new Relationship(ISA, SNOMEDCT_ROOT)).addFSN("Food 2 (food)");
		Concept cheesePizza_3 = new Concept("100003").addRelationship(new Relationship(ISA, food_1.getId())).addFSN("Cheese Pizza (pizza)");
		Concept reallyCheesyPizza_4 = new Concept("100004").addRelationship(new Relationship(ISA, cheesePizza_3.getId())).addFSN("Really Cheesy Pizza (pizza)");
		Concept reallyCheesyPizza_5 = new Concept("100005").addRelationship(new Relationship(ISA, reallyCheesyPizza_4.getId())).addFSN("So Cheesy Pizza (pizza)");
		List<Concept> concepts = newArrayList(root, food_1, food_2, cheesePizza_3, reallyCheesyPizza_4, reallyCheesyPizza_5);
		setModulesAndLanguage(concepts);
		conceptService.batchCreate(concepts, path);

		referenceSetMemberService.createMembers(path, Sets.newHashSet(
				new ReferenceSetMember(Concepts.CORE_MODULE, Concepts.REFSET_MRCM_DOMAIN, "100003"),
				new ReferenceSetMember(Concepts.CORE_MODULE, Concepts.REFSET_MRCM_DOMAIN, "100004"),
				new ReferenceSetMember(Concepts.CORE_MODULE, Concepts.REFSET_MRCM_ATTRIBUTE_RANGE, "100005")
		));

		DescriptionCriteria descriptionCriteria = new DescriptionCriteria().active(true);
		Map<String, Map<String, Long>> allAggregations = descriptionService.findDescriptionsWithAggregations(path, descriptionCriteria, PageRequest.of(0, 10)).getBuckets();
		assertEquals("{900000000000207008=5}", getAggregationString("module", allAggregations));
		assertEquals("{en=5}", getAggregationString("language", allAggregations));
		assertEquals("{pizza=3, food=2}", getAggregationString("semanticTags", allAggregations));
		assertEquals("{723592007=1, 723589008=2}", getAggregationString("membership", allAggregations));

		descriptionCriteria.semanticTag("pizza");
		Map<String, Map<String, Long>> pizzaFilteredAggregations = descriptionService.findDescriptionsWithAggregations(path, descriptionCriteria, PageRequest.of(0, 10)).getBuckets();
		assertEquals("{900000000000207008=3}", getAggregationString("module", pizzaFilteredAggregations));
		assertEquals("{en=3}", getAggregationString("language", pizzaFilteredAggregations));
		assertEquals("{pizza=3}", getAggregationString("semanticTags", pizzaFilteredAggregations));
		assertEquals("{723592007=1, 723589008=2}", getAggregationString("membership", pizzaFilteredAggregations));

		descriptionCriteria.semanticTag(null);
		Set<String> semanticTags = new HashSet<>();
		semanticTags.add("food");
		semanticTags.add("pizza");
		descriptionCriteria.semanticTags(semanticTags);
		Map<String, Map<String, Long>> multipleSemanticTagsFilteredAggregations = descriptionService.findDescriptionsWithAggregations(path, descriptionCriteria, PageRequest.of(0, 10)).getBuckets();
		assertEquals("{900000000000207008=5}", getAggregationString("module", multipleSemanticTagsFilteredAggregations));
		assertEquals("{en=5}", getAggregationString("language", multipleSemanticTagsFilteredAggregations));
		assertEquals("{pizza=3, food=2}", getAggregationString("semanticTags", multipleSemanticTagsFilteredAggregations));
		assertEquals("{723592007=1, 723589008=2}", getAggregationString("membership", multipleSemanticTagsFilteredAggregations));

	}

	@Test
	public void testDescriptionSearchAggregationsActiveConcept() throws ServiceException {
		String path = "MAIN";
		Concept root = new Concept(SNOMEDCT_ROOT);
		Concept pizza_2 = new Concept("100002").addRelationship(new Relationship(ISA, SNOMEDCT_ROOT)).addFSN("Food (food)");
		Concept cheesePizza_3 = new Concept("100003").addRelationship(new Relationship(ISA, pizza_2.getId())).addFSN("Cheese Pizza (pizza)");
		Concept reallyCheesyPizza_4 = new Concept("100004").addRelationship(new Relationship(ISA, cheesePizza_3.getId())).addFSN("Really Cheesy Pizza (pizza)");
		Concept reallyCheesyPizza_5 = new Concept("100005").addRelationship(new Relationship(ISA, reallyCheesyPizza_4.getId())).addFSN("So Cheesy Pizza (pizza)");
		List<Concept> concepts = newArrayList(root, pizza_2, cheesePizza_3, reallyCheesyPizza_4, reallyCheesyPizza_5);
		setModulesAndLanguage(concepts);
		conceptService.batchCreate(concepts, path);

		// Make one pizza concept inactive
		reallyCheesyPizza_5.setActive(false);
		conceptService.update(reallyCheesyPizza_5, path);

		Concept concept = conceptService.find(reallyCheesyPizza_5.getId(), path);
		assertFalse(concept.isActive());

		DescriptionCriteria descriptionCriteria = new DescriptionCriteria()
				.active(true)
				.term("pizza");

		descriptionCriteria.conceptActive(null);
		assertEquals("Should find all three pizza concepts", 3, descriptionService.findDescriptionsWithAggregations(path, descriptionCriteria, PageRequest.of(0, 10)).getTotalElements());

		descriptionCriteria.conceptActive(true);
		assertEquals("Should find the two active pizza concepts", 2, descriptionService.findDescriptionsWithAggregations(path, descriptionCriteria, PageRequest.of(0, 10)).getTotalElements());

		descriptionCriteria.conceptActive(false);
		assertEquals("Should find the one inactive pizza concept", 1, descriptionService.findDescriptionsWithAggregations(path, descriptionCriteria, PageRequest.of(0, 10)).getTotalElements());
	}

	@Test
	public void testDescriptionSearchAggregationsRefsetFilter() throws ServiceException {
		String path = "MAIN";
		Concept root = new Concept(SNOMEDCT_ROOT);
		Concept pizza_2 = new Concept("100002").addRelationship(new Relationship(ISA, SNOMEDCT_ROOT)).addFSN("Food (food)");
		Concept cheesePizza_3 = new Concept("100003").addRelationship(new Relationship(ISA, pizza_2.getId())).addFSN("Cheese Pizza (pizza)");
		Concept reallyCheesyPizza_4 = new Concept("100004").addRelationship(new Relationship(ISA, cheesePizza_3.getId())).addFSN("Really Cheesy Pizza (pizza)");
		Concept reallyCheesyPizza_5 = new Concept("100005").addRelationship(new Relationship(ISA, reallyCheesyPizza_4.getId())).addFSN("So Cheesy Pizza (so pizza)");
		List<Concept> concepts = newArrayList(root, pizza_2, cheesePizza_3, reallyCheesyPizza_4, reallyCheesyPizza_5);
		setModulesAndLanguage(concepts);
		conceptService.batchCreate(concepts, path);

		referenceSetMemberService.createMembers(path, Sets.newHashSet(
				new ReferenceSetMember(Concepts.CORE_MODULE, Concepts.REFSET_MRCM_DOMAIN, "100003"),
				new ReferenceSetMember(Concepts.CORE_MODULE, Concepts.REFSET_MRCM_DOMAIN, "100004"),
				new ReferenceSetMember(Concepts.CORE_MODULE, Concepts.REFSET_MRCM_ATTRIBUTE_RANGE, "100005")
		));

		DescriptionCriteria descriptionCriteria = new DescriptionCriteria()
				.active(true)
				.term("pizza")
				.conceptActive(true);

		descriptionCriteria.conceptRefset(null);
		assertEquals(3, descriptionService.findDescriptionsWithAggregations(path, descriptionCriteria, PageRequest.of(0, 10)).getTotalElements());

		descriptionCriteria.conceptRefset(Concepts.REFSET_MRCM_ATTRIBUTE_RANGE);
		assertEquals(1, descriptionService.findDescriptionsWithAggregations(path, descriptionCriteria, PageRequest.of(0, 10)).getTotalElements());
	}

	@Test
	public void testDescriptionSearchTypeFilter() throws ServiceException {
		String path = "MAIN";
		Concept root = new Concept(SNOMEDCT_ROOT);
		Concept pizza_2 = new Concept("100002").addRelationship(new Relationship(ISA, SNOMEDCT_ROOT)).addFSN("Food (food)");
		Concept cheesePizza_3 = new Concept("100003").addRelationship(new Relationship(ISA, pizza_2.getId())).addFSN("Cheese Pizza (pizza)");
		Concept reallyCheesyPizza_4 = new Concept("100004").addRelationship(new Relationship(ISA, cheesePizza_3.getId())).addFSN("Really Cheesy Pizza (pizza)");
		Concept reallyCheesyPizza_5 = new Concept("100005").addRelationship(new Relationship(ISA, reallyCheesyPizza_4.getId())).addFSN("So Cheesy Pizza (so pizza)")
				.addDescription(new Description("So cheesy pizza synonym"));
		List<Concept> concepts = newArrayList(root, pizza_2, cheesePizza_3, reallyCheesyPizza_4, reallyCheesyPizza_5);
		setModulesAndLanguage(concepts);
		conceptService.batchCreate(concepts, path);

		DescriptionCriteria descriptionCriteria = new DescriptionCriteria()
				.active(true)
				.term("pizza");
		assertEquals(4, descriptionService.findDescriptionsWithAggregations(path, descriptionCriteria, PageRequest.of(0, 10)).getTotalElements());

		descriptionCriteria.type(Collections.singleton(parseLong(Concepts.FSN)));
		assertEquals(3, descriptionService.findDescriptionsWithAggregations(path, descriptionCriteria, PageRequest.of(0, 10)).getTotalElements());

		descriptionCriteria.type(Collections.singleton(parseLong(Concepts.SYNONYM)));
		assertEquals(1, descriptionService.findDescriptionsWithAggregations(path, descriptionCriteria, PageRequest.of(0, 10)).getTotalElements());

		descriptionCriteria.type(Collections.singleton(parseLong(Concepts.TEXT_DEFINITION)));
		assertEquals(0, descriptionService.findDescriptionsWithAggregations(path, descriptionCriteria, PageRequest.of(0, 10)).getTotalElements());

		descriptionCriteria.type(newArrayList(parseLong(FSN), parseLong(SYNONYM)));
		assertEquals(4, descriptionService.findDescriptionsWithAggregations(path, descriptionCriteria, PageRequest.of(0, 10)).getTotalElements());
	}

	@Test
	public void testDescriptionSearchAcceptabilityFilter() throws ServiceException {
		String path = "MAIN";
		Concept root = new Concept(SNOMEDCT_ROOT);
		Concept pizza_2 = new Concept("100002").addRelationship(new Relationship(ISA, SNOMEDCT_ROOT))
				.addDescription(new Description("Food (food)").addLanguageRefsetMember(GB_EN_LANG_REFSET, PREFERRED));
		Concept cheesePizza_3 = new Concept("100003").addRelationship(new Relationship(ISA, pizza_2.getId()))
				.addDescription(new Description("Cheese Pizza (pizza)").setTypeId(FSN).addLanguageRefsetMember(GB_EN_LANG_REFSET, PREFERRED))
				.addDescription(new Description("Cheese Pizza").addLanguageRefsetMember(GB_EN_LANG_REFSET, PREFERRED))
				.addDescription(new Description("Cheese").addLanguageRefsetMember(GB_EN_LANG_REFSET, ACCEPTABLE))
				.addDescription(new Description("Cheeze Pizza").addLanguageRefsetMember(GB_EN_LANG_REFSET, ACCEPTABLE))
				.addDescription(new Description("Cheezze Pizza").addLanguageRefsetMember(US_EN_LANG_REFSET, ACCEPTABLE));

		Concept reallyCheesyPizza_4 = new Concept("100004").addRelationship(new Relationship(ISA, cheesePizza_3.getId()))
				.addFSN("Really Cheesy Pizza (pizza)")
				.addDescription(new Description("Really Cheesy Pizza").addLanguageRefsetMember(GB_EN_LANG_REFSET, PREFERRED));

		Concept reallyCheesyPizza_5 = new Concept("100005").addRelationship(new Relationship(ISA, reallyCheesyPizza_4.getId())).addFSN("So Cheesy Pizza (so pizza)")
				.addDescription(new Description("So cheesy pizza synonym"));
		List<Concept> concepts = newArrayList(root, pizza_2, cheesePizza_3, reallyCheesyPizza_4, reallyCheesyPizza_5);
		setModulesAndLanguage(concepts);
		conceptService.batchCreate(concepts, path);

		DescriptionCriteria descriptionCriteria = new DescriptionCriteria()
				.active(true)
				.term("pizza");
		assertEquals(8, descriptionService.findDescriptionsWithAggregations(path, descriptionCriteria, PageRequest.of(0, 10)).getTotalElements());

		descriptionCriteria.type(Collections.singleton(parseLong(Concepts.FSN)));
		assertEquals(3, descriptionService.findDescriptionsWithAggregations(path, descriptionCriteria, PageRequest.of(0, 10)).getTotalElements());

		descriptionCriteria.preferredIn(Collections.singleton(parseLong(GB_EN_LANG_REFSET)));
		assertEquals(1, descriptionService.findDescriptionsWithAggregations(path, descriptionCriteria, PageRequest.of(0, 10)).getTotalElements());

		descriptionCriteria
				.term("Cheese")
				.type(Collections.singleton(parseLong(Concepts.SYNONYM)))
				.preferredIn(Collections.singleton(parseLong(GB_EN_LANG_REFSET)));
		assertEquals(1, descriptionService.findDescriptionsWithAggregations(path, descriptionCriteria, PageRequest.of(0, 10)).getTotalElements());
		descriptionCriteria.preferredIn(null);

		descriptionCriteria
				.term("Cheeze")
				.type(Collections.singleton(parseLong(Concepts.SYNONYM)))
				.preferredIn(Collections.singleton(parseLong(GB_EN_LANG_REFSET)));
		assertEquals(0, descriptionService.findDescriptionsWithAggregations(path, descriptionCriteria, PageRequest.of(0, 10)).getTotalElements());
		descriptionCriteria.preferredIn(null);

		descriptionCriteria
				.term("Cheeze")
				.type(Collections.singleton(parseLong(Concepts.SYNONYM)))
				.acceptableIn(Collections.singleton(parseLong(GB_EN_LANG_REFSET)));
		assertEquals(1, descriptionService.findDescriptionsWithAggregations(path, descriptionCriteria, PageRequest.of(0, 10)).getTotalElements());
		descriptionCriteria.acceptableIn(null);

		descriptionCriteria
				.term("Cheezze")
				.type(Collections.singleton(parseLong(Concepts.SYNONYM)))
				.preferredIn(Collections.singleton(parseLong(US_EN_LANG_REFSET)));
		assertEquals(0, descriptionService.findDescriptionsWithAggregations(path, descriptionCriteria, PageRequest.of(0, 10)).getTotalElements());
		descriptionCriteria.preferredIn(null);

		descriptionCriteria
				.term("Cheezze")
				.type(Collections.singleton(parseLong(Concepts.SYNONYM)))
				.preferredOrAcceptableIn(Collections.singleton(parseLong(US_EN_LANG_REFSET)));
		assertEquals(1, descriptionService.findDescriptionsWithAggregations(path, descriptionCriteria, PageRequest.of(0, 10)).getTotalElements());
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
