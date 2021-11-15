package org.snomed.snowstorm.core.data.services.classification;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.activemq.command.ActiveMQTextMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.core.data.services.classification.pojo.ClassificationStatusResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

import javax.jms.JMSException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Component
@ConditionalOnProperty(value = "classification-service.job.status.use-jms", havingValue = "true")
public class JMSListenerClassificationStatusService implements ClassificationStatusService {
	private static final Logger LOGGER = LoggerFactory.getLogger(JMSListenerClassificationStatusService.class);

	private final ObjectMapper objectMapper = new ObjectMapper();
	private final Map<String, ClassificationStatusResponse> classificationStatusChanges = Collections.synchronizedMap(new HashMap<>());

	public JMSListenerClassificationStatusService(@Value("${classification-service.job.status.location}") String responseMessageQueue) {
		if (responseMessageQueue == null || responseMessageQueue.isEmpty()) {
			throw new IllegalStateException("No queue specified");
		}
	}

	@JmsListener(destination = "${classification-service.job.status.location}")
	void messageConsumer(ActiveMQTextMessage activeMQTextMessage) throws JMSException, JsonProcessingException {
		try {
			ClassificationStatusResponse response = objectMapper.readValue(activeMQTextMessage.getText(), ClassificationStatusResponse.class);
			classificationStatusChanges.put(response.getId(), response);
		} catch (JsonParseException | JsonMappingException e) {
			LOGGER.error("Failed to parse message. Message: {}.", activeMQTextMessage.getText());
		}
	}

	@Override
	public ClassificationStatusResponse getStatusChange(String classificationId) {
		return classificationStatusChanges.remove(classificationId);
	}
}
