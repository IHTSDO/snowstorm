package org.snomed.snowstorm.core.data.services;

import io.kaicode.elasticvc.api.BranchService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.snomed.otf.snomedboot.testutil.ZipUtil;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.TestConfig;
import org.snomed.snowstorm.core.data.domain.CodeSystem;
import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.core.rf2.RF2Type;
import org.snomed.snowstorm.core.rf2.rf2import.ImportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.io.File;
import java.io.FileInputStream;

import static org.junit.Assert.*;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = TestConfig.class)
public class AdminOperationsServiceTest extends AbstractTest {
	@Autowired
	private AdminOperationsService operationsService;

	@Autowired
	private CodeSystemService codeSystemService;

	@Autowired
	private ImportService importService;

	@Autowired
	private ConceptService conceptService;

	@Autowired
	private BranchService branchService;

	@Test
	public void testPromoteReleaseFix() throws Exception {

		// Create international code system
		CodeSystem codeSystem = new CodeSystem("SNOMEDCT", "MAIN", "International Edition", "");
		codeSystemService.createCodeSystem(codeSystem);

		// Import dummy international content as base
		File snomedBase = ZipUtil.zipDirectoryRemovingCommentsAndBlankLines("src/test/resources/dummy-snomed-content/SnomedCT_MiniRF2_Base_snapshot");
		String importJob = importService.createJob(RF2Type.SNAPSHOT, "MAIN", true, false);
		importService.importArchive(importJob, new FileInputStream(snomedBase));

		long baseTotal = conceptService.findAll("MAIN", PageRequest.of(0, 1)).getTotalElements();
		assertEquals(17, baseTotal);

		// Import dummy international content delta for 20200131
		File snomedDelta = ZipUtil.zipDirectoryRemovingCommentsAndBlankLines("src/test/resources/dummy-snomed-content/SnomedCT_MiniRF2_donation_delta");
		String deltaImportJob = importService.createJob(RF2Type.DELTA, "MAIN", false, true);
		importService.importArchive(deltaImportJob, new FileInputStream(snomedDelta));

		// version MAIN to create release branch 201200131
		codeSystemService.createVersion(codeSystem, 20200131, "2020-01-31 International Release");
		assertNotNull(codeSystemService.findVersion("SNOMEDCT", 20200131));
		long releasedTotal = conceptService.findAll("MAIN/2020-01-31", PageRequest.of(0, 1)).getTotalElements();
		assertEquals(18, releasedTotal);

		// Delay before new authoring cycle import
		Thread.sleep(15_000L);

		// import new authoring cycle to MAIN
		File authoringDelta = ZipUtil.zipDirectoryRemovingCommentsAndBlankLines("src/test/resources/dummy-snomed-content/SnomedCT_MiniRF2_New_Authoring_cyle_delta");
		String authoringDeltaImportJob = importService.createJob(RF2Type.DELTA, "MAIN", false, true);
		importService.importArchive(authoringDeltaImportJob, new FileInputStream(authoringDelta));
		long totalIncludingNewAuthoring = conceptService.findAll("MAIN", PageRequest.of(0, 1)).getTotalElements();
		assertEquals(19, totalIncludingNewAuthoring);

		// import release fix into the release branch
		File releaseFixDelta = ZipUtil.zipDirectoryRemovingCommentsAndBlankLines("src/test/resources/dummy-snomed-content/SnomedCT_MiniRF2_Release_Fix_delta");
		String releaseFixDeltaImportJob = importService.createJob(RF2Type.DELTA, "MAIN/2020-01-31", false, false);
		importService.importArchive(releaseFixDeltaImportJob, new FileInputStream(releaseFixDelta));

		long releasedTotalAfterFix = conceptService.findAll("MAIN/2020-01-31", PageRequest.of(0, 1)).getTotalElements();
		assertEquals(19, releasedTotalAfterFix);

		// assert new added concept
		Concept conv19Concept = conceptService.find("840533007", "MAIN/2020-01-31");
		assertNotNull(conv19Concept);
		assertTrue(conv19Concept.isReleased());
		assertEquals("20200131", conv19Concept.getEffectiveTime());

		// import release fix into MAIN
		String importReleaseFixIntoMainJob = importService.createJob(RF2Type.DELTA, "MAIN", false, false);
		importService.importArchive(importReleaseFixIntoMainJob, new FileInputStream(releaseFixDelta));

		// assert data promoted to MAIN
		Concept cov19ConceptOnMain = conceptService.find("840533007", "MAIN");
		assertNotNull(cov19ConceptOnMain);
		assertTrue(cov19ConceptOnMain.isReleased());
		assertEquals("20200131", cov19ConceptOnMain.getEffectiveTime());

		// promote release fix to MAIN
		operationsService.promoteReleaseFix("MAIN/2020-01-31");

		// assert data promoted to MAIN
		cov19ConceptOnMain = conceptService.find("840533007", "MAIN");
		assertNotNull(cov19ConceptOnMain);
		assertTrue(cov19ConceptOnMain.isReleased());
		assertEquals("20200131", cov19ConceptOnMain.getEffectiveTime());

		// assert new authoring concept 131148009
		Concept conceptForNewCycle = conceptService.find("131148009", "MAIN");
		assertNotNull(conceptForNewCycle);
		assertTrue(!conceptForNewCycle.isReleased());
		assertNull(conceptForNewCycle.getEffectiveTime());
		long totalConceptsOnMain = conceptService.findAll("MAIN", PageRequest.of(0, 1)).getTotalElements();
		assertEquals(20, totalConceptsOnMain);

		// assert contents after promotion
		assertEquals(19, conceptService.findAll("MAIN/2020-01-31", PageRequest.of(0, 1)).getTotalElements());

		Concept cov19ConceptOnReleaseBranch = conceptService.find("840533007", "MAIN/2020-01-31");
		assertNotNull(cov19ConceptOnReleaseBranch);
		assertTrue(cov19ConceptOnReleaseBranch.isReleased());
		assertEquals("20200131", cov19ConceptOnReleaseBranch.getEffectiveTime());

		conceptForNewCycle = conceptService.find("131148009", "MAIN/2020-01-31");
		assertNull(conceptForNewCycle);
	}
}
