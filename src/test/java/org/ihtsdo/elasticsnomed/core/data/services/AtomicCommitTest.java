package org.ihtsdo.elasticsnomed.core.data.services;

import com.google.common.collect.Lists;
import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.domain.Branch;
import org.ihtsdo.elasticsnomed.TestConfig;
import org.ihtsdo.elasticsnomed.core.data.domain.Concept;
import org.ihtsdo.elasticsnomed.core.data.domain.Description;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

/**
 * This class checks that making updates within a commit happen atomically.
 * If several concepts are being changed at once and one fails the whole operation should not be persisted.
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = TestConfig.class)
public class AtomicCommitTest {

	@Autowired
	private BranchService branchService;

	@Autowired
	private ConceptService conceptService;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	@Before
	public void setup() {
		branchService.create("MAIN");
		branchService.create("MAIN/task");
	}

	@Test
	public void testMultipleConceptCreationRollback() throws ServiceException {
		String branch = "MAIN/task";
		Branch branchBefore = branchService.findLatest(branch);
		assertEquals("Branch should be up to date before commit.", Branch.BranchState.UP_TO_DATE, branchBefore.getState());
		assertFalse(branchBefore.isLocked());
		long headTimestampBefore = branchBefore.getHeadTimestamp();

		assertNull("Concept 1 should not exist before the attempted commit.", conceptService.find("1", branch));

		try {
			conceptService.create(Lists.newArrayList(
					new Concept("1").addDescription(new Description("one")),
					new Concept("2").addDescription(new Description("two")),
					new Concept("3").addDescription(new Description("three")).setInactivationIndicatorName("DOES_NOT_EXIST")
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

}
