package org.snomed.snowstorm.fhir.services;

import org.hl7.fhir.r4.model.Parameters;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.hibernate.validator.internal.util.Contracts.assertNotNull;
import static org.junit.jupiter.api.Assertions.*;
import static org.snomed.snowstorm.fhir.config.FHIRConstants.SNOMED_URI;

class FHIRCodeSystemProviderValidateTest extends AbstractFHIRTest {
	
	@Test
	void testValidateCode() {
		String version = "version=http://snomed.info/sct/1234000008";
		//Test recovery using code with version
		Parameters response = getParameters("/CodeSystem/$validate-code?url=" + SNOMED_URI + "&" + version + "&code=" + sampleSCTID);
		String result = getPropertyString(response, "result");
		assertEquals("true", result);
		Boolean inactive = toBoolean(getProperty(response, "inactive"));
		assertFalse(inactive);

		//Alternative URLs using coding saying the same thing
		response = getParameters("/CodeSystem/$validate-code?" + version + "&coding=http://snomed.info/sct|" + sampleSCTID);
		result = getPropertyString(response, "result");
		assertEquals("true", result);
		
		//Known not present
		response = getParameters("/CodeSystem/$validate-code?url=" + SNOMED_URI + "&" + version + "&code=1234000008501");
		result = getPropertyString(response, "result");
		assertEquals("false", result);
		
		//Also check the preferred term
		response = getParameters("/CodeSystem/$validate-code?" + version + "&coding=http://snomed.info/sct|" + sampleSCTID + "&display=Baked potato 1");
		result = getPropertyString(response, "result");
		assertEquals("true", result);
		String msg = getPropertyString(response, "message");
		assertNull(msg);  //Display is the PT so we don't expect any message
		
		response = getParameters("/CodeSystem/$validate-code?" + version + "&coding=http://snomed.info/sct|" + sampleSCTID + "&display=Baked potato 1 (substance)");
		result = getPropertyString(response, "result");
		assertEquals("true", result);
		msg = getPropertyString(response, "message");
		assertNotNull(msg);  //Display is not PT so we expect a message
		
		//Check for completely wrong display value
		response = getParameters("/CodeSystem/$validate-code?" + version + "&coding=http://snomed.info/sct|" + sampleSCTID + "&display=foo");
		result = getPropertyString(response, "result");
		assertEquals("false", result);
		//TODO However we do get the actual PT here, so check that
	}

	@Test
	void testValidateCodeInactive() {
		String version = "version=http://snomed.info/sct/1234000008";
		//Test recovery using code with version
		String url = "http://localhost:" + port + "/fhir/CodeSystem/$validate-code?url=" + SNOMED_URI + "&" + version + "&code=" + sampleInactiveSCTID;
		Parameters p = getParameters(url);
		String result = toString(getProperty(p, "result"));
		assertEquals("true", result);
		Boolean inactive = toBoolean(getProperty(p, "inactive"));
		assertTrue(inactive);
	}

	@Test
	void testValidateUnpublishedCode() {
		String version = "version=http://snomed.info/xsct/" + sampleModuleId;
		//Test recovery using code with version with "unpublished" indicator
		String url = baseUrl + "/CodeSystem/$validate-code?url=" + "http://snomed.info/xsct" + "&" + version + "&code=" + sampleSCTID;
		Parameters p = getParameters(url);
		String result = getPropertyString(p, "result");
		assertEquals("true", result);
	}

	@Test
	void testValidateExpression() {
		Parameters response = getParameters("/CodeSystem/$validate-code" +
				"?url=http://snomed.info/sct" +
				"&version=" + EXPRESSION_REPO_VERSION +
				"&code=<<<257751006");
		assertEquals("true", getPropertyString(response, "result"), () -> getSummary(response).toString());
	}

	private Map<String, String> getSummary(Parameters response) {
		Map<String, String> summary = new LinkedHashMap<>();
		summary.put("result", getPropertyString(response, "result"));
		summary.put("message", getPropertyString(response, "message"));
		summary.put("display", getPropertyString(response, "display"));
		return summary;
	}

}
