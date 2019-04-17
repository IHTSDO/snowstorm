package org.snomed.snowstorm.core.data.services;

import io.kaicode.elasticvc.api.BranchService;
import org.ihtsdo.otf.snomedboot.ReleaseImportException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.snomed.otf.snomedboot.testutil.ZipUtil;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.TestConfig;
import org.snomed.snowstorm.core.data.domain.CodeSystem;
import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.core.data.domain.Concepts;
import org.snomed.snowstorm.core.data.domain.Relationship;
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
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = TestConfig.class)
public class CodeSystemIntegrationTest extends AbstractTest {

	@Autowired
	private CodeSystemService codeSystemService;

	@Autowired
	private BranchService branchService;

	@Autowired
	private ImportService importService;

	@Autowired
	private IntegrityService integrityService;

	@Autowired
	private ConceptService conceptService;

	@Before
	public void setup() {
		branchService.create("MAIN");
	}

	@Test
	// We set up content for the international edition and an extension.
	// We inactivate an international concept then see the extension break when it's upgraded.
	public void createCodeSystemsWithContentTestingUpgrade() throws IOException, ReleaseImportException, ServiceException {
		// Create international code system
		codeSystemService.createCodeSystem(new CodeSystem("SNOMEDCT", "MAIN"));

		// Import dummy international content
		File snomedBase = ZipUtil.zipDirectoryRemovingCommentsAndBlankLines("src/test/resources/dummy-snomed-content/SnomedCT_MiniRF2_Base_snapshot");
		String importJob = importService.createJob(RF2Type.SNAPSHOT, "MAIN", true);
		importService.importArchive(importJob, new FileInputStream(snomedBase));

		// Check integrity of international dummy content
		IntegrityIssueReport componentsWithBadIntegrityOnMAIN = integrityService.findAllComponentsWithBadIntegrity(branchService.findLatest("MAIN"), true);
		assertTrue("Integrity report should be empty: " + componentsWithBadIntegrityOnMAIN.toString(), componentsWithBadIntegrityOnMAIN.isEmpty());


		// Create extension code system
		CodeSystem extensionCodeSystem = new CodeSystem("SNOMEDCT-BE", "MAIN/2018-07-31/SNOMEDCT-BE");
		codeSystemService.createCodeSystem(extensionCodeSystem);

		// Import dummy extension content
		File snomedExtension = ZipUtil.zipDirectoryRemovingCommentsAndBlankLines("src/test/resources/dummy-snomed-content/SnomedCT_MiniRF2_Extension_snapshot");
		importJob = importService.createJob(RF2Type.SNAPSHOT, extensionCodeSystem.getBranchPath(), true);
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
