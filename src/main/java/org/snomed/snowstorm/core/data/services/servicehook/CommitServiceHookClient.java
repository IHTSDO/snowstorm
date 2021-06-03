package org.snomed.snowstorm.core.data.services.servicehook;

import io.kaicode.elasticvc.api.CommitListener;
import io.kaicode.elasticvc.domain.Commit;
import org.apache.http.entity.ContentType;
import org.ihtsdo.sso.integration.SecurityUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Service
public class CommitServiceHookClient implements CommitListener {

	private final RestTemplate restTemplate;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private static final HttpHeaders JSON_TYPE_HEADER = new HttpHeaders();
	static {
		JSON_TYPE_HEADER.setContentType(MediaType.APPLICATION_JSON);
	}

	public CommitServiceHookClient(@Value("${service-hook.commit.url}") String serviceUrl) {

		if (!StringUtils.isEmpty(serviceUrl)) {
			final RestTemplateBuilder builder = new RestTemplateBuilder()
					.rootUri(serviceUrl);
			restTemplate = builder.build();
		} else {
			restTemplate = null;
		}
	}

	@Override
	public void preCommitCompletion(Commit commit) throws IllegalStateException {
		if (restTemplate == null) {
			return;
		}

		try {
			final HttpHeaders httpHeaders = new HttpHeaders();
			httpHeaders.setContentType(MediaType.APPLICATION_JSON);
			httpHeaders.add(HttpHeaders.COOKIE, SecurityUtil.getAuthenticationToken());
			restTemplate.postForEntity("/snowstorm-integration/commit",
					new HttpEntity<>(new CommitInformation(commit), httpHeaders), Void.class);
		} catch (RestClientException e) {
			logger.warn("Commit service hook failed for branch {}, commit {}.", commit.getBranch().getPath(), commit.getTimepoint().getTime(), e);
		}
	}
}
