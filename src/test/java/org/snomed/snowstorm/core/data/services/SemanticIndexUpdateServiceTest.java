package org.snomed.snowstorm.core.data.services;

import com.google.common.collect.Lists;
import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.api.CommitListener;
import io.kaicode.elasticvc.domain.Commit;
import org.elasticsearch.index.query.MatchAllQueryBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.TestConfig;
import org.snomed.snowstorm.config.Config;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.data.services.transitiveclosure.GraphBuilderException;
import org.snomed.snowstorm.mrcm.MRCMLoader;
import org.snomed.snowstorm.mrcm.MRCMUpdateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;
import java.util.stream.Collectors;

import static io.kaicode.elasticvc.api.ComponentService.LARGE_PAGE;
import static org.junit.jupiter.api.Assertions.*;
import static org.snomed.snowstorm.core.data.domain.Concepts.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = TestConfig.class)
class SemanticIndexUpdateServiceTest extends AbstractTest {

	@Autowired
	private QueryService queryService;

	@Autowired
	private SemanticIndexUpdateService updateService;

	@Autowired
	private BranchService branchService;

	@Autowired
	private BranchMergeService branchMergeService;

	@Autowired
	private ConceptService conceptService;

	@Autowired
	private ConceptUpdateHelper conceptUpdateHelper;

	@Autowired
	private ElasticsearchRestTemplate elasticsearchTemplate;

	@Autowired
	private ReferenceSetMemberService referenceSetMemberService;

	private static final PageRequest PAGE_REQUEST = PageRequest.of(0, 50);

	@Test
	void testCommitListenerOrderingConfig() {
		List<CommitListener> commitListeners = branchService.getCommitListeners();
		assertEquals(7, commitListeners.size());
		assertEquals(MRCMLoader.class, commitListeners.get(0).getClass());
		assertEquals(ConceptDefinitionStatusUpdateService.class, commitListeners.get(1).getClass());
		assertEquals(SemanticIndexUpdateService.class, commitListeners.get(2).getClass());
		assertEquals(MRCMUpdateService.class, commitListeners.get(3).getClass());
		assertEquals(TraceabilityLogService.class, commitListeners.get(4).getClass());
		assertEquals(IntegrityService.class, commitListeners.get(5).getClass());
	}

	@Test
	void testIncrementalStatedTransitiveClosureUpdate() throws Exception {
		// Create three nodes, each parent of the next
		Concept root = new Concept(SNOMEDCT_ROOT);
		Concept pizza_2 = new Concept("100002").addRelationship(new Relationship(ISA, SNOMEDCT_ROOT));
		Concept cheesePizza_3 = new Concept("100003").addRelationship(new Relationship(ISA, pizza_2.getId()));
		Concept brick_10 = new Concept("1000010").addRelationship(new Relationship(ISA, SNOMEDCT_ROOT));

		String branch = "MAIN";
		System.out.println("Create first three nodes");
		conceptService.batchCreate(Lists.newArrayList(root, pizza_2, cheesePizza_3, brick_10), branch);

		assertTC(root);
		assertTC(pizza_2, root);
		assertTC(cheesePizza_3, pizza_2, root);
		assertTC(brick_10, root);

		// Add another leaf node
		System.out.println("Add a second leaf to node 2");
		Concept pizzaWithTopping_4 = new Concept("100004").addRelationship(new Relationship(ISA, pizza_2.getId()));
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
		Concept food_5 = new Concept("100005").addRelationship(new Relationship(ISA, root.getId()));
		pizza_2.getRelationships().iterator().next().setActive(false);
		pizza_2.addRelationship(new Relationship(ISA, food_5.getId()));
		conceptService.create(food_5, branch);
		conceptService.update(pizza_2, branch);

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
	void testIncrementalStatedUpdateUsingNonIsAChanges() throws Exception {
		Concept root = new Concept(SNOMEDCT_ROOT);
		Concept toppingAttribute = new Concept("110000000").addRelationship(new Relationship(ISA, root.getId()));
		Concept cheeseTopping = new Concept("210000000").addRelationship(new Relationship(ISA, root.getId()));
		Concept hamTopping = new Concept("220000000").addRelationship(new Relationship(ISA, root.getId()));

		Concept pizza = new Concept("200000000").addRelationship(new Relationship(ISA, SNOMEDCT_ROOT));

		Concept cheesePizza = new Concept("300000000").addRelationship(new Relationship(ISA, pizza.getId()))
		// Cheese pizza has a topping
				.addRelationship(new Relationship(toppingAttribute.getId(), cheeseTopping.getId()));

		Concept hamPizza = new Concept("400000000").addRelationship(new Relationship(ISA, pizza.getId()));

		String branch = "MAIN";
		conceptService.batchCreate(Lists.newArrayList(root, toppingAttribute, cheeseTopping, hamTopping, pizza, cheesePizza, hamPizza), branch);

		String ecl = "<<" + root.getId();
		assertEquals(7, eclSearchForIds(ecl, branch).size());
		String eclAnyConceptWithATopping = "<" + root.getId() + ":" + toppingAttribute.getId() + "=*";
		assertEquals(1, eclSearchForIds(eclAnyConceptWithATopping, branch).size(), "Should find 1 pizza with a topping");
		assertTC(hamPizza, pizza, root);

		// Add a topping to ham pizza
		hamPizza.addRelationship(new Relationship(toppingAttribute.getId(), hamTopping.getId()));
		conceptService.update(hamPizza, branch);

		assertEquals(2, eclSearchForIds(eclAnyConceptWithATopping, branch).size(), "Should now find 2 pizzas with a topping");

		assertTC(hamPizza, pizza, root);
	}

	private List<String> eclSearchForIds(String ecl, String branch) {
		return queryService.search(queryService.createQueryBuilder(false).ecl(ecl), branch, LARGE_PAGE).getContent().stream()
				.map(ConceptMini::getConceptId)
				.collect(Collectors.toList());
	}

	@Test
	void testDestinationHierarchyBranchTCInherited() throws ServiceException {
		// Create two hierarchy branches of three and four concepts in length under the root
		Concept root = new Concept(SNOMEDCT_ROOT);

		Concept n11 = new Concept("1000011").addRelationship(new Relationship(ISA, SNOMEDCT_ROOT));
		Concept n12 = new Concept("1000012").addRelationship(new Relationship(ISA, n11.getId()));
		Concept n13 = new Concept("1000013").addRelationship(new Relationship(ISA, n12.getId()));

		Concept n21 = new Concept("1000021").addRelationship(new Relationship(ISA, SNOMEDCT_ROOT));
		Concept n22 = new Concept("1000022").addRelationship(new Relationship(ISA, n21.getId()));
		Concept n23 = new Concept("1000023").addRelationship(new Relationship(ISA, n22.getId()));
		Concept n24 = new Concept("1000024").addRelationship(new Relationship(ISA, n23.getId()));

		String branch = "MAIN";
		conceptService.batchCreate(Lists.newArrayList(root, n11, n12, n13, n21, n22, n23, n24), branch);

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

	@Test
	void testUpdateAncestorWhereDescendantHasMultipleParents() throws ServiceException {
		Concept root = new Concept(SNOMEDCT_ROOT);

		Concept a = new Concept("100001001").addRelationship(new Relationship(ISA, SNOMEDCT_ROOT));
		Concept aa = new Concept("100001002").addRelationship(new Relationship(ISA, a.getId()));
		Concept aaa = new Concept("100001003").addRelationship(new Relationship(ISA, aa.getId()));

		Concept ab = new Concept("100002001").addRelationship(new Relationship(ISA, a.getId()));

		Concept ac = new Concept("100003001").addRelationship(new Relationship(ISA, a.getId()));
		Concept acc = new Concept("100003002").addRelationship(new Relationship(ISA, ac.getId()));
		// This concept has 2 parents in different parts of the 'a' hierarchy
		Concept accc = new Concept("100003003")
				.addRelationship(new Relationship(ISA, acc.getId()))
				.addRelationship(new Relationship(ISA, aaa.getId()));

		String branch = "MAIN";
		conceptService.batchCreate(Lists.newArrayList(root, a, aa, aaa, ab, ac, acc, accc), branch);

		assertTC(accc, a, aa, aaa, ac, acc, root);
		assertTC(ac, a, root);

		ac.getRelationships().clear();
		ac.addRelationship(new Relationship(ISA, ab.getId()));
		conceptService.update(ac, branch);

		assertTC(ac, ab, a, root);
		// After 'ac' is updated descendant 'accc' should gain ancestor 'ab' but should not loose existing ancestors from alternative routes e.g. 'aa'.
		assertTC(accc, a, aa, aaa, ac, acc, ab, root);
	}

	@Test
	void testSecondIsARemoval() throws ServiceException {
		Concept root = new Concept(SNOMEDCT_ROOT);

		Concept n11 = new Concept("1000011").addRelationship(new Relationship(ISA, SNOMEDCT_ROOT));
		Concept n12 = new Concept("1000012").addRelationship(new Relationship(ISA, n11.getId()));
		Concept n13 = new Concept("1000013").addRelationship(new Relationship(ISA, n12.getId()));
		Concept n14 = new Concept("1000014").addRelationship(new Relationship(ISA, n13.getId())).addRelationship(new Relationship(ISA, n12.getId()));

		String branch = "MAIN";
		conceptService.batchCreate(Lists.newArrayList(root, n11, n12, n13, n14), branch);

		assertTC(n14, n13, n12, n11, root);

		Set<Relationship> relationships = n14.getRelationships();
		assertEquals(2, relationships.size());
		List<Relationship> list = relationships.stream().filter(r -> r.getDestinationId().equals("1000013")).collect(Collectors.toList());
		relationships.removeAll(list);
		assertEquals(1, relationships.size());
		n14 = conceptService.update(n14, branch);

		assertTC(n14, n12, n11, root);
	}

	@Test
	void testDeleteSecondInferredIsAOnChildBranch() throws ServiceException {
		Concept root = new Concept(SNOMEDCT_ROOT);

		Concept n11 = new Concept("1000011").addRelationship(new Relationship(ISA, SNOMEDCT_ROOT));
		Concept n12 = new Concept("1000012").addRelationship(new Relationship(ISA, n11.getId()));
		Concept n13 = new Concept("1000013").addRelationship(new Relationship(ISA, n12.getId()));
		Concept n14 = new Concept("1000014").addRelationship(new Relationship(ISA, n13.getId())).addRelationship(new Relationship(ISA, n12.getId()));

		String branch = "MAIN/ABC";
		branchService.create(branch);
		conceptService.batchCreate(Lists.newArrayList(root, n11, n12, n13, n14), branch);

		assertTC(n14, branch, n13, n12, n11, root);

		branch = "MAIN/ABC/ABC-1";
		branchService.create(branch);

		n14 = conceptService.find(n14.getId(), branch);
		Set<Relationship> relationships = n14.getRelationships();
		assertEquals(2, relationships.size());
		Optional<Relationship> relationshipOptional = relationships.stream().filter(r -> r.getDestinationId().equals("1000013")).findFirst();
		assertTrue(relationshipOptional.isPresent());
		Relationship relationshipToDelete = relationshipOptional.get();
		assertTrue(relationships.remove(relationshipToDelete));
		assertEquals(1, relationships.size());
		n14 = conceptService.update(n14, branch);
		assertEquals(1, n14.getRelationships().size());

		assertTC(n14, branch, n12, n11, root);
		assertEquals(1, queryService.eclSearch(">!" + n14.getId(), false, branch, LARGE_PAGE).getTotalElements());
	}

	@Test
	void testInactivateSecondInferredIsAOnChildBranch() throws ServiceException {
		Concept root = new Concept(SNOMEDCT_ROOT);

		Concept n11 = new Concept("1000011").addRelationship(new Relationship(ISA, SNOMEDCT_ROOT));
		Concept n12 = new Concept("1000012").addRelationship(new Relationship(ISA, n11.getId()));
		Concept n13 = new Concept("1000013").addRelationship(new Relationship(ISA, n12.getId()));
		Concept n14 = new Concept("1000014").addRelationship(new Relationship(ISA, n13.getId())).addRelationship(new Relationship(ISA, n12.getId()));

		String branch = "MAIN/ABC";
		branchService.create(branch);
		conceptService.batchCreate(Lists.newArrayList(root, n11, n12, n13, n14), branch);

		assertTC(n14, branch, n13, n12, n11, root);

		branch = "MAIN/ABC/ABC-1";
		branchService.create(branch);

		n14 = conceptService.find(n14.getId(), branch);
		Set<Relationship> relationships = n14.getRelationships();
		assertEquals(2, relationships.size());
		Optional<Relationship> relationshipOptional = relationships.stream().filter(r -> r.getDestinationId().equals("1000013")).findFirst();
		assertTrue(relationshipOptional.isPresent());
		Relationship relationshipToMakeInactive = relationshipOptional.get();
		relationshipToMakeInactive.setActive(false);
		assertEquals(2, relationships.size());
		n14 = conceptService.update(n14, branch);
		assertEquals(2, n14.getRelationships().size());

		assertTC(n14, branch, n12, n11, root);
		assertEquals(1, queryService.eclSearch(">!" + n14.getId(), false, branch, LARGE_PAGE).getTotalElements());
	}

	@Test
	void testCircularReferenceInNormalCommitThrowsException() {
		Concept root = new Concept(SNOMEDCT_ROOT);
		Concept n11 = new Concept("1000011").addRelationship(new Relationship(ISA, SNOMEDCT_ROOT));
		Concept n12 = new Concept("1000012").addRelationship(new Relationship(ISA, n11.getId()));
		Concept n13 = new Concept("1000013").addRelationship(new Relationship(ISA, n12.getId()));
		n11.addRelationship(new Relationship(ISA, n13.getId()));
		String branch = "MAIN";
		List<Concept> concepts = Lists.newArrayList(root, n11, n12, n13);
		assertThrows(IllegalStateException.class, () -> conceptService.batchCreate(concepts, branch));
	}

	@Test
	void testCircularReferenceCreatedDuringRebaseDoesNotBreak() throws ServiceException {
		// On MAIN
		Concept root = new Concept(SNOMEDCT_ROOT);
		Concept cA = new Concept("1000000").addRelationship(new Relationship(ISA, SNOMEDCT_ROOT));
		Concept cB = new Concept("2000000").addRelationship(new Relationship(ISA, SNOMEDCT_ROOT));
		// C -> B
		Concept cC = new Concept("3000000").addRelationship(new Relationship(ISA, cB.getId()));
		conceptService.batchCreate(Lists.newArrayList(root, cA, cB, cC), "MAIN");

		// On MAIN/A
		// B -> A
		cB.addRelationship(new Relationship(ISA, cA.getId()));
		branchService.create("MAIN/A");
		conceptService.update(cB, "MAIN/A");

		// On MAIN
		// A -> C
		cA.addRelationship(new Relationship(ISA, cC.getId()));
		conceptService.update(cA, "MAIN");

		cA = conceptService.find(cA.getId(), "MAIN");
		assertEquals(2, cA.getRelationships().size());
		assertEquals(1, cA.getRelationshipsWithDestination(SNOMEDCT_ROOT).size());
		assertEquals(1, cA.getRelationshipsWithDestination(cC.getId()).size());

		cA = conceptService.find(cA.getId(), "MAIN/A");
		assertEquals(1, cA.getRelationships().size());
		assertEquals(1, cA.getRelationshipsWithDestination(SNOMEDCT_ROOT).size());

		// Rebase causes the loop
		branchMergeService.mergeBranchSync("MAIN", "MAIN/A", Collections.emptySet());

		cA = conceptService.find(cA.getId(), "MAIN/A");
		assertEquals(2, cA.getRelationships().size());
		assertEquals(1, cA.getRelationshipsWithDestination(SNOMEDCT_ROOT).size());
		assertEquals(1, cA.getRelationshipsWithDestination(cC.getId()).size());

		// Expecting loop B -> A -> C -> B
		assertEquals("[" + SNOMEDCT_ROOT + ", " + cC.getId() + ", " + cA.getId() + "]", eclSearchForIds(">" + cB.getId(), "MAIN/A").toString());
	}

	@Test
	void testCircularReferenceCreatedDuringNormalCommits() throws ServiceException {
		// On MAIN
		Concept root = new Concept(SNOMEDCT_ROOT);
		Concept cA = new Concept("1000011").addRelationship(new Relationship(ISA, SNOMEDCT_ROOT));
		Concept cB = new Concept("1000012").addRelationship(new Relationship(ISA, SNOMEDCT_ROOT));
		// C -> B
		Concept cC = new Concept("1000013").addRelationship(new Relationship(ISA, cB.getId()));
		conceptService.batchCreate(Lists.newArrayList(root, cA, cB, cC), "MAIN");

		// On MAIN
		// B -> A
		cB.addRelationship(new Relationship(ISA, cA.getId()));
		conceptService.update(cB, "MAIN");

		// On MAIN
		// A -> C
		// Commit causes the loop
		cA.addRelationship(new Relationship(ISA, cC.getId()));
		try {
			conceptService.update(cA, "MAIN");
			fail("Should have thrown IllegalStateException > GraphBuilderException");
		} catch (IllegalStateException e) {
			GraphBuilderException graphBuilderException = (GraphBuilderException) e.getCause();
			assertEquals("Loop found in transitive closure for concept 1000011 on branch MAIN. " +
					"The concept 1000011 is in its own set of ancestors: [1000013, 1000012, 1000011, 138875005]", graphBuilderException.getMessage());
		}
	}

	@Test
	void inactiveConceptsNotAddedToStatedIndex() throws ServiceException {
		String path = "MAIN";
		conceptService.create(new Concept(SNOMEDCT_ROOT), path);
		Concept ambulanceman = new Concept().addFSN("Ambulanceman")
				// Stated relationship
				.addRelationship(new Relationship(ISA, SNOMEDCT_ROOT).setInferred(false));
		ambulanceman.setActive(false);
		conceptService.create(ambulanceman, path);

		boolean stated = true;
		Page<ConceptMini> concepts = queryService.search(queryService.createQueryBuilder(stated).ecl("<" + SNOMEDCT_ROOT), path, PAGE_REQUEST);
		assertEquals(0, concepts.getTotalElements());
	}

	@Test
	void inactiveRelationshipsNotAddedToInferredIndex() throws ServiceException {
		String path = "MAIN";
		conceptService.create(new Concept(SNOMEDCT_ROOT), path);
		Concept ambulanceman = new Concept().addFSN("Ambulanceman").addRelationship(new Relationship(ISA, SNOMEDCT_ROOT));
		ambulanceman.setActive(false);
		ambulanceman.getRelationships().forEach(r -> r.setActive(false));
		conceptService.create(ambulanceman, path);

		boolean stated = false;
		Page<ConceptMini> concepts = queryService.search(queryService.createQueryBuilder(stated).ecl("<" + SNOMEDCT_ROOT), path, PAGE_REQUEST);
		assertEquals(0, concepts.getTotalElements());
	}

	@Test
	void testSwapParentsMustNotCreateCircularReference() throws ServiceException {
		// On MAIN
		Concept root = new Concept(SNOMEDCT_ROOT);
		Concept isA = new Concept(ISA).addRelationship(new Relationship(ISA, SNOMEDCT_ROOT));
		// A -> root
		Concept cA = new Concept("1001000").addRelationship(new Relationship(ISA, SNOMEDCT_ROOT));
		// B -> A
		Concept cB = new Concept("1002000").addRelationship(new Relationship(ISA, cA.getId()));
		// C -> A
		Concept cC = new Concept("1003000").addRelationship(new Relationship(ISA, cA.getId()));

		conceptService.batchCreate(Lists.newArrayList(root, isA, cA, cB, cC), "MAIN");

		// On MAIN/A
		// C -> B
		branchService.create("MAIN/A");
		cC.addRelationship(new Relationship(ISA, cB.getId()));
		conceptService.update(cC, "MAIN/A");

		// On MAIN
		// B -> C
		cB.addRelationship(new Relationship(ISA, cC.getId()));
		conceptService.update(cB, "MAIN");

		// Rebase MAIN/A
		// Introduces a transitive closure loop but will be fixed in MAIN/A
		branchMergeService.mergeBranchSync("MAIN", "MAIN/A", Collections.emptySet());

		// Update from C -> B -> A
		// Update to   B -> C -> A
		/// Remove C -> B
		cC.getRelationships().forEach(relationship -> relationship.setActive(false));
		/// C -> A
		cC.addRelationship(new Relationship(ISA, cA.getId()));
		conceptService.update(cC, "MAIN/A");

		Page<ConceptMini> concepts = queryService.eclSearch(">! " + cB.getId(), false, "MAIN/A", PageRequest.of(0, 10));
		assertEquals("[" + cC.getId() + ", 1001000]", concepts.getContent().stream().map(ConceptMini::getConceptId).collect(Collectors.toList()).toString());
	}

	@Test
	void inactiveConceptsRemovedFromStatedIndex() throws ServiceException {
		String path = "MAIN";
		conceptService.create(new Concept(SNOMEDCT_ROOT), path);
		Concept ambulanceman = conceptService.create(new Concept("10000123").addFSN("Ambulanceman")
				// Stated relationship
				.addRelationship(new Relationship(ISA, SNOMEDCT_ROOT).setInferred(false)),
				path);

		boolean stated = true;
		Page<ConceptMini> concepts = queryService.search(queryService.createQueryBuilder(stated).ecl("<<" + SNOMEDCT_ROOT).descriptionTerm("Amb"), path, PAGE_REQUEST);
		assertEquals(1, concepts.getTotalElements());

		ambulanceman.setActive(false);
		conceptService.update(ambulanceman, path);

		concepts = queryService.search(queryService.createQueryBuilder(stated).ecl("<<" + SNOMEDCT_ROOT).descriptionTerm("Amb"), path, PAGE_REQUEST);
		assertEquals(0, concepts.getTotalElements());
	}

	@Test
	void unversionedInactiveInferredRelationshipsRemoved() throws ServiceException {
		String path = "MAIN";
		conceptService.create(new Concept(SNOMEDCT_ROOT), path);
		Concept ambulanceman = conceptService.create(new Concept("10000123").addFSN("Ambulanceman").addRelationship(new Relationship(ISA, SNOMEDCT_ROOT)), path);

		Page<ConceptMini> concepts = queryService.search(queryService.createQueryBuilder(false).ecl("<<" + SNOMEDCT_ROOT).descriptionTerm("Amb"), path, PAGE_REQUEST);
		assertEquals(1, concepts.getTotalElements());

		// The classification process would usually do this
		ambulanceman.getRelationships().forEach(r -> r.setActive(false));
		conceptService.update(ambulanceman, path);

		concepts = queryService.search(queryService.createQueryBuilder(false).ecl("<<" + SNOMEDCT_ROOT).descriptionTerm("Amb"), path, PAGE_REQUEST);
		assertEquals(0, concepts.getTotalElements());
	}

	@Test
	void testGetParentPaths() {
		assertEquals("[]", updateService.getParentPaths("MAIN").toString());
		assertEquals("[MAIN]", updateService.getParentPaths("MAIN/ONE").toString());
		assertEquals("[MAIN/ONE, MAIN]", updateService.getParentPaths("MAIN/ONE/ONE-123").toString());
	}

	@Test
	void testSavePartialBatch() throws ServiceException {
		List<Concept> concepts = new ArrayList<>();
		concepts.add(new Concept(SNOMEDCT_ROOT));
		int conceptCount = Config.BATCH_SAVE_SIZE + 100;
		for (int i = 0; i < conceptCount; i++) {
			concepts.add(new Concept("10000" + i).addRelationship(new Relationship(ISA, SNOMEDCT_ROOT)));
		}
		String branch = "MAIN";
		conceptService.batchCreate(concepts, branch);

		Page<ConceptMini> page = queryService.search(queryService.createQueryBuilder(false).ecl("<<" + SNOMEDCT_ROOT), branch, QueryService.PAGE_OF_ONE);
		assertEquals(conceptCount + 1, page.getTotalElements());
	}

	@Test
	void testRelationshipEffectiveDateSorting() throws ServiceException {
		List<Concept> concepts = new ArrayList<>();
		concepts.add(new Concept(SNOMEDCT_ROOT));
		for (int conceptId = 0; conceptId < 50; conceptId++) {
			Relationship relationship = new Relationship(ISA, SNOMEDCT_ROOT);
			relationship.setRelationshipId(conceptId + "020");
			concepts.add(new Concept("10000" + conceptId).addRelationship(relationship));
		}
		String branch = "MAIN";
		conceptService.batchCreate(concepts, branch);

		assertEquals(51, queryService.search(queryService.createQueryBuilder(false).ecl("<<" + SNOMEDCT_ROOT), branch, QueryService.PAGE_OF_ONE).getTotalElements(),
				"Total concepts should be 51");

		List<Relationship> relationshipVersions = new ArrayList<>();
		for (int conceptId = 0; conceptId < 50; conceptId++) {
			for (int relationshipVersion = 1; relationshipVersion < 10; relationshipVersion++) {
				Relationship relationship = new Relationship(ISA, SNOMEDCT_ROOT);
				relationship.setRelationshipId("10000" + conceptId + "020");
				relationship.setSourceId("10000" + conceptId);
				relationship.setActive((relationshipVersion % 2) == 1);
				relationship.setEffectiveTimeI(20180100 + relationshipVersion);
				relationship.markChanged();
				relationshipVersions.add(relationship);
			}
		}
		try (Commit commit = branchService.openCommit(branch)) {
			conceptUpdateHelper.doSaveBatchRelationships(relationshipVersions, commit);
			commit.markSuccessful();
		}

		assertEquals(51, queryService.search(queryService.createQueryBuilder(false).ecl("<<" + SNOMEDCT_ROOT), branch, QueryService.PAGE_OF_ONE).getTotalElements(),
				"Total concepts should be 51");
	}

	@Test
	void testRebuildSemanticIndexWithMixedEffectiveDates() throws ServiceException {
		String path = "MAIN";
		List<Concept> concepts = new ArrayList<>();

		concepts.add(new Concept(SNOMEDCT_ROOT));
		concepts.add(new Concept("116680003")
				.addRelationship(new Relationship(ISA, SNOMEDCT_ROOT))
		);
		concepts.add(new Concept("39607008")
				.addRelationship(new Relationship(ISA, SNOMEDCT_ROOT))
		);
		concepts.add(new Concept("363698007")
				.addRelationship(new Relationship(ISA, SNOMEDCT_ROOT))
		);

		conceptService.batchCreate(concepts, path);
		concepts.clear();

		concepts.add(new Concept("34020007")
				.addRelationship(new Relationship(UUID.randomUUID().toString(), ISA, SNOMEDCT_ROOT))
				.addRelationship(new Relationship("756906025", 20040731, false, "900000000000207008", "34020007", "39607008", 1, "363698007", "900000000000011006", "900000000000451002"))
				.addRelationship(new Relationship("3250849023", 20100131, false, "900000000000207008", "34020007", "39607008", 1, "363698007", "900000000000011006", "900000000000451002"))
				.addRelationship(new Relationship("3332956025", 20150731, false, "900000000000207008", "34020007", "39607008", 2, "363698007", "900000000000011006", "900000000000451002"))
				.addRelationship(new Relationship("5963641025", 20150731, true, "900000000000207008", "34020007", "39607008", 1, "363698007", "900000000000011006", "900000000000451002"))
		);

		// Use low level component save to prevent effectiveTimes being stripped by concept service
		simulateRF2Import(path, concepts);

		assertEquals(4, queryService.search(queryService.createQueryBuilder(false).ecl("<" + SNOMEDCT_ROOT), path, QueryService.PAGE_OF_ONE).getTotalElements());
		assertEquals(1, queryService.search(queryService.createQueryBuilder(false).ecl("*:363698007=*"), path, QueryService.PAGE_OF_ONE).getTotalElements());
	}

	@Test
	void testRebuildSemanticIndexWithSameTripleActiveAndInactiveOnSameDate() throws ServiceException, InterruptedException {
		String path = "MAIN";
		List<Concept> concepts = new ArrayList<>();

		concepts.add(new Concept(SNOMEDCT_ROOT));
		concepts.add(new Concept("116680003")
				.addRelationship(new Relationship(ISA, SNOMEDCT_ROOT))
		);
		concepts.add(new Concept("39607008")
				.addRelationship(new Relationship(ISA, SNOMEDCT_ROOT))
		);
		concepts.add(new Concept("363698007")
				.addRelationship(new Relationship(ISA, SNOMEDCT_ROOT))
		);

		conceptService.batchCreate(concepts, path);
		concepts.clear();

		concepts.add(new Concept("34020007")
				.addRelationship(new Relationship(UUID.randomUUID().toString(), ISA, SNOMEDCT_ROOT))
				.addRelationship(new Relationship("3332956025", 20150731, false, "900000000000207008", "34020007", "39607008", 1, "363698007", "900000000000011006", "900000000000451002"))
				.addRelationship(new Relationship("5963641025", 20150731, true, "900000000000207008", "34020007", "39607008", 1, "363698007", "900000000000011006", "900000000000451002"))
		);

		// Use low level component save to prevent effectiveTimes being stripped by concept service
		simulateRF2Import(path, concepts);

		assertEquals(4, queryService.search(queryService.createQueryBuilder(false).ecl("<" + SNOMEDCT_ROOT), path, QueryService.PAGE_OF_ONE).getTotalElements());
		assertEquals(1, queryService.search(queryService.createQueryBuilder(false).ecl("*:363698007=*"), path, QueryService.PAGE_OF_ONE).getTotalElements());
		assertEquals(5, queryService.search(queryService.createQueryBuilder(false).ecl("<<" + SNOMEDCT_ROOT), path, QueryService.PAGE_OF_ONE).getTotalElements());

		// Delete all documents in semantic index and rebuild

		List<QueryConcept> queryConcepts = elasticsearchTemplate.search(new NativeSearchQueryBuilder().build(), QueryConcept.class)
				.stream().map(SearchHit::getContent).collect(Collectors.toList());
		assertEquals(6, queryConcepts.size());

		Query deleteQuery = new NativeSearchQueryBuilder().withQuery(new MatchAllQueryBuilder()).build();
		elasticsearchTemplate.delete(deleteQuery, QueryConcept.class, elasticsearchTemplate.getIndexCoordinatesFor(QueryConcept.class));

		// Wait for deletion to flush through
		Thread.sleep(2000);

		queryConcepts = elasticsearchTemplate.search(new NativeSearchQueryBuilder().build(), QueryConcept.class)
				.stream().map(SearchHit::getContent).collect(Collectors.toList());
		assertEquals(0, queryConcepts.size());

		assertEquals(0, queryService.search(queryService.createQueryBuilder(false).ecl("<" + SNOMEDCT_ROOT), path, QueryService.PAGE_OF_ONE).getTotalElements());

		updateService.rebuildStatedAndInferredSemanticIndex(path);

		assertEquals(4, queryService.search(queryService.createQueryBuilder(false).ecl("<" + SNOMEDCT_ROOT), path, QueryService.PAGE_OF_ONE).getTotalElements());
		assertEquals(1, queryService.search(queryService.createQueryBuilder(false).ecl("*:363698007=*"), path, QueryService.PAGE_OF_ONE).getTotalElements());
		assertEquals(5, queryService.search(queryService.createQueryBuilder(false).ecl("<<" + SNOMEDCT_ROOT), path, QueryService.PAGE_OF_ONE).getTotalElements());
	}

	@Test
	void testSameTripleMadeInactiveInDifferentModule() throws ServiceException {
		// There are around 150 instances in the US Edition of 'is a' relationships being made inactive in the US module straight
		// after the same triple is made active in the International Module (different relationship id).
		// This test checks that making the same triple inactive in a different module does not remove the triple from the semantic index.

		String path = "MAIN";
		List<Concept> concepts = new ArrayList<>();

		concepts.add(new Concept(SNOMEDCT_ROOT));
		String internationalCoreModule = "900000000000207008";
		String usModule = "731000124108";

		// Create concept with US relationship
		Concept concept = new Concept("272379006").setModuleId(internationalCoreModule)
				.addRelationship(new Relationship(ISA, SNOMEDCT_ROOT).setModuleId(usModule));
		concepts.add(concept);
		conceptService.batchCreate(concepts, path);
		concepts.clear();

		assertEquals(1, queryService.search(queryService.createQueryBuilder(false).ecl("<" + SNOMEDCT_ROOT), path, QueryService.PAGE_OF_ONE).getTotalElements());

		// Add Int relationship
		concept.getRelationships().add(new Relationship(ISA, SNOMEDCT_ROOT).setModuleId(internationalCoreModule));
		conceptService.update(concept, path);

		assertEquals(1, queryService.search(queryService.createQueryBuilder(false).ecl("<" + SNOMEDCT_ROOT), path, QueryService.PAGE_OF_ONE).getTotalElements());

		// Make US relationship inactive
		concept.getRelationships().stream().filter(relationship -> relationship.getModuleId().equals(usModule)).forEach(relationship -> relationship.setActive(false));
		conceptService.update(concept, path);

		assertEquals(1, queryService.search(queryService.createQueryBuilder(false).ecl("<" + SNOMEDCT_ROOT), path, QueryService.PAGE_OF_ONE).getTotalElements());
	}

	@Test
	// This tests the semantic index recognises that relationships can be made inactive on a parent branch but still be active on the child.
	// The concepts used in this unit test are completely meaningless, as usual.
	void testSameTripleMadeInactiveInChildBranchAfterParentChanged() throws ServiceException {
		String path = "MAIN";
		String extensionBranch = "MAIN/SNOMEDCT-US";
		List<Concept> concepts = new ArrayList<>();

		concepts.add(new Concept(SNOMEDCT_ROOT));
		concepts.add(new Concept(CLINICAL_FINDING).addRelationship(new Relationship(ISA, SNOMEDCT_ROOT)));
		concepts.add(new Concept(FINDING_SITE).addRelationship(new Relationship(ISA, SNOMEDCT_ROOT)));

		// Create concept with relationship to root and clinical finding
		String conceptId = "272379006";
		Concept concept = new Concept(conceptId)
				.addRelationship(new Relationship(ISA, SNOMEDCT_ROOT))
				.addRelationship(new Relationship(ISA, CLINICAL_FINDING));
		concepts.add(concept);
		conceptService.batchCreate(concepts, path);
		concepts.clear();

		// Create child branch
		branchService.create(extensionBranch);
		assertEquals(1, queryService.search(queryService.createQueryBuilder(false).ecl("<" + CLINICAL_FINDING), extensionBranch, QueryService.PAGE_OF_ONE).getTotalElements(),
				"Concept exists on child branch");

		// On the parent branch inactivate the concept parents and add another
		concept.getRelationships().forEach(relationship -> relationship.setActive(false));
		concept.getRelationships().add(new Relationship(ISA, FINDING_SITE));
		conceptService.update(concept, path);

		// Child branch is still on the original version of the concept.
		// Make the relationship to root inactive - should still have the parent to clinical finding after that
		Concept extensionVersionConcept = conceptService.find(conceptId, extensionBranch);
		extensionVersionConcept.getRelationships().stream()
				.filter(relationship -> relationship.getDestinationId().equals(SNOMEDCT_ROOT)).forEach(relationship -> relationship.setActive(false));
		conceptService.update(extensionVersionConcept, extensionBranch);

		assertEquals(1, queryService.search(queryService.createQueryBuilder(false).ecl("<" + CLINICAL_FINDING), extensionBranch, QueryService.PAGE_OF_ONE).getTotalElements());
	}

	@Test
	// This tests the semantic index recognises that relationships can be made inactive on a grandparent branch but still be active on the child.
	// The concepts used in this unit test are completely meaningless, as usual.
	void testSameTripleMadeInactiveInChildBranchAfterGrandparentChanged() throws ServiceException {
		String path = "MAIN";
		String extensionBranch = "MAIN/SNOMEDCT-US";
		String extensionProjectBranch = "MAIN/SNOMEDCT-US/PROJECTA";
		List<Concept> concepts = new ArrayList<>();

		concepts.add(new Concept(SNOMEDCT_ROOT));
		concepts.add(new Concept(CLINICAL_FINDING).addRelationship(new Relationship(ISA, SNOMEDCT_ROOT)));
		concepts.add(new Concept(FINDING_SITE).addRelationship(new Relationship(ISA, SNOMEDCT_ROOT)));

		// Create concept with relationship to root and clinical finding
		String conceptId = "272379006";
		Concept concept = new Concept(conceptId)
				.addRelationship(new Relationship(ISA, SNOMEDCT_ROOT))
				.addRelationship(new Relationship(ISA, CLINICAL_FINDING));
		concepts.add(concept);
		conceptService.batchCreate(concepts, path);
		concepts.clear();

		// Create child branch
		branchService.create(extensionBranch);

		// On the parent branch inactivate the concept
		concept.setActive(false);
		conceptService.update(concept, path);

		// Create grandchild branch
		branchService.create(extensionProjectBranch);
		assertEquals(1, queryService.search(queryService.createQueryBuilder(false).ecl("<" + CLINICAL_FINDING), extensionProjectBranch, QueryService.PAGE_OF_ONE).getTotalElements(),
				"Concept exists on grandchild branch");

		// Grandchild branch is still on the original version of the concept.
		// Make the relationship to root inactive - should still have the parent to clinical finding after that
		Concept extensionVersionConcept = conceptService.find(conceptId, extensionProjectBranch);
		extensionVersionConcept.getRelationships().stream()
				.filter(relationship -> relationship.getDestinationId().equals(SNOMEDCT_ROOT)).forEach(relationship -> relationship.setActive(false));
		conceptService.update(extensionVersionConcept, extensionProjectBranch);

		assertEquals(1, queryService.search(queryService.createQueryBuilder(false).ecl("<" + CLINICAL_FINDING), extensionProjectBranch, QueryService.PAGE_OF_ONE).getTotalElements());
	}

	@Test
	void testRebuildRestoresParentVisibility() throws ServiceException {
		String path = "MAIN";
		String projectBranch = "MAIN/TEST1";
		List<Concept> concepts = new ArrayList<>();

		concepts.add(new Concept(SNOMEDCT_ROOT));
		concepts.add(new Concept(CLINICAL_FINDING).addRelationship(new Relationship(ISA, SNOMEDCT_ROOT)));
		concepts.add(new Concept(FINDING_SITE).addRelationship(new Relationship(ISA, SNOMEDCT_ROOT)));
		concepts.add(new Concept("100100000001")
				.addRelationship(new Relationship(ISA, SNOMEDCT_ROOT))
				.addRelationship(new Relationship(FINDING_SITE, "100200000001"))
		);
		concepts.add(new Concept("100200000001").addRelationship(new Relationship(ISA, SNOMEDCT_ROOT)));
		concepts.add(new Concept("100300000001").addRelationship(new Relationship(ISA, SNOMEDCT_ROOT)));

		conceptService.batchCreate(concepts, path);
		concepts.clear();

		// Create child branch
		branchService.create(projectBranch);
		assertEquals(1, queryService.search(queryService.createQueryBuilder(false).ecl("100100000001"), projectBranch, QueryService.PAGE_OF_ONE).getTotalElements(),
				"Concept exists on child branch");

		// On the child branch add another attribute
		Concept concept = conceptService.find("100100000001", projectBranch);
		concept.getRelationships().add(new Relationship(FINDING_SITE, "100300000001"));
		conceptService.update(concept, projectBranch);

		assertEquals(1, queryService.search(queryService.createQueryBuilder(false).ecl("100100000001"), projectBranch, QueryService.PAGE_OF_ONE).getTotalElements(),
				"Concept exists on child branch");
		assertEquals(1, queryService.search(queryService.createQueryBuilder(false).ecl("100100000001 : " + FINDING_SITE + " = 100300000001"), projectBranch,
				QueryService.PAGE_OF_ONE).getTotalElements(),
				"Concept has attribute in semantic index on child branch");

		// Remove attribute from concept
		concept = conceptService.find("100100000001", projectBranch);
		concept.setRelationships(concept.getRelationships().stream()
				.filter(r -> !r.getDestinationId().equals("100300000001"))
				.collect(Collectors.toSet()));
		conceptService.update(concept, projectBranch);

		assertEquals(1, queryService.search(queryService.createQueryBuilder(false).ecl("100100000001"), projectBranch, QueryService.PAGE_OF_ONE).getTotalElements(),
				"Concept exists on child branch");
		assertEquals(0, queryService.search(queryService.createQueryBuilder(false).ecl("100100000001 : " + FINDING_SITE + " = 100300000001"), projectBranch,
				QueryService.PAGE_OF_ONE).getTotalElements(),
				"Concept does not have attribute in semantic index on child branch");

		// Make a commit on MAIN
		conceptService.create(new Concept("100400000001").addRelationship(new Relationship(ISA, SNOMEDCT_ROOT)), path);

		// Rebase the child branch
		try (Commit rebaseCommit = branchService.openRebaseCommit(projectBranch)) {
			rebaseCommit.markSuccessful();
		}

		// This fails before the fix for MAINT-1501
		assertEquals(1, queryService.search(queryService.createQueryBuilder(false).ecl("100100000001"), projectBranch, QueryService.PAGE_OF_ONE).getTotalElements(),
				"Concept exists on child branch");
	}

	@Test
	public void testAutoMergeAttributeOnLeftTCChangeOnRight() throws ServiceException {
		List<Concept> concepts = new ArrayList<>();

		concepts.add(new Concept(SNOMEDCT_ROOT));
		concepts.add(new Concept(CLINICAL_FINDING).addRelationship(new Relationship(ISA, SNOMEDCT_ROOT)));
		concepts.add(new Concept(FINDING_SITE).addRelationship(new Relationship(ISA, SNOMEDCT_ROOT)));
		concepts.add(new Concept("100000001").addRelationship(new Relationship(ISA, SNOMEDCT_ROOT)));
		concepts.add(new Concept("200000001").addRelationship(new Relationship(ISA, SNOMEDCT_ROOT)));
		concepts.add(new Concept("300000001").addRelationship(new Relationship(ISA, "200000001")));

		conceptService.batchCreate(concepts, "MAIN");

		branchService.create("MAIN/A");

		// Add an attribute to concept 3 in MAIN
		addRelationship("300000001", FINDING_SITE, CLINICAL_FINDING, "MAIN");

		// Change transitive closure of concept 3 (via concept 2) in project
		addRelationship("200000001", ISA, "100000001", "MAIN/A");

		branchMergeService.mergeBranchSync("MAIN", "MAIN/A", Collections.emptySet());

		assertEquals(0, eclSearchForIds("<100000001 AND 300000001", "MAIN").size());
		assertEquals(1, eclSearchForIds("300000001:" + FINDING_SITE + "=*", "MAIN").size());
		assertEquals(1, eclSearchForIds("<100000001 AND 300000001", "MAIN/A").size());
		assertEquals(1, eclSearchForIds("300000001:" + FINDING_SITE + "=*", "MAIN/A").size());
	}

	@Test
	public void testAutoMergeTCChangeOnRightAttributeOnLeft() throws ServiceException {
		List<Concept> concepts = new ArrayList<>();

		concepts.add(new Concept(SNOMEDCT_ROOT));
		concepts.add(new Concept(CLINICAL_FINDING).addRelationship(new Relationship(ISA, SNOMEDCT_ROOT)));
		concepts.add(new Concept(FINDING_SITE).addRelationship(new Relationship(ISA, SNOMEDCT_ROOT)));
		concepts.add(new Concept("100000001").addRelationship(new Relationship(ISA, SNOMEDCT_ROOT)));
		concepts.add(new Concept("200000001").addRelationship(new Relationship(ISA, SNOMEDCT_ROOT)));
		concepts.add(new Concept("300000001").addRelationship(new Relationship(ISA, "200000001")));

		conceptService.batchCreate(concepts, "MAIN");

		branchService.create("MAIN/A");

		// Add an attribute to concept 3 in project
		addRelationship("300000001", FINDING_SITE, CLINICAL_FINDING, "MAIN/A");

		// Change transitive closure of concept 3 (via concept 2) in MAIN
		addRelationship("200000001", ISA, "100000001", "MAIN");

		branchMergeService.mergeBranchSync("MAIN", "MAIN/A", Collections.emptySet());

		assertEquals(1, eclSearchForIds("<100000001 AND 300000001", "MAIN").size());
		assertEquals(0, eclSearchForIds("300000001:" + FINDING_SITE + "=*", "MAIN").size());
		assertEquals(1, eclSearchForIds("<100000001 AND 300000001", "MAIN/A").size());
		assertEquals(1, eclSearchForIds("300000001:" + FINDING_SITE + "=*", "MAIN/A").size());
	}

	private void addRelationship(String conceptId, String type, String target, String branch) throws ServiceException {
		final Concept concept = conceptService.find(conceptId, branch);
		concept.addRelationship(new Relationship(type, target));
		conceptService.update(concept, branch);
	}

	@Test
	void testRebuildSemanticIndexWithNumericValueForStringType() throws ServiceException {
		String path = "MAIN";
		List<Concept> concepts = createConcreteValueTestConcepts(path);

		concepts.add(new Concept("34020007").addRelationship(new Relationship(UUID.randomUUID().toString(), ISA, "34020006"))
				// The data type is String for 396070081 but actual value is an integer
				.addRelationship(new Relationship("4332956027", null, true, "900000000000207008", "34020007",
						"#10", 1, "396070081", "900000000000011006", "900000000000451002")));
		Exception exception = assertThrows(IllegalStateException.class, () -> {
			simulateRF2Import(path, concepts);
		});
		assertEquals("Concrete value 10 with data type DECIMAL in relationship is not matching data type STRING defined in the MRCM for attribute 396070081",
				exception.getMessage());
	}

	@Test
	void testRebuildSemanticIndexWhenNoDataTypeDefined() throws ServiceException {
		String path = "MAIN";
		List<Concept> concepts = createConcreteValueTestConcepts(path);

		// 396070085 has no data type defined in the MRCM
		concepts.add(new Concept("34020007").addRelationship(new Relationship(UUID.randomUUID().toString(), ISA, "34020006"))
				.addRelationship(new Relationship("3332956025", null, true, "900000000000207008", "34020007",
						"#10.01", 1, "396070085", "900000000000011006", "900000000000451002")));
		Exception exception = assertThrows(IllegalStateException.class, () -> {
			simulateRF2Import(path, concepts);
		});
		assertEquals("No MRCM range constraint is defined for concrete attribute 396070085",
				exception.getMessage());
	}

	@Test
	void testRebuildSemanticIndexWithDecimalValueForIntType() throws ServiceException {
		String path = "MAIN";
		List<Concept> concepts = createConcreteValueTestConcepts(path);

		// 396070082 has data type defined as int in the MRCM
		concepts.add(new Concept("34020007").addRelationship(new Relationship(UUID.randomUUID().toString(), ISA, "34020006"))
				.addRelationship(new Relationship("3332956025", null, true, "900000000000207008", "34020007",
						"#10.01", 1, "396070082", "900000000000011006", "900000000000451002")));
		Exception exception = assertThrows(IllegalStateException.class, () -> {
			simulateRF2Import(path, concepts);
		});
		assertEquals("Concrete value 10.01 with data type DECIMAL in relationship is not matching data type INTEGER defined in the MRCM for attribute 396070082",
				exception.getMessage());
	}

	@Test
	void testRebuildSemanticIndexWithIntegerValueForDecimalDataType() throws ServiceException {
		String path = "MAIN";
		List<Concept> concepts = createConcreteValueTestConcepts(path);

		// 396070080 has data type defined as decimal in the MRCM
		concepts.add(new Concept("34020007").addRelationship(new Relationship(UUID.randomUUID().toString(), ISA, "34020006"))
				.addRelationship(new Relationship("3332956025", null, true, "900000000000207008", "34020007",
						"#50", 1, "396070080", "900000000000011006", "900000000000451002")));
		// no exception should be thrown
		simulateRF2Import(path, concepts);
		assertEquals(1, queryService.search(queryService.createQueryBuilder(false).ecl("<<34020007:* = #50"), path, QueryService.PAGE_OF_ONE).getTotalElements());

	}

	@Test
	void testRebuildSemanticIndexWithConcreteValues() throws ServiceException {
		String path = "MAIN";
		List<Concept> concepts = createConcreteValueTestConcepts(path);

		concepts.add(new Concept("34020007").addRelationship(new Relationship(UUID.randomUUID().toString(), ISA, "34020006"))
				.addRelationship(new Relationship("3332956025", null, true, "900000000000207008", "34020007", "#50", 1, "396070080", "900000000000011006", "900000000000451002"))
				.addRelationship(new Relationship("3332956026", null, true, "900000000000207008", "34020007", "#20", 1, "396070082", "900000000000011006", "900000000000451002"))
				.addRelationship(new Relationship("4332956027", null, true, "900000000000207008", "34020007", "\"123test\"", 1, "396070081", "900000000000011006", "900000000000451002"))
				.addRelationship(new Relationship("5963641028", null, true, "900000000000207008", "34020007", "396070080", 1, "363698007", "900000000000011006", "900000000000451002")));

		// add a value with decimal for 396070080 attribute
		concepts.add(new Concept("34020009").addRelationship(new Relationship(UUID.randomUUID().toString(), ISA, "34020006"))
				.addRelationship(new Relationship("3332955025", null, true, "900000000000207008",
						"34020009", "#100.000005", 1, "396070080", "900000000000011006", "900000000000451002")));
		// Use low level component save to prevent effectiveTimes being stripped by concept service
		simulateRF2Import(path, concepts);

		// wildcard query to test attr.all field in the semantic index
		assertEquals(1, queryService.search(queryService.createQueryBuilder(false).ecl("<<34020007:* = #50"), path, QueryService.PAGE_OF_ONE).getTotalElements());
		assertEquals(1, queryService.search(queryService.createQueryBuilder(false).ecl("<<34020007: [1..1] * = #50"), path, QueryService.PAGE_OF_ONE).getTotalElements());
		assertEquals(1, queryService.search(queryService.createQueryBuilder(false).ecl("<<34020007: [1..*] * = #50"), path, QueryService.PAGE_OF_ONE).getTotalElements());
		assertEquals(0, queryService.search(queryService.createQueryBuilder(false).ecl("<<34020007: [2..2] * = #50"), path, QueryService.PAGE_OF_ONE).getTotalElements());
		assertEquals(1, queryService.search(queryService.createQueryBuilder(false).ecl("<<34020007: { * = #50 }"), path, QueryService.PAGE_OF_ONE).getTotalElements());
		assertEquals(1, queryService.search(queryService.createQueryBuilder(false).ecl("<<34020007: [1..1] { * = #50 }"), path, QueryService.PAGE_OF_ONE).getTotalElements());
		assertEquals(1, queryService.search(queryService.createQueryBuilder(false).ecl("<<34020007: [1..2] { * = #50 }"), path, QueryService.PAGE_OF_ONE).getTotalElements());
		assertEquals(0, queryService.search(queryService.createQueryBuilder(false).ecl("<<34020007: [2..2] { * = #50 }"), path, QueryService.PAGE_OF_ONE).getTotalElements());
		assertEquals(0, queryService.search(queryService.createQueryBuilder(false).ecl("<<34020007: [2..*] { * = #50 }"), path, QueryService.PAGE_OF_ONE).getTotalElements());
		assertEquals(1, queryService.search(queryService.createQueryBuilder(false).ecl("<<34020007: { [1..1] * = #50 }"), path, QueryService.PAGE_OF_ONE).getTotalElements());
		assertEquals(1, queryService.search(queryService.createQueryBuilder(false).ecl("<<34020007: { * >= #50, * <= #20 }"), path, QueryService.PAGE_OF_ONE).getTotalElements());
		assertEquals(1, queryService.search(queryService.createQueryBuilder(false).ecl("<<34020007: { * >= #45, * <= #20 }"), path, QueryService.PAGE_OF_ONE).getTotalElements());
		assertEquals(0, queryService.search(queryService.createQueryBuilder(false).ecl("<<34020007: { * >= #51, * <= #20 }"), path, QueryService.PAGE_OF_ONE).getTotalElements());
		assertEquals(1, queryService.search(queryService.createQueryBuilder(false).ecl("<<34020007: { * <= #50, * <= #20 }"), path, QueryService.PAGE_OF_ONE).getTotalElements());
		assertEquals(0, queryService.search(queryService.createQueryBuilder(false).ecl("<<34020007: { * <= #19 }"), path, QueryService.PAGE_OF_ONE).getTotalElements());
		assertEquals(1, queryService.search(queryService.createQueryBuilder(false).ecl("<<34020007: * >= #6"), path, QueryService.PAGE_OF_ONE).getTotalElements());
		assertEquals(2, queryService.search(queryService.createQueryBuilder(false).ecl("*:396070080 >= #0"), path, QueryService.PAGE_OF_ONE).getTotalElements());
		assertEquals(1, queryService.search(queryService.createQueryBuilder(false).ecl("<<34020007:* = #50"), path, QueryService.PAGE_OF_ONE).getTotalElements());
		assertEquals(1, queryService.search(queryService.createQueryBuilder(false).ecl("<<34020007:* >= #6"), path, QueryService.PAGE_OF_ONE).getTotalElements());
		assertEquals(2, queryService.search(queryService.createQueryBuilder(false).ecl("*:396070080 = *"), path, QueryService.PAGE_OF_ONE).getTotalElements());
		assertEquals(1, queryService.search(queryService.createQueryBuilder(false).ecl("*:396070081 = *"), path, QueryService.PAGE_OF_ONE).getTotalElements());

		// string query
		assertEquals(1, queryService.search(queryService.createQueryBuilder(false).ecl("*:396070081 = \"123test\""), path, QueryService.PAGE_OF_ONE).getTotalElements());
		assertEquals(0, queryService.search(queryService.createQueryBuilder(false).ecl("*:396070081 = \"50\""), path, QueryService.PAGE_OF_ONE).getTotalElements());
		assertEquals(0, queryService.search(queryService.createQueryBuilder(false).ecl("*:396070081 = \"#50\""), path, QueryService.PAGE_OF_ONE).getTotalElements());

		// number query
		assertEquals(1, queryService.search(queryService.createQueryBuilder(false).ecl("*:396070080 = #100.000005"), path, QueryService.PAGE_OF_ONE).getTotalElements());
		assertEquals(1, queryService.search(queryService.createQueryBuilder(false).ecl("*:396070080 = #50"), path, QueryService.PAGE_OF_ONE).getTotalElements());
		assertEquals(0, queryService.search(queryService.createQueryBuilder(false).ecl("*:396070080 = #20"), path, QueryService.PAGE_OF_ONE).getTotalElements());
		assertEquals(1, queryService.search(queryService.createQueryBuilder(false).ecl("*:396070082 = #20"), path, QueryService.PAGE_OF_ONE).getTotalElements());

		// range queries
		assertEquals(2, queryService.search(queryService.createQueryBuilder(false).ecl("*:396070080 >= #50"), path, QueryService.PAGE_OF_ONE).getTotalElements());
		assertEquals(1, queryService.search(queryService.createQueryBuilder(false).ecl("*:396070080 > #50"), path, QueryService.PAGE_OF_ONE).getTotalElements());
		assertEquals(0, queryService.search(queryService.createQueryBuilder(false).ecl("*:396070080 < #50"), path, QueryService.PAGE_OF_ONE).getTotalElements());
		assertEquals(1, queryService.search(queryService.createQueryBuilder(false).ecl("*:396070080 <= #50"), path, QueryService.PAGE_OF_ONE).getTotalElements());

		// make sure range query is not done alphabetically but based on the number value
		assertEquals(2, queryService.search(queryService.createQueryBuilder(false).ecl("*:396070080 >= #6"), path, QueryService.PAGE_OF_ONE).getTotalElements());

		// Not equal to query
		assertEquals(0, queryService.search(queryService.createQueryBuilder(false).ecl("<<34020009:396070080 != #100.000005"), path, QueryService.PAGE_OF_ONE).getTotalElements());
		assertEquals(1, queryService.search(queryService.createQueryBuilder(false).ecl("*:396070080 != #50"), path, QueryService.PAGE_OF_ONE).getTotalElements());
		assertEquals(2, queryService.search(queryService.createQueryBuilder(false).ecl("*:396070080 != #10"), path, QueryService.PAGE_OF_ONE).getTotalElements());
		assertEquals(0, queryService.search(queryService.createQueryBuilder(false).ecl("*:396070081 != \"123test\""), path, QueryService.PAGE_OF_ONE).getTotalElements());
		assertEquals(1, queryService.search(queryService.createQueryBuilder(false).ecl("*:396070081 != \"test\""), path, QueryService.PAGE_OF_ONE).getTotalElements());

		// no results with dot notation or reverse flag for concrete domain attributes
		assertEquals(0, queryService.search(queryService.createQueryBuilder(false).ecl("<34020006 . 396070080"), path, QueryService.PAGE_OF_ONE).getTotalElements());
		assertEquals(0, queryService.search(queryService.createQueryBuilder(false).ecl("*: R 396070080 = <34020006"), path, QueryService.PAGE_OF_ONE).getTotalElements());

		// should work for non concrete domain attributes
		assertEquals(1, queryService.search(queryService.createQueryBuilder(false).ecl("<34020006 . 363698007"), path, QueryService.PAGE_OF_ONE).getTotalElements());
		assertEquals(1, queryService.search(queryService.createQueryBuilder(false).ecl("*: R 363698007 = <34020006"), path, QueryService.PAGE_OF_ONE).getTotalElements());
	}

	private ReferenceSetMember constructMrcmRange(String referencedComponentId, String rangeConstraint) {
		ReferenceSetMember rangeMember = new ReferenceSetMember("900000000000207008", REFSET_MRCM_ATTRIBUTE_RANGE_INTERNATIONAL, referencedComponentId);
		rangeMember.setAdditionalField("rangeConstraint", rangeConstraint);
		rangeMember.setAdditionalField("attributeRule", "");
		rangeMember.setAdditionalField("ruleStrengthId", "723597001");
		rangeMember.setAdditionalField("contentTypeId", "723596005");
		return rangeMember;
	}

	private void simulateRF2Import(String path, List<Concept> concepts) {
		try (Commit commit = branchService.openCommit(path)) {
			concepts.forEach(Concept::markChanged);
			conceptUpdateHelper.doSaveBatchConcepts(concepts, commit);

			Set<Relationship> relationships = concepts.stream().map(Concept::getRelationships).flatMap(Collection::stream).collect(Collectors.toSet());
			relationships.forEach(Relationship::markChanged);
			conceptUpdateHelper.doSaveBatchRelationships(relationships, commit);

			commit.markSuccessful();
		}
	}

	private void assertTC(Concept concept, Concept... ancestors) {
		assertTC(concept, "MAIN", ancestors);
	}

	private void assertTC(Concept concept, String branch, Concept... ancestors) {
		Set<Long> expectedAncestors = Arrays.stream(ancestors).map(Concept::getConceptIdAsLong).collect(Collectors.toSet());
		assertEquals(expectedAncestors, queryService.findAncestorIds(concept.getId(), branch, false));
	}

	private List<Concept> createConcreteValueTestConcepts(String branchPath) throws ServiceException {
		List<Concept> concepts = new ArrayList<>();
		concepts.add(new Concept(SNOMEDCT_ROOT));
		concepts.add(new Concept("116680003").addRelationship(new Relationship(ISA, SNOMEDCT_ROOT)));
		concepts.add(new Concept("396070080").addRelationship(new Relationship(ISA, SNOMEDCT_ROOT)));
		concepts.add(new Concept("396070081").addRelationship(new Relationship(ISA, SNOMEDCT_ROOT)));
		concepts.add(new Concept("396070082").addRelationship(new Relationship(ISA, SNOMEDCT_ROOT)));
		concepts.add(new Concept("363698007").addRelationship(new Relationship(ISA, SNOMEDCT_ROOT)));
		concepts.add(new Concept("34020006").addRelationship(new Relationship(ISA, SNOMEDCT_ROOT)));

		conceptService.batchCreate(concepts, branchPath);
		concepts.clear();

		Set<ReferenceSetMember> attributeRanges = new HashSet<>();
		attributeRanges.add(constructMrcmRange("396070080", "dec(>#0..)"));
		attributeRanges.add(constructMrcmRange("396070082", "int(>#0..#20)"));
		attributeRanges.add(constructMrcmRange("396070081", "str(\"test\")"));
		referenceSetMemberService.createMembers(branchPath, attributeRanges);
		return concepts;
	}

	@AfterEach
	void teardown() {
		conceptService.deleteAll();
	}
}
