package org.snomed.snowstorm.fhir.services;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Sets;
import org.hl7.fhir.r4.model.*;
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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = FHIRTestConfig.class)
@ActiveProfiles("test")
public class AbstractFHIRTest {

	@LocalServerPort
	protected int port = 8080;

	@Autowired
	protected TestRestTemplate restTemplate;

	@Value("${ims-security.roles.enabled}")
	private boolean rolesEnabled;

	protected static final String sampleSCTID = "257751006";
	protected static final String sampleInactiveSCTID = "60363000";
	protected static final String sampleModuleId = "1234";
	protected static final String sampleVersion = "20190731";
	protected static final String STRENGTH_NUMERATOR = "1142135004";

	protected String baseUrl;
	protected  HttpHeaders headers;

	protected IParser fhirJsonParser;
	protected HttpEntity<String> defaultRequestEntity;

	protected ObjectMapper mapper = new ObjectMapper();

	@BeforeEach
	public void setup() {
		// Setup security
		if (!rolesEnabled) {
			PreAuthenticatedAuthenticationToken authentication = new PreAuthenticatedAuthenticationToken("test-admin", "1234", Sets.newHashSet(new SimpleGrantedAuthority("USER")));
			SecurityContextHolder.setContext(new SecurityContextImpl(authentication));
		} else {
			SecurityContextHolder.clearContext();
		}

		baseUrl = "http://localhost:" + port + "/fhir/ValueSet";
		headers = new HttpHeaders();
		headers.setContentType(new MediaType("application", "fhir+json", StandardCharsets.UTF_8));
		headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

		fhirJsonParser = FhirContext.forR4().newJsonParser();

		HttpHeaders headers = new HttpHeaders();
		headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
		defaultRequestEntity = new HttpEntity<>(headers);
	}

	protected void checkForExpectedError(ResponseEntity<String> response) throws FHIROperationException {
		String body = response.getBody();
		boolean expectedErrorEncountered = false;
		if (!HttpStatus.OK.equals(response.getStatusCode())) {
			if (body.contains("\"status\":5") ||
					body.contains("\"status\":4") ||
					body.contains("\"status\":3")) {
				expectedErrorEncountered = true;
			} else if (body.contains("\"resourceType\":\"OperationOutcome\"")) {
				expectedErrorEncountered = true;
			}
		}

		if (!expectedErrorEncountered) {
			throw new FHIROperationException(OperationOutcome.IssueType.EXCEPTION, "Expected error was NOT encountered");
		}
	}

	protected void checkForError(ResponseEntity<String> response) throws FHIROperationException {
		String body = response.getBody();
		try {
			if (!HttpStatus.OK.equals(response.getStatusCode())) {
				if (body.contains("\"status\":5") ||
						body.contains("\"status\":4") ||
						body.contains("\"status\":3")) {
					ErrorResponse error = mapper.readValue(body, ErrorResponse.class);
					throw new FHIROperationException(OperationOutcome.IssueType.EXCEPTION, error.getMessage());
				} else if (body.contains("\"resourceType\":\"OperationOutcome\"")) {
					//OperationOutcome outcome = fhirJsonParser.parseResource(OperationOutcome.class, body);
					//TODO Find or write pretty print to give structured output of OperationOutcome
					throw new FHIROperationException(OperationOutcome.IssueType.EXCEPTION, body);
				}
			}
		} catch (IOException e) {
			throw new FHIROperationException(OperationOutcome.IssueType.EXCEPTION, body);
		}
	}

	protected Parameters getParameters(String url) throws FHIROperationException {
		ResponseEntity<String> response = this.restTemplate.exchange(url, HttpMethod.GET, defaultRequestEntity, String.class);
		checkForError(response);
		return fhirJsonParser.parseResource(Parameters.class, response.getBody());
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
