package org.snomed.snowstorm.fhir.services;

import org.hl7.fhir.r4.model.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.snomed.snowstorm.TestConfig;
import org.snomed.snowstorm.core.data.domain.Concepts;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import static org.junit.Assert.assertEquals;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = TestConfig.class)
public class ValueSetProviderTest extends AbstractFHIRTest {
	
	@Test
	public void testECLRecovery_DescOrSelf() throws FHIROperationException {
		String url = "http://localhost:" + port + "/fhir/ValueSet/$expand?url=http://snomed.info/sct?fhir_vs=ecl/<<" + Concepts.SNOMEDCT_ROOT + "&_format=json";
		ValueSet v = get(url);
		assertEquals(11,v.getExpansion().getContains().size());
	}
	
	@Test
	public void testECLRecovery_DescOrSelf_Edition() throws FHIROperationException {
		String url = "http://localhost:" + port + "/fhir/ValueSet/$expand?url=http://snomed.info/sct/" + sampleModuleId + "?fhir_vs=ecl/<<" + Concepts.SNOMEDCT_ROOT + "&_format=json";
		ValueSet v = get(url);
		//We'll get the 11 concepts defined on main (Root + 10 potatoes) 
		//plus the additional two defined for the new Edition
		assertEquals(13,v.getExpansion().getContains().size());
	}

	@Test
	public void testECLRecovery_Self() throws FHIROperationException {
		String url = "http://localhost:" + port + "/fhir/ValueSet/$expand?url=http://snomed.info/sct?fhir_vs=ecl/" + sampleSCTID +"&_format=json";
		ValueSet v = get(url);
		assertEquals(1,v.getExpansion().getContains().size());
	}
	
	@Test
	public void testECLWithFilter() throws FHIROperationException {
		//Expecting 0 results when filter is applied
		String url = "http://localhost:" + port + "/fhir/ValueSet/$expand?url=http://snomed.info/sct?fhir_vs=ecl/<<" + Concepts.SNOMEDCT_ROOT + "&filter=banana&_format=json";
		ValueSet v = get(url);
		assertEquals(0,v.getExpansion().getContains().size());
	}
	
	@Test
	public void testECLWithOffsetCount() throws FHIROperationException {
		//Asking for 5 at a time, expect 10 total
		String url = "http://localhost:" + port + "/fhir/ValueSet/$expand?url=http://snomed.info/sct?fhir_vs=ecl/<" + Concepts.SNOMEDCT_ROOT + "&offset=0&count=5&_format=json";
		ValueSet v = get(url);
		assertEquals(5,v.getExpansion().getContains().size());
		assertEquals(10,v.getExpansion().getTotal());
		
		url = "http://localhost:" + port + "/fhir/ValueSet/$expand?url=http://snomed.info/sct?fhir_vs=ecl/<" + Concepts.SNOMEDCT_ROOT + "&offset=1&count=5&_format=json";
		v = get(url);
		assertEquals(5,v.getExpansion().getContains().size());
		assertEquals(10,v.getExpansion().getTotal());
		
		url = "http://localhost:" + port + "/fhir/ValueSet/$expand?url=http://snomed.info/sct?fhir_vs=ecl/<" + Concepts.SNOMEDCT_ROOT + "&offset=2&count=5&_format=json";
		v = get(url);
		assertEquals(0,v.getExpansion().getContains().size());
		assertEquals(10,v.getExpansion().getTotal());
	}
	
	private ValueSet get(String url) throws FHIROperationException {
		ResponseEntity<String> response = this.restTemplate.exchange(url, HttpMethod.GET, defaultRequestEntity, String.class);
		//TODO pull back HTTP code 
		String json = response.getBody();
		checkForError(json);
		return fhirJsonParser.parseResource(ValueSet.class, json);
	}
	
}
