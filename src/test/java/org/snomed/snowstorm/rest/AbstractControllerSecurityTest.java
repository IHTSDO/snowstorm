package org.snomed.snowstorm.rest;

import io.kaicode.elasticvc.api.BranchService;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.TestConfig;
import org.snomed.snowstorm.core.data.domain.CodeSystem;
import org.snomed.snowstorm.core.data.domain.security.Role;
import org.snomed.snowstorm.core.data.services.CodeSystemService;
import org.snomed.snowstorm.core.data.services.PermissionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import java.net.URI;
import java.util.Calendar;
import java.util.Collections;
import java.util.GregorianCalendar;

import static java.lang.String.format;
import static org.junit.Assert.*;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = TestConfig.class)
@ActiveProfiles("secure-test")
public abstract class AbstractControllerSecurityTest extends AbstractTest {

	@LocalServerPort
	protected int port;

	@Autowired
	protected TestRestTemplate restTemplate;

	@Autowired
	protected PermissionService permissionService;

	@Autowired
	protected BranchService branchService;

	@Autowired
	protected CodeSystemService codeSystemService;

	protected String url;
	protected HttpHeaders userWithoutRoleHeaders;
	protected HttpHeaders authorHeaders;
	protected HttpHeaders extensionAuthorHeaders;
	protected HttpHeaders multiExtensionAuthorHeaders;
	protected HttpHeaders extensionAdminHeaders;
	protected HttpHeaders globalAdminHeaders;
	protected CodeSystem extensionBCodeSystem;

	@Value("${ims-security.roles.enabled}")
	private boolean rolesEnabled;

	@Before
	public void setup() {
		assertTrue("Role based access control must be enabled for security tests.", rolesEnabled);

		url = "http://localhost:" + port;

		userWithoutRoleHeaders = new HttpHeaders();
		userWithoutRoleHeaders.add("X-AUTH-username", "userA");
		userWithoutRoleHeaders.add("X-AUTH-roles", "");

		authorHeaders = new HttpHeaders();
		authorHeaders.add("X-AUTH-username", "userB");
		authorHeaders.add("X-AUTH-roles", "int-author-group");

		extensionAuthorHeaders = new HttpHeaders();
		extensionAuthorHeaders.add("X-AUTH-username", "userC");
		extensionAuthorHeaders.add("X-AUTH-roles", "extensionA-author-group");

		multiExtensionAuthorHeaders = new HttpHeaders();
		multiExtensionAuthorHeaders.add("X-AUTH-username", "userC");
		multiExtensionAuthorHeaders.add("X-AUTH-roles", "int-author-group,extensionA-author-group");

		extensionAdminHeaders = new HttpHeaders();
		extensionAdminHeaders.add("X-AUTH-username", "userD");
		extensionAdminHeaders.add("X-AUTH-roles", "extensionA-admin,extensionB-admin");

		globalAdminHeaders = new HttpHeaders();
		globalAdminHeaders.add("X-AUTH-username", "userE");
		globalAdminHeaders.add("X-AUTH-roles", "snowstorm-admin");

		permissionService.setGlobalRoleGroups(Role.ADMIN, Collections.singleton("snowstorm-admin"));
		permissionService.setBranchRoleGroups("MAIN/SNOMEDCT-A", Role.ADMIN, Collections.singleton("extensionA-admin"));
		permissionService.setBranchRoleGroups("MAIN/SNOMEDCT-B", Role.ADMIN, Collections.singleton("extensionA-admin"));
		permissionService.setBranchRoleGroups("MAIN", Role.AUTHOR, Collections.singleton("int-author-group"));
		permissionService.setBranchRoleGroups("MAIN/SNOMEDCT-A", Role.AUTHOR, Collections.singleton("extensionA-author-group"));

		codeSystemService.createCodeSystem(new CodeSystem("SNOMEDCT", "MAIN"));

		branchService.create("MAIN/ProjectA");
		codeSystemService.createCodeSystem(new CodeSystem("SNOMEDCT-A", "MAIN/SNOMEDCT-A"));

		extensionBCodeSystem = new CodeSystem("SNOMEDCT-B", "MAIN/SNOMEDCT-B");
	}

	protected ResponseEntity<String> testStatusCode(HttpStatus expectedStatusCode, HttpHeaders imsHeaders, RequestEntity<Object> requestEntity) {
		return testStatusCode(expectedStatusCode.value(), imsHeaders, requestEntity);
	}

	protected ResponseEntity<String> testStatusCode(int expectedStatusCode, HttpHeaders imsHeaders, RequestEntity<Object> requestEntity) {
		HttpHeaders combinedHeaders = new HttpHeaders();
		combinedHeaders.addAll(requestEntity.getHeaders());
		combinedHeaders.addAll(imsHeaders);
		ResponseEntity<String> response = restTemplate.exchange(new RequestEntity<>(requestEntity.getBody(), combinedHeaders, requestEntity.getMethod(), requestEntity.getUrl()), String.class);
		assertEquals(response.getBody(), expectedStatusCode, response.getStatusCodeValue());
		return response;
	}


	protected void waitForStatus(ResponseEntity<String> response, String status, HttpHeaders userHeaders) {
		waitForStatus(response, status, userHeaders, 30);
	}

	protected void waitForStatus(ResponseEntity<String> response, String requiredStatus, HttpHeaders userHeaders, int timeoutSeconds) {
		GregorianCalendar timeout = new GregorianCalendar();
		timeout.add(Calendar.SECOND, timeoutSeconds);

		URI location = response.getHeaders().getLocation();
		String latestStatus;
		do {
			System.out.println("Get " + location.toString());

			ResponseEntity<StatusHolder> responseEntity = restTemplate.exchange(new RequestEntity<>(userHeaders, HttpMethod.GET, location), StatusHolder.class);
			assertEquals("Job status check response code.", 200, responseEntity.getStatusCodeValue());

			StatusHolder statusHolder = responseEntity.getBody();
			if (statusHolder == null) {
				fail("Status object is null");
			}
			latestStatus = statusHolder.getStatus();
			if (requiredStatus.equals(latestStatus)) {
				return;
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

		public void setStatus(String status) {
			this.status = status;
		}
	}

}
