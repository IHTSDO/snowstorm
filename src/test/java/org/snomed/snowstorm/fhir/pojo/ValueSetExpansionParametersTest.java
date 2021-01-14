package org.snomed.snowstorm.fhir.pojo;

import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.StringType;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class ValueSetExpansionParametersTest {

	@Test
	public void testBuildWithUrlFromPOST() {
		final ValueSetExpansionParameters valueSetExpansionParameters =
				ValueSetExpansionParameters.newBuilderFromPOST().withUrl(getComponent("url",
						"http://snomed.info/sct?fhir_vs=ecl/^ 32570581000036105 : << 263502005 = << 90734009%7CChronic%7C")).build();
		assertNotNull(valueSetExpansionParameters);
		assertEquals("http://snomed.info/sct?fhir_vs=ecl/^ 32570581000036105 : << 263502005 = << 90734009%7CChronic%7C", valueSetExpansionParameters.getUrl());
	}

	@Test
	public void testBuildWithUrlFromGET() {
		final ValueSetExpansionParameters valueSetExpansionParameters =
				ValueSetExpansionParameters.newBuilderFromGET().withUrl("http://snomed.info/sct?fhir_vs=ecl/^ 32570581000036105 : << 263502005 = << 90734009%7CChronic%7C").build();
		assertNotNull(valueSetExpansionParameters);
		assertEquals("http://snomed.info/sct?fhir_vs=ecl/^ 32570581000036105 : << 263502005 = << 90734009%7CChronic%7C", valueSetExpansionParameters.getUrl());
	}

	@Test
	public void testBuildWithFilterFromPOST() {
		final ValueSetExpansionParameters valueSetExpansionParameters =
				ValueSetExpansionParameters.newBuilderFromPOST().withFilter(getComponent("filter",
						"test")).build();
		assertNotNull(valueSetExpansionParameters);
		assertEquals("test", valueSetExpansionParameters.getFilter());
	}

	@Test
	public void testBuildWithFilterFromGET() {
		final ValueSetExpansionParameters valueSetExpansionParameters =
				ValueSetExpansionParameters.newBuilderFromGET().withFilter("test").build();
		assertNotNull(valueSetExpansionParameters);
		assertEquals("test", valueSetExpansionParameters.getFilter());
	}

	@Test
	public void testBuildWithActiveTypeFromPOST() {
		final ValueSetExpansionParameters valueSetExpansionParameters =
				ValueSetExpansionParameters.newBuilderFromPOST().withActiveType(getComponent("activeType",
						true)).build();
		assertNotNull(valueSetExpansionParameters);
		assertEquals(true, valueSetExpansionParameters.getActiveType().getValue());
	}

	@Test
	public void testBuildWithActiveTypeFromGET() {
		final ValueSetExpansionParameters valueSetExpansionParameters =
				ValueSetExpansionParameters.newBuilderFromGET().withActiveType(new BooleanType(true)).build();
		assertNotNull(valueSetExpansionParameters);
		assertEquals(true, valueSetExpansionParameters.getActiveType().getValue());
	}

	@Test
	public void testBuildWithIncludeDesignationsTypeFromPOST() {
		final ValueSetExpansionParameters valueSetExpansionParameters =
				ValueSetExpansionParameters.newBuilderFromPOST().withIncludeDesignationsType(getComponent("includeDesignations",
						true)).build();
		assertNotNull(valueSetExpansionParameters);
		assertEquals(true, valueSetExpansionParameters.getIncludeDesignationsType().getValue());
	}

	@Test
	public void testBuildWithIncludeDesignationsTypeFromGET() {
		final ValueSetExpansionParameters valueSetExpansionParameters =
				ValueSetExpansionParameters.newBuilderFromGET().withIncludeDesignationsType(new BooleanType(true)).build();
		assertNotNull(valueSetExpansionParameters);
		assertEquals(true, valueSetExpansionParameters.getIncludeDesignationsType().getValue());
	}

	@Test
	public void testBuildWithDisplayLanguageFromPOST() {
		final ValueSetExpansionParameters valueSetExpansionParameters =
				ValueSetExpansionParameters.newBuilderFromPOST().withDisplayLanguage(getComponent("displayLanguage",
						"en-gb")).build();
		assertNotNull(valueSetExpansionParameters);
		assertEquals("en-gb", valueSetExpansionParameters.getDisplayLanguage());
	}

	@Test
	public void testBuildWithDisplayLanguageFromGET() {
		final ValueSetExpansionParameters valueSetExpansionParameters =
				ValueSetExpansionParameters.newBuilderFromGET().withDisplayLanguage("en-gb").build();
		assertNotNull(valueSetExpansionParameters);
		assertEquals("en-gb", valueSetExpansionParameters.getDisplayLanguage());
	}

	@Test
	public void testBuildWithOffsetFromPOST() {
		final ValueSetExpansionParameters valueSetExpansionParameters =
				ValueSetExpansionParameters.newBuilderFromPOST().withOffset(getComponent("offset",
						"1")).build();
		assertNotNull(valueSetExpansionParameters);
		assertEquals("1", valueSetExpansionParameters.getOffsetStr());
	}

	@Test
	public void testBuildWithOffsetFromGET() {
		final ValueSetExpansionParameters valueSetExpansionParameters =
				ValueSetExpansionParameters.newBuilderFromGET().withOffset("1").build();
		assertNotNull(valueSetExpansionParameters);
		assertEquals("1", valueSetExpansionParameters.getOffsetStr());
	}

	@Test
	public void testBuildWithCountFromPOST() {
		final ValueSetExpansionParameters valueSetExpansionParameters =
				ValueSetExpansionParameters.newBuilderFromPOST().withCount(getComponent("count",
						"1")).build();
		assertNotNull(valueSetExpansionParameters);
		assertEquals("1", valueSetExpansionParameters.getCountStr());
	}

	@Test
	public void testBuildWithCountFromGET() {
		final ValueSetExpansionParameters valueSetExpansionParameters =
				ValueSetExpansionParameters.newBuilderFromGET().withCount("1").build();
		assertNotNull(valueSetExpansionParameters);
		assertEquals("1", valueSetExpansionParameters.getCountStr());
	}

	@Test
	public void testBuildWithSystemVersionFromPOST() {
		final ValueSetExpansionParameters valueSetExpansionParameters =
				ValueSetExpansionParameters.newBuilderFromPOST().withSystemVersion(getComponent("system-version",
						"test")).build();
		assertNotNull(valueSetExpansionParameters);
		assertEquals("test", valueSetExpansionParameters.getSystemVersion().getValue());
	}

	@Test
	public void testBuildWithSystemVersionFromGET() {
		final ValueSetExpansionParameters valueSetExpansionParameters =
				ValueSetExpansionParameters.newBuilderFromGET().withSystemVersion(new StringType("test")).build();
		assertNotNull(valueSetExpansionParameters);
		assertEquals("test", valueSetExpansionParameters.getSystemVersion().getValue());
	}

	@Test
	public void testBuildWithForceSystemVersionFromPOST() {
		final ValueSetExpansionParameters valueSetExpansionParameters =
				ValueSetExpansionParameters.newBuilderFromPOST().withForceSystemVersion(getComponent("force-system-version",
						"test")).build();
		assertNotNull(valueSetExpansionParameters);
		assertEquals("test", valueSetExpansionParameters.getForceSystemVersion().getValue());
	}

	@Test
	public void testBuildWithForceSystemVersionFromGET() {
		final ValueSetExpansionParameters valueSetExpansionParameters =
				ValueSetExpansionParameters.newBuilderFromGET().withForceSystemVersion(new StringType("test")).build();
		assertNotNull(valueSetExpansionParameters);
		assertEquals("test", valueSetExpansionParameters.getForceSystemVersion().getValue());
	}

	private Parameters.ParametersParameterComponent getComponent(final String name, final String value) {
		final Parameters.ParametersParameterComponent component = Mockito.mock(Parameters.ParametersParameterComponent.class);
		Mockito.when(component.getName()).thenReturn(name);
		Mockito.when(component.getValue()).thenReturn(new StringType(value));
		return component;
	}

	private Parameters.ParametersParameterComponent getComponent(final String name, final boolean value) {
		final Parameters.ParametersParameterComponent component = Mockito.mock(Parameters.ParametersParameterComponent.class);
		Mockito.when(component.getName()).thenReturn(name);
		Mockito.when(component.getValue()).thenReturn(new BooleanType(value));
		return component;
	}
}
