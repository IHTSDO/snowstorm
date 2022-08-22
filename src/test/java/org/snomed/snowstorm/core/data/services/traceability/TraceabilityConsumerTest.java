package org.snomed.snowstorm.core.data.services.traceability;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import org.springframework.jms.core.JmsTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = TraceabilityConsumer.class)
@TestPropertySource(properties = {"jms.queue.prefix=TEST123", "activemq.max.message.concept-activities=100"})
public class TraceabilityConsumerTest {

	private final String QUEUE = "DefaultQueue";
	private final int MAX_CHANGES = 5;


	@Mock
	private JmsTemplate jmsTemplate;

	@InjectMocks
	private TraceabilityConsumer t;

	@Before
	public void beforeTests() {
		ReflectionTestUtils.setField(t, "jmsQueuePrefix", QUEUE);
		ReflectionTestUtils.setField(t, "maxConceptActiviesPerMessage", MAX_CHANGES);
	}

	@Test
	public void testSingleSend() {
		t.accept(createActivity());
		verify(jmsTemplate, times(1)).convertAndSend(anyString(), any(Activity.class));
	}

	@Test
	public void testSend_times3() {
		Activity a = createActivity();
		for (int i = 0; i < 12; i++) {
			a.addConceptActivity(String.valueOf(i));
		}
		t.accept(a);
		verify(jmsTemplate, times(3)).convertAndSend(anyString(), any(Activity.class));
	}

	private Activity createActivity() {
		return new Activity("user", "MAIN/BLA", 999999999999l, null, Activity.ActivityType.CONTENT_CHANGE);
	}

}
