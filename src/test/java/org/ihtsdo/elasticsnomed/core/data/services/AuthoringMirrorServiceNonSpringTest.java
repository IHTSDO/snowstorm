package org.ihtsdo.elasticsnomed.core.data.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.kaicode.elasticvc.api.BranchService;
import org.apache.tomcat.util.http.fileupload.util.Streams;
import org.ihtsdo.elasticsnomed.TestConfig;
import org.ihtsdo.elasticsnomed.TestUtil;
import org.ihtsdo.elasticsnomed.core.data.domain.Concept;
import org.ihtsdo.elasticsnomed.core.data.domain.Description;
import org.ihtsdo.elasticsnomed.core.data.domain.Relationship;
import org.ihtsdo.elasticsnomed.core.data.services.authoringmirror.TraceabilityActivity;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RunWith(SpringJUnit4ClassRunner.class)
public class AuthoringMirrorServiceNonSpringTest {

	private AuthoringMirrorService authoringMirrorService;

	@Before
	public void setup() {
		authoringMirrorService = new AuthoringMirrorService();
	}

	@Test
	public void testQuoteEscapeFix() {
		Assert.assertEquals("{\"userId\":\"mharry\",\"commitComment\":\"mharry updating concept Arsenic-induced \\\"rain-drop\\\" hypomelanosis (disorder)\",\"branchPath\":\"MAIN/CRSJAN18/CRSJAN18-588\"}",
				authoringMirrorService.fixQuotesNotEscaped("{\"userId\":\"mharry\",\"commitComment\":\"mharry updating concept Arsenic-induced \"rain-drop\" hypomelanosis (disorder)\",\"branchPath\":\"MAIN/CRSJAN18/CRSJAN18-588\"}"));
		Assert.assertEquals("\"userId\":\"mharry\",\"commitComment\":\"updating concept Arsenic-induced \\\"\\\" (disorder)\",\"branchPath\":\"MAIN/CRSJAN18/CRSJAN18-588\"",
				authoringMirrorService.fixQuotesNotEscaped("\"userId\":\"mharry\",\"commitComment\":\"updating concept Arsenic-induced \"\" (disorder)\",\"branchPath\":\"MAIN/CRSJAN18/CRSJAN18-588\""));
	}

}
