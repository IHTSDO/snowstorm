package org.snomed.snowstorm.core.data.services;

import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.domain.Branch;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.core.data.domain.Concept;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

class SBranchServiceTest extends AbstractTest {

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

	@Test
	public void rollbackPartialCommit() throws ServiceException {
		String branch = "MAIN";
		String conceptId = "100001";
		conceptService.create(new Concept(conceptId, "10000111"), branch);
		Assertions.assertThrows(IllegalStateException.class, () -> sBranchService.rollbackPartialCommit(branch),
				"Throws illegal state because there is no partial commit on the branch");
	}
}