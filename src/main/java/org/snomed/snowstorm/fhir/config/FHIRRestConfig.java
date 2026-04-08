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
import java.nio.file.attribute.PosixFilePermissions;

@Configuration
public class FHIRRestConfig {

	private static final int MB_IN_BYTES = 1024 * 1024;

	@Bean
	public ServletRegistrationBean<HapiRestfulServlet> hapi(
			@Autowired(required = false) BuildProperties buildProperties,
			@Autowired @Lazy FHIRCodeSystemService codeSystemService) {

		HapiRestfulServlet hapiServlet = new HapiRestfulServlet(buildProperties, codeSystemService);

		// "/fhir" is required in addition to "/fhir/*" so the base URL matches the servlet (spec: path "/fhir" alone does not match "/fhir/*").
		ServletRegistrationBean<HapiRestfulServlet> servletRegistrationBean = new ServletRegistrationBean<>(hapiServlet, "/fhir", "/fhir/*");
		hapiServlet.setServerName("Snowstorm FHIR Server");
		hapiServlet.setServerVersion(buildProperties != null ? buildProperties.getVersion() : "development");
		hapiServlet.setDefaultResponseEncoding(EncodingEnum.JSON);

		return servletRegistrationBean;
	}

	@Bean
	public ServletRegistrationBean<FHIRLoadPackageServlet> addBundleServlet() throws IOException {
		ServletRegistrationBean<FHIRLoadPackageServlet> registrationBean = new ServletRegistrationBean<>(new FHIRLoadPackageServlet(), "/fhir-admin/load-package");
		registrationBean.setMultipartConfig(
				new MultipartConfigElement(createSecureUploadDir(), MB_IN_BYTES * 200L, MB_IN_BYTES * 200L, 0));
		return registrationBean;
	}

	// Creates an upload directory accessible by the owner only, avoiding the world-writable default of the shared temp directory.
	private static String createSecureUploadDir() throws IOException {
		try {
			return Files.createTempDirectory("fhir-bundle-upload",
					PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")))
					.toAbsolutePath().toString();
		} catch (UnsupportedOperationException e) {
			// Non-POSIX filesystem (e.g. Windows), where the per-user temp directory is already private.
			return Files.createTempDirectory("fhir-bundle-upload").toAbsolutePath().toString();
		}
	}

}
