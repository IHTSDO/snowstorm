package org.snomed.snowstorm.fhir.services;

import jakarta.servlet.http.HttpServletRequest;

import ca.uhn.fhir.rest.server.RestfulServer;
import ca.uhn.fhir.rest.server.provider.ServerCapabilityStatementProvider;
import org.hl7.fhir.instance.model.api.IBaseConformance;
import ca.uhn.fhir.rest.annotation.Metadata;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import org.springframework.boot.info.BuildProperties;

/**
 * See https://www.hl7.org/fhir/terminologycapabilities.html
 * See https://smilecdr.com/hapi-fhir/docs/server_plain/introduction.html#capability-statement-server-metadata
 * Call using GET [base]/metadata?mode=terminology
 * See https://github.com/jamesagnew/hapi-fhir/issues/1681
 */
public class FHIRTerminologyCapabilitiesProvider extends ServerCapabilityStatementProvider {

	private final BuildProperties buildProperties;
	private final FHIRCodeSystemService codeSystemService;

	public FHIRTerminologyCapabilitiesProvider(RestfulServer theServer, BuildProperties buildProperties, FHIRCodeSystemService codeSystemService) {
		super(theServer);
		this.buildProperties = buildProperties;
		this.codeSystemService = codeSystemService;
	}

	@Metadata(cacheMillis = 0)
	public IBaseConformance getMetadataResource(HttpServletRequest request, RequestDetails requestDetails) {
		if ("terminology".equals(request.getParameter("mode"))) {
			return new FHIRTerminologyCapabilities().withDefaults(buildProperties, codeSystemService);
		} else {
			return super.getServerConformance(request, requestDetails);
		}
	}
}
