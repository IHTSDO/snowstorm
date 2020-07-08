package org.snomed.snowstorm.core.data.services;

import io.kaicode.elasticvc.api.BranchService;
import org.ihtsdo.otf.snomedboot.ReleaseImportException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.snomed.otf.snomedboot.testutil.ZipUtil;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.TestConfig;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.data.services.pojo.IntegrityIssueReport;
import org.snomed.snowstorm.core.rf2.RF2Type;
import org.snomed.snowstorm.core.rf2.rf2import.ImportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = TestConfig.class)
public class CodeSystemServiceIntegrationTest extends AbstractTest {

	@Autowired
	private CodeSystemService codeSystemService;

	@Autowired
	private CodeSystemUpgradeService codeSystemUpgradeService;

	@Autowired
	private BranchService branchService;

	@Autowired
	private ImportService importService;

	@Autowired
	private IntegrityService integrityService;

	@Autowired
	private ConceptService conceptService;

	@Test
	// We set up two versions of the international edition, 20180731 and 20190131, both on MAIN.
	// We import an extension directly under MAIN at the timepoint when 20180731 was created.
	// We then upgrade the extension to international version 20190131 which contains concept 18736003 that has been donated from the extension.
	// We check that the donated concept is in the correct module.
	public void testCodeSystemUpgradeProcessDonatedContent() throws IOException, ReleaseImportException, ServiceException {
		// Create international code system
		codeSystemService.createCodeSystem(new CodeSystem("SNOMEDCT", "MAIN", "International Edition", ""));

		// Import dummy international content for 20180731
		File snomedBase = ZipUtil.zipDirectoryRemovingCommentsAndBlankLines("src/test/resources/dummy-snomed-content/SnomedCT_MiniRF2_Base_snapshot");
		String importJob = importService.createJob(RF2Type.SNAPSHOT, "MAIN", true, false);
		importService.importArchive(importJob, new FileInputStream(snomedBase));
		assertNotNull(codeSystemService.findVersion("SNOMEDCT", 20180731));

		// Import dummy international content for 20190131
		File snomedDelta = ZipUtil.zipDirectoryRemovingCommentsAndBlankLines("src/test/resources/dummy-snomed-content/SnomedCT_MiniRF2_donation_delta");
		String deltaImportJob = importService.createJob(RF2Type.DELTA, "MAIN", true, false);
		importService.importArchive(deltaImportJob, new FileInputStream(snomedDelta));
		assertNotNull(codeSystemService.findVersion("SNOMEDCT", 20190131));

		// Create extension code system under 20180731 int version
		CodeSystem extensionCodeSystem = new CodeSystem("SNOMEDCT-BE", "MAIN/SNOMEDCT-BE", "Belgian Edition", "be");
		extensionCodeSystem.setDependantVersionEffectiveTime(20180731);
		codeSystemService.createCodeSystem(extensionCodeSystem);

		// Extension concept 18736003 should not exist on extension branch
		// because although it exists on the latest version of MAIN the extension is dependant on the older version
		assertNotNull(conceptService.find("18736003", "MAIN"));
		assertNull(conceptService.find("18736003", "MAIN/SNOMEDCT-BE"));

		// Import dummy extension content
		File snomedExtension = ZipUtil.zipDirectoryRemovingCommentsAndBlankLines("src/test/resources/dummy-snomed-content/SnomedCT_MiniRF2_Extension_snapshot");
		importJob = importService.createJob(RF2Type.SNAPSHOT, extensionCodeSystem.getBranchPath(), true, false);
		importService.importArchive(importJob, new FileInputStream(snomedExtension));

		IntegrityIssueReport componentsWithBadIntegrityOnExtension = integrityService.findAllComponentsWithBadIntegrity(
				branchService.findLatest(extensionCodeSystem.getBranchPath()), true);
		assertTrue("Integrity report should be empty: " + componentsWithBadIntegrityOnExtension.toString(), componentsWithBadIntegrityOnExtension.isEmpty());

		// Extension concept 18736003 should be in the extension module
		assertEquals("900101001", conceptService.find("18736003", "MAIN/SNOMEDCT-BE").getModuleId());

		// Upgrade the extension to international version 20190131
		codeSystemUpgradeService.upgrade(extensionCodeSystem, 20190131, true);

		extensionCodeSystem = codeSystemService.find("SNOMEDCT-BE");
		assertEquals("MAIN/SNOMEDCT-BE", extensionCodeSystem.getBranchPath());

		// Integrity check still be clean
		componentsWithBadIntegrityOnExtension = integrityService.findAllComponentsWithBadIntegrity(
				branchService.findLatest(extensionCodeSystem.getBranchPath()), true);
		assertTrue("Integrity report should be empty: " + componentsWithBadIntegrityOnExtension.toString(), componentsWithBadIntegrityOnExtension.isEmpty());

		// Extension concept 18736003 has now been donated so has the international module.
		assertEquals("900000000000207008", conceptService.find("18736003", "MAIN/SNOMEDCT-BE").getModuleId());

		// Test delete code system
		assertEquals(1, codeSystemService.findAllVersions("SNOMEDCT-BE", true).size());
		codeSystemService.deleteCodeSystemAndVersions(extensionCodeSystem);
		assertNull(codeSystemService.find("SNOMEDCT-BE"));
		assertEquals(0, codeSystemService.findAllVersions("SNOMEDCT-BE", true).size());
	}

	@Test
	// We set up content for the international edition and an extension.
	// We inactivate an international concept then see the extension break when it's upgraded.
	public void testCodeSystemUpgradeFindBrokenRelationships() throws IOException, ReleaseImportException, ServiceException {
		// Create international code system
		String snomedct = "SNOMEDCT";
		codeSystemService.createCodeSystem(new CodeSystem(snomedct, "MAIN", "International Edition", ""));

		// Import dummy international content
		File snomedBase = ZipUtil.zipDirectoryRemovingCommentsAndBlankLines("src/test/resources/dummy-snomed-content/SnomedCT_MiniRF2_Base_snapshot");
		String importJob = importService.createJob(RF2Type.SNAPSHOT, "MAIN", true, false);
		importService.importArchive(importJob, new FileInputStream(snomedBase));
		List<CodeSystemVersion> intVersions = codeSystemService.findAllVersions(snomedct, true);
		assertEquals(1, intVersions.size());
		assertEquals("MAIN/2018-07-31", intVersions.get(0).getBranchPath());
		assertEquals(20180731, intVersions.get(0).getEffectiveDate().intValue());

		// Check integrity of international dummy content
		IntegrityIssueReport componentsWithBadIntegrityOnMAIN = integrityService.findAllComponentsWithBadIntegrity(branchService.findLatest("MAIN"), true);
		assertTrue("Integrity report should be empty: " + componentsWithBadIntegrityOnMAIN.toString(), componentsWithBadIntegrityOnMAIN.isEmpty());


		// Create extension code system
		CodeSystem extensionCodeSystem = new CodeSystem("SNOMEDCT-BE", "MAIN/SNOMEDCT-BE", "Belgian Edition", "be");
		codeSystemService.createCodeSystem(extensionCodeSystem);
		assertEquals(20180731, codeSystemService.find(extensionCodeSystem.getShortName()).getDependantVersionEffectiveTime().intValue());

		// Import dummy extension content
		File snomedExtension = ZipUtil.zipDirectoryRemovingCommentsAndBlankLines("src/test/resources/dummy-snomed-content/SnomedCT_MiniRF2_Extension_snapshot");
		importJob = importService.createJob(RF2Type.SNAPSHOT, extensionCodeSystem.getBranchPath(), true, false);
		importService.importArchive(importJob, new FileInputStream(snomedExtension));

		IntegrityIssueReport componentsWithBadIntegrityOnExtension = integrityService.findAllComponentsWithBadIntegrity(
				branchService.findLatest(extensionCodeSystem.getBranchPath()), true);
		assertTrue("Integrity report should be empty: " + componentsWithBadIntegrityOnExtension.toString(), componentsWithBadIntegrityOnExtension.isEmpty());


		// Replace a concept in International
		// Create "Incision of ear (procedure)"
		Concept incisionOfEar = conceptService.create(new Concept().addRelationship(new Relationship(Concepts.ISA, Concepts.SNOMEDCT_ROOT)), "MAIN");

		// Make "Incision of middle ear (procedure)" inactive
		String incisionOfMiddleEarId = "12481008";
		Concept incisionOfMiddleEarConcept = conceptService.find(incisionOfMiddleEarId, "MAIN");
		incisionOfMiddleEarConcept.setActive(false);
		// Mark as outdated
		incisionOfMiddleEarConcept.setInactivationIndicator(Concepts.inactivationIndicatorNames.get(Concepts.OUTDATED));
		// Mark as possibly equivalent to "Incision of ear (procedure)"
		HashMap<String, Set<String>> associationTargets = new HashMap<>();
		associationTargets.put(Concepts.historicalAssociationNames.get(Concepts.REFSET_POSSIBLY_EQUIVALENT_TO_ASSOCIATION), Collections.singleton(incisionOfEar.getConceptId()));
		incisionOfMiddleEarConcept.setAssociationTargets(associationTargets);
		conceptService.update(incisionOfMiddleEarConcept, "MAIN");

		// Create a new version of International
		codeSystemService.createVersion(codeSystemService.find(snomedct), 20190131, "Dummy 2019-01-31 release.");

		// Upgrade the extension to the new international version
		assertEquals(20180731, codeSystemService.find(extensionCodeSystem.getShortName()).getDependantVersionEffectiveTime().intValue());
		codeSystemUpgradeService.upgrade(extensionCodeSystem, 20190131, true);
		assertEquals(20190131, codeSystemService.find(extensionCodeSystem.getShortName()).getDependantVersionEffectiveTime().intValue());

		extensionCodeSystem = codeSystemService.find("SNOMEDCT-BE");
		assertEquals("MAIN/SNOMEDCT-BE", extensionCodeSystem.getBranchPath());

		// Integrity check should fail because the extension is using a concept which has been made inactive
		componentsWithBadIntegrityOnExtension = integrityService.findAllComponentsWithBadIntegrity(
				branchService.findLatest(extensionCodeSystem.getBranchPath()), true);
		assertEquals("One of the extension relationships should be found to have an inactive destination concept.",
				1, componentsWithBadIntegrityOnExtension.getRelationshipsWithMissingOrInactiveDestination().size());
	}

	// NOTE - This is the old way of doing things using the deprecated migrateDependantCodeSystemVersion method.
	// Please use the upgrade method instead.
	// We set up content for the international edition and an extension.
	// We inactivate an international concept then see the extension break when it's upgraded.
	@Test
	public void createCodeSystemsWithContentTestingUpgrade() throws IOException, ReleaseImportException, ServiceException {
		// Create international code system
		codeSystemService.createCodeSystem(new CodeSystem("SNOMEDCT", "MAIN", "International Edition", ""));

		// Import dummy international content
		File snomedBase = ZipUtil.zipDirectoryRemovingCommentsAndBlankLines("src/test/resources/dummy-snomed-content/SnomedCT_MiniRF2_Base_snapshot");
		String importJob = importService.createJob(RF2Type.SNAPSHOT, "MAIN", true, false);
		importService.importArchive(importJob, new FileInputStream(snomedBase));

		// Check integrity of international dummy content
		IntegrityIssueReport componentsWithBadIntegrityOnMAIN = integrityService.findAllComponentsWithBadIntegrity(branchService.findLatest("MAIN"), true);
		assertTrue("Integrity report should be empty: " + componentsWithBadIntegrityOnMAIN.toString(), componentsWithBadIntegrityOnMAIN.isEmpty());


		// Create extension code system
		CodeSystem extensionCodeSystem = new CodeSystem("SNOMEDCT-BE", "MAIN/2018-07-31/SNOMEDCT-BE", "Belgian Edition", "be");
		codeSystemService.createCodeSystem(extensionCodeSystem);

		// Import dummy extension content
		File snomedExtension = ZipUtil.zipDirectoryRemovingCommentsAndBlankLines("src/test/resources/dummy-snomed-content/SnomedCT_MiniRF2_Extension_snapshot");
		importJob = importService.createJob(RF2Type.SNAPSHOT, extensionCodeSystem.getBranchPath(), true, false);
		importService.importArchive(importJob, new FileInputStream(snomedExtension));

		IntegrityIssueReport componentsWithBadIntegrityOnExtension = integrityService.findAllComponentsWithBadIntegrity(
				branchService.findLatest(extensionCodeSystem.getBranchPath()), true);
		assertTrue("Integrity report should be empty: " + componentsWithBadIntegrityOnExtension.toString(), componentsWithBadIntegrityOnExtension.isEmpty());


		// Replace a concept in International
		// Create "Incision of ear (procedure)"
		Concept incisionOfEar = conceptService.create(new Concept().addRelationship(new Relationship(Concepts.ISA, Concepts.SNOMEDCT_ROOT)), "MAIN");

		// Make "Incision of middle ear (procedure)" inactive
		String incisionOfMiddleEarId = "12481008";
		Concept incisionOfMiddleEarConcept = conceptService.find(incisionOfMiddleEarId, "MAIN");
		incisionOfMiddleEarConcept.setActive(false);
		// Mark as outdated
		incisionOfMiddleEarConcept.setInactivationIndicator(Concepts.inactivationIndicatorNames.get(Concepts.OUTDATED));
		// Mark as possibly equivalent to "Incision of ear (procedure)"
		HashMap<String, Set<String>> associationTargets = new HashMap<>();
		associationTargets.put(Concepts.historicalAssociationNames.get(Concepts.REFSET_POSSIBLY_EQUIVALENT_TO_ASSOCIATION), Collections.singleton(incisionOfEar.getConceptId()));
		incisionOfMiddleEarConcept.setAssociationTargets(associationTargets);
		conceptService.update(incisionOfMiddleEarConcept, "MAIN");

		// Create a new version of International
		codeSystemService.createVersion(codeSystemService.find("SNOMEDCT"), 20190131, "Dummy 2019-01-31 release.");

		// Upgrade the extension to the new international version
		codeSystemService.migrateDependantCodeSystemVersion(codeSystemService.find("SNOMEDCT-BE"), "SNOMEDCT", 20190131, true);

		extensionCodeSystem = codeSystemService.find("SNOMEDCT-BE");
		assertEquals("MAIN/2019-01-31/SNOMEDCT-BE", extensionCodeSystem.getBranchPath());

		// Integrity check should fail because the extension is using a concept which has been made inactive
		componentsWithBadIntegrityOnExtension = integrityService.findAllComponentsWithBadIntegrity(
				branchService.findLatest(extensionCodeSystem.getBranchPath()), true);
		assertEquals("One of the extension relationships should be found to have an inactive destination concept.",
				1, componentsWithBadIntegrityOnExtension.getRelationshipsWithMissingOrInactiveDestination().size());
	}
}
