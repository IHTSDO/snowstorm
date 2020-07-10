package org.snomed.snowstorm.core.data.services;

import io.kaicode.elasticvc.domain.Branch;
import io.kaicode.elasticvc.domain.Commit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.TestConfig;
import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.core.data.domain.Concepts;
import org.snomed.snowstorm.core.data.domain.Description;
import org.snomed.snowstorm.core.data.domain.Relationship;
import org.snomed.snowstorm.core.data.services.traceability.Activity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import static junit.framework.TestCase.assertNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = TestConfig.class)
class TraceabilityLogServiceTest extends AbstractTest {

	@Autowired
	private TraceabilityLogService traceabilityLogService;

	@Autowired
	private ConceptService conceptService;

	private static Stack<Activity> activitiesLogged = new Stack<>();

	private boolean testContextTraceabilityEnabled;

	@BeforeEach
	void setup() {
		testContextTraceabilityEnabled = traceabilityLogService.isEnabled();
		// Temporarily enable traceability if not already enabled in the test context
		traceabilityLogService.setEnabled(true);
	}

	@AfterEach
	void tearDown() {
		// Restore test context traceability switch
		traceabilityLogService.setEnabled(testContextTraceabilityEnabled);
	}

	@JmsListener(destination = "${jms.queue.prefix}.traceability")
	void messageConsumer(Activity activity) {
		System.out.println("Got activity " + activity.getCommitComment());
		activitiesLogged.push(activity);
	}

	@Test
	void createDeleteConcept() throws ServiceException, InterruptedException {
		Concept concept = conceptService.create(new Concept().addFSN("New concept"), MAIN);

		Activity activity = getActivity();
		assertEquals("Creating concept New concept", activity.getCommitComment());

		// Add description
		concept.addDescription(new Description("another"));
		conceptService.update(concept, MAIN);
		activity = getActivity();
		assertEquals("Updating concept New concept", activity.getCommitComment());
		assertEquals(1, activity.getChanges().size());

		// Add axiom
		concept.addAxiom(new Relationship(Concepts.ISA, Concepts.CLINICAL_FINDING));
		conceptService.update(concept, MAIN);
		activity = getActivity();
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
		activity = getActivity();
		assertEquals("Classified ontology.", activity.getCommitComment());
		assertEquals(1, activity.getChanges().size());

		// Update concept with no change
		conceptService.update(concept, MAIN);
		activity = getActivityWithTimeout(10);// Shorter timeout here because we know the test JMS broker is up and we don't expect a message to come.
		assertNull("No concept changes so no traceability commit.", activity);

		conceptService.deleteConceptAndComponents(concept.getConceptId(), MAIN, false);
		activity = getActivity();
		assertNotNull(activity);
		assertEquals("Deleting concept New concept", activity.getCommitComment());
		assertEquals(1, activity.getChanges().size());
	}

	public Activity getActivity() throws InterruptedException {
		return getActivityWithTimeout(20);
	}

	public Activity getActivityWithTimeout(int maxWait) throws InterruptedException {
		int waited = 0;
		while (activitiesLogged.isEmpty() && waited < maxWait) {
			Thread.sleep(1_000);
			waited++;
		}
		if (activitiesLogged.isEmpty()) {
			return null;
		}
		return activitiesLogged.pop();
	}

	@Test
	void createCommitCommentRebase() {
		Commit commit = new Commit(new Branch("MAIN/A"), Commit.CommitType.REBASE, null, null);
		commit.setSourceBranchPath("MAIN");
		assertEquals("kkewley performed merge of MAIN to MAIN/A", traceabilityLogService.createCommitComment("kkewley", commit, Collections.emptySet(), false));
	}

	@Test
	void createCommitCommentPromotion() {
		Commit commit = new Commit(new Branch("MAIN"), Commit.CommitType.PROMOTION, null, null);
		commit.setSourceBranchPath("MAIN/A");
		assertEquals("kkewley performed merge of MAIN/A to MAIN", traceabilityLogService.createCommitComment("kkewley", commit, Collections.emptySet(), false));
	}
}
