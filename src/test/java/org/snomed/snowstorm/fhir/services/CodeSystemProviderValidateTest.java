package org.snomed.snowstorm.fhir.services;

import org.hl7.fhir.r4.model.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.snomed.snowstorm.TestConfig;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = TestConfig.class)
public class CodeSystemProviderValidateTest extends AbstractFHIRTest {
	
	@Test
	public void testValidateCode() throws FHIROperationException {
		//Test recovery using 
		String url = "http://localhost:" + port + "/fhir/CodeSystem/$validate-code?system=http://snomed.info/sct&code=" + sampleSCTID;
		Parameters p = get(url);
		String result = toString(getProperty(p, "result"));
		assertEquals("true", result);
		
		//Alternative URLs using coding saying the same thing
		url = "http://localhost:" + port + "/fhir/CodeSystem/$validate-code?coding=http://snomed.info/sct|" + sampleSCTID;
		p = get(url);
		result = toString(getProperty(p, "result"));
		assertEquals("true", result);
		
		//Known not present
		url = "http://localhost:" + port + "/fhir/CodeSystem/$validate-code?system=http://snomed.info/sct&code=1234501";
		p = get(url);
		result = toString(getProperty(p, "result"));
		assertEquals("false", result);
		
		//Also check the preferred term
		url = "http://localhost:" + port + "/fhir/CodeSystem/$validate-code?coding=http://snomed.info/sct|" + sampleSCTID;
		url += "&display=Baked potato 1";
		p = get(url);
		result = toString(getProperty(p, "result"));
		assertEquals("true", result);
		String msg = toString(getProperty(p, "message"));
		assertNull(msg);  //Display is the PT so we don't expect any message
		
		url = "http://localhost:" + port + "/fhir/CodeSystem/$validate-code?coding=http://snomed.info/sct|" + sampleSCTID;
		url += "&display=Baked potato 1 (substance)";
		p = get(url);
		result = toString(getProperty(p, "result"));
		assertEquals("true", result);
		msg = toString(getProperty(p, "message"));
		assertNotNull(msg);  //Display is not ege PT so we expect a message
		
		//Check for completely wrong display value
		url = "http://localhost:" + port + "/fhir/CodeSystem/$validate-code?coding=http://snomed.info/sct|" + sampleSCTID;
		url += "&display=foo";
		p = get(url);
		result = toString(getProperty(p, "result"));
		assertEquals("false", result);
		//TODO However we do get the actual PT here, so check that
	}
	
}
