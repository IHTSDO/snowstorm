package org.snomed.snowstorm.fhir.config;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.EncodingEnum;
import org.snomed.snowstorm.fhir.services.FHIRAddBundleServlet;
import org.snomed.snowstorm.fhir.services.HapiParametersMapper;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.servlet.MultipartConfigElement;
import java.io.IOException;
import java.nio.file.Files;

@Configuration
public class FHIRConfig {

	private static final int MB_IN_BYTES = 1024 * 1024;

	@Bean
	public HapiParametersMapper hapiParametersMapper() {
		return new HapiParametersMapper();
	}
	
	@Bean
	public ServletRegistrationBean<HapiRestfulServlet> hapi() {
		HapiRestfulServlet hapiServlet = new HapiRestfulServlet();

		ServletRegistrationBean<HapiRestfulServlet> servletRegistrationBean = new ServletRegistrationBean<>(hapiServlet, "/fhir/*");
		hapiServlet.setServerName("Snowstorm X FHIR Server");
		hapiServlet.setServerVersion(getClass().getPackage().getImplementationVersion());
		hapiServlet.setDefaultResponseEncoding(EncodingEnum.JSON);
		return servletRegistrationBean;
	}

	@Bean
	public ServletRegistrationBean<FHIRAddBundleServlet> addBundleServlet() throws IOException {
		ServletRegistrationBean<FHIRAddBundleServlet> registrationBean = new ServletRegistrationBean<>(new FHIRAddBundleServlet(), "/fhir-admin/addBundle");
		registrationBean.setMultipartConfig(
				new MultipartConfigElement(Files.createTempDirectory("fhir-bundle-upload").toFile().getAbsolutePath(), MB_IN_BYTES * 200, MB_IN_BYTES * 200, 0));
		return registrationBean;
	}

	@Bean
	public FhirContext fhirContext() {
		return FhirContext.forR4();
	}

}
