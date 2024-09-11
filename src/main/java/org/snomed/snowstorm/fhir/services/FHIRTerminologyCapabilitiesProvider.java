package org.snomed.snowstorm.fhir.services;

import ca.uhn.fhir.rest.annotation.Metadata;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.RestfulServer;
import ca.uhn.fhir.rest.server.provider.ServerCapabilityStatementProvider;
import jakarta.servlet.http.HttpServletRequest;
import org.hl7.fhir.instance.model.api.IBaseConformance;
import org.hl7.fhir.r4.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.info.BuildProperties;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

import java.util.Arrays;
import java.util.Date;

/**
 * See https://www.hl7.org/fhir/terminologycapabilities.html
 * See https://smilecdr.com/hapi-fhir/docs/server_plain/introduction.html#capability-statement-server-metadata
 * Call using GET [base]/metadata?mode=terminology
 * See https://github.com/jamesagnew/hapi-fhir/issues/1681
 */
public class FHIRTerminologyCapabilitiesProvider extends ServerCapabilityStatementProvider {
	private final Logger logger = LoggerFactory.getLogger(getClass());

	private final BuildProperties buildProperties;
	private final FHIRCodeSystemService codeSystemService;

	public FHIRTerminologyCapabilitiesProvider(RestfulServer theServer, BuildProperties buildProperties, FHIRCodeSystemService codeSystemService) {
		super(theServer);
		this.buildProperties = buildProperties;
		this.codeSystemService = codeSystemService;
	}

	@Metadata(cacheMillis = 0)
	public IBaseConformance getMetadataResource(HttpServletRequest request, RequestDetails requestDetails) {
		logger.info(requestDetails.getCompleteUrl());
		final WebApplicationContext applicationContext =
				WebApplicationContextUtils.getWebApplicationContext(request.getServletContext());
		if ("terminology".equals(request.getParameter("mode"))) {
			FHIRTerminologyCapabilities tc = new FHIRTerminologyCapabilities().withDefaults(this.buildProperties,this.codeSystemService);
			tc.setVersion(buildProperties.getVersion());
			tc.setDate(new Date(buildProperties.getTime().toEpochMilli()));
			TerminologyCapabilities.TerminologyCapabilitiesExpansionComponent expansion = new TerminologyCapabilities.TerminologyCapabilitiesExpansionComponent();
			Arrays.asList("activeOnly",
			"count",
			"displayLanguage",
			"excludeNested",
			"force-system-version",
			"includeDefinition",
			"includeDesignations",
			"offset",
			"property",
			"system-version",
			"tx-resource").stream().forEach(x -> expansion.addParameter(new TerminologyCapabilities.TerminologyCapabilitiesExpansionParameterComponent().setName(x)));
			tc.setExpansion(expansion);
			return tc;
		} else {
			IBaseConformance resource = super.getServerConformance(request, requestDetails);
			CapabilityStatement cs = (CapabilityStatement) resource;
			Extension testsVersion = new Extension("http://hl7.org/fhir/uv/application-feature/StructureDefinition/feature");
			testsVersion.addExtension("definition", new CanonicalType("http://hl7.org/fhir/uv/tx-tests/FeatureDefinition/test-version"));
			testsVersion.addExtension("value", new CodeType("1.7.0"));
			cs.addExtension(testsVersion);
			Extension codeSystemAsParameter = new Extension("http://hl7.org/fhir/uv/application-feature/StructureDefinition/feature");
			codeSystemAsParameter.addExtension("definition", new CanonicalType("http://hl7.org/fhir/uv/tx-ecosystem/FeatureDefinition/CodeSystemAsParameter"));
			codeSystemAsParameter.addExtension("value", new StringType("empty"));
			cs.addExtension(codeSystemAsParameter);
			cs.setUrl(requestDetails.getFhirServerBase()+"/metadata");
			CapabilityStatement.CapabilityStatementRestResourceOperationComponent operation = new CapabilityStatement.CapabilityStatementRestResourceOperationComponent();
			operation.setName("versions");
			operation.setDefinition(requestDetails.getFhirServerBase()+"/versions");
			cs.getRest().stream().filter(x->x.getMode()== CapabilityStatement.RestfulCapabilityMode.SERVER).findFirst().ifPresent(x -> x.addOperation(operation));
			cs.getSoftware().setReleaseDate(new Date(buildProperties.getTime().toEpochMilli()));
			cs.setVersion(buildProperties.getVersion());
			cs.setTitle(buildProperties.getName());
			return cs;
		}
	}
}
