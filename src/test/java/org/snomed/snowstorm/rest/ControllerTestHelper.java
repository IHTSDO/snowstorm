package org.snomed.snowstorm.rest;

import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;

import java.net.URI;
import java.util.Calendar;
import java.util.GregorianCalendar;

import static java.lang.String.format;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class ControllerTestHelper {

	static void waitForStatus(ResponseEntity<String> createdResponseWithLocationHeader, String requiredStatus, String failStatus, HttpHeaders userHeaders, TestRestTemplate restTemplate) {
		waitForStatus(createdResponseWithLocationHeader, requiredStatus, failStatus, userHeaders, 30, restTemplate);
	}

	static void waitForStatus(ResponseEntity<String> createdResponseWithLocationHeader, String requiredStatus, String failStatus, HttpHeaders userHeaders, int timeoutSeconds, TestRestTemplate restTemplate) {
		GregorianCalendar timeout = new GregorianCalendar();
		timeout.add(Calendar.SECOND, timeoutSeconds);

		URI location = createdResponseWithLocationHeader.getHeaders().getLocation();
		String latestStatus;
		do {
			System.out.println("Get " + location.toString());

			ResponseEntity<StatusHolder> responseEntity = restTemplate.exchange(new RequestEntity<>(userHeaders, HttpMethod.GET, location), StatusHolder.class);
			assertEquals(200, responseEntity.getStatusCodeValue(), "Job status check response code.");

			StatusHolder statusHolder = responseEntity.getBody();
			if (statusHolder == null) {
				fail("Status object is null");
			}
			latestStatus = statusHolder.getStatus();
			if (requiredStatus.equals(latestStatus)) {
				return;
			}
			if (failStatus != null && failStatus.equals(latestStatus)) {
				fail(format("Actual status matched failure status '%s'.", failStatus));
			}
			try {
				Thread.sleep(1_000);
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			}
		} while (new GregorianCalendar().before(timeout));
		fail(format("Timeout while waiting for status %s, latest status was %s.", requiredStatus, latestStatus));
	}

	private static final class StatusHolder {
		private String status;

		public String getStatus() {
			return status;
		}

		void setStatus(String status) {
			this.status = status;
		}
	}

}
