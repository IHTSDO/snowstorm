package org.snomed.snowstorm.core.data.services;

import io.kaicode.elasticvc.api.CommitListener;
import io.kaicode.elasticvc.domain.Commit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

	@Value("${snowstorm.branch-labelled-event.message.enabled:false}")
	private boolean enabled;

	@Value("${jms.queue.prefix}")
	private String jmsQueuePrefix;

	private final JmsTemplate jmsTemplate;

	public BranchLabelledEventService(JmsTemplate jmsTemplate) {
		this.jmsTemplate = jmsTemplate;
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
		if (labels == null || labels.isEmpty()) {
			return;
		}

		if (labels.contains("translation")) {
			sendBranchLabelledEvent(commit, commitType, labels);
		}
	}

	private void sendBranchLabelledEvent(Commit commit, Commit.CommitType commitType, List<String> labels) {
		Map<String, String> message = new HashMap<>();
		message.put("branchPath", commit.getBranch().getPath());
		message.put("sourceBranchPath", commit.getSourceBranchPath());
		message.put("commitType", commitType.name());
		message.put("labels", String.join(",", labels));

		try {
			LOGGER.info("Sending branch labelled event for branch {} ({})", commit.getBranch().getPath(), commitType);
			jmsTemplate.convertAndSend(jmsQueuePrefix + QUEUE_SUFFIX, message);
		} catch (Exception e) {
			LOGGER.error("Failed to send branch labelled event for branch {}", commit.getBranch().getPath(), e);
		}
	}
}
