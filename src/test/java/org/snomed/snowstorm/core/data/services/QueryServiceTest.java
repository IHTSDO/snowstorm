package org.snomed.snowstorm.core.data.services;

import com.google.common.collect.Lists;
import io.kaicode.elasticvc.api.BranchService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.TestConfig;
import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.core.data.domain.ConceptMini;
import org.snomed.snowstorm.core.data.domain.Relationship;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.List;

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
	private BranchService branchService;

	@Autowired
	private ConceptService conceptService;

	private static final PageRequest PAGE_REQUEST = PageRequest.of(0, 50);

	@Before
	public void setup() {
		branchService.create("MAIN");
	}

	@Test
	public void testSearchResultOrdering() throws ServiceException {
		String path = "MAIN";
		Concept root = new Concept(SNOMEDCT_ROOT);
		Concept pizza_2 = new Concept("100002").addRelationship(new Relationship(ISA, SNOMEDCT_ROOT)).addFSN("Pizza");
		Concept cheesePizza_3 = new Concept("100005").addRelationship(new Relationship(ISA, pizza_2.getId())).addFSN("Cheese Pizza");
		Concept reallyCheesyPizza_4 = new Concept("100008").addRelationship(new Relationship(ISA, cheesePizza_3.getId())).addFSN("Really Cheesy Pizza");
		Concept reallyCheesyPizza_5 = new Concept("100003").addRelationship(new Relationship(ISA, reallyCheesyPizza_4.getId())).addFSN("So Cheesy Pizza");
		conceptService.batchCreate(Lists.newArrayList(root, pizza_2, cheesePizza_3, reallyCheesyPizza_4, reallyCheesyPizza_5), path);

		List<ConceptMini> matches = service.search(service.createQueryBuilder(true).termPrefix("Piz"), path, PAGE_REQUEST).getContent();
		assertEquals(4, matches.size());
		assertEquals("Pizza", matches.get(0).getFsn());
		assertEquals("Cheese Pizza", matches.get(1).getFsn());
		assertEquals("So Cheesy Pizza", matches.get(2).getFsn());
		assertEquals("Really Cheesy Pizza", matches.get(3).getFsn());

		matches = service.search(service.createQueryBuilder(true).descendant(parseLong(SNOMEDCT_ROOT)).termPrefix("Piz"), path, PAGE_REQUEST).getContent();
		assertEquals(4, matches.size());
		assertEquals("Pizza", matches.get(0).getFsn());
		assertEquals("Cheese Pizza", matches.get(1).getFsn());
		assertEquals("So Cheesy Pizza", matches.get(2).getFsn());
		assertEquals("Really Cheesy Pizza", matches.get(3).getFsn());

		matches = service.search(service.createQueryBuilder(true).descendant(parseLong(pizza_2.getConceptId())).termPrefix("Piz"), path, PAGE_REQUEST).getContent();
		assertEquals(3, matches.size());
		assertEquals("Cheese Pizza", matches.get(0).getFsn());
		assertEquals("So Cheesy Pizza", matches.get(1).getFsn());
		assertEquals("Really Cheesy Pizza", matches.get(2).getFsn());

		matches = service.search(service.createQueryBuilder(true).descendant(parseLong(pizza_2.getConceptId())).termPrefix("Cheesy"), path, PAGE_REQUEST).getContent();
		assertEquals(2, matches.size());
		assertEquals("So Cheesy Pizza", matches.get(0).getFsn());
		assertEquals("Really Cheesy Pizza", matches.get(1).getFsn());
	}

}
