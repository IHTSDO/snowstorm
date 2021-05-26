package org.snomed.snowstorm.core.data.services;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.google.common.collect.ImmutableMap;
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

import static junit.framework.TestCase.assertNull;
import static org.junit.Assert.*;

class TraceabilityLogServiceTest extends AbstractTest {

	@Autowired
	private TraceabilityLogService traceabilityLogService;

	@Autowired
	private ConceptService conceptService;

	@Autowired
	private BranchService branchService;


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
		Concept concept = conceptService.create(new Concept().addFSN("New concept"), MAIN);

		Activity activity = getTraceabilityActivity();
		assertEquals("Creating concept New concept", activity.getCommitComment());

		// Add description
		concept.addDescription(new Description("another"));
		conceptService.update(concept, MAIN);
		activity = getTraceabilityActivity();
		assertEquals("Updating concept New concept", activity.getCommitComment());
		assertEquals(1, activity.getChanges().size());

		// Add axiom
		concept.addAxiom(new Relationship(Concepts.ISA, Concepts.CLINICAL_FINDING));
		conceptService.update(concept, MAIN);
		activity = getTraceabilityActivity();
		assertEquals("Updating concept New concept", activity.getCommitComment());
		Map<String, Activity.ConceptActivity> changes = activity.getChanges();
		assertEquals(1, changes.size());
		Activity.ConceptActivity conceptActivity = changes.get(concept.getConceptId());
		Set<Activity.ComponentChange> conceptChanges = conceptActivity.getChanges();
		assertEquals(1, conceptChanges.size());
		Activity.ComponentChange axiomChange = conceptChanges.iterator().next();
		assertEquals("OWLAxiom", axiomChange.getComponentType());
		assertEquals(concept.getClassAxioms().iterator().next().getAxiomId(), axiomChange.getComponentId());

		// Add inferred relationship
		concept.addRelationship(new Relationship(Concepts.ISA, Concepts.CLINICAL_FINDING).setInferred(true));
		conceptService.update(concept, MAIN);
		activity = getTraceabilityActivity();
		assertEquals("Classified ontology.", activity.getCommitComment());
		assertEquals(1, activity.getChanges().size());

		// Update concept with no change
		conceptService.update(concept, MAIN);
		activity = getTraceabilityActivityWithTimeout(10);// Shorter timeout here because we know the test JMS broker is up and we don't expect a message to come.
		assertNull("No concept changes so no traceability commit.", activity);

		conceptService.deleteConceptAndComponents(concept.getConceptId(), MAIN, false);
		activity = getTraceabilityActivity();
		assertNotNull(activity);
		assertEquals("Deleting concept New concept", activity.getCommitComment());
		assertEquals(1, activity.getChanges().size());
	}

	@Test
	void createDeleteConceptOnChildBranch() throws ServiceException, InterruptedException {
		Concept concept = conceptService.create(new Concept().addFSN("New concept"), MAIN);

		Activity activity = getTraceabilityActivity();
		assertEquals("Creating concept New concept", activity.getCommitComment());

		// Add description
		concept.addDescription(new Description("another"));
		conceptService.update(concept, MAIN);
		activity = getTraceabilityActivity();
		assertEquals("Updating concept New concept", activity.getCommitComment());
		assertEquals(1, activity.getChanges().size());

		// Add axiom
		concept.addAxiom(new Relationship(Concepts.ISA, Concepts.CLINICAL_FINDING));
		conceptService.update(concept, MAIN);
		activity = getTraceabilityActivity();
		assertEquals("Updating concept New concept", activity.getCommitComment());
		Map<String, Activity.ConceptActivity> changes = activity.getChanges();
		assertEquals(1, changes.size());
		Activity.ConceptActivity conceptActivity = changes.get(concept.getConceptId());
		Set<Activity.ComponentChange> conceptChanges = conceptActivity.getChanges();
		assertEquals(1, conceptChanges.size());
		Activity.ComponentChange axiomChange = conceptChanges.iterator().next();
		assertEquals("OWLAxiom", axiomChange.getComponentType());
		assertEquals(concept.getClassAxioms().iterator().next().getAxiomId(), axiomChange.getComponentId());

		// Add inferred relationship
		concept.addRelationship(new Relationship(Concepts.ISA, Concepts.CLINICAL_FINDING).setInferred(true));
		conceptService.update(concept, MAIN);
		activity = getTraceabilityActivity();
		assertEquals("Classified ontology.", activity.getCommitComment());
		assertEquals(1, activity.getChanges().size());

		// Update concept with no change
		conceptService.update(concept, MAIN);
		activity = getTraceabilityActivityWithTimeout(10);// Shorter timeout here because we know the test JMS broker is up and we don't expect a message to come.
		assertNull("No concept changes so no traceability commit.", activity);

		branchService.create("MAIN/A");

		conceptService.deleteConceptAndComponents(concept.getConceptId(), "MAIN/A", false);
		activity = getTraceabilityActivity();
		assertNotNull(activity);
		assertEquals("Deleting concept New concept", activity.getCommitComment());
		assertEquals(1, activity.getChanges().size());
	}

	@Test
	void createCommitCommentRebase() {
		Commit commit = new Commit(new Branch("MAIN/A"), Commit.CommitType.REBASE, null, null);
		commit.setSourceBranchPath("MAIN");
		assertEquals("kkewley performed merge of MAIN to MAIN/A", traceabilityLogService.createCommitComment("kkewley", commit, Collections.emptySet(), false, null));
	}

	@Test
	void createCommitCommentPromotion() {
		Commit commit = new Commit(new Branch("MAIN"), Commit.CommitType.PROMOTION, null, null);
		commit.setSourceBranchPath("MAIN/A");
		assertEquals("kkewley performed merge of MAIN/A to MAIN", traceabilityLogService.createCommitComment("kkewley", commit, Collections.emptySet(), false, null));
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
		assertEquals("Skipping traceability because there was no traceable change.", logsList.get(0).getMessage());
		assertEquals(Level.INFO, logsList.get(0).getLevel());
		listAppender.stop();
	}

	@Test
	void testDeltaImportWithOneAdditionChange() throws InterruptedException {
		final Commit commit = new Commit(branchService.create("MAIN/RF2DeltaImport"), Commit.CommitType.CONTENT, null, null);
		final PersistedComponents persistedComponents =
				PersistedComponents.newBuilder()
								   .withPersistedConcepts(Collections.singleton(new Concept("3311481044").addFSN("Test FSN")))
								   .withPersistedDescriptions(Collections.singleton(new Description("8635753033", 1, true, "900000000000012033", "3311481044",
																									"en", "900000000000013044", "Test term", "900000000000448022"))).build();
		traceabilityLogService.logActivity(null, commit, persistedComponents, false, "Delta");
		final Activity activity = getTraceabilityActivityWithTimeout(2);
		assertNotNull(activity);
		assertTrue(activity.getCommitComment().contains("RF2 Import - Updating concept Test FSN"));
	}

	@Test
	void testDeltaImportWithTwoAdditionChange() throws InterruptedException {
		final Commit commit = new Commit(branchService.create("MAIN/RF2DeltaImport"), Commit.CommitType.CONTENT, null, null);
		final PersistedComponents persistedComponents =
				PersistedComponents.newBuilder()
								   .withPersistedConcepts(Arrays.asList(new Concept("3311481044").addFSN("Test FSN"), new Concept("3311483055").addFSN("Test FSN2")))
								   .withPersistedDescriptions(Arrays.asList(new Description("8635753033", 1, true, "900000000000012033", "3311481044",
																									"en", "900000000000013044", "Test term", "900000000000448022"),
																			new Description("8635753033", 1, true, "900000000000012033", "3311483055",
																							"en", "900000000000013044", "Test term", "900000000000448022"))).build();
		traceabilityLogService.logActivity(null, commit, persistedComponents, false, "Delta");
		final Activity activity = getTraceabilityActivityWithTimeout(2);
		assertNotNull(activity);
		assertTrue(activity.getCommitComment().contains("RF2 Import - Bulk update to 2 concepts."));
	}
}
