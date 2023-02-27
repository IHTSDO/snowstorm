package org.snomed.snowstorm.fhir.services;

import org.hl7.fhir.r4.model.Parameters;
import org.junit.jupiter.api.Test;
import org.snomed.snowstorm.core.data.domain.CodeSystem;
import org.snomed.snowstorm.core.data.services.CodeSystemService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;

import java.net.URI;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class FHIRCodeSystemProviderExpressionSupplementTest extends AbstractFHIRTest {

	private final String CREATE_SUPPLEMENT_REQUEST = "{\n" +
			"  \"resourceType\" : \"CodeSystem\",\n" +
			"  \"url\" : \"http://snomed.info/sct\",\n" +
			"  \"version\" : \"http://snomed.info/xsct/11000003104\",\n" +
			"  \"content\" : \"supplement\",\n" +
			"  \"supplements\" : \"http://snomed.info/sct|http://snomed.info/sct/900000000000207008/version/20190131\"\n" +
			"}\n";

	@Autowired
	private CodeSystemService codeSystemService;

	@Test
	void testCreateDeleteExpressionRepo() {
		String url = baseUrl + "/CodeSystem";
		HttpHeaders headers = new HttpHeaders();
		headers.add("Content-Type", "application/fhir+json");
		ResponseEntity<String> response = this.restTemplate.exchange(new RequestEntity<>(CREATE_SUPPLEMENT_REQUEST, headers, HttpMethod.POST, URI.create(url)), String.class);

		expectResponse(response, 201);

		HttpHeaders responseHeaders = response.getHeaders();
		assertNotNull(responseHeaders.getLocation());
		String location = responseHeaders.getLocation().toString();
		location = location.substring(0, location.indexOf("/_history"));
		assertTrue(location.contains("/CodeSystem/"));

		List<CodeSystem> all = codeSystemService.findAll();
		Optional<CodeSystem> first = all.stream().filter(CodeSystem::isPostcoordinatedNullSafe).findFirst();
		assertTrue(first.isPresent());
		codeSystemService.deleteCodeSystemAndVersions(first.get(), true);
	}
	
	@Test
	void testSinglePropertiesRecovery() {
		String url = baseUrl + "/CodeSystem/$lookup?system=http://snomed.info/sct&code=" + sampleSCTID + "&property=normalForm&_format=json";
		Parameters p = getParameters(url);
		/*for (ParametersParameterComponent parameter : p.getParameter()) {
			logger.info(toString(parameter, ""));
		}*/
		String normalFormProperty = toString(getProperty(p, "normalForm"));
		assertNotNull(normalFormProperty);
	}

	@Test
	void testMultipleConceptPropertiesRecovery() {
		String url = baseUrl + "/CodeSystem/$lookup?system=http://snomed.info/sct&code=" + sampleSCTID + "&property=normalForm&property=sufficientlyDefined&_format=json";
		Parameters p = getParameters(url);
		
		String normalFormProperty = toString(getProperty(p, "normalForm"));
		assertNotNull(normalFormProperty);
		
		String sdProperty = toString(getProperty(p, "sufficientlyDefined"));
		assertNotNull(sdProperty);
	}

	@Test
	void testParameterActiveWhenActive() {
		String url = baseUrl + "/CodeSystem/$lookup?system=http://snomed.info/sct&code=" + sampleSCTID + "&property=normalForm&property=sufficientlyDefined&_format=json";
		Parameters p = getParameters(url);
		Boolean active = toBoolean(getProperty(p, "active"));
		assertTrue(active);
	}

	@Test
	void testParameterActiveWhenInactive() {
		String url = baseUrl + "/CodeSystem/$lookup?system=http://snomed.info/sct&code=" + sampleInactiveSCTID + "&property=normalForm&property=sufficientlyDefined&_format=json";
		Parameters p = getParameters(url);
		Boolean active = toBoolean(getProperty(p, "active"));
		assertFalse(active);
	}
	
	@Test
	void testSingleUnpublishedConceptRecovery() {
		String version = "version=http://snomed.info/xsct/" + sampleModuleId;
		String url = baseUrl + "/CodeSystem/$lookup?system=http://snomed.info/xsct&code=" + sampleSCTID + "&" + version;
		Parameters p = getParameters(url);
		assertNotNull(p);
	}
	
}
