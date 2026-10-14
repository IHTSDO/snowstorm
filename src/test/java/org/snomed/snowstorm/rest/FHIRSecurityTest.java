package org.snomed.snowstorm.rest;

import org.hl7.fhir.r4.model.IdType;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;

class FHIRSecurityTest extends AbstractControllerSecurityTest {

	private static final String VALUE_SET_JSON = """
			{
			  "resourceType": "ValueSet",
			  "id": "pip-1338",
			  "url": "http://example.com/fhir/ValueSet/pip-1338",
			  "version": "1",
			  "status": "active",
			  "compose": {"include": [{"system": "http://snomed.info/sct", "concept": [{"code": "404684003"}]}]}
			}""";

	@Test
	void valueSetWritesRequireGlobalAdmin() throws URISyntaxException {
		URI valueSetUri = new URI(url + "/fhir/ValueSet/pip-1338");
		RequestEntity<Object> update = new RequestEntity<>(VALUE_SET_JSON, fhirJsonHeaders(), HttpMethod.PUT, valueSetUri);
		testStatusCode(HttpStatus.FORBIDDEN, userWithoutRoleHeaders, update);
		testStatusCode(HttpStatus.FORBIDDEN, authorHeaders, update);
		testStatusCode(HttpStatus.FORBIDDEN, extensionAdminHeaders, update);
		testStatusCode(HttpStatus.OK, globalAdminHeaders, update);

		RequestEntity<Object> read = new RequestEntity<>(HttpMethod.GET, valueSetUri);
		testStatusCode(HttpStatus.OK, userWithoutRoleHeaders, read);

		RequestEntity<Object> delete = new RequestEntity<>(HttpMethod.DELETE, valueSetUri);
		testStatusCode(HttpStatus.FORBIDDEN, userWithoutRoleHeaders, delete);
		testStatusCode(HttpStatus.FORBIDDEN, authorHeaders, delete);
		testStatusCode(HttpStatus.NO_CONTENT, globalAdminHeaders, delete);
	}

	@Test
	void valueSetCreateRequiresGlobalAdmin() throws URISyntaxException {
		RequestEntity<Object> create = new RequestEntity<>(VALUE_SET_JSON, fhirJsonHeaders(), HttpMethod.POST, new URI(url + "/fhir/ValueSet"));
		testStatusCode(HttpStatus.FORBIDDEN, userWithoutRoleHeaders, create);
		testStatusCode(HttpStatus.FORBIDDEN, authorHeaders, create);
		ResponseEntity<String> created = testStatusCode(HttpStatus.CREATED, globalAdminHeaders, create);

		String createdId = new IdType(created.getHeaders().getLocation().toString()).getIdPart();
		testStatusCode(HttpStatus.NO_CONTENT, globalAdminHeaders, new RequestEntity<>(HttpMethod.DELETE, new URI(url + "/fhir/ValueSet/" + createdId)));
	}

	@Test
	void uploadExternalCodeSystemRequiresGlobalAdmin() throws URISyntaxException {
		String parameters = """
				{"resourceType": "Parameters", "parameter": [
				  {"name": "system", "valueUri": "http://snomed.info/sct"},
				  {"name": "file", "valueAttachment": {"url": "file:/missing.zip"}}
				]}""";
		RequestEntity<Object> upload = new RequestEntity<>(parameters, fhirJsonHeaders(), HttpMethod.POST, new URI(url + "/fhir/CodeSystem/$upload-external-code-system"));
		testStatusCode(HttpStatus.FORBIDDEN, userWithoutRoleHeaders, upload);
		testStatusCode(HttpStatus.FORBIDDEN, authorHeaders, upload);
		// SNOMED CT uploads are rejected after the permission check, so an admin gets 400 without anything being stored.
		testStatusCode(HttpStatus.BAD_REQUEST, globalAdminHeaders, upload);
	}

	@Test
	void batchValidateCodeDoesNotRequireAdmin() throws URISyntaxException {
		String parameters = """
				{"resourceType": "Parameters", "parameter": []}""";
		RequestEntity<Object> batchValidate = new RequestEntity<>(parameters, fhirJsonHeaders(), HttpMethod.POST, new URI(url + "/fhir/ValueSet/$batch-validate-code"));
		testStatusCode(HttpStatus.OK, userWithoutRoleHeaders, batchValidate);
	}

	@Test
	void loadPackageRequiresGlobalAdmin() throws URISyntaxException, IOException {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.MULTIPART_FORM_DATA);
		File temp = Files.createTempFile("pip-1338-package", ".tgz").toFile();
		temp.deleteOnExit();
		MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
		body.add("file", new FileSystemResource(temp));
		RequestEntity<Object> loadPackage = new RequestEntity<>(body, headers, HttpMethod.POST, new URI(url + "/fhir-admin/load-package"));
		testStatusCode(HttpStatus.FORBIDDEN, userWithoutRoleHeaders, loadPackage);
		testStatusCode(HttpStatus.FORBIDDEN, authorHeaders, loadPackage);
		testStatusCode(HttpStatus.FORBIDDEN, extensionAdminHeaders, loadPackage);
		// No resourceUrls, so an admin passes the permission check and gets 400 without anything being loaded.
		testStatusCode(HttpStatus.BAD_REQUEST, globalAdminHeaders, loadPackage);
	}

	private static HttpHeaders fhirJsonHeaders() {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.parseMediaType("application/fhir+json"));
		return headers;
	}

}
