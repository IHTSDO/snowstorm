package org.snomed.snowstorm.fhir.services;

import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.StringType;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.web.bind.annotation.RequestMethod;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Collections;

@RunWith(MockitoJUnitRunner.class)
public class FHIRValueSetProviderTest {

	@InjectMocks
	private final FHIRValueSetProvider fhirValueSetProvider = new FHIRValueSetProvider();

	@Mock
	private FHIRHelper fhirHelper;

	private static final String VALUE_SET_EXPANSION_POST_BODY_WITH_VERSION = "{\n" +
			"    \"resourceType\": \"Parameters\",\n" +
			"    \"parameter\": [\n" +
			"        {\n" +
			"            \"name\": \"valueSet\",\n" +
			"            \"resource\": {\n" +
			"                \"resourceType\": \"ValueSet\",\n" +
			"                \"compose\": {\n" +
			"                    \"include\": [\n" +
			"                        {\n" +
			"                            \"valueSet\": \"http://snomed.info/sct?fhir_vs=ecl/^ 32570581000036105 : << 263502005 = << 90734009%7CChronic%7C\" \n" +
			"                        }\n" +
			"                    ]\n" +
			"                }\n" +
			"            }\n" +
			"        },\n" +
			"        {\n" +
			"            \"name\": \"displayLanguage\",\n" +
			"            \"valueString\": \"en-GB\"\n" +
			"        },\n" +
			"        {\n" +
			"            \"name\": \"version\",\n" +
			"            \"valueString\": \"test\"\n" +
			"        },\n" +
			"        {\n" +
			"            \"name\": \"offset\",\n" +
			"            \"valueString\": \"1\"\n" +
			"        },\n" +
			"        {\n" +
			"            \"name\": \"count\",\n" +
			"            \"valueString\": \"2\"\n" +
			"        }\n" +
			"    ]\n" +
			"}";

	@Test(expected = FHIROperationException.class)
	public void testExpandInstanceThrowsFHIROperationExceptionIfVersionUsedWhilePerformingPOSTOperation() throws FHIROperationException {
		Mockito.doThrow(new FHIROperationException(OperationOutcome.IssueType.NOTSUPPORTED, "Input parameter 'version' is not currently " +
				"supported in the context of a ValueSet $expand operation. Use system-version or force-system-version parameters instead."))
				.when(fhirHelper).notSupported(Mockito.anyString(), Mockito.any(), Mockito.anyString());
		fhirValueSetProvider.expandInstance(IdType.newRandomUuid(),
				getHttpServletRequestWithRequestMethodPOST(),
				Mockito.mock(HttpServletResponse.class),
				VALUE_SET_EXPANSION_POST_BODY_WITH_VERSION,
				"",
				"",
				new BooleanType(),
				new BooleanType(),
				Collections.emptyList(),
				"",
				"",
				"",
				new StringType(),
				new StringType(),
				new StringType());
	}

	@Test(expected = FHIROperationException.class)
	public void testExpandTypeThrowsFHIROperationExceptionIfVersionUsedWhilePerformingPOSTOperation() throws FHIROperationException {
		Mockito.doThrow(new FHIROperationException(OperationOutcome.IssueType.NOTSUPPORTED, "Input parameter 'version' is not currently " +
				"supported in the context of a ValueSet $expand operation. Use system-version or force-system-version parameters instead."))
				.when(fhirHelper).notSupported(Mockito.anyString(), Mockito.any(), Mockito.anyString());
		fhirValueSetProvider.expandInstance(IdType.newRandomUuid(),
				getHttpServletRequestWithRequestMethodPOST(),
				Mockito.mock(HttpServletResponse.class),
				VALUE_SET_EXPANSION_POST_BODY_WITH_VERSION,
				"",
				"",
				new BooleanType(),
				new BooleanType(),
				Collections.emptyList(),
				"",
				"",
				"",
				new StringType(),
				new StringType(),
				new StringType());
	}

	private HttpServletRequest getHttpServletRequestWithRequestMethodPOST() {
		final HttpServletRequest httpServletRequest = Mockito.mock(HttpServletRequest.class);
		Mockito.when(httpServletRequest.getMethod()).thenReturn(RequestMethod.POST.name());
		return httpServletRequest;
	}
}
