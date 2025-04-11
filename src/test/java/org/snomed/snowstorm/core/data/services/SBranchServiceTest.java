package org.snomed.snowstorm.core.data.services;

import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.domain.Branch;
import org.ihtsdo.otf.snomedboot.ReleaseImportException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.snomed.otf.snomedboot.testutil.ZipUtil;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.config.Config;
import org.snomed.snowstorm.core.data.domain.CodeSystem;
import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.core.data.domain.Concepts;
import org.snomed.snowstorm.core.rf2.RF2Type;
import org.snomed.snowstorm.core.rf2.rf2import.ImportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.snomed.snowstorm.core.data.domain.Concepts.*;
import static org.snomed.snowstorm.core.data.domain.Concepts.ICD11_MODULE;

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

	@Test
	void getModules_ShouldReturnExpected_WhenGivenNull() {
		// when
		Set<String> result = sBranchService.getModules(null);

		// then
		assertTrue(result.isEmpty());
	}

	@Test
	void getModules_ShouldReturnExpected_WhenGivenUnknown() {
		// when
		Set<String> result = sBranchService.getModules("i-dont-exist");

		// then
		assertTrue(result.isEmpty());
	}

	@Test
	void getModules_ShouldReturnExpected_WhenGivenInternational() {
		// given
		branchService.updateMetadata("MAIN", Map.of(Config.EXPECTED_EXTENSION_MODULES, List.of(CORE_MODULE, MODEL_MODULE, ICD10_MODULE, ICD11_MODULE)));
		Set<String> expectedModules = Set.of(Concepts.CORE_MODULE, Concepts.MODEL_MODULE, Concepts.ICD10_MODULE, Concepts.ICD11_MODULE);

		// when
		Set<String> result = sBranchService.getModules("MAIN");

		// then
		assertEquals(4, result.size());
		assertTrue(result.containsAll(expectedModules));
	}

	@Test
	void getModules_ShouldReturnExpected_WhenGivenExtension() {
		// given
		codeSystemService.createCodeSystem(new CodeSystem("SNOMEDCT-XX", "MAIN/SNOMEDCT-XX"));
		branchService.updateMetadata(
				"MAIN/SNOMEDCT-XX",
				Map.of(Config.EXPECTED_EXTENSION_MODULES, List.of("100011"),
						Config.DEPENDENCY_PACKAGE, "20250101.zip"
				)
		);

		// when
		Set<String> result = sBranchService.getModules("MAIN/SNOMEDCT-XX");

		// then
		assertEquals(1, result.size());
		assertEquals("100011", result.iterator().next());
	}
}