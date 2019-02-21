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
import org.snomed.snowstorm.core.data.domain.Relationship;
import org.snomed.snowstorm.core.data.services.ConceptService;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.snomed.snowstorm.core.pojo.BranchTimepoint;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Date;
import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;

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
	private Date timepointWithOneRelationship;

	@Before
	public void setup() throws ServiceException, InterruptedException {
		branchService.create("MAIN");

		// Create dummy concept with descriptions containing quotes
		Concept concept = conceptService.create(
				new Concept("257751006")
						.addDescription(new Description("Wallace \"69\" side-to-end anastomosis - action (qualifier value)")
								.setTypeId(Concepts.FSN)
								.addLanguageRefsetMember(Concepts.US_EN_LANG_REFSET, Concepts.PREFERRED))
						.addDescription(new Description("Wallace \"69\" side-to-end anastomosis - action")
								.setTypeId(Concepts.SYNONYM)
								.addLanguageRefsetMember(Concepts.US_EN_LANG_REFSET, Concepts.PREFERRED)),
				"MAIN");

		// Add 1 second sleeps because the timepoint URI format uses second as the finest level
		Thread.sleep(1_000);

		// Create a project branch and add a relationship to the dummy concept
		branchService.create("MAIN/projectA");
		concept.getRelationships().add(new Relationship(Concepts.ISA, Concepts.SNOMEDCT_ROOT));
		concept = conceptService.update(concept, "MAIN/projectA");

		Thread.sleep(1_000);

		// Make a note of the time the dummy concept had one relationship and two descriptions
		timepointWithOneRelationship = new Date();

		Thread.sleep(1_000);

		// Add another relationship and description making two relationships and three descriptions
		concept.getRelationships().add(new Relationship(Concepts.ISA, Concepts.CLINICAL_FINDING));
		concept.getDescriptions().add(new Description("Test"));
		conceptService.update(concept, "MAIN/projectA");
	}

	@Test
	public void testLoadConceptTimepoints() {
		// Load initial version of dummy concept
		String timepoint = "@-";
		Concept initialConceptVersion = this.restTemplate.getForObject("http://localhost:" + port + "/browser/MAIN/projectA" + timepoint + "/concepts/257751006", Concept.class);
		assertEquals(0, initialConceptVersion.getRelationships().size());
		assertEquals(2, initialConceptVersion.getDescriptions().size());

		// Load intermediate version of dummy concept
		timepoint = "@" + BranchTimepoint.DATE_FORMAT.format(timepointWithOneRelationship);
		System.out.println("Intermediate version timepoint " + timepoint);
		Concept intermediateConceptVersion = this.restTemplate.getForObject("http://localhost:" + port + "/browser/MAIN/projectA" + timepoint + "/concepts/257751006", Concept.class);
		assertEquals(1, intermediateConceptVersion.getRelationships().size());
		assertEquals(2, intermediateConceptVersion.getDescriptions().size());

		// Load current version of dummy concept
		timepoint = "";
		Concept currentConceptVersion = this.restTemplate.getForObject("http://localhost:" + port + "/browser/MAIN/projectA" + timepoint + "/concepts/257751006", Concept.class);;
		assertEquals(2, currentConceptVersion.getRelationships().size());
		assertEquals(3, currentConceptVersion.getDescriptions().size());
	}

	@Test
	public void testQuotesEscapedAllConceptEndpoints() {
		// Browser Concept
		String responseBody = this.restTemplate.getForObject("http://localhost:" + port + "/browser/MAIN/concepts/257751006", String.class);
		System.out.println(responseBody);

		// Assert that quotes are escaped
		assertThat(responseBody).contains("\"Wallace \\\"69\\\" side-to-end anastomosis - action (qualifier value)\"");
		assertThat(responseBody).contains("\"Wallace \\\"69\\\" side-to-end anastomosis - action\"");


		// Simple Concept
		responseBody = this.restTemplate.getForObject("http://localhost:" + port + "/MAIN/concepts/257751006", String.class);
		System.out.println(responseBody);

		// Assert that quotes are escaped
		assertThat(responseBody).contains("\"Wallace \\\"69\\\" side-to-end anastomosis - action (qualifier value)\"");
		assertThat(responseBody).contains("\"Wallace \\\"69\\\" side-to-end anastomosis - action\"");


		// Simple Concept ECL
		HashMap<String, Object> urlVariables = new HashMap<>();
		urlVariables.put("ecl", "257751006");
		responseBody = this.restTemplate.getForObject("http://localhost:" + port + "/MAIN/concepts", String.class, urlVariables);
		System.out.println(responseBody);

		// Assert that quotes are escaped
		assertThat(responseBody).contains("\"Wallace \\\"69\\\" side-to-end anastomosis - action (qualifier value)\"");
		assertThat(responseBody).contains("\"Wallace \\\"69\\\" side-to-end anastomosis - action\"");
	}
}
