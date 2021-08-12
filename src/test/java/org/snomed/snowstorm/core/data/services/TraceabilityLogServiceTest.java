package org.snomed.snowstorm.core.data.services;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.domain.Branch;
import io.kaicode.elasticvc.domain.Commit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.core.data.domain.Concepts;
import org.snomed.snowstorm.core.data.domain.Description;
import org.snomed.snowstorm.core.data.domain.Relationship;
import org.snomed.snowstorm.core.data.services.pojo.PersistedComponents;
import org.snomed.snowstorm.core.data.services.traceability.Activity;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.snomed.snowstorm.core.data.services.traceability.Activity.ActivityType.PROMOTION;
import static org.snomed.snowstorm.core.data.services.traceability.Activity.ActivityType.REBASE;

class TraceabilityLogServiceTest extends AbstractTest {

	@Autowired
	private TraceabilityLogService traceabilityLogService;

	@Autowired
	private ConceptService conceptService;

	@Autowired
	private BranchService branchService;

	@Autowired
	private BranchMergeService branchMergeService;

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
	void createDeleteConcept() throws ServiceException, InterruptedException {
		assertNull(getTraceabilityActivityWithTimeout(5));

		Concept concept = conceptService.create(new Concept().addFSN("New concept"), MAIN);

		Activity activity = getTraceabilityActivity();
		assertEquals(1, activity.getChangesMap().size());
		final String conceptId = concept.getConceptId();
		assertTrue(activity.getChangesMap().containsKey(conceptId));
		final Activity.ConceptActivity createActivity = activity.getChangesMap().get(conceptId);
		assertEquals(3, createActivity.getComponentChanges().size(), createActivity.getComponentChanges().toString());

		// Add description
		concept.addDescription(new Description("Another")
				.addAcceptability(Concepts.US_EN_LANG_REFSET, Concepts.PREFERRED_CONSTANT)
		);
		concept = conceptService.update(concept, MAIN);
		activity = getTraceabilityActivity();
		assertEquals(1, activity.getChangesMap().size());
		final Set<Activity.ComponentChange> componentChangesAddDesc = activity.getChanges().iterator().next().getComponentChanges();
		assertEquals(2, componentChangesAddDesc.size());
		assertNull(getTraceabilityActivityWithTimeout(5));

		// Test update with no change logs no traceability
		concept = simulateRestTransfer(concept);
		concept = conceptService.update(concept, MAIN);
		assertNull(getTraceabilityActivityWithTimeout(5));

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
		assertEquals(Collections.singleton(new Activity.ComponentChange(Activity.ComponentType.RELATIONSHIP, Long.parseLong(Concepts.INFERRED_RELATIONSHIP),
						relationshipId, Activity.ChangeType.CREATE, true)),
				activity.getChangesMap().get(conceptId).getComponentChanges());

		// Update concept with no change
		conceptService.update(concept, MAIN);
		activity = getTraceabilityActivityWithTimeout(2);// Shorter timeout here because we know the test JMS broker is up and we don't expect a message to come.
		assertNull(activity, "No concept changes so no traceability commit.");

		// Delete description
		final Optional<Description> desc = concept.getDescriptions().stream().filter(description1 -> description1.getTerm().equals("Another")).findFirst();
		assertTrue(desc.isPresent());
		concept.getDescriptions().remove(desc.get());
		conceptService.update(concept, MAIN);
		activity = getTraceabilityActivity();
		assertEquals(1, activity.getChangesMap().size());
		final Set<Activity.ComponentChange> componentChangesDeleteDesc = activity.getChanges().iterator().next().getComponentChanges();
		assertEquals(2, componentChangesDeleteDesc.size());

		conceptService.deleteConceptAndComponents(conceptId, MAIN, false);
		activity = getTraceabilityActivity();
		assertNotNull(activity);
		assertEquals(1, activity.getChangesMap().size());
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
	}

	@Test
	void testDeltaImportWithNoChanges() {
		final ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
		listAppender.start();
		((Logger) LoggerFactory.getLogger(TraceabilityLogService.class)).addAppender(listAppender);
		traceabilityLogService.setEnabled(true);
		final Commit commit = new Commit(new Branch("MAIN/Delta"), Commit.CommitType.CONTENT, null, null);
		commit.setSourceBranchPath("MAIN");
		commit.getBranch().getMetadata().putString("importType", "Delta");
		traceabilityLogService.preCommitCompletion(commit);
		final List<ILoggingEvent> logsList = listAppender.list;
		assertEquals("Skipping traceability because there was no traceable change for commit {} at {}.", logsList.get(0).getMessage());
		assertEquals(Level.INFO, logsList.get(0).getLevel());
		listAppender.stop();
	}

	@Test
	void testDeltaImportWithOneAdditionChange() throws InterruptedException {
		final Commit commit = new Commit(branchService.create("MAIN/RF2DeltaImport"), Commit.CommitType.CONTENT, null, null);
		final PersistedComponents persistedComponents =
				PersistedComponents.builder()
						.withPersistedConcepts(Collections.singleton(new Concept("3311481044").addFSN("Test FSN")))
						.withPersistedDescriptions(Collections.singleton(
								new Description("8635753033", 1, true, "900000000000012033", "3311481044", "en", Concepts.SYNONYM, "Test term", "900000000000448022")))
						.build();
		traceabilityLogService.logActivity(null, commit, persistedComponents, false, Activity.ActivityType.CONTENT_CHANGE);
		final Activity activity = getTraceabilityActivityWithTimeout(2);
		assertNotNull(activity);
		assertEquals(1, activity.getChangesMap().size());
		assertTrue(activity.getChangesMap().containsKey("3311481044"));
	}

	@Test
	void testDeltaImportWithTwoAdditionChange() throws InterruptedException {
		final Commit commit = new Commit(branchService.create("MAIN/RF2DeltaImport"), Commit.CommitType.CONTENT, null, null);
		final PersistedComponents persistedComponents =
				PersistedComponents.builder()
								   .withPersistedConcepts(Arrays.asList(new Concept("3311481044").addFSN("Test FSN"), new Concept("3311483055").addFSN("Test FSN2")))
								   .withPersistedDescriptions(Arrays.asList(new Description("8635753033", 1, true, "900000000000012033", "3311481044",
																									"en", Concepts.SYNONYM, "Test term", "900000000000448022"),
																			new Description("8635753033", 1, true, "900000000000012033", "3311483055",
																							"en", Concepts.SYNONYM, "Test term", "900000000000448022"))).build();
		traceabilityLogService.logActivity(null, commit, persistedComponents, false, Activity.ActivityType.CONTENT_CHANGE);
		final Activity activity = getTraceabilityActivityWithTimeout(2);
		assertNotNull(activity);
		assertEquals(2, activity.getChangesMap().size());
		assertTrue(activity.getChangesMap().containsKey("3311481044"));
		assertTrue(activity.getChangesMap().containsKey("3311483055"));
	}
}
