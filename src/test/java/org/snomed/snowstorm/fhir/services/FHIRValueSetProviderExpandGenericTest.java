package org.snomed.snowstorm.fhir.services;

import org.hl7.fhir.r4.model.CodeSystem;
import org.hl7.fhir.r4.model.ValueSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.snomed.snowstorm.fhir.domain.FHIRCodeSystemVersion;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StreamUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FHIRValueSetProviderExpandGenericTest extends AbstractFHIRTest {

	@Autowired
	private FHIRConceptService conceptService;

	@Autowired
	private FHIRCodeSystemService codeSystemService;

	private FHIRCodeSystemVersion codeSystemVersion;

	@BeforeEach
	public void testSetup() throws IOException {
		// Create generic code system for tests
		File codeSystemFile = new File("src/test/resources/dummy-fhir-content/hl7/CodeSystem-v3-ContextControl.json");
		assertTrue(codeSystemFile.isFile());
		String codeSystemString = StreamUtils.copyToString(new FileInputStream(codeSystemFile), StandardCharsets.UTF_8);
		CodeSystem codeSystem = fhirJsonParser.parseResource(CodeSystem.class, codeSystemString);
		codeSystemVersion = codeSystemService.save(codeSystem);
		conceptService.saveAllConceptsOfCodeSystemVersion(codeSystem.getConcept(), codeSystemVersion);

		// Create ValueSet that is included in a test
		HttpEntity<String> expandRequest = new HttpEntity<>("{\n" +
				"	\"resourceType\": \"ValueSet\",\n" +
				"	\"url\": \"http://example.com/fhir/vs/sex\",\n" +
				"	\"version\": \"0.1\",\n" +
				"	\"name\": \"Sex\",\n" +
				"	\"description\": \"List of possible sexes for patient details capture.\",\n" +
				"	\"status\": \"draft\",\n" +
				"	\"experimental\": true,\n" +
				"	\"compose\": {\n" +
				"		\"include\": [\n" +
				"			{\n" +
				"				\"system\": \"http://terminology.hl7.org/CodeSystem/v3-ContextControl\",\n" +
				"				\"concept\": [\n" +
				"					{\n" +
				"						\"code\": \"AP\"\n" +
				"					},\n" +
				"					{\n" +
				"						\"code\": \"AN\"\n" +
				"					},\n" +
				"					{\n" +
				"						\"code\": \"ON\"\n" +
				"					}\n" +
				"				]\n" +
				"			}\n" +
				"		]\n" +
				"	}\n" +
				"}", headers);
		ResponseEntity<String> expandResponse = restTemplate.exchange(baseUrl + "/ValueSet", HttpMethod.POST, expandRequest, String.class);
		assertEquals(HttpStatus.CREATED, expandResponse.getStatusCode());
	}

	@AfterEach
	public void testAfter() {
		// Delete the value set
		ResponseEntity<String> response = restTemplate.exchange(baseUrl + "/ValueSet?url=http://example.com/fhir/vs/sex&version=0.1", HttpMethod.DELETE, null, String.class);
		assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode(), response.getBody());

		// Delete the code system
		codeSystemService.deleteCodeSystemVersion(codeSystemVersion.getId());
	}

	@Test
	public void testExpandUsingHierarchy() {
		HttpEntity<String> expandRequest = new HttpEntity<>("{\n" +
				"	\"resourceType\": \"Parameters\",\n" +
				"	\"parameter\": [\n" +
				"		{\n" +
				"			\"name\": \"valueSet\",\n" +
				"			\"resource\": {\n" +
				"				\"resourceType\": \"ValueSet\",\n" +
				"					\"compose\": {\n" +
				"						\"include\": [\n" +
				"							{\n" +
				"								\"system\": \"http://terminology.hl7.org/CodeSystem/v3-ContextControl\"," +
				"								\"filter\": [\n" +
				"									{\n" +
				"										\"property\": \"concept\",\n" +
				"										\"op\": \"is-a\",\n" +
				"										\"value\": \"_ContextControlAdditive\"\n" +
				"									}\n" +
				"								]\n" +
				"							}\n" +
				"						]\n" +
				"					}\n" +
				"				}\n" +
				"			}\n" +
				"		]\n" +
				"}", headers);
		ResponseEntity<String> expandResponse = restTemplate.exchange(baseUrl + "/ValueSet/$expand", HttpMethod.POST, expandRequest, String.class);
		assertEquals(HttpStatus.OK, expandResponse.getStatusCode(), expandResponse.getBody());
		ValueSet valueSet = fhirJsonParser.parseResource(ValueSet.class, expandResponse.getBody());
		assertEquals(3, valueSet.getExpansion().getContains().size());
	}

	@Test
	public void testExpandUsingHierarchyWithExclude() {
		HttpEntity<String> expandRequest = new HttpEntity<>("{\n" +
				"	\"resourceType\": \"Parameters\",\n" +
				"	\"parameter\": [\n" +
				"		{\n" +
				"			\"name\": \"valueSet\",\n" +
				"			\"resource\": {\n" +
				"				\"resourceType\": \"ValueSet\",\n" +
				"					\"compose\": {\n" +
				"						\"include\": [\n" +
				"							{\n" +
				"								\"system\": \"http://terminology.hl7.org/CodeSystem/v3-ContextControl\"," +
				"								\"filter\": [\n" +
				"									{\n" +
				"										\"property\": \"concept\",\n" +
				"										\"op\": \"is-a\",\n" +
				"										\"value\": \"_ContextControlAdditive\"\n" +
				"									}\n" +
				"								]\n" +
				"							}\n" +
				"						]," +
				"						\"exclude\": [\n" +
				"							{\n" +
				"								\"system\": \"http://terminology.hl7.org/CodeSystem/v3-ContextControl\",\n" +
				"								\"concept\": [\n" +
				"			  						{\n" +
				"										\"code\": \"_ContextControlAdditive\"\n" +
				"			 						}\n" +
				"								]\n" +
				"							}\n" +
				"						]\n" +
				"					}\n" +
				"				}\n" +
				"			}\n" +
				"		]\n" +
				"}", headers);
		ResponseEntity<String> expandResponse = restTemplate.exchange(baseUrl + "/ValueSet/$expand", HttpMethod.POST, expandRequest, String.class);
		System.out.println(expandResponse.getBody());
		assertEquals(HttpStatus.OK, expandResponse.getStatusCode(), expandResponse.getBody());
		ValueSet valueSet = fhirJsonParser.parseResource(ValueSet.class, expandResponse.getBody());
		assertEquals(2, valueSet.getExpansion().getContains().size());
	}

	@Test
	public void testExpandIncludesOtherValueSet() {
		HttpEntity<String> expandRequest = new HttpEntity<>("{\n" +
				"	\"resourceType\": \"Parameters\",\n" +
				"	\"parameter\": [\n" +
				"		{\n" +
				"			\"name\": \"valueSet\",\n" +
				"			\"resource\": {\n" +
				"				\"resourceType\": \"ValueSet\",\n" +
				"					\"compose\": {\n" +
				"						\"include\": [\n" +
				"							{\n" +
				"								\"valueSet\": \"http://example.com/fhir/vs/sex\"" +
				"							}\n" +
				"						]," +
				"						\"exclude\": [\n" +
				"							{\n" +
				"								\"system\": \"http://terminology.hl7.org/CodeSystem/v3-ContextControl\",\n" +
				"								\"concept\": [\n" +
				"									{\n" +
				"										\"code\": \"ON\"\n" +
				"									}\n" +
				"								]\n" +
				"							}\n" +
				"						]\n" +
				"					}\n" +
				"				}\n" +
				"			}\n" +
				"		]\n" +
				"}", headers);
		ResponseEntity<String> expandResponse = restTemplate.exchange(baseUrl + "/ValueSet/$expand", HttpMethod.POST, expandRequest, String.class);
		System.out.println(expandResponse.getBody());
		assertEquals(HttpStatus.OK, expandResponse.getStatusCode(), expandResponse.getBody());
		ValueSet valueSet = fhirJsonParser.parseResource(ValueSet.class, expandResponse.getBody());
		assertEquals(2, valueSet.getExpansion().getContains().size());
	}

}
