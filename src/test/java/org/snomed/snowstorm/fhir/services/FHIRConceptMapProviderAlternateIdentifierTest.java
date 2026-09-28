package org.snomed.snowstorm.fhir.services;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.ConceptMap;
import org.hl7.fhir.r4.model.Parameters;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.snomed.snowstorm.core.data.domain.CodeSystem;
import org.snomed.snowstorm.core.data.domain.Concepts;
import org.snomed.snowstorm.core.data.domain.Identifier;
import org.snomed.snowstorm.core.data.services.CodeSystemService;
import org.snomed.snowstorm.core.data.services.IdentifierComponentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.snomed.snowstorm.fhir.services.FHIRConceptMapService.ALTERNATE_IDENTIFIER_MAP_URL;

class FHIRConceptMapProviderAlternateIdentifierTest extends AbstractFHIRTest {

	private static final String LOINC_URI = "http://loinc.org";
	private static final String LOINC_MODULE = "11010000107";
	private static final String LOINC_SCHEME = "30051010000102";
	private static final String LOINC_CODE = "LP12345-6";

	private static final String NPU_URI = "http://npu.org";
	private static final String NPU_MODULE = "11003000102";
	private static final String NPU_SCHEME = "21003000106";
	private static final String NPU_CODE = "NPU01234";

	@Autowired
	private CodeSystemService codeSystemService;

	@Autowired
	private IdentifierComponentService identifierComponentService;

	private final List<CodeSystem> createdCodeSystems = new ArrayList<>();

	@BeforeEach
	void setUpLoincExtension() {
		createExtensionWithIdentifier("SNOMEDCT-LOINCEXT", LOINC_MODULE, LOINC_SCHEME, LOINC_CODE);
	}

	@AfterEach
	void tearDownExtensions() {
		createdCodeSystems.forEach(codeSystem -> codeSystemService.deleteCodeSystemAndVersions(codeSystem, true));
		createdCodeSystems.clear();
		identifierComponentService.deleteAll();
	}

	private void createExtensionWithIdentifier(String shortName, String moduleId, String schemeId, String alternateIdentifier) {
		CodeSystem codeSystem = new CodeSystem(shortName, "MAIN/" + shortName).setUriModuleId(moduleId);
		codeSystemService.createCodeSystem(codeSystem);
		createdCodeSystems.add(codeSystem);
		identifierComponentService.createIdentifier(codeSystem.getBranchPath(),
				new Identifier(alternateIdentifier, null, true, moduleId, schemeId, sampleSCTID));
		codeSystemService.createVersion(codeSystem, 20200131, shortName + " test version");
	}

	@Test
	void testTranslateSnomedToAlternateIdentifier() {
		Parameters parameters = getParameters(baseUrl + "/ConceptMap/$translate?code=" + sampleSCTID +
				"&system=http://snomed.info/sct&version=http://snomed.info/sct/" + LOINC_MODULE +
				"&targetsystem=" + LOINC_URI + "&url=" + ALTERNATE_IDENTIFIER_MAP_URL);
		assertTrue(parameters.getParameterBool("result"));
		assertEquals(List.of(LOINC_URI + "|" + LOINC_CODE), getMatchConcepts(parameters));
		assertEquals(ALTERNATE_IDENTIFIER_MAP_URL, parameters.getParameter("match").getPart().stream()
				.filter(part -> part.getName().equals("source")).findFirst().orElseThrow().getValue().primitiveValue());

		// R5 spelling of the target system parameter
		parameters = getParameters(baseUrl + "/ConceptMap/$translate?code=" + sampleSCTID +
				"&system=http://snomed.info/sct&targetSystem=" + LOINC_URI + "&url=" + ALTERNATE_IDENTIFIER_MAP_URL);
		assertEquals(List.of(LOINC_URI + "|" + LOINC_CODE), getMatchConcepts(parameters));
	}

	@Test
	void testTranslateWithoutTargetSystemOrVersionSearchesAllAlternateSchemas() {
		Parameters parameters = getParameters(baseUrl + "/ConceptMap/$translate?code=" + sampleSCTID +
				"&system=http://snomed.info/sct&url=" + ALTERNATE_IDENTIFIER_MAP_URL);
		assertTrue(parameters.getParameterBool("result"));
		assertEquals(List.of(LOINC_URI + "|" + LOINC_CODE), getMatchConcepts(parameters));

		getParameters(baseUrl + "/ConceptMap/$translate?code=1000" +
				"&system=http://snomed.info/sct&url=" + ALTERNATE_IDENTIFIER_MAP_URL, 200, "No mapping found for code");
	}

	@Test
	void testTranslateWithoutTargetSystemOrVersionReturnsIdentifiersFromAllExtensions() {
		createExtensionWithIdentifier("SNOMEDCT-NPU", NPU_MODULE, NPU_SCHEME, NPU_CODE);

		Parameters parameters = getParameters(baseUrl + "/ConceptMap/$translate?code=" + sampleSCTID +
				"&system=http://snomed.info/sct&url=" + ALTERNATE_IDENTIFIER_MAP_URL);
		assertTrue(parameters.getParameterBool("result"));
		assertEquals(Set.of(LOINC_URI + "|" + LOINC_CODE, NPU_URI + "|" + NPU_CODE), Set.copyOf(getMatchConcepts(parameters)));

		// A version of a non alternate schema edition also searches all extensions
		parameters = getParameters(baseUrl + "/ConceptMap/$translate?code=" + sampleSCTID +
				"&system=http://snomed.info/sct&version=http://snomed.info/sct/" + Concepts.CORE_MODULE +
				"&url=" + ALTERNATE_IDENTIFIER_MAP_URL);
		assertEquals(Set.of(LOINC_URI + "|" + LOINC_CODE, NPU_URI + "|" + NPU_CODE), Set.copyOf(getMatchConcepts(parameters)));
	}

	@Test
	void testTargetSystemOrExtensionVersionLimitsToOneExtension() {
		createExtensionWithIdentifier("SNOMEDCT-NPU", NPU_MODULE, NPU_SCHEME, NPU_CODE);

		Parameters parameters = getParameters(baseUrl + "/ConceptMap/$translate?code=" + sampleSCTID +
				"&system=http://snomed.info/sct&targetsystem=" + NPU_URI + "&url=" + ALTERNATE_IDENTIFIER_MAP_URL);
		assertEquals(List.of(NPU_URI + "|" + NPU_CODE), getMatchConcepts(parameters));

		parameters = getParameters(baseUrl + "/ConceptMap/$translate?code=" + sampleSCTID +
				"&system=http://snomed.info/sct&version=http://snomed.info/sct/" + LOINC_MODULE +
				"&url=" + ALTERNATE_IDENTIFIER_MAP_URL);
		assertEquals(List.of(LOINC_URI + "|" + LOINC_CODE), getMatchConcepts(parameters));

		// The version of one extension with the target system of another has nothing to search
		getParameters(baseUrl + "/ConceptMap/$translate?code=" + sampleSCTID +
				"&system=http://snomed.info/sct&version=http://snomed.info/sct/" + LOINC_MODULE +
				"&targetsystem=" + NPU_URI + "&url=" + ALTERNATE_IDENTIFIER_MAP_URL, 200, "No mapping found for code");

		parameters = getParameters(baseUrl + "/ConceptMap/$translate?code=" + NPU_CODE +
				"&system=" + NPU_URI + "&url=" + ALTERNATE_IDENTIFIER_MAP_URL);
		assertEquals(List.of("http://snomed.info/sct|" + sampleSCTID), getMatchConcepts(parameters));
	}

	@Test
	void testTranslateAlternateIdentifierToSnomed() {
		Parameters parameters = getParameters(baseUrl + "/ConceptMap/$translate?code=" + LOINC_CODE +
				"&system=" + LOINC_URI + "&url=" + ALTERNATE_IDENTIFIER_MAP_URL);
		assertTrue(parameters.getParameterBool("result"));
		assertEquals(List.of("http://snomed.info/sct|" + sampleSCTID), getMatchConcepts(parameters));
	}

	@Test
	void testUnsupportedTargetSystemAndListing() {
		getParameters(baseUrl + "/ConceptMap/$translate?code=" + sampleSCTID +
				"&system=http://snomed.info/sct&targetsystem=http://example.org&url=" + ALTERNATE_IDENTIFIER_MAP_URL, 404, "No suitable map found");

		ResponseEntity<String> response = restTemplate.exchange(baseUrl + "/ConceptMap?url=" + ALTERNATE_IDENTIFIER_MAP_URL,
				HttpMethod.GET, defaultRequestEntity, String.class);
		expectResponse(response, 200);
		Bundle bundle = fhirJsonParser.parseResource(Bundle.class, response.getBody());
		assertEquals(1, bundle.getEntry().size());
		assertEquals(ALTERNATE_IDENTIFIER_MAP_URL, ((ConceptMap) bundle.getEntry().get(0).getResource()).getUrl());
	}

	private List<String> getMatchConcepts(Parameters parameters) {
		return parameters.getParameters("match").stream()
				.flatMap(match -> match.getPart().stream())
				.filter(part -> part.getName().equals("concept"))
				.map(part -> (Coding) part.getValue())
				.map(coding -> coding.getSystem() + "|" + coding.getCode())
				.toList();
	}
}
