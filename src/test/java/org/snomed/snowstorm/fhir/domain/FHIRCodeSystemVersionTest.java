package org.snomed.snowstorm.fhir.domain;

import org.hl7.fhir.r4.model.CodeSystem;
import org.junit.jupiter.api.Test;
import org.snomed.snowstorm.core.data.services.pojo.CodeSystemDefaultConfiguration;

import static org.junit.jupiter.api.Assertions.*;
import static org.snomed.snowstorm.fhir.config.FHIRConstants.SNOMED_INTERNATIONAL;

class FHIRCodeSystemVersionTest {

	@Test
	void testIdGen() {
		FHIRCodeSystemVersion version = new FHIRCodeSystemVersion(new CodeSystem().setUrl("http://hl7.org/fhir/sid/icd-10-cm_2"));
		assertEquals("hl7.org-fhir-sid-icd-10-cm-2", version.getId(),
				"ID generated from URL should only contain a-z, A-Z, 0-9, '-' or '.'");
	}

	@Test
	void publisherTakesOwnerFromApplicationConfig() {
		CodeSystemDefaultConfiguration configuration = new CodeSystemDefaultConfiguration(
				"United States Edition", "SNOMEDCT-US", "731000124108", "us", "National Library of Medicine", null, null);

		org.snomed.snowstorm.core.data.domain.CodeSystem codeSystem =
				new org.snomed.snowstorm.core.data.domain.CodeSystem("SNOMEDCT-US", "MAIN/SNOMEDCT-US");
		codeSystem.setOwner("SNOMED International");

		FHIRCodeSystemVersion version = new FHIRCodeSystemVersion(codeSystem, false, configuration);

		assertEquals("National Library of Medicine", version.getPublisher());
	}

	@Test
	void publisherFallsBackToCodeSystemOwnerWhenNotInConfig() {
		org.snomed.snowstorm.core.data.domain.CodeSystem codeSystem =
				new org.snomed.snowstorm.core.data.domain.CodeSystem("SNOMEDCT-AU", "MAIN/SNOMEDCT-AU");
		codeSystem.setOwner("Australian Digital Health Agency");

		FHIRCodeSystemVersion version = new FHIRCodeSystemVersion(codeSystem, false);

		assertEquals("Australian Digital Health Agency", version.getPublisher());
	}

	@Test
	void publisherFallsBackToSnomedInternationalWhenOwnerMissing() {
		org.snomed.snowstorm.core.data.domain.CodeSystem codeSystem =
				new org.snomed.snowstorm.core.data.domain.CodeSystem("SNOMEDCT", "MAIN");

		FHIRCodeSystemVersion version = new FHIRCodeSystemVersion(codeSystem, false);

		assertEquals(SNOMED_INTERNATIONAL, version.getPublisher());
	}
}
