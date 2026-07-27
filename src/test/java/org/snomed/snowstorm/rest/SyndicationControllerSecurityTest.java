package org.snomed.snowstorm.rest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.snomed.snowstorm.syndication.InstallationTask;
import org.snomed.snowstorm.syndication.SyndicationService;
import org.snomed.snowstorm.syndication.dto.InstallEditionRequest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.RequestEntity;

import java.net.URI;
import java.net.URISyntaxException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SyndicationControllerSecurityTest extends AbstractControllerSecurityTest {

	@MockBean
	private SyndicationService syndicationService;

	@BeforeEach
	void stubSyndicationService() {
		when(syndicationService.isConfigCredentialsConfigured()).thenReturn(false);
	}

	@Test
	void installEdition() throws URISyntaxException {
		InstallEditionRequest request = new InstallEditionRequest("http://snomed.info/sct/900000000000207008", "20250301");
		request.setUsername("mlds-user");
		request.setPassword("mlds-pass");
		InstallationTask task = new InstallationTask(request.getEditionId(), request.getVersion(), null,
				request.getUsername(), request.getPassword(), null);
		when(syndicationService.installEdition(
				eq(request.getEditionId()),
				eq(request.getVersion()),
				any(),
				eq(request.getUsername()),
				eq(request.getPassword()))).thenReturn(task.getTaskId());
		when(syndicationService.getInstallationTask(task.getTaskId())).thenReturn(task);

		RequestEntity<Object> installRequest = new RequestEntity<>(request, HttpMethod.POST, new URI(url + "/syndication/install"));

		testStatusCode(HttpStatus.FORBIDDEN, userWithoutRoleHeaders, installRequest);
		testStatusCode(HttpStatus.FORBIDDEN, authorHeaders, installRequest);
		testStatusCode(HttpStatus.FORBIDDEN, extensionAdminHeaders, installRequest);
		testStatusCode(HttpStatus.ACCEPTED, globalAdminHeaders, installRequest);
	}

	@Test
	void installEditionWithoutCredentialsWhenConfigUnset() throws URISyntaxException {
		InstallEditionRequest request = new InstallEditionRequest("http://snomed.info/sct/900000000000207008", "20250301");
		RequestEntity<Object> installRequest = new RequestEntity<>(request, globalAdminHeaders, HttpMethod.POST,
				new URI(url + "/syndication/install"));
		testStatusCode(HttpStatus.BAD_REQUEST, globalAdminHeaders, installRequest);
		verify(syndicationService, never()).installEdition(any(), any(), any(), any(), any());
	}

	@Test
	void getCredentialsConfigured() throws URISyntaxException {
		RequestEntity<Object> request = new RequestEntity<>(HttpMethod.GET, new URI(url + "/syndication/credentials-configured"));
		testStatusCode(HttpStatus.OK, userWithoutRoleHeaders, request);
	}

}
