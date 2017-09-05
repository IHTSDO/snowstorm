package org.ihtsdo.elasticsnomed.core.data.services.cis;

import org.ihtsdo.elasticsnomed.core.data.services.RuntimeServiceException;
import org.ihtsdo.elasticsnomed.core.data.services.ServiceException;
import org.ihtsdo.elasticsnomed.core.data.services.identifier.IdentifierStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import ch.qos.logback.core.subst.Token;

import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import javax.swing.text.html.HTMLDocument.HTMLReader.ParagraphAction;

public class CISClient implements IdentifierStorage {

	static final String SOFTWARE_NAME = "Elastic Snomed";
	private static final int SECONDS_TIMEOUT = 20;
	
	private static final String GENERATE = "generate";
	private static final String RESERVE = "reserve";
	private static final String REGISTER = "register";

	@Value("${cis.token}")
	private String token;
	
	@Value("${cis.api.url")
	private String cisApiUrl;
	
	private final RestTemplate restTemplate;
	private final Logger logger = LoggerFactory.getLogger(getClass());
	private ExecutorService executorService;

	public CISClient() {
		// TODO: inject this
		executorService = Executors.newCachedThreadPool();

		restTemplate =
				new RestTemplateBuilder()
						.rootUri(cisApiUrl)
						.additionalMessageConverters(new MappingJackson2HttpMessageConverter())
						.errorHandler(new DefaultResponseErrorHandler())
						.build();
	}
	
	@Override	
	public List<String> generate(int namespaceId, String partitionId, int quantity) throws ServiceException {
		CISGenerateRequest request = new CISGenerateRequest(namespaceId, partitionId, quantity);
		return callCIS(GENERATE, request);
	}
	
	@Override	
	public List<String> reserve(int namespaceId, String partitionId, int quantity) throws ServiceException {
		CISGenerateRequest request = new CISGenerateRequest(namespaceId, partitionId, quantity);
		return callCIS(RESERVE, request);
	}
	
	@Override
	public void registerIdentifiers(int namespaceId, Collection<String> idsAssigned) throws ServiceException {
		CISRegisterRequest request = new CISRegisterRequest(namespaceId, idsAssigned);
		callCIS(REGISTER, request);
	}

	private List<String> callCIS(String operation, Object request) throws ServiceException {
		try {
			// Request IDs
			
			CISBulkRequestResponse responseBody = restTemplate.postForObject("/sct/bulk/{operation}?token={token}", request, CISBulkRequestResponse.class, operation, token);
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
			ResponseEntity<List<CISRecord>> recordsResponse = restTemplate.exchange("/bulk/jobs/{jobId}/records?token={token}", HttpMethod.GET, null, new ParameterizedTypeReference<List<CISRecord>>() {}, bulkJobId, token);
			checkStatusCode(recordsResponse.getStatusCode());
			List<CISRecord> records = recordsResponse.getBody();
			return records.stream().map(CISRecord::getSctid).collect(Collectors.toList());
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
		CISClient client = new CISClient();
		client.token = "YOUR_DEV_TOKEN";
		List<String> ids = client.generate(0, "01", 20);
		System.out.println(ids);
	}

}
