package org.snomed.snowstorm.core.data.services.traceability;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;

@Component
@Lazy
public class TraceabilityConsumer {

	@Value("${jms.queue.prefix}")
	private String jmsQueuePrefix;

	@Autowired
	private JmsTemplate jmsTemplate;

	public void accept(Activity activity) {
		jmsTemplate.convertAndSend(jmsQueuePrefix + ".traceability", activity);
	}

}
