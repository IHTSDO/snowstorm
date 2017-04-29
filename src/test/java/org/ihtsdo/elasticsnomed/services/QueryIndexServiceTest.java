package org.ihtsdo.elasticsnomed.services;

import com.google.common.collect.Lists;
import io.kaicode.elasticvc.api.BranchService;
import org.ihtsdo.elasticsnomed.TestConfig;
import org.ihtsdo.elasticsnomed.domain.Concept;
import org.ihtsdo.elasticsnomed.domain.Relationship;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.ihtsdo.elasticsnomed.domain.Concepts.ISA;
import static org.ihtsdo.elasticsnomed.domain.Concepts.SNOMEDCT_ROOT;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = TestConfig.class)
public class QueryIndexServiceTest {

	@Autowired
	private QueryIndexService service;

	@Autowired
	private BranchService branchService;

	@Autowired
	private ConceptService conceptService;

	@Before
	public void setup() {
		branchService.create("MAIN");
	}

	@Test
	public void testIncrementalStatedTransitiveClosureUpdate() throws Exception {
		// Create three nodes, each parent of the next
		Concept root = new Concept(SNOMEDCT_ROOT);
		Concept pizza = new Concept("2").addRelationship(new Relationship(ISA, SNOMEDCT_ROOT));
		Concept cheesePizza = new Concept("3").addRelationship(new Relationship(ISA, pizza.getId()));

		String branch = "MAIN";
		conceptService.create(Lists.newArrayList(root, pizza, cheesePizza), branch);

		assertTC(root);
		assertTC(pizza, root);
		assertTC(cheesePizza, pizza, root);

		// Add another leaf node
		Concept pizzaWithTopping = new Concept("4").addRelationship(new Relationship(ISA, pizza.getId()));
		conceptService.create(pizzaWithTopping, branch);

		assertTC(root);
		assertTC(pizza, root);
		assertTC(cheesePizza, pizza, root);
		assertTC(pizzaWithTopping, pizza, root);

		// Make an existing leaf node a child of another existing leaf node
		cheesePizza.getRelationships().iterator().next().setActive(false);
		cheesePizza.addRelationship(new Relationship(ISA, pizzaWithTopping.getId()));
		conceptService.update(cheesePizza, branch);

		assertTC(root);
		assertTC(pizza, root);
		assertTC(cheesePizza, pizzaWithTopping, pizza, root);
		assertTC(pizzaWithTopping, pizza, root);

		// Make all exiting nodes a descendant of a new root child node
		Concept food = new Concept("5").addRelationship(new Relationship(ISA, root.getId()));
		pizza.getRelationships().iterator().next().setActive(false);
		pizza.addRelationship(new Relationship(ISA, food.getId()));
		conceptService.createUpdate(Lists.newArrayList(food, pizza), branch);

		assertTC(root);
		assertTC(food, root);
		assertTC(pizza, food, root);
		assertTC(cheesePizza, pizzaWithTopping, pizza, food, root);
		assertTC(pizzaWithTopping, pizza, food, root);

		// Move all those nodes back under root
		pizza.getRelationshipsWithDestination(food.getId()).iterator().next().setActive(false);
		pizza.getRelationshipsWithDestination(root.getId()).iterator().next().setActive(true);
		conceptService.update(pizza, branch);

		assertTC(root);
		assertTC(food, root);
		assertTC(pizza, root);
		assertTC(cheesePizza, pizzaWithTopping, pizza, root);
		assertTC(pizzaWithTopping, pizza, root);
	}

	private void assertTC(Concept concept, Concept... ancestors) {
		Set<Long> expectedAncestors = Arrays.stream(ancestors).map(Concept::getConceptIdAsLong).collect(Collectors.toSet());
		Assert.assertEquals(expectedAncestors, service.retrieveAncestors(concept.getId(),"MAIN", true));
	}

}
