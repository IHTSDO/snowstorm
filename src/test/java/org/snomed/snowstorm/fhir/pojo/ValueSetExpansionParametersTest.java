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
	public void testBuildWithUrl() {
		final ValueSetExpansionParameters valueSetExpansionParameters =
				ValueSetExpansionParameters.newBuilder().withUrl(getComponent("url",
						"http://snomed.info/sct?fhir_vs=ecl/^ 32570581000036105 : << 263502005 = << 90734009%7CChronic%7C")).build();
		assertNotNull(valueSetExpansionParameters);
		assertEquals("http://snomed.info/sct?fhir_vs=ecl/^ 32570581000036105 : << 263502005 = << 90734009%7CChronic%7C", valueSetExpansionParameters.getUrl());
	}

	@Test
	public void testBuildWithFilter() {
		final ValueSetExpansionParameters valueSetExpansionParameters =
				ValueSetExpansionParameters.newBuilder().withFilter(getComponent("filter",
						"test")).build();
		assertNotNull(valueSetExpansionParameters);
		assertEquals("test", valueSetExpansionParameters.getFilter());
	}

	@Test
	public void testBuildWithActiveType() {
		final ValueSetExpansionParameters valueSetExpansionParameters =
				ValueSetExpansionParameters.newBuilder().withActiveType(getComponent("activeType",
						true)).build();
		assertNotNull(valueSetExpansionParameters);
		assertEquals(true, valueSetExpansionParameters.getActiveType().getValue());
	}

	@Test
	public void testBuildWithIncludeDesignationsType() {
		final ValueSetExpansionParameters valueSetExpansionParameters =
				ValueSetExpansionParameters.newBuilder().withIncludeDesignationsType(getComponent("includeDesignations",
						true)).build();
		assertNotNull(valueSetExpansionParameters);
		assertEquals(true, valueSetExpansionParameters.getIncludeDesignationsType().getValue());
	}

	@Test
	public void testBuildWithDisplayLanguage() {
		final ValueSetExpansionParameters valueSetExpansionParameters =
				ValueSetExpansionParameters.newBuilder().withDisplayLanguage(getComponent("displayLanguage",
						"en-GB")).build();
		assertNotNull(valueSetExpansionParameters);
		assertEquals("en-GB", valueSetExpansionParameters.getDisplayLanguage());
	}

	@Test
	public void testBuildWithOffset() {
		final ValueSetExpansionParameters valueSetExpansionParameters =
				ValueSetExpansionParameters.newBuilder().withOffset(getComponent("offset",
						"1")).build();
		assertNotNull(valueSetExpansionParameters);
		assertEquals("1", valueSetExpansionParameters.getOffsetStr());
	}

	@Test
	public void testBuildWithCount() {
		final ValueSetExpansionParameters valueSetExpansionParameters =
				ValueSetExpansionParameters.newBuilder().withCount(getComponent("count",
						"1")).build();
		assertNotNull(valueSetExpansionParameters);
		assertEquals("1", valueSetExpansionParameters.getCountStr());
	}

	@Test
	public void testBuildWithSystemVersion() {
		final ValueSetExpansionParameters valueSetExpansionParameters =
				ValueSetExpansionParameters.newBuilder().withSystemVersion(getComponent("system-version",
						"test")).build();
		assertNotNull(valueSetExpansionParameters);
		assertEquals("test", valueSetExpansionParameters.getSystemVersion().getValue());
	}

	@Test
	public void testBuildWithForceSystemVersion() {
		final ValueSetExpansionParameters valueSetExpansionParameters =
				ValueSetExpansionParameters.newBuilder().withForceSystemVersion(getComponent("force-system-version",
						"test")).build();
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
