package org.snomed.snowstorm.rest;

import org.junit.jupiter.api.Test;
import org.snomed.snowstorm.syndication.dto.InstallEditionRequest;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.RequestEntity;

import java.net.URI;
import java.net.URISyntaxException;

class SyndicationControllerSecurityTest extends AbstractControllerSecurityTest {

	@Test
	void installEdition() throws URISyntaxException {
		InstallEditionRequest request = new InstallEditionRequest("http://snomed.info/sct/900000000000207008", "20250301");
		RequestEntity<Object> installRequest = new RequestEntity<>(request, HttpMethod.POST, new URI(url + "/syndication/install"));

		testStatusCode(HttpStatus.FORBIDDEN, userWithoutRoleHeaders, installRequest);
		testStatusCode(HttpStatus.FORBIDDEN, authorHeaders, installRequest);
		testStatusCode(HttpStatus.FORBIDDEN, extensionAdminHeaders, installRequest);
		testStatusCode(HttpStatus.ACCEPTED, globalAdminHeaders, installRequest);
	}

}
