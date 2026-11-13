package org.snomed.snowstorm.core.data.services;

import io.kaicode.elasticvc.api.BranchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.core.data.domain.Concept;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;
import java.util.Stack;

import static org.junit.jupiter.api.Assertions.*;

@TestPropertySource(properties = "snowstorm.branch-labelled-event.message.enabled=true")
class BranchLabelledEventServiceTest extends AbstractTest {
	private static final Stack<Map<String, Object>> messagesReceived = new Stack<>();

	@Autowired
	private BranchService branchService;

	@Autowired
	private BranchMergeService branchMergeService;

	@Autowired
	private ConceptService conceptService;

	@BeforeEach
	void clearMessages() {
		messagesReceived.clear();
	}

	@JmsListener(destination = "${jms.queue.prefix}.branch.labelled.event")
	void messageConsumer(Map<String, Object> message) {
		messagesReceived.push(message);
	}

	@Test
	void sendsMessageOnPromotion() throws Exception {
		// given
		String project = branchService.create("MAIN/projectA").getPath();
		String task = branchService.create("MAIN/projectA/taskA").getPath();
		addBranchLabel(project);

		// Author content
		conceptService.create(new Concept(), task);

		// Promote task to project
		branchMergeService.mergeBranchSync(task, project, null);

		// Assert
		Map<String, Object> message = waitForMessage();
		assertNotNull(message, "Expected JMS message for promotion");
		assertEquals(project, message.get("branchPath"));
		assertEquals(task, message.get("sourceBranchPath"));
		assertEquals("PROMOTION", message.get("commitType"));
		assertEquals("translation", message.get("labels"));
	}

	@Test
	void sendsMessageOnRebase() throws Exception {
		// given
		String project = branchService.create("MAIN/projectB").getPath();
		addBranchLabel(project);

		// Author content
		conceptService.create(new Concept(), MAIN);

		// Rebase main onto project
		branchMergeService.mergeBranchSync(MAIN, project, null);

		// Assert
		Map<String, Object> message = waitForMessage();
		assertNotNull(message, "Expected JMS message for rebase");
		assertEquals(project, message.get("branchPath"));
		assertEquals(MAIN, message.get("sourceBranchPath"));
		assertEquals("REBASE", message.get("commitType"));
		assertEquals("translation", message.get("labels"));
	}

	@Test
	void doesNotSendMessageWhenNoLabel() throws Exception {
		// given
		String project = branchService.create("MAIN/projectC").getPath();
		String task = branchService.create("MAIN/projectC/taskC").getPath();

		// Author content
		conceptService.create(new Concept(), task);

		// Promote task to project
		branchMergeService.mergeBranchSync(task, project, null);

		// Assert
		assertNull(waitForMessageWithTimeout(5));
	}

	private Map<String, Object> waitForMessage() throws InterruptedException {
		return waitForMessageWithTimeout(20);
	}

	private Map<String, Object> waitForMessageWithTimeout(int maxWaitSeconds) throws InterruptedException {
		int waited = 0;
		while (messagesReceived.isEmpty() && waited < maxWaitSeconds) {
			Thread.sleep(1_000);
			waited++;
		}
		return messagesReceived.isEmpty() ? null : messagesReceived.pop();
	}

	private void addBranchLabel(String branchPath) {
		branchService.updateMetadata(branchPath, Map.of(BranchMetadataKeys.LABELS, java.util.List.of("translation")));
	}
}
