package org.snomed.snowstorm.core.data.services;

import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.domain.Branch;
import io.kaicode.elasticvc.domain.Commit;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.TestConfig;
import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.core.data.domain.Concepts;
import org.snomed.snowstorm.core.data.domain.Relationship;
import org.snomed.snowstorm.core.data.services.pojo.IntegrityIssueReport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.Assert.*;
import static org.snomed.snowstorm.core.data.services.BranchMetadataHelper.INTERNAL_METADATA_KEY;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = TestConfig.class)
public class IntegrityServiceTest extends AbstractTest {

	@Autowired
	private BranchService branchService;

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
	private BranchMetadataHelper branchMetadataHelper;

	@Test
	/*
		Test the method that checks all the components visible on the branch.
	 */
	public void testFindAllComponentsWithBadIntegrity() throws ServiceException {
		branchService.create("MAIN/project");

		branchService.create("MAIN/project/test1");

		conceptService.create(new Concept("100001"), "MAIN/project");
		// Valid relationship on MAIN/project
		conceptService.create(new Concept("10000101").addRelationship(new Relationship("10000101", "100001")), "MAIN/project");
		// Valid relationship on MAIN/project
		conceptService.create(new Concept("100002").addRelationship(new Relationship("10000101", "100001")), "MAIN/project");
		// Missing Type on MAIN/project
		conceptService.create(new Concept("100003").addRelationship(new Relationship("10000102", "100002")), "MAIN/project");
		// Missing Destination on MAIN/project
		conceptService.create(new Concept("100004").addRelationship(new Relationship("10000101", "100001000")), "MAIN/project");
		// Valid relationship on MAIN/project
		conceptService.create(new Concept("100005").addRelationship(new Relationship("10000101", "100002")), "MAIN/project");

		branchService.create("MAIN/project/test2");
		// Valid relationship on MAIN/project/test2
		conceptService.create(new Concept("100006").addRelationship(new Relationship("10000101", "100005")), "MAIN/project/test2");
		// Missing Destination on MAIN/project/test2
		conceptService.create(new Concept("100007").addRelationship(new Relationship("10000101", "100001000")), "MAIN/project/test2");

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
	public void testFindChangedComponentsWithBadIntegrity() throws ServiceException {
		branchService.create("MAIN/project");

		branchService.create("MAIN/project/test1");

		conceptService.create(new Concept(Concepts.ISA), "MAIN/project");
		conceptService.create(new Concept("100001"), "MAIN/project");
		conceptService.create(new Concept("609096000"), "MAIN/project");
		// Valid relationship on MAIN/project
		conceptService.create(new Concept("10000101").addRelationship(new Relationship("10000101", "100001")), "MAIN/project");
		// Valid relationship on MAIN/project
		conceptService.create(new Concept("100002").addRelationship(new Relationship("10000101", "100001")), "MAIN/project");
		// Missing Type on MAIN/project
		conceptService.create(new Concept("100003").addRelationship(new Relationship("10000102", "100002")), "MAIN/project");
		// Missing Destination on MAIN/project
		conceptService.create(new Concept("100004").addRelationship(new Relationship("10000101", "100001000")), "MAIN/project");
		// Missing Destination on MAIN/project - axiom
		conceptService.create(new Concept("100104").addAxiom(new Relationship(Concepts.ISA, "100002"), new Relationship("10000101", "100001000")), "MAIN/project");
		// Valid relationship on MAIN/project
		conceptService.create(new Concept("100005").addRelationship(new Relationship("10000101", "100002")), "MAIN/project");

		branchService.create("MAIN/project/test2");
		// Valid relationship on MAIN/project/test2
		conceptService.create(new Concept("100006").addRelationship(new Relationship("10000101", "100005")), "MAIN/project/test2");
		// Valid axiom on MAIN/project/test2
		conceptService.create(new Concept("101006").addAxiom(new Relationship(Concepts.ISA, "100002"), new Relationship("10000101", "100005")), "MAIN/project/test2");
		// Missing Destination on MAIN/project/test2
		conceptService.create(new Concept("100007").addRelationship(new Relationship("10000101", "100001000")), "MAIN/project/test2");

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
		assertNull(reportProjectTest2.getRelationshipsWithMissingOrInactiveType());
		assertEquals(1, reportProjectTest2.getRelationshipsWithMissingOrInactiveDestination().size());
		assertNull(reportProjectTest2.getAxiomsWithMissingOrInactiveReferencedConcept());

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
		assertNull(reportProjectTest2Run2.getRelationshipsWithMissingOrInactiveType());
		assertEquals("There should be an extra rel with missing destination.", 2, reportProjectTest2Run2.getRelationshipsWithMissingOrInactiveDestination().size());
		assertEquals("There should be an extra axiom with missing referenced concept.", 1, reportProjectTest2Run2.getAxiomsWithMissingOrInactiveReferencedConcept().size());
		assertEquals("[100005]", getAxiomReferencedConcepts(reportProjectTest2Run2));
	}

	@SuppressWarnings("unchecked")
	public String getAxiomReferencedConcepts(IntegrityIssueReport reportProject) {
		return Arrays.toString(reportProject.getAxiomsWithMissingOrInactiveReferencedConcept().values().stream()
				.map(conceptMini -> (Set<Long>)conceptMini.getExtraFields().get("missingOrInactiveConcepts")).flatMap(Collection::stream).sorted().toArray());
	}

	@Test
	public void testIntegrityCommitHook() throws Exception {
		String path = "MAIN/project";
		Branch branch = branchService.create(path);
		// invalid relationship
		conceptService.create(new Concept("10000101").addRelationship(new Relationship("100002", "100001")), path);

		// Two bad relationships are on project
		IntegrityIssueReport reportProject = integrityService.findChangedComponentsWithBadIntegrity(branchService.findLatest(path));
		assertNull(reportProject.getRelationshipsWithMissingOrInactiveSource());
		assertEquals(1, reportProject.getRelationshipsWithMissingOrInactiveType().size());
		assertEquals(1, reportProject.getRelationshipsWithMissingOrInactiveDestination().size());

		Map<String, String> metaData = branch.getMetadata();
		Map<String, Object> metaDataExpanded = metaData == null ? new HashMap<>() : branchMetadataHelper.expandObjectValues(metaData);
		metaDataExpanded.put("existingConfig", "test");
		Map<String, String> integrityIssueMetaData = new HashMap<>();
		integrityIssueMetaData.put(IntegrityService.INTEGRITY_ISSUE_METADATA_KEY, "true");
		integrityIssueMetaData.put("other_key", "something else");
		metaDataExpanded.put(INTERNAL_METADATA_KEY, integrityIssueMetaData);
		branchService.updateMetadata(branch.getPath(), branchMetadataHelper.flattenObjectValues(metaDataExpanded));

		branch = branchService.findLatest(path);
		assertNotNull(branch.getMetadata());
		metaDataExpanded = branchMetadataHelper.expandObjectValues(branch.getMetadata());
		assertTrue(metaDataExpanded.containsKey(INTERNAL_METADATA_KEY));
		String integrityIssueFound = ((Map<String, String>) metaDataExpanded.get(INTERNAL_METADATA_KEY)).get(IntegrityService.INTEGRITY_ISSUE_METADATA_KEY);
		assertTrue(Boolean.valueOf(integrityIssueFound));

		// partial fix
		conceptService.create(new Concept("100001"), path);
		branch = branchService.findLatest(path);
		reportProject = integrityService.findChangedComponentsWithBadIntegrity(branch);
		assertFalse(reportProject.isEmpty());
		assertNotNull(branch.getMetadata());
		metaDataExpanded = branchMetadataHelper.expandObjectValues(branch.getMetadata());
		assertTrue(metaDataExpanded.containsKey(INTERNAL_METADATA_KEY));
		integrityIssueFound = ((Map<String, String>) metaDataExpanded.get(INTERNAL_METADATA_KEY)).get(IntegrityService.INTEGRITY_ISSUE_METADATA_KEY);
		assertTrue(Boolean.valueOf(integrityIssueFound));

		// complete fix
		conceptService.create(new Concept("100002"), path);
		branch = branchService.findLatest(path);
		reportProject = integrityService.findChangedComponentsWithBadIntegrity(branch);
		assertTrue(reportProject.isEmpty());
		assertNotNull(branch.getMetadata());
		metaDataExpanded = branchMetadataHelper.expandObjectValues(branch.getMetadata());
		assertTrue(metaDataExpanded.containsKey(INTERNAL_METADATA_KEY));
		Map<String, String> internalExpanded = (Map<String, String>) branchMetadataHelper.expandObjectValues(branch.getMetadata()).get(INTERNAL_METADATA_KEY);
		assertFalse(internalExpanded.containsKey(IntegrityService.INTEGRITY_ISSUE_METADATA_KEY));
		assertTrue(internalExpanded.containsKey("other_key"));
		assertTrue(metaDataExpanded.containsKey("existingConfig"));

	}
}
