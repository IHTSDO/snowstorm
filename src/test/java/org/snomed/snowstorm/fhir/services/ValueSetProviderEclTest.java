package org.snomed.snowstorm.fhir.services;

import org.hl7.fhir.r4.model.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.snomed.snowstorm.TestConfig;
import org.snomed.snowstorm.core.data.domain.Concepts;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.snomed.snowstorm.fhir.config.FHIRConstants;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.*;
import org.springframework.test.context.junit4.SpringRunner;

import ca.uhn.fhir.rest.api.MethodOutcome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = TestConfig.class)
public class ValueSetProviderEclTest extends AbstractFHIRTest implements FHIRConstants {
	
	String baseUrl;
	HttpHeaders headers;
	
	@Before
	synchronized public void setup() throws ServiceException, InterruptedException {
		baseUrl = "http://localhost:" + port + "/fhir/ValueSet";
		headers = new HttpHeaders();
		headers.setContentType(new MediaType("application", "fhir+json", StandardCharsets.UTF_8));
		headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
		super.setup();
	}
	
	@Test
	public void testECLRecovery_DescOrSelf() throws FHIROperationException {
		String url = "http://localhost:" + port + "/fhir/ValueSet/$expand?url=http://snomed.info/sct?fhir_vs=ecl/<<" + Concepts.SNOMEDCT_ROOT + "&_format=json";
		ValueSet v = getValueSet(url);
		assertEquals(11,v.getExpansion().getContains().size());
	}
	
	@Test
	public void testECLRecovery_DescOrSelf_Edition() throws FHIROperationException {
		String url = "http://localhost:" + port + "/fhir/ValueSet/$expand?" + 
				"url=http://snomed.info/sct/" + sampleModuleId + "?fhir_vs=ecl/<<" + Concepts.SNOMEDCT_ROOT + 
				"&_format=json";
		ValueSet v = getValueSet(url);
		//We'll get the 11 concepts defined on main (Root + 10 potatoes) 
		//plus the additional two defined for the new Edition
		assertEquals(13,v.getExpansion().getContains().size());
	}

	@Test
	public void testECLRecovery_Self() throws FHIROperationException {
		String url = "http://localhost:" + port + "/fhir/ValueSet/$expand?url=http://snomed.info/sct?fhir_vs=ecl/" + sampleSCTID +"&_format=json";
		ValueSet v = getValueSet(url);
		assertEquals(1,v.getExpansion().getContains().size());
	}
	
	@Test
	public void testECLRecovery_Descriptions() throws FHIROperationException {
		String url = "http://localhost:" + port + "/fhir/ValueSet/$expand?url=http://snomed.info/sct?fhir_vs=ecl/" + sampleSCTID +"&includeDesignations=true&_format=json";
		ValueSet v = getValueSet(url);
		assertEquals(1,v.getExpansion().getContains().size());
		assertFalse(v.getExpansion().getContains().get(0).getDesignation().isEmpty());
		assertTrue(v.getExpansion().getContains().get(0).getDesignation().get(0).getValue().contains("potato"));
	}
	
	@Test
	public void testECLWithFilter() throws FHIROperationException {
		//Expecting 0 results when filter is applied
		String url = "http://localhost:" + port + "/fhir/ValueSet/$expand?url=http://snomed.info/sct?fhir_vs=ecl/<<" + Concepts.SNOMEDCT_ROOT + "&filter=banana&_format=json";
		ValueSet v = getValueSet(url);
		assertEquals(0,v.getExpansion().getContains().size());
	}
	
	@Test
	public void testECLWithOffsetCount() throws FHIROperationException {
		//Asking for 5 at a time, expect 10 total
		String url = "http://localhost:" + port + "/fhir/ValueSet/$expand?url=http://snomed.info/sct?fhir_vs=ecl/<" + Concepts.SNOMEDCT_ROOT + "&offset=0&count=5&_format=json";
		ValueSet v = getValueSet(url);
		assertEquals(5,v.getExpansion().getContains().size());
		assertEquals(10,v.getExpansion().getTotal());
		
		url = "http://localhost:" + port + "/fhir/ValueSet/$expand?url=http://snomed.info/sct?fhir_vs=ecl/<" + Concepts.SNOMEDCT_ROOT + "&offset=1&count=5&_format=json";
		v = getValueSet(url);
		assertEquals(5,v.getExpansion().getContains().size());
		assertEquals(10,v.getExpansion().getTotal());
		
		url = "http://localhost:" + port + "/fhir/ValueSet/$expand?url=http://snomed.info/sct?fhir_vs=ecl/<" + Concepts.SNOMEDCT_ROOT + "&offset=2&count=5&_format=json";
		v = getValueSet(url);
		assertEquals(0,v.getExpansion().getContains().size());
		assertEquals(10,v.getExpansion().getTotal());
	}
	
	@Test
	public void testECLWithSpecificVersion() throws FHIROperationException {
		//Asking for 5 at a time, expect 10 total
		String url = "http://localhost:" + port + "/fhir/ValueSet/$expand?system-version=http://snomed.info/sct/900000000000207008/version/20190731&" + 
				"url=http://snomed.info/sct/" + sampleModuleId + "?fhir_vs=ecl/<<" + Concepts.SNOMEDCT_ROOT + 
				"&_format=json";
		ValueSet v = getValueSet(url);
		assertEquals(13,v.getExpansion().getContains().size());
	}
	
	@Test(expected=FHIROperationException.class)
	public void testECLWithSpecificVersionFail() throws FHIROperationException {
		//Asking for 5 at a time, expect 10 total
		String url = "http://localhost:" + port + "/fhir/ValueSet/$expand?system-version=http://snomed.info/sct/900000000000207008/version/19990731&" + 
				"url=http://snomed.info/sct/" + sampleModuleId + "?fhir_vs=ecl/<<" + Concepts.SNOMEDCT_ROOT + 
				"&_format=json";
		getValueSet(url);
	}
	
	@Test
	public void testImplcitValueSets() throws FHIROperationException {
		
		// ?fhir_vs -> all concepts
		String url = "http://localhost:" + port + "/fhir/ValueSet/$expand?url=http://snomed.info/sct?fhir_vs&_format=json";
		ValueSet v = getValueSet(url);
		assertEquals(11,v.getExpansion().getTotal());
		
		// ?fhir_vs=refset -> all concepts representing refsets
		url = "http://localhost:" + port + "/fhir/ValueSet/$expand?url=http://snomed.info/sct?fhir_vs=refset&_format=json";
		v = getValueSet(url);
		assertEquals("Two value sets in the latest release branch.", 2, v.getExpansion().getTotal());
		
		// ?fhir_vs=isa/<root concept> -> all concepts under root plus self
		url = "http://localhost:" + port + "/fhir/ValueSet/$expand?url=http://snomed.info/sct?fhir_vs=isa/" + Concepts.SNOMEDCT_ROOT + "&_format=json";
		v = getValueSet(url);
		assertEquals(11,v.getExpansion().getTotal());
		
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
	public void testExplicitValueSetExpansion() {
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
		assertEquals(13, savedVS.getExpansion().getTotal());
		restTemplate.delete(baseUrl + "/reason-for-encounter");
	}
	
	private void storeVs(String id, String vsJson) {
		HttpEntity<String> request = new HttpEntity<>(vsJson, headers);
		ResponseEntity<MethodOutcome> response = restTemplate.exchange(baseUrl + "/" + id, HttpMethod.PUT, request, MethodOutcome.class);
		assertEquals(HttpStatus.OK, response.getStatusCode());
		MethodOutcome outcome = response.getBody();
		assertNull(outcome);
	}
	
}
