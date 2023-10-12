package org.snomed.snowstorm.rest;

import org.junit.jupiter.api.Test;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.TestConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = TestConfig.class)
class UtilityControllerTest extends AbstractTest {

	@LocalServerPort
	private int port;

	@Autowired
	private TestRestTemplate restTemplate;

	@Test
	public void testEclWithGroup() {
		assertEquals(200, apiParseEcl("<<900000000000496009:{100000000=200000000}").getStatusCode().value());
		assertEquals(200, apiParseEcl("<<900000000000496009:{*=*}").getStatusCode().value());
	}

	private ResponseEntity<String> apiParseEcl(String ecl) {
		String url = "http://localhost:" + port + "/util/ecl-string-to-model";
		return restTemplate.postForEntity(url, ecl, String.class);
	}

}
