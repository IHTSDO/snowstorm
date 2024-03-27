package org.snomed.snowstorm.core.data.services.traceability;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Lazy
public class TraceabilityConsumer {

	@Value("${jms.queue.prefix}")
	private String jmsQueuePrefix;

	@Value("${activemq.max.message.concept-activities}")
	private int maxConceptActiviesPerMessage;

	@Autowired
	private JmsTemplate jmsTemplate;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public void accept(Activity activity) {
		if (activity.getChanges().size() <= maxConceptActiviesPerMessage) {
			jmsTemplate.convertAndSend(jmsQueuePrefix + ".traceability", activity);
		}
		else {
			sendInBatches(activity);
		}
	}

	/**
	 * A large activity object (with too many changes) should be messaged in batches.
	 * @param activity
	 */
	void sendInBatches(Activity activity) {
		int changeListSize = activity.getChanges().size();
		List<List<Activity.ConceptActivity>> chunkedList = Lists.partition(activity.getChanges(), maxConceptActiviesPerMessage);
		logger.info("Chunking {} concept activities into {} batches (of max size {})", changeListSize, chunkedList.size(), maxConceptActiviesPerMessage);

		for (List<Activity.ConceptActivity> conceptActivities : chunkedList) {
			Activity activityChunk = new Activity(activity.getUserId(), activity.getBranchPath(),
					activity.getCommitTimestamp(), activity.getSourceBranch(), activity.getActivityType());
			activityChunk.setChanges(conceptActivities);
			jmsTemplate.convertAndSend(jmsQueuePrefix + ".traceability", activityChunk);
		}
	}
}