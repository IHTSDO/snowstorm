package org.snomed.snowstorm.fhir.config;

import ca.uhn.fhir.rest.api.Constants;
import ca.uhn.fhir.rest.api.EncodingEnum;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.interceptor.ResponseHighlighterInterceptor;
import org.hl7.fhir.instance.model.api.IBaseConformance;
import org.hl7.fhir.r4.model.CapabilityStatement;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.TerminologyCapabilities;
import org.snomed.snowstorm.core.data.services.CodeSystemService;
import org.snomed.snowstorm.fhir.services.FHIRCodeSystemService;
import org.snomed.snowstorm.fhir.services.FHIRLoadPackageServlet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.servlet.MultipartConfigElement;
import org.springframework.context.annotation.Lazy;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

@Configuration
public class FHIRRestConfig {

	private static final int MB_IN_BYTES = 1024 * 1024;

	@Bean
	public ServletRegistrationBean<HapiRestfulServlet> hapi(
			@Autowired(required = false) BuildProperties buildProperties,
			@Autowired @Lazy FHIRCodeSystemService codeSystemService) {

		HapiRestfulServlet hapiServlet = new HapiRestfulServlet(buildProperties, codeSystemService);

		ServletRegistrationBean<HapiRestfulServlet> servletRegistrationBean = new ServletRegistrationBean<>(hapiServlet, "/fhir/*");
		hapiServlet.setServerName("SnowstormX FHIR Server");
		hapiServlet.setServerVersion(buildProperties != null ? buildProperties.getVersion() : "development");
		hapiServlet.setDefaultResponseEncoding(EncodingEnum.JSON);

		hapiServlet.registerInterceptor(new ResponseHighlighterInterceptor() {
			@Override
			public void capabilityStatementGenerated(RequestDetails theRequestDetails, IBaseConformance theCapabilityStatement) {
				if (theCapabilityStatement instanceof CapabilityStatement statement) {
					statement.setPublisherElement(new StringType("SNOMED International"));
					statement.setFormat(new ArrayList<>());
					statement.addFormat(Constants.CT_FHIR_JSON_NEW);
					statement.addFormat(Constants.CT_FHIR_XML_NEW);
					super.capabilityStatementGenerated(theRequestDetails, theCapabilityStatement);
				}
			}
		});

		return servletRegistrationBean;
	}

	@Bean
	public ServletRegistrationBean<FHIRLoadPackageServlet> addBundleServlet() throws IOException {
		ServletRegistrationBean<FHIRLoadPackageServlet> registrationBean = new ServletRegistrationBean<>(new FHIRLoadPackageServlet(), "/fhir-admin/load-package");
		registrationBean.setMultipartConfig(
				new MultipartConfigElement(Files.createTempDirectory("fhir-bundle-upload").toFile().getAbsolutePath(), MB_IN_BYTES * 200, MB_IN_BYTES * 200, 0));
		return registrationBean;
	}

}
