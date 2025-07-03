package org.snomed.snowstorm.fhir.config;

import ca.uhn.fhir.rest.api.EncodingEnum;
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

		return servletRegistrationBean;
	}

	@Bean
	public ServletRegistrationBean<FHIRLoadPackageServlet> addBundleServlet() throws IOException {
		ServletRegistrationBean<FHIRLoadPackageServlet> registrationBean = new ServletRegistrationBean<>(new FHIRLoadPackageServlet(), "/fhir-admin/load-package");
		registrationBean.setMultipartConfig(
				new MultipartConfigElement(Files.createTempDirectory("fhir-bundle-upload").toFile().getAbsolutePath(), MB_IN_BYTES * 200L, MB_IN_BYTES * 200L, 0));
		return registrationBean;
	}

}
