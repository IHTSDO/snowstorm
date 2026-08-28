package org.snomed.snowstorm.rest;

import org.junit.jupiter.api.Test;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.TestConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The UI sends some uris with a trailing slash. PathMatchConfigurer.setUseTrailingSlashMatch(true) used to
 * make those match; it was removed in Spring Framework 7 and replaced by the UrlHandlerFilter registered in
 * WebConfig. These cases cover a plain uri and one that also passes through the branch path rewrite filter,
 * which is what the filter ordering has to get right.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = TestConfig.class)
@AutoConfigureTestRestTemplate
class TrailingSlashTest extends AbstractTest {

	@LocalServerPort
	private int port;

	@Autowired
	private TestRestTemplate restTemplate;

	@Test
	void testTrailingSlashMatchesWithoutBranchPath() {
		assertEquals(get("/branches").getStatusCode(), get("/branches/").getStatusCode());
		assertEquals(200, get("/branches/").getStatusCode().value());
	}

	@Test
	void testTrailingSlashMatchesWithBranchPath() {
		assertEquals(get("/MAIN/concepts").getStatusCode(), get("/MAIN/concepts/").getStatusCode());
		assertEquals(200, get("/MAIN/concepts/").getStatusCode().value());
	}

	private ResponseEntity<String> get(String path) {
		return restTemplate.getForEntity("http://localhost:" + port + path, String.class);
	}
}
