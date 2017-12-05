package org.snomed.snowstorm.core.data.services.identifier.cis;

import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestClientException;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.stream.Collectors;

public class ExpressiveErrorHandler extends DefaultResponseErrorHandler {
	@Override
	public void handleError(ClientHttpResponse response) {
		//In the event of an error, our response may have some text to explain
		//what went wrong.  Attempt to recover this.
		String statusMsg = "HttpStatus: ";
		String errMsg = "Unable to recover failure reason";
		try {
			statusMsg += response.getStatusCode();
			InputStreamReader isr = new InputStreamReader(response.getBody());
			errMsg = new BufferedReader(isr).lines().collect(Collectors.joining("\n"));
		} catch (Exception e){}
		
		throw new RestClientException(statusMsg + ", " + errMsg);
	}
}
