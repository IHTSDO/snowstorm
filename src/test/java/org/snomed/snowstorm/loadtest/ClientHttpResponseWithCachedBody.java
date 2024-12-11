package org.snomed.snowstorm.loadtest;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

public class ClientHttpResponseWithCachedBody implements ClientHttpResponse {

	private final ClientHttpResponse httpResponse;
	private final String body;

	public ClientHttpResponseWithCachedBody(ClientHttpResponse httpResponse, String body) {
		this.httpResponse = httpResponse;
		this.body = body;
	}

	@Override
	public HttpStatusCode getStatusCode() throws IOException {
		return httpResponse.getStatusCode();
	}

	@Override
	public String getStatusText() throws IOException {
		return httpResponse.getStatusText();
	}

	@Override
	public void close() {
		httpResponse.close();
	}

	@Override
	public InputStream getBody() throws IOException {
		return new ByteArrayInputStream(body.getBytes());
	}

	@Override
	public HttpHeaders getHeaders() {
		return httpResponse.getHeaders();
	}
}
