package org.snomed.snowstorm.core.data.services;

import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.domain.Branch;
import org.ihtsdo.otf.snomedboot.ReleaseImportException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.snomed.otf.snomedboot.testutil.ZipUtil;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.core.data.domain.CodeSystem;
import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.core.rf2.RF2Type;
import org.snomed.snowstorm.core.rf2.rf2import.ImportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class SBranchServiceTest extends AbstractTest {

	@Autowired
	private ConceptService conceptService;

	@Autowired
	private SBranchService sBranchService;

	@Autowired
	private BranchService branchService;

	@Autowired
	private CodeSystemService codeSystemService;

	@Autowired
	private ImportService importService;

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
		assertNull(sBranchService.getPartialCommitTimestamp(branch), "Expect null because there is no partial commit on the branch.");
	}

	@Test
	public void rollbackPartialCommit() throws ServiceException {
		String branch = "MAIN";
		String conceptId = "100001";
		conceptService.create(new Concept(conceptId, "10000111"), branch);
		Assertions.assertThrows(IllegalStateException.class, () -> sBranchService.rollbackPartialCommit(branch),
				"Throws illegal state because there is no partial commit on the branch");
	}

	@Test
	public void rollbackCommitWithCodeSystemVersion() throws IOException, ReleaseImportException {
		// Create international code system
		codeSystemService.createCodeSystem(new CodeSystem("SNOMEDCT", "MAIN", "International Edition", ""));

		// Import dummy international content for 20180731
		File snomedBase = ZipUtil.zipDirectoryRemovingCommentsAndBlankLines("src/test/resources/dummy-snomed-content/SnomedCT_MiniRF2_Base_snapshot");
		String importJob = importService.createJob(RF2Type.SNAPSHOT, "MAIN", true, false);
		importService.importArchive(importJob, new FileInputStream(snomedBase));

		assertNotNull(codeSystemService.findVersion("SNOMEDCT", 20180731));
		assertNotNull(branchService.findLatest("MAIN/2018-07-31"));

		final Branch branch = branchService.findLatest("MAIN");
		sBranchService.rollbackCommit("MAIN", branch.getHead().getTime());

		assertNull(codeSystemService.findVersion("SNOMEDCT", 20180731));
		assertNull(branchService.findLatest("MAIN/2018-07-31"));
	}
}