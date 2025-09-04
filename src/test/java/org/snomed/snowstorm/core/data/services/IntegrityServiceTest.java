package org.snomed.snowstorm.core.data.services;

import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.domain.Branch;
import io.kaicode.elasticvc.domain.Commit;
import io.kaicode.elasticvc.domain.Metadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.TestConfig;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.data.services.classification.BranchClassificationStatusService;
import org.snomed.snowstorm.core.data.services.pojo.IntegrityIssueReport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.snomed.snowstorm.core.data.domain.Concepts.CORE_MODULE;
import static org.snomed.snowstorm.core.data.domain.Concepts.ISA;
import static org.snomed.snowstorm.core.data.services.BranchMetadataHelper.INTERNAL_METADATA_KEY;
import static org.snomed.snowstorm.core.data.services.IntegrityService.INTEGRITY_ISSUE_METADATA_KEY;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = TestConfig.class)
class IntegrityServiceTest extends AbstractTest {

	@Autowired
	private BranchService branchService;

	@Autowired
	private SBranchService sBranchService;

	@Autowired
	private BranchMergeService branchMergeService;

	@Autowired
	private ConceptService conceptService;

	@Autowired
	private ConceptUpdateHelper conceptUpdateHelper;

	@Autowired
	private IntegrityService integrityService;

	@Autowired
	private RelationshipService relationshipService;

	@Autowired
	private CodeSystemService codeSystemService;

	@Autowired
	private CodeSystemUpgradeService codeSystemUpgradeService;
    @Autowired
    private ReferenceSetMemberService referenceSetMemberService;

	@Test
	/*
		Test the method that checks all the components visible on the branch.
	 */
	void testFindAllComponentsWithBadIntegrity() throws ServiceException {
		sBranchService.create("MAIN/PROJECT");

		sBranchService.create("MAIN/PROJECT/TEST1");

		conceptService.create(new Concept("100001"), "MAIN/PROJECT");
		// Valid relationship on MAIN/project
		conceptService.create(new Concept("10000101").addRelationship(new Relationship("10000101", "100001").setInferred(false)), "MAIN/PROJECT");
		// Valid relationship on MAIN/project
		conceptService.create(new Concept("100002").addRelationship(new Relationship("10000101", "100001").setInferred(false)), "MAIN/PROJECT");
		// Missing Type on MAIN/project
		conceptService.create(new Concept("100003").addRelationship(new Relationship("10000102", "100002").setInferred(false)), "MAIN/PROJECT");
		// Missing Destination on MAIN/project
		conceptService.create(new Concept("100004").addRelationship(new Relationship("10000101", "100001000").setInferred(false)), "MAIN/PROJECT");
		// Valid relationship on MAIN/project
		conceptService.create(new Concept("100005").addRelationship(new Relationship("10000101", "100002").setInferred(false)), "MAIN/PROJECT");

		branchService.create("MAIN/PROJECT/TEST2");
		// Valid relationship on MAIN/project/test2
		conceptService.create(new Concept("100006").addRelationship(new Relationship("10000101", "100005").setInferred(false)), "MAIN/PROJECT/TEST2");
		// Missing Destination on MAIN/project/test2
		conceptService.create(new Concept("100007").addRelationship(new Relationship("10000101", "100001000").setInferred(false)), "MAIN/PROJECT/TEST2");

		// Two bad relationships are on MAIN/project
		IntegrityIssueReport reportProject = integrityService.findAllComponentsWithBadIntegrity(branchService.findLatest("MAIN/PROJECT"), true);
		assertNull(reportProject.getRelationshipsWithMissingOrInactiveSource());
		assertEquals(1, reportProject.getRelationshipsWithMissingOrInactiveType().size());
		assertEquals(1, reportProject.getRelationshipsWithMissingOrInactiveDestination().size());

		// MAIN/project/test1 was created before the bad relationships so there should be no issue on that branch
		IntegrityIssueReport reportProjectTest1 = integrityService.findAllComponentsWithBadIntegrity(branchService.findLatest("MAIN/PROJECT/TEST1"), true);
		assertNull(reportProjectTest1.getRelationshipsWithMissingOrInactiveSource());
		assertNull(reportProjectTest1.getRelationshipsWithMissingOrInactiveType());
		assertNull(reportProjectTest1.getRelationshipsWithMissingOrInactiveDestination());

		// MAIN/project/test2 was created after the bad relationships so should be able to see the issues on MAIN/project plus the new bad relationship on MAIN/project/test2
		IntegrityIssueReport reportProjectTest2 = integrityService.findAllComponentsWithBadIntegrity(branchService.findLatest("MAIN/PROJECT/TEST2"), true);
		assertNull(reportProjectTest2.getRelationshipsWithMissingOrInactiveSource());
		assertEquals(1, reportProjectTest2.getRelationshipsWithMissingOrInactiveType().size());
		assertEquals(2, reportProjectTest2.getRelationshipsWithMissingOrInactiveDestination().size());

		// Let's make concept 5 inactive on MAIN/project
		conceptService.update(new Concept("100005").setActive(false), "MAIN/PROJECT");

		// MAIN/project should have no new issues. Concept 5's relationship will not have a missing source concept because the relationship will have been deleted automatically

		// Still just two bad relationships are on MAIN/project
		assertEquals(reportProject, integrityService.findAllComponentsWithBadIntegrity(branchService.findLatest("MAIN/PROJECT"), true), "MAIN/PROJECT report should be unchanged.");

		// There is a relationship on MAIN/project/test2 which will break now that concept 5 is inactive,
		// however the report on MAIN/project/test2 should not have changed yet because we have not rebased.
		assertEquals(reportProjectTest2, integrityService.findAllComponentsWithBadIntegrity(branchService.findLatest("MAIN/PROJECT/TEST2"), true), "MAIN/PROJECT/TEST2 report should be unchanged");

		// Let's rebase MAIN/project/test2
		branchMergeService.mergeBranchSync("MAIN/PROJECT", "MAIN/PROJECT/TEST2", Collections.emptySet());

		// MAIN/project/test2 should now have a new issue because the stated relationship on concept 6 points to the inactive concept 5.
		IntegrityIssueReport reportProjectTest2Run2 = integrityService.findAllComponentsWithBadIntegrity(branchService.findLatest("MAIN/PROJECT/TEST2"), true);
		assertNotEquals(reportProjectTest2, reportProjectTest2Run2, "MAIN/PROJECT/TEST2 report should be unchanged");
		assertNull(reportProjectTest2Run2.getRelationshipsWithMissingOrInactiveSource());
		assertEquals(1, reportProjectTest2Run2.getRelationshipsWithMissingOrInactiveType().size());
		assertEquals(3, reportProjectTest2Run2.getRelationshipsWithMissingOrInactiveDestination().size(), "There should be an extra rel with missing destination.");

		// Making relationships inactive should remove them from the report
		Set<Long> ids = new HashSet<>(reportProjectTest2Run2.getRelationshipsWithMissingOrInactiveType().keySet());
		ids.addAll(reportProjectTest2Run2.getRelationshipsWithMissingOrInactiveDestination().keySet());
		makeRelationshipInactive(ids, "MAIN/PROJECT/TEST2");

		IntegrityIssueReport reportProjectTest2Run3 = integrityService.findAllComponentsWithBadIntegrity(branchService.findLatest("MAIN/PROJECT/TEST2"), true);
		assertNull(reportProjectTest2Run3.getRelationshipsWithMissingOrInactiveSource());
		assertNull(reportProjectTest2Run3.getRelationshipsWithMissingOrInactiveType());
		assertNull(reportProjectTest2Run3.getRelationshipsWithMissingOrInactiveDestination());
	}

	private void makeRelationshipInactive(Collection<Long> relationshipIds, String branchPath) {
		try (Commit commit = branchService.openCommit(branchPath)) {
			Set<Relationship> relationships = relationshipIds.stream().map(id -> {
						Relationship relationship = relationshipService.findRelationship(branchPath, id.toString());
						relationship.setActive(false);
						relationship.markChanged();
						return relationship;
					}).collect(Collectors.toSet());
			conceptUpdateHelper.doSaveBatchRelationships(relationships, commit);
			commit.markSuccessful();
		}
	}

	@Test
	/*
		Test the method that checks only the components changed on the branch.
		The purpose of this method is to check only what's changed for speed but to block promotion until changes are fixed.
	 */
	void testFindChangedComponentsWithBadIntegrity() throws ServiceException {
		sBranchService.create("MAIN/PROJECT");

		sBranchService.create("MAIN/PROJECT/TEST1");

		conceptService.create(new Concept(ISA), "MAIN/PROJECT");
		conceptService.create(new Concept("100001"), "MAIN/PROJECT");
		conceptService.create(new Concept("609096000"), "MAIN/PROJECT");
		// Valid relationship on MAIN/project
		conceptService.create(new Concept("10000101").addRelationship(new Relationship("10000101", "100001").setInferred(false)), "MAIN/PROJECT");
		// Valid relationship on MAIN/project
		conceptService.create(new Concept("100002").addRelationship(new Relationship("10000101", "100001").setInferred(false)), "MAIN/PROJECT");
		// Missing Type on MAIN/project
		conceptService.create(new Concept("100003").addRelationship(new Relationship("10000102", "100002").setInferred(false)), "MAIN/PROJECT");
		// Missing Destination on MAIN/project
		conceptService.create(new Concept("100004").addRelationship(new Relationship("10000101", "100001000").setInferred(false)), "MAIN/PROJECT");
		// Missing Destination on MAIN/project - axiom
		conceptService.create(new Concept("100104").addAxiom(new Relationship(ISA, "100002"), new Relationship("10000101", "100001000")), "MAIN/PROJECT");
		// Valid relationship on MAIN/project
		conceptService.create(new Concept("100005").addRelationship(new Relationship("10000101", "100002").setInferred(false)), "MAIN/PROJECT");

		branchService.create("MAIN/PROJECT/TEST2");
		// Valid relationship on MAIN/project/test2
		conceptService.create(new Concept("100006").addRelationship(new Relationship("10000101", "100005").setInferred(false)), "MAIN/PROJECT/TEST2");
		// Valid axiom on MAIN/project/test2
		conceptService.create(new Concept("101006").addAxiom(new Relationship(ISA, "100002"), new Relationship("10000101", "100005")), "MAIN/PROJECT/TEST2");
		// Missing Destination on MAIN/project/test2
		conceptService.create(new Concept("100007").addRelationship(new Relationship("10000101", "100001000").setInferred(false)), "MAIN/PROJECT/TEST2");

		// create range constraint
		createRangeConstraint("MAIN/PROJECT/TEST2", "10000201", "dec(>#0..)");
		createRangeConstraint("MAIN/PROJECT/TEST2", "10000202", "dec(>#0..)");

		// missing concrete attribute type in axiom on MAIN/project/test2
		conceptService.create(new Concept("100008").addAxiom(new Relationship(ISA, "100002"), Relationship.newConcrete("10000201", ConcreteValue.newDecimal("#50.0"))),
				"MAIN/PROJECT/TEST2");

		// missing concrete attribute in concrete value relationship
		// using stated here for testing as integrity service doesn't check inferred. Inferred relationships are checked during classification.
		conceptService.create(new Concept("100009").addRelationship(Relationship.newConcrete("10000202", ConcreteValue.newDecimal("#50.0")).setInferred(false)),
				"MAIN/PROJECT/TEST2");

		Concept wrongConcept = conceptService.find("100009", "MAIN/PROJECT/TEST2");
		wrongConcept.getRelationships().forEach(System.out::println);
		assertNotNull(wrongConcept);
		try {
			integrityService.findChangedComponentsWithBadIntegrityNotFixed(branchService.findLatest("MAIN"));
			fail("We should never get to this line because we should throw an exception when attempting the incremental integrity check on MAIN - use the full check there!");
		} catch (Exception e) {
			// Pass
		}

		// Two bad relationships are on MAIN/project
		IntegrityIssueReport reportProject = integrityService.findChangedComponentsWithBadIntegrityNotFixed(branchService.findLatest("MAIN/PROJECT"));
		assertNull(reportProject.getRelationshipsWithMissingOrInactiveSource());
		assertEquals(1, reportProject.getRelationshipsWithMissingOrInactiveType().size());
		assertEquals(1, reportProject.getRelationshipsWithMissingOrInactiveDestination().size());
		assertEquals(1, reportProject.getAxiomsWithMissingOrInactiveReferencedConcept().size());
		assertEquals("[100001000]", getAxiomReferencedConcepts(reportProject));

		// MAIN/project/test1 was created before the bad relationships so there should be no issue on that branch
		IntegrityIssueReport reportProjectTest1 = integrityService.findChangedComponentsWithBadIntegrityNotFixed(branchService.findLatest("MAIN/PROJECT/TEST1"));
		assertNull(reportProjectTest1.getRelationshipsWithMissingOrInactiveSource());
		assertNull(reportProjectTest1.getRelationshipsWithMissingOrInactiveType());
		assertNull(reportProjectTest1.getRelationshipsWithMissingOrInactiveDestination());
		assertNull(reportProjectTest1.getAxiomsWithMissingOrInactiveReferencedConcept());

		// MAIN/project/test2 was created after the bad relationships and axiom so can see the issues on MAIN/project,
		// however this method only reports issues created on that branch, so we are only expecting the 1 issue created on MAIN/project/test2 to be reported
		IntegrityIssueReport reportProjectTest2 = integrityService.findChangedComponentsWithBadIntegrityNotFixed(branchService.findLatest("MAIN/PROJECT/TEST2"));
		assertNull(reportProjectTest2.getRelationshipsWithMissingOrInactiveSource());
		assertEquals(1, reportProjectTest2.getRelationshipsWithMissingOrInactiveType().size());
		assertEquals(1, reportProjectTest2.getRelationshipsWithMissingOrInactiveDestination().size());
		assertEquals(1, reportProjectTest2.getAxiomsWithMissingOrInactiveReferencedConcept().size());
		assertEquals("100008", reportProjectTest2.getAxiomsWithMissingOrInactiveReferencedConcept().values().iterator().next().getConceptId());

		// Let's make concept 5 inactive on MAIN/project
		conceptService.update(new Concept("100005").setActive(false), "MAIN/PROJECT");

		// MAIN/project should have no new issues. Concept 5's relationship will not have a missing source concept because the relationship will have been deleted automatically

		// Still just two bad relationships are on MAIN/project
		assertEquals(reportProject, integrityService.findChangedComponentsWithBadIntegrityNotFixed(branchService.findLatest("MAIN/PROJECT")), "MAIN/PROJECT report should be unchanged.");

		// There is a relationship and an axiom on MAIN/project/test2 which will break now that concept 5 is inactive,
		// however the report on MAIN/project/test2 should not have changed yet because we have not rebased.
		assertEquals(reportProjectTest2, integrityService.findChangedComponentsWithBadIntegrityNotFixed(branchService.findLatest("MAIN/PROJECT/TEST2")), "MAIN/PROJECT/TEST2 report should be unchanged");

		// Let's rebase MAIN/project/test2
		branchMergeService.mergeBranchSync("MAIN/PROJECT", "MAIN/PROJECT/TEST2", Collections.emptySet());

		// MAIN/project/test2 should now have a new issues because the stated relationship on concept 100006 and the axiom on concept 101006 points to the inactive concept 100005.
		// Although this method only reports changes on that branch and the concept was not made inactive on that branch because the relationship was created (or modified) on
		// that branch it will still be reported.
		IntegrityIssueReport reportProjectTest2Run2 = integrityService.findChangedComponentsWithBadIntegrityNotFixed(branchService.findLatest("MAIN/PROJECT/TEST2"));
		assertNotEquals(reportProjectTest2, reportProjectTest2Run2, "MAIN/PROJECT/TEST2 report should be unchanged");
		assertNull(reportProjectTest2Run2.getRelationshipsWithMissingOrInactiveSource());
		assertEquals(1, reportProjectTest2Run2.getRelationshipsWithMissingOrInactiveType().size());
		assertEquals(1, reportProjectTest2.getAxiomsWithMissingOrInactiveReferencedConcept().size());
		assertEquals("100008", reportProjectTest2.getAxiomsWithMissingOrInactiveReferencedConcept().values().iterator().next().getConceptId());
		assertEquals(2, reportProjectTest2Run2.getRelationshipsWithMissingOrInactiveDestination().size(), "There should be an extra rel with missing destination.");
		assertEquals(2, reportProjectTest2Run2.getAxiomsWithMissingOrInactiveReferencedConcept().size(), "There should be an extra axiom with missing referenced concept.");
		assertEquals("[100005, 10000201]", getAxiomReferencedConcepts(reportProjectTest2Run2));
	}

	@SuppressWarnings("unchecked")
	public String getAxiomReferencedConcepts(IntegrityIssueReport reportProject) {
		return Arrays.toString(reportProject.getAxiomsWithMissingOrInactiveReferencedConcept().values().stream()
				.map(conceptMini -> (Set<Long>)conceptMini.getExtraFields().get("missingOrInactiveConcepts")).flatMap(Collection::stream).sorted().toArray());
	}

	@Test
	void testIntegrityCommitHook() throws Exception {
		// create US CodeSystem for testing
		CodeSystem codeSystem = new CodeSystem("SNOMEDCT-US", "MAIN/SNOMEDCT-US");
		codeSystemService.createCodeSystem(codeSystem);

		// create extension project branch
		String project = "MAIN/SNOMEDCT-US/PROJECT";
		sBranchService.create(project);

		// add some bad data on the code system branch to simulate extension upgrade results
		String path = codeSystem.getBranchPath();
		// invalid relationship
		conceptService.create(new Concept("10000101").addRelationship(new Relationship("100002", "100001").setInferred(false)), path);

		// Two bad relationships are on the CodeSystem branch
		IntegrityIssueReport reportProject = integrityService.findChangedComponentsWithBadIntegrityNotFixed(branchService.findLatest(path));
		assertNull(reportProject.getRelationshipsWithMissingOrInactiveSource());
		assertEquals(1, reportProject.getRelationshipsWithMissingOrInactiveType().size());
		assertEquals(1, reportProject.getRelationshipsWithMissingOrInactiveDestination().size());

		Branch branch = branchService.findBranchOrThrow(path);
		Map<String, Object> metadataMap = branch.getMetadata().getAsMap();
		Map<String, String> integrityIssueMetaData = new HashMap<>();
		integrityIssueMetaData.put(INTEGRITY_ISSUE_METADATA_KEY, "true");
		metadataMap.put(INTERNAL_METADATA_KEY, integrityIssueMetaData);
		branchService.updateMetadata(branch.getPath(), metadataMap);

		branch = branchService.findLatest(path);
		Metadata metadata = branch.getMetadata();
		assertTrue(metadata.containsKey(INTERNAL_METADATA_KEY));
		String integrityIssueFound = metadata.getMapOrCreate(INTERNAL_METADATA_KEY).get(INTEGRITY_ISSUE_METADATA_KEY);
		assertTrue(Boolean.parseBoolean(integrityIssueFound));

		// rebase extension project branch and check the integrity flag is updated in branch metadata
		branchMergeService.mergeBranchSync(codeSystem.getBranchPath(), project, Collections.emptyList());
		branch = branchService.findLatest(project);
		metadata = branch.getMetadata();
		assertTrue(metadata.containsKey(INTERNAL_METADATA_KEY));
		integrityIssueFound = metadata.getMapOrCreate(INTERNAL_METADATA_KEY).get(INTEGRITY_ISSUE_METADATA_KEY);
		assertTrue(Boolean.parseBoolean(integrityIssueFound));

		// create a fix task and check the integrity issue flag is set to true
		String taskBranchPath = project + "/TASKA";
		sBranchService.create(taskBranchPath);

		Branch taskBranch = branchService.findBranchOrThrow(taskBranchPath, false);
		String taskIntegrityFlag = taskBranch.getMetadata().getMapOrCreate(INTERNAL_METADATA_KEY).get(INTEGRITY_ISSUE_METADATA_KEY);
		assertNotNull(taskIntegrityFlag);
		assertTrue(Boolean.parseBoolean(taskIntegrityFlag));

		// make partial fix
		conceptService.create(new Concept("100001"), taskBranchPath);
		taskBranch = branchService.findBranchOrThrow(taskBranchPath, true);
		String taskClassifiedFlag = taskBranch.getMetadata().getMapOrCreate(INTERNAL_METADATA_KEY).get(BranchClassificationStatusService.CLASSIFIED_METADATA_KEY);
		assertNotNull(taskClassifiedFlag);
		assertFalse(Boolean.parseBoolean(taskClassifiedFlag));
		taskIntegrityFlag = taskBranch.getMetadata().getMapOrCreate(INTERNAL_METADATA_KEY).get(INTEGRITY_ISSUE_METADATA_KEY);
		assertTrue(Boolean.parseBoolean(taskIntegrityFlag));

		taskClassifiedFlag = taskBranch.getMetadata().getMapOrCreate(INTERNAL_METADATA_KEY).get(BranchClassificationStatusService.CLASSIFIED_METADATA_KEY);
		assertNotNull(taskClassifiedFlag);
		assertFalse(Boolean.parseBoolean(taskClassifiedFlag));

		// promote partial fix to project and check the integrity on project is still set to true
		branchMergeService.mergeBranchSync(taskBranchPath, project, null);
		branch = branchService.findLatest(project);
		metadata = branch.getMetadata();
		integrityIssueFound = metadata.getMapOrCreate(INTERNAL_METADATA_KEY).get(INTEGRITY_ISSUE_METADATA_KEY);
		assertTrue(Boolean.parseBoolean(integrityIssueFound));

		// add complete fix in another task and promote
		taskBranchPath = project + "/TASKB";
		sBranchService.create(taskBranchPath);
		conceptService.create(new Concept("100002"), taskBranchPath);
		branch = branchService.findLatest(taskBranchPath);
		reportProject = integrityService.findChangedComponentsWithBadIntegrityNotFixed(branch);
		assertTrue(reportProject.isEmpty());
		metadata = branch.getMetadata();
		integrityIssueFound = metadata.getMapOrCreate(INTERNAL_METADATA_KEY).get(INTEGRITY_ISSUE_METADATA_KEY);
		// CodeSystem integrity issue flag should be cleared on the fix task as all fixes are completed.
		assertNull(integrityIssueFound);

		// promote task to project
		branchMergeService.mergeBranchSync(taskBranchPath, project, null);
		branch = branchService.findLatest(project);
		metadata = branch.getMetadata();
		integrityIssueFound = metadata.getMapOrCreate(INTERNAL_METADATA_KEY).get(INTEGRITY_ISSUE_METADATA_KEY);
		assertNull(integrityIssueFound, "The integrityIssue flag should be removed after all issues are fixed on the project");

		// promote project
		branchMergeService.mergeBranchSync(project, codeSystem.getBranchPath(), null);

		branch = branchService.findLatest(codeSystem.getBranchPath());
		reportProject = integrityService.findChangedComponentsWithBadIntegrityNotFixed(branch);
		assertTrue(reportProject.isEmpty());
		metadata = branch.getMetadata();
		integrityIssueFound = metadata.getMapOrCreate(INTERNAL_METADATA_KEY).get(INTEGRITY_ISSUE_METADATA_KEY);
		// CodeSystem integrity issue flag should be cleared
		assertNull(integrityIssueFound, "The integrityIssue flag should be removed after all issues are fixed");
	}

	@Test
	void testDeepBranchStructure() {
		sBranchService.create("MAIN/SNOMEDCT-NO");
		sBranchService.create("MAIN/SNOMEDCT-NO/REFSETS");
		sBranchService.create("MAIN/SNOMEDCT-NO/REFSETS/REFSET-77141000202106");
		Branch task = sBranchService.create("MAIN/SNOMEDCT-NO/REFSETS/REFSET-77141000202106/TASK");
		String extensionMain = "MAIN/SNOMEDCT-NO";
		try {
			integrityService.findChangedComponentsWithBadIntegrityNotFixed(task, extensionMain);
		} catch (Exception e) {
			fail("Shouldn't throw exception");
		}
	}

	@Test
	void testIntegrityReportWithAdditionalCodeSystemDependency() throws Exception {
		// Create a concept in MAIN module
		conceptService.create(new Concept(ISA), "MAIN");

		// Version on MAIN
		CodeSystem main = new CodeSystem("SNOMEDCT", "MAIN");
		codeSystemService.createCodeSystem(main);
		codeSystemService.createVersion(main, 20250101, "SNOMEDCT 20250101 release");

		// Create LOINC code system as an additional dependency
		CodeSystem loinc = new CodeSystem("SNOMEDCT-LOINC", "MAIN/SNOMEDCT-LOINC");
		codeSystemService.createCodeSystem(loinc);
		conceptService.create(new Concept("11010000107"), loinc.getBranchPath());
		Metadata metadata = new Metadata();
		metadata.putString("defaultModuleId", "11010000107");
		branchService.updateMetadata(loinc.getBranchPath(), metadata);

		// Create LOINC MDRS
		referenceSetMemberService.createMember(loinc.getBranchPath(), constructMDRS("11010000107", CORE_MODULE,20250101));

		// Create a concept in LOINC module
		conceptService.create(new Concept("11010000120"), "MAIN/SNOMEDCT-LOINC");

		// Version the LOINC code system
		codeSystemService.createVersion(loinc, 20250201, "LOINC 2025-02-01 release");

		// Create extension code system that depends on both MAIN and LOINC
		CodeSystem extension = new CodeSystem("SNOMEDCT-EXT", "MAIN/SNOMEDCT-EXT");
		codeSystemService.createCodeSystem(extension);

		metadata = new Metadata();
		metadata.putString("defaultModuleId", "22010000108");
		branchService.updateMetadata(extension.getBranchPath(), metadata);

		// Add additional dependency on LOINC version in branch metadata
		referenceSetMemberService.createMember(extension.getBranchPath(), constructMDRS("22010000108", "11010000107",20250201));

		Metadata branchMetadata = branchService.findLatest(extension.getBranchPath()).getMetadata();
		assertTrue(branchMetadata.containsKey("vc.additional-dependent-branches"), "Additional code system dependency should be updated in branch metadata");

		assertEquals(List.of("MAIN/SNOMEDCT-LOINC/2025-02-01"), branchMetadata.getList("vc.additional-dependent-branches"));

		// Create a concept in the extension that references the LOINC concept
		Concept conceptReferenceLoinc = new Concept("220100001");
		conceptReferenceLoinc.addRelationship(new Relationship(ISA, "11010000120").setInferred(false));
		conceptService.create(conceptReferenceLoinc, extension.getBranchPath());

		// Run integrity check on the extension branch - should be clean initially
		IntegrityIssueReport initialReport = integrityService.findChangedComponentsWithBadIntegrityNotFixed(branchService.findLatest(extension.getBranchPath()));
		assertTrue(initialReport.isEmpty(), "Initial integrity check should be clean");

		// Inactivate the concept in the MAIN code system
		conceptService.update(new Concept(ISA).setActive(false), "MAIN");

		// Version the MAIN code system again with the inactive concept
		codeSystemService.createVersion(main, 20250201, "SNOMEDCT 2025-02-01 release");

		// Now inactivate the LOINC concept in the LOINC code system
		codeSystemUpgradeService.upgrade(null, loinc, 20250201, true);
		conceptService.update(new Concept("11010000120").setActive(false), loinc.getBranchPath());
		
		// Version the LOINC code system again with the inactive concept
		codeSystemService.createVersion(loinc, 20250301, "LOINC 2025-03-01 release");

		// Now update extension to the latest International and LOINC version to trigger integrity issues
		codeSystemUpgradeService.upgrade(null, extension,20250201, true);

		// Assert additional code system dependency is updated in branch metadata
		branchMetadata = branchService.findLatest(extension.getBranchPath()).getMetadata();
		assertEquals(List.of("MAIN/SNOMEDCT-LOINC/2025-03-01"), branchMetadata.getList("vc.additional-dependent-branches"));

		// Check concept 11010000107 is inactive after upgrade
		Concept afterUpgrade = conceptService.find("11010000120", branchService.findLatest(extension.getBranchPath()).getPath());
		assertFalse(afterUpgrade.isActive(), "Concept 11010000120 should be inactive after upgrade");

		// Check integrity issues on the extension branch
		assertEquals("true", branchMetadata.getMapOrCreate(INTERNAL_METADATA_KEY).get(IntegrityService.INTEGRITY_ISSUE_METADATA_KEY));

		// Verify that the extension branch now has integrity issues
		IntegrityIssueReport integrityReport = integrityService.findChangedComponentsWithBadIntegrityNotFixed(branchService.findLatest(extension.getBranchPath()));
		
		// Should have integrity issues due to inactive LOINC concept
		assertNotNull(integrityReport.getRelationshipsWithMissingOrInactiveDestination());
		assertEquals(1, integrityReport.getRelationshipsWithMissingOrInactiveDestination().size());
		assertEquals(1, integrityReport.getRelationshipsWithMissingOrInactiveType().size());

		// The relationship pointing to inactive LOINC concept should be flagged
		Long badRelationshipId = integrityReport.getRelationshipsWithMissingOrInactiveDestination().keySet().iterator().next();
		assertEquals(Long.valueOf("11010000120"), integrityReport.getRelationshipsWithMissingOrInactiveDestination().get(badRelationshipId));
		assertEquals(Long.valueOf(ISA), integrityReport.getRelationshipsWithMissingOrInactiveType().get(badRelationshipId));
	}

	private ReferenceSetMember constructMDRS(String moduleId, String dependantModuleId, Integer targetEffectiveTime) {
		ReferenceSetMember mdrs = new ReferenceSetMember(moduleId, Concepts.MODULE_DEPENDENCY_REFERENCE_SET, dependantModuleId);
		if (targetEffectiveTime != null) {
			mdrs.setAdditionalField("targetEffectiveTime", targetEffectiveTime.toString());
		}
		return mdrs;
	}
}
