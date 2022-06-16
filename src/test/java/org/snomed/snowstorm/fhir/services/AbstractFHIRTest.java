package org.snomed.snowstorm.fhir.services;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.rest.api.MethodOutcome;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Sets;
import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.lang.String.format;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = FHIRTestConfig.class)
@ActiveProfiles({"test", "fhir-test"})
public class AbstractFHIRTest {

	@LocalServerPort
	protected int port = 8080;

	@Autowired
	protected TestRestTemplate restTemplate;

	@Value("${ims-security.roles.enabled}")
	private boolean rolesEnabled;

	protected static final String sampleSCTID = "257751006";
	protected static final String sampleInactiveSCTID = "60363000";
	protected static final String sampleModuleId = "1234000008";
	protected static final int sampleVersion = 20190731;
	protected static final String STRENGTH_NUMERATOR = "1142135004";

	protected String baseUrl;
	protected HttpHeaders headers;

	protected IParser fhirJsonParser;
	protected HttpEntity<String> defaultRequestEntity;

	@BeforeEach
	public void setup() {
		// Setup security
		if (!rolesEnabled) {
			PreAuthenticatedAuthenticationToken authentication = new PreAuthenticatedAuthenticationToken("test-admin", "1234000008", Sets.newHashSet(new SimpleGrantedAuthority("USER")));
			SecurityContextHolder.setContext(new SecurityContextImpl(authentication));
		} else {
			SecurityContextHolder.clearContext();
		}

		baseUrl = "http://localhost:" + port + "/fhir";
		headers = new HttpHeaders();
		headers.setContentType(new MediaType("application", "fhir+json", StandardCharsets.UTF_8));
		headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

		fhirJsonParser = FhirContext.forR4().newJsonParser();

		HttpHeaders headers = new HttpHeaders();
		headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
		defaultRequestEntity = new HttpEntity<>(headers);
	}

	protected void expectResponse(ResponseEntity<String> response, int expectedStatusCode) {
		expectResponse(response, expectedStatusCode, null);
	}

	protected void expectResponse(ResponseEntity<String> response, int expectedStatusCode, String expectBodyContains) {
		String body = response.getBody();
		assertEquals(expectedStatusCode, response.getStatusCodeValue(), () -> format("Expected status code '%s' but was '%s', body: '%s'",
				expectedStatusCode, response.getStatusCode(), body));
		if (expectBodyContains != null) {
			assertNotNull(body);
			assertTrue(body.contains(expectBodyContains), () -> format("Expected body to contain '%s' but was '%s'", expectBodyContains, body));
		}
	}

	protected Parameters getParameters(String url) throws FHIROperationException {
		return getParameters(url, 200, null);
	}

	protected Parameters getParameters(String url, int statusCode, String expectBodyContains) {
		ResponseEntity<String> response = this.restTemplate.exchange(url, HttpMethod.GET, defaultRequestEntity, String.class);
		expectResponse(response, statusCode, expectBodyContains);
		return statusCode == 200 ? fhirJsonParser.parseResource(Parameters.class, response.getBody()) : null;
	}

	protected Type getProperty(Parameters params, String propertyName) {
		Map<String, Type> propertyMap = new HashMap<>();
		for (Parameters.ParametersParameterComponent p : params.getParameter()) {
			if (p.getName().equals("property")) {
				populatePropertyMap(propertyMap, p.getPart());
			}
			if (p.getName().equals(propertyName)) {
				return p.getValue();
			}
		}
		return propertyMap.get(propertyName);
	}

	protected Boolean toBoolean(Type value) {
		if (value instanceof BooleanType) {
			return ((BooleanType) value).booleanValue();
		}

		return null;
	}

	protected String toString(Type value) {
		if (value == null) {
			return null;
		}
		if (value instanceof Coding) {
			Coding codingValue = (Coding)value;
			return "[ " + codingValue.getSystem() + " : " + codingValue.getCode() + "|" + codingValue.getDisplay()  + "| ]";
		} else if (value instanceof CodeType) {
			CodeType codeValue = (CodeType)value;
			return  codeValue.getCode();
		} else if (value instanceof StringType) {
			return value.castToString(value).asStringValue();
		} else if (value instanceof BooleanType) {
			return value.castToString(value).asStringValue();
		} else {
			return value.toString();
		}
	}

	protected String toString(Parameters.ParametersParameterComponent p, String indent) {
		StringBuilder sb = new StringBuilder();
		sb.append(p.getName()).append(" (").append(p.fhirType()).append(")");
		if (p.getValue() != null) {
			sb.append(": ").append(toString(p.getValue()));
		}
		if (p.getResource() != null) {
			sb.append(": ").append(p.getResource());
		}
		for (Parameters.ParametersParameterComponent part : p.getPart()) {
			sb.append("\n").append(toString(part, indent + "  "));
		}
		return sb.toString();
	}

	void storeVs(String id, String vsJson) {
		HttpEntity<String> request = new HttpEntity<>(vsJson, headers);
		ResponseEntity<MethodOutcome> response = restTemplate.exchange(baseUrl + "/ValueSet/" + id, HttpMethod.PUT, request, MethodOutcome.class);
		assertEquals(HttpStatus.OK, response.getStatusCode());
		MethodOutcome outcome = response.getBody();
		assertNull(outcome);
	}

	void deleteVs(String id) {
		HttpEntity<String> request = new HttpEntity<>(null, headers);
		String valueSetUrl = baseUrl + "/ValueSet/" + id;
		ResponseEntity<MethodOutcome> response = restTemplate.exchange(valueSetUrl, HttpMethod.DELETE, request, MethodOutcome.class);
		assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());

		// Assert it's no longer there
		assertEquals(HttpStatus.NOT_FOUND, restTemplate.getForEntity(valueSetUrl, String.class).getStatusCode());
	}

	private void populatePropertyMap(Map<String, Type> propertyMap, List<Parameters.ParametersParameterComponent> parts) {
		String key = null;
		Type value = null;
		for (Parameters.ParametersParameterComponent part : parts) {
			if (part.getName().equals("code")) {
				key = part.getValue().castToString(part.getValue()).asStringValue();
			} else {
				value = part.getValue();
			}
		}
		propertyMap.put(key, value);
	}
}
