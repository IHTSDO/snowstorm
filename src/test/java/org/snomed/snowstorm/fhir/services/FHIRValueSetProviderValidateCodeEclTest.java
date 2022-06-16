package org.snomed.snowstorm.fhir.services;

import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.ValueSet;
import org.junit.jupiter.api.Test;
import org.snomed.snowstorm.core.data.domain.Concepts;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.io.InputStream;

import static java.lang.String.format;
import static org.junit.jupiter.api.Assertions.*;
import static org.snomed.snowstorm.fhir.config.FHIRConstants.SNOMED_URI;

class FHIRValueSetProviderValidateCodeEclTest extends AbstractFHIRTest {

	@Test
	void testImplicitValidate_DescOrSelf() {
		validateCode(baseUrl + "/ValueSet/$validate-code?" +
						"url=http://snomed.info/sct?fhir_vs=ecl/<<" + Concepts.SNOMEDCT_ROOT +
						"&system=" + SNOMED_URI +
						"&code=" + Concepts.SNOMEDCT_ROOT,
				true);
		validateCode(baseUrl + "/ValueSet/$validate-code?" +
						"url=http://snomed.info/sct?fhir_vs=ecl/<<" + Concepts.SNOMEDCT_ROOT +
						"&system=" + SNOMED_URI +
						"&code=257751006",
				true);
		validateCode(baseUrl + "/ValueSet/$validate-code?" +
						"url=http://snomed.info/sct?fhir_vs=ecl/<" + Concepts.SNOMEDCT_ROOT +
						"&system=" + SNOMED_URI +
						"&code=" + Concepts.SNOMEDCT_ROOT,
				false);
	}

	@Test
	void testImplicitValidate_DescOrSelfEncodedECL() {
		validateCode(baseUrl + "/ValueSet/$validate-code?" +
						"url=http://snomed.info/sct?fhir_vs=ecl/%3C%3C" + Concepts.SNOMEDCT_ROOT +
						"&system=" + SNOMED_URI +
						"&code=" + Concepts.SNOMEDCT_ROOT,
				true);
	}

	@Test
	void testImplicitValidate_DescOrSelf_Edition() {
		// Edition without version
		validateCode(baseUrl + "/ValueSet/$validate-code?" +
						"url=http://snomed.info/sct/1234000008?fhir_vs=ecl/%3C%3C" + Concepts.SNOMEDCT_ROOT +
						"&system=" + SNOMED_URI +
						"&code=" + Concepts.SNOMEDCT_ROOT,
				true);

		// Edition with version
		validateCode(baseUrl + "/ValueSet/$validate-code?" +
						"url=http://snomed.info/sct/1234000008/version/20190731?fhir_vs=ecl/%3C%3C" + Concepts.SNOMEDCT_ROOT +
						"&system=" + SNOMED_URI +
						"&code=" + Concepts.SNOMEDCT_ROOT,
				true);

		// Edition with bad version
		validateCode(baseUrl + "/ValueSet/$validate-code?" +
						"url=http://snomed.info/sct/1234000008/version/20990131?fhir_vs=ecl/%3C%3C" + Concepts.SNOMEDCT_ROOT +
						"&system=" + SNOMED_URI +
						"&code=" + Concepts.SNOMEDCT_ROOT,
				404, "The requested CodeSystem version (http://snomed.info/sct/1234000008/version/20990131) was not found.");

		// Unknown edition
		validateCode(baseUrl + "/ValueSet/$validate-code?" +
						"url=http://snomed.info/sct/99999?fhir_vs=ecl/%3C%3C" + Concepts.SNOMEDCT_ROOT +
						"&system=" + SNOMED_URI +
						"&code=" + Concepts.SNOMEDCT_ROOT,
				404, "The requested CodeSystem (http://snomed.info/sct/99999) was not found.");
	}

	@Test
	void testImplicitValidate_Self() {
		// Edition code in edition
		validateCode(baseUrl + "/ValueSet/$validate-code?" +
						"url=http://snomed.info/sct/1234000008?fhir_vs" +
						"&system=" + SNOMED_URI +
						"&code=257751006",
				true);

		// Edition code not in International
		validateCode(baseUrl + "/ValueSet/$validate-code?" +
						"url=http://snomed.info/sct/900000000000207008?fhir_vs" +
						"&system=" + SNOMED_URI +
						"&code=257751106",
				false);
	}

	@Test
	void testImplicitValidate_Display() {
		validateCode(baseUrl + "/ValueSet/$validate-code?" +
						"url=http://snomed.info/sct?fhir_vs=ecl/" + sampleSCTID +
						"&system=" + SNOMED_URI +
						"&code=257751006" +
						"&display=Baked potato 1",
				true);
		validateCode(baseUrl + "/ValueSet/$validate-code?" +
						"url=http://snomed.info/sct?fhir_vs=ecl/" + sampleSCTID +
						"&system=" + SNOMED_URI +
						"&code=257751006" +
						"&display=Baked potato",
				false, "The code '257751006' was found in the ValueSet, however the display 'Baked potato' did not match any designations.");
	}

	@Test
	void testECLWithSpecificCodingVersion() {
		// Edition code is not in International
		validateCode(baseUrl + "/ValueSet/$validate-code?" +
						"url=http://snomed.info/sct?fhir_vs" +
						"&system=" + SNOMED_URI +
						"&code=99990000009" +
						"&systemVersion=http://snomed.info/sct/1234000008",
				false);

		// Using incorrectly named "system-version" param
		validateCode(baseUrl + "/ValueSet/$validate-code?" +
						"url=http://snomed.info/sct?fhir_vs" +
						"&system=" + SNOMED_URI +
						"&code=138875005" +
						"&system-version=http://snomed.info/sct/900000000000207008",
				400, "Parameter name 'system-version' is not applicable to this operation. Please use 'systemVersion' instead.");

		// Code "systemVersion" version matches the resolved value set version
		validateCode(baseUrl + "/ValueSet/$validate-code?" +
						"url=http://snomed.info/sct?fhir_vs" +
						"&system=" + SNOMED_URI +
						"&code=138875005" +
						"&systemVersion=http://snomed.info/sct/900000000000207008",
				true, "{\"name\":\"version\",\"valueString\":\"http://snomed.info/sct/900000000000207008/version/20190131\"}");

		// Code "systemVersion" version matches the resolved value set version (extension)
		validateCode(baseUrl + "/ValueSet/$validate-code?" +
						"url=http://snomed.info/sct?fhir_vs" +
						"&system=" + SNOMED_URI +
						"&code=138875005" +
						"&systemVersion=http://snomed.info/sct/1234000008",
				true, "{\"name\":\"version\",\"valueString\":\"http://snomed.info/sct/1234000008/version/20190731\"}");

		validateCode(baseUrl + "/ValueSet/$validate-code?" +
						"url=http://snomed.info/sct/900000000000207008?fhir_vs" +
						"&system=" + SNOMED_URI +
						"&code=138875005" +
						"&systemVersion=http://snomed.info/sct/1234000008",
				false, "The system 'http://snomed.info/sct' is included in this ValueSet but the version 'http://snomed.info/sct/1234000008' is not.");

	}

	@Test
	void testECLWithUnknownVersionFail() {
		String url = baseUrl + "/ValueSet/$expand?system-version=http://snomed.info/sct|http://snomed.info/sct/900000000000207008/version/19990731&" +
				"url=http://snomed.info/sct?fhir_vs=ecl/<<" + Concepts.SNOMEDCT_ROOT +
				"&_format=json";
		validateCode(url, 404, "The requested CodeSystem version (http://snomed.info/sct/900000000000207008/version/19990731) was not found.");
	}

	@Test
	void testExplicitValueSetExpansion() {
		ClassLoader classloader = Thread.currentThread().getContextClassLoader();
		InputStream is = classloader.getResourceAsStream("dummy-fhir-content/exampleVS_ECL_testdata_descendants.json");
		assertNotNull(is);
		ValueSet exampleVS = fhirJsonParser.parseResource(ValueSet.class, is);
		String vsJson = fhirJsonParser.encodeResourceToString(exampleVS);
		storeVs("reason-for-encounter", vsJson);

		try {
			//Now expand that ValueSet we just saved
			String url = baseUrl + "/ValueSet/reason-for-encounter/$expand";
			ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
			ValueSet savedVS = fhirJsonParser.parseResource(ValueSet.class, response.getBody());
			assertEquals(14, savedVS.getExpansion().getTotal(), () -> "Body: " + response.getBody());
		} finally {
			deleteVs("reason-for-encounter");
		}
	}

	private Parameters validateCode(String url, boolean expectedResult) {
		return validateCode(url, 200, null, expectedResult);
	}

	private Parameters validateCode(String url, boolean expectedResult, String expectBodyContains) {
		return validateCode(url, 200, expectBodyContains, expectedResult);
	}

	private Parameters validateCode(String url, int expectedStatusCode, String expectBodyContains) {
		return validateCode(url, expectedStatusCode, expectBodyContains, false);
	}

	private Parameters validateCode(String url, int expectedStatusCode, String expectBodyContains, boolean expectedResult) {
		ResponseEntity<String> response = this.restTemplate.exchange(url, HttpMethod.GET, defaultRequestEntity, String.class);
		expectResponse(response, expectedStatusCode, expectBodyContains);
		System.out.println(response.getBody());
		if (expectedStatusCode == 200) {
			Parameters parameters = fhirJsonParser.parseResource(Parameters.class, response.getBody());
			boolean actualResult = parameters.getParameterBool("result");
			assertEquals(expectedResult, actualResult, format("Expected result '%s' but got '%s'", expectedResult, actualResult));
			return parameters;
		}
		if (expectedResult) {
			fail("Expected result = true but status code != 200.");
		}
		return null;
	}

}
