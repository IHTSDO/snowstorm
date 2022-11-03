package org.snomed.snowstorm.core.data.services;

import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.domain.Branch;
import org.ihtsdo.otf.snomedboot.ReleaseImportException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.snomed.otf.snomedboot.testutil.ZipUtil;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.config.Config;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.data.services.traceability.Activity;
import org.snomed.snowstorm.core.data.services.traceability.TraceabilityLogService;
import org.snomed.snowstorm.core.rf2.RF2Type;
import org.snomed.snowstorm.core.rf2.rf2import.ImportService;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.snomed.snowstorm.core.data.domain.Concepts.ISA;
import static org.snomed.snowstorm.core.data.domain.Concepts.PRIMITIVE;
import static org.snomed.snowstorm.core.data.services.traceability.Activity.ActivityType.*;

class TraceabilityLogServiceTest extends AbstractTest {

	@Autowired
	private TraceabilityLogService traceabilityLogService;

	@Autowired
	private ConceptService conceptService;

	@Autowired
	private BranchService branchService;

	@Autowired
	private BranchMergeService branchMergeService;

	@Autowired
	private CodeSystemService codeSystemService;

	@Autowired
	private ImportService importService;

	@Autowired
	private ReferenceSetMemberService referenceSetMemberService;

	@Autowired
	private CodeSystemUpgradeService codeSystemUpgradeService;

	@Autowired
	private RelationshipService relationshipService;

	private boolean traceabilityOriginallyEnabled;

	@BeforeEach
	void setup() {
		traceabilityOriginallyEnabled = traceabilityLogService.isEnabled();
		// Temporarily enable traceability if not already enabled in the test context
		traceabilityLogService.setEnabled(true);
	}

	@AfterEach
	void tearDown() {
		// Restore test context traceability switch
		traceabilityLogService.setEnabled(traceabilityOriginallyEnabled);
	}

	@Test
	void createUpdateDeleteConcept() throws ServiceException, InterruptedException {
		assertNull(getTraceabilityActivityWithTimeout(2));

		Concept concept = conceptService.create(new Concept().addFSN("New concept"), MAIN);

		Activity activity = getTraceabilityActivity();
		assertEquals(1, activity.getChangesMap().size());
		final String conceptId = concept.getConceptId();
		assertTrue(activity.getChangesMap().containsKey(conceptId));
		assertEquals("[ComponentChange{componentType=CONCEPT, componentSubType=null, componentId='x', changeType=CREATE, effectiveTimeNull=true}, " +
						"ComponentChange{componentType=DESCRIPTION, componentSubType=900000000000003001, componentId='x', changeType=CREATE, effectiveTimeNull=true}, " +
						"ComponentChange{componentType=REFERENCE_SET_MEMBER, componentSubType=900000000000509007, componentId='x', changeType=CREATE, effectiveTimeNull=true}]",
				toString(activity.getChangesMap().get(conceptId).getComponentChanges()));

		// Add description
		concept.addDescription(new Description("Another")
				.addAcceptability(Concepts.US_EN_LANG_REFSET, Concepts.PREFERRED_CONSTANT)
		);
		concept = conceptService.update(concept, MAIN);
		activity = getTraceabilityActivity();
		assertEquals(1, activity.getChangesMap().size());
		final Set<Activity.ComponentChange> componentChangesAddDesc = activity.getChanges().iterator().next().getComponentChanges();
		assertEquals(2, componentChangesAddDesc.size());
		assertEquals("[ComponentChange{componentType=DESCRIPTION, componentSubType=900000000000013009, componentId='x', changeType=CREATE, effectiveTimeNull=true}, " +
						"ComponentChange{componentType=REFERENCE_SET_MEMBER, componentSubType=900000000000509007, componentId='x', changeType=CREATE, effectiveTimeNull=true}]",
				toString(activity.getChangesMap().get(conceptId).getComponentChanges()));
		assertNull(getTraceabilityActivityWithTimeout(2));

		// Test update with no change logs no traceability
		concept = simulateRestTransfer(concept);
		concept = conceptService.update(concept, MAIN);
		assertNull(getTraceabilityActivityWithTimeout(2));

		// Add axiom
		concept.addAxiom(new Relationship(Concepts.ISA, Concepts.CLINICAL_FINDING));
		concept = conceptService.update(concept, MAIN);
		activity = getTraceabilityActivity();
		Map<String, Activity.ConceptActivity> changes = activity.getChangesMap();
		assertEquals(1, changes.size());
		Activity.ConceptActivity conceptActivity = changes.get(conceptId);
		Set<Activity.ComponentChange> componentChanges = conceptActivity.getComponentChanges();
		assertEquals(1, componentChanges.size(), componentChanges::toString);
		Activity.ComponentChange axiomChange = componentChanges.iterator().next();
		assertEquals(Activity.ComponentType.REFERENCE_SET_MEMBER, axiomChange.getComponentType());
		assertEquals(concept.getClassAxioms().iterator().next().getAxiomId(), axiomChange.getComponentId());

		// Add inferred relationship
		concept.addRelationship(new Relationship(Concepts.ISA, Concepts.CLINICAL_FINDING).setInferred(true));
		conceptService.update(concept, MAIN);
		final String relationshipId = concept.getRelationships().iterator().next().getRelationshipId();
		activity = getTraceabilityActivity();
		assertEquals(1, activity.getChangesMap().size());
		assertEquals(new Activity.ComponentChange(Activity.ComponentType.RELATIONSHIP, Long.parseLong(Concepts.INFERRED_RELATIONSHIP),
						relationshipId, Activity.ChangeType.CREATE, true).toString(),
				activity.getChangesMap().get(conceptId).getComponentChanges().iterator().next().toString());

		// Update concept with no change
		conceptService.update(concept, MAIN);
		activity = getTraceabilityActivityWithTimeout(2);// Shorter timeout here because we know the test JMS broker is up and we don't expect a message to come.
		assertNull(activity, "No concept changes so no traceability commit.");

		// Update description
		Optional<Description> desc = concept.getDescriptions().stream().filter(description1 -> description1.getTerm().equals("Another")).findFirst();
		assertTrue(desc.isPresent());
		desc.get().setCaseSignificanceId(Concepts.ENTIRE_TERM_CASE_SENSITIVE);
		conceptService.update(concept, MAIN);
		activity = getTraceabilityActivity();
		assertEquals(1, activity.getChangesMap().size());
		assertEquals("[ComponentChange{componentType=DESCRIPTION, componentSubType=900000000000013009, componentId='x', changeType=UPDATE, effectiveTimeNull=true}]",
				toString(activity.getChanges().iterator().next().getComponentChanges()));

		// Delete description
		desc = concept.getDescriptions().stream().filter(description1 -> description1.getTerm().equals("Another")).findFirst();
		assertTrue(desc.isPresent());
		concept.getDescriptions().remove(desc.get());
		conceptService.update(concept, MAIN);
		activity = getTraceabilityActivity();
		assertEquals(1, activity.getChangesMap().size());
		assertEquals("[ComponentChange{componentType=DESCRIPTION, componentSubType=900000000000013009, componentId='x', changeType=DELETE, effectiveTimeNull=true}, " +
						"ComponentChange{componentType=REFERENCE_SET_MEMBER, componentSubType=900000000000509007, componentId='x', changeType=DELETE, effectiveTimeNull=true}]",
				toString(activity.getChanges().iterator().next().getComponentChanges()));

		conceptService.deleteConceptAndComponents(conceptId, MAIN, false);
		activity = getTraceabilityActivity();
		assertNotNull(activity);
		assertEquals("[ComponentChange{componentType=CONCEPT, componentSubType=null, componentId='x', changeType=DELETE, effectiveTimeNull=true}, " +
						"ComponentChange{componentType=DESCRIPTION, componentSubType=900000000000003001, componentId='x', changeType=DELETE, effectiveTimeNull=true}, " +
						"ComponentChange{componentType=RELATIONSHIP, componentSubType=900000000000011006, componentId='x', changeType=DELETE, effectiveTimeNull=true}, " +
						"ComponentChange{componentType=REFERENCE_SET_MEMBER, componentSubType=733073007, componentId='x', changeType=DELETE, effectiveTimeNull=true}, " +
						"ComponentChange{componentType=REFERENCE_SET_MEMBER, componentSubType=900000000000509007, componentId='x', changeType=DELETE, effectiveTimeNull=true}]",
				toString(activity.getChanges().iterator().next().getComponentChanges()));
	}

	@Test
	void createConceptAndVersion() throws ServiceException, InterruptedException {
		assertNull(getTraceabilityActivityWithTimeout(2));

		Concept concept = conceptService.create(new Concept().addFSN("New concept"), MAIN);

		Activity activity = getTraceabilityActivity();
		assertEquals(1, activity.getChangesMap().size());
		final String conceptId = concept.getConceptId();
		assertTrue(activity.getChangesMap().containsKey(conceptId));
		assertEquals("[ComponentChange{componentType=CONCEPT, componentSubType=null, componentId='x', changeType=CREATE, effectiveTimeNull=true}, " +
						"ComponentChange{componentType=DESCRIPTION, componentSubType=900000000000003001, componentId='x', changeType=CREATE, effectiveTimeNull=true}, " +
						"ComponentChange{componentType=REFERENCE_SET_MEMBER, componentSubType=900000000000509007, componentId='x', changeType=CREATE, effectiveTimeNull=true}]",
				toString(activity.getChangesMap().get(conceptId).getComponentChanges()));

		final CodeSystem codeSystem = codeSystemService.createCodeSystem(new CodeSystem("SNOMEDCT", MAIN));
		codeSystemService.createVersion(codeSystem, 20220131, "");

		// Assert versioned
		final Concept versionedConcept = conceptService.find(conceptId, MAIN);
		assertEquals(20220131, versionedConcept.getEffectiveTimeI());

		activity = getTraceabilityActivity();
		assertNotNull(activity);
		assertTrue(activity.getChangesMap().isEmpty());
		assertEquals(CREATE_CODE_SYSTEM_VERSION, activity.getActivityType());
	}

	@Test
	void createDeleteConceptOnChildBranch() throws ServiceException, InterruptedException {
		Concept concept = conceptService.create(new Concept().addFSN("New concept"), MAIN);

		Activity activity = getTraceabilityActivity();
		assertEquals(1, activity.getChangesMap().size());
		assertTrue(activity.getChangesMap().containsKey(concept.getConceptId()));

		// Add description
		concept.addDescription(new Description("another"));
		concept = conceptService.update(concept, MAIN);
		activity = getTraceabilityActivity();
		assertEquals(1, activity.getChangesMap().size());
		assertTrue(activity.getChangesMap().containsKey(concept.getConceptId()));

		// Add axiom
		concept.addAxiom(new Relationship(Concepts.ISA, Concepts.CLINICAL_FINDING));
		concept = conceptService.update(concept, MAIN);
		activity = getTraceabilityActivity();
		Map<String, Activity.ConceptActivity> changes = activity.getChangesMap();
		assertEquals(1, changes.size());
		Activity.ConceptActivity conceptActivity = changes.get(concept.getConceptId());
		Set<Activity.ComponentChange> conceptChanges = conceptActivity.getComponentChanges();
		assertEquals(1, conceptChanges.size(), conceptChanges::toString);
		Activity.ComponentChange axiomChange = conceptChanges.iterator().next();
		assertEquals(Activity.ComponentType.REFERENCE_SET_MEMBER, axiomChange.getComponentType());
		assertEquals(concept.getClassAxioms().iterator().next().getAxiomId(), axiomChange.getComponentId());

		// Add inferred relationship
		concept.addRelationship(new Relationship(Concepts.ISA, Concepts.CLINICAL_FINDING).setInferred(true));
		concept = conceptService.update(concept, MAIN);
		activity = getTraceabilityActivity();
		assertEquals(1, activity.getChangesMap().size());

		// Update concept with no change
		conceptService.update(concept, MAIN);
		activity = getTraceabilityActivityWithTimeout(5);// Shorter timeout here because we know the test JMS broker is up and we don't expect a message to come.
		assertNull(activity, "No concept changes so no traceability commit.");

		branchService.create("MAIN/A");

		conceptService.deleteConceptAndComponents(concept.getConceptId(), "MAIN/A", false);
		activity = getTraceabilityActivity();
		assertNotNull(activity);
		assertEquals(1, activity.getChangesMap().size());
		final Activity.ConceptActivity deleteActivity = activity.getChangesMap().get(concept.getConceptId());
		final Activity.ComponentChange componentChange = deleteActivity.getComponentChanges().iterator().next();
		assertEquals(Activity.ChangeType.DELETE, componentChange.getChangeType());
	}

	@Test
	void rebase() throws InterruptedException, ServiceException {
		branchService.create("MAIN/A");
		conceptService.create(new Concept().addFSN("New concept"), MAIN);
		clearActivities();

		branchMergeService.mergeBranchSync("MAIN", "MAIN/A", Collections.emptyList());

		Activity activity = getTraceabilityActivity();
		assertEquals(REBASE, activity.getActivityType());
		assertEquals("MAIN/A", activity.getBranchPath());
		assertEquals("MAIN", activity.getSourceBranch());
	}


	@Test
	void rebaseWithPublishedChangesFromParent() throws InterruptedException, ServiceException {
		// Create a concept on MAIN
		Concept concept = conceptService.create(new Concept().addFSN("New concept").addRelationship(ISA, Concepts.CLINICAL_FINDING), MAIN);

		// Versioning
		CodeSystem codeSystem = codeSystemService.createCodeSystem(new CodeSystem("SNOMEDCT", MAIN));
		codeSystemService.createVersion(codeSystem, 20220131, "");
		clearActivities();

		// Make inactivation on A and B for the same relationship
		branchService.create("MAIN/A");
		branchService.create("MAIN/B");
		concept = conceptService.find(concept.getId(), "MAIN/A");
		Relationship relationship = concept.getRelationships().iterator().next();
		relationship.setActive(false);
		conceptService.update(concept, "MAIN/A");
		conceptService.update(concept, "MAIN/B");

		// Promote A to MAIN and version
		branchMergeService.mergeBranchSync("MAIN/A", "MAIN", Collections.emptyList());
		codeSystemService.createVersion(codeSystem, 20220331, "");
		concept = conceptService.find(concept.getId(), "MAIN");
		relationship = concept.getRelationship(relationship.getRelationshipId());
		clearActivities();

		// Before rebase on B
		concept = conceptService.find(concept.getId(), "MAIN/B");
		relationship = concept.getRelationship(relationship.getRelationshipId());
		String internalIdOnB = relationship.getInternalId();

		// Rebase B with MAIN
		branchMergeService.mergeBranchSync("MAIN", "MAIN/B", Collections.emptyList());

		// Relationship should have effective time after rebase
		concept = conceptService.find(concept.getId(), "MAIN/B");
		relationship = concept.getRelationship(relationship.getRelationshipId());
		assertNotNull(relationship.getEffectiveTimeI());

		Activity activity = getTraceabilityActivity();
		assertEquals(REBASE, activity.getActivityType());
		assertNotNull(activity.getChanges());
		assertFalse(activity.getChanges().isEmpty());
		Activity.ConceptActivity conceptActivity = activity.getChanges().iterator().next();
		assertEquals(concept.getId(), conceptActivity.getConceptId());
		conceptActivity.getComponentChanges().forEach(System.out::println);
		assertEquals(1, conceptActivity.getComponentChanges().size());
		Activity.ComponentChange componentChange = conceptActivity.getComponentChanges().iterator().next();
		assertEquals(Activity.ComponentType.RELATIONSHIP, componentChange.getComponentType());
		assertEquals(relationship.getId(), componentChange.getComponentId());
		assertEquals(Activity.ChangeType.INACTIVATE, componentChange.getChangeType());
		assertTrue(componentChange.isEffectiveTimeNull());
		assertTrue(componentChange.isSuperseded());
	}

	@Test
	void promoteRebase() throws ServiceException, InterruptedException {
		branchService.create("MAIN/A");
		branchService.create("MAIN/B");
		conceptService.create(new Concept().addFSN("New concept"), "MAIN/A");
		clearActivities();

		branchMergeService.mergeBranchSync("MAIN/A", "MAIN", Collections.emptyList());

		Activity promotionActivity = getTraceabilityActivity();
		assertEquals(PROMOTION, promotionActivity.getActivityType());
		assertEquals("MAIN", promotionActivity.getBranchPath());
		assertEquals("MAIN/A", promotionActivity.getSourceBranch());
		assertTrue(promotionActivity.getChanges().isEmpty());

		branchMergeService.mergeBranchSync("MAIN", "MAIN/B", Collections.emptyList());

		Activity rebaseActivity = getTraceabilityActivity();
		assertEquals(REBASE, rebaseActivity.getActivityType());
		assertEquals("MAIN/B", rebaseActivity.getBranchPath());
		assertEquals("MAIN", rebaseActivity.getSourceBranch());
		final Collection<Activity.ConceptActivity> changes = rebaseActivity.getChanges();
		System.out.println(changes);
		assertTrue(changes.isEmpty());
	}

	@Test
	void rebaseWithAutomaticallyResolvedSynonymConflict() throws ServiceException, InterruptedException {
		final String conceptId = conceptService.create(new Concept().addFSN("New concept").addDescription(new Description("Some synonym")), "MAIN").getConceptId();
		final Concept concept = conceptService.find(conceptId, "MAIN");
		final Optional<Description> descriptionOptional = concept.getDescriptions().stream().filter(d -> d.getTypeId().equals(Concepts.SYNONYM)).findFirst();
		assertTrue(descriptionOptional.isPresent());
		final Description description = descriptionOptional.get();
		branchService.create("MAIN/A");
		branchService.create("MAIN/B");
		clearActivities();

		description.setCaseSignificanceId(Concepts.ENTIRE_TERM_CASE_SENSITIVE);
		concept.addDescription(description);
		assertEquals(2, concept.getDescriptions().size());
		conceptService.update(concept, "MAIN/A");

		description.setCaseSignificanceId(Concepts.INITIAL_CHARACTER_CASE_INSENSITIVE);
		concept.addDescription(description);
		assertEquals(2, concept.getDescriptions().size());
		conceptService.update(concept, "MAIN/B");

		branchMergeService.mergeBranchSync("MAIN/A", "MAIN", Collections.emptyList());

		Activity promotionActivity = getTraceabilityActivity();
		assertEquals(PROMOTION, promotionActivity.getActivityType());
		assertEquals("MAIN", promotionActivity.getBranchPath());
		assertEquals("MAIN/A", promotionActivity.getSourceBranch());
		assertTrue(promotionActivity.getChanges().isEmpty());

		branchMergeService.mergeBranchSync("MAIN", "MAIN/B", Collections.emptyList());

		Activity rebaseActivity = getTraceabilityActivity();
		assertEquals(REBASE, rebaseActivity.getActivityType());
		assertEquals("MAIN/B", rebaseActivity.getBranchPath());
		assertEquals("MAIN", rebaseActivity.getSourceBranch());
		final Collection<Activity.ConceptActivity> changes = rebaseActivity.getChanges();
		assertEquals(1, changes.size());
		Activity.ConceptActivity conceptActivity = changes.iterator().next();
		Activity.ComponentChange componentChange = conceptActivity.getComponentChanges().iterator().next();
		assertEquals(Activity.ComponentType.DESCRIPTION, componentChange.getComponentType());
		assertEquals(description.getId(), componentChange.getComponentId());
		assertEquals(Activity.ChangeType.UPDATE, componentChange.getChangeType());
	}

	@Test
	void rebaseVersionedWithManuallyChosenLeftHandSide() throws ServiceException, InterruptedException {
		final String conceptId = conceptService.create(new Concept().addFSN("New concept"), "MAIN").getConceptId();
		final Concept concept = conceptService.find(conceptId, "MAIN");
		final Description description = concept.getDescriptions().iterator().next();
		branchService.create("MAIN/A");
		branchService.create("MAIN/B");
		clearActivities();

		description.setCaseSignificanceId(Concepts.ENTIRE_TERM_CASE_SENSITIVE);
		concept.addDescription(description);
		assertEquals(1, concept.getDescriptions().size());
		conceptService.update(concept, "MAIN/A");

		description.setCaseSignificanceId(Concepts.INITIAL_CHARACTER_CASE_INSENSITIVE);
		concept.addDescription(description);
		assertEquals(1, concept.getDescriptions().size());
		conceptService.update(concept, "MAIN/B");

		branchMergeService.mergeBranchSync("MAIN/A", "MAIN", Collections.emptyList());

		Activity promotionActivity = getTraceabilityActivity();
		assertEquals(PROMOTION, promotionActivity.getActivityType());
		assertEquals("MAIN", promotionActivity.getBranchPath());
		assertEquals("MAIN/A", promotionActivity.getSourceBranch());
		assertTrue(promotionActivity.getChanges().isEmpty());

		final CodeSystem codeSystem = codeSystemService.createCodeSystem(new CodeSystem("SNOMEDCT", MAIN));
		codeSystemService.createVersion(codeSystem, 20220131, "");

		branchMergeService.mergeBranchSync("MAIN", "MAIN/B", Collections.singleton(conceptService.find(conceptId, "MAIN")));

		Activity rebaseActivity = getTraceabilityActivity();
		assertEquals(REBASE, rebaseActivity.getActivityType());
		assertEquals("MAIN/B", rebaseActivity.getBranchPath());
		assertEquals("MAIN", rebaseActivity.getSourceBranch());
		final Collection<Activity.ConceptActivity> changes = rebaseActivity.getChanges();
		assertEquals(1, changes.size());
		final Activity.ConceptActivity activity = changes.iterator().next();
		assertEquals(3, activity.getComponentChanges().size());

		assertEquals("[ComponentChange{componentType=CONCEPT, componentSubType=null, componentId='x', changeType=UPDATE, effectiveTimeNull=false}, " +
						"ComponentChange{componentType=DESCRIPTION, componentSubType=900000000000003001, componentId='x', changeType=UPDATE, effectiveTimeNull=false}, " +
						"ComponentChange{componentType=REFERENCE_SET_MEMBER, componentSubType=900000000000509007, componentId='x', changeType=UPDATE, effectiveTimeNull=false}]",
				toString(activity.getComponentChanges()));
	}

	@Test
	void rebaseVersionedWithManuallyChosenRightHandSide() throws ServiceException, InterruptedException {
		final String conceptId = conceptService.create(new Concept().addFSN("New concept"), "MAIN").getConceptId();
		final Concept concept = conceptService.find(conceptId, "MAIN");
		final Description description = concept.getDescriptions().iterator().next();
		branchService.create("MAIN/A");
		branchService.create("MAIN/B");
		clearActivities();

		description.setCaseSignificanceId(Concepts.ENTIRE_TERM_CASE_SENSITIVE);
		concept.addDescription(description);
		assertEquals(1, concept.getDescriptions().size());
		conceptService.update(concept, "MAIN/A");

		description.setCaseSignificanceId(Concepts.INITIAL_CHARACTER_CASE_INSENSITIVE);
		concept.addDescription(description);
		assertEquals(1, concept.getDescriptions().size());
		conceptService.update(concept, "MAIN/B");

		branchMergeService.mergeBranchSync("MAIN/A", "MAIN", Collections.emptyList());

		Activity promotionActivity = getTraceabilityActivity();
		assertEquals(PROMOTION, promotionActivity.getActivityType());
		assertEquals("MAIN", promotionActivity.getBranchPath());
		assertEquals("MAIN/A", promotionActivity.getSourceBranch());
		assertTrue(promotionActivity.getChanges().isEmpty());

		final CodeSystem codeSystem = codeSystemService.createCodeSystem(new CodeSystem("SNOMEDCT", MAIN));
		codeSystemService.createVersion(codeSystem, 20220131, "");

		branchMergeService.mergeBranchSync("MAIN", "MAIN/B", Collections.singleton(conceptService.find(conceptId, "MAIN/B")));

		Activity rebaseActivity = getTraceabilityActivity();
		assertEquals(REBASE, rebaseActivity.getActivityType());
		assertEquals("MAIN/B", rebaseActivity.getBranchPath());
		assertEquals("MAIN", rebaseActivity.getSourceBranch());
		final Collection<Activity.ConceptActivity> changes = rebaseActivity.getChanges();
		assertEquals(1, changes.size());
		final Activity.ConceptActivity activity = changes.iterator().next();
		assertEquals(3, activity.getComponentChanges().size());

		assertEquals("[ComponentChange{componentType=CONCEPT, componentSubType=null, componentId='x', changeType=UPDATE, effectiveTimeNull=false}, " +
						"ComponentChange{componentType=DESCRIPTION, componentSubType=900000000000003001, componentId='x', changeType=UPDATE, effectiveTimeNull=true}, " +
						"ComponentChange{componentType=REFERENCE_SET_MEMBER, componentSubType=900000000000509007, componentId='x', changeType=UPDATE, effectiveTimeNull=false}]",
				toString(activity.getComponentChanges()));
	}

	@Test
	void testDeltaImport() throws IOException, ReleaseImportException, InterruptedException {
		branchService.create("MAIN/A");
		java.io.File rf2Archive = ZipUtil.zipDirectoryRemovingCommentsAndBlankLines("src/main/resources/dummy-snomed-content/RF2Release/Delta");
		final String importJob = importService.createJob(RF2Type.DELTA, "MAIN/A", false, false);
		clearActivities();

		importService.importArchive(importJob, new FileInputStream(rf2Archive));

		Activity activity = getTraceabilityActivity();
		assertEquals(CONTENT_CHANGE, activity.getActivityType());
		assertEquals("MAIN/A", activity.getBranchPath());
		assertEquals(1, activity.getChanges().size());
	}

	@Test
	void testCreateRefsetMember() throws InterruptedException {
		// Given
		branchService.create("MAIN/A");

		// When
		referenceSetMemberService.createMember("MAIN/A", new ReferenceSetMember(Concepts.CORE_MODULE, Concepts.REFSET_SIMPLE, Concepts.CLINICAL_FINDING));

		// Then
		final Activity activity = getTraceabilityActivity();
		assertEquals(CONTENT_CHANGE, activity.getActivityType());
		assertEquals("MAIN/A", activity.getBranchPath());
		assertEquals(1, activity.getChanges().size());
		final Activity.ConceptActivity conceptActivity = activity.getChanges().iterator().next();
		assertEquals("[ComponentChange{componentType=REFERENCE_SET_MEMBER, componentSubType=446609009, componentId='x', changeType=CREATE, effectiveTimeNull=true}]",
				toString(conceptActivity.getComponentChanges()));
	}

	@Test
	void testRebaseWithChangesUpdatedByOtherProject() throws Exception {
		// Create a concept with relationships and version in MAIN
		conceptService.create(new Concept(ISA).setDefinitionStatusId(PRIMITIVE).addFSN("Is a (attribute)"), "MAIN");
		final String disease = "10000001";
		conceptService.create(new Concept(disease).addFSN("Disease (disorder)"), "MAIN");
		Concept concept = conceptService.create(new Concept().addFSN("Test concept").addRelationship(0, ISA, disease), "MAIN");
		assertEquals(1, concept.getRelationships().size());

		final String conceptId = concept.getConceptId();
		final CodeSystem codeSystem = codeSystemService.createCodeSystem(new CodeSystem("SNOMEDCT", MAIN));
		codeSystemService.createVersion(codeSystem, 20220131, "");

		// Check concept on MAIN after versioning
		concept = conceptService.find(conceptId, "MAIN");
		assertEquals(1, concept.getRelationships().size());
		Relationship relationship = concept.getRelationships().iterator().next();
		assertTrue(relationship.isActive());
		assertTrue(relationship.isReleased());
		assertEquals("20220131", relationship.getEffectiveTime());

		final String relationshipId = relationship.getRelationshipId();

		// Create project A and inactivate the relationship created above
		Branch projectA = branchService.create("MAIN/A");
		concept = conceptService.find(conceptId, projectA.getPath());
		assertEquals("20220131", concept.getEffectiveTime());
		concept.getRelationship(relationshipId).setActive(false);
		conceptService.update(concept, projectA.getPath());

		// Re-activate relationship on project A
		concept = conceptService.find(conceptId, projectA.getPath());
		concept.getRelationship(relationshipId).setActive(true);
		conceptService.update(concept, projectA.getPath());

		// Relationship gets the effective time restored
		concept = conceptService.find(conceptId, projectA.getPath());
		assertEquals("20220131", concept.getEffectiveTime());
		assertEquals("20220131", concept.getRelationship(relationshipId).getEffectiveTime());

		// Create project B and make inactivation for the same relationship as above
		Branch projectB = branchService.create("MAIN/B");
		concept = conceptService.find(conceptId, projectB.getPath());
		concept.getRelationship(relationshipId).setActive(false);
		conceptService.update(concept, projectB.getPath());

		// Promote project B to MAIN
		branchMergeService.mergeBranchSync(projectB.getPath(), "MAIN", Collections.singleton(conceptService.find(conceptId, projectB.getPath())));

		// Rebase project A from MAIN without manual merge concepts
		// Inferred relationship changes don't trigger merge review see details in mergeBranchSync() method
		// The version on project A is ended and the version from MAIN is chosen by default
		branchMergeService.mergeBranchSync("MAIN", projectA.getPath(), Collections.emptyList());

		// Check the effectiveTimeNull is set to true in saved relationship after rebase
		concept = conceptService.find(conceptId, projectA.getPath());
		assertNull(concept.getRelationship(relationshipId).getEffectiveTime());

		Activity rebaseActivity = getTraceabilityActivity();
		assertEquals(REBASE, rebaseActivity.getActivityType());
		assertEquals("MAIN/A", rebaseActivity.getBranchPath());
		assertEquals("MAIN", rebaseActivity.getSourceBranch());
		assertFalse(rebaseActivity.getChanges().isEmpty());

		final Collection<Activity.ConceptActivity> changes = rebaseActivity.getChanges();
		assertFalse(changes.isEmpty());
		Activity.ConceptActivity conceptActivity = changes.iterator().next();
		assertEquals(conceptId, conceptActivity.getConceptId());
		assertEquals(1,  conceptActivity.getComponentChanges().size());
		Activity.ComponentChange componentChange = conceptActivity.getComponentChanges().iterator().next();
		assertEquals(relationshipId, componentChange.getComponentId());
		assertEquals(Activity.ChangeType.UPDATE, componentChange.getChangeType());
		assertTrue(componentChange.isSuperseded());
		assertFalse(componentChange.isEffectiveTimeNull());
	}

	@Test
	void testRebaseWithChangeSupersededByOtherProject() throws Exception {
		// Create a concept with relationships and version in MAIN
		conceptService.create(new Concept(ISA).setDefinitionStatusId(PRIMITIVE).addFSN("Is a (attribute)"), "MAIN");
		final String disease = "10000001";
		conceptService.create(new Concept(disease).addFSN("Disease (disorder)"), "MAIN");
		Concept concept = conceptService.create(new Concept().addFSN("Test concept").addRelationship(0, ISA, disease), "MAIN");
		assertEquals(1, concept.getRelationships().size());

		final String conceptId = concept.getConceptId();
		final CodeSystem codeSystem = codeSystemService.createCodeSystem(new CodeSystem("SNOMEDCT", MAIN));
		codeSystemService.createVersion(codeSystem, 20220131, "");

		// Check concept on MAIN after versioning
		concept = conceptService.find(conceptId, "MAIN");
		assertEquals(1, concept.getRelationships().size());
		Relationship relationship = concept.getRelationships().iterator().next();
		assertTrue(relationship.isActive());
		assertTrue(relationship.isReleased());
		assertEquals("20220131", relationship.getEffectiveTime());

		final String relationshipId = relationship.getRelationshipId();

		// Create project A and inactivate the relationship created above
		Branch projectA = branchService.create("MAIN/A");
		concept = conceptService.find(conceptId, projectA.getPath());
		assertEquals("20220131", concept.getEffectiveTime());
		concept.getRelationship(relationshipId).setActive(false);
		conceptService.update(concept, projectA.getPath());

		concept = conceptService.find(conceptId, projectA.getPath());
		assertNull(concept.getRelationship(relationshipId).getEffectiveTime());

		// Create project B and make the same inactivation as above and promote changes to MAIN
		Branch projectB = branchService.create("MAIN/B");
		concept = conceptService.find(conceptId, projectB.getPath());
		concept.getRelationship(relationshipId).setActive(false);
		conceptService.update(concept, projectB.getPath());

		// Promote project B to MAIN
		branchMergeService.mergeBranchSync(projectB.getPath(), "MAIN", Collections.singleton(conceptService.find(conceptId, projectB.getPath())));

		// Rebase project A from MAIN without manual merge concepts
		// Inferred relationship changes don't trigger merge review see details in mergeBranchSync() method
		// The version on project A is ended and the version from MAIN is chosen by default
		branchMergeService.mergeBranchSync("MAIN", projectA.getPath(), Collections.emptyList());

		// Check the effectiveTimeNull is set to true in saved relationship after rebase
		concept = conceptService.find(conceptId, projectA.getPath());
		assertNull(concept.getRelationship(relationshipId).getEffectiveTime());

		Activity rebaseActivity = getTraceabilityActivity();
		assertEquals(REBASE, rebaseActivity.getActivityType());
		assertEquals("MAIN/A", rebaseActivity.getBranchPath());
		assertEquals("MAIN", rebaseActivity.getSourceBranch());
		assertFalse(rebaseActivity.getChanges().isEmpty());
		final Collection<Activity.ConceptActivity> changes = rebaseActivity.getChanges();
		assertFalse(changes.isEmpty());
		Activity.ConceptActivity conceptActivity = changes.iterator().next();
		assertEquals(conceptId, conceptActivity.getConceptId());
		Activity.ComponentChange componentChange = conceptActivity.getComponentChanges().iterator().next();
		assertEquals(relationshipId, componentChange.getComponentId());
		assertEquals(Activity.ChangeType.INACTIVATE, componentChange.getChangeType());
		assertTrue(componentChange.isEffectiveTimeNull());
		assertTrue(componentChange.isSuperseded());
	}

	@Test
	void testExtensionUpgradingDependencyWithSameComponentEdited() throws ServiceException, InterruptedException {
		Concept concept;
		CodeSystem codeSystem;
		String extMain = "MAIN/SNOMEDCT-TEST";
		Relationship relationship;

		// Create International Concepts
		concept = conceptService.create(
				new Concept().addFSN("Vehicle (vehicle)").addRelationship(ISA, Concepts.SNOMEDCT_ROOT),
				MAIN
		);
		String vehicleId = concept.getConceptId();
		concept = conceptService.create(
				// Published with wrong modelling
				new Concept().addFSN("Car (vehicle)").addRelationship(ISA, Concepts.SNOMEDCT_ROOT),
				MAIN
		);
		String carId = concept.getConceptId();
		String relationshipToBeInactivated = concept.getRelationships().iterator().next().getRelationshipId();

		// Version International
		codeSystem = codeSystemService.createCodeSystem(new CodeSystem("SNOMEDCT", MAIN));
		codeSystemService.createVersion(codeSystem, 20220131, "20220131");

		// Create Extension
		codeSystemService.createCodeSystem(new CodeSystem("SNOMEDCT-TEST", extMain));
		concept = conceptService.create(
				new Concept().addFSN("Extension module (core metadata concept)").addRelationship(ISA, Concepts.MODULE),
				extMain
		);
		String extModule = concept.getConceptId();
		branchService.updateMetadata(extMain, Map.of(Config.DEFAULT_MODULE_ID_KEY, extModule));

		// International fixes modelling (through classification)
		concept = conceptService.find(carId, MAIN);
		concept.getRelationships().iterator().next().setActive(false).updateEffectiveTime();
		concept.getRelationships().add(new Relationship(ISA, vehicleId));
		concept = conceptService.update(concept, MAIN);
		List<String> intRelationships = getRelationshipIds(concept);

		// Version International
		codeSystem = codeSystemService.find("SNOMEDCT");
		codeSystemService.createVersion(codeSystem, 20220731, "20220731");

		// Extension fixes modelling (through classification)
		concept = conceptService.find(carId, extMain);
		concept.getRelationships().iterator().next().setActive(false).updateEffectiveTime();
		concept.getRelationships().add(new Relationship(ISA, vehicleId));
		concept = conceptService.update(concept, extMain);
		List<String> extRelationships = getRelationshipIds(concept);

		// Assert before upgrade
		Set<Activity.ComponentChange> componentChanges = getTraceabilityActivity().getChanges().iterator().next().getComponentChanges();
		for (Activity.ComponentChange componentChange : componentChanges) {
			String componentId = componentChange.getComponentId();
			if (relationshipToBeInactivated.equals(componentId)) {
				assertEquals(Activity.ChangeType.INACTIVATE, componentChange.getChangeType());
			} else {
				assertEquals(Activity.ChangeType.CREATE, componentChange.getChangeType());
			}
		}

		// Extension upgrades to International's July content
		codeSystem = codeSystemService.find("SNOMEDCT-TEST");
		codeSystemUpgradeService.upgrade(codeSystem, 20220731, false);

		// Extension ends up with International version of inactivated Relationship post upgrade
		relationship = relationshipService.findRelationship(extMain, relationshipToBeInactivated);
		assertTrue(relationship.isReleased());
		assertEquals(Concepts.CORE_MODULE, relationship.getModuleId());
		assertEquals(20220731, relationship.getReleasedEffectiveTime());

		// Assert after upgrade
		Activity.ComponentChange changedComponent = getTraceabilityActivity().getChanges().iterator().next().getComponentChanges().iterator().next();
		Activity.ChangeType changeType = changedComponent.getChangeType();
		String componentId = changedComponent.getComponentId();
		boolean superseded = changedComponent.isSuperseded();

		assertEquals(componentId, relationshipToBeInactivated);
		assertEquals(Activity.ChangeType.INACTIVATE, changeType);
		assertTrue(superseded);
	}

	private List<String> getRelationshipIds(Concept concept) {
		List<String> relationships = new ArrayList<>();
		for (Relationship relationship : concept.getRelationships()) {
			relationships.add(relationship.getRelationshipId());
		}

		return relationships;
	}

	private String toString(Set<Activity.ComponentChange> componentChanges) {
		List<Activity.ComponentChange> changes = new ArrayList<>(componentChanges);
		changes.sort(Comparator.comparing(Activity.ComponentChange::getComponentType)
				.thenComparing(Activity.ComponentChange::getComponentSubType)
				.thenComparing(Activity.ComponentChange::getChangeType));
		return changes.toString().replaceAll("componentId='[0-9a-z\\-]*'", "componentId='x'");
	}

}
