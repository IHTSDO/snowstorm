package org.snomed.snowstorm.core.data.services.classification;

import org.snomed.snowstorm.core.data.services.classification.pojo.ClassificationStatusResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
@ConditionalOnProperty(value = "classification-service.job.status.use-jms", havingValue = "false")
public class RestTemplateClassificationStatusService implements ClassificationStatusService {

	private final RestTemplate restTemplate;

	public RestTemplateClassificationStatusService(@Value("${classification-service.url}") String serviceUrl,
												   @Value("${classification-service.username}") String serviceUsername,
												   @Value("${classification-service.password}") String servicePassword,
												   @Value("${classification-service.job.status.location}") String responseMessageQueue) {
		if (responseMessageQueue != null && !responseMessageQueue.isEmpty()) {
			throw new IllegalStateException("Queue specified but not required for this strategy.");
		}

		restTemplate = new RestTemplateBuilder()
				.rootUri(serviceUrl)
				.basicAuthentication(serviceUsername, servicePassword)
				.build();
	}

	@Override
	public ClassificationStatusResponse getStatusChange(String classificationId) {
		return restTemplate.getForObject("/classifications/{classificationId}", ClassificationStatusResponse.class, classificationId);
	}
}
