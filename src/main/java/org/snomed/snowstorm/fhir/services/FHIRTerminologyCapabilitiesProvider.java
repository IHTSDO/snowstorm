package org.snomed.snowstorm.fhir.services;

import javax.servlet.http.HttpServletRequest;

import org.hl7.fhir.instance.model.api.IBaseConformance;
import org.hl7.fhir.r4.hapi.rest.server.ServerCapabilityStatementProvider;
import org.springframework.stereotype.Component;

import ca.uhn.fhir.rest.annotation.Metadata;
import ca.uhn.fhir.rest.api.server.RequestDetails;

@Component
/**
 * See https://www.hl7.org/fhir/terminologycapabilities.html
 * See https://smilecdr.com/hapi-fhir/docs/server_plain/introduction.html#capability-statement-server-metadata
 * Call using GET [base]/metadata?mode=terminology
 * See https://github.com/jamesagnew/hapi-fhir/issues/1681
 */
public class FHIRTerminologyCapabilitiesProvider extends ServerCapabilityStatementProvider {
	
	@Metadata
	public IBaseConformance getMetadataResource(HttpServletRequest request, RequestDetails requestDetails) {
		if (request.getParameter("mode") != null && request.getParameter("mode").equals("terminology")) {
			return new FHIRTerminologyCapabilities().withDefaults();
		} else {
			return super.getServerConformance(request, requestDetails);
		}
	}
}
