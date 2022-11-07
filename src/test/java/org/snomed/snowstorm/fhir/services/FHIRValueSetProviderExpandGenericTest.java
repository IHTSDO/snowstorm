package org.snomed.snowstorm.fhir.services;

import org.hl7.fhir.r4.model.CodeSystem;
import org.hl7.fhir.r4.model.ValueSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.snomed.snowstorm.fhir.domain.FHIRCodeSystemVersion;
import org.snomed.snowstorm.fhir.pojo.FHIRCodeSystemVersionParams;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StreamUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FHIRValueSetProviderExpandGenericTest extends AbstractFHIRTest {

	@Autowired
	private FHIRConceptService conceptService;

	@Autowired
	private FHIRCodeSystemService codeSystemService;

	private static final String INLINE_VALUE_SET_ISA_OPERATION = "{\n" +
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
			"}";
	
	private static final String INLINE_VALUE_SET_ISA_OPERATION_WITH_EXCLUDE = "{\n" +
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
			"}";

	private FHIRCodeSystemVersion codeSystemVersion;

	@BeforeEach
	public void testSetup() throws IOException {
		File codeSystemFile = new File("src/test/resources/dummy-fhir-content/hl7/CodeSystem-v3-ContextControl.json");
		assertTrue(codeSystemFile.isFile());
		String codeSystemString = StreamUtils.copyToString(new FileInputStream(codeSystemFile), StandardCharsets.UTF_8);
		CodeSystem codeSystem = fhirJsonParser.parseResource(CodeSystem.class, codeSystemString);
		codeSystemVersion = codeSystemService.save(codeSystem);
		conceptService.saveAllConceptsOfCodeSystemVersion(codeSystem.getConcept(), codeSystemVersion);
	}

	@AfterEach
	public void testAfter() {
		codeSystemService.deleteCodeSystemVersion(codeSystemVersion.getId());
	}

	@Test
	public void testCreateGenericCodeSystemAndExpandUsingHierarchy() {
		HttpEntity<String> expandRequest = new HttpEntity<>(INLINE_VALUE_SET_ISA_OPERATION, headers);
		ResponseEntity<String> expandResponse = restTemplate.exchange(baseUrl + "/ValueSet/$expand", HttpMethod.POST, expandRequest, String.class);
		assertEquals(HttpStatus.OK, expandResponse.getStatusCode());
		ValueSet valueSet = fhirJsonParser.parseResource(ValueSet.class, expandResponse.getBody());
		assertEquals(3, valueSet.getExpansion().getContains().size());
	}

	@Test
	public void testCreateGenericCodeSystemAndExpandUsingHierarchyWithExclude() {
		HttpEntity<String> expandRequest = new HttpEntity<>(INLINE_VALUE_SET_ISA_OPERATION_WITH_EXCLUDE, headers);
		ResponseEntity<String> expandResponse = restTemplate.exchange(baseUrl + "/ValueSet/$expand", HttpMethod.POST, expandRequest, String.class);
		System.out.println(expandResponse.getBody());
		assertEquals(HttpStatus.OK, expandResponse.getStatusCode());
		ValueSet valueSet = fhirJsonParser.parseResource(ValueSet.class, expandResponse.getBody());
		assertEquals(2, valueSet.getExpansion().getContains().size());
	}

}
