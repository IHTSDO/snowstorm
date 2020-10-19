package org.snomed.snowstorm.core.data.services;

import com.google.common.collect.Lists;
import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.domain.Branch;
import io.kaicode.elasticvc.domain.Commit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.TestConfig;
import org.snomed.snowstorm.core.data.domain.CodeSystem;
import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.core.data.domain.Description;
import org.snomed.snowstorm.core.data.domain.Relationship;
import org.snomed.snowstorm.core.data.services.transitiveclosure.GraphBuilderException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Map;
import java.util.Optional;

import static java.util.Arrays.asList;
import static org.junit.Assert.*;
import static org.snomed.snowstorm.core.data.domain.Concepts.ISA;
import static org.snomed.snowstorm.core.data.domain.Concepts.SNOMEDCT_ROOT;

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

	@BeforeEach
	void setup() {
		branchService.create("MAIN/task");
	}

	@Test
	void testMultipleConceptCreationRollback() throws ServiceException {
		String branch = "MAIN/task";
		Branch branchBefore = branchService.findLatest(branch);
		assertEquals("Branch should be up to date before commit.", Branch.BranchState.UP_TO_DATE, branchBefore.getState());
		assertFalse(branchBefore.isLocked());
		long headTimestampBefore = branchBefore.getHeadTimestamp();

		assertNull("Concept 1 should not exist before the attempted commit.", conceptService.find("1", branch));

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
		assertEquals("Head timestamp should be the same before and after a failed commit.", headTimestampBefore, branchAfter.getHeadTimestamp());
		assertFalse("Branch should be unlocked as part of commit rollback.", branchAfter.isLocked());

		assertNull("Concept 1 should not exist after the attempted commit " +
				"because although there is nothing wrong with that concepts the whole commit should be rolled back.", conceptService.find("1", branch));
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
			assertEquals("Loop found in transitive closure for concept 1000013 on branch MAIN/my-branch. " +
					"The concept 1000013 is in its own set of ancestors: [1000012, 1000011, 1000013, 138875005]", graphBuilderException.getMessage());
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
	@SuppressWarnings("unchecked")
	void testBranchLockMetadata() {
		try (Commit commit = branchService.openCommit("MAIN/task", branchMetadataHelper.getBranchLockMetadata("Testing lock metadata"))) {
			Branch branch = branchService.findLatest("MAIN/task");
			Map<String, Object> metadata = branchMetadataHelper.expandObjectValues(branch.getMetadata());
			Map<String, Object> lockMetadata = (Map<String, Object>) metadata.get("lock");
			Map<String, Object> lockMetadataContext = (Map<String, Object>) lockMetadata.get("context");
			assertEquals("Testing lock metadata", lockMetadataContext.get("description"));
		}
	}

}
