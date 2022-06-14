package org.snomed.snowstorm.fhir.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.hl7.fhir.r4.model.ValueSet;
import org.junit.jupiter.api.Test;
import org.snomed.snowstorm.core.data.domain.Concepts;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ValueSetProviderEclTest extends AbstractFHIRTest {
	
	@Test
	void testECLRecovery_DescOrSelf() {
		String url = baseUrl + "/ValueSet/$expand?url=http://snomed.info/sct?fhir_vs=ecl/<<" + Concepts.SNOMEDCT_ROOT + "&_format=json";
		ValueSet v = getValueSet(url);
		assertEquals(11, v.getExpansion().getContains().size());
	}
	
	@Test
	void testECLRecovery_DescOrSelfEncodedECL() {
		String url = baseUrl + "/ValueSet/$expand?url=http://snomed.info/sct?fhir_vs=ecl/%3C%3C138875005&_format=json";
		ValueSet v = getValueSet(url);
		assertEquals(11, v.getExpansion().getContains().size());
	}

	@Test
	void testECLRecovery_DescOrSelf_Edition() {
		String url = baseUrl + "/ValueSet/$expand?" +
				"url=http://snomed.info/sct/" + sampleModuleId + "?fhir_vs=ecl/<<" + Concepts.SNOMEDCT_ROOT + 
				"&_format=json";
		ValueSet v = getValueSet(url);
		//We'll get the 11 concepts defined on main (Root + 10 potatoes) 
		//plus the additional two defined for the new Edition
		//plus the concrete example
		assertEquals(14,v.getExpansion().getContains().size());
	}

	@Test
	void testECLRecovery_Self() {
		String url = baseUrl + "/ValueSet/$expand?url=http://snomed.info/sct/1234?fhir_vs=ecl/" + sampleSCTID +"&_format=json";
		ValueSet v = getValueSet(url);
		assertEquals(1,v.getExpansion().getContains().size());
	}
	
	@Test
	void testECLRecovery_Descriptions() {
		String url = baseUrl + "/ValueSet/$expand?url=http://snomed.info/sct/1234?fhir_vs=ecl/" + sampleSCTID +"&includeDesignations=true&_format=json";
		ValueSet v = getValueSet(url);
		assertEquals(1, v.getExpansion().getContains().size());
		assertFalse(v.getExpansion().getContains().get(0).getDesignation().isEmpty());
		assertTrue(v.getExpansion().getContains().get(0).getDesignation().get(0).getValue().contains("potato"));
	}

	@Test
	void testECLWithFilter() {
		//Expecting 0 results when filter is applied
		String url = baseUrl + "/ValueSet/$expand?url=http://snomed.info/sct/1234?fhir_vs=ecl/<<" + Concepts.SNOMEDCT_ROOT + "&filter=banana&_format=json";
		ValueSet v = getValueSet(url);
		assertEquals(0,v.getExpansion().getContains().size());
	}
	
	@Test
	void testECLWithOffsetCount() {
		//Asking for 5 at a time, expect 13 total - 10 on MAIN + 3 in the sample module 
		String url = baseUrl + "/ValueSet/$expand?url=http://snomed.info/sct/1234?fhir_vs=ecl/<" + Concepts.SNOMEDCT_ROOT + "&offset=0&count=5&_format=json";
		ValueSet v = getValueSet(url);
		assertEquals(5,v.getExpansion().getContains().size());
		assertEquals(13,v.getExpansion().getTotal());
		
		//When not specifying a module, we'll read from MAIN so only the original 10 dummy concepts there
		url = baseUrl + "/ValueSet/$expand?url=http://snomed.info/sct?fhir_vs=ecl/<" + Concepts.SNOMEDCT_ROOT + "&offset=5&count=5&_format=json";
		v = getValueSet(url);
		assertEquals(5,v.getExpansion().getContains().size());
		assertEquals(10,v.getExpansion().getTotal());
		
		//With a total of 13 concepts and 5 per page, we expect our 3rd page to contain the last 3 concepts
		url = baseUrl + "/ValueSet/$expand?url=http://snomed.info/sct/1234?fhir_vs=ecl/<" + Concepts.SNOMEDCT_ROOT + "&offset=10&count=5&_format=json";
		v = getValueSet(url);
		assertEquals(3,v.getExpansion().getContains().size());
		assertEquals(13,v.getExpansion().getTotal());
	}
	
	@Test
	void testECLWithSpecificVersion() {
		//Asking for 5 at a time, expect 13 Total - 10 on MAIN + 3 in the sample module + 1 Root concept
		String url = baseUrl + "/ValueSet/$expand?system-version=http://snomed.info/sct|http://snomed.info/sct/1234/version/20190731&" +
				"url=http://snomed.info/sct/" + sampleModuleId + "?fhir_vs=ecl/<<" + Concepts.SNOMEDCT_ROOT + 
				"&_format=json";
		ValueSet v = getValueSet(url);
		assertEquals(14,v.getExpansion().getContains().size());
	}
	
	@Test
	void testECLWithUnknownVersionFail() {
		String url = baseUrl + "/ValueSet/$expand?system-version=http://snomed.info/sct|http://snomed.info/sct/900000000000207008/version/19990731&" +
				"url=http://snomed.info/sct?fhir_vs=ecl/<<" + Concepts.SNOMEDCT_ROOT +
				"&_format=json";
		getValueSet(url, 404, "Code system not found.");
	}
	
	@Test
	void testImplicitValueSets() {
		
		// ?fhir_vs -> all concepts
		// expect 13 Total - 10 on MAIN + 3 in the sample module + 1 Root concept
		String url = baseUrl + "/ValueSet/$expand?url=http://snomed.info/sct/1234?fhir_vs";
		ValueSet v = getValueSet(url);
		assertEquals(14,v.getExpansion().getTotal());
		
		// ?fhir_vs=refset -> all concepts representing refsets
//		url = baseUrl + "/ValueSet/$expand?url=http://snomed.info/sct/1234?fhir_vs=refset";
//		v = getValueSet(url);
//		//Language Refsets + OWLAxiom Refset + ModuleDependencyRefset created during versioning.
//		assertEquals(3, v.getExpansion().getTotal());
		
		// ?fhir_vs=isa/<root concept> -> all concepts under root plus self
		url = baseUrl + "/ValueSet/$expand?url=http://snomed.info/sct/1234?fhir_vs=isa/" + Concepts.SNOMEDCT_ROOT;
		v = getValueSet(url);
		assertEquals(14,v.getExpansion().getTotal());
		
		// ?fhir_vs=refset/<refsetId> -> all concepts in that refset
		// Note that refset must be loaded on the branch for this to return
		url = baseUrl + "/ValueSet/$expand?url=http://snomed.info/sct?fhir_vs=refset/" + Concepts.OWL_AXIOM_REFERENCE_SET + "&_format=json";
		v = getValueSet(url);
		assertEquals(10, v.getExpansion().getTotal());
	}

	private ValueSet getValueSet(String url) {
		return getValueSet(url, 200, null);
	}

	private ValueSet getValueSet(String url, int expectedStatusCode, String expectBodyContains) {
		ResponseEntity<String> response = this.restTemplate.exchange(url, HttpMethod.GET, defaultRequestEntity, String.class);
		expectResponse(response, expectedStatusCode, expectBodyContains);
		System.out.println(response.getBody());
		return expectedStatusCode == 200 ? fhirJsonParser.parseResource(ValueSet.class, response.getBody()) : null;
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
	
	@Test
	void testECLWithUnpublishedVersion() {
		//Asking for 5 at a time, expect 13 Total - 10 on MAIN + 3 in the sample module + 1 Root concept
		String url = baseUrl + "/ValueSet/$expand?system-version=http://snomed.info/xsct|http://snomed.info/xsct/1234&" +
				"url=http://snomed.info/sct/" + sampleModuleId + "?fhir_vs=ecl/<<" + Concepts.SNOMEDCT_ROOT + 
				"&_format=json";
		ValueSet v = getValueSet(url);
		assertEquals(14,v.getExpansion().getContains().size());
	}

	@Test
	void testECLWithDesignationUseContextExpansion() throws JsonProcessingException {
		String url = baseUrl + "/ValueSet/$expand?url=http://snomed.info/sct?fhir_vs=ecl/257751006&includeDesignations=true&_format=json";
		ValueSet valueSet = getValueSet(url);
		assertEquals(1, valueSet.getExpansion().getContains().size());
		List<ValueSet.ConceptReferenceDesignationComponent> designations = valueSet.getExpansion().getContains().get(0).getDesignation();
		assertEquals(3, designations.size());
		assertDesignation("Baked potato 1", "en", "http://terminology.hl7.org/CodeSystem/designation-usage", "display",
				designations.get(0));
		assertDesignation("Baked potato 1 (Substance)", "en", "http://snomed.info/sct", "900000000000003001",
				designations.get(1));
		assertDesignation("Baked potato 1", "en", "http://snomed.info/sct", "900000000000013009",
				designations.get(2));
	}

	private void assertDesignation(String expectedValue, String expectedLang, String expectedUseSystem, String expectedUseCode, ValueSet.ConceptReferenceDesignationComponent designationComponent) {
		assertEquals(expectedValue, designationComponent.getValue());
		assertEquals(expectedLang, designationComponent.getLanguage());
		if (expectedUseSystem != null) {
			assertEquals(expectedUseSystem, designationComponent.getUse().getSystem());
			assertEquals(expectedUseCode, designationComponent.getUse().getCode());
		}
	}

}
