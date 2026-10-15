package org.snomed.snowstorm.rest;

import org.apache.tomcat.util.buf.EncodedSolidusHandling;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.tomcat.TomcatConnectorCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.*;

import java.io.IOException;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// TestConfig does not pick up SnowstormApplication's connector customizer, so %2F would otherwise be rejected by Tomcat.
@Import(SwaggerSecurityTest.DecodeEncodedSolidus.class)
class SwaggerSecurityTest extends AbstractControllerSecurityTest {

	@Test
	void swaggerIsAccessibleWithoutLogin() throws URISyntaxException {
		for (HttpHeaders headers : new HttpHeaders[]{new HttpHeaders(), userWithoutRoleHeaders}) {
			testStatusCode(HttpStatus.OK, headers, get("/swagger-ui/index.html"));
			testStatusCode(HttpStatus.OK, headers, get("/v3/api-docs"));
			testStatusCode(HttpStatus.OK, headers, get("/v3/api-docs/swagger-config"));
			testStatusCode(HttpStatus.OK, headers, get("/version"));
		}
	}

	@Test
	void encodedSlashOutOfSwaggerPathRequiresLogin() throws URISyntaxException {
		testStatusCode(HttpStatus.FORBIDDEN, new HttpHeaders(), get("/swagger-ui%2Ffhir/ValueSet"));
		testStatusCode(HttpStatus.FORBIDDEN, new HttpHeaders(), get("/swagger-ui%2Fcodesystems"));
	}

	@Test
	void dotDotSegmentOutOfSwaggerPathIsRejected() throws URISyntaxException {
		testStatusCode(HttpStatus.BAD_REQUEST, new HttpHeaders(), get("/swagger-ui/..%2Fcodesystems"));
		testStatusCode(HttpStatus.BAD_REQUEST, new HttpHeaders(), get("/swagger-ui/..%2Fversion"));
		// Dispatched to the HAPI servlet, where the trailing slash filter fails on the path before the firewall runs.
		testStatusCode(HttpStatus.FORBIDDEN, new HttpHeaders(), get("/swagger-ui/..%2Ffhir/ValueSet"));
		testStatusCode(HttpStatus.BAD_REQUEST, userWithoutRoleHeaders, get("/admin/permissions/user-group/x%2F..%2F..%2F..%2F..%2Fswagger-ui%2Fy"));
	}

	@Test
	void controlCharacterInParameterValueIsRejected() throws URISyntaxException {
		testStatusCode(HttpStatus.BAD_REQUEST, authorHeaders, get("/codesystems?forBranch=%00"));
		testStatusCode(HttpStatus.BAD_REQUEST, authorHeaders, get("/fhir/ValueSet?url=%00"));
	}

	@Test
	void controlCharacterInFhirHeaderValueIsRejected() throws IOException {
		// Raw socket so the 0x85 byte (obs-text, which Tomcat accepts and decodes to a C1 control) is sent as-is.
		try (Socket socket = new Socket("localhost", port)) {
			String request = "GET /fhir/ValueSet HTTP/1.1\r\nHost: localhost\r\nX-AUTH-username: userB\r\nX-AUTH-roles: int-author-group\r\n" +
					"X-Test: a\u0085b\r\nConnection: close\r\n\r\n";
			socket.getOutputStream().write(request.getBytes(StandardCharsets.ISO_8859_1));
			String response = new String(socket.getInputStream().readAllBytes(), StandardCharsets.ISO_8859_1);
			assertTrue(response.startsWith("HTTP/1.1 400"), response);
			assertFalse(response.contains("a\u0085b"), response);
		}
	}

	@Test
	void swaggerPathRewrittenToBranchControllerRequiresLogin() throws URISyntaxException {
		testStatusCode(HttpStatus.FORBIDDEN, new HttpHeaders(), get("/swagger-ui/MAIN/concepts"));
	}

	private RequestEntity<Object> get(String path) throws URISyntaxException {
		return new RequestEntity<>(HttpMethod.GET, new URI(url + path));
	}

	@TestConfiguration
	static class DecodeEncodedSolidus {
		@Bean
		TomcatConnectorCustomizer decodeEncodedSolidus() {
			return connector -> connector.setEncodedSolidusHandling(EncodedSolidusHandling.DECODE.getValue());
		}
	}

}
