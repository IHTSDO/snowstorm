package org.ihtsdo.elasticsnomed.core.data.services;

import com.google.common.collect.Lists;
import io.kaicode.elasticvc.api.BranchService;
import org.ihtsdo.elasticsnomed.TestConfig;
import org.ihtsdo.elasticsnomed.core.data.domain.Concept;
import org.ihtsdo.elasticsnomed.core.data.domain.Relationship;
import org.junit.After;
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

import static org.ihtsdo.elasticsnomed.core.data.domain.Concepts.ISA;
import static org.ihtsdo.elasticsnomed.core.data.domain.Concepts.SNOMEDCT_ROOT;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = TestConfig.class)
public class QueryServiceTest {

	@Autowired
	private QueryService service;

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
		Concept pizza_2 = new Concept("2").addRelationship(new Relationship(ISA, SNOMEDCT_ROOT));
		Concept cheesePizza_3 = new Concept("3").addRelationship(new Relationship(ISA, pizza_2.getId()));
		Concept brick_10 = new Concept("10").addRelationship(new Relationship(ISA, SNOMEDCT_ROOT));

		String branch = "MAIN";
		System.out.println("Create first three nodes");
		conceptService.create(Lists.newArrayList(root, pizza_2, cheesePizza_3, brick_10), branch);

		assertTC(root);
		assertTC(pizza_2, root);
		assertTC(cheesePizza_3, pizza_2, root);
		assertTC(brick_10, root);

		// Add another leaf node
		System.out.println("Add a second leaf to node 2");
		Concept pizzaWithTopping_4 = new Concept("4").addRelationship(new Relationship(ISA, pizza_2.getId()));
		conceptService.create(pizzaWithTopping_4, branch);

		assertTC(root);
		assertTC(pizza_2, root);
		assertTC(cheesePizza_3, pizza_2, root);
		assertTC(pizzaWithTopping_4, pizza_2, root);
		assertTC(brick_10, root);

		// Make an existing leaf node a child of another existing leaf node
		System.out.println("Move 3 from root to 4");
		cheesePizza_3.getRelationships().iterator().next().setActive(false);
		cheesePizza_3.addRelationship(new Relationship(ISA, pizzaWithTopping_4.getId()));
		conceptService.update(cheesePizza_3, branch);

		assertTC(root);
		assertTC(pizza_2, root);
		assertTC(cheesePizza_3, pizzaWithTopping_4, pizza_2, root);
		assertTC(pizzaWithTopping_4, pizza_2, root);
		assertTC(brick_10, root);

		// Make all exiting nodes a descendant of a new root child node
		System.out.println("Add node 5. Move 2 and descendants from root to 5.");
		Concept food_5 = new Concept("5").addRelationship(new Relationship(ISA, root.getId()));
		pizza_2.getRelationships().iterator().next().setActive(false);
		pizza_2.addRelationship(new Relationship(ISA, food_5.getId()));
		conceptService.createUpdate(Lists.newArrayList(food_5, pizza_2), branch);

		assertTC(root);
		assertTC(food_5, root);
		assertTC(pizza_2, food_5, root);
		assertTC(cheesePizza_3, pizzaWithTopping_4, pizza_2, food_5, root);
		assertTC(pizzaWithTopping_4, pizza_2, food_5, root);
		assertTC(brick_10, root);

		// Give part of the tree a second parent
		System.out.println("Give 2 as second parent of 10.");
		pizza_2.getRelationships().add(new Relationship(ISA, brick_10.getId()));
		conceptService.update(pizza_2, branch);

		assertTC(root);
		assertTC(food_5, root);
		assertTC(pizza_2, food_5, root, brick_10);
		assertTC(cheesePizza_3, pizzaWithTopping_4, pizza_2, food_5, root, brick_10);
		assertTC(pizzaWithTopping_4, pizza_2, food_5, root, brick_10);
		assertTC(brick_10, root);

		// Remove second parent via relationship inactivation
		System.out.println("Remove second parent from 2 via relationship inactivation.");
		pizza_2.getRelationshipsWithDestination(brick_10.getId()).iterator().next().setActive(false);
		conceptService.update(pizza_2, branch);

		assertTC(root);
		assertTC(food_5, root);
		assertTC(pizza_2, food_5, root);
		assertTC(cheesePizza_3, pizzaWithTopping_4, pizza_2, food_5, root);
		assertTC(pizzaWithTopping_4, pizza_2, food_5, root);
		assertTC(brick_10, root);

		// Give part of the tree a second parent again
		System.out.println("Give 2 as second parent of 10 again.");
		pizza_2.getRelationshipsWithDestination(brick_10.getId()).iterator().next().setActive(true);
		conceptService.update(pizza_2, branch);

		assertTC(root);
		assertTC(food_5, root);
		assertTC(pizza_2, food_5, root, brick_10);
		assertTC(cheesePizza_3, pizzaWithTopping_4, pizza_2, food_5, root, brick_10);
		assertTC(pizzaWithTopping_4, pizza_2, food_5, root, brick_10);
		assertTC(brick_10, root);


		// Remove second parent via relationship deletion
		System.out.println("Remove second parent from 2 via relationship deletion.");
		pizza_2.getRelationships().remove(pizza_2.getRelationshipsWithDestination(brick_10.getId()).iterator().next());
		conceptService.update(pizza_2, branch);

		assertTC(root);
		assertTC(food_5, root);
		assertTC(pizza_2, food_5, root);
		assertTC(cheesePizza_3, pizzaWithTopping_4, pizza_2, food_5, root);
		assertTC(pizzaWithTopping_4, pizza_2, food_5, root);
		assertTC(brick_10, root);

		// Move all those nodes back under root
		System.out.println("Move 2 and descendants from 5 back to root.");
		pizza_2.getRelationshipsWithDestination(food_5.getId()).iterator().next().setActive(false);
		pizza_2.getRelationshipsWithDestination(root.getId()).iterator().next().setActive(true);
		conceptService.update(pizza_2, branch);

		assertTC(root);
		assertTC(food_5, root);
		assertTC(pizza_2, root);
		assertTC(cheesePizza_3, pizzaWithTopping_4, pizza_2, root);
		assertTC(pizzaWithTopping_4, pizza_2, root);
		assertTC(brick_10, root);
	}

	@Test
	public void testDestinationHierarchyBranchTCInherited() {
		// Create two hierarchy branches of three and four concepts in length under the root
		Concept root = new Concept(SNOMEDCT_ROOT);

		Concept n11 = new Concept("11").addRelationship(new Relationship(ISA, SNOMEDCT_ROOT));
		Concept n12 = new Concept("12").addRelationship(new Relationship(ISA, n11.getId()));
		Concept n13 = new Concept("13").addRelationship(new Relationship(ISA, n12.getId()));

		Concept n21 = new Concept("21").addRelationship(new Relationship(ISA, SNOMEDCT_ROOT));
		Concept n22 = new Concept("22").addRelationship(new Relationship(ISA, n21.getId()));
		Concept n23 = new Concept("23").addRelationship(new Relationship(ISA, n22.getId()));
		Concept n24 = new Concept("24").addRelationship(new Relationship(ISA, n23.getId()));

		String branch = "MAIN";
		conceptService.create(Lists.newArrayList(root, n11, n12, n13, n21, n22, n23, n24), branch);

		assertTC(root);

		assertTC(n11, root);
		assertTC(n12, n11, root);// n12 starts under n11
		assertTC(n13, n12, n11, root);

		assertTC(n21, root);
		assertTC(n22, n21, root);
		assertTC(n23, n22, n21, root);
		assertTC(n24, n23, n22, n21, root);


		// Move part of first branch to end of second branch and assert that the full transitive closure is realised
		n12.getRelationships().clear();
		n12.getRelationships().add(new Relationship(ISA, n23.getId()));
		conceptService.update(n12, branch);

		assertTC(root);

		assertTC(n11, root);

		assertTC(n21, root);
		assertTC(n22, n21, root);
		assertTC(n23, n22, n21, root);
		assertTC(n24, n23, n22, n21, root);

		assertTC(n12, n23, n22, n21, root);// n12 ends up under n23, inheriting it's TC
		assertTC(n13, n12, n23, n22, n21, root);
	}

	private void assertTC(Concept concept, Concept... ancestors) {
		Set<Long> expectedAncestors = Arrays.stream(ancestors).map(Concept::getConceptIdAsLong).collect(Collectors.toSet());
		Assert.assertEquals(expectedAncestors, service.retrieveAncestors(concept.getId(),"MAIN", true));
	}

	@After
	public void tearDown() {
		conceptService.deleteAll();
		branchService.deleteAll();
	}

}
