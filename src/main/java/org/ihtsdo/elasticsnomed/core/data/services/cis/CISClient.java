package org.ihtsdo.elasticsnomed.core.data.services.cis;

import org.ihtsdo.elasticsnomed.core.data.services.RuntimeServiceException;
import org.ihtsdo.elasticsnomed.core.data.services.ServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class CISClient {

	static final String SOFTWARE_NAME = "Elastic Snomed";
	private static final int SECONDS_TIMEOUT = 20;

	private final String token;
	private final RestTemplate restTemplate;
	private final Logger logger = LoggerFactory.getLogger(getClass());
	private ExecutorService executorService;

	public CISClient(String token) {
		// TODO: inject this
		executorService = Executors.newCachedThreadPool();

		this.token = token;
		restTemplate =
				new RestTemplateBuilder()
						.rootUri("https://dev-cis.ihtsdotools.org/api")
						.additionalMessageConverters(new MappingJackson2HttpMessageConverter())
						.errorHandler(new DefaultResponseErrorHandler())
						.build();
	}

	private List<String> generate(int namespace, String partitionId, int quantity) throws ServiceException {
		try {
			// Request IDs
			CISGenerateRequest request = new CISGenerateRequest(namespace, partitionId, quantity);
			CISBulkRequestResponse responseBody = restTemplate.postForObject("/sct/bulk/generate?token={token}", request, CISBulkRequestResponse.class, token);
			String bulkJobId = responseBody.getId();

			// Wait for CIS bulk job to complete
			Date timeoutDate = getTimeoutDate();
			CISBulkJobStatusResponse jobStatusResponse;
			do {
				jobStatusResponse = restTemplate.getForObject("/bulk/jobs/{jobId}?token={token}", CISBulkJobStatusResponse.class, bulkJobId, token);
				if (new Date().after(timeoutDate)) {
					throw new RuntimeServiceException("Timeout waiting for identifier generation service. JobID:" + bulkJobId);
				}
				Thread.sleep(500);
			} while (!jobStatusResponse.getStatus().equals("2"));

			// Fetch IDs
			ResponseEntity<List<CISRecordsResponse>> recordsResponse = restTemplate.exchange("/bulk/jobs/{jobId}/records?token={token}", HttpMethod.GET, null, new ParameterizedTypeReference<List<CISRecordsResponse>>() {}, bulkJobId, token);
			checkStatusCode(recordsResponse.getStatusCode());
			List<CISRecordsResponse> records = recordsResponse.getBody();
			return records.stream().map(CISRecordsResponse::getSctid).collect(Collectors.toList());
		} catch (InterruptedException | RestClientException e) {
			throw new ServiceException("Failed to generate identifiers.", e);
		}
	}

	private void checkStatusCode(HttpStatus statusCode) throws ServiceException {
		if (!statusCode.is2xxSuccessful()) {
			throw new ServiceException("Failed to generate identifiers. " + statusCode.getReasonPhrase());
		}
	}

	private Date getTimeoutDate() {
		GregorianCalendar timeout = new GregorianCalendar();
		timeout.add(Calendar.SECOND, SECONDS_TIMEOUT);
		return timeout.getTime();
	}

	public static void main(String[] args) throws ServiceException {
		List<String> ids = new CISClient("YOUR_DEV_TOKEN").generate(0, "01", 20);
		System.out.println(ids);
	}

}
