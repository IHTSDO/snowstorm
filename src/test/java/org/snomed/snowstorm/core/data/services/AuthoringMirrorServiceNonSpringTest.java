package org.snomed.snowstorm.core.data.services;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;


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
