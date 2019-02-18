package org.snomed.snowstorm.rest;

import io.kaicode.elasticvc.api.BranchService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.TestConfig;
import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.core.data.domain.Concepts;
import org.snomed.snowstorm.core.data.domain.Description;
import org.snomed.snowstorm.core.data.services.ConceptService;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = TestConfig.class)
public class ConceptControllerTest extends AbstractTest {

	@LocalServerPort
	private int port;

	@Autowired
	private TestRestTemplate restTemplate;

	@Autowired
	private BranchService branchService;

	@Autowired
	private ConceptService conceptService;

	@Before
	public void setup() throws ServiceException {
		branchService.create("MAIN");

		conceptService.create(
				new Concept("257751006")
						.addDescription(new Description("Wallace \"69\" side-to-end anastomosis - action (qualifier value)")
								.setTypeId(Concepts.FSN)
								.addLanguageRefsetMember(Concepts.US_EN_LANG_REFSET, Concepts.PREFERRED)),
				"MAIN");
		conceptService.create(
				new Concept()
						.addDescription(new Description("Wallace \"69\" side-to-end anastomosis - action")
								.setTypeId(Concepts.SYNONYM)
								.addLanguageRefsetMember(Concepts.US_EN_LANG_REFSET, Concepts.PREFERRED)),
				"MAIN");
	}

	@Test
	public void testQuotesEscapedAllConceptEndpoints() {
		// Browser Concept
		String responseBody = this.restTemplate.getForObject("http://localhost:" + port + "/browser/MAIN/concepts/257751006", String.class);
		System.out.println(responseBody);

		// Assert that quotes are escaped
		assertThat(responseBody).contains("Wallace \\\"69\\\" side-to-end anastomosis - action (qualifier value)");
		assertThat(responseBody).contains("Wallace \\\"69\\\" side-to-end anastomosis - action");


		// Simple Concept
		responseBody = this.restTemplate.getForObject("http://localhost:" + port + "/MAIN/concepts/257751006", String.class);
		System.out.println(responseBody);

		// Assert that quotes are escaped
		assertThat(responseBody).contains("Wallace \\\"69\\\" side-to-end anastomosis - action (qualifier value)");
		assertThat(responseBody).contains("Wallace \\\"69\\\" side-to-end anastomosis - action");


		// Simple Concept ECL
		HashMap<String, Object> urlVariables = new HashMap<>();
		urlVariables.put("ecl", "257751006");
		responseBody = this.restTemplate.getForObject("http://localhost:" + port + "/MAIN/concepts", String.class, urlVariables);
		System.out.println(responseBody);

		// Assert that quotes are escaped
		assertThat(responseBody).contains("Wallace \\\"69\\\" side-to-end anastomosis - action (qualifier value)");
		assertThat(responseBody).contains("Wallace \\\"69\\\" side-to-end anastomosis - action");
	}
}
