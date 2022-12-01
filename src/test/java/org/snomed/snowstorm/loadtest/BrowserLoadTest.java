package org.snomed.snowstorm.loadtest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.core.data.services.DescriptionService;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * This is to load test SNOMED browser with simulated number of users and various types of SNOMED queries.
 * 1. Taxonomy loading
 * 2. Descriptions search
 * 3. Descriptions search in multiple code systems
 * 4. Semantic tags aggregation search
 * 5. ECL search
 * 6. Concepts loading
 * 7. Concepts history search
 *
 */
public class BrowserLoadTest {
	private static final String SNOWSTORM_API_URI = "http://localhost:8080";
	private static final String COOKIE = "IMS_COOKIE";
	private static final int CONCURRENT_USERS = 1;
	private static final float USER_START_STAGGER = 1f;
	private static final int limit = 100;
	private static final String[] BRANCHES_TO_SEARCH = {"MAIN"};

	private static final Logger LOGGER = LoggerFactory.getLogger(BrowserLoadTest.class);
	private static final int TOTAL_RUN = 1;

	private RestTemplate restTemplate;

	private String loadTestBranch;

	private final Map<String, List<Float>> searches = new LinkedHashMap<>();

	public static void main(String[] args) throws InterruptedException {
		new BrowserLoadTest().run(CONCURRENT_USERS);
	}

	private void run(int concurrentUsers) throws InterruptedException {
		restTemplate = new RestTemplateBuilder().additionalInterceptors((request, body, execution) -> {
			request.getHeaders().setContentType(MediaType.APPLICATION_JSON);
			request.getHeaders().add("Cookie", COOKIE);
			ClientHttpResponse httpResponse = execution.execute(request, body);
			if (!(httpResponse.getRawStatusCode() + "").startsWith("2")) {
				LOGGER.info("Request failed. Request '{}'", new String(body));
				String responseBody = StreamUtils.copyToString(httpResponse.getBody(), Charset.defaultCharset());
				LOGGER.info("Request failed. Response {} '{}'", httpResponse.getRawStatusCode(), responseBody);
				return new ClientHttpResponseWithCachedBody(httpResponse, responseBody);
			}
			return httpResponse;
		}).rootUri(SNOWSTORM_API_URI).build();

		ExecutorService executorService = Executors.newCachedThreadPool();
		Set<Future> futures = new HashSet<>();
		for (int i = 0; i < concurrentUsers; i++) {
			// Start a user
			System.out.println("Create user " + (i + 1));
			futures.add(executorService.submit(new BrowserLoadTest.Search("user-" + (i + 1))));
			// Wait before starting another
			int millis = (int) USER_START_STAGGER * 1000;
			System.out.println("Sleep " + millis);
			Thread.sleep(millis);
		}
		futures.forEach(future -> {
			try {
				future.get();
			} catch (InterruptedException | ExecutionException e) {
				e.printStackTrace();
			}
		});

		System.out.println();
		System.out.println("Report --- with " + TOTAL_RUN + " runs");
		System.out.printf("%s concurrent users%n", concurrentUsers);
		float totalTime = 0;
		int totalSearches = 0;
		for (String operation : searches.keySet()) {
			List<Float> operationTimes = searches.get(operation);
			totalSearches += operationTimes.size();
			if (!operationTimes.isEmpty()) {
				float sum = 0;
				float max = 0;
				for (Float operationTime : operationTimes) {
					sum += operationTime;
					if (operationTime > max) {
						max = operationTime;
					}
				}
				float seconds = Math.round((sum * 100) / (float) operationTimes.size()) / 100f;
				System.out.printf("%s average = %s seconds, max = %s (%s searches)%n", operation, seconds, max, operationTimes.size());
				totalTime += sum;
			}
		}
		System.out.printf("average search time = %s seconds, total searches=%s%n", Math.round((totalTime * 100)/ (float) totalSearches/100f), totalSearches);
		executorService.shutdown();
	}


	class Search implements Runnable {

		private final String username;
		Search(String username) {
			this.username = username;
		}

		@Override
		public void run() {
			for (int i=0; i < TOTAL_RUN; i++) {
				Arrays.stream(BRANCHES_TO_SEARCH).forEach(this::runSearchQueries);
			}
		}

		private void runSearchQueries(String branch) {
			loadTestBranch = branch;
			loadTaxonomy("stated", false);
			loadTaxonomy("inferred", false);
			loadTaxonomy("stated", true);
			loadTaxonomy("inferred", true);

			// aggregations (descriptions)
			searchByDescription("organism", "english", DescriptionService.SearchMode.STANDARD, false);
			searchByDescription("organism", "english", DescriptionService.SearchMode.STANDARD, true);
			searchByDescription("organism", "english", DescriptionService.SearchMode.WHOLE_WORD, true);
			searchByDescription("organism", "english", DescriptionService.SearchMode.WHOLE_WORD, false);
			searchByDescription("organ.*", "english", DescriptionService.SearchMode.REGEX, false);
			searchByDescription("organ.*", "english", DescriptionService.SearchMode.REGEX, true);
			semanticTagAggregationSearch();
			// ECL searches
			// *.116676008 takes too long when running 5 users and resulted in 504
			searchByECL("181216001 AND (^723264001)", false);
			searchByECL(">!61968008", false);
			searchByECL("<<404684003:116676008=*", false);
			searchByECL("<<27624003", false);
			searchByECL("<<763158003", false);
			searchByECL(">>27624003", false);
			searchByECL(">>763158003", false);
			searchByECL("((<< 123037004 )) MINUS ((<< 442083009 ) OR (<< 118956008 ))", false);
			searchByECL("^ 447562003 |ICD-10 complex map reference set (foundation metadata concept)|", false);
			// ECl with concrete values
			searchByECL("*: 1142135004 |Has presentation strength numerator value (attribute)| < #50", false);

			List<String> conceptIds = Arrays.asList( "24526004", "57952007", "58944007", "62692004", "180047007",
					"233734006", "254977002", "263079005", "263084004", "283677000", "283678005");

			// loading concepts
			loadConcepts(conceptIds);
			// history search
			searchConceptHistory(conceptIds);
			// Multi search
			multiCodeSystemSearchByDescription(Arrays.asList("bleeding", "cold"), false);
			multiCodeSystemSearchByDescription(Arrays.asList("bleeding", "cold"), true);

		}

		private void searchConceptHistory(List<String> conceptIds) {
			String searchType = "Concept history search";
			long startMillis = new Date().getTime();
			for (String conceptId : conceptIds) {
				String url = "/browser/" + loadTestBranch + "/concepts/" + conceptId + "/history?showFutureVersions=false";
				ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, null, String.class);
				if (!response.getStatusCode().is2xxSuccessful()) {
					LOGGER.error("Search request not successful {} {}", url, response.getStatusCodeValue());
				}
				String searchDescription = String.format(searchType + " for %s", conceptId);
				LOGGER.info(searchDescription + " completed in {} seconds ", recordDuration(searchType, startMillis));
			}
		}

		private void loadTaxonomy(String form, boolean isDescendantCountOn) {
			long startMillis = new Date().getTime();
			String url = "/browser/" + loadTestBranch + "/concepts/" + 404684003 + "/children?form=" + form + "&includeDescendantCount=" + isDescendantCountOn;
			ResponseEntity<String> conceptResponse = restTemplate.exchange(url, HttpMethod.GET, null, String.class);
			if (!conceptResponse.getStatusCode().is2xxSuccessful()) {
				LOGGER.error("Search request not successful {} {}", url, conceptResponse.getStatusCodeValue());
			}
			String searchType = "Loading taxonomy";
			String searchDescription = String.format(searchType + " for %s with with descendant count set to %s", form, isDescendantCountOn);
			LOGGER.info(searchDescription + " completed in {} seconds ", recordDuration(searchType, startMillis));
		}
	}

	private void multiCodeSystemSearchByDescription(List<String> terms, boolean withAggregation) {
		long startMillis = new Date().getTime();
		String searchType = "Description search in multiple code systems";
		for (String term : terms) {
			String url = "/multisearch/descriptions?term=" + term + "&active=true&contentScope=ALL_PUBLISHED_CONTENT&limit=" + limit;
			if (withAggregation) {
				url = "/multisearch/descriptions/referencesets?term=" + term + "&contentScope=ALL_PUBLISHED_CONTENT&limit=" + limit;
			}

			ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, null, String.class);
			if (!response.getStatusCode().is2xxSuccessful()) {
				LOGGER.error("Search request not successful {} {}", url, response.getStatusCodeValue());
			}

			String searchDescription = String.format(searchType + " for term %s", term);
			if (withAggregation) {
				searchDescription = String.format(searchType + " with aggregations on reference sets for term %s", term);
			}
			LOGGER.info(searchDescription + " completed in {} seconds ", recordDuration(searchType, startMillis));
		}
	}

	private void semanticTagAggregationSearch() {
		long startMillis = new Date().getTime();
		String url = "/" + loadTestBranch + "/descriptions/semantictags";
		ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, null, String.class);
		if (!response.getStatusCode().is2xxSuccessful()) {
			LOGGER.error("Search request not successful {} {}", url, response.getStatusCodeValue());
		}
		String searchType = "Semantic tag aggregation search";
		String searchDescription = String.format(searchType + " on branch %s", loadTestBranch);
		LOGGER.info(searchDescription + " completed in {} seconds ", recordDuration(searchType, startMillis));
	}

	private void searchByECL(String ecl, boolean returnIdOnly) {
		long startMillis = new Date().getTime();
		String url = SNOWSTORM_API_URI + "/" + loadTestBranch + "/concepts";
		UriComponentsBuilder uriComponentsBuilder = UriComponentsBuilder.fromUriString(url)
				.queryParam("ecl", ecl)
				.queryParam("returnIdOnly", returnIdOnly)
				.queryParam("limit", limit);

		ResponseEntity<String> response = restTemplate.exchange(uriComponentsBuilder.build().toUri(), HttpMethod.GET, null, String.class);
		if (!response.getStatusCode().is2xxSuccessful()) {
			LOGGER.error("Search request not successful {} {}", url, response.getStatusCodeValue());
		}
		String searchType = "ECL search";
		String searchDescription = String.format(searchType + " for %s returnIdOnly=%s", ecl, returnIdOnly);
		LOGGER.info(searchDescription + " completed in {} seconds ", recordDuration(searchType, startMillis));
	}

	private void searchByDescription(String term, String language, DescriptionService.SearchMode searchMode, boolean groupByConcept) {
		long startMillis = new Date().getTime();
		String url = "/browser/" + loadTestBranch + "/descriptions?&limit=" + limit + "&term=" + term + "&active=true&conceptActive=true&lang=" + language
				+ "&groupByConcept=" + groupByConcept + "&searchMode=" + searchMode.name();
		ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, null, String.class);
		if (!response.getStatusCode().is2xxSuccessful()) {
			LOGGER.error("Search request not successful {} {}", url, response.getStatusCodeValue());
		}
		String searchDescription = String.format("Description search for term=%s language=%s searchMode=%s and groupByConcept=%s",
				term, language, searchMode.name(), groupByConcept);
		LOGGER.info(searchDescription + " completed in {} seconds ", recordDuration("Description search", startMillis));
	}

	private synchronized float recordDuration(String operation, long startMillis) {
		float seconds = Math.round((new Date().getTime() - startMillis) / 10f) / 100f;
		searches.computeIfAbsent(operation, i -> new ArrayList<>()).add(seconds);
		return seconds;
	}

	private void loadConcepts(List<String> conceptIds) {
		long startMillis = new Date().getTime();
		for (String conceptId : conceptIds) {
			String url = "/browser/" + loadTestBranch + "/concepts/" + conceptId;

			ResponseEntity<Concept> conceptResponse = restTemplate.exchange(url, HttpMethod.GET, null, Concept.class);
			if (!conceptResponse.getStatusCode().is2xxSuccessful()) {
				LOGGER.error("Search request not successful {} {}", url, conceptResponse.getStatusCodeValue());
			}
			Concept concept = conceptResponse.getBody();
			if (concept != null) {
				LOGGER.info("Concept {} |{}| fetched in {} seconds.", conceptId, concept.getFsn() != null ? concept.getFsn().getTerm() : "N/A",
						recordDuration("Loading concepts", startMillis));
			}
		}
	}
}
