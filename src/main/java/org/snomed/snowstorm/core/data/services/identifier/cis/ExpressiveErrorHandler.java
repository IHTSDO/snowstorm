package org.snomed.snowstorm.core.data.services.identifier.cis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestClientResponseException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.stream.Collectors;

public class ExpressiveErrorHandler extends DefaultResponseErrorHandler {

	private Logger logger = LoggerFactory.getLogger(getClass());

	@Override
	public void handleError(ClientHttpResponse response) {
		// Recover error code and message from response
		int statusCode = 0;
		String statusText = "";
		String errMsg;
		try {
			HttpStatus httpStatus = response.getStatusCode();
			statusCode = httpStatus.value();
			statusText = httpStatus.getReasonPhrase();
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.getBody()))) {
				errMsg = reader.lines().collect(Collectors.joining("\n"));
			}
		} catch (IOException ignored) {
			errMsg = "Unable to recover failure reason";
		}

		logger.info("Got REST client error {} - {}", statusCode, statusText);

		throw new RestClientResponseException(errMsg, statusCode, statusText, null, null, null);
	}
}
