package org.snomed.snowstorm.dailybuild;

import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.domain.Branch;
import org.ihtsdo.otf.resourcemanager.ResourceManager;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.snomed.otf.snomedboot.testutil.ZipUtil;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.TestConfig;
import org.snomed.snowstorm.core.data.domain.CodeSystem;
import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.core.data.services.CodeSystemService;
import org.snomed.snowstorm.core.data.services.ConceptService;
import org.snomed.snowstorm.core.rf2.RF2Type;
import org.snomed.snowstorm.core.rf2.rf2import.ImportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ResourceLoader;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.core.ElasticsearchTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import static junit.framework.TestCase.assertNull;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = TestConfig.class)
public class ScheduledDailyBuildImportServiceTest extends AbstractTest {
	@Autowired
	private ScheduledDailyBuildImportService dailyBuildImportService;

	@Autowired
	private BranchService branchService;

	@Autowired
	private ImportService importService;

	@Autowired
	private ConceptService conceptService;

	@Autowired
	private CodeSystemService codeSystemService;

	private File baseLineRelease;

	private File rf2Archive1;

	private File rf2Archive2;

	private CodeSystem snomedct;

	@Autowired
	private ElasticsearchTemplate elasticsearchTemplate;

	@Autowired
	private DailyBuildResourceConfig dailyBuildResourceConfig;

	@Autowired
	private ResourceLoader resourceLoader;

	@Before
	public void setUp() throws Exception {
		baseLineRelease = ZipUtil.zipDirectoryRemovingCommentsAndBlankLines("src/test/resources/dummy-snomed-content/SnomedCT_MiniRF2_Base_snapshot");
		snomedct = new CodeSystem("SNOMEDCT", "MAIN");
		codeSystemService.createCodeSystem(snomedct);
		String importId = importService.createJob(RF2Type.SNAPSHOT, snomedct.getBranchPath(), true);
		importService.importArchive(importId, new FileInputStream(baseLineRelease));
		rf2Archive1 = ZipUtil.zipDirectoryRemovingCommentsAndBlankLines("src/test/resources/dummy-daily-build/DailyBuild_Day1");
		rf2Archive2 = ZipUtil.zipDirectoryRemovingCommentsAndBlankLines("src/test/resources/dummy-daily-build/DailyBuild_Day2");
	}

	@Test
	public void testDailyBuildImport() throws Exception {
		String branchPath = snomedct.getBranchPath();
		Page<Branch> branchPage = branchService.findAllVersions(branchPath, Pageable.unpaged());
		assertEquals(3, branchPage.getTotalElements());

		// checking published concept
		Concept publishedConcept = conceptService.find("12481008", branchPath);
		assertNotNull(publishedConcept);
		assertTrue(publishedConcept.isActive());

		String publishedInternalId = publishedConcept.getInternalId();

		InputStream dailyBuildInputStream = new FileInputStream(rf2Archive1);
		// first RF2 Delta import
		String importId = importService.createJob(RF2Type.DELTA, branchPath, false);
		importService.importArchive(importId, dailyBuildInputStream);
		branchPage = branchService.findAllVersions("MAIN", Pageable.unpaged());
		assertEquals(4, branchPage.getTotalElements());

		// inactivation
		publishedConcept = conceptService.find("12481008", branchPath);
		assertNotNull(publishedConcept);
		assertTrue(!publishedConcept.isActive());

		String day1InternalId = publishedConcept.getInternalId();
		assertNotEquals(publishedInternalId, day1InternalId);

		// newly added
		Concept concept = conceptService.find("131148009", branchPath);
		assertNotNull(concept);
		assertEquals("131148009", concept.getConceptId());

		// scheduled daily build import with changes to revert previous day's authoring and add a brand new concept

		InputStream dailyBuildInputStream2 = new FileInputStream(rf2Archive2);
		dailyBuildImportService.dailyBuildDeltaImport(snomedct, dailyBuildInputStream2);
		Thread.sleep(2000);
		elasticsearchTemplate.refresh(Branch.class);
		branchPage = branchService.findAllVersions(branchPath, Pageable.unpaged());

		assertEquals(4, branchPage.getTotalElements());

		// published concept inactivation is reverted
		publishedConcept = conceptService.find("12481008", branchPath);
		assertNotNull(publishedConcept);
		assertTrue(publishedConcept.isActive());
		assertEquals(publishedInternalId, publishedConcept.getInternalId());
		// dropped
		concept = conceptService.find("131148010", branchPath);
		assertNotNull(concept);

		// newly added
		concept = conceptService.find("131148009", branchPath);
		assertNull(concept);

	}


	@Test
	public void testGetDailyBuildAvailable() throws Exception {
		Branch main = branchService.findLatest(snomedct.getBranchPath());
		ResourceManager resourceManager = new ResourceManager(dailyBuildResourceConfig, resourceLoader);
		Date now = new Date(main.getHeadTimestamp() + 1000);

		SimpleDateFormat formatter = new SimpleDateFormat(ScheduledDailyBuildImportService.DAILY_BUILD_DATE_FORMAT);
		String first = formatter.format(now);
		// TODO need to update the Resource manager
//		resourceManager.writeResource(snomedct.getShortName() + "/" + first + ".zip", new FileInputStream(rf2Archive1));

		assertNotNull(main);
		InputStream inputStream = dailyBuildImportService.getLatestDailyBuildIfExists(snomedct, main.getHeadTimestamp());
		assertNull(inputStream);
	}
}

