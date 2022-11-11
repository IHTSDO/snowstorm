package org.snomed.snowstorm.fhir.services;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.hl7.fhir.r4.model.ValueSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.snomed.snowstorm.fhir.domain.FHIRCodeSystemVersion;
import org.snomed.snowstorm.fhir.domain.FHIRValueSet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class FHIRLoadPackageServiceTest extends AbstractFHIRTest {

	@Autowired
	private FHIRLoadPackageService service;

	private File packageFile;

	@BeforeEach
	public void setup() throws IOException {
		// Create compressed archive for the test FHIR Package file
		packageFile = Files.createTempFile(getClass().getSimpleName() + "test-file", "tgz").toFile();
		try (GzipCompressorOutputStream gzipOut = new GzipCompressorOutputStream(new FileOutputStream(packageFile));
			TarArchiveOutputStream tarOut = new TarArchiveOutputStream(gzipOut)) {

			File packageDir = new File("src/test/resources/dummy-fhir-content/tiny-package");
			File[] files = packageDir.listFiles();
			if (files != null) {
				for (File file : files) {
					if (file.isFile()) {
						ArchiveEntry archiveEntry = tarOut.createArchiveEntry(file, file.getName());
						tarOut.putArchiveEntry(archiveEntry);
						Files.copy(file.toPath(), tarOut);
						tarOut.closeArchiveEntry();
					}
				}
			}
		}

		deleteAllFHIRResource();
	}

	@Test
	void uploadPackageResources() throws IOException {
		service.uploadPackageResources(packageFile, Collections.singleton("*"), packageFile.getName());

		for (FHIRCodeSystemVersion codeSystemVersion : codeSystemRepository.findAll()) {
			System.out.println(codeSystemVersion.getCanonical());
		}
		assertEquals(1, codeSystemRepository.count());

		for (FHIRValueSet valueSet : valueSetRepository.findAll()) {
			System.out.println(valueSet.getUrl() + "|" + valueSet.getVersion());
		}
		assertEquals(1, valueSetRepository.count());


		ResponseEntity<String> pageResponse = restTemplate.exchange(baseUrl + "/ValueSet", HttpMethod.GET, null, String.class);
		System.out.println("Page");
		System.out.println(pageResponse.getBody());

		// Expand imported implicit value set, that includes codes from imported code system
		//
		String testValueSetUri = "http://terminology.hl7.org/ValueSet/device-status-reason";
		ResponseEntity<String> response = restTemplate.exchange(baseUrl + "/ValueSet/$expand?url=" + testValueSetUri, HttpMethod.GET, null, String.class);
		assertEquals(HttpStatus.OK, response.getStatusCode());
		String valueSetString = response.getBody();
		ValueSet valueSet = fhirJsonParser.parseResource(ValueSet.class, valueSetString);
		assertNotNull(valueSet);
		assertEquals(testValueSetUri, valueSet.getUrl());
		assertEquals("0.1.0", valueSet.getVersion());
		assertEquals(8, valueSet.getExpansion().getContains().size());
	}

}
