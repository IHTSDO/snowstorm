package org.snomed.snowstorm.core.data.services.traceability;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = TraceabilityConsumer.class)
@TestPropertySource(properties = {"jms.queue.prefix=TEST123", "activemq.max.message.concept-activities=3"})
class TraceabilityConsumerTest {

	@MockBean
	private JmsTemplate jmsTemplate;

	@Autowired
	private TraceabilityConsumer traceabilityConsumer;

	@Test
	void testSingleSend() {
		traceabilityConsumer.accept(createActivity());
		verify(jmsTemplate, times(1)).convertAndSend(anyString(), any(Activity.class));
	}

	@Test
	void testSend_times3() {
		Activity activity = createActivity();
		for (int i = 0; i < 12; i++) {
			activity.addConceptActivity(String.valueOf(i));
		}
		Assertions.assertEquals(12, activity.getChanges().size());
		traceabilityConsumer.accept(activity);
		verify(jmsTemplate, times(4)).convertAndSend(anyString(), any(Activity.class));
	}

	private Activity createActivity() {
		return new Activity("user", "MAIN/BLA", 999999999999l, null, Activity.ActivityType.CONTENT_CHANGE);
	}
}
