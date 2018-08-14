package org.snomed.snowstorm.core.data.services;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.domain.Branch;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.TestConfig;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.rest.pojo.MergeRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = TestConfig.class)
public class BranchMergeServiceTest extends AbstractTest {

	@Autowired
	private BranchMergeService branchMergeService;

	@Autowired
	private BranchService branchService;

	@Autowired
	private ConceptService conceptService;

	@Autowired
	private QueryService queryService;

	@Before
	public void setup() {
		branchService.create("MAIN");
		branchService.create("MAIN/A");
		branchService.create("MAIN/A/A1");
		branchService.create("MAIN/A/A2");
		branchService.create("MAIN/C");
	}

	@Test
	public void testNewConceptWithoutComponentsRebaseAndPromotion() throws Exception {
		assertBranchState("MAIN", Branch.BranchState.UP_TO_DATE);
		assertBranchState("MAIN/A", Branch.BranchState.UP_TO_DATE);
		assertBranchState("MAIN/A/A1", Branch.BranchState.UP_TO_DATE);
		assertBranchState("MAIN/A/A2", Branch.BranchState.UP_TO_DATE);
		assertBranchState("MAIN/C", Branch.BranchState.UP_TO_DATE);

		// Create concept on A1
		final String conceptId = "123";
		conceptService.create(new Concept(conceptId), "MAIN/A/A1");
		assertBranchStateAndConceptVisibility("MAIN", Branch.BranchState.UP_TO_DATE, conceptId, false);
		assertBranchStateAndConceptVisibility("MAIN/A", Branch.BranchState.UP_TO_DATE, conceptId, false);
		assertBranchStateAndConceptVisibility("MAIN/A/A1", Branch.BranchState.FORWARD, conceptId, true);
		assertBranchStateAndConceptVisibility("MAIN/A/A2", Branch.BranchState.UP_TO_DATE, conceptId, false);
		assertBranchStateAndConceptVisibility("MAIN/C", Branch.BranchState.UP_TO_DATE, conceptId, false);

		// Promote to A
		branchMergeService.mergeBranchSync("MAIN/A/A1", "MAIN/A", null);
		assertBranchStateAndConceptVisibility("MAIN", Branch.BranchState.UP_TO_DATE, conceptId, false);
		assertBranchStateAndConceptVisibility("MAIN/A", Branch.BranchState.FORWARD, conceptId, true);
		assertBranchStateAndConceptVisibility("MAIN/A/A1", Branch.BranchState.UP_TO_DATE, conceptId, true);
		assertBranchStateAndConceptVisibility("MAIN/A/A2", Branch.BranchState.BEHIND, conceptId, false);
		assertBranchStateAndConceptVisibility("MAIN/C", Branch.BranchState.UP_TO_DATE, conceptId, false);

		// Bebase to A2
		branchMergeService.mergeBranchSync("MAIN/A", "MAIN/A/A2", null);
		assertBranchStateAndConceptVisibility("MAIN", Branch.BranchState.UP_TO_DATE, conceptId, false);
		assertBranchStateAndConceptVisibility("MAIN/A", Branch.BranchState.FORWARD, conceptId, true);
		assertBranchStateAndConceptVisibility("MAIN/A/A1", Branch.BranchState.UP_TO_DATE, conceptId, true);
		assertBranchStateAndConceptVisibility("MAIN/A/A2", Branch.BranchState.UP_TO_DATE, conceptId, true);
		assertBranchStateAndConceptVisibility("MAIN/C", Branch.BranchState.UP_TO_DATE, conceptId, false);

		// Promote to MAIN
		branchMergeService.mergeBranchSync("MAIN/A", "MAIN", null);
		assertBranchStateAndConceptVisibility("MAIN", Branch.BranchState.UP_TO_DATE, conceptId, true);
		assertBranchStateAndConceptVisibility("MAIN/A", Branch.BranchState.UP_TO_DATE, conceptId, true);
		assertBranchStateAndConceptVisibility("MAIN/A/A1", Branch.BranchState.BEHIND, conceptId, true);
		assertBranchStateAndConceptVisibility("MAIN/A/A2", Branch.BranchState.BEHIND, conceptId, true);
		assertBranchStateAndConceptVisibility("MAIN/C", Branch.BranchState.BEHIND, conceptId, false);

		// Delete from MAIN
		conceptService.deleteConceptAndComponents(conceptId, "MAIN", false);
		assertBranchStateAndConceptVisibility("MAIN", Branch.BranchState.UP_TO_DATE, conceptId, false);
		assertBranchStateAndConceptVisibility("MAIN/A", Branch.BranchState.BEHIND, conceptId, true);
		assertBranchStateAndConceptVisibility("MAIN/A/A1", Branch.BranchState.BEHIND, conceptId, true);
		assertBranchStateAndConceptVisibility("MAIN/A/A2", Branch.BranchState.BEHIND, conceptId, true);
		assertBranchStateAndConceptVisibility("MAIN/C", Branch.BranchState.BEHIND, conceptId, false);

		// Rebase to C
		branchMergeService.mergeBranchSync("MAIN", "MAIN/C", null);
		assertBranchStateAndConceptVisibility("MAIN", Branch.BranchState.UP_TO_DATE, conceptId, false);
		assertBranchStateAndConceptVisibility("MAIN/A", Branch.BranchState.BEHIND, conceptId, true);
		assertBranchStateAndConceptVisibility("MAIN/A/A1", Branch.BranchState.BEHIND, conceptId, true);
		assertBranchStateAndConceptVisibility("MAIN/A/A2", Branch.BranchState.BEHIND, conceptId, true);
		assertBranchStateAndConceptVisibility("MAIN/C", Branch.BranchState.UP_TO_DATE, conceptId, false);
	}

	@Test
	public void testPromotionOfConceptAndDescriptions() throws ServiceException {
		assertBranchState("MAIN", Branch.BranchState.UP_TO_DATE);
		assertBranchState("MAIN/A", Branch.BranchState.UP_TO_DATE);
		assertBranchState("MAIN/A/A1", Branch.BranchState.UP_TO_DATE);
		assertBranchState("MAIN/A/A2", Branch.BranchState.UP_TO_DATE);
		assertBranchState("MAIN/C", Branch.BranchState.UP_TO_DATE);

		// - Create a concepts on A1 and A2
		final String concept1 = "100";
		final String concept2 = "200";
		conceptService.create(new Concept(concept1)
						.addDescription(new Description("One")),
				"MAIN/A/A1");
		conceptService.create(new Concept(concept2)
						.addDescription(new Description("21", "Two1"))
						.addDescription(new Description("22", "Two2")),
				"MAIN/A/A2");

		// MAIN can't see concepts
		assertBranchStateAndConceptVisibility("MAIN", Branch.BranchState.UP_TO_DATE, concept1, false);
		assertConceptNotVisible("MAIN", concept2);

		// MAIN/A can't see concepts
		assertBranchStateAndConceptVisibility("MAIN/A", Branch.BranchState.UP_TO_DATE, concept1, false);
		assertConceptNotVisible("MAIN/A", concept2);

		// MAIN/A/A1 can see concept1 but not concept2
		Concept concept1OnA1 = assertBranchStateAndConceptVisibility("MAIN/A/A1", Branch.BranchState.FORWARD, concept1, true);
		assertEquals(1, concept1OnA1.getDescriptions().size());
		assertConceptNotVisible("MAIN/A/A1", concept2);

		// MAIN/A/A2 can see concept2 but not concept1
		final Concept concept2OnA2 = assertBranchStateAndConceptVisibility("MAIN/A/A2", Branch.BranchState.FORWARD, concept2, true);
		assertEquals(2, concept2OnA2.getDescriptions().size());
		assertConceptNotVisible("MAIN/A/A2", concept1);

		// MAIN/C can't see concepts
		assertBranchStateAndConceptVisibility("MAIN/C", Branch.BranchState.UP_TO_DATE, concept1, false);
		assertConceptNotVisible("MAIN/C", concept2);


		// - Promote A1 to A
		branchMergeService.mergeBranchSync("MAIN/A/A1", "MAIN/A", null);

		// MAIN can't see
		assertBranchStateAndConceptVisibility("MAIN", Branch.BranchState.UP_TO_DATE, concept1, false);

		// MAIN/A can see concept1 and descriptions but not concept2
		Concept concept1OnA = assertBranchStateAndConceptVisibility("MAIN/A", Branch.BranchState.FORWARD, concept1, true);
		assertEquals(1, concept1OnA.getDescriptions().size());
		assertConceptNotVisible("MAIN/A", concept2);

		// MAIN/A/A1 is up-to-date and can still see concept1 but not concept2
		concept1OnA1 = assertBranchStateAndConceptVisibility("MAIN/A/A1", Branch.BranchState.UP_TO_DATE, concept1, true);
		assertEquals(1, concept1OnA1.getDescriptions().size());
		assertConceptNotVisible("MAIN/A/A1", concept2);

		// MAIN/A/A2 is now diverged because there are changes on the branch and it's parent
		assertBranchStateAndConceptVisibility("MAIN/A/A2", Branch.BranchState.DIVERGED, concept2, true);

		// MAIN/C remains the same
		assertBranchState("MAIN/C", Branch.BranchState.UP_TO_DATE);


		// - Rebase A2
		final List<Concept> emptyListOfConflictMerges = Collections.emptyList();// Merge review will result in nothing to merge
		branchMergeService.mergeBranchSync("MAIN/A", "MAIN/A/A2", emptyListOfConflictMerges);

		// MAIN can't see
		assertBranchStateAndConceptVisibility("MAIN", Branch.BranchState.UP_TO_DATE, concept1, false);

		// MAIN/A can see concept1 and descriptions but not concept2
		concept1OnA = assertBranchStateAndConceptVisibility("MAIN/A", Branch.BranchState.FORWARD, concept1, true);
		assertEquals(1, concept1OnA.getDescriptions().size());
		assertConceptNotVisible("MAIN/A", concept2);

		// MAIN/A/A1 is up-to-date and can still see concept1 but not concept2
		assertBranchStateAndConceptVisibility("MAIN/A/A1", Branch.BranchState.UP_TO_DATE, concept1, true);
		assertConceptNotVisible("MAIN/A/A1", concept2);

		// MAIN/A/A2 is now forward because there are changes on the branch but now it's got it's parent's changes
		assertBranchStateAndConceptVisibility("MAIN/A/A2", Branch.BranchState.FORWARD, concept2, true);
		final Concept concept1OnA2 = assertConceptVisible("MAIN/A/A2", concept1);
		assertEquals(1, concept1OnA2.getDescriptions().size());

		// MAIN/C remains the same
		assertBranchState("MAIN/C", Branch.BranchState.UP_TO_DATE);


		// - Promote A to MAIN
		branchMergeService.mergeBranchSync("MAIN/A", "MAIN", null);

		// MAIN can see concept1! but not concept2 because it wasn't promoted to A before A was promoted
		assertBranchStateAndConceptVisibility("MAIN", Branch.BranchState.UP_TO_DATE, concept1, true);
		assertConceptNotVisible("MAIN", concept2);

		// MAIN/A is now up-to-date and can still see concept1 but not concept2
		assertBranchStateAndConceptVisibility("MAIN/A", Branch.BranchState.UP_TO_DATE, concept1, true);
		assertConceptNotVisible("MAIN/A", concept2);

		// MAIN/A/A1 is behind because there was a change on it's parent branch. That change was promotion.
		assertBranchStateAndConceptVisibility("MAIN/A/A1", Branch.BranchState.BEHIND, concept1, true);

		// MAIN/A/A2 is diverged because there was a change on A (promotion)
		assertBranchStateAndConceptVisibility("MAIN/A/A2", Branch.BranchState.DIVERGED, concept2, true);

		// MAIN/C is behind because MAIN has changes - concept1 just landed
		assertBranchState("MAIN/C", Branch.BranchState.BEHIND);


		// - Rebase A2 and promote to A
		branchMergeService.mergeBranchSync("MAIN/A", "MAIN/A/A2", emptyListOfConflictMerges);
		branchMergeService.mergeBranchSync("MAIN/A/A2", "MAIN/A", null);

		// MAIN can see concept1 but not concept2
		assertBranchStateAndConceptVisibility("MAIN", Branch.BranchState.UP_TO_DATE, concept1, true);
		assertConceptNotVisible("MAIN", concept2);

		// MAIN/A has changes again (concept2)
		assertBranchStateAndConceptVisibility("MAIN/A", Branch.BranchState.FORWARD, concept1, true);
		final Concept concept2OnA = assertConceptVisible("MAIN/A", concept2);
		assertEquals(2, concept2OnA.getDescriptions().size());

		// MAIN/A/A1 is still behind and can't see concept2
		assertBranchStateAndConceptVisibility("MAIN/A/A1", Branch.BranchState.BEHIND, concept1, true);
		assertConceptNotVisible("MAIN", concept2);

		assertBranchStateAndConceptVisibility("MAIN/A/A2", Branch.BranchState.UP_TO_DATE, concept2, true);

		assertBranchState("MAIN/C", Branch.BranchState.BEHIND);


		// - Promote A to MAIN
		branchMergeService.mergeBranchSync("MAIN/A", "MAIN", null);

		// MAIN can see concept1 and concept2
		assertBranchStateAndConceptVisibility("MAIN", Branch.BranchState.UP_TO_DATE, concept1, true);
		assertConceptVisible("MAIN", concept2);

		// MAIN/A is up to date
		assertBranchStateAndConceptVisibility("MAIN/A", Branch.BranchState.UP_TO_DATE, concept1, true);
		assertConceptVisible("MAIN/A", concept2);
	}

	@Test
	public void testRebaseCapturesChangesAcrossBranchesForTransitiveClosureIncrementalUpdate() throws ServiceException {
		assertBranchState("MAIN", Branch.BranchState.UP_TO_DATE);
		assertBranchState("MAIN/A", Branch.BranchState.UP_TO_DATE);
		assertBranchState("MAIN/A/A1", Branch.BranchState.UP_TO_DATE);

		// MAIN: 1 Root
		System.out.println("// MAIN: 1 Root");
		conceptService.create(new Concept("1"), "MAIN");

		// MAIN: 2 -> 1
		System.out.println("// MAIN: 2 -> 1");
		conceptService.create(new Concept("2").addRelationship(new Relationship(Concepts.ISA, "1")), "MAIN");

		// Rebase MAIN/A
		System.out.println("// Rebase MAIN/A");
		assertBranchState("MAIN/A", Branch.BranchState.BEHIND);
		branchMergeService.mergeBranchSync("MAIN", "MAIN/A", null);

		// MAIN: 4 -> 1
		System.out.println("// MAIN: 4 -> 1");
		conceptService.create(new Concept("4").addRelationship(new Relationship(Concepts.ISA, "1")), "MAIN");

		// MAIN: 2 -> 4
		System.out.println("// MAIN: 2 -> 4");
		conceptService.update(new Concept("2").addRelationship(new Relationship(Concepts.ISA, "4")), "MAIN");
		Assert.assertEquals(Sets.newHashSet(1L, 4L), queryService.retrieveAncestors("2", "MAIN", true));

		// MAIN/A: 3 -> 2
		System.out.println("// MAIN/A: 3 -> 2");
		conceptService.create(new Concept("3").addRelationship(new Relationship(Concepts.ISA, "2")), "MAIN/A");
		Assert.assertEquals(Sets.newHashSet(1L, 2L), queryService.retrieveAncestors("3", "MAIN/A", true));

		// Rebase MAIN/A
		System.out.println("// Rebase MAIN/A");
		assertBranchState("MAIN/A", Branch.BranchState.DIVERGED);

		branchMergeService.mergeBranchSync("MAIN", "MAIN/A", Collections.emptySet());

		Assert.assertEquals(Sets.newHashSet(1L, 4L), queryService.retrieveAncestors("2", "MAIN/A", true));
		Assert.assertEquals(Sets.newHashSet(1L, 4L, 2L), queryService.retrieveAncestors("3", "MAIN/A", true));
	}

/*
	// TODO: Disabled until production behaviour can be fixed.
	@Test(expected = IllegalArgumentException.class)
	public void testConflictWithoutManualMergeSupplied() throws ServiceException {
		final String concept1 = "100";
		final Concept concept = new Concept(concept1);
		conceptService.create(concept, "MAIN/A/A1");
		conceptService.create(concept, "MAIN/A");
		branchMergeService.mergeBranchSync("MAIN/A", "MAIN/A/A1", null);
	}
*/

	@Test
	public void testConflictConceptMergeChangesFromLeft() throws ServiceException {
		final String conceptId = "100";
		final Description description = new Description("One");

		setupConflictSituation(
				new Concept(conceptId, "9001").addDescription(description),
				new Concept(conceptId, "9002").addDescription(description),
				new Concept(conceptId, "9003").addDescription(description));

		// Rebase the diverged branch supplying the manually merged concept
		final Concept conceptFromLeft = new Concept(conceptId, "9002").addDescription(description);
		branchMergeService.mergeBranchSync("MAIN/A", "MAIN/A/A2", Collections.singleton(conceptFromLeft));

		assertEquals("9002", conceptService.find(conceptId, "MAIN/A/A2").getModuleId());

		// Promote the branch (no conflicts at this point)
		branchMergeService.mergeBranchSync("MAIN/A/A2", "MAIN/A", null);
		assertEquals("9002", conceptService.find(conceptId, "MAIN/A").getModuleId());
	}

	@Test
	public void testConflictConceptMergeChangesFromRight() throws ServiceException {
		final String conceptId = "100";
		final Description description = new Description("One");

		setupConflictSituation(
				new Concept(conceptId, "9001").addDescription(description),
				new Concept(conceptId, "9002").addDescription(description),
				new Concept(conceptId, "9003").addDescription(description));

		// Rebase the diverged branch supplying the manually merged concept
		final Concept conceptFromRight = new Concept(conceptId, "9003").addDescription(description);
		branchMergeService.mergeBranchSync("MAIN/A", "MAIN/A/A2", Collections.singleton(conceptFromRight));

		final String conceptFromMergedA2 = conceptService.find(conceptId, "MAIN/A/A2").getModuleId();
		Assert.assertEquals("9003", conceptFromMergedA2);

		// Promote the branch (no conflicts at this point)
		branchMergeService.mergeBranchSync("MAIN/A/A2", "MAIN/A", null);
		assertEquals("9003", conceptService.find(conceptId, "MAIN/A").getModuleId());
	}

	@Test
	public void testConflictConceptMergeChangesFromNowhere() throws ServiceException {
		final String conceptId = "100";
		final Description description = new Description("One");

		setupConflictSituation(
				new Concept(conceptId, "9001").addDescription(description),
				new Concept(conceptId, "9002").addDescription(description),
				new Concept(conceptId, "9003").addDescription(description));

		// Rebase the diverged branch supplying the manually merged concept
		final Concept conceptFromRight = new Concept(conceptId, "9099").addDescription(description);
		branchMergeService.mergeBranchSync("MAIN/A", "MAIN/A/A2", Collections.singleton(conceptFromRight));

		assertEquals("9099", conceptService.find(conceptId, "MAIN/A/A2").getModuleId());

		// Promote the branch (no conflicts at this point)
		branchMergeService.mergeBranchSync("MAIN/A/A2", "MAIN/A", null);
		assertEquals("9099", conceptService.find(conceptId, "MAIN/A").getModuleId());
	}

	@Test
	public void testConflictDescriptionsNewOnBothSides() throws ServiceException {
		final String conceptId = "100";

		setupConflictSituation(
				new Concept(conceptId, "9001").addDescription(new Description("101", "Orig")),

				new Concept(conceptId, "9002").addDescription(new Description("101", "Orig"))
						.addDescription(new Description("201", "New Left")),

				new Concept(conceptId, "9003").addDescription(new Description("101", "Orig"))
						.addDescription(new Description("301", "New Right")));

		// Rebase the diverged branch supplying the manually merged concept
		final Concept conceptMerge = new Concept(conceptId, "9099");
		conceptMerge.addDescription(new Description("101", "Orig"));
		conceptMerge.addDescription(new Description("201", "New Left"));
		conceptMerge.addDescription(new Description("301", "New Right"));
		branchMergeService.mergeBranchSync("MAIN/A", "MAIN/A/A2", Collections.singleton(conceptMerge));

		assertEquals(3, conceptService.find(conceptId, "MAIN/A/A2").getDescriptions().size());

		// Promote the branch (no conflicts at this point)
		branchMergeService.mergeBranchSync("MAIN/A/A2", "MAIN/A", null);
		assertEquals(3, conceptService.find(conceptId, "MAIN/A").getDescriptions().size());
	}

	@Test
	public void testConflictDescriptionsNewOnBothSidesAllDeletedInManualMerge() throws ServiceException {
		final String conceptId = "100";

		setupConflictSituation(
				new Concept(conceptId, "9001").addDescription(new Description("101", "Orig")),

				new Concept(conceptId, "9002").addDescription(new Description("101", "Orig"))
						.addDescription(new Description("201", "New Left")),

				new Concept(conceptId, "9003").addDescription(new Description("101", "Orig"))
						.addDescription(new Description("301", "New Right")));

		assertEquals(2, conceptService.find(conceptId, "MAIN/A").getDescriptions().size());
		assertEquals(2, conceptService.find(conceptId, "MAIN/A/A2").getDescriptions().size());

		// Rebase the diverged branch supplying the manually merged concept
		final Concept conceptMerge = new Concept(conceptId, "9099");
		branchMergeService.mergeBranchSync("MAIN/A", "MAIN/A/A2", Collections.singleton(conceptMerge));

		assertEquals(0, conceptService.find(conceptId, "MAIN/A/A2").getDescriptions().size());

		// Promote the branch (no conflicts at this point)
		branchMergeService.mergeBranchSync("MAIN/A/A2", "MAIN/A", null);
		assertEquals(0, conceptService.find(conceptId, "MAIN/A").getDescriptions().size());
	}

	@Test
	public void testConcurrentPromotionBlockedByBranchLock() throws ServiceException, InterruptedException {
		conceptService.create(new Concept("100").addDescription(new Description("1")), "MAIN/A");
		conceptService.create(new Concept("100").addDescription(new Description("2")), "MAIN/C");

		BranchMergeJob branchMergeJobA = branchMergeService.mergeBranchAsync(new MergeRequest("MAIN/A", "MAIN", "Promote A", null));
		BranchMergeJob branchMergeJobC = branchMergeService.mergeBranchAsync(new MergeRequest("MAIN/C", "MAIN", "Promote C", null));

		Thread.sleep(2000);

		List<BranchMergeJob> jobs = Lists.newArrayList(branchMergeJobA, branchMergeJobC);
		List<BranchMergeJob> completeJobs = jobs.stream().filter(job -> job.getStatus() == JobStatus.COMPLETED).collect(Collectors.toList());
		List<BranchMergeJob> failedJobs = jobs.stream().filter(job -> job.getStatus() == JobStatus.FAILED).collect(Collectors.toList());

		assertEquals(1, completeJobs.size());
		assertEquals(1, failedJobs.size());
		assertEquals("Branch MAIN is already locked", failedJobs.get(0).getMessage());
	}

	/**
	 * Set up a content conflict situation.
	 * Three versions of the same concept should be given.
	 * parentConcept is saved to branch MAIN/A and rebased to MAIN/A/A2
	 * leftConcept is then saved and promoted from MAIN/A/A1 to MAIN/A.
	 * rightConcept is then saved to MAIN/A/A2.
	 * At that point MAIN/A/A2 has a conflict in the rebase.
	 */
	private void setupConflictSituation(Concept parentConcept, Concept leftConcept, Concept rightConcept) throws ServiceException {
		assertBranchState("MAIN/A", Branch.BranchState.UP_TO_DATE);
		assertBranchState("MAIN/A/A1", Branch.BranchState.UP_TO_DATE);
		assertBranchState("MAIN/A/A2", Branch.BranchState.UP_TO_DATE);

		// - Create concept on A
		conceptService.create(parentConcept, "MAIN/A");

		// - Rebase A1 and A2
		branchMergeService.mergeBranchSync("MAIN/A", "MAIN/A/A1", null);
		branchMergeService.mergeBranchSync("MAIN/A", "MAIN/A/A2", null);

		// Update concept on A1 and promote
		conceptService.update(leftConcept, "MAIN/A/A1");
		branchMergeService.mergeBranchSync("MAIN/A/A1", "MAIN/A", null);

		// Update concept on A2
		conceptService.update(rightConcept, "MAIN/A/A2");
		assertBranchState("MAIN/A/A2", Branch.BranchState.DIVERGED);

		// Conflict setup complete - rebase A2 for conflict
	}

	private Concept assertBranchStateAndConceptVisibility(String path, Branch.BranchState expectedBranchState,
														  String conceptId, boolean expectedVisible) {
		assertBranchState(path, expectedBranchState);
		if (expectedVisible) {
			return assertConceptVisible(path, conceptId);
		} else {
			assertConceptNotVisible(path, conceptId);
		}
		return null;
	}

	private Concept assertConceptVisible(String path, String conceptId) {
		final Concept concept = conceptService.find(conceptId, path);
		assertEquals(conceptId, concept.getConceptId());
		return concept;
	}

	private void assertConceptNotVisible(String path, String conceptId) {
		Assert.assertNull(conceptService.find(conceptId, path));
	}

	private void assertBranchState(String path, Branch.BranchState expectedBranchState) {
		assertEquals(expectedBranchState, branchService.findLatest(path).getState());
	}

}
