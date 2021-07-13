package org.snomed.snowstorm.core.data.services;

import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.domain.Branch;
import io.kaicode.elasticvc.domain.Commit;
import io.kaicode.elasticvc.domain.Metadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.TestConfig;
import org.snomed.snowstorm.core.data.domain.CodeSystem;
import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.core.data.domain.ConcreteValue;
import org.snomed.snowstorm.core.data.domain.Relationship;
import org.snomed.snowstorm.core.data.services.classification.BranchClassificationStatusService;
import org.snomed.snowstorm.core.data.services.pojo.IntegrityIssueReport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.Assert.*;
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

	@Test
	/*
		Test the method that checks all the components visible on the branch.
	 */
	void testFindAllComponentsWithBadIntegrity() throws ServiceException {
		sBranchService.create("MAIN/project");

		sBranchService.create("MAIN/project/test1");

		conceptService.create(new Concept("100001"), "MAIN/project");
		// Valid relationship on MAIN/project
		conceptService.create(new Concept("10000101").addRelationship(new Relationship("10000101", "100001").setInferred(false)), "MAIN/project");
		// Valid relationship on MAIN/project
		conceptService.create(new Concept("100002").addRelationship(new Relationship("10000101", "100001").setInferred(false)), "MAIN/project");
		// Missing Type on MAIN/project
		conceptService.create(new Concept("100003").addRelationship(new Relationship("10000102", "100002").setInferred(false)), "MAIN/project");
		// Missing Destination on MAIN/project
		conceptService.create(new Concept("100004").addRelationship(new Relationship("10000101", "100001000").setInferred(false)), "MAIN/project");
		// Valid relationship on MAIN/project
		conceptService.create(new Concept("100005").addRelationship(new Relationship("10000101", "100002").setInferred(false)), "MAIN/project");

		branchService.create("MAIN/project/test2");
		// Valid relationship on MAIN/project/test2
		conceptService.create(new Concept("100006").addRelationship(new Relationship("10000101", "100005").setInferred(false)), "MAIN/project/test2");
		// Missing Destination on MAIN/project/test2
		conceptService.create(new Concept("100007").addRelationship(new Relationship("10000101", "100001000").setInferred(false)), "MAIN/project/test2");

		// Two bad relationships are on MAIN/project
		IntegrityIssueReport reportProject = integrityService.findAllComponentsWithBadIntegrity(branchService.findLatest("MAIN/project"), true);
		assertNull(reportProject.getRelationshipsWithMissingOrInactiveSource());
		assertEquals(1, reportProject.getRelationshipsWithMissingOrInactiveType().size());
		assertEquals(1, reportProject.getRelationshipsWithMissingOrInactiveDestination().size());

		// MAIN/project/test1 was created before the bad relationships so there should be no issue on that branch
		IntegrityIssueReport reportProjectTest1 = integrityService.findAllComponentsWithBadIntegrity(branchService.findLatest("MAIN/project/test1"), true);
		assertNull(reportProjectTest1.getRelationshipsWithMissingOrInactiveSource());
		assertNull(reportProjectTest1.getRelationshipsWithMissingOrInactiveType());
		assertNull(reportProjectTest1.getRelationshipsWithMissingOrInactiveDestination());

		// MAIN/project/test2 was created after the bad relationships so should be able to see the issues on MAIN/project plus the new bad relationship on MAIN/project/test2
		IntegrityIssueReport reportProjectTest2 = integrityService.findAllComponentsWithBadIntegrity(branchService.findLatest("MAIN/project/test2"), true);
		assertNull(reportProjectTest2.getRelationshipsWithMissingOrInactiveSource());
		assertEquals(1, reportProjectTest2.getRelationshipsWithMissingOrInactiveType().size());
		assertEquals(2, reportProjectTest2.getRelationshipsWithMissingOrInactiveDestination().size());

		// Let's make concept 5 inactive on MAIN/project
		conceptService.update((Concept) new Concept("100005").setActive(false), "MAIN/project");

		// MAIN/project should have no new issues. Concept 5's relationship will not have a missing source concept because the relationship will have been deleted automatically

		// Still just two bad relationships are on MAIN/project
		assertEquals("MAIN/project report should be unchanged.", reportProject, integrityService.findAllComponentsWithBadIntegrity(branchService.findLatest("MAIN/project"), true));

		// There is a relationship on MAIN/project/test2 which will break now that concept 5 is inactive,
		// however the report on MAIN/project/test2 should not have changed yet because we have not rebased.
		assertEquals("MAIN/project/test2 report should be unchanged", reportProjectTest2, integrityService.findAllComponentsWithBadIntegrity(branchService.findLatest("MAIN/project/test2"), true));

		// Let's rebase MAIN/project/test2
		branchMergeService.mergeBranchSync("MAIN/project", "MAIN/project/test2", Collections.emptySet());

		// MAIN/project/test2 should now have a new issue because the stated relationship on concept 6 points to the inactive concept 5.
		IntegrityIssueReport reportProjectTest2Run2 = integrityService.findAllComponentsWithBadIntegrity(branchService.findLatest("MAIN/project/test2"), true);
		assertNotEquals("MAIN/project/test2 report should be unchanged", reportProjectTest2, reportProjectTest2Run2);
		assertNull(reportProjectTest2Run2.getRelationshipsWithMissingOrInactiveSource());
		assertEquals(1, reportProjectTest2Run2.getRelationshipsWithMissingOrInactiveType().size());
		assertEquals("There should be an extra rel with missing destination.", 3, reportProjectTest2Run2.getRelationshipsWithMissingOrInactiveDestination().size());

		// Making relationships inactive should remove them from the report
		Set<Long> ids = new HashSet<>(reportProjectTest2Run2.getRelationshipsWithMissingOrInactiveType().keySet());
		ids.addAll(reportProjectTest2Run2.getRelationshipsWithMissingOrInactiveDestination().keySet());
		makeRelationshipInactive(ids, "MAIN/project/test2");

		IntegrityIssueReport reportProjectTest2Run3 = integrityService.findAllComponentsWithBadIntegrity(branchService.findLatest("MAIN/project/test2"), true);
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
		sBranchService.create("MAIN/project");

		sBranchService.create("MAIN/project/test1");

		conceptService.create(new Concept(ISA), "MAIN/project");
		conceptService.create(new Concept("100001"), "MAIN/project");
		conceptService.create(new Concept("609096000"), "MAIN/project");
		// Valid relationship on MAIN/project
		conceptService.create(new Concept("10000101").addRelationship(new Relationship("10000101", "100001").setInferred(false)), "MAIN/project");
		// Valid relationship on MAIN/project
		conceptService.create(new Concept("100002").addRelationship(new Relationship("10000101", "100001").setInferred(false)), "MAIN/project");
		// Missing Type on MAIN/project
		conceptService.create(new Concept("100003").addRelationship(new Relationship("10000102", "100002").setInferred(false)), "MAIN/project");
		// Missing Destination on MAIN/project
		conceptService.create(new Concept("100004").addRelationship(new Relationship("10000101", "100001000").setInferred(false)), "MAIN/project");
		// Missing Destination on MAIN/project - axiom
		conceptService.create(new Concept("100104").addAxiom(new Relationship(ISA, "100002"), new Relationship("10000101", "100001000")), "MAIN/project");
		// Valid relationship on MAIN/project
		conceptService.create(new Concept("100005").addRelationship(new Relationship("10000101", "100002").setInferred(false)), "MAIN/project");

		branchService.create("MAIN/project/test2");
		// Valid relationship on MAIN/project/test2
		conceptService.create(new Concept("100006").addRelationship(new Relationship("10000101", "100005").setInferred(false)), "MAIN/project/test2");
		// Valid axiom on MAIN/project/test2
		conceptService.create(new Concept("101006").addAxiom(new Relationship(ISA, "100002"), new Relationship("10000101", "100005")), "MAIN/project/test2");
		// Missing Destination on MAIN/project/test2
		conceptService.create(new Concept("100007").addRelationship(new Relationship("10000101", "100001000").setInferred(false)), "MAIN/project/test2");

		// create range constraint
		createRangeConstraint("MAIN/project/test2", "10000201", "dec(>#0..)");
		createRangeConstraint("MAIN/project/test2", "10000202", "dec(>#0..)");

		// missing concrete attribute type in axiom on MAIN/project/test2
		conceptService.create(new Concept("100008").addAxiom(new Relationship(ISA, "100002"), Relationship.newConcrete("10000201", ConcreteValue.newDecimal("#50.0"))),
				"MAIN/project/test2");

		// missing concrete attribute in concrete value relationship
		// using stated here for testing as integrity service doesn't check inferred. Inferred relationships are checked during classification.
		conceptService.create(new Concept("100009").addRelationship(Relationship.newConcrete("10000202", ConcreteValue.newDecimal("#50.0")).setInferred(false)),
				"MAIN/project/test2");

		Concept wrongConcept = conceptService.find("100009", "MAIN/project/test2");
		wrongConcept.getRelationships().stream().forEach(System.out::println);
		assertNotNull(wrongConcept);
		try {
			integrityService.findChangedComponentsWithBadIntegrity(branchService.findLatest("MAIN"));
			fail("We should never get to this line because we should throw an exception when attempting the incremental integrity check on MAIN - use the full check there!");
		} catch (Exception e) {
			// Pass
		}

		// Two bad relationships are on MAIN/project
		IntegrityIssueReport reportProject = integrityService.findChangedComponentsWithBadIntegrity(branchService.findLatest("MAIN/project"));
		assertNull(reportProject.getRelationshipsWithMissingOrInactiveSource());
		assertEquals(1, reportProject.getRelationshipsWithMissingOrInactiveType().size());
		assertEquals(1, reportProject.getRelationshipsWithMissingOrInactiveDestination().size());
		assertEquals(1, reportProject.getAxiomsWithMissingOrInactiveReferencedConcept().size());
		assertEquals("[100001000]", getAxiomReferencedConcepts(reportProject));

		// MAIN/project/test1 was created before the bad relationships so there should be no issue on that branch
		IntegrityIssueReport reportProjectTest1 = integrityService.findChangedComponentsWithBadIntegrity(branchService.findLatest("MAIN/project/test1"));
		assertNull(reportProjectTest1.getRelationshipsWithMissingOrInactiveSource());
		assertNull(reportProjectTest1.getRelationshipsWithMissingOrInactiveType());
		assertNull(reportProjectTest1.getRelationshipsWithMissingOrInactiveDestination());
		assertNull(reportProjectTest1.getAxiomsWithMissingOrInactiveReferencedConcept());

		// MAIN/project/test2 was created after the bad relationships and axiom so can see the issues on MAIN/project,
		// however this method only reports issues created on that branch so we are only expecting the 1 issue created on MAIN/project/test2 to be reported
		IntegrityIssueReport reportProjectTest2 = integrityService.findChangedComponentsWithBadIntegrity(branchService.findLatest("MAIN/project/test2"));
		assertNull(reportProjectTest2.getRelationshipsWithMissingOrInactiveSource());
		assertEquals(1, reportProjectTest2.getRelationshipsWithMissingOrInactiveType().size());
		assertEquals(1, reportProjectTest2.getRelationshipsWithMissingOrInactiveDestination().size());
		assertEquals(1, reportProjectTest2.getAxiomsWithMissingOrInactiveReferencedConcept().size());
		assertEquals("100008", reportProjectTest2.getAxiomsWithMissingOrInactiveReferencedConcept().values().iterator().next().getConceptId());

		// Let's make concept 5 inactive on MAIN/project
		conceptService.update((Concept) new Concept("100005").setActive(false), "MAIN/project");

		// MAIN/project should have no new issues. Concept 5's relationship will not have a missing source concept because the relationship will have been deleted automatically

		// Still just two bad relationships are on MAIN/project
		assertEquals("MAIN/project report should be unchanged.", reportProject, integrityService.findChangedComponentsWithBadIntegrity(branchService.findLatest("MAIN/project")));

		// There is a relationship and an axiom on MAIN/project/test2 which will break now that concept 5 is inactive,
		// however the report on MAIN/project/test2 should not have changed yet because we have not rebased.
		assertEquals("MAIN/project/test2 report should be unchanged", reportProjectTest2, integrityService.findChangedComponentsWithBadIntegrity(branchService.findLatest("MAIN/project/test2")));

		// Let's rebase MAIN/project/test2
		branchMergeService.mergeBranchSync("MAIN/project", "MAIN/project/test2", Collections.emptySet());

		// MAIN/project/test2 should now have a new issues because the stated relationship on concept 100006 and the axiom on concept 101006 points to the inactive concept 100005.
		// Although this method only reports changes on that branch and the concept was not made inactive on that branch because the relationship was created (or modified) on
		// that branch it will still be reported.
		IntegrityIssueReport reportProjectTest2Run2 = integrityService.findChangedComponentsWithBadIntegrity(branchService.findLatest("MAIN/project/test2"));
		assertNotEquals("MAIN/project/test2 report should be unchanged", reportProjectTest2, reportProjectTest2Run2);
		assertNull(reportProjectTest2Run2.getRelationshipsWithMissingOrInactiveSource());
		assertEquals(1, reportProjectTest2Run2.getRelationshipsWithMissingOrInactiveType().size());
		assertEquals(1, reportProjectTest2.getAxiomsWithMissingOrInactiveReferencedConcept().size());
		assertEquals("100008", reportProjectTest2.getAxiomsWithMissingOrInactiveReferencedConcept().values().iterator().next().getConceptId());
		assertEquals("There should be an extra rel with missing destination.", 2, reportProjectTest2Run2.getRelationshipsWithMissingOrInactiveDestination().size());
		assertEquals("There should be an extra axiom with missing referenced concept.", 2, reportProjectTest2Run2.getAxiomsWithMissingOrInactiveReferencedConcept().size());
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
		String project = "MAIN/SNOMEDCT-US/project";
		sBranchService.create(project);

		// add some bad data on the code system branch to simulate extension upgrade results
		String path = codeSystem.getBranchPath();
		// invalid relationship
		conceptService.create(new Concept("10000101").addRelationship(new Relationship("100002", "100001").setInferred(false)), path);

		// Two bad relationships are on the CodeSystem branch
		IntegrityIssueReport reportProject = integrityService.findChangedComponentsWithBadIntegrity(branchService.findLatest(path));
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
		branchMergeService.mergeBranchSync(codeSystem.getBranchPath(), project, Collections.EMPTY_LIST);
		branch = branchService.findLatest(project);
		metadata = branch.getMetadata();
		assertTrue(metadata.containsKey(INTERNAL_METADATA_KEY));
		integrityIssueFound = metadata.getMapOrCreate(INTERNAL_METADATA_KEY).get(INTEGRITY_ISSUE_METADATA_KEY);
		assertTrue(Boolean.parseBoolean(integrityIssueFound));

		// create a fix task and check the integrity issue flag is set to true
		String taskBranchPath = project + "/taskA";
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
		taskBranchPath = project + "/taskB";
		sBranchService.create(taskBranchPath);
		conceptService.create(new Concept("100002"), taskBranchPath);
		branch = branchService.findLatest(taskBranchPath);
		reportProject = integrityService.findChangedComponentsWithBadIntegrity(branch);
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
		assertNull("The integrityIssue flag should be removed after all issues are fixed on the project", integrityIssueFound);

		// promote project
		branchMergeService.mergeBranchSync(project, codeSystem.getBranchPath(), null);

		branch = branchService.findLatest(codeSystem.getBranchPath());
		reportProject = integrityService.findChangedComponentsWithBadIntegrity(branch);
		assertTrue(reportProject.isEmpty());
		metadata = branch.getMetadata();
		integrityIssueFound = metadata.getMapOrCreate(INTERNAL_METADATA_KEY).get(INTEGRITY_ISSUE_METADATA_KEY);
		// CodeSystem integrity issue flag should be cleared
		assertNull("The integrityIssue flag should be removed after all issues are fixed", integrityIssueFound);
	}
}
