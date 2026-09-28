package org.snomed.snowstorm.fhir.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.snomed.snowstorm.core.data.domain.CodeSystem;
import org.snomed.snowstorm.core.data.domain.CodeSystemVersion;
import org.snomed.snowstorm.core.data.services.CodeSystemDefaultConfigurationService;
import org.snomed.snowstorm.core.data.services.CodeSystemService;
import org.snomed.snowstorm.core.data.services.ConceptService;
import org.snomed.snowstorm.core.data.services.IdentifierComponentService;
import org.snomed.snowstorm.core.data.services.ReferenceSetMemberService;
import org.snomed.snowstorm.core.data.services.pojo.CodeSystemDefaultConfiguration;
import org.snomed.snowstorm.fhir.config.FHIRConceptMapImplicitConfig;
import org.snomed.snowstorm.fhir.domain.FHIRConceptMap;
import org.snomed.snowstorm.fhir.pojo.FHIRSnomedConceptMapConfig;
import org.snomed.snowstorm.fhir.repositories.FHIRConceptMapRepository;
import org.snomed.snowstorm.fhir.repositories.FHIRMapElementRepository;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FHIRConceptMapServiceTest {

	@Mock
	private FHIRConceptMapRepository conceptMapRepository;

	@Mock
	private CodeSystemService codeSystemService;

	@Mock
	private FHIRConceptMapImplicitConfig implicitMapConfig;

	@Mock
	private CodeSystemDefaultConfigurationService codeSystemDefaultConfigurationService;

	@Mock
	private IdentifierComponentService identifierComponentService;

	@InjectMocks
	private FHIRConceptMapService service;

	@BeforeEach
	void setUp() {
		when(implicitMapConfig.getImplicitMaps()).thenReturn(List.of(
				new FHIRSnomedConceptMapConfig("900000000000497000", "Test map", "http://snomed.info/sct", "http://hl7.org/fhir/sid/icd-10", "equivalent")
		));
		when(implicitMapConfig.getSnomedCorrelationToFhirEquivalenceMap()).thenReturn(Collections.emptyMap());
		service.init();
	}

	@Test
	void findAllOmitsImplicitMapsWhenNoSnomedVersionImported() {
		when(codeSystemService.findAll()).thenReturn(List.of(new CodeSystem(CodeSystemService.SNOMEDCT, CodeSystemService.MAIN)));
		when(codeSystemService.findLatestImportedVersion(CodeSystemService.SNOMEDCT)).thenReturn(null);
		when(conceptMapRepository.findAll(any(PageRequest.class))).thenReturn(new PageImpl<>(List.of()));

		List<FHIRConceptMap> all = service.findAll();

		assertTrue(all.stream().noneMatch(FHIRConceptMap::isImplicitSnomedMap));
	}

	@Test
	void findAllIncludesImplicitMapsWhenSnomedVersionImported() {
		when(codeSystemService.findAll()).thenReturn(List.of(new CodeSystem(CodeSystemService.SNOMEDCT, CodeSystemService.MAIN)));
		when(codeSystemService.findLatestImportedVersion(CodeSystemService.SNOMEDCT)).thenReturn(new CodeSystemVersion());
		when(conceptMapRepository.findAll(any(PageRequest.class))).thenReturn(new PageImpl<>(List.of()));

		List<FHIRConceptMap> all = service.findAll();

		assertTrue(all.stream().anyMatch(FHIRConceptMap::isImplicitSnomedMap));
		assertTrue(all.stream().noneMatch(FHIRConceptMap::isAlternateIdentifierMap));
	}

	@Test
	void findAllIncludesAlternateIdentifierMapWhenAlternateSchemaCodeSystemImported() {
		when(codeSystemService.findAll()).thenReturn(List.of(new CodeSystem(CodeSystemService.SNOMEDCT, CodeSystemService.MAIN)));
		when(codeSystemService.findLatestImportedVersion(CodeSystemService.SNOMEDCT)).thenReturn(new CodeSystemVersion());
		when(codeSystemDefaultConfigurationService.getConfigurations()).thenReturn(Set.of(LOINC_CONFIG));
		when(codeSystemService.findLatestVisibleVersion(LOINC_CONFIG.shortName())).thenReturn(new CodeSystemVersion());
		when(conceptMapRepository.findAll(any(PageRequest.class))).thenReturn(new PageImpl<>(List.of()));

		List<FHIRConceptMap> all = service.findAll();

		assertTrue(all.stream().anyMatch(map -> map.isAlternateIdentifierMap()
				&& FHIRConceptMapService.ALTERNATE_IDENTIFIER_MAP_URL.equals(map.getUrl())));
	}

	@Test
	void findAllOmitsAlternateIdentifierMapWhenAlternateSchemaCodeSystemNotImported() {
		when(codeSystemService.findAll()).thenReturn(List.of(new CodeSystem(CodeSystemService.SNOMEDCT, CodeSystemService.MAIN)));
		when(codeSystemService.findLatestImportedVersion(CodeSystemService.SNOMEDCT)).thenReturn(new CodeSystemVersion());
		when(codeSystemDefaultConfigurationService.getConfigurations()).thenReturn(Set.of(LOINC_CONFIG));
		when(codeSystemService.findLatestImportedVersion(LOINC_CONFIG.shortName())).thenReturn(null);
		when(conceptMapRepository.findAll(any(PageRequest.class))).thenReturn(new PageImpl<>(List.of()));

		List<FHIRConceptMap> all = service.findAll();

		assertTrue(all.stream().noneMatch(FHIRConceptMap::isAlternateIdentifierMap));
	}

	private static final CodeSystemDefaultConfiguration LOINC_CONFIG = new CodeSystemDefaultConfiguration("LOINC Ontology", "SNOMEDCT-LOINCEXT",
			"11010000107", "us", "Regenstrief Institute", "http://loinc.org", "30051010000102");
}
