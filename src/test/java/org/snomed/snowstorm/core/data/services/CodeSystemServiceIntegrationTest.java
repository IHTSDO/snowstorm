package org.snomed.snowstorm.core.data.services;

import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.domain.Metadata;
import org.ihtsdo.otf.snomedboot.ReleaseImportException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.snomed.otf.snomedboot.testutil.ZipUtil;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.TestConfig;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.data.services.pojo.IntegrityIssueReport;
import org.snomed.snowstorm.core.rf2.RF2Type;
import org.snomed.snowstorm.core.rf2.rf2import.ImportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;

import static org.junit.Assert.*;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.snomed.snowstorm.core.data.domain.Concepts.*;
import static org.snomed.snowstorm.core.data.services.BranchMetadataKeys.DEPENDENCY_PACKAGE;
import static org.snomed.snowstorm.core.data.services.BranchMetadataKeys.DEPENDENCY_RELEASE;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = TestConfig.class)
class CodeSystemServiceIntegrationTest extends AbstractTest {

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

	private CodeSystem codeSystem;


	@BeforeEach
	void setup() {
		codeSystem = new CodeSystem("SNOMEDCT", "MAIN");
		codeSystemService.createCodeSystem(codeSystem);
	}

	@Test
	// We set up two versions of the international edition, 20180731 and 20190131, both on MAIN.
	// We import an extension directly under MAIN at the timepoint when 20180731 was created.
	// We then upgrade the extension to international version 20190131 which contains concept 18736003 that has been donated from the extension.
	// We check that the donated concept is in the correct module.
	void testCodeSystemUpgradeProcessDonatedContent() throws IOException, ReleaseImportException, ServiceException {
		// Import dummy international content for 20180731
		File snomedBase = ZipUtil.zipDirectoryRemovingCommentsAndBlankLines("src/test/resources/dummy-snomed-content/SnomedCT_MiniRF2_Base_snapshot");
		String importJob = importService.createJob(RF2Type.SNAPSHOT, "MAIN", true, false);
		importService.importArchive(importJob, new FileInputStream(snomedBase));
		assertNotNull(codeSystemService.findVersion(codeSystem.getShortName(), 20180731));

		// Import dummy international content for 20190131
		File snomedDelta = ZipUtil.zipDirectoryRemovingCommentsAndBlankLines("src/test/resources/dummy-snomed-content/SnomedCT_MiniRF2_donation_delta");
		String deltaImportJob = importService.createJob(RF2Type.DELTA, "MAIN", true, false);
		importService.importArchive(deltaImportJob, new FileInputStream(snomedDelta));
		assertNotNull(codeSystemService.findVersion(codeSystem.getShortName(), 20190131));

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
		codeSystemUpgradeService.upgrade(null, extensionCodeSystem, 20190131, true);

		extensionCodeSystem = codeSystemService.find("SNOMEDCT-BE");
		assertEquals("MAIN/SNOMEDCT-BE", extensionCodeSystem.getBranchPath());

		// Integrity check still be clean
		componentsWithBadIntegrityOnExtension = integrityService.findAllComponentsWithBadIntegrity(
				branchService.findLatest(extensionCodeSystem.getBranchPath()), true);
		assertTrue("Integrity report should be empty: " + componentsWithBadIntegrityOnExtension.toString(), componentsWithBadIntegrityOnExtension.isEmpty());

		// Extension concept 18736003 has now been donated so has the international module.
		assertEquals("900000000000207008", conceptService.find("18736003", "MAIN/SNOMEDCT-BE").getModuleId());

		// Test delete code system
		assertEquals(1, codeSystemService.findAllVersions("SNOMEDCT-BE", true, false).size());
		codeSystemService.deleteCodeSystemAndVersions(extensionCodeSystem, false);
		assertNull(codeSystemService.find("SNOMEDCT-BE"));
		assertEquals(0, codeSystemService.findAllVersions("SNOMEDCT-BE", true, false).size());
	}

	@Test
	// We set up content for the international edition and an extension.
	// We inactivate an international concept then see the extension break when it's upgraded.
		void testCodeSystemUpgradeFindBrokenRelationships() throws IOException, ReleaseImportException, ServiceException {
		// Import dummy international content
		File snomedBase = ZipUtil.zipDirectoryRemovingCommentsAndBlankLines("src/test/resources/dummy-snomed-content/SnomedCT_MiniRF2_Base_snapshot");
		String importJob = importService.createJob(RF2Type.SNAPSHOT, "MAIN", true, false);
		importService.importArchive(importJob, new FileInputStream(snomedBase));
		List<CodeSystemVersion> intVersions = codeSystemService.findAllVersions(codeSystem.getShortName(), true, false);
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
		codeSystemService.createVersion(codeSystemService.find(codeSystem.getShortName()), 20190131, "Dummy 2019-01-31 release.");

		// update with release package
		String releasePackage = "Test_20190131_Release_Snapshot.zip";
		CodeSystemVersion codeSystemVersion = codeSystemService.findVersion(codeSystem.getShortName(), 20190131);
		codeSystemVersion = codeSystemService.updateCodeSystemVersionPackage(codeSystemVersion, releasePackage);
		assertEquals(releasePackage, codeSystemVersion.getReleasePackage());

		// Upgrade the extension to the new international version
		assertEquals(20180731, codeSystemService.find(extensionCodeSystem.getShortName()).getDependantVersionEffectiveTime().intValue());
		codeSystemUpgradeService.upgrade(null, extensionCodeSystem, 20190131, true);
		assertEquals(20190131, codeSystemService.find(extensionCodeSystem.getShortName()).getDependantVersionEffectiveTime().intValue());

		// check branch metadata
		Metadata branchMetaData = branchService.findLatest(extensionCodeSystem.getBranchPath()).getMetadata();
		assertEquals("Dependant release version is updated", "20190131", branchMetaData.getString(DEPENDENCY_RELEASE));
		assertEquals("DependencyPackage is updated", releasePackage, branchMetaData.getString(DEPENDENCY_PACKAGE));


		extensionCodeSystem = codeSystemService.find("SNOMEDCT-BE");
		assertEquals("MAIN/SNOMEDCT-BE", extensionCodeSystem.getBranchPath());

		// Integrity check should fail because the extension is using a concept which has been made inactive
		componentsWithBadIntegrityOnExtension = integrityService.findAllComponentsWithBadIntegrity(
				branchService.findLatest(extensionCodeSystem.getBranchPath()), true);
		assertEquals("One of the extension relationships should be found to have an inactive destination concept.",
				1, componentsWithBadIntegrityOnExtension.getRelationshipsWithMissingOrInactiveDestination().size());
	}

	@Test
	void testExtensionRelationshipsNotBeingOverWrittenAfterUpgrade() throws Exception {
		// Create an active inferred relationship in the International code system
		Concept concept = conceptService.create(
				new Concept("100001")
						.addRelationship(new Relationship("100001", ISA, SNOMEDCT_ROOT))
						.addRelationship(new Relationship("100002", ISA, CLINICAL_FINDING))
				, "MAIN");
		concept = conceptService.find(concept.getConceptId(), "MAIN");
		assertNotNull(concept);
		Relationship relationship = concept.getRelationship("100002");
		assertNotNull(relationship);

		// Version the International code system
		codeSystemService.createVersion(codeSystem, 20200731, "International 20200731 Release");

		// Create a US code system depending on the latest International version
		CodeSystem usCodeSystem = new CodeSystem("SNOMEDCT-US", "MAIN/SNOMEDCT-US");
		codeSystemService.createCodeSystem(usCodeSystem);
		assertEquals(20200731, codeSystemService.find(usCodeSystem.getShortName()).getDependantVersionEffectiveTime().intValue());

		// Make the relationship inactive in the US
		concept = conceptService.find(concept.getConceptId(), usCodeSystem.getBranchPath());
		assertNotNull(concept);
		relationship = concept.getRelationship("100002");
		assertNotNull(relationship);
		relationship.setActive(false);
		relationship.setModuleId("731000124108");
		conceptService.update(concept, usCodeSystem.getBranchPath());

		// Version the US code system
		codeSystemService.createVersion(usCodeSystem, 20200901, "20200901 US Release");

		// Make the inferred relationship inactive in the International code system in the MAIN branch.
		concept = conceptService.find(concept.getConceptId(), "MAIN");
		relationship = concept.getRelationship("100002");
		assertTrue(relationship.isActive());
		relationship.setActive(false);
		conceptService.update(concept, "MAIN");

		// In another commit make it active again.
		// (This has the effect of making the relationship newer than the US version in version control
		// but it will not have a newer effectiveTime so it should not be used during upgrade - this is essentially the bug)
		relationship.setActive(true);
		conceptService.update(concept, "MAIN");
		concept = conceptService.find(concept.getConceptId(), "MAIN");
		relationship = concept.getRelationship("100002");
		assertTrue(relationship.isActive());
		assertEquals("20200731", relationship.getEffectiveTime());

		// Version the International code system
		codeSystemService.createVersion(codeSystem, 20210131, "International 20210131 Release");

		// check the inferred relationship before upgrade
		concept = conceptService.find(concept.getConceptId(), usCodeSystem.getBranchPath());
		relationship = concept.getRelationship("100002");
		assertFalse(relationship.isActive());
		assertEquals("20200901", relationship.getEffectiveTime());
		assertEquals("731000124108", relationship.getModuleId());

		// Upgrade the US code system
		codeSystemUpgradeService.upgrade(null, usCodeSystem, 20210131, true);

		// Assert that the inferred relationship in the US branch is still inactive and still has the US version effectiveTime.
		concept = conceptService.find(concept.getConceptId(), usCodeSystem.getBranchPath());
		relationship = concept.getRelationship("100002");
		assertFalse(relationship.isActive());
		assertEquals("20200901", relationship.getEffectiveTime());
		assertEquals("731000124108", relationship.getModuleId());
	}
}
