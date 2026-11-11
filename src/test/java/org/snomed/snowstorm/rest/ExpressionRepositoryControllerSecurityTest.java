package org.snomed.snowstorm.rest;

import com.google.common.collect.Lists;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.core.data.domain.Concepts;
import org.snomed.snowstorm.core.data.services.ConceptService;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.snomed.snowstorm.core.data.services.postcoordination.ExpressionRepositoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.*;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.snomed.snowstorm.core.data.domain.Concepts.ISA;

class ExpressionRepositoryControllerSecurityTest extends AbstractControllerSecurityTest {
	@Autowired
	private ExpressionRepositoryService expressionRepositoryService;

	@Autowired
	private ConceptService conceptService;

	private static final String VALID_EXPRESSION = "83152002 |Oophorectomy|";

	@BeforeEach
	void setUp() throws ServiceException {
		List<Concept> concepts = Lists.newArrayList(new Concept(Concepts.SNOMEDCT_ROOT), new Concept("71388002").addFSN("Procedure").addRelationship(ISA, Concepts.SNOMEDCT_ROOT), new Concept("83152002").addFSN("Oophorectomy").addRelationship(ISA, "71388002"));
		conceptService.batchCreate(concepts, "MAIN");
		conceptService.batchCreate(concepts, "MAIN/SNOMEDCT-A");
	}

	@Test
	void retrieveAllExpressions_noAuthRequired() throws URISyntaxException {
		RequestEntity<Object> request = new RequestEntity<>(HttpMethod.GET, new URI(url + "/MAIN/expressions"));
		testStatusCode(HttpStatus.OK, userWithoutRoleHeaders, request);
		testStatusCode(HttpStatus.OK, authorHeaders, request);
		testStatusCode(HttpStatus.OK, extensionAuthorHeaders, request);
		testStatusCode(HttpStatus.OK, multiExtensionAuthorHeaders, request);
		testStatusCode(HttpStatus.OK, extensionAdminHeaders, request);
		testStatusCode(HttpStatus.OK, globalAdminHeaders, request);
	}

	@Test
	void createExpression_requiresAuthorRole() throws URISyntaxException {
		// MAIN — only int-author-group members are authorised
		testStatusCode(HttpStatus.FORBIDDEN, userWithoutRoleHeaders, putRequest("MAIN"));
		testStatusCode(HttpStatus.OK, authorHeaders, putRequest("MAIN"));
		testStatusCode(HttpStatus.FORBIDDEN, extensionAuthorHeaders, putRequest("MAIN"));
		testStatusCode(HttpStatus.OK, multiExtensionAuthorHeaders, putRequest("MAIN"));
		testStatusCode(HttpStatus.FORBIDDEN, extensionAdminHeaders, putRequest("MAIN"));
		testStatusCode(HttpStatus.FORBIDDEN, globalAdminHeaders, putRequest("MAIN"));

		// MAIN/SNOMEDCT-A — only extensionA-author-group members are authorised
		testStatusCode(HttpStatus.FORBIDDEN, userWithoutRoleHeaders, putRequest("MAIN/SNOMEDCT-A"));
		testStatusCode(HttpStatus.FORBIDDEN, authorHeaders, putRequest("MAIN/SNOMEDCT-A"));
		testStatusCode(HttpStatus.OK, extensionAuthorHeaders, putRequest("MAIN/SNOMEDCT-A"));
		testStatusCode(HttpStatus.OK, multiExtensionAuthorHeaders, putRequest("MAIN/SNOMEDCT-A"));
		testStatusCode(HttpStatus.FORBIDDEN, extensionAdminHeaders, putRequest("MAIN/SNOMEDCT-A"));
		testStatusCode(HttpStatus.FORBIDDEN, globalAdminHeaders, putRequest("MAIN/SNOMEDCT-A"));
	}

	@Test
	void createExpression_crossCodeSystemAccessBlocked() throws URISyntaxException {
		// Demonstrates the fix for MAINT-3132: an author on one CodeSystem must not be able
		// to write expressions into a different CodeSystem's branch.
		// Requests are always fired so both the status and data assertions are independent checks.

		// International author: authorised on MAIN, must be blocked from the extension branch.
		ResponseEntity<Object> mainWrite = testExchange(authorHeaders, putRequest("MAIN"));
		ResponseEntity<Object> extensionBlockedWrite = testExchange(authorHeaders, putRequest("MAIN/SNOMEDCT-A"));
		assertEquals(HttpStatus.OK, mainWrite.getStatusCode());
		assertEquals(HttpStatus.FORBIDDEN, extensionBlockedWrite.getStatusCode());
		assertEquals(0, expressionRepositoryService.findAll("MAIN/SNOMEDCT-A", PageRequest.of(0, 10)).getTotalElements(), "International author must not have written any expressions to the extension branch");

		// Extension author: authorised on MAIN/SNOMEDCT-A, must be blocked from the international branch.
		ResponseEntity<Object> extensionWrite = testExchange(extensionAuthorHeaders, putRequest("MAIN/SNOMEDCT-A"));
		ResponseEntity<Object> mainBlockedWrite = testExchange(extensionAuthorHeaders, putRequest("MAIN"));
		assertEquals(HttpStatus.OK, extensionWrite.getStatusCode());
		assertEquals(HttpStatus.FORBIDDEN, mainBlockedWrite.getStatusCode());
		assertEquals(1, expressionRepositoryService.findAll("MAIN", PageRequest.of(0, 10)).getTotalElements(), "Extension author must not have added expressions to the international branch");
	}

	@Test
	void transformExpression_requiresAuthorRole() throws URISyntaxException {
		// MAIN — only int-author-group members are authorised
		testStatusCode(HttpStatus.FORBIDDEN, userWithoutRoleHeaders, postRequest("MAIN"));
		testStatusCode(HttpStatus.OK, authorHeaders, postRequest("MAIN"));
		testStatusCode(HttpStatus.FORBIDDEN, extensionAuthorHeaders, postRequest("MAIN"));
		testStatusCode(HttpStatus.OK, multiExtensionAuthorHeaders, postRequest("MAIN"));
		testStatusCode(HttpStatus.FORBIDDEN, extensionAdminHeaders, postRequest("MAIN"));
		testStatusCode(HttpStatus.FORBIDDEN, globalAdminHeaders, postRequest("MAIN"));

		// MAIN/SNOMEDCT-A — only extensionA-author-group members are authorised
		testStatusCode(HttpStatus.FORBIDDEN, userWithoutRoleHeaders, postRequest("MAIN/SNOMEDCT-A"));
		testStatusCode(HttpStatus.FORBIDDEN, authorHeaders, postRequest("MAIN/SNOMEDCT-A"));
		testStatusCode(HttpStatus.OK, extensionAuthorHeaders, postRequest("MAIN/SNOMEDCT-A"));
		testStatusCode(HttpStatus.OK, multiExtensionAuthorHeaders, postRequest("MAIN/SNOMEDCT-A"));
		testStatusCode(HttpStatus.FORBIDDEN, extensionAdminHeaders, postRequest("MAIN/SNOMEDCT-A"));
		testStatusCode(HttpStatus.FORBIDDEN, globalAdminHeaders, postRequest("MAIN/SNOMEDCT-A"));
	}

	private RequestEntity<Object> putRequest(String branch) throws URISyntaxException {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		return new RequestEntity<>(Map.of("closeToUserForm", VALID_EXPRESSION), headers, HttpMethod.PUT, new URI(url + "/" + branch + "/expressions"));
	}

	private RequestEntity<Object> postRequest(String branch) throws URISyntaxException {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		return new RequestEntity<>(Map.of("closeToUserForm", VALID_EXPRESSION), headers, HttpMethod.POST, new URI(url + "/" + branch + "/expressions/transform"));
	}
}
