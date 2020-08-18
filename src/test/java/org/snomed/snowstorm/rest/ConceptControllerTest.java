package org.snomed.snowstorm.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.kaicode.elasticvc.api.BranchService;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.TestConfig;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.data.services.BranchMergeService;
import org.snomed.snowstorm.core.data.services.CodeSystemService;
import org.snomed.snowstorm.core.data.services.ConceptService;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.snomed.snowstorm.core.pojo.BranchTimepoint;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = TestConfig.class)
class ConceptControllerTest extends AbstractTest {

	@LocalServerPort
	private int port;

	@Autowired
	private TestRestTemplate restTemplate;

	@Autowired
	private BranchService branchService;

	@Autowired
	private BranchMergeService branchMergeService;

	@Autowired
	private ConceptService conceptService;

	@Autowired
	private CodeSystemService codeSystemService;

	@Autowired
	private ObjectMapper objectMapper;

	private Date timepointWithOneRelationship;

	@BeforeEach
	void setup() throws ServiceException, InterruptedException {
		// Create dummy concept with descriptions containing quotes
		String conceptId = "257751006";
		Concept concept = conceptService.create(
				new Concept(conceptId)
						.addDescription(new Description("Wallace \"69\" side-to-end anastomosis - action (qualifier value)")
								.setTypeId(Concepts.FSN)
								.addLanguageRefsetMember(Concepts.US_EN_LANG_REFSET, Concepts.PREFERRED))
						.addDescription(new Description("Wallace \"69\" side-to-end anastomosis - action")
								.setTypeId(Concepts.SYNONYM)
								.addLanguageRefsetMember(Concepts.US_EN_LANG_REFSET, Concepts.PREFERRED))
						.addAxiom(new Relationship(Concepts.ISA, Concepts.SNOMEDCT_ROOT)),
				"MAIN");

		// Add 1 second sleeps because the timepoint URI format uses second as the finest level
		Thread.sleep(1_000);

		// Create a project branch and add a relationship to the dummy concept
		branchService.create("MAIN/projectA");
		concept.getRelationships().add(new Relationship(Concepts.ISA, Concepts.CLINICAL_FINDING));
		concept = conceptService.update(concept, "MAIN/projectA");

		Thread.sleep(1_000);

		// Make a note of the time the dummy concept had one relationship and two descriptions
		timepointWithOneRelationship = new Date();

		Thread.sleep(1_000);

		// Add a synonym on MAIN and rebase
		concept = conceptService.find(conceptId, "MAIN");
		concept.getDescriptions().add(new Description("New syn on MAIN"));
		conceptService.update(concept, "MAIN");
		branchMergeService.mergeBranchSync("MAIN", "MAIN/projectA", Collections.emptySet());

		// Add another relationship and description making two relationships and four descriptions
		concept = conceptService.find(conceptId, "MAIN/projectA");
		concept.getRelationships().add(new Relationship(Concepts.ISA, Concepts.CLINICAL_FINDING));
		concept.getDescriptions().add(new Description("Test"));
		conceptService.update(concept, "MAIN/projectA");

		// Version content to fill effectiveTime fields
		CodeSystem codeSystem = new CodeSystem("SNOMEDCT", "MAIN");
		codeSystemService.createCodeSystem(codeSystem);
		codeSystemService.createVersion(codeSystem, 20190731, "");
	}

	@Test
	void testLoadConceptTimepoints() {
		// Load initial version of dummy concept
		String timepoint = "@-";
		Concept initialConceptVersion = this.restTemplate.getForObject("http://localhost:" + port + "/browser/MAIN/projectA" + timepoint + "/concepts/257751006", Concept.class);
		assertEquals("257751006", initialConceptVersion.getConceptId());
		assertEquals(0, initialConceptVersion.getRelationships().size());
		assertEquals(2, initialConceptVersion.getDescriptions().size());

		// Load intermediate version of dummy concept
		timepoint = "@" + BranchTimepoint.DATE_FORMAT.format(timepointWithOneRelationship);
		System.out.println("Intermediate version timepoint " + timepoint);
		Concept intermediateConceptVersion = this.restTemplate.getForObject("http://localhost:" + port + "/browser/MAIN/projectA" + timepoint + "/concepts/257751006", Concept.class);
		assertEquals(1, intermediateConceptVersion.getRelationships().size());
		assertEquals(2, intermediateConceptVersion.getDescriptions().size());

		// Load base version of the concept (from parent branch)
		timepoint = "@^";
		Concept baseConceptVersion = this.restTemplate.getForObject("http://localhost:" + port + "/browser/MAIN/projectA" + timepoint + "/concepts/257751006", Concept.class);
		assertEquals(0, baseConceptVersion.getRelationships().size());
		assertEquals(3, baseConceptVersion.getDescriptions().size());

		// Load current version of dummy concept
		timepoint = "";
		Concept currentConceptVersion = this.restTemplate.getForObject("http://localhost:" + port + "/browser/MAIN/projectA" + timepoint + "/concepts/257751006", Concept.class);
		assertEquals(2, currentConceptVersion.getRelationships().size());
		assertEquals(4, currentConceptVersion.getDescriptions().size());
	}

	@Test
	void testQuotesEscapedAllConceptEndpoints() {
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

	@Test
	void testConceptEndpointFields() throws IOException {
		// Browser Concept
		String responseBody = this.restTemplate.getForObject("http://localhost:" + port + "/browser/MAIN/concepts/257751006", String.class);
		checkFields(responseBody);
		LinkedHashMap<String, Object> properties = objectMapper.readValue(responseBody, LinkedHashMap.class);
		assertEquals("[conceptId, fsn, pt, active, effectiveTime, released, releasedEffectiveTime, moduleId, definitionStatus, " +
				"descriptions, classAxioms, gciAxioms, relationships]", properties.keySet().toString());
		Object fsn = properties.get("fsn");
		assertEquals("LinkedHashMap", fsn.getClass().getSimpleName());
		assertEquals("{term=Wallace \"69\" side-to-end anastomosis - action (qualifier value), lang=en}", fsn.toString());

		// Simple Concept
		responseBody = this.restTemplate.getForObject("http://localhost:" + port + "/MAIN/concepts/257751006", String.class);
		checkFields(responseBody);

		// Simple Concept ECL
		HashMap<String, Object> urlVariables = new HashMap<>();
		urlVariables.put("ecl", "257751006");
		responseBody = this.restTemplate.getForObject("http://localhost:" + port + "/MAIN/concepts", String.class, urlVariables);
		checkFields(responseBody);
	}

	private void checkFields(String responseBody) {
		System.out.println(responseBody);
		assertThat(responseBody).doesNotContain("\"internalId\"");
		assertThat(responseBody).doesNotContain("\"start\"");
		assertThat(responseBody).doesNotContain("\"effectiveTimeI\"");
	}

	@Test
    void testConceptSearchWithCSVResults() throws IOException {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Accept", "text/csv");
        ResponseEntity<String> responseEntity = this.restTemplate.exchange("http://localhost:" + port + "/MAIN/projectA/concepts",
                HttpMethod.GET, new HttpEntity<>(null, headers), String.class, Collections.singletonMap("limit", 100));

        assertEquals(200, responseEntity.getStatusCode().value());
        String responseBody = responseEntity.getBody();
        assertNotNull(responseBody);
        try (BufferedReader reader = new BufferedReader(new StringReader(responseBody))) {
            String header = reader.readLine();
            assertEquals("id\tfsn\teffectiveTime\tactive\tmoduleId\tdefinitionStatus\tpt_900000000000508004\tpt_900000000000509007", header);
            assertEquals("257751006\tWallace \"69\" side-to-end anastomosis - action (qualifier value)\t\ttrue\t900000000000207008\tPRIMITIVE\t\tWallace \"69\" side-to-end anastomosis - action", reader.readLine());
        }
    }

    @Test
    void testConceptSearchWithLanguageRefsets() throws JSONException {
        String conceptId = "257751006";

		// Expected 1 concept found for US_EN language refset
	    ResponseEntity<String> responseEntity = this.restTemplate.exchange("http://localhost:" + port + "/MAIN/projectA/concepts?preferredOrAcceptableIn=" + Long.parseLong(Concepts.US_EN_LANG_REFSET) + "&conceptIds=" + conceptId,
                HttpMethod.GET, new HttpEntity<>(null), String.class);
        assertEquals(200, responseEntity.getStatusCode().value());
		String responseBody = responseEntity.getBody();
        assertNotNull(responseBody);
		JSONObject jsonObject = new JSONObject(responseBody);
		assertEquals(1, jsonObject.get("total"));

		// No result for invalid given language refset
		long belgiumDutchLanguageRefsetId = 31000172101L;
		responseEntity = this.restTemplate.exchange("http://localhost:" + port + "/MAIN/projectA/concepts?preferredOrAcceptableIn=" + belgiumDutchLanguageRefsetId + "&conceptIds=" + conceptId,
				HttpMethod.GET, new HttpEntity<>(null), String.class);
		assertEquals(200, responseEntity.getStatusCode().value());
		responseBody = responseEntity.getBody();
		assertNotNull(responseBody);
		jsonObject = new JSONObject(responseBody);
		assertEquals(0, jsonObject.get("total"));
    }
}
