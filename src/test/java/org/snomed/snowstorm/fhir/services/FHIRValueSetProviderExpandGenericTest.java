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
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

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
		codeSystemVersion = codeSystemService.createUpdate(codeSystem);
		conceptService.saveAllConceptsOfCodeSystemVersion(codeSystem.getConcept(), codeSystemVersion);

		// Create ValueSet that is included in a test
		HttpEntity<String> expandRequest = new HttpEntity<>("""
                {
                	"resourceType": "ValueSet",
                	"url": "http://example.com/fhir/vs/sex",
                	"version": "0.1",
                	"name": "Sex",
                	"description": "List of possible sexes for patient details capture.",
                	"status": "draft",
                	"experimental": true,
                	"compose": {
                		"include": [
                			{
                				"system": "http://terminology.hl7.org/CodeSystem/v3-ContextControl",
                				"concept": [
                					{
                						"code": "AP"
                					},
                					{
                						"code": "AN"
                					},
                					{
                						"code": "ON"
                					}
                				]
                			}
                		]
                	}
                }""", headers);
		ResponseEntity<String> expandResponse = restTemplate.exchange(baseUrl + "/ValueSet", HttpMethod.POST, expandRequest, String.class);
		assertEquals(HttpStatus.CREATED, expandResponse.getStatusCode());
	}

	@AfterEach
	public void testAfter() {
		// Delete the value set
		ResponseEntity<String> response = restTemplate.exchange(baseUrl + "/ValueSet?url=http://example.com/fhir/vs/sex&version=0.1", HttpMethod.DELETE, null, String.class);
		assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode(), response.getBody());

		// Delete the code system
		codeSystemService.deleteCodeSystemVersion(codeSystemVersion);
	}

	@Test
	public void testExpandUsingHierarchy() {
		HttpEntity<String> expandRequest = new HttpEntity<>("""
                {
                	"resourceType": "Parameters",
                	"parameter": [
                		{
                			"name": "valueSet",
                			"resource": {
                				"resourceType": "ValueSet",
                					"compose": {
                						"include": [
                							{
                								"system": "http://terminology.hl7.org/CodeSystem/v3-ContextControl",								"filter": [
                									{
                										"property": "concept",
                										"op": "is-a",
                										"value": "_ContextControlAdditive"
                									}
                								]
                							}
                						]
                					}
                				}
                			}
                		]
                }""", headers);
		ResponseEntity<String> expandResponse = restTemplate.exchange(baseUrl + "/ValueSet/$expand", HttpMethod.POST, expandRequest, String.class);
		assertEquals(HttpStatus.OK, expandResponse.getStatusCode(), expandResponse.getBody());
		ValueSet valueSet = fhirJsonParser.parseResource(ValueSet.class, expandResponse.getBody());
		assertEquals(3, valueSet.getExpansion().getContains().size());
	}

	@Test
	public void testExpandUsingHierarchyWithExclude() {
		HttpEntity<String> expandRequest = new HttpEntity<>("""
                {
                	"resourceType": "Parameters",
                	"parameter": [
                		{
                			"name": "valueSet",
                			"resource": {
                				"resourceType": "ValueSet",
                					"compose": {
                						"include": [
                							{
                								"system": "http://terminology.hl7.org/CodeSystem/v3-ContextControl",								"filter": [
                									{
                										"property": "concept",
                										"op": "is-a",
                										"value": "_ContextControlAdditive"
                									}
                								]
                							}
                						],						"exclude": [
                							{
                								"system": "http://terminology.hl7.org/CodeSystem/v3-ContextControl",
                								"concept": [
                			  						{
                										"code": "_ContextControlAdditive"
                			 						}
                								]
                							}
                						]
                					}
                				}
                			}
                		]
                }""", headers);
		ResponseEntity<String> expandResponse = restTemplate.exchange(baseUrl + "/ValueSet/$expand", HttpMethod.POST, expandRequest, String.class);
		System.out.println(expandResponse.getBody());
		assertEquals(HttpStatus.OK, expandResponse.getStatusCode(), expandResponse.getBody());
		ValueSet valueSet = fhirJsonParser.parseResource(ValueSet.class, expandResponse.getBody());
		assertEquals(2, valueSet.getExpansion().getContains().size());
	}

	@Test
	public void testExpandIncludesOtherValueSet() {
		HttpEntity<String> expandRequest = new HttpEntity<>("""
                {
                	"resourceType": "Parameters",
                	"parameter": [
                		{
                			"name": "valueSet",
                			"resource": {
                				"resourceType": "ValueSet",
                					"compose": {
                						"include": [
                							{
                								"valueSet": "http://example.com/fhir/vs/sex"							}
                						],						"exclude": [
                							{
                								"system": "http://terminology.hl7.org/CodeSystem/v3-ContextControl",
                								"concept": [
                									{
                										"code": "ON"
                									}
                								]
                							}
                						]
                					}
                				}
                			}
                		]
                }""", headers);
		ResponseEntity<String> expandResponse = restTemplate.exchange(baseUrl + "/ValueSet/$expand", HttpMethod.POST, expandRequest, String.class);
		System.out.println(expandResponse.getBody());
		assertEquals(HttpStatus.OK, expandResponse.getStatusCode(), expandResponse.getBody());
		ValueSet valueSet = fhirJsonParser.parseResource(ValueSet.class, expandResponse.getBody());
		assertEquals(2, valueSet.getExpansion().getContains().size());
	}

	@Test
	void testSearchMultipleWordsOrPrefixes() {
		// "display": "additive, propagating",
		System.out.println("----------");
		ResponseEntity<String> response = restTemplate.exchange(baseUrl + "/ValueSet/$expand?url=http://terminology.hl7.org/CodeSystem/v3-ContextControl?fhir_vs", HttpMethod.GET, null, String.class);
		System.out.println("Search no filter");
		assertExpand(response, 8, null);

		System.out.println("----------");
		response = restTemplate.exchange(baseUrl + "/ValueSet/$expand?url=http://terminology.hl7.org/CodeSystem/v3-ContextControl?fhir_vs&filter=additive", HttpMethod.GET, null, String.class);
		System.out.println("Search additive");
		System.out.println(response.getBody());
		assertExpand(response, 2, "[AP|'additive, propagating', AN|'additive, non-propagating']");

		System.out.println("----------");
		response = restTemplate.exchange(baseUrl + "/ValueSet/$expand?url=http://terminology.hl7.org/CodeSystem/v3-ContextControl?fhir_vs&filter=additive propagating", HttpMethod.GET, null, String.class);
		System.out.println("Search additive propagating");
		System.out.println(response.getBody());
		assertExpand(response, 2, "[AP|'additive, propagating', AN|'additive, non-propagating']");

		System.out.println("----------");
		response = restTemplate.exchange(baseUrl + "/ValueSet/$expand?url=http://terminology.hl7.org/CodeSystem/v3-ContextControl?fhir_vs&filter=add", HttpMethod.GET, null, String.class);
		System.out.println("Search add");
		System.out.println(response.getBody());
		assertExpand(response, 2, "[AP|'additive, propagating', AN|'additive, non-propagating']");

		System.out.println("----------");
		response = restTemplate.exchange(baseUrl + "/ValueSet/$expand?url=http://terminology.hl7.org/CodeSystem/v3-ContextControl?fhir_vs&filter=add prop non", HttpMethod.GET, null, String.class);
		System.out.println("Search add prop non");
		System.out.println(response.getBody());
		assertExpand(response, 1, "[AN|'additive, non-propagating']");

		System.out.println("----------");
	}

	private void assertExpand(ResponseEntity<String> response, int expected, String expectedCodingString) {
		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
		ValueSet valueSet = fhirJsonParser.parseResource(ValueSet.class, response.getBody());
		List<ValueSet.ValueSetExpansionContainsComponent> contains = valueSet.getExpansion().getContains();
		assertEquals(expected, contains.size());
		if (expectedCodingString != null) {
			assertEquals(expectedCodingString, toCodingsString(contains));
		}
	}

	private String toCodingsString(List<ValueSet.ValueSetExpansionContainsComponent> contains) {
		return contains.stream().map(coding -> "%s|'%s'".formatted(coding.getCode(), coding.getDisplay())).toList().toString();
	}

}
