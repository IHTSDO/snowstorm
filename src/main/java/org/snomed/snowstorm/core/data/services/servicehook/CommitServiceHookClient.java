package org.snomed.snowstorm.core.data.services.servicehook;

import io.kaicode.elasticvc.api.CommitListener;
import io.kaicode.elasticvc.domain.Branch;
import io.kaicode.elasticvc.domain.Commit;
import org.ihtsdo.sso.integration.SecurityUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Service
public class CommitServiceHookClient implements CommitListener {

	private final RestTemplate restTemplate;

	private final Logger logger = LoggerFactory.getLogger(getClass());
	private final String serviceUrl;
	private final boolean failIfError;
	private final boolean blockPromotion;

	public CommitServiceHookClient(@Value("${service-hook.commit.url}") String serviceUrl,
								   @Value("${service-hook.commit.fail-if-error:true}") String failIfError,
								   @Value("${service-hook.commit.block-promotion-if-error:false}") String blockPromotion) {
		this.serviceUrl = serviceUrl;
		this.failIfError = Boolean.parseBoolean(failIfError);
		this.blockPromotion = Boolean.parseBoolean(blockPromotion);
		if (!StringUtils.isEmpty(serviceUrl)) {
			final RestTemplateBuilder builder = new RestTemplateBuilder()
					.rootUri(serviceUrl);
			restTemplate = builder.build();
		} else {
			logger.info("CommitServiceHookClient is muted as service url not configured.");
			restTemplate = null;
		}

		if (restTemplate != null) {
			if (this.failIfError) {
				logger.info("Commits will fail if external system cannot be reached.");
			} else {
				logger.info("Commits will not fail if external system cannot be reached.");
			}

			if (this.blockPromotion) {
				logger.info("Promotions will be blocked if an error is encountered.");
			} else {
				logger.info("Promotions will not be blocked if an error is encountered.");
			}
		}
	}

	@Override
	public void preCommitCompletion(Commit commit) throws IllegalStateException {
		if (restTemplate == null) {
			return;
		}

		try {
			String authenticationToken = SecurityUtil.getAuthenticationToken();
			HttpHeaders httpHeaders = buildHttpHeaders(authenticationToken);
			logRequest(commit, authenticationToken, commit.getBranch());
			ResponseEntity<?> responseEntity = restTemplate.postForEntity("/integration/snowstorm/commit",
					new HttpEntity<>(new CommitInformation(commit), httpHeaders), Void.class);
		} catch (RestClientException e) {
			logger.error("Commit service hook failed for branch {}, commit {}, url {}, cookie {}",
					commit.getBranch().getPath(), commit.getTimepoint().getTime(), serviceUrl,
					obfuscateToken(authenticationToken), e);
		}
	}

	private HttpHeaders buildHttpHeaders(String authenticationToken) {
		HttpHeaders httpHeaders = new HttpHeaders();
		httpHeaders.setContentType(MediaType.APPLICATION_JSON);
		httpHeaders.add(HttpHeaders.COOKIE, authenticationToken);

		return httpHeaders;
	}

	private void logRequest(Commit commit, String authenticationToken, Branch branch) {
		Commit.CommitType commitType = commit.getCommitType();
		logger.info("CommitType {} being sent to external system.", commitType);
		logger.debug("CommitType: {}. Source Branch: {}. Target Branch: {}", commitType, commit.getSourceBranchPath(), branch.getPath());
		logger.trace("Commit: {}", commit);
		logger.trace("Branch: {}", branch);
		logger.trace("Cookie: {}", obfuscateToken(authenticationToken));
		logger.trace("Service URL: {}", serviceUrl);
	}

	private Object obfuscateToken(String authenticationToken) {
		if (authenticationToken != null && !authenticationToken.isEmpty()) {
			if (authenticationToken.contains("=")) {
				return authenticationToken.substring(0, authenticationToken.indexOf("=") + 4) + "...";
			} else {
				return authenticationToken.substring(0, 4) + "...";
			}
		}
		return null;
	}
}
