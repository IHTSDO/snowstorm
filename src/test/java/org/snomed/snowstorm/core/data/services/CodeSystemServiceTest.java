package org.snomed.snowstorm.core.data.services;

import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.domain.Branch;
import io.kaicode.elasticvc.domain.Metadata;
import org.junit.jupiter.api.Test;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.core.data.domain.CodeSystem;
import org.snomed.snowstorm.core.data.domain.CodeSystemVersion;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Set;

import static java.time.Instant.now;
import static org.junit.jupiter.api.Assertions.*;

class CodeSystemServiceTest extends AbstractTest {

	@Autowired
	private CodeSystemService codeSystemService;

	@Autowired
	private BranchService branchService;

	@Autowired
	private  CodeSystemUpgradeService codeSystemUpgradeService;

	@Test
	void createCodeSystems() throws ServiceException {
		codeSystemService.createCodeSystem(new CodeSystem("SNOMEDCT", "MAIN").setOwner("SNOMED International"));

		assertEquals(1, codeSystemService.findAll().size());

		CodeSystem codeSystemBe = new CodeSystem("SNOMEDCT-BE", "MAIN/SNOMEDCT-BE");
		codeSystemService.createCodeSystem(codeSystemBe);

		assertEquals(2, codeSystemService.findAll().size());

		assertEquals(codeSystemBe, codeSystemService.find("SNOMEDCT-BE"));
	}

	@Test
	void createCodeSystemWithBadBranchPath() throws ServiceException {
		codeSystemService.createCodeSystem(new CodeSystem("SNOMEDCT", "MAIN"));
		assertEquals(1, codeSystemService.findAll().size());
		CodeSystem codeSystemBe = new CodeSystem("SNOMEDCT-TEST", "MAIN.TEST");
		assertThrows(IllegalArgumentException.class, () -> codeSystemService.createCodeSystem(codeSystemBe));
	}

	@Test
	void createPostcoordinatedCodeSystemInRoot() {
		CodeSystem codeSystemPCE = new CodeSystem("SNOMEDCT", "MAIN").setMaximumPostcoordinationLevel((short) 2);
		assertThrows(IllegalArgumentException.class, () -> codeSystemService.createCodeSystem(codeSystemPCE));
	}

	@Test
	void createPostcoordinatedCodeSystem() throws ServiceException {
		codeSystemService.createCodeSystem(new CodeSystem("SNOMEDCT", "MAIN"));
		CodeSystem codeSystemPCE = new CodeSystem("SNOMEDCT-PCETEST", "MAIN/PCETEST").setMaximumPostcoordinationLevel((short) 2);
		codeSystemService.createCodeSystem(codeSystemPCE);
	}

	@Test
	void testFindLatestImportedVersion() throws ServiceException {
		CodeSystem codeSystem = new CodeSystem("SNOMEDCT", "MAIN");
		codeSystemService.createCodeSystem(codeSystem);
		codeSystemService.createVersion(codeSystem, 20190731, "");

		// Now version it again with a later date, and recover the most recent one
		codeSystemService.createVersion(codeSystem, 20200131, "");
		assertEquals(20200131, codeSystemService.findLatestImportedVersion("SNOMEDCT").getEffectiveDate().intValue());

		// Versions in the future will be returned with this method.
		codeSystemService.createVersion(codeSystem, 20990131, "");
		assertEquals(20990131, codeSystemService.findLatestImportedVersion("SNOMEDCT").getEffectiveDate().intValue());

	}

	@Test
	void testFindInternalVersion() throws ServiceException {
		CodeSystem codeSystem = new CodeSystem("SNOMEDCT", "MAIN");
		codeSystemService.createCodeSystem(codeSystem);

		// Create internal release
		codeSystemService.createVersion(codeSystem, 20190731, "", true);

		assertEquals(20190731, codeSystemService.findLatestImportedVersion("SNOMEDCT").getEffectiveDate().intValue(),
				"Internal release listed as imported.");
		assertNull(codeSystemService.findLatestVisibleVersion("SNOMEDCT"),
				"Internal release not listed as visible, by default.");

		// Create release, not internal
		codeSystemService.createVersion(codeSystem, 20200131, "", false);
		assertEquals(20200131, codeSystemService.findLatestImportedVersion("SNOMEDCT").getEffectiveDate().intValue());
		assertEquals(20200131, codeSystemService.findLatestVisibleVersion("SNOMEDCT").getEffectiveDate().intValue());
	}

	@Test
	void testFindLatestEffectiveVersion() throws ServiceException {
		CodeSystem codeSystem = new CodeSystem("SNOMEDCT", "MAIN");
		codeSystemService.createCodeSystem(codeSystem);
		codeSystemService.createVersion(codeSystem, 20190131, "");

		codeSystemService.createVersion(codeSystem, 20190701, "");
		assertEquals(20190701, codeSystemService.findLatestVisibleVersion("SNOMEDCT").getEffectiveDate().intValue());

		// Versions in the future will NOT be returned with this method.
		codeSystemService.createVersion(codeSystem, 20990131, "");
		assertEquals(20190701, codeSystemService.findLatestVisibleVersion("SNOMEDCT").getEffectiveDate().intValue());

		codeSystemService.setLatestVersionCanBeFuture(true);
		assertEquals(20990131, codeSystemService.findLatestVisibleVersion("SNOMEDCT").getEffectiveDate().intValue());
		codeSystemService.setLatestVersionCanBeFuture(false);
	}

	@Test
	void testFindVersionsByCodeSystemAndBaseTimepointRange() throws ServiceException {
		CodeSystem codeSystem = codeSystemService.createCodeSystem(new CodeSystem("SNOMEDCT", "MAIN"));
		codeSystemService.createVersion(codeSystem, 20230101, "20230101 release");
		// Within time range
		Set<Branch> results = codeSystemService.findVersionsByCodeSystemAndBaseTimepointRange(codeSystem, now().minusMillis(1000l).toEpochMilli(), now().plusMillis(1000L).toEpochMilli());
		assertEquals(1, results.size());

		// Out of range
		results = codeSystemService.findVersionsByCodeSystemAndBaseTimepointRange(codeSystem, now().plusMillis(1000l).toEpochMilli(), now().plusMillis(2000L).toEpochMilli());
		assertEquals(0, results.size());

		// Time points out of order
		assertThrows(IllegalArgumentException.class, () -> codeSystemService.findVersionsByCodeSystemAndBaseTimepointRange(codeSystem, now().toEpochMilli(), now().minusMillis(2000L).toEpochMilli()));
	}

	@Test
	void testUpdateCodeSystemBranchMetadata() throws ServiceException {
		// Create a parent code system
		CodeSystem codeSystem = new CodeSystem("SNOMEDCT", "MAIN");
		codeSystemService.createCodeSystem(codeSystem);

		// Create a new parent code system version
		codeSystemService.createVersion(codeSystem, 20230131, "");
		CodeSystemVersion codeSystemVersion = codeSystemService.findVersion("MAIN/2023-01-31");
		codeSystemService.updateCodeSystemVersionPackage(codeSystemVersion, "SnomedCT_InternationalRF2_PRODUCTION_20230131T120000Z.zip");

		// Create an extension code system
		CodeSystem extensionCodeSystem = new CodeSystem("SNOMEDCT-TEST", "MAIN/SNOMEDCT-TEST");
		codeSystemService.createCodeSystem(extensionCodeSystem);

		// Create a new extension code system version
		codeSystemService.createVersion(extensionCodeSystem, 20230331, "");
		CodeSystemVersion extensionCodeSystemVersion = codeSystemService.findVersion("MAIN/SNOMEDCT-TEST/2023-03-31");
		codeSystemService.updateCodeSystemVersionPackage(extensionCodeSystemVersion, "SnomedCT_TEST_RF2_20230331T120000Z.zip");

		// Start a new authoring cycle for the parent code system branch
		codeSystemService.updateCodeSystemBranchMetadata(codeSystem);
		Metadata metadata = branchService.findLatest(codeSystem.getBranchPath()).getMetadata();
		// Assert that previous release data changed
		assertEquals("20230131", metadata.getString(BranchMetadataKeys.PREVIOUS_RELEASE));
		assertEquals("SnomedCT_InternationalRF2_PRODUCTION_20230131T120000Z.zip", metadata.getString(BranchMetadataKeys.PREVIOUS_PACKAGE));

		// Start a new authoring cycle for the extension code system branch
		codeSystemService.updateCodeSystemBranchMetadata(extensionCodeSystem);
		metadata = branchService.findLatest(extensionCodeSystem.getBranchPath()).getMetadata();
		// Assert that previous release data changed
		assertEquals("20230331", metadata.getString(BranchMetadataKeys.PREVIOUS_RELEASE));
		assertEquals("SnomedCT_TEST_RF2_20230331T120000Z.zip", metadata.getString(BranchMetadataKeys.PREVIOUS_PACKAGE));
		assertEquals("SnomedCT_InternationalRF2_PRODUCTION_20230131T120000Z.zip", metadata.getString(BranchMetadataKeys.PREVIOUS_DEPENDENCY_PACKAGE));

		// Create another parent code system version
		codeSystemService.createVersion(codeSystem, 20230331, "");
		codeSystemVersion = codeSystemService.findVersion("MAIN/2023-03-31");
		codeSystemService.updateCodeSystemVersionPackage(codeSystemVersion, "SnomedCT_InternationalRF2_PRODUCTION_20230331T120000Z.zip");

		// Upgrade extension code system branch to use that parent version
		codeSystemUpgradeService.upgrade(null, extensionCodeSystem, 20230331, true);
		metadata = branchService.findLatest(extensionCodeSystem.getBranchPath()).getMetadata();
		// Assert that dependency data changed
		assertEquals("20230331", metadata.getString(BranchMetadataKeys.DEPENDENCY_RELEASE));
		assertEquals("SnomedCT_InternationalRF2_PRODUCTION_20230331T120000Z.zip", metadata.getString(BranchMetadataKeys.DEPENDENCY_PACKAGE));
		// Assert that previous release data not changed
		assertEquals("20230331", metadata.getString(BranchMetadataKeys.PREVIOUS_RELEASE));
		assertEquals("SnomedCT_TEST_RF2_20230331T120000Z.zip", metadata.getString(BranchMetadataKeys.PREVIOUS_PACKAGE));
		assertEquals("SnomedCT_InternationalRF2_PRODUCTION_20230131T120000Z.zip", metadata.getString(BranchMetadataKeys.PREVIOUS_DEPENDENCY_PACKAGE));

		// Create another extension code system version
		codeSystemService.createVersion(extensionCodeSystem, 20230430, "");
		extensionCodeSystemVersion = codeSystemService.findVersion("MAIN/SNOMEDCT-TEST/2023-04-30");
		codeSystemService.updateCodeSystemVersionPackage(extensionCodeSystemVersion, "SnomedCT_TEST_RF2_20230430T120000Z.zip");

		// Start a new authoring cycle for the parent code system branch
		codeSystemService.updateCodeSystemBranchMetadata(codeSystem);
		metadata = branchService.findLatest(codeSystem.getBranchPath()).getMetadata();
		// Assert that previous release data changed
		assertEquals("20230331", metadata.getString(BranchMetadataKeys.PREVIOUS_RELEASE));
		assertEquals("SnomedCT_InternationalRF2_PRODUCTION_20230331T120000Z.zip", metadata.getString(BranchMetadataKeys.PREVIOUS_PACKAGE));

		// Start a new authoring cycle for the extension code system branch
		codeSystemService.updateCodeSystemBranchMetadata(extensionCodeSystem);
		metadata = branchService.findLatest(extensionCodeSystem.getBranchPath()).getMetadata();
		// Assert that previous release data changed
		assertEquals("20230430", metadata.getString(BranchMetadataKeys.PREVIOUS_RELEASE));
		assertEquals("SnomedCT_TEST_RF2_20230430T120000Z.zip", metadata.getString(BranchMetadataKeys.PREVIOUS_PACKAGE));
		assertEquals("SnomedCT_InternationalRF2_PRODUCTION_20230131T120000Z.zip", metadata.getString(BranchMetadataKeys.PREVIOUS_DEPENDENCY_PACKAGE));
		// Assert that dependency data not changed
		assertEquals("20230331", metadata.getString(BranchMetadataKeys.DEPENDENCY_RELEASE));
		assertEquals("SnomedCT_InternationalRF2_PRODUCTION_20230331T120000Z.zip", metadata.getString(BranchMetadataKeys.DEPENDENCY_PACKAGE));
	}

}
