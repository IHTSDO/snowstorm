package org.snomed.snowstorm.core.data.services.servicehook;

import org.ihtsdo.sso.integration.SecurityUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
public class UpgradeServiceHookClient {

	private final RestTemplate restTemplate;

	private final Logger logger = LoggerFactory.getLogger(getClass());
	private final String serviceUrl;
	private boolean applicationReady = false;

	public UpgradeServiceHookClient(@Value("${service-hook.upgrade.url}") String serviceUrl) {
		this.serviceUrl = serviceUrl;
		if (StringUtils.hasLength(serviceUrl)) {
			final RestTemplateBuilder builder = new RestTemplateBuilder()
					.rootUri(serviceUrl);
			restTemplate = builder.build();
		} else {
			logger.info("UpgradeServiceHookClient is muted as service url not configured.");
			restTemplate = null;
		}
	}

	public void upgradeCompletion(String codeSystemShortName, String newDependantReleasePackage) throws IllegalStateException {
		if (restTemplate == null) {
			logger.info("restTemplate == null, upgrade completion done.");
			return;
		}

		// This class is used for communication with SRS (see configuration of serviceUrl).
		if (!applicationReady) {
			logger.info("Application still in startup, upgrade completion done.");
			return;
		}

		try {
			String authenticationToken = SecurityUtil.getAuthenticationToken();
			HttpHeaders httpHeaders = buildHttpHeaders(authenticationToken);
			logRequest(codeSystemShortName, authenticationToken, newDependantReleasePackage);
			Map requestBody = new HashMap();
			requestBody.put("codeSystemShortName", codeSystemShortName);
			requestBody.put("newDependantReleasePackage", newDependantReleasePackage);
			ResponseEntity<?> responseEntity = restTemplate.postForEntity("/integration/snowstorm/upgrade",
					new HttpEntity<>(requestBody, httpHeaders), Void.class);
			logger.info("External system returned HTTP status code {}.", responseEntity.getStatusCodeValue());
		} catch (RestClientException e) {
			// Cannot communicate with external system; perhaps lacking authentication or url configured incorrectly.
			logger.error("Cannot communicate with external system. Failure will be ignored.");
		}
	}

	private HttpHeaders buildHttpHeaders(String authenticationToken) {
		HttpHeaders httpHeaders = new HttpHeaders();
		httpHeaders.setContentType(MediaType.APPLICATION_JSON);
		httpHeaders.add(HttpHeaders.COOKIE, authenticationToken);

		return httpHeaders;
	}

	private void logRequest(String shortName, String authenticationToken, String newDependantReleasePackage) {
		logger.info("Upgrading Completion is being sent to external system.");
		logger.trace("Cody System ShortName: {}", shortName);
		logger.trace("New Dependant Release Package: {}", newDependantReleasePackage);
		logger.trace("Cookie: {}", obfuscateToken(authenticationToken));
		logger.trace("Service URL: {}", serviceUrl);
	}

	private Object obfuscateToken(String authenticationToken) {
		if (authenticationToken != null) {
			if (authenticationToken.contains("=")) {
				return authenticationToken.substring(0, authenticationToken.indexOf("=") + 4) + "...";
			} else {
				return authenticationToken.substring(0, 4) + "...";
			}
		}
		return null;
	}

	@EventListener(ApplicationReadyEvent.class)
	public void applicationIsReady() {
		applicationReady =true;
	}
}
