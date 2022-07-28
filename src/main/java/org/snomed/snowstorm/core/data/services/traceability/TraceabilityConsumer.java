package org.snomed.snowstorm.core.data.services.traceability;

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
		logger.info("Number of changes (concept activities) is {} and is larger than max ({}).", changeListSize, maxConceptActiviesPerMessage );
		int idx = 0;

		while (idx <= activity.getChanges().size() -1 ) {
			logger.info("Sending Changes batch with start index {}.", idx);
			List<Activity.ConceptActivity> changes = activity.getChanges().subList(idx, Math.min(idx + maxConceptActiviesPerMessage, changeListSize));
			Activity activitySlice = new Activity(activity.getUserId(), activity.getBranchPath(),
			activity.getCommitTimestamp(), activity.getSourceBranch(), activity.getActivityType(), false);
			activitySlice.setChanges(changes);
			jmsTemplate.convertAndSend(jmsQueuePrefix + ".traceability", activitySlice);
			idx += maxConceptActiviesPerMessage;
		}
	}
}