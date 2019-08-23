package org.snomed.snowstorm.core.data.services;

import com.google.common.collect.Lists;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.TestConfig;
import org.snomed.snowstorm.core.data.domain.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static java.lang.Long.parseLong;
import static org.junit.Assert.assertEquals;
import static org.snomed.snowstorm.core.data.domain.Concepts.ISA;
import static org.snomed.snowstorm.core.data.domain.Concepts.SNOMEDCT_ROOT;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = TestConfig.class)
public class QueryServiceTest extends AbstractTest {

	@Autowired
	private QueryService service;

	@Autowired
	private ConceptService conceptService;

	private static final PageRequest PAGE_REQUEST = PageRequest.of(0, 50);
	public static final String PATH = "MAIN";
	private Concept root;
	private Concept pizza_2;
	private Concept cheesePizza_3;
	private Concept reallyCheesyPizza_4;
	private Concept reallyCheesyPizza_5;
	private Concept inactivePizza_6;

	@Before
	public void setup() throws ServiceException {
		root = new Concept(SNOMEDCT_ROOT);
		pizza_2 = new Concept("100002").addRelationship(new Relationship(ISA, SNOMEDCT_ROOT)).addFSN("Pizza");
		cheesePizza_3 = new Concept("100005").addRelationship(new Relationship(ISA, pizza_2.getId())).addFSN("Cheese Pizza");
		reallyCheesyPizza_4 = new Concept("100008").addRelationship(new Relationship(ISA, cheesePizza_3.getId())).addFSN("Really Cheesy Pizza");
		reallyCheesyPizza_5 = new Concept("100003").addRelationship(new Relationship(ISA, reallyCheesyPizza_4.getId())).addFSN("So Cheesy Pizza")
				.addDescription(new Description("Cheesy Pizza"));
		inactivePizza_6 = (Concept) new Concept("100006").addRelationship(new Relationship(ISA, reallyCheesyPizza_4.getId())).addFSN("Inactive Pizza")
				.addDescription( new Description("additional pizza")).setActive(false);
		conceptService.batchCreate(Lists.newArrayList(root, pizza_2, cheesePizza_3, reallyCheesyPizza_4, reallyCheesyPizza_5, inactivePizza_6), PATH);
	}

	@Test
	public void testSearchResultOrdering() {
		List<ConceptMini> matches = service.search(service.createQueryBuilder(true).activeFilter(true).termMatch("Piz"), PATH, PAGE_REQUEST).getContent();
		assertEquals(4, matches.size());
		assertEquals("Pizza", matches.get(0).getFsnTerm());
		assertEquals("Cheese Pizza", matches.get(1).getFsnTerm());
		assertEquals("So Cheesy Pizza", matches.get(2).getFsnTerm());
		assertEquals("Really Cheesy Pizza", matches.get(3).getFsnTerm());

		matches = service.search(service.createQueryBuilder(true).descendant(parseLong(SNOMEDCT_ROOT)).termMatch("Piz"), PATH, PAGE_REQUEST).getContent();
		assertEquals(4, matches.size());
		assertEquals("Pizza", matches.get(0).getFsnTerm());
		assertEquals("Cheese Pizza", matches.get(1).getFsnTerm());
		assertEquals("So Cheesy Pizza", matches.get(2).getFsnTerm());
		assertEquals("Really Cheesy Pizza", matches.get(3).getFsnTerm());

		matches = service.search(service.createQueryBuilder(true).descendant(parseLong(pizza_2.getConceptId())).termMatch("Piz"), PATH, PAGE_REQUEST).getContent();
		assertEquals(3, matches.size());
		assertEquals("Cheese Pizza", matches.get(0).getFsnTerm());
		assertEquals("So Cheesy Pizza", matches.get(1).getFsnTerm());
		assertEquals("Really Cheesy Pizza", matches.get(2).getFsnTerm());

		matches = service.search(service.createQueryBuilder(true).descendant(parseLong(pizza_2.getConceptId())).termMatch("Cheesy"), PATH, PAGE_REQUEST).getContent();
		assertEquals(2, matches.size());
		assertEquals("So Cheesy Pizza", matches.get(0).getFsnTerm());
		assertEquals("Really Cheesy Pizza", matches.get(1).getFsnTerm());
	}

	@Test
	public void testFindInactiveConcept() {
		Set<String> inactiveConceptId = Collections.singleton(inactivePizza_6.getId());
		List<ConceptMini> content = service.search(service.createQueryBuilder(true).conceptIds(inactiveConceptId), PATH, PAGE_REQUEST).getContent();
		assertEquals(1, content.size());
		assertEquals("Inactive Pizza", content.get(0).getFsnTerm());

		assertEquals(1, service.search(service.createQueryBuilder(true).termMatch("Inacti").definitionStatusFilter(Concepts.PRIMITIVE).conceptIds(inactiveConceptId), PATH, PAGE_REQUEST).getContent().size());
		assertEquals(0, service.search(service.createQueryBuilder(true).termMatch("Not").definitionStatusFilter(Concepts.PRIMITIVE).conceptIds(inactiveConceptId), PATH, PAGE_REQUEST).getContent().size());
		assertEquals(0, service.search(service.createQueryBuilder(true).definitionStatusFilter(Concepts.FULLY_DEFINED).conceptIds(inactiveConceptId), PATH, PAGE_REQUEST).getContent().size());
	}


	@Test
	public void testFindConceptsByTerm() {

		Page<ConceptMini> activeSearch = service.search(service.createQueryBuilder(true).termMatch("pizza").activeFilter(true), PATH, PAGE_REQUEST);
		assertEquals(4, activeSearch.getNumberOfElements());

		Page<ConceptMini> inactiveSearch = service.search(service.createQueryBuilder(true).termMatch("pizza").activeFilter(false), PATH, PAGE_REQUEST);
		assertEquals(1, inactiveSearch.getNumberOfElements());

		Page<ConceptMini> page = service.search(service.createQueryBuilder(true).termMatch("pizza"), PATH, PAGE_REQUEST);
		assertEquals(5, page.getNumberOfElements());
	}

	@Test
	public void testDefinitionStatusFilter() {
		QueryService.ConceptQueryBuilder query = service.createQueryBuilder(true)
				.ecl(pizza_2.getConceptId())
				.definitionStatusFilter(Concepts.SUFFICIENTLY_DEFINED);
		assertEquals(0, service.search(query, PATH, PAGE_REQUEST).getTotalElements());
		QueryService.ConceptQueryBuilder query2 = service.createQueryBuilder(true)
				.ecl(pizza_2.getConceptId())
				.definitionStatusFilter(Concepts.PRIMITIVE);
		assertEquals(1, service.search(query2, PATH, PAGE_REQUEST).getTotalElements());
	}

	@Test
	public void testPagination() {
		QueryService.ConceptQueryBuilder queryBuilder = service.createQueryBuilder(true).activeFilter(true);
		Page<ConceptMini> page = service.search(queryBuilder, PATH, PageRequest.of(0, 2));
		assertEquals(5, page.getTotalElements());
	}

}
