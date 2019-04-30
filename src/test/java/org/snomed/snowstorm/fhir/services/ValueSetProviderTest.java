package org.snomed.snowstorm.fhir.services;

import org.hl7.fhir.dstu3.model.*;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.snomed.snowstorm.TestConfig;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import ca.uhn.fhir.context.FhirContext;

import static org.junit.Assert.assertEquals;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = TestConfig.class)
public class ValueSetProviderTest extends AbstractFHIRTest {

	@Test
	public void testECLRecovery_DescOrSelf() {
		String url = "http://localhost:" + port + "/fhir/ValueSet/$expand?url=http://snomed.info/sct?fhir_vs=ecl/<<" + conceptId + "&_format=json";
		String json = this.restTemplate.getForObject(url, String.class);
		ValueSet v = fhirJsonParser.parseResource(ValueSet.class, json);
		assertEquals(1,v.getExpansion().getContains().size());
	}
	
	@Test
	public void testECLRecovery_Self() {
		String url = "http://localhost:" + port + "/fhir/ValueSet/$expand?url=http://snomed.info/sct?fhir_vs=ecl/" + conceptId + "&_format=json";
		String json = this.restTemplate.getForObject(url, String.class);
		ValueSet v = fhirJsonParser.parseResource(ValueSet.class, json);
		assertEquals(1,v.getExpansion().getContains().size());
	}
	
	@Test
	public void testECLWithFilter() throws Exception {
		//Expecting 0 results when filter is applied
		String url = "http://localhost:" + port + "/fhir/ValueSet/$expand?url=http://snomed.info/sct?fhir_vs=ecl/<<" + conceptId + "&filter=banana&_format=json";
		String json = this.restTemplate.getForObject(url, String.class);
		ValueSet v = fhirJsonParser.parseResource(ValueSet.class, json);
		assertEquals(0,v.getExpansion().getContains().size());
	}
}
