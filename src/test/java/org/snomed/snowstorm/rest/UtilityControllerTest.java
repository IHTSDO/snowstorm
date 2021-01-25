package org.snomed.snowstorm.rest;

import org.apache.http.message.BasicNameValuePair;
import org.junit.jupiter.api.Test;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.TestConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;

import java.util.Collections;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.http.client.utils.URLEncodedUtils.format;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = TestConfig.class)
class UtilityControllerTest extends AbstractTest {

	@LocalServerPort
	private int port;

	@Autowired
	private TestRestTemplate restTemplate;

	@Test
	public void testEclWithGroup() {
		assertEquals(200, apiParseEcl("<<900000000000496009:{100000000=200000000}").getStatusCodeValue());
		assertEquals(200, apiParseEcl("<<900000000000496009:{*=*}").getStatusCodeValue());
	}

	private ResponseEntity<String> apiParseEcl(String ecl) {
		String url = "http://localhost:" + port + "/util/parse-ecl?" + format(Collections.singletonList(new BasicNameValuePair("ecl", ecl)), UTF_8);
		return restTemplate.getForEntity(url, String.class);
	}

}
