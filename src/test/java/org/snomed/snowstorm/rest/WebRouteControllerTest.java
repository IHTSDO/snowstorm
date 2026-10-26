package org.snomed.snowstorm.rest;

import org.junit.jupiter.api.Test;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.TestConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Regression test for MAINT-3110. Content negotiation may legitimately serve this error as JSON or
 * XML depending on the client's Accept header, but whichever format is chosen, the offending part of
 * the uri must always come back through that format's own escaping - never as raw, unescaped markup
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = TestConfig.class)
@TestPropertySource(properties = "uri.dereferencing.prefix=http://example.com/")
// Boot 4 no longer auto-registers TestRestTemplate; it needs this annotation.
@AutoConfigureTestRestTemplate
class WebRouteControllerTest extends AbstractTest {

	@LocalServerPort
	private int port;

	@Autowired
	private TestRestTemplate restTemplate;

	@Test
	void testErrorResponseNeverReflectsRawMarkupFromUri() {
		String uriParam = "http://example.com/t<tag>marker</tag>";
		URI url = UriComponentsBuilder.fromUriString("http://localhost:" + port + "/web-route")
				.queryParam("uri", uriParam)
				.build()
				.encode()
				.toUri();

		HttpHeaders requestHeaders = new HttpHeaders();
		requestHeaders.set(HttpHeaders.ACCEPT, "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");

		ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(requestHeaders), String.class);

		assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
		assertNotNull(response.getHeaders().getContentType(), "Response must declare a content type");

		String body = response.getBody();
		assertNotNull(body);
		assertFalse(body.contains("<tag>marker</tag>"),
				"The uri's markup must come back escaped by the negotiated format, never verbatim");
	}

}
