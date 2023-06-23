package org.snomed.snowstorm.core.data.services.classification;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.activemq.command.ActiveMQTextMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.core.data.domain.classification.ClassificationStatus;
import org.snomed.snowstorm.core.data.services.classification.pojo.ClassificationStatusResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

import javax.jms.JMSException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Component
@ConditionalOnProperty(value = "classification-service.job.status.use-jms", havingValue = "true")
public class JMSListenerClassificationStatusService implements ClassificationStatusService {
	private static final Logger LOGGER = LoggerFactory.getLogger(JMSListenerClassificationStatusService.class);

	private final ObjectMapper objectMapper = new ObjectMapper();
	private final Map<String, ClassificationStatusResponse> classificationStatusChanges = Collections.synchronizedMap(new HashMap<>());

	public JMSListenerClassificationStatusService(@Value("${classification-service.message.status.destination}") String messageStatusDestination) {
		if (messageStatusDestination == null || messageStatusDestination.isEmpty()) {
			throw new IllegalStateException("No destination specified");
		}

		LOGGER.info("Classification statuses will be retrieved via JMS.");
	}

	@JmsListener(destination = "${classification-service.message.status.destination}")
	void messageConsumer(ActiveMQTextMessage activeMQTextMessage) throws JMSException, JsonProcessingException {
		try {
			ClassificationStatusResponse response = objectMapper.readValue(activeMQTextMessage.getText(), ClassificationStatusResponse.class);
			classificationStatusChanges.put(response.getId(), response);
		} catch (JsonParseException | JsonMappingException e) {
			LOGGER.error("Failed to parse message. Message: {}.", activeMQTextMessage.getText());
		}
	}

	@Override
	public Optional<ClassificationStatusResponse> getStatusChange(String classificationId) {
		ClassificationStatusResponse value = classificationStatusChanges.get(classificationId);
		if (value != null) {
			ClassificationStatus status = value.getStatus();
			if (ClassificationStatus.SCHEDULED != status && ClassificationStatus.RUNNING != status) {
				LOGGER.debug("Classification '{}' is '{}' and will be removed from internal cache.", classificationId, status);
				classificationStatusChanges.remove(classificationId);
			}
		}

		return Optional.ofNullable(value);
	}
}
