package org.snomed.snowstorm.dailybuild;

import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.domain.Branch;
import org.ihtsdo.otf.resourcemanager.ResourceConfiguration;
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

import java.io.*;
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
	private ResourceLoader resourceLoader;

	@Autowired
	private DailyBuildResourceConfig dailyBuildResourceConfig;

	@Before
	public void setUp() throws Exception {
		baseLineRelease = ZipUtil.zipDirectoryRemovingCommentsAndBlankLines("src/test/resources/dummy-snomed-content/SnomedCT_MiniRF2_Base_snapshot");
		snomedct = new CodeSystem("SNOMEDCT", "MAIN");
		codeSystemService.createCodeSystem(snomedct);
		String importId = importService.createJob(RF2Type.SNAPSHOT, snomedct.getBranchPath(), true, false);
		importService.importArchive(importId, new FileInputStream(baseLineRelease));
		rf2Archive1 = ZipUtil.zipDirectoryRemovingCommentsAndBlankLines("src/test/resources/dummy-daily-build/DailyBuild_Day1");
		rf2Archive2 = ZipUtil.zipDirectoryRemovingCommentsAndBlankLines("src/test/resources/dummy-daily-build/DailyBuild_Day2");
		dailyBuildImportService.setResourceManager(new MockResourceManager(dailyBuildResourceConfig, resourceLoader));
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
		String importId = importService.createJob(RF2Type.DELTA, branchPath, false, true);
		importService.importArchive(importId, dailyBuildInputStream);
		branchPage = branchService.findAllVersions("MAIN", Pageable.unpaged());
		assertEquals(4, branchPage.getTotalElements());

		// inactivation
		publishedConcept = conceptService.find("12481008", branchPath);
		assertNotNull(publishedConcept);
		assertTrue(!publishedConcept.isActive());
		assertTrue(publishedConcept.isReleased());
		assertNotNull(publishedConcept.getReleasedEffectiveTime());
		assertNull(publishedConcept.getEffectiveTime());

		String day1InternalId = publishedConcept.getInternalId();
		assertNotEquals(publishedInternalId, day1InternalId);

		// newly added
		Concept concept = conceptService.find("131148009", branchPath);
		assertNotNull(concept);
		assertEquals("131148009", concept.getConceptId());
		assertNull(concept.getEffectiveTimeI());

		// scheduled daily build import with changes to revert previous day's authoring and add a brand new concept
		dailyBuildImportService.dailyBuildDeltaImport(snomedct, rf2Archive2.getAbsolutePath());
		Thread.sleep(2000);
		elasticsearchTemplate.refresh(Branch.class);
		branchPage = branchService.findAllVersions(branchPath, Pageable.unpaged());

		assertEquals(4, branchPage.getTotalElements());

		// published concept inactivation is reverted
		publishedConcept = conceptService.find("12481008", branchPath);
		assertNotNull(publishedConcept);
		assertTrue(publishedConcept.isActive());
		assertEquals(publishedInternalId, publishedConcept.getInternalId());
		assertTrue(publishedConcept.isReleased());
		assertNotNull(publishedConcept.getReleasedEffectiveTime());
		assertNotNull(publishedConcept.getEffectiveTime());
		assertEquals("20170131", publishedConcept.getEffectiveTime());

		// dropped
		concept = conceptService.find("131148010", branchPath);
		assertNotNull(concept);

		// newly added
		concept = conceptService.find("131148009", branchPath);
		assertNull(concept);

	}

	private static class MockResourceManager extends ResourceManager {

		public static final String SNOMEDCT = "SNOMEDCT";

		public MockResourceManager(ResourceConfiguration resourceConfiguration, ResourceLoader cloudResourceLoader) {
			super(resourceConfiguration, cloudResourceLoader);
		}

		public InputStream readResourceStream(String fullPath) throws IOException {
			if (fullPath.startsWith(SNOMEDCT)) {
				fullPath = fullPath.replace(SNOMEDCT, "");
			}
			File file = new File(fullPath);
			if (file.exists() && file.canRead()) {
				return new FileInputStream(fullPath);
			} else {
				return null;
			}
		}

		public InputStream readResourceStreamOrNullIfNotExists(String fullPath) throws IOException {
			return  this.readResourceStream(fullPath);
		}
	}
}

