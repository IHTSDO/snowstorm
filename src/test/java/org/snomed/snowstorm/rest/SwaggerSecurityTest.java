package org.snomed.snowstorm.rest;

import org.apache.tomcat.util.buf.EncodedSolidusHandling;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.tomcat.TomcatConnectorCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.*;

import java.net.URI;
import java.net.URISyntaxException;

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
	void dotDotSegmentOutOfSwaggerPathRequiresLogin() throws URISyntaxException {
		// The firewall rejects these (see SnowstormHttpFirewallTest) and the 403 comes from the secured /error dispatch.
		testStatusCode(HttpStatus.FORBIDDEN, new HttpHeaders(), get("/swagger-ui/..%2Fcodesystems"));
		testStatusCode(HttpStatus.FORBIDDEN, new HttpHeaders(), get("/swagger-ui/..%2Fversion"));
		testStatusCode(HttpStatus.FORBIDDEN, new HttpHeaders(), get("/swagger-ui/..%2Ffhir/ValueSet"));
		testStatusCode(HttpStatus.FORBIDDEN, userWithoutRoleHeaders, get("/admin/permissions/user-group/x%2F..%2F..%2F..%2F..%2Fswagger-ui%2Fy"));
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
