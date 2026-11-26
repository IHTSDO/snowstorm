package org.snomed.snowstorm.core.data.services;

import org.junit.jupiter.api.Test;
import org.snomed.snowstorm.core.data.domain.PackageType;
import org.snomed.snowstorm.core.data.services.pojo.CodeSystemDefaultConfiguration;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class CodeSystemDefaultConfigurationServiceTest {

	@Test
	void readsPackageType() {
		CodeSystemDefaultConfigurationService service = load(
				"codesystem.config.SNOMEDCT", "International Edition|900000000000207008||SNOMED International|||Edition",
				"codesystem.config.SNOMEDCT-NPU", "NPU Extension|11003000102|gb|IFCC and IUPAC|http://npu.org|21003000106|extension",
				"codesystem.config.SNOMEDCT-ICD10", "ICD10|5991000124107");

		CodeSystemDefaultConfiguration international = service.findByShortName("SNOMEDCT");
		assertEquals(PackageType.EDITION, international.packageType());
		assertEquals("SNOMED International", international.owner());
		assertNull(international.alternateSchemaUri(), "Empty placeholder reads as not set");
		assertNull(international.alternateSchemaSctid(), "Empty placeholder reads as not set");

		CodeSystemDefaultConfiguration npu = service.findByShortName("SNOMEDCT-NPU");
		assertEquals(PackageType.EXTENSION, npu.packageType(), "Case is ignored");
		assertEquals("http://npu.org", npu.alternateSchemaUri());
		assertEquals("21003000106", npu.alternateSchemaSctid());

		assertNull(service.findByShortName("SNOMEDCT-ICD10").packageType());
	}

	@Test
	void readsApplicationProperties() throws IOException {
		Properties properties = new Properties();
		try (InputStream inputStream = getClass().getResourceAsStream("/application.properties")) {
			properties.load(inputStream);
		}
		CodeSystemDefaultConfigurationService service = new CodeSystemDefaultConfigurationService();
		properties.stringPropertyNames().stream()
				.filter(key -> key.startsWith("codesystem.config."))
				.forEach(key -> service.getConfig().put(key, properties.getProperty(key)));
		ReflectionTestUtils.invokeMethod(service, "init");

		assertEquals(PackageType.EDITION, service.getDefaultPackageType("SNOMEDCT"));
		assertEquals(PackageType.EDITION, service.getDefaultPackageType("SNOMEDCT-US"));
		assertEquals(PackageType.EXTENSION, service.getDefaultPackageType("SNOMEDCT-BE"));
		assertEquals(PackageType.EXTENSION, service.getDefaultPackageType("SNOMEDCT-CL"));
		assertEquals(PackageType.DERIVATIVE, service.getDefaultPackageType("SNOMEDCT-NUVA"));
		assertEquals(PackageType.DERIVATIVE, service.getDefaultPackageType("SNOMEDCT-ICD10"));
		assertEquals(PackageType.DERIVATIVE, service.getDefaultPackageType("SNOMEDCT-VET"));
		assertEquals("http://loinc.org", service.findByShortName("SNOMEDCT-LOINCEXT").alternateSchemaUri());
		assertNull(service.findByShortName("SNOMEDCT-US").alternateSchemaUri());
	}

	@Test
	void getDefaultPackageType() {
		CodeSystemDefaultConfigurationService service = load(
				"codesystem.config.SNOMEDCT", "International Edition|900000000000207008||SNOMED International|||Edition");

		assertEquals(PackageType.EDITION, service.getDefaultPackageType("SNOMEDCT"));
		assertEquals(PackageType.EDITION, service.getDefaultPackageType("snomedct"));
		assertNull(service.getDefaultPackageType("SNOMEDCT-XX"));
		assertNull(service.getDefaultPackageType(null));
	}

	@Test
	void failsOnUnknownPackageType() {
		IllegalStateException e = assertThrows(IllegalStateException.class, () -> load(
				"codesystem.config.SNOMEDCT", "International Edition|900000000000207008||SNOMED International|||Release"));
		assertTrue(e.getMessage().contains("codesystem.config.SNOMEDCT"), e.getMessage());
		assertTrue(e.getMessage().contains("Release"), e.getMessage());
	}

	@Test
	void packageTypeFromTag() {
		assertEquals(PackageType.DERIVATIVE, PackageType.fromTag("Derivative"));
		assertEquals(PackageType.DERIVATIVE, PackageType.fromTag(" DERIVATIVE "));
		assertNull(PackageType.fromTag(""));
		assertNull(PackageType.fromTag(null));
		assertThrows(IllegalArgumentException.class, () -> PackageType.fromTag("Release"));
	}

	private static CodeSystemDefaultConfigurationService load(String... keysAndValues) {
		CodeSystemDefaultConfigurationService service = new CodeSystemDefaultConfigurationService();
		for (int i = 0; i < keysAndValues.length; i += 2) {
			service.getConfig().put(keysAndValues[i], keysAndValues[i + 1]);
		}
		ReflectionTestUtils.invokeMethod(service, "init");
		return service;
	}
}
