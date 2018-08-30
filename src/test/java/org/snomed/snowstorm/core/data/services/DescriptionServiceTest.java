package org.snomed.snowstorm.core.data.services;

import com.google.common.collect.Lists;
import io.kaicode.elasticvc.api.BranchService;
import org.elasticsearch.common.util.set.Sets;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.range.Range;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedStringTerms;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.TestConfig;
import org.snomed.snowstorm.core.data.domain.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.aggregation.AggregatedPage;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.lang.Long.parseLong;
import static org.junit.Assert.assertEquals;
import static org.snomed.snowstorm.core.data.domain.Concepts.ISA;
import static org.snomed.snowstorm.core.data.domain.Concepts.SNOMEDCT_ROOT;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = TestConfig.class)
public class DescriptionServiceTest extends AbstractTest {

	@Autowired
	private BranchService branchService;

	@Autowired
	private ConceptService conceptService;

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
		conceptService.create(concepts, path);

		referenceSetMemberService.createMembers(path, Sets.newHashSet(
				new ReferenceSetMember(Concepts.CORE_MODULE, Concepts.REFSET_MRCM_DOMAIN, "100003"),
				new ReferenceSetMember(Concepts.CORE_MODULE, Concepts.REFSET_MRCM_DOMAIN, "100004"),
				new ReferenceSetMember(Concepts.CORE_MODULE, Concepts.REFSET_MRCM_ATTRIBUTE_RANGE, "100005")
		));

		Aggregations foodAggs = descriptionService.findDescriptionsWithAggregations(path, "food", PageRequest.of(0, 10)).getAggregations();
		assertEquals("{900000000000207008=1}", getAggregationString("module", foodAggs));
		assertEquals("{en=1}", getAggregationString("language", foodAggs));
		assertEquals("{food=1}", getAggregationString("semanticTags", foodAggs));
		assertEquals("{}", getAggregationString("membership", foodAggs));

		Aggregations pizzaAggs = descriptionService.findDescriptionsWithAggregations(path, "pizza", PageRequest.of(0, 10)).getAggregations();
		assertEquals("{900000000000207008=3}", getAggregationString("module", pizzaAggs));
		assertEquals("{en=3}", getAggregationString("language", pizzaAggs));
		assertEquals("{pizza=3}", getAggregationString("semanticTags", pizzaAggs));
		assertEquals("{723592007=1, 723589008=2}", getAggregationString("membership", pizzaAggs));
	}

	private String getAggregationString(String name, Aggregations aggregations) {
		Map<String, Aggregation> aggregationMap = aggregations.asList().stream().collect(Collectors.toMap(Aggregation::getName, Function.identity()));
		ParsedStringTerms ag = (ParsedStringTerms) aggregationMap.get(name);
		return ag.getBuckets().stream().collect(Collectors.toMap(Terms.Bucket::getKeyAsString, Terms.Bucket::getDocCount)).toString();
	}

	private void setModulesAndLanguage(List<Concept> concepts) {
		concepts.forEach(c -> c.getDescriptions().forEach(d -> {
			d.setModuleId(Concepts.CORE_MODULE);
			d.setLanguageCode("en");
		}));
	}

}
