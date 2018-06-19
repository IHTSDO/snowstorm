package org.snomed.snowstorm.core.data.services.identifier.cis;

import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import org.apache.commons.lang.time.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.core.data.services.RuntimeServiceException;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.snomed.snowstorm.core.data.services.identifier.IdentifierSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.stream.Collectors;

public class CISClient implements IdentifierSource {

	static final String SOFTWARE_NAME = "Snowstorm";

	private static final int MAX_BULK_REQUEST = 1000;

	//private static final String GENERATE = "generate";
	private static final String RESERVE = "reserve";
	private static final String REGISTER = "register";
	private static final String BULK_RESERVE = "--bulk-reserve";

	private static int STATUS_SUCCESS = 2;
	private static int STATUS_FAIL = 3;

	private static final ParameterizedTypeReference<List<CISRecord>> PARAMETERIZED_TYPE_CIS_RECORDS = new ParameterizedTypeReference<List<CISRecord>>() {};

	@Value("${cis.username}")
	private String username;

	@Value("${cis.password}")
	private String password;

	@Value("${cis.api.url}")
	private String cisApiUrl;

	@Value("${cis.timeout}")
	private int timeout;

	private String token = "";

	private RestTemplate restTemplate;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	@PostConstruct
	void init() {
		//Note that error handler has been removed.  We'll check the httpStatus in programmatically to recover error messages.
		restTemplate = new RestTemplateBuilder()
						.rootUri(cisApiUrl)
						.additionalMessageConverters(new MappingJackson2HttpMessageConverter())
						.errorHandler(new ExpressiveErrorHandler())
						.build();
		//Set timeouts
		HttpComponentsClientHttpRequestFactory restFactory =  (HttpComponentsClientHttpRequestFactory) restTemplate.getRequestFactory();
		restFactory.setReadTimeout(timeout * 1000);
		restFactory.setConnectTimeout(timeout * 1000);
		authenticate();// Fail fast
	}

	private void authenticate() {
		Map<String, String> request = new HashMap<>();
		request.put("token", token);
		try {
			restTemplate.postForObject("/authenticate", request, Map.class);
		} catch (RestClientResponseException e) {
			if (e.getRawStatusCode() == 401) {
				login();
			} else {
				throw e;
			}
		}
	}

	private void login() {
		Map<String, String> request = new HashMap<>();
		request.put("username", username);
		request.put("password", password);
		Map response = restTemplate.postForObject("/login", request, Map.class);
		token = (String) response.get("token");
	}

	@Override
	public List<Long> reserve(int namespaceId, String partitionId, int quantity) throws ServiceException {
		authenticate();
		List<Long> reservedIdentifiers = new ArrayList<>();
		int requestQuantity = MAX_BULK_REQUEST;
		while (reservedIdentifiers.size() < quantity) {
			if (requestQuantity > quantity) {
				requestQuantity = quantity - reservedIdentifiers.size();
			}
			CISGenerateRequest request = new CISGenerateRequest(namespaceId, partitionId, requestQuantity);
			reservedIdentifiers.addAll(callCis(RESERVE, request, false));
		}
		return reservedIdentifiers;
	}

	@Override
	public void registerIdentifiers(int namespaceId, Collection<Long> ids) throws ServiceException {
		authenticate();

		// Fetch the status of these ids from CIS
		CISBulkGetRequest getRequest = new CISBulkGetRequest(ids);
		ResponseEntity<List<CISRecord>> response = restTemplate.exchange("/sct/bulk/ids?token={token}", HttpMethod.POST, new HttpEntity<>(getRequest), PARAMETERIZED_TYPE_CIS_RECORDS, token);
		if (!response.getStatusCode().is2xxSuccessful()) {
			throw new ServiceException("Failed to fetch identifiers during registration. Response status code:" + response.getStatusCodeValue());
		}
		List<CISRecord> cisRecords = response.getBody();

		// Check for ids with the wrong status
		Set<String> acceptableStatuses = Sets.newHashSet("Available", "Reserved", "Assigned");
		List<CISRecord> problemCisRecords = cisRecords.stream().filter(id -> !acceptableStatuses.contains(id.getStatus())).collect(Collectors.toList());
		if (!problemCisRecords.isEmpty()) {
			throw new ServiceException("Can not register the following identifiers because they do not have status 'Available', 'Reserved' or 'Assigned': " + problemCisRecords);
		}

		// Register the ids which are not yet registered
		List<Long> cisRecordsToRegister = cisRecords.stream().filter(id -> !id.getStatus().equals("Assigned")).map(CISRecord::getSctidAsLong).collect(Collectors.toList());
		if (!cisRecordsToRegister.isEmpty()) {
			Iterable<List<Long>> partitions = Iterables.partition(cisRecordsToRegister, MAX_BULK_REQUEST);
			for (List<Long> partition : partitions) {
				CISRegisterRequest request = new CISRegisterRequest(namespaceId, partition);
				callCis(REGISTER, request, true);
			}
		}
	}

	private List<Long> callCis(String operation, CISBulkRequest request, boolean includeSchemeName) throws ServiceException {
		String bulkJobId;
		String jobInfo = operation;
		StopWatch timer = new StopWatch();
		timer.start();
		try {
			CISBulkRequestResponse responseBody;
			if (includeSchemeName) {
				responseBody = restTemplate.postForObject("/sct/bulk/{operation}?token={token}&schemeName=SNOMEDID", request, CISBulkRequestResponse.class, operation, token);
			} else {
				responseBody = restTemplate.postForObject("/sct/bulk/{operation}?token={token}", request, CISBulkRequestResponse.class, operation, token);
			}
			bulkJobId = responseBody.getId();

			// Wait for CIS bulk job to complete
			boolean warningGiven = false;
			Date warningDate = getDurationEnd((int)(timeout/3d));
			Date timeoutDate = getDurationEnd(timeout);
			CISBulkJobStatusResponse jobStatusResponse;
			jobInfo  += ". JobID:" + bulkJobId + " (" + request.size() + " records)";
			logger.info ("CIS call started for {}",jobInfo);
			do {
				jobStatusResponse = restTemplate.getForObject("/bulk/jobs/{jobId}?token={token}", CISBulkJobStatusResponse.class, bulkJobId, token);
				if (new Date().after(timeoutDate)) {
					throw new RuntimeServiceException("Timeout waiting for identifier service - " + jobInfo);
				}
				if (!warningGiven && new Date().after(warningDate)) {
					logger.warn ("CIS call taking longer than expected for {}. Last status {} , \"{}\"", jobInfo, jobStatusResponse.getStatus(), jobStatusResponse.getLog());
					warningGiven = true;
				}
				Thread.sleep(500);
			} while (Integer.parseInt(jobStatusResponse.getStatus()) < STATUS_SUCCESS);
			
			if (Integer.parseInt(jobStatusResponse.getStatus()) == STATUS_FAIL) {
				throw new ServiceException ("Failed to " + jobInfo + " due to " + jobStatusResponse.getLog());
			}

			// Fetch data
			ResponseEntity<List<CISRecord>> recordsResponse = restTemplate.exchange("/bulk/jobs/{jobId}/records?token={token}", HttpMethod.GET, null, new ParameterizedTypeReference<List<CISRecord>>() {}, bulkJobId, token);
			checkStatusCode(recordsResponse.getStatusCode());
			List<CISRecord> records = recordsResponse.getBody();
			logger.info("CIS call completed for {} in {}", jobInfo, timer.toString());
			return records.stream().map(CISRecord::getSctidAsLong).collect(Collectors.toList());
		} catch (InterruptedException | RestClientException e) {
			throw new ServiceException("Failed to " + operation + " identifiers. " + jobInfo , e);
		}
	}

	private void checkStatusCode(HttpStatus statusCode) throws RestClientException {
		if (!statusCode.is2xxSuccessful()) {
			throw new RestClientException("Failed to generate identifiers." + statusCode.getReasonPhrase());
		}
	}

	private Date getDurationEnd(int duration) {
		GregorianCalendar calendar = new GregorianCalendar();
		calendar.add(Calendar.SECOND, duration);
		return calendar.getTime();
	}

	// Used by script
	public static void main(String[] args) {
		if (args.length != 8) {
			System.err.println("Not enough arguments for CIS reserve.");
			System.exit(1);
		}

		if (!args[1].equals(BULK_RESERVE)) {
			System.err.println("Missing argument " + BULK_RESERVE);
			System.exit(1);
		}

		CISClient cisClient = new CISClient();
		cisClient.cisApiUrl = args[2];
		cisClient.username = args[3];
		cisClient.password = args[4];
		cisClient.timeout = 120;// seconds
		cisClient.init();

		int namespaceId = Integer.parseInt(args[5]);
		String partitionId = args[6];
		int quantity = Integer.parseInt(args[7]);

		System.out.println("Reserving " + quantity + " identifiers in namespace " + namespaceId + ", partitionId " + partitionId);

		try {
			cisClient.reserve(
					namespaceId,
					partitionId,
					quantity
			);
		} catch (ServiceException e) {
			e.printStackTrace();
		}
	}

}
