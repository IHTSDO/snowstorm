package org.snomed.snowstorm.core.data.services;

import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.api.VersionControlHelper;
import org.elasticsearch.search.sort.SortBuilders;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtendWith;
import org.snomed.otf.snomedboot.testutil.ZipUtil;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.TestConfig;
import org.snomed.snowstorm.core.data.domain.CodeSystem;
import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.core.data.domain.Description;
import org.snomed.snowstorm.core.rf2.RF2Type;
import org.snomed.snowstorm.core.rf2.rf2import.ImportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.File;
import java.io.FileInputStream;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.junit.Assert.*;

@ExtendWith(SpringExtension.class)
class AdminOperationsServiceTest extends AbstractTest {

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

	@Autowired
	private VersionControlHelper versionControlHelper;

	@Autowired
	private ElasticsearchOperations elasticsearchOperations;

	@Test
	void testPromoteReleaseFix() throws Exception {

		// Create international code system
		String mainBranch = "MAIN";
		CodeSystem codeSystem = new CodeSystem("SNOMEDCT", mainBranch, "International Edition", "");
		codeSystemService.createCodeSystem(codeSystem);

		// Import dummy international content as base
		File snomedBase = ZipUtil.zipDirectoryRemovingCommentsAndBlankLines("src/test/resources/dummy-snomed-content/SnomedCT_MiniRF2_Base_snapshot");
		String importJob = importService.createJob(RF2Type.SNAPSHOT, mainBranch, true, false);
		importService.importArchive(importJob, new FileInputStream(snomedBase));

		long baseTotal = conceptService.findAll(mainBranch, PageRequest.of(0, 1)).getTotalElements();
		assertEquals(17, baseTotal);

		// Import dummy international content delta for 20200131
		File snomedDelta = ZipUtil.zipDirectoryRemovingCommentsAndBlankLines("src/test/resources/dummy-snomed-content/SnomedCT_MiniRF2_donation_delta");
		String deltaImportJob = importService.createJob(RF2Type.DELTA, mainBranch, false, true);
		importService.importArchive(deltaImportJob, new FileInputStream(snomedDelta));

		// version MAIN to create release branch 201200131
		codeSystemService.createVersion(codeSystem, 20200131, "2020-01-31 International Release");
		assertNotNull(codeSystemService.findVersion("SNOMEDCT", 20200131));
		String releaseBranchPath = "MAIN/2020-01-31";
		long releasedTotal = conceptService.findAll(releaseBranchPath, PageRequest.of(0, 1)).getTotalElements();
		assertEquals(18, releasedTotal);

		// Delay before new authoring cycle import
		System.out.println("Wait 15 seconds ...");
		Thread.sleep(15_000L);

		// import new authoring cycle to MAIN
		File authoringDelta = ZipUtil.zipDirectoryRemovingCommentsAndBlankLines("src/test/resources/dummy-snomed-content/SnomedCT_MiniRF2_New_Authoring_cyle_delta");
		String authoringDeltaImportJob = importService.createJob(RF2Type.DELTA, mainBranch, false, true);
		importService.importArchive(authoringDeltaImportJob, new FileInputStream(authoringDelta));
		long totalIncludingNewAuthoring = conceptService.findAll(mainBranch, PageRequest.of(0, 1)).getTotalElements();
		assertEquals(19, totalIncludingNewAuthoring);

		// import release fix into the release branch
		File releaseFixDelta = ZipUtil.zipDirectoryRemovingCommentsAndBlankLines("src/test/resources/dummy-snomed-content/SnomedCT_MiniRF2_Release_Fix_delta");
		String releaseFixDeltaImportJob = importService.createJob(RF2Type.DELTA, releaseBranchPath, false, false);
		importService.importArchive(releaseFixDeltaImportJob, new FileInputStream(releaseFixDelta));

		assertEquals(19, conceptService.findAll(releaseBranchPath, PageRequest.of(0, 1)).getTotalElements());

		// Hard delete a concept in fix branch
		String conceptToDeleteAsFix = "281615006";
		assertNotNull(conceptService.find(conceptToDeleteAsFix, releaseBranchPath));
		conceptService.deleteConceptAndComponents(conceptToDeleteAsFix, releaseBranchPath, true);
		assertNull(conceptService.find(conceptToDeleteAsFix, releaseBranchPath));
		assertNotNull(conceptService.find(conceptToDeleteAsFix, mainBranch));
		assertEquals(18, conceptService.findAll(releaseBranchPath, PageRequest.of(0, 1)).getTotalElements());


		// assert new added concept
		Concept conv19Concept = conceptService.find("840533007", releaseBranchPath);
		assertNotNull(conv19Concept);
		assertFalse(conv19Concept.isReleased());
		assertNull(conv19Concept.getEffectiveTime());

		List<Description> incisionOfMiddleEarDescriptions = elasticsearchOperations.search(new NativeSearchQueryBuilder().withQuery(
				boolQuery()
						.must(versionControlHelper.getBranchCriteria(releaseBranchPath).getEntityBranchCriteria(Description.class))
						.must(termQuery(Description.Fields.DESCRIPTION_ID, "728558011"))).build(), Description.class)
				.stream().map(SearchHit::getContent).collect(Collectors.toList());
		assertEquals(1, incisionOfMiddleEarDescriptions.size());

		printAllVersionsOfConcept(conceptToDeleteAsFix, "before promotion");

		// promote release fix to MAIN
		operationsService.promoteReleaseFix(releaseBranchPath);

		printAllVersionsOfConcept(conceptToDeleteAsFix, "after promotion");

		// assert data promoted to MAIN and visible in release branch
		conv19Concept = conceptService.find("840533007", releaseBranchPath);
		assertNotNull(conv19Concept);
		assertTrue(conv19Concept.isReleased());
		assertEquals("20200131", conv19Concept.getEffectiveTime());
		assertEquals(mainBranch, conv19Concept.getPath());

		// Should not be in the head of MAIN because will have been reverted
		assertNull(conceptService.find("840533007", mainBranch));

		// assert new authoring concept 131148009
		Concept conceptForNewCycle = conceptService.find("131148009", mainBranch);
		assertNotNull(conceptForNewCycle);
		assertFalse(conceptForNewCycle.isReleased());
		assertNull(conceptForNewCycle.getEffectiveTime());
		assertNull("Concept deleted in fix branch should still be deleted in fix branch.",
				conceptService.find(conceptToDeleteAsFix, releaseBranchPath));

		long totalConceptsOnMain = conceptService.findAll(mainBranch, PageRequest.of(0, 1)).getTotalElements();
		assertEquals(19, totalConceptsOnMain);

		// assert contents after promotion
		assertEquals(18, conceptService.findAll(releaseBranchPath, PageRequest.of(0, 1)).getTotalElements());

		Concept cov19ConceptOnReleaseBranch = conceptService.find("840533007", releaseBranchPath);
		assertNotNull(cov19ConceptOnReleaseBranch);
		assertTrue(cov19ConceptOnReleaseBranch.isReleased());
		assertEquals("20200131", cov19ConceptOnReleaseBranch.getEffectiveTime());

		conceptForNewCycle = conceptService.find("131148009", releaseBranchPath);
		assertNull(conceptForNewCycle);

		incisionOfMiddleEarDescriptions = elasticsearchOperations.search(new NativeSearchQueryBuilder().withQuery(
				boolQuery()
						.must(versionControlHelper.getBranchCriteria(releaseBranchPath).getEntityBranchCriteria(Description.class))
						.must(termQuery(Description.Fields.DESCRIPTION_ID, "728558011"))).build(), Description.class)
				.stream().map(SearchHit::getContent).collect(Collectors.toList());
		assertEquals(1, incisionOfMiddleEarDescriptions.size());
	}

	@Test
	void testPromoteReleaseFix_deletionsOnly() throws Exception {
		// Create international code system
		String mainBranch = "MAIN";
		CodeSystem codeSystem = new CodeSystem("SNOMEDCT", mainBranch, "International Edition", "");
		codeSystemService.createCodeSystem(codeSystem);

		// Import dummy international content as base
		File snomedBase = ZipUtil.zipDirectoryRemovingCommentsAndBlankLines("src/test/resources/dummy-snomed-content/SnomedCT_MiniRF2_Base_snapshot");
		String importJob = importService.createJob(RF2Type.SNAPSHOT, mainBranch, true, false);
		importService.importArchive(importJob, new FileInputStream(snomedBase));

		long baseTotal = conceptService.findAll(mainBranch, PageRequest.of(0, 1)).getTotalElements();
		assertEquals(17, baseTotal);

		// version MAIN to create release branch 201200131
		codeSystemService.createVersion(codeSystem, 20200131, "2020-01-31 International Release");
		assertNotNull(codeSystemService.findVersion("SNOMEDCT", 20200131));
		String releaseBranchPath = "MAIN/2020-01-31";
		long releasedTotal = conceptService.findAll(releaseBranchPath, PageRequest.of(0, 1)).getTotalElements();
		assertEquals(17, releasedTotal);

		// Delay before new authoring cycle import
		System.out.println("Wait 15 seconds ...");
		Thread.sleep(15_000L);

		// import new authoring cycle to MAIN
		File authoringDelta = ZipUtil.zipDirectoryRemovingCommentsAndBlankLines("src/test/resources/dummy-snomed-content/SnomedCT_MiniRF2_New_Authoring_cyle_delta");
		String authoringDeltaImportJob = importService.createJob(RF2Type.DELTA, mainBranch, false, true);
		importService.importArchive(authoringDeltaImportJob, new FileInputStream(authoringDelta));
		long totalIncludingNewAuthoring = conceptService.findAll(mainBranch, PageRequest.of(0, 1)).getTotalElements();
		assertEquals(18, totalIncludingNewAuthoring);

		// import release fix into the release branch
		File releaseFixDelta = ZipUtil.zipDirectoryRemovingCommentsAndBlankLines("src/test/resources/dummy-snomed-content/SnomedCT_MiniRF2_Release_Fix_delta");
		String releaseFixDeltaImportJob = importService.createJob(RF2Type.DELTA, releaseBranchPath, false, false);
		importService.importArchive(releaseFixDeltaImportJob, new FileInputStream(releaseFixDelta));

		assertEquals(18, conceptService.findAll(releaseBranchPath, PageRequest.of(0, 1)).getTotalElements());

		// Hard delete a concept in fix branch
		String conceptToDeleteAsFix = "281615006";
		assertNotNull(conceptService.find(conceptToDeleteAsFix, releaseBranchPath));
		conceptService.deleteConceptAndComponents(conceptToDeleteAsFix, releaseBranchPath, true);
		assertNull(conceptService.find(conceptToDeleteAsFix, releaseBranchPath));
		assertNotNull(conceptService.find(conceptToDeleteAsFix, mainBranch));
		assertEquals(17, conceptService.findAll(releaseBranchPath, PageRequest.of(0, 1)).getTotalElements());

		printAllVersionsOfConcept(conceptToDeleteAsFix, "before promotion");

		// promote release fix to MAIN
		operationsService.promoteReleaseFix(releaseBranchPath);

		printAllVersionsOfConcept(conceptToDeleteAsFix, "after promotion");

		// assert data promoted to MAIN and visible in release branch

		// assert new authoring concept 131148009
		Concept conceptForNewCycle = conceptService.find("131148009", mainBranch);
		assertNotNull(conceptForNewCycle);
		assertFalse(conceptForNewCycle.isReleased());
		assertNull(conceptForNewCycle.getEffectiveTime());
		assertNull("Concept deleted in fix branch should still be deleted in fix branch.",
				conceptService.find(conceptToDeleteAsFix, releaseBranchPath));

		long totalConceptsOnMain = conceptService.findAll(mainBranch, PageRequest.of(0, 1)).getTotalElements();
		assertEquals(18, totalConceptsOnMain);

		// assert contents after promotion
		assertEquals(17, conceptService.findAll(releaseBranchPath, PageRequest.of(0, 1)).getTotalElements());
	}

	private void printAllVersionsOfConcept(String conceptId, String event) {
		System.out.println("All versions of concept " + conceptId + ", " + event);
		elasticsearchOperations.search(new NativeSearchQueryBuilder().withQuery(termQuery(Concept.Fields.CONCEPT_ID, conceptId)).withSort(SortBuilders.fieldSort("start")).build(), Concept.class)
				.forEach(hit -> {
					Concept c = hit.getContent();
					System.out.println(String.format("start:%s end:%s path:%s", getTimeLong(c.getStart()), getTimeLong(c.getEnd()), c.getPath()));
				});
		System.out.println("--");
	}

	private Long getTimeLong(Date time) {
		return time == null ? null : time.getTime();
	}
}
