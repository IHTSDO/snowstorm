package org.snomed.snowstorm.fhir.services;

import ca.uhn.fhir.rest.api.MethodOutcome;
import org.hl7.fhir.r4.model.ValueSet;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.snomed.snowstorm.core.data.domain.Concepts;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.io.InputStream;

import static org.junit.Assert.*;

class ValueSetProviderEclTest extends AbstractFHIRTest {
	
	@Test
	void testECLRecovery_DescOrSelf() throws FHIROperationException {
		String url = "http://localhost:" + port + "/fhir/ValueSet/$expand?url=http://snomed.info/sct?fhir_vs=ecl/<<" + Concepts.SNOMEDCT_ROOT + "&_format=json";
		ValueSet v = getValueSet(url);
		assertEquals(11,v.getExpansion().getContains().size());
	}
	
	@Test
	void testECLRecovery_DescOrSelf_Edition() throws FHIROperationException {
		String url = "http://localhost:" + port + "/fhir/ValueSet/$expand?" + 
				"url=http://snomed.info/sct/" + sampleModuleId + "?fhir_vs=ecl/<<" + Concepts.SNOMEDCT_ROOT + 
				"&_format=json";
		ValueSet v = getValueSet(url);
		//We'll get the 11 concepts defined on main (Root + 10 potatoes) 
		//plus the additional two defined for the new Edition
		//plus the concrete example
		assertEquals(14,v.getExpansion().getContains().size());
	}

	@Test
	void testECLRecovery_Self() throws FHIROperationException {
		String url = "http://localhost:" + port + "/fhir/ValueSet/$expand?url=http://snomed.info/sct/1234?fhir_vs=ecl/" + sampleSCTID +"&_format=json";
		ValueSet v = getValueSet(url);
		assertEquals(1,v.getExpansion().getContains().size());
	}
	
	@Test
	void testECLRecovery_Descriptions() throws FHIROperationException {
		String url = "http://localhost:" + port + "/fhir/ValueSet/$expand?url=http://snomed.info/sct/1234?fhir_vs=ecl/" + sampleSCTID +"&includeDesignations=true&_format=json";
		ValueSet v = getValueSet(url);
		assertEquals(1,v.getExpansion().getContains().size());
		assertFalse(v.getExpansion().getContains().get(0).getDesignation().isEmpty());
		assertTrue(v.getExpansion().getContains().get(0).getDesignation().get(0).getValue().contains("potato"));
	}
	
	@Test
	void testECLWithFilter() throws FHIROperationException {
		//Expecting 0 results when filter is applied
		String url = "http://localhost:" + port + "/fhir/ValueSet/$expand?url=http://snomed.info/sct/1234?fhir_vs=ecl/<<" + Concepts.SNOMEDCT_ROOT + "&filter=banana&_format=json";
		ValueSet v = getValueSet(url);
		assertEquals(0,v.getExpansion().getContains().size());
	}
	
	@Test
	void testECLWithOffsetCount() throws FHIROperationException {
		//Asking for 5 at a time, expect 13 total - 10 on MAIN + 3 in the sample module 
		String url = "http://localhost:" + port + "/fhir/ValueSet/$expand?url=http://snomed.info/sct/1234?fhir_vs=ecl/<" + Concepts.SNOMEDCT_ROOT + "&offset=0&count=5&_format=json";
		ValueSet v = getValueSet(url);
		assertEquals(5,v.getExpansion().getContains().size());
		assertEquals(13,v.getExpansion().getTotal());
		
		//When not specifying a module, we'll read from MAIN so only the original 10 dummy concepts there
		url = "http://localhost:" + port + "/fhir/ValueSet/$expand?url=http://snomed.info/sct?fhir_vs=ecl/<" + Concepts.SNOMEDCT_ROOT + "&offset=5&count=5&_format=json";
		v = getValueSet(url);
		assertEquals(5,v.getExpansion().getContains().size());
		assertEquals(10,v.getExpansion().getTotal());
		
		//With a total of 13 concepts and 5 per page, we expect our 3rd page (offset 2) to contain the last 3 concepts
		url = "http://localhost:" + port + "/fhir/ValueSet/$expand?url=http://snomed.info/sct/1234?fhir_vs=ecl/<" + Concepts.SNOMEDCT_ROOT + "&offset=10&count=5&_format=json";
		v = getValueSet(url);
		assertEquals(3,v.getExpansion().getContains().size());
		assertEquals(13,v.getExpansion().getTotal());
	}
	
	@Test
	void testECLWithSpecificVersion() throws FHIROperationException {
		//Asking for 5 at a time, expect 13 Total - 10 on MAIN + 3 in the sample module + 1 Root concept
		String url = "http://localhost:" + port + "/fhir/ValueSet/$expand?system-version=http://snomed.info/sct/1234/version/20190731&" + 
				"url=http://snomed.info/sct/" + sampleModuleId + "?fhir_vs=ecl/<<" + Concepts.SNOMEDCT_ROOT + 
				"&_format=json";
		ValueSet v = getValueSet(url);
		assertEquals(14,v.getExpansion().getContains().size());
	}
	
	@Test
	void testECLWithSpecificVersionFail() throws FHIROperationException {

		Assertions.assertThrows(FHIROperationException.class, () -> {
			//Asking for 5 at a time, expect 10 total
			String url = "http://localhost:" + port + "/fhir/ValueSet/$expand?system-version=http://snomed.info/sct/900000000000207008/version/19990731&" +
					"url=http://snomed.info/sct/" + sampleModuleId + "?fhir_vs=ecl/<<" + Concepts.SNOMEDCT_ROOT +
					"&_format=json";
			getValueSet(url);
		});
	}
	
	@Test
	void testImplcitValueSets() throws FHIROperationException {
		
		// ?fhir_vs -> all concepts
		// expect 13 Total - 10 on MAIN + 3 in the sample module + 1 Root concept
		String url = "http://localhost:" + port + "/fhir/ValueSet/$expand?url=http://snomed.info/sct/1234?fhir_vs&_format=json";
		ValueSet v = getValueSet(url);
		assertEquals(14,v.getExpansion().getTotal());
		
		// ?fhir_vs=refset -> all concepts representing refsets
		url = "http://localhost:" + port + "/fhir/ValueSet/$expand?url=http://snomed.info/sct/1234?fhir_vs=refset&_format=json";
		v = getValueSet(url);
		assertEquals("Two value sets in the latest release branch.", 3, v.getExpansion().getTotal());
		
		// ?fhir_vs=isa/<root concept> -> all concepts under root plus self
		url = "http://localhost:" + port + "/fhir/ValueSet/$expand?url=http://snomed.info/sct/1234?fhir_vs=isa/" + Concepts.SNOMEDCT_ROOT + "&_format=json";
		v = getValueSet(url);
		assertEquals(14,v.getExpansion().getTotal());
		
		// ?fhir_vs=refset/<refsetId> -> all concepts in that refset
		// Note that refset must be loaded on the branch for this to return
		/*url = "http://localhost:" + port + "/fhir/ValueSet/$expand?url=http://snomed.info/sct?fhir_vs=refset/" + Concepts.REFSET_SAME_AS_ASSOCIATION + "&_format=json";
		v = getValueSet(url);
		assertEquals(1,v.getExpansion().getTotal());*/
	}
	
	private ValueSet getValueSet(String url) throws FHIROperationException {
		ResponseEntity<String> response = this.restTemplate.exchange(url, HttpMethod.GET, defaultRequestEntity, String.class);
		checkForError(response);
		return fhirJsonParser.parseResource(ValueSet.class, response.getBody());
	}
	
	@Test
	void testExplicitValueSetExpansion() {
		ClassLoader classloader = Thread.currentThread().getContextClassLoader();
		InputStream is = classloader.getResourceAsStream("dummy-fhir-content/exampleVS_ECL_testdata_descendants.json");
		assertNotNull(is);
		ValueSet exampleVS = fhirJsonParser.parseResource(ValueSet.class, is);
		String vsJson = fhirJsonParser.encodeResourceToString(exampleVS);
		storeVs("reason-for-encounter", vsJson);
		
		//Now expand that ValueSet we just saved
		String url = baseUrl + "/reason-for-encounter/$expand";
		ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
		ValueSet savedVS = fhirJsonParser.parseResource(ValueSet.class, response.getBody());
		assertEquals(14, savedVS.getExpansion().getTotal());
		restTemplate.delete(baseUrl + "/reason-for-encounter");
	}
	
	@Test
	void testECLWithUnpublishedVersion() throws FHIROperationException {
		//Asking for 5 at a time, expect 13 Total - 10 on MAIN + 3 in the sample module + 1 Root concept
		String url = "http://localhost:" + port + "/fhir/ValueSet/$expand?system-version=http://snomed.info/xsct/1234&" + 
				"url=http://snomed.info/sct/" + sampleModuleId + "?fhir_vs=ecl/<<" + Concepts.SNOMEDCT_ROOT + 
				"&_format=json";
		ValueSet v = getValueSet(url);
		assertEquals(14,v.getExpansion().getContains().size());
	}
	
	private void storeVs(String id, String vsJson) {
		HttpEntity<String> request = new HttpEntity<>(vsJson, headers);
		ResponseEntity<MethodOutcome> response = restTemplate.exchange(baseUrl + "/" + id, HttpMethod.PUT, request, MethodOutcome.class);
		assertEquals(HttpStatus.OK, response.getStatusCode());
		MethodOutcome outcome = response.getBody();
		assertNull(outcome);
	}
	
}
