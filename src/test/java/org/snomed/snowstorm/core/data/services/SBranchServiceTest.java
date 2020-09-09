package org.snomed.snowstorm.core.data.services;

import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.domain.Branch;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.TestConfig;
import org.snomed.snowstorm.core.data.domain.Concept;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import static org.junit.Assert.*;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = TestConfig.class)
public class SBranchServiceTest extends AbstractTest {

	@Autowired
	private ConceptService conceptService;

	@Autowired
	private SBranchService sBranchService;

	@Autowired
	private BranchService branchService;

	@Test
	public void rollbackCommit() throws ServiceException {
		String branch = "MAIN";
		String conceptId = "100001";
		assertEquals(0, conceptService.findAll(branch, PageRequest.of(0, 100)).getTotalElements());
		conceptService.create(new Concept(conceptId, "10000111"), branch);
		assertEquals(1, conceptService.findAll(branch, PageRequest.of(0, 100)).getTotalElements());

		Branch latest = branchService.findLatest(branch);
		sBranchService.rollbackCommit(branch, latest.getHead().getTime());
		assertEquals(0, conceptService.findAll(branch, PageRequest.of(0, 100)).getTotalElements());
	}

	@Test
	public void getPartialCommitTimestamp() throws ServiceException {
		String branch = "MAIN";
		String conceptId = "100001";
		conceptService.create(new Concept(conceptId, "10000111"), branch);
		assertNull("Expect null because there is no partial commit on the branch.", sBranchService.getPartialCommitTimestamp(branch));
	}

	@Test(expected = IllegalStateException.class)
	public void rollbackPartialCommit() throws ServiceException {
		String branch = "MAIN";
		String conceptId = "100001";
		conceptService.create(new Concept(conceptId, "10000111"), branch);
		sBranchService.rollbackPartialCommit(branch);
		// Throws illegal state because there is no partial commit on the branch
	}

}