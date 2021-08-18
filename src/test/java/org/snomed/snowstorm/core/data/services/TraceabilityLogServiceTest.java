package org.snomed.snowstorm.core.data.services;

import io.kaicode.elasticvc.api.BranchService;
import org.ihtsdo.otf.snomedboot.ReleaseImportException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.snomed.otf.snomedboot.testutil.ZipUtil;
import org.snomed.snowstorm.AbstractTest;
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

		assertNull(getTraceabilityActivityWithTimeout(2), "There should be no traceability when creating a code system version.");
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
	void promote() throws ServiceException, InterruptedException {
		branchService.create("MAIN/A");
		conceptService.create(new Concept().addFSN("New concept"), "MAIN/A");
		clearActivities();

		branchMergeService.mergeBranchSync("MAIN/A", "MAIN", Collections.emptyList());

		Activity activity = getTraceabilityActivity();
		assertEquals(PROMOTION, activity.getActivityType());
		assertEquals("MAIN", activity.getBranchPath());
		assertEquals("MAIN/A", activity.getSourceBranch());
		assertTrue(activity.getChanges().isEmpty());
	}

	@Autowired
	private ImportService importService;

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

	private String toString(Set<Activity.ComponentChange> componentChanges) {
		List<Activity.ComponentChange> changes = new ArrayList<>(componentChanges);
		changes.sort(Comparator.comparing(Activity.ComponentChange::getComponentType)
				.thenComparing(Activity.ComponentChange::getComponentSubType)
				.thenComparing(Activity.ComponentChange::getChangeType));
		return changes.toString().replaceAll("componentId='[0-9a-z\\-]*'", "componentId='x'");
	}

}
