package org.snomed.snowstorm.fhir.services;

import org.hl7.fhir.r4.model.Parameters;
import org.junit.jupiter.api.Test;

import static org.hibernate.validator.internal.util.Contracts.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.snomed.snowstorm.fhir.config.FHIRConstants.SNOMED_URI;

class FHIRCodeSystemProviderValidateTest extends AbstractFHIRTest {
	
	@Test
	void testValidateCode() {
		String version = "version=http://snomed.info/sct/1234000008";
		//Test recovery using code with version
		String url = "http://localhost:" + port + "/fhir/CodeSystem/$validate-code?url=" + SNOMED_URI + "&" + version + "&code=" + sampleSCTID;
		Parameters p = getParameters(url);
		String result = toString(getProperty(p, "result"));
		assertEquals("true", result);
		
		//Alternative URLs using coding saying the same thing
		url = "http://localhost:" + port + "/fhir/CodeSystem/$validate-code?" + version + "&coding=http://snomed.info/sct|" + sampleSCTID;
		p = getParameters(url);
		result = toString(getProperty(p, "result"));
		assertEquals("true", result);
		
		//Known not present
		url = "http://localhost:" + port + "/fhir/CodeSystem/$validate-code?url=" + SNOMED_URI + "&" + version + "&code=1234000008501";
		p = getParameters(url);
		result = toString(getProperty(p, "result"));
		assertEquals("false", result);
		
		//Also check the preferred term
		url = "http://localhost:" + port + "/fhir/CodeSystem/$validate-code?" + version + "&coding=http://snomed.info/sct|" + sampleSCTID;
		url += "&display=Baked potato 1";
		p = getParameters(url);
		result = toString(getProperty(p, "result"));
		assertEquals("true", result);
		String msg = toString(getProperty(p, "message"));
		assertNull(msg);  //Display is the PT so we don't expect any message
		
		url = "http://localhost:" + port + "/fhir/CodeSystem/$validate-code?" + version + "&coding=http://snomed.info/sct|" + sampleSCTID;
		url += "&display=Baked potato 1 (substance)";
		p = getParameters(url);
		result = toString(getProperty(p, "result"));
		assertEquals("true", result);
		msg = toString(getProperty(p, "message"));
		assertNotNull(msg);  //Display is not PT so we expect a message
		
		//Check for completely wrong display value
		url = "http://localhost:" + port + "/fhir/CodeSystem/$validate-code?" + version + "&coding=http://snomed.info/sct|" + sampleSCTID;
		url += "&display=foo";
		p = getParameters(url);
		result = toString(getProperty(p, "result"));
		assertEquals("false", result);
		//TODO However we do get the actual PT here, so check that
	}
	
	@Test
	void testValidateUnpublishedCode() {
		String version = "version=http://snomed.info/xsct/" + sampleModuleId;
		//Test recovery using code with version with "unpublished" indicator
		String url = "http://localhost:" + port + "/fhir/CodeSystem/$validate-code?url=" + "http://snomed.info/xsct" + "&" + version + "&code=" + sampleSCTID;
		Parameters p = getParameters(url);
		String result = toString(getProperty(p, "result"));
		assertEquals("true", result);
	}
	
}
