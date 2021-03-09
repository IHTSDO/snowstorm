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
import org.snomed.snowstorm.core.data.services.pojo.ConceptHistory;
import org.snomed.snowstorm.core.pojo.BranchTimepoint;
import org.snomed.snowstorm.loadtest.ItemsPagePojo;
import org.snomed.snowstorm.rest.pojo.ConceptBulkLoadRequest;
import org.snomed.snowstorm.util.ConceptControllerTestConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.util.UriComponentsBuilder;
import org.testcontainers.shaded.com.google.common.collect.ImmutableMap;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.*;
import static org.snomed.snowstorm.core.data.domain.Concepts.ISA;
import static org.snomed.snowstorm.core.data.domain.Concepts.SNOMEDCT_ROOT;

@ActiveProfiles("test")
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
						.addAxiom(new Relationship(Concepts.ISA, SNOMEDCT_ROOT)),
				"MAIN");

		// Add 1 second sleeps because the timepoint URI format uses second as the finest level
		Thread.sleep(1_000);

		// Create a project branch and add a relationship to the dummy concept
		branchService.create("MAIN/projectA");
		concept.getRelationships().add(new Relationship(Concepts.ISA, Concepts.CLINICAL_FINDING));
		conceptService.update(concept, "MAIN/projectA");

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
				"descriptions, classAxioms, gciAxioms, relationships, validationResults]", properties.keySet().toString());
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

	@Test
	void testConceptSearchWithValidEclExpression() {
		String validEclExpression = "257751006 |Clinical finding|";

		ResponseEntity<String> responseEntity = this.restTemplate.exchange("http://localhost:" + port + "/MAIN/concepts?ecl=" + validEclExpression,
				HttpMethod.GET, new HttpEntity<>(null), String.class);
		assertEquals(200, responseEntity.getStatusCode().value());
	}

	@Test
	void testFailsConceptSearchWithInactiveConceptIdInEclExpression() throws ServiceException, JSONException {
		String conceptId = "257751006";
		Concept concept = conceptService.find(conceptId, "MAIN");
		concept.setActive(false);
		conceptService.update(concept, "MAIN");
		String eclExpressionWithInactiveConcept = "257751006 |Clinical finding|";

		ResponseEntity<String> responseEntity = this.restTemplate.exchange("http://localhost:" + port + "/MAIN/concepts?ecl=" + eclExpressionWithInactiveConcept,
				HttpMethod.GET, new HttpEntity<>(null), String.class);
		assertEquals(400, responseEntity.getStatusCode().value());
		String responseBody = responseEntity.getBody();
		JSONObject jsonObject = new JSONObject(responseBody);
		assertEquals("Concepts in the ECL request do not exist or are inactive on branch MAIN: 257751006.", jsonObject.get("message"));
	}

	@Test
	void testFailsConceptSearchWithNonexistentConceptIdInEclExpression() throws JSONException {
		String conceptId = "257751006";
		conceptService.deleteConceptAndComponents(conceptId, "MAIN", true);
		String eclExpressionWithNonexistentConcept = "257751006 |Clinical finding|";

		ResponseEntity<String> responseEntity = this.restTemplate.exchange("http://localhost:" + port + "/MAIN/concepts?ecl=" + eclExpressionWithNonexistentConcept,
				HttpMethod.GET, new HttpEntity<>(null), String.class);
		assertEquals(400, responseEntity.getStatusCode().value());
		String responseBody = responseEntity.getBody();
		JSONObject jsonObject = new JSONObject(responseBody);
		assertEquals("Concepts in the ECL request do not exist or are inactive on branch MAIN: 257751006.", jsonObject.get("message"));
	}

	@Test
	void testCreateConceptWithValidationEnabled() {
		branchService.updateMetadata("MAIN", ImmutableMap.of(
				"assertionGroupNames", "common-authoring",
				"previousRelease", "20210131",
				"defaultReasonerNamespace", "",
				"previousPackage", "prod_main_2021-01-31_20201124120000.zip"));
		HttpHeaders httpHeaders = new HttpHeaders();
		httpHeaders.set("Content-Type", "application/json");
		final String responseEntity = restTemplate.exchange(
				UriComponentsBuilder.fromUriString("http://localhost:" + port + "/browser/MAIN/concepts").queryParam("validate", "true").build().toUri(),
				HttpMethod.POST, new HttpEntity<>(ConceptControllerTestConstants.CONCEPT_WITH_VALIDATION_WARNINGS_ONLY, httpHeaders), String.class).toString();
		assertTrue(responseEntity.contains("200"));
		assertTrue(responseEntity.contains("Test resources were not available so assertions like case significance and US specific terms checks will not have run."));
	}

	@Test
	void testCreateConceptWithValidationEnabledWhichContainsErrorsReturnsBadRequest() {
		branchService.updateMetadata("MAIN", ImmutableMap.of(
				"assertionGroupNames", "common-authoring",
				"previousRelease", "20210131",
				"defaultReasonerNamespace", "",
				"previousPackage", "prod_main_2021-01-31_20201124120000.zip"));
		HttpHeaders httpHeaders = new HttpHeaders();
		httpHeaders.setContentType(MediaType.APPLICATION_JSON);
		final ResponseEntity<String> responseEntity = restTemplate.exchange(
				UriComponentsBuilder.fromUriString("http://localhost:" + port + "/browser/MAIN/concepts").queryParam("validate", "true").build().toUri(),
				HttpMethod.POST, new HttpEntity<>(ConceptControllerTestConstants.CONCEPT_WITH_VALIDATION_ERRORS_AND_WARNINGS, httpHeaders), String.class);
		assertEquals(HttpStatus.BAD_REQUEST, responseEntity.getStatusCode());
	}

	@Test
	void testUpdateConceptWithValidationEnabled() throws ServiceException {
		HttpHeaders httpHeaders = new HttpHeaders();
		httpHeaders.setContentType(MediaType.APPLICATION_JSON);
		branchService.updateMetadata("MAIN", ImmutableMap.of(
				"assertionGroupNames", "common-authoring",
				"previousRelease", "20210131",
				"defaultReasonerNamespace", "",
				"previousPackage", "prod_main_2021-01-31_20201124120000.zip"));
		conceptService.create(new Concept("99970008"), "MAIN");
		final String responseEntity = restTemplate.exchange(
				UriComponentsBuilder.fromUriString("http://localhost:" + port + "/browser/MAIN/concepts/99970008").queryParam("validate", "true").build().toUri(),
				HttpMethod.PUT, new HttpEntity<>(ConceptControllerTestConstants.CONCEPT_WITH_VALIDATION_WARNINGS_ONLY, httpHeaders), String.class).toString();
		assertTrue(responseEntity.contains("200"));
		assertTrue(responseEntity.contains("Test resources were not available so assertions like case significance and US specific terms checks will not have run."));
	}

	@Test
	void testUpdateConceptWithValidationEnabledWhichContainsErrorsReturnsBadRequest() throws ServiceException {
		branchService.updateMetadata("MAIN", ImmutableMap.of(
				"assertionGroupNames", "common-authoring",
				"previousRelease", "20210131",
				"defaultReasonerNamespace", "",
				"previousPackage", "prod_main_2021-01-31_20201124120000.zip"));
		HttpHeaders httpHeaders = new HttpHeaders();
		httpHeaders.setContentType(MediaType.APPLICATION_JSON);
		conceptService.create(new Concept("9999005"), "MAIN");
		final ResponseEntity<ConceptView> responseEntity = restTemplate.exchange(
				UriComponentsBuilder.fromUriString("http://localhost:" + port + "/browser/MAIN/concepts/99970008").queryParam("validate", "true").build().toUri(),
				HttpMethod.PUT, new HttpEntity<>(ConceptControllerTestConstants.CONCEPT_WITH_VALIDATION_ERRORS_AND_WARNINGS, httpHeaders), ConceptView.class);
		assertEquals(HttpStatus.BAD_REQUEST, responseEntity.getStatusCode());
	}

	@Test
	void testECLSearchAfter() throws ServiceException {
		conceptService.create(new Concept(SNOMEDCT_ROOT), "MAIN");
		conceptService.create(new Concept(Concepts.CLINICAL_FINDING).addAxiom(new Relationship(Concepts.ISA, SNOMEDCT_ROOT)), "MAIN");
		assertEquals(3, conceptService.findAll("MAIN", PageRequest.of(1, 100)).getTotalElements());

		// Fetch first page
		ResponseEntity<ItemsPagePojo<ConceptMini>> responseEntity = this.restTemplate.exchange("http://localhost:" + port + "/MAIN/concepts?activeFilter=true&statedEcl=<138875005&limit=1",
				HttpMethod.GET, new HttpEntity<>(null), new ParameterizedTypeReference<ItemsPagePojo<ConceptMini>>() {});
		assertEquals(200, responseEntity.getStatusCode().value());
		ItemsPagePojo<ConceptMini> page = responseEntity.getBody();
		assertNotNull(page);
		assertEquals(2L, page.getTotal());
		assertEquals(1, page.getItems().size());
		String conceptIdFromFirstPage = page.getItems().iterator().next().getConceptId();
		String searchAfterFromFirstPage = page.getSearchAfter();
		assertNotNull(searchAfterFromFirstPage);

		// Fetch second page
		responseEntity = this.restTemplate.exchange("http://localhost:" + port + "/MAIN/concepts?activeFilter=true&statedEcl=<138875005&limit=1&searchAfter=" + searchAfterFromFirstPage,
				HttpMethod.GET, new HttpEntity<>(null), new ParameterizedTypeReference<ItemsPagePojo<ConceptMini>>() {});
		assertEquals(200, responseEntity.getStatusCode().value());
		page = responseEntity.getBody();
		assertNotNull(page);
		assertEquals(1, page.getItems().size());
		String conceptIdFromSecondPage = page.getItems().iterator().next().getConceptId();
		assertNotEquals(conceptIdFromFirstPage, conceptIdFromSecondPage);
	}


	@Test
	void testECLSearchAfterWithConceptIdsOnly() throws ServiceException {
		conceptService.create(new Concept(SNOMEDCT_ROOT), "MAIN");
		conceptService.create(new Concept(Concepts.CLINICAL_FINDING).addAxiom(new Relationship(Concepts.ISA, SNOMEDCT_ROOT)), "MAIN");
		assertEquals(3, conceptService.findAll("MAIN", PageRequest.of(1, 100)).getTotalElements());

		// Fetch all in one page
		ResponseEntity<ItemsPagePojo<Long>> responseEntity = this.restTemplate.exchange("http://localhost:" + port + "/MAIN/concepts?activeFilter=true&statedEcl=<138875005&returnIdOnly=true&limit=100",
				HttpMethod.GET, new HttpEntity<>(null), new ParameterizedTypeReference<ItemsPagePojo<Long>>() {});
		assertEquals(200, responseEntity.getStatusCode().value());
		ItemsPagePojo<Long> page = responseEntity.getBody();
		assertNotNull(page);
		assertEquals(2L, page.getTotal());
		assertEquals(2L, page.getItems().size());
		List<Long> results = page.getItems();

		// Fetch first page
		responseEntity = this.restTemplate.exchange("http://localhost:" + port + "/MAIN/concepts?activeFilter=true&statedEcl=<138875005&returnIdOnly=true&limit=1",
				HttpMethod.GET, new HttpEntity<>(null), new ParameterizedTypeReference<ItemsPagePojo<Long>>() {});
		assertEquals(200, responseEntity.getStatusCode().value());
		page = responseEntity.getBody();
		assertNotNull(page);
		assertEquals(2L, page.getTotal());
		assertEquals(1, page.getItems().size());
		Long conceptIdFromFirstPage = page.getItems().iterator().next();
		assertTrue(results.contains(conceptIdFromFirstPage));
		String searchAfterFromFirstPage = page.getSearchAfter();
		assertNotNull(searchAfterFromFirstPage);

		// Fetch second page
		responseEntity = this.restTemplate.exchange("http://localhost:" + port + "/MAIN/concepts?activeFilter=true&statedEcl=<138875005&returnIdOnly=true&limit=1&searchAfter=" + searchAfterFromFirstPage,
				HttpMethod.GET, new HttpEntity<>(null), new ParameterizedTypeReference<ItemsPagePojo<Long>>() {});
		assertEquals(200, responseEntity.getStatusCode().value());
		page = responseEntity.getBody();
		assertNotNull(page);
		assertEquals(1, page.getItems().size());
		Long conceptIdFromSecondPage = page.getItems().iterator().next();
		assertTrue(results.contains(conceptIdFromSecondPage));
		assertNotEquals(conceptIdFromFirstPage, conceptIdFromSecondPage);
	}


	@Test
	void testSearchAfter() throws ServiceException {
		conceptService.create(new Concept(Concepts.CLINICAL_FINDING), "MAIN");
		assertEquals(2, conceptService.findAll("MAIN", PageRequest.of(1, 100)).getTotalElements());

		// Fetch first page
		ResponseEntity<ItemsPagePojo<ConceptMini>> responseEntity = this.restTemplate.exchange("http://localhost:" + port + "/MAIN/concepts?activeFilter=true&limit=1",
				HttpMethod.GET, new HttpEntity<>(null), new ParameterizedTypeReference<ItemsPagePojo<ConceptMini>>() {});
		assertEquals(200, responseEntity.getStatusCode().value());
		ItemsPagePojo<ConceptMini> page = responseEntity.getBody();
		assertNotNull(page);
		assertEquals(2L, page.getTotal());
		assertEquals(1, page.getItems().size());
		String conceptIdFromFirstPage = page.getItems().iterator().next().getConceptId();
		String searchAfterFromFirstPage = page.getSearchAfter();
		assertNotNull(searchAfterFromFirstPage);

		// Fetch second page
		System.out.println("searchAfter '" + searchAfterFromFirstPage + "'");
		responseEntity = this.restTemplate.exchange("http://localhost:" + port + "/MAIN/concepts?activeFilter=true&limit=1&searchAfter=" + searchAfterFromFirstPage,
				HttpMethod.GET, new HttpEntity<>(null), new ParameterizedTypeReference<ItemsPagePojo<ConceptMini>>() {});
		assertEquals(200, responseEntity.getStatusCode().value());
		page = responseEntity.getBody();
		assertNotNull(page);
		assertEquals(1, page.getItems().size());
		String conceptIdFromSecondPage = page.getItems().iterator().next().getConceptId();
		assertNotEquals(conceptIdFromFirstPage, conceptIdFromSecondPage);
	}

	@Test
	public void findConceptHistory_ShouldReturnExpectedResponse_WhenConceptCannotBeFound() {
		//given
		String requestUrl = "http://localhost:" + port + "/browser/MAIN/concepts/12345/history";

		//when
		ResponseEntity<ConceptHistory> responseEntity = this.restTemplate.exchange(requestUrl, HttpMethod.GET, new HttpEntity<>(null), new ParameterizedTypeReference<ConceptHistory>() {
		});

		//then
		assertEquals(404, responseEntity.getStatusCodeValue());
	}

	@Test
	public void findConceptHistory_ShouldReturnExpectedResponse_WhenConceptFound() {
		//given
		String requestUrl = "http://localhost:" + port + "/browser/MAIN/concepts/257751006/history";

		//when
		ResponseEntity<ConceptHistory> responseEntity = this.restTemplate.exchange(requestUrl, HttpMethod.GET, new HttpEntity<>(null), new ParameterizedTypeReference<ConceptHistory>() {
		});

		//then
		assertEquals(200, responseEntity.getStatusCodeValue());
	}

	@Test
	public void findConceptHistory_ShouldReturnExpectedConceptHistory_WhenConceptHasHistory() throws Exception {
		//given
		String requestUrl = "http://localhost:" + port + "/browser/MAIN/BROWSE-241/concepts/123456789101112/history";
		Procedure methodTestDataFixture = () -> {
			String projectBranch = "MAIN/BROWSE-241";
			String taskBranch = "MAIN/BROWSE-241/TASK-1";
			//Create project branch
			CodeSystem codeSystem = new CodeSystem(projectBranch, projectBranch);
			codeSystemService.createCodeSystem(codeSystem);

			//Add Concepts to branch
			conceptService.create(
					new Concept("12345678910")
							.addDescription(new Description("Computer structure (computer structure)")
									.setTypeId(Concepts.FSN)
									.addLanguageRefsetMember(Concepts.US_EN_LANG_REFSET, Concepts.PREFERRED))
							.addDescription(new Description("Computer structure")
									.setTypeId(Concepts.SYNONYM)
									.addLanguageRefsetMember(Concepts.US_EN_LANG_REFSET, Concepts.PREFERRED))
							.addAxiom(new Relationship(Concepts.ISA, SNOMEDCT_ROOT)),
					projectBranch);

			conceptService.create(
					new Concept("1234567891011")
							.addDescription(new Description("Non specific site (computer structure)")
									.setTypeId(Concepts.FSN)
									.addLanguageRefsetMember(Concepts.US_EN_LANG_REFSET, Concepts.PREFERRED))
							.addDescription(new Description("Non specific site")
									.setTypeId(Concepts.SYNONYM)
									.addLanguageRefsetMember(Concepts.US_EN_LANG_REFSET, Concepts.PREFERRED))
							.addAxiom(new Relationship(Concepts.ISA, "12345678910")),
					projectBranch);

			conceptService.create(
					new Concept("123456789101112")
							.addDescription(new Description("Specific site (computer structure)")
									.setTypeId(Concepts.FSN)
									.addLanguageRefsetMember(Concepts.US_EN_LANG_REFSET, Concepts.PREFERRED))
							.addDescription(new Description("Specific site")
									.setTypeId(Concepts.SYNONYM)
									.addLanguageRefsetMember(Concepts.US_EN_LANG_REFSET, Concepts.PREFERRED))
							.addAxiom(new Relationship(Concepts.ISA, "12345678910"), new Relationship("12345", "12345678910"))
							.addRelationship(new Relationship(Concepts.ISA, "12345678910")),
					projectBranch);

			//Version project
			codeSystemService.createVersion(codeSystem, 20200131, "Release 2020-01-31.");

			//Create task branch
			branchService.create(taskBranch);

			//Update Concept with new Descriptions on task branch
			Concept concept = conceptService.find("123456789101112", projectBranch);
			concept.getDescriptions()
					.add(
							new Description("Specific")
									.setTypeId(Concepts.SYNONYM)
									.addLanguageRefsetMember(Concepts.US_EN_LANG_REFSET, Concepts.PREFERRED)
					);
			conceptService.update(concept, taskBranch);

			//Promote task
			branchMergeService.mergeBranchSync(taskBranch, projectBranch, Collections.emptySet());

			//Version project
			codeSystemService.createVersion(codeSystem, 20200731, "Release 2020-07-31.");
		};
		methodTestDataFixture.insert();

		//when
		ResponseEntity<ConceptHistory> responseEntity = this.restTemplate.exchange(requestUrl, HttpMethod.GET, new HttpEntity<>(null), new ParameterizedTypeReference<ConceptHistory>() {
		});
		ConceptHistory conceptHistory = responseEntity.getBody();
		List<ConceptHistory.ConceptHistoryItem> history = conceptHistory.getHistory();
		ConceptHistory.ConceptHistoryItem januaryRelease = conceptHistory.getConceptHistoryItem("20200131").get();
		ConceptHistory.ConceptHistoryItem julyRelease = conceptHistory.getConceptHistoryItem("20200731").get();
		List<ComponentType> januaryReleaseComponentTypes = new ArrayList<>(januaryRelease.getComponentTypes());
		List<ComponentType> julyReleaseComponentTypes = new ArrayList<>(julyRelease.getComponentTypes());

		//then
		assertEquals(200, responseEntity.getStatusCodeValue());
		assertEquals(2, history.size()); //Concept has changed since previous version.
		assertEquals(4, januaryReleaseComponentTypes.size()); //Concept was created with Description, Relationship & Axiom
		assertEquals(1, julyReleaseComponentTypes.size()); //Description was added
		assertEquals(ComponentType.Concept, januaryReleaseComponentTypes.get(0));
		assertEquals(ComponentType.Description, januaryReleaseComponentTypes.get(1));
		assertEquals(ComponentType.Relationship, januaryReleaseComponentTypes.get(2));
		assertEquals(ComponentType.Axiom, januaryReleaseComponentTypes.get(3));
		assertEquals(ComponentType.Description, julyReleaseComponentTypes.get(0));
	}

	@Test
	public void findConceptHistory_ShouldReturnExpectedConceptHistory_WhenConceptHasNoHistory() throws Exception {
		//given
		String requestUrl = "http://localhost:" + port + "/browser/MAIN/BROWSE-241/concepts/123456789101112/history";
		Procedure methodTestDataFixture = () -> {
			String projectBranch = "MAIN/BROWSE-241";
			//Create project branch
			CodeSystem codeSystem = new CodeSystem(projectBranch, projectBranch);
			codeSystemService.createCodeSystem(codeSystem);

			//Add Concepts to branch
			conceptService.create(
					new Concept("12345678910")
							.addDescription(new Description("Computer structure (computer structure)")
									.setTypeId(Concepts.FSN)
									.addLanguageRefsetMember(Concepts.US_EN_LANG_REFSET, Concepts.PREFERRED))
							.addDescription(new Description("Computer structure")
									.setTypeId(Concepts.SYNONYM)
									.addLanguageRefsetMember(Concepts.US_EN_LANG_REFSET, Concepts.PREFERRED))
							.addAxiom(new Relationship(Concepts.ISA, SNOMEDCT_ROOT)),
					projectBranch);

			conceptService.create(
					new Concept("1234567891011")
							.addDescription(new Description("Non specific site (computer structure)")
									.setTypeId(Concepts.FSN)
									.addLanguageRefsetMember(Concepts.US_EN_LANG_REFSET, Concepts.PREFERRED))
							.addDescription(new Description("Non specific site")
									.setTypeId(Concepts.SYNONYM)
									.addLanguageRefsetMember(Concepts.US_EN_LANG_REFSET, Concepts.PREFERRED))
							.addAxiom(new Relationship(Concepts.ISA, "12345678910")),
					projectBranch);

			conceptService.create(
					new Concept("123456789101112")
							.addDescription(new Description("Specific site (computer structure)")
									.setTypeId(Concepts.FSN)
									.addLanguageRefsetMember(Concepts.US_EN_LANG_REFSET, Concepts.PREFERRED))
							.addDescription(new Description("Specific site")
									.setTypeId(Concepts.SYNONYM)
									.addLanguageRefsetMember(Concepts.US_EN_LANG_REFSET, Concepts.PREFERRED))
							.addAxiom(new Relationship(Concepts.ISA, "12345678910"), new Relationship("12345", "12345678910"))
							.addRelationship(new Relationship(Concepts.ISA, "12345678910")),
					projectBranch);

			//Version project
			codeSystemService.createVersion(codeSystem, 20200131, "Release 2020-01-31.");
		};
		methodTestDataFixture.insert();

		//when
		ResponseEntity<ConceptHistory> responseEntity = this.restTemplate.exchange(requestUrl, HttpMethod.GET, new HttpEntity<>(null), new ParameterizedTypeReference<ConceptHistory>() {
		});
		ConceptHistory conceptHistory = responseEntity.getBody();
		List<ConceptHistory.ConceptHistoryItem> history = conceptHistory.getHistory();
		ConceptHistory.ConceptHistoryItem januaryRelease = conceptHistory.getConceptHistoryItem("20200131").get();
		List<ComponentType> januaryReleaseComponentTypes = new ArrayList<>(januaryRelease.getComponentTypes());

		//then
		assertEquals(200, responseEntity.getStatusCodeValue());
		assertEquals(1, history.size()); //Concept has not changed since first release.
		assertEquals(4, januaryReleaseComponentTypes.size()); //Concept was created with Description, Relationship & Axiom
		assertEquals(ComponentType.Concept, januaryReleaseComponentTypes.get(0));
		assertEquals(ComponentType.Description, januaryReleaseComponentTypes.get(1));
		assertEquals(ComponentType.Relationship, januaryReleaseComponentTypes.get(2));
		assertEquals(ComponentType.Axiom, januaryReleaseComponentTypes.get(3));
	}

	@Test
	public void findConceptHistory_ShouldReturnExpectedConceptHistory_WhenShowFutureVersionsFlagIsFalse() throws Exception {
		//given
		String requestUrl = "http://localhost:" + port + "/browser/MAIN/BROWSE-241/concepts/123456789101112/history";
		Procedure methodTestDataFixture = () -> {
			String projectBranch = "MAIN/BROWSE-241";
			String taskBranch = "MAIN/BROWSE-241/TASK-1";
			//Create project branch
			CodeSystem codeSystem = new CodeSystem(projectBranch, projectBranch);
			codeSystemService.createCodeSystem(codeSystem);

			//Add Concepts to branch
			conceptService.create(
					new Concept("12345678910")
							.addDescription(new Description("Computer structure (computer structure)")
									.setTypeId(Concepts.FSN)
									.addLanguageRefsetMember(Concepts.US_EN_LANG_REFSET, Concepts.PREFERRED))
							.addDescription(new Description("Computer structure")
									.setTypeId(Concepts.SYNONYM)
									.addLanguageRefsetMember(Concepts.US_EN_LANG_REFSET, Concepts.PREFERRED))
							.addAxiom(new Relationship(Concepts.ISA, SNOMEDCT_ROOT)),
					projectBranch);

			conceptService.create(
					new Concept("1234567891011")
							.addDescription(new Description("Non specific site (computer structure)")
									.setTypeId(Concepts.FSN)
									.addLanguageRefsetMember(Concepts.US_EN_LANG_REFSET, Concepts.PREFERRED))
							.addDescription(new Description("Non specific site")
									.setTypeId(Concepts.SYNONYM)
									.addLanguageRefsetMember(Concepts.US_EN_LANG_REFSET, Concepts.PREFERRED))
							.addAxiom(new Relationship(Concepts.ISA, "12345678910")),
					projectBranch);

			conceptService.create(
					new Concept("123456789101112")
							.addDescription(new Description("Specific site (computer structure)")
									.setTypeId(Concepts.FSN)
									.addLanguageRefsetMember(Concepts.US_EN_LANG_REFSET, Concepts.PREFERRED))
							.addDescription(new Description("Specific site")
									.setTypeId(Concepts.SYNONYM)
									.addLanguageRefsetMember(Concepts.US_EN_LANG_REFSET, Concepts.PREFERRED))
							.addAxiom(new Relationship(Concepts.ISA, "12345678910"), new Relationship("12345", "12345678910"))
							.addRelationship(new Relationship(Concepts.ISA, "12345678910")),
					projectBranch);

			//Version project
			codeSystemService.createVersion(codeSystem, 20200131, "Release 2020-01-31.");

			//Create task branch
			branchService.create(taskBranch);

			//Update Concept with new Descriptions on task branch
			Concept concept = conceptService.find("123456789101112", projectBranch);
			concept.getDescriptions()
					.add(
							new Description("Specific")
									.setTypeId(Concepts.SYNONYM)
									.addLanguageRefsetMember(Concepts.US_EN_LANG_REFSET, Concepts.PREFERRED)
					);
			conceptService.update(concept, taskBranch);

			//Promote task
			branchMergeService.mergeBranchSync(taskBranch, projectBranch, Collections.emptySet());

			//Version project
			Date date = new Date();
			Calendar c = Calendar.getInstance();
			c.setTime(date);
			c.add(Calendar.YEAR, 1);
			date = c.getTime();
			String nextYear = new SimpleDateFormat("yyyyMMdd").format(date);

			codeSystemService.createVersion(codeSystem, Integer.parseInt(nextYear), "");
		};
		methodTestDataFixture.insert();

		//when
		ResponseEntity<ConceptHistory> responseEntity = this.restTemplate.exchange(requestUrl, HttpMethod.GET, new HttpEntity<>(null), new ParameterizedTypeReference<ConceptHistory>() {
		});
		ConceptHistory conceptHistory = responseEntity.getBody();
		List<ConceptHistory.ConceptHistoryItem> history = conceptHistory.getHistory();

		//then
		assertEquals(1, history.size()); //Future version shouldn't appear
	}

	@Test
	public void findConceptHistory_ShouldReturnExpectedConceptHistory_WhenShowFutureVersionsFlagIsTrue() throws Exception {
		//given
		String requestUrl = "http://localhost:" + port + "/browser/MAIN/BROWSE-241/concepts/123456789101112/history?showFutureVersions=true";
		Procedure methodTestDataFixture = () -> {
			String projectBranch = "MAIN/BROWSE-241";
			String taskBranch = "MAIN/BROWSE-241/TASK-1";
			//Create project branch
			CodeSystem codeSystem = new CodeSystem(projectBranch, projectBranch);
			codeSystemService.createCodeSystem(codeSystem);

			//Add Concepts to branch
			conceptService.create(
					new Concept("12345678910")
							.addDescription(new Description("Computer structure (computer structure)")
									.setTypeId(Concepts.FSN)
									.addLanguageRefsetMember(Concepts.US_EN_LANG_REFSET, Concepts.PREFERRED))
							.addDescription(new Description("Computer structure")
									.setTypeId(Concepts.SYNONYM)
									.addLanguageRefsetMember(Concepts.US_EN_LANG_REFSET, Concepts.PREFERRED))
							.addAxiom(new Relationship(Concepts.ISA, SNOMEDCT_ROOT)),
					projectBranch);

			conceptService.create(
					new Concept("1234567891011")
							.addDescription(new Description("Non specific site (computer structure)")
									.setTypeId(Concepts.FSN)
									.addLanguageRefsetMember(Concepts.US_EN_LANG_REFSET, Concepts.PREFERRED))
							.addDescription(new Description("Non specific site")
									.setTypeId(Concepts.SYNONYM)
									.addLanguageRefsetMember(Concepts.US_EN_LANG_REFSET, Concepts.PREFERRED))
							.addAxiom(new Relationship(Concepts.ISA, "12345678910")),
					projectBranch);

			conceptService.create(
					new Concept("123456789101112")
							.addDescription(new Description("Specific site (computer structure)")
									.setTypeId(Concepts.FSN)
									.addLanguageRefsetMember(Concepts.US_EN_LANG_REFSET, Concepts.PREFERRED))
							.addDescription(new Description("Specific site")
									.setTypeId(Concepts.SYNONYM)
									.addLanguageRefsetMember(Concepts.US_EN_LANG_REFSET, Concepts.PREFERRED))
							.addAxiom(new Relationship(Concepts.ISA, "12345678910"), new Relationship("12345", "12345678910"))
							.addRelationship(new Relationship(Concepts.ISA, "12345678910")),
					projectBranch);

			//Version project
			codeSystemService.createVersion(codeSystem, 20200131, "Release 2020-01-31.");

			//Create task branch
			branchService.create(taskBranch);

			//Update Concept with new Descriptions on task branch
			Concept concept = conceptService.find("123456789101112", projectBranch);
			concept.getDescriptions()
					.add(
							new Description("Specific")
									.setTypeId(Concepts.SYNONYM)
									.addLanguageRefsetMember(Concepts.US_EN_LANG_REFSET, Concepts.PREFERRED)
					);
			conceptService.update(concept, taskBranch);

			//Promote task
			branchMergeService.mergeBranchSync(taskBranch, projectBranch, Collections.emptySet());

			//Version project
			Date date = new Date();
			Calendar c = Calendar.getInstance();
			c.setTime(date);
			c.add(Calendar.YEAR, 1);
			date = c.getTime();
			String nextYear = new SimpleDateFormat("yyyyMMdd").format(date);

			codeSystemService.createVersion(codeSystem, Integer.parseInt(nextYear), "");
		};
		methodTestDataFixture.insert();

		//when
		ResponseEntity<ConceptHistory> responseEntity = this.restTemplate.exchange(requestUrl, HttpMethod.GET, new HttpEntity<>(null), new ParameterizedTypeReference<ConceptHistory>() {
		});
		ConceptHistory conceptHistory = responseEntity.getBody();
		List<ConceptHistory.ConceptHistoryItem> history = conceptHistory.getHistory();

		//then
		assertEquals(2, history.size()); //Future version should appear
	}

	@Test
	void testCreateConceptWithConcreteValueInsideAxiomRelationship() throws ServiceException {
		final Concept createdConcept = new Concept("12345678910").addAxiom(new Relationship(ISA, SNOMEDCT_ROOT),
				Relationship.newConcrete("1142135004", ConcreteValue.newDecimal("#55.5")));
		createRangeConstraint("1142135004", "dec(>#0..)");
		conceptService.create(createdConcept, MAIN);

		final Concept retrievedConcept = conceptService.find("12345678910", MAIN);

		retrievedConcept.getClassAxioms().forEach(axiom -> axiom.getRelationships().forEach(relationship -> {
			final ConcreteValue concreteValue = relationship.getConcreteValue();
			if (concreteValue != null) {
				assertEquals("55.5", concreteValue.getValue());
				assertEquals("#55.5", concreteValue.getValueWithPrefix());
				assertEquals(ConcreteValue.DataType.DECIMAL, concreteValue.getDataType());
			} else {
				assertEquals(ISA, relationship.getTypeId());
			}
		}));
	}

	@Test
	void testUpdateConceptWithConcreteValueInsideAxiomRelationship() throws ServiceException {
		final Concept createdConcept = new Concept("12345678910").addAxiom(new Relationship(ISA, SNOMEDCT_ROOT),
				Relationship.newConcrete("1142135004", ConcreteValue.newDecimal("#55.5")));
		createRangeConstraint("1142135004", "dec(>#0..)");
		conceptService.create(createdConcept, MAIN);

		final Concept updatedConcept = new Concept("12345678910").addAxiom(new Relationship(ISA, SNOMEDCT_ROOT),
				Relationship.newConcrete("1142135004", ConcreteValue.newDecimal("#55.9")));
		conceptService.update(updatedConcept, MAIN);

		final Concept retrievedConcept = conceptService.find("12345678910", MAIN);

		retrievedConcept.getClassAxioms().forEach(axiom -> axiom.getRelationships().forEach(relationship -> {
			final ConcreteValue concreteValue = relationship.getConcreteValue();
			if (concreteValue != null) {
				assertEquals("55.9", concreteValue.getValue());
				assertEquals("#55.9", concreteValue.getValueWithPrefix());
				assertEquals(ConcreteValue.DataType.DECIMAL, concreteValue.getDataType());
			} else {
				assertEquals(ISA, relationship.getTypeId());
			}
		}));
	}

	@Test
	void testBulkLoadWithNullConceptIdentifiers() throws URISyntaxException {
		//given
		List<String> conceptIds = Arrays.asList("782964007", "255314001", null, null, null, "308490002");
		ConceptBulkLoadRequest conceptBulkLoadRequest = new ConceptBulkLoadRequest(conceptIds, Collections.emptySet());
		RequestEntity<?> request = new RequestEntity<>(conceptBulkLoadRequest, HttpMethod.POST, new URI("http://localhost:" + port + "/browser/MAIN/concepts/bulk-load"));

		//when
		ResponseEntity<?> responseEntity = this.restTemplate.exchange(request, Collection.class);

		//then
		assertEquals(200, responseEntity.getStatusCodeValue());
	}

	@Test
	void testBulkLoadWithNullDescriptionIdentifiers() throws URISyntaxException {
		//given
		List<String> conceptIds = Arrays.asList("782964007", "255314001", "308490002");
		Set<String> descriptionIds = Stream.of("3756961018", "3756960017", null, null, "705033019", "451847013").collect(Collectors.toSet());
		ConceptBulkLoadRequest conceptBulkLoadRequest = new ConceptBulkLoadRequest(conceptIds, descriptionIds);
		RequestEntity<?> request = new RequestEntity<>(conceptBulkLoadRequest, HttpMethod.POST, new URI("http://localhost:" + port + "/browser/MAIN/concepts/bulk-load"));

		//when
		ResponseEntity<?> responseEntity = this.restTemplate.exchange(request, Collection.class);

		//then
		assertEquals(200, responseEntity.getStatusCodeValue());
	}

	protected interface Procedure {
		void insert() throws Exception;
	}
}
