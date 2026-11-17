package org.snomed.snowstorm.core.data.services;

import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.api.CommitListener;
import io.kaicode.elasticvc.api.PathUtil;
import io.kaicode.elasticvc.domain.Branch;
import io.kaicode.elasticvc.domain.Commit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.rest.pojo.BranchLabelledEventRequest.EventType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class BranchLabelledEventService implements CommitListener {
	private static final Logger LOGGER = LoggerFactory.getLogger(BranchLabelledEventService.class);
	private static final String QUEUE_SUFFIX = ".branch.labelled.event";

	private final JmsTemplate jmsTemplate;
	private final BranchService branchService;

	@Value("${snowstorm.branch-labelled-event.message.enabled:false}")
	private boolean enabled;

	@Value("${jms.queue.prefix}")
	private String jmsQueuePrefix;

	public BranchLabelledEventService(JmsTemplate jmsTemplate, BranchService branchService) {
		this.jmsTemplate = jmsTemplate;
		this.branchService = branchService;
	}

	@Override
	public void preCommitCompletion(Commit commit) throws IllegalStateException {
		if (!enabled) {
			return;
		}

		// commit check
		Commit.CommitType commitType = commit.getCommitType();
		if (commitType != Commit.CommitType.PROMOTION && commitType != Commit.CommitType.REBASE) {
			return;
		}

		// label check
		List<String> labels = commit.getBranch().getMetadata().getList(BranchMetadataKeys.LABELS);
		if (labels == null || labels.isEmpty() || !labels.contains("translation")) {
			return;
		}

		// send event
		String eventType = commitType == Commit.CommitType.PROMOTION ? "PROMOTE" : commitType.name();
		sendEvent(commit.getBranch().getPath(), commit.getSourceBranchPath(), eventType, commitType.name(), labels);
	}

	public void handleEvent(String childBranchPath, EventType eventType) {
		if (!enabled) {
			return;
		}

		// branch check
		String parentPath = PathUtil.getParentPath(childBranchPath);
		if (parentPath == null) {
			return;
		}

		Branch parentBranch = branchService.findLatest(parentPath);
		if (parentBranch == null) {
			return;
		}

		// label check
		List<String> labels = parentBranch.getMetadata().getList(BranchMetadataKeys.LABELS);
		if (labels == null || labels.isEmpty() || !labels.contains("translation")) {
			return;
		}

		// send event
		sendEvent(childBranchPath, null, eventType.name(), null, labels);
	}

	private void sendEvent(String branchPath, String sourceBranchPath, String eventType, String commitType, List<String> labels) {
		Map<String, Object> branch = new HashMap<>();
		branch.put("branchPath", branchPath);
		branch.put("labels", String.join(",", labels));

		if (sourceBranchPath != null) {
			branch.put("sourceBranchPath", sourceBranchPath);
		}

		if (commitType != null) {
			branch.put("commitType", commitType);
		}

		Map<String, Object> event = new HashMap<>();
		event.put("event", eventType);
		event.put("branch", branch);

		try {
			LOGGER.info("Sending branch labelled {} event for branch {}", eventType, branchPath);
			jmsTemplate.convertAndSend(jmsQueuePrefix + QUEUE_SUFFIX, List.of(event));
		} catch (Exception e) {
			LOGGER.error("Failed to send branch labelled {} event for branch {}", eventType, branchPath, e);
		}
	}
}
