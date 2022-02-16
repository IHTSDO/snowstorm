package org.snomed.snowstorm.fhir.services;

import javax.servlet.http.HttpServletRequest;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.support.IValidationSupport;
import ca.uhn.fhir.rest.server.RestfulServer;
import ca.uhn.fhir.rest.server.RestfulServerConfiguration;
import ca.uhn.fhir.rest.server.provider.ServerCapabilityStatementProvider;
import ca.uhn.fhir.rest.server.util.ISearchParamRegistry;
import org.hl7.fhir.instance.model.api.IBaseConformance;
import org.springframework.stereotype.Component;

import ca.uhn.fhir.rest.annotation.Metadata;
import ca.uhn.fhir.rest.api.server.RequestDetails;

/**
 * See https://www.hl7.org/fhir/terminologycapabilities.html
 * See https://smilecdr.com/hapi-fhir/docs/server_plain/introduction.html#capability-statement-server-metadata
 * Call using GET [base]/metadata?mode=terminology
 * See https://github.com/jamesagnew/hapi-fhir/issues/1681
 */
public class FHIRTerminologyCapabilitiesProvider extends ServerCapabilityStatementProvider {

	public FHIRTerminologyCapabilitiesProvider(RestfulServer theServer) {
		super(theServer);
	}

	public FHIRTerminologyCapabilitiesProvider(FhirContext theContext, RestfulServerConfiguration theServerConfiguration) {
		super(theContext, theServerConfiguration);
	}

	public FHIRTerminologyCapabilitiesProvider(RestfulServer theRestfulServer, ISearchParamRegistry theSearchParamRegistry, IValidationSupport theValidationSupport) {
		super(theRestfulServer, theSearchParamRegistry, theValidationSupport);
	}

	@Metadata
	public IBaseConformance getMetadataResource(HttpServletRequest request, RequestDetails requestDetails) {
		if (request.getParameter("mode") != null && request.getParameter("mode").equals("terminology")) {
			return new FHIRTerminologyCapabilities().withDefaults();
		} else {
			return super.getServerConformance(request, requestDetails);
		}
	}
}
