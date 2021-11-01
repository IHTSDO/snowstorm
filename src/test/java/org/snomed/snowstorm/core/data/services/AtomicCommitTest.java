package org.snomed.snowstorm.core.data.services;

import com.google.common.collect.Lists;
import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.domain.Branch;
import io.kaicode.elasticvc.domain.Commit;
import io.kaicode.elasticvc.domain.Metadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.TestConfig;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.data.services.transitiveclosure.GraphBuilderException;
import org.snomed.snowstorm.rest.pojo.MergeRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Map;
import java.util.Optional;

import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.snomed.snowstorm.core.data.domain.Concepts.*;

/**
 * This class checks that making updates within a commit happen atomically.
 * If several concepts are being changed at once and one fails the whole operation should not be persisted.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = TestConfig.class)
class AtomicCommitTest extends AbstractTest {

	@Autowired
	private BranchService branchService;

	@Autowired
	private BranchMetadataHelper branchMetadataHelper;

	@Autowired
	private ConceptService conceptService;

	@Autowired
	private CodeSystemService codeSystemService;

	@Autowired
	private BranchMergeService branchMergeService;

	@BeforeEach
	void setup() {
		branchService.create("MAIN/task");
	}

	@Test
	void testMultipleConceptCreationRollback() throws ServiceException {
		String branch = "MAIN/task";
		Branch branchBefore = branchService.findLatest(branch);
		assertEquals(Branch.BranchState.UP_TO_DATE, branchBefore.getState(), "Branch should be up to date before commit.");
		assertFalse(branchBefore.isLocked());
		long headTimestampBefore = branchBefore.getHeadTimestamp();

		assertNull(conceptService.find("1", branch), "Concept 1 should not exist before the attempted commit.");

		try {
			conceptService.batchCreate(Lists.newArrayList(
					new Concept("1").addDescription(new Description("one")),
					new Concept("2").addDescription(new Description("two")),
					new Concept("3").addDescription(new Description("three")).setInactivationIndicator("DOES_NOT_EXIST")
			), branch);
		} catch (IllegalArgumentException e) {
			// java.lang.IllegalArgumentException: Concept inactivation indicator not recognised 'DOES_NOT_EXIST'.
		}

		Branch branchAfter = branchService.findLatest(branch);
		// Branch should still be up to date after failed commit
		assertEquals(Branch.BranchState.UP_TO_DATE, branchAfter.getState());
		assertEquals(headTimestampBefore, branchAfter.getHeadTimestamp(), "Head timestamp should be the same before and after a failed commit.");
		assertFalse(branchAfter.isLocked(), "Branch should be unlocked as part of commit rollback.");

		assertNull(conceptService.find("1", branch), "Concept 1 should not exist after the attempted commit " +
				"because although there is nothing wrong with that concepts the whole commit should be rolled back.");
	}

	@Test
	void testRollbackOfComponentsAlreadyChangedOnSameBranch() throws ServiceException {
		// On MAIN
		Concept root = new Concept(SNOMEDCT_ROOT);
		// A -> root
		Concept cA = new Concept("1000011").addRelationship(new Relationship(ISA, SNOMEDCT_ROOT));
		// B -> root
		Concept cB = new Concept("1000012").addRelationship(new Relationship(ISA, SNOMEDCT_ROOT));
		// C -> B
		Concept cC = new Concept("1000013").addRelationship(new Relationship(ISA, cB.getId()));
		conceptService.batchCreate(Lists.newArrayList(root, cA, cB, cC), "MAIN");

		// On MAIN
		// B -> A
		cB.addRelationship(new Relationship(ISA, cA.getId()));
		conceptService.update(cB, "MAIN");

		// Version content
		codeSystemService.createCodeSystem(new CodeSystem("SNOMEDCT", "MAIN"));
		codeSystemService.createVersion(codeSystemService.find("SNOMEDCT"), 20200731, "A version");

		// On Branch
		String myBranch = "MAIN/my-branch";
		branchService.create(myBranch);

		// Inactivate B -> root relationship
		cB = conceptService.find(cB.getId(), myBranch);
		Optional<Relationship> bToRootRelationship = cB.getRelationships().stream().filter(r -> r.getDestinationId().equals(SNOMEDCT_ROOT)).findFirst();
		assertTrue(bToRootRelationship.isPresent());
		bToRootRelationship.get().setActive(false);
		conceptService.update(cB, myBranch);

		// Check B -> root relationship still has published flag
		cB = conceptService.find(cB.getId(), myBranch);
		bToRootRelationship = cB.getRelationships().stream().filter(r -> r.getDestinationId().equals(SNOMEDCT_ROOT)).findFirst();
		assertTrue(bToRootRelationship.isPresent());
		assertTrue(bToRootRelationship.get().isReleased());
		assertEquals(myBranch, bToRootRelationship.get().getPath());

		// Restore B -> root relationship
		// and create A -> C relationship which causes a transitive closure loop and triggers rollback
		cB = conceptService.find(cB.getId(), myBranch);
		bToRootRelationship = cB.getRelationships().stream().filter(r -> r.getDestinationId().equals(SNOMEDCT_ROOT)).findFirst();
		assertTrue(bToRootRelationship.isPresent());
		bToRootRelationship.get().setActive(true);
		cA.addRelationship(new Relationship(ISA, cC.getId()));
		try {
			conceptService.createUpdate(asList(cB, cA), myBranch);
			fail("Should have thrown IllegalStateException > GraphBuilderException");
		} catch (IllegalStateException e) {
			GraphBuilderException graphBuilderException = (GraphBuilderException) e.getCause();
			assertTrue(graphBuilderException.getMessage().startsWith("Loop found in transitive closure for concept 1000012 on branch MAIN/my-branch"));
		}

		// Check B -> root relationship still has published flag and is not ended
		cB = conceptService.find(cB.getId(), myBranch);
		bToRootRelationship = cB.getRelationships().stream().filter(r -> r.getDestinationId().equals(SNOMEDCT_ROOT)).findFirst();
		assertTrue(bToRootRelationship.isPresent());
		assertTrue(bToRootRelationship.get().isReleased());
		assertFalse(bToRootRelationship.get().isActive());
		assertNull(bToRootRelationship.get().getEnd());
	}

	@Test
	void testBranchLockMetadata() {
		try (Commit commit = branchService.openCommit("MAIN/task", branchMetadataHelper.getBranchLockMetadata("Testing lock metadata"))) {
			Metadata metadata = branchService.findLatest("MAIN/task").getMetadata();
			Map lockMetadata = metadata.getMap("lock");
			@SuppressWarnings("unchecked")
			Map<String, String> lockContext = (Map<String, String>) lockMetadata.get("context");
			final String description = lockContext.get("description");
			assertEquals("Testing lock metadata", description);
		}
	}

	@Test
	void testRollbackOfPromotion() throws ServiceException, InterruptedException {
		// Concept to save & modify
		String preferred = Concepts.descriptionAcceptabilityNames.get(Concepts.PREFERRED);
		Description fsn = new Description("Pizza (food)");
		fsn.setTypeId(FSN);
		fsn.setAcceptabilityMap(Map.of(Concepts.US_EN_LANG_REFSET, preferred, Concepts.GB_EN_LANG_REFSET, preferred));
		fsn.setCaseSignificance("CASE_INSENSITIVE");
		fsn.setLanguageCode("en");

		Description pt = new Description("Pizza");
		pt.setTypeId(SYNONYM);
		pt.setAcceptabilityMap(Map.of(Concepts.US_EN_LANG_REFSET, preferred, Concepts.GB_EN_LANG_REFSET, preferred));
		pt.setCaseSignificance("CASE_INSENSITIVE");
		pt.setLanguageCode("en");

		Concept pizza = new Concept().addDescription(fsn).addDescription(pt);

		// Create Pizza on MAIN
		Concept pizzaOnMain = conceptService.create(pizza, "MAIN");

		// Update Pizza on Project
		branchService.create("MAIN/projectA");
		pizzaOnMain.getDescriptions().iterator().next().setCaseSignificance("ENTIRE_TERM_CASE_SENSITIVE");
		Concept pizzaOnProject = conceptService.update(pizzaOnMain, "MAIN/projectA");

		// Promote Project to MAIN (it will fail as SAC not complete)
		givenSACIncomplete();
		promote("MAIN/projectA", "MAIN");

		// Assert state of Concept before second attempt of promotion
		Concept beforePromotion = conceptService.find(pizzaOnProject.getId(), "MAIN");
		assertEquals("Pizza (food)", beforePromotion.getFsn().getTerm());
		assertEquals("en", beforePromotion.getFsn().getLang());
		assertEquals("Pizza", beforePromotion.getPt().getTerm());
		assertEquals("en", beforePromotion.getPt().getLang());

		// Promote Project to MAIN again (it will succeed as SAC is now complete)
		givenSACComplete();
		promote("MAIN/projectA", "MAIN");

		// Assert state of Concept after second attempt of promotion
		Concept afterPromotion = conceptService.find(pizzaOnProject.getId(), "MAIN");
		assertEquals("Pizza (food)", afterPromotion.getFsn().getTerm());
		assertEquals("en", afterPromotion.getFsn().getLang());
		assertEquals("Pizza", afterPromotion.getPt().getTerm());
		assertEquals("en", afterPromotion.getPt().getLang());
	}

	private void givenSACComplete() {
		// When SAC is complete, nothing is returned/thrown.
		Mockito.doNothing().when(commitServiceHookClient).preCommitCompletion(any());
	}

	private void givenSACIncomplete() {
		// When SAC is incomplete, IllegalStateException is thrown.
		Mockito.doThrow(IllegalStateException.class).when(commitServiceHookClient).preCommitCompletion(any());
	}

	private void promote(String source, String target) throws InterruptedException {
		MergeRequest mergeRequest = new MergeRequest();
		mergeRequest.setSource(source);
		mergeRequest.setTarget(target);

		branchMergeService.mergeBranchAsync(mergeRequest);
		Thread.sleep(2000L);
	}
}
