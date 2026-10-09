package org.snomed.snowstorm.fhir.services;

import org.hl7.fhir.r4.model.CodeSystem;
import org.hl7.fhir.r4.model.Parameters;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.snomed.snowstorm.fhir.domain.FHIRCodeSystemVersion;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;

class FHIRCodeSystemProviderLookupTest extends AbstractFHIRTest {

	@Test
	void testSingleConceptRecovery() {
		String url = baseUrl + "/CodeSystem/$lookup?system=http://snomed.info/sct&code=" + sampleSCTID + "&_format=json";
		Parameters p = getParameters(url);
		assertNotNull(p);
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
		Boolean inactive = toBoolean(getProperty(p, "inactive"));
		assertFalse(inactive);
	}

	@Test
	void testParameterActiveWhenInactive() {
		String url = baseUrl + "/CodeSystem/$lookup?system=http://snomed.info/sct&code=" + sampleInactiveSCTID + "&property=normalForm&property=sufficientlyDefined&_format=json";
		Parameters p = getParameters(url);
		Boolean inactive = toBoolean(getProperty(p, "inactive"));
		assertTrue(inactive);
	}
	
	@Test
	void testSingleUnpublishedConceptRecovery() {
		String version = "version=http://snomed.info/xsct/" + sampleModuleId;
		String url = baseUrl + "/CodeSystem/$lookup?system=http://snomed.info/xsct&code=" + sampleSCTID + "&" + version;
		Parameters p = getParameters(url);
		assertNotNull(p);
	}

	// A non-SNOMED code system with designations, created and removed per test so the shared fixture's counts do not drift.
	private static final String LANG_CS = "http://example.com/fhir/CodeSystem/lookup-language-test";
	private static final String LANG_CS_JSON = """
			{ "resourceType": "CodeSystem", "url": "%s", "version": "1", "name": "LookupLanguageTest", "status": "draft", "content": "complete",
			  "concept": [
			    { "code": "NO-DISPLAY", "designation": [
			        { "language": "fr-CA", "value": "Bonjour du Canada" }, { "language": "fr", "value": "Bonjour" }, { "language": "en", "value": "Hello" } ] },
			    { "code": "BOTH", "display": "Consultation", "designation": [
			        { "language": "en", "value": "Visit" }, { "language": "en-GB", "value": "Consultation (GB)" },
			        { "language": "fr", "value": "Consultation (retiree)", "extension": [ { "url": "http://hl7.org/fhir/StructureDefinition/structuredefinition-standards-status", "valueCode": "withdrawn" } ] },
			        { "language": "fr", "value": "Consultation en francais" } ] },
			    { "code": "DISPLAY-ONLY", "display": "Plain display" } ] }""";

	private static final String FR_CS = "http://example.com/fhir/CodeSystem/lookup-language-test-fr";
	private static final String FR_CS_JSON = """
			{ "resourceType": "CodeSystem", "url": "%s", "version": "1", "name": "LookupLanguageTestFr", "status": "draft", "content": "complete", "language": "fr-CA",
			  "concept": [ { "code": "CONSULT", "display": "Consultation", "designation": [ { "language": "fr", "value": "Consultation (generique)" }, { "language": "en", "value": "Consultation (EN)" } ] } ] }""";
	@Autowired
	private FHIRConceptService fhirConceptService;

	@Autowired
	private FHIRCodeSystemService fhirCodeSystemService;

	private FHIRCodeSystemVersion languageCodeSystem;
	private FHIRCodeSystemVersion frenchCodeSystem;

	@BeforeEach
	void createLanguageCodeSystems() throws ServiceException {
		languageCodeSystem = createCodeSystem(LANG_CS_JSON.formatted(LANG_CS));
		frenchCodeSystem = createCodeSystem(FR_CS_JSON.formatted(FR_CS));
	}

	private FHIRCodeSystemVersion createCodeSystem(String json) throws ServiceException {
		CodeSystem codeSystem = fhirJsonParser.parseResource(CodeSystem.class, json);
		FHIRCodeSystemVersion version = fhirCodeSystemService.createUpdate(codeSystem);
		fhirConceptService.saveAllConceptsOfCodeSystemVersion(codeSystem.getConcept(), version);
		return version;
	}

	@AfterEach
	void deleteLanguageCodeSystems() {
		fhirCodeSystemService.deleteCodeSystemVersion(languageCodeSystem);
		fhirCodeSystemService.deleteCodeSystemVersion(frenchCodeSystem);
	}

	private String display(String code, String query, String acceptLanguage) {
		return display(LANG_CS, code, query, acceptLanguage);
	}

	private String display(String system, String code, String query, String acceptLanguage) {
		HttpHeaders headers = new HttpHeaders();
		if (acceptLanguage != null) {
			headers.set("Accept-Language", acceptLanguage);
		}
		ResponseEntity<String> response = restTemplate.exchange(baseUrl + "/CodeSystem/$lookup?system=" + system + "&code=" + code + query,
				HttpMethod.GET, new HttpEntity<>(headers), String.class);
		expectResponse(response, 200);
		return toString(getProperty(fhirJsonParser.parseResource(Parameters.class, response.getBody()), "display"));
	}

	@Test
	void testNonSnomedDisplayLanguage() {
		assertEquals("Hello", display("NO-DISPLAY", "&displayLanguage=en", null));
		assertEquals("Bonjour du Canada", display("NO-DISPLAY", "&displayLanguage=fr-CA", null));
		assertEquals("Bonjour", display("NO-DISPLAY", "&displayLanguage=fr", null));
		assertEquals("Consultation en francais", display("BOTH", "&displayLanguage=fr", null));
		assertEquals("Consultation en francais", display("BOTH", "&displayLanguage=fr-CA", null));
		assertEquals("Consultation en francais", display("BOTH", "", "fr-CA"));
		assertEquals("Consultation", display("BOTH", "", null));
		assertEquals("Consultation", display("BOTH", "&displayLanguage=en", null));
		assertEquals("Consultation", display("BOTH", "", "en-US,en;q=0.9"));
		assertEquals("Consultation (GB)", display("BOTH", "&displayLanguage=en-GB", null));
		assertEquals("Plain display", display("DISPLAY-ONLY", "&displayLanguage=fr-CA", null));
		// A French code system's own display is the French display, for fr as well as fr-CA; nothing requested means nothing changes.
		assertEquals("Consultation", display(FR_CS, "CONSULT", "&displayLanguage=fr-CA", null));
		assertEquals("Consultation", display(FR_CS, "CONSULT", "&displayLanguage=fr", null));
		assertEquals("Consultation", display(FR_CS, "CONSULT", "", null));
		assertEquals("Consultation (EN)", display(FR_CS, "CONSULT", "&displayLanguage=en", null));
	}
}
