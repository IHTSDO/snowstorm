package org.snomed.snowstorm.fhir.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.snomed.snowstorm.core.data.domain.CodeSystem;
import org.snomed.snowstorm.core.data.domain.CodeSystemVersion;
import org.snomed.snowstorm.core.data.services.CodeSystemService;
import org.snomed.snowstorm.core.data.services.ConceptService;
import org.snomed.snowstorm.core.data.services.ReferenceSetMemberService;
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
	}
}
