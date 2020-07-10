package org.snomed.snowstorm.dailybuild;

import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.domain.Branch;
import org.ihtsdo.otf.resourcemanager.ResourceConfiguration;
import org.ihtsdo.otf.resourcemanager.ResourceManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.snomed.otf.snomedboot.testutil.ZipUtil;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.TestConfig;
import org.snomed.snowstorm.core.data.domain.CodeSystem;
import org.snomed.snowstorm.core.data.domain.CodeSystemVersion;
import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.core.data.services.AdminOperationsService;
import org.snomed.snowstorm.core.data.services.CodeSystemService;
import org.snomed.snowstorm.core.data.services.CodeSystemUpgradeService;
import org.snomed.snowstorm.core.data.services.ConceptService;
import org.snomed.snowstorm.core.rf2.RF2Type;
import org.snomed.snowstorm.core.rf2.rf2import.ImportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ResourceLoader;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.*;
import java.util.List;

import static junit.framework.TestCase.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = TestConfig.class)
class ScheduledDailyBuildImportServiceTest extends AbstractTest {

	@Autowired
	private DailyBuildService dailyBuildImportService;

	@Autowired
	private BranchService branchService;

	@Autowired
	private ImportService importService;

	@Autowired
	private ConceptService conceptService;

	@Autowired
	private CodeSystemService codeSystemService;

	@Autowired
	private CodeSystemUpgradeService codeSystemUpgradeService;

	@Autowired
	private AdminOperationsService adminOperationsService;

	private File baseLineRelease;

	private File rf2Archive1;

	private File rf2Archive2;

	private CodeSystem snomedct;

	@Autowired
	private ElasticsearchRestTemplate elasticsearchTemplate;

	@Autowired
	private ResourceLoader resourceLoader;

	@Autowired
	private DailyBuildResourceConfig dailyBuildResourceConfig;

	@BeforeEach
	void setUp() throws Exception {
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
	void testDailyBuildImport() throws Exception {
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
		assertFalse(publishedConcept.isActive());
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

	@Test
	void testExtensionDailyBuildImport() throws Exception {
		String shortName = "SNOMEDCT-LAND";
		String branchPath = "MAIN/SNOMEDCT-LAND";

		// Create dummy International version
		codeSystemService.createVersion(snomedct, 20190731, "");

		CodeSystem snomedExtensionCodeSystem = new CodeSystem(shortName, branchPath);
		snomedExtensionCodeSystem.setDailyBuildAvailable(true);
		codeSystemService.createCodeSystem(snomedExtensionCodeSystem);
		assertEquals(20190731, codeSystemService.find(shortName).getDependantVersionEffectiveTime().intValue());

		String importId = importService.createJob(RF2Type.SNAPSHOT, snomedExtensionCodeSystem.getBranchPath(), true, false);
		File extensionRelease = ZipUtil.zipDirectoryRemovingCommentsAndBlankLines("src/test/resources/dummy-snomed-content/SnomedCT_MiniRF2_Extension_snapshot");
		importService.importArchive(importId, new FileInputStream(extensionRelease));

		List<CodeSystemVersion> allVersions = codeSystemService.findAllVersions(shortName, false);
		assertEquals(1, allVersions.size());

		String dailyBuild1Concept = "131148009";
		String dailyBuild2Concept = "131148010";
		assertNull("Concept not yet imported", conceptService.find(dailyBuild1Concept, branchPath));
		assertNull("Concept not yet imported", conceptService.find(dailyBuild2Concept, branchPath));

		// Trigger #1 daily build import manually
		dailyBuildImportService.dailyBuildDeltaImport(snomedExtensionCodeSystem, rf2Archive1.getAbsolutePath());
		assertNotNull("Concept imported in daily build #1", conceptService.find(dailyBuild1Concept, branchPath));
		assertNull("Concept not yet imported", conceptService.find(dailyBuild2Concept, branchPath));

		// Trigger #2 daily build import manually
		dailyBuildImportService.dailyBuildDeltaImport(snomedExtensionCodeSystem, rf2Archive2.getAbsolutePath());
		assertNull("Daily build #1 import reverted and concept not present in daily build #2", conceptService.find(dailyBuild1Concept, branchPath));
		assertNotNull("Concept imported in daily build #2", conceptService.find(dailyBuild2Concept, branchPath));

		// Create fake international release
		codeSystemService.createVersion(snomedct, 20200131, "");

		// Upgrade the extension (rollback of previous daily build is automatic)
		codeSystemUpgradeService.upgrade(snomedExtensionCodeSystem, 20200131, false);
		assertEquals("Assert extension upgraded", 20200131, codeSystemService.find(shortName).getDependantVersionEffectiveTime().intValue());
		assertNull("Daily build 1 still not there.", conceptService.find(dailyBuild1Concept, branchPath));
		assertNull("Daily build 2 Concept should have been reverted as part of the upgrade.", conceptService.find(dailyBuild2Concept, branchPath));

		// Import daily build #1
		dailyBuildImportService.dailyBuildDeltaImport(snomedExtensionCodeSystem, rf2Archive1.getAbsolutePath());
		assertEquals("Assert extension is still upgraded", 20200131, codeSystemService.find(shortName).getDependantVersionEffectiveTime().intValue());
		assertNotNull("Daily build 1 Concept is now there.", conceptService.find(dailyBuild1Concept, branchPath));
		assertNull("Daily build 2 Concept should not be there yet.", conceptService.find(dailyBuild2Concept, branchPath));

		// Import daily build #2
		dailyBuildImportService.dailyBuildDeltaImport(snomedExtensionCodeSystem, rf2Archive2.getAbsolutePath());
		assertEquals("Assert extension is still upgraded", 20200131, codeSystemService.find(shortName).getDependantVersionEffectiveTime().intValue());
		assertNull("Daily build 1 Concept should now have been reverted.", conceptService.find(dailyBuild1Concept, branchPath));
		assertNotNull("Daily build 2 Concept should now be there.", conceptService.find(dailyBuild2Concept, branchPath));
	}

	private static class MockResourceManager extends ResourceManager {

		public MockResourceManager(ResourceConfiguration resourceConfiguration, ResourceLoader cloudResourceLoader) {
			super(resourceConfiguration, cloudResourceLoader);
		}

		public InputStream readResourceStream(String fullPath) throws IOException {
			fullPath = fullPath.substring(fullPath.indexOf("/"));
			System.out.println("Full path: '" + fullPath + "'");
			File file = new File(fullPath);
			if (file.exists() && file.canRead()) {
				return new FileInputStream(fullPath);
			} else {
				System.err.println("Returning null");
				return null;
			}
		}

		public InputStream readResourceStreamOrNullIfNotExists(String fullPath) throws IOException {
			return this.readResourceStream(fullPath);
		}
	}
}
