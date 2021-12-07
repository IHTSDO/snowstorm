package org.snomed.snowstorm.core.data.services.classification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.core.data.services.classification.pojo.ClassificationStatusResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;

@Component
@ConditionalOnProperty(value = "classification-service.job.status.use-jms", havingValue = "false")
public class RestTemplateClassificationStatusService implements ClassificationStatusService {
	private static final Logger LOGGER = LoggerFactory.getLogger(RestTemplateClassificationStatusService.class);

	private final RestTemplate restTemplate;

	public RestTemplateClassificationStatusService(@Value("${classification-service.url}") String serviceUrl,
												   @Value("${classification-service.username}") String serviceUsername,
												   @Value("${classification-service.password}") String servicePassword,
												   @Value("${classification-service.message.status.destination}") String messageStatusDestination) {
		if (messageStatusDestination != null && !messageStatusDestination.isEmpty()) {
			throw new IllegalStateException("Destination specified but not required for this strategy.");
		}

		restTemplate = new RestTemplateBuilder()
				.rootUri(serviceUrl)
				.basicAuthentication(serviceUsername, servicePassword)
				.build();

		LOGGER.info("Classification statuses will be retrieved via REST.");
	}

	@Override
	public Optional<ClassificationStatusResponse> getStatusChange(String classificationId) {
		return Optional.ofNullable(restTemplate.getForObject("/classifications/{classificationId}", ClassificationStatusResponse.class, classificationId));
	}
}
