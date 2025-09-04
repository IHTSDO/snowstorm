package org.snomed.snowstorm.core.data.services;

import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.ErrorResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.TestConfig;
import org.snomed.snowstorm.core.data.domain.CodeSystem;
import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.core.data.domain.Concepts;
import org.snomed.snowstorm.core.data.domain.Description;
import org.snomed.snowstorm.core.data.services.pojo.MultiSearchDescriptionCriteria;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.UncategorizedElasticsearchException;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = TestConfig.class)
class MultiSearchServiceTest extends AbstractTest {

	@Autowired
	private MultiSearchService multiSearchService;

	@Autowired
	private CodeSystemService codeSystemService;

	@Autowired
	private ConceptService conceptService;

	private ServiceTestUtil testUtil;

	@BeforeEach
	void setup() {
		testUtil = new ServiceTestUtil(conceptService);
	}

	@Test
	void testFindDescriptions() throws ServiceException {
		CodeSystem codeSystemInternational = new CodeSystem("SNOMEDCT", "MAIN");
		codeSystemService.createCodeSystem(codeSystemInternational);
		testUtil.createConceptWithPathIdAndTerm("MAIN", Concepts.CLINICAL_FINDING, "Clinical finding");
		String term = "fin";
		assertEquals(0, runSearch(term).getTotalElements(), "Nothing found because code system is not versioned");
		codeSystemService.createVersion(codeSystemInternational, 20190731, " Int 2019-07-31");

		CodeSystem codeSystemBE = new CodeSystem("SNOMEDCT-BE", "MAIN/SNOMEDCT-BE");
		codeSystemService.createCodeSystem(codeSystemBE);
		testUtil.createConceptWithPathIdAndTerm("MAIN/SNOMEDCT-BE", "123123404684003", "Some finding");

		assertEquals( 1, runSearch(term).getTotalElements(), "Only one found because SNOMEDCT-BE code system is not versioned yet");
		codeSystemService.createVersion(codeSystemBE, 20190931, "");

		Page<Description> descriptions = runSearch(term);
		assertEquals(2, descriptions.getTotalElements(), "1 International and 1 BE result found.");

		// inactivation of International concept
		Concept clinicalFinding = conceptService.find(Concepts.CLINICAL_FINDING, "MAIN");
		clinicalFinding.setActive(false);
		conceptService.update(clinicalFinding, "MAIN");
		codeSystemService.createVersion(codeSystemInternational, 20200131, "");

		clinicalFinding = conceptService.find(Concepts.CLINICAL_FINDING, "MAIN");
		Assertions.assertFalse(clinicalFinding.isActive());

		// search both
		descriptions = runSearch(term);
		assertEquals(2, descriptions.getTotalElements(), "Only version from BE and 1 from International version");
		assertEquals("Some finding", descriptions.getContent().get(0).getTerm());
		assertEquals("MAIN/SNOMEDCT-BE", descriptions.getContent().get(0).getPath());
		assertEquals("Clinical finding", descriptions.getContent().get(1).getTerm());
		assertEquals("MAIN", descriptions.getContent().get(1).getPath());

		// search with active concept only
		descriptions = runSearch(term, true);
		assertEquals(1, descriptions.getTotalElements(), "Only one version for BE as International version is inactive");
		assertEquals("Some finding", descriptions.getContent().get(0).getTerm());
		assertEquals("MAIN/SNOMEDCT-BE", descriptions.getContent().get(0).getPath());

		// search with inactive concept only
		descriptions = runSearch(term, false);
		assertEquals(1, descriptions.getTotalElements(), "Only one version as International version is inactivated");
		assertEquals("Clinical finding", descriptions.getContent().get(0).getTerm());
		assertEquals("MAIN", descriptions.getContent().get(0).getPath());

	}


	@Test
	void testExceptionHandling() {
		// assert throws exception
		Throwable rootCause = assertThrows(UncategorizedElasticsearchException.class, this::simulateElasticsearchException).getRootCause();
		assertTrue(rootCause instanceof ElasticsearchException);
		ErrorResponse errorResponse = ((ElasticsearchException) rootCause).response();
		assertNotNull(errorResponse);
		assertEquals(400, errorResponse.status());
		assertTrue(errorResponse.toString().contains("\"type\":\"query_shard_exception\",\"reason\":\"failed to create query: For input string: \\\"2024-09-01\\\""), "Error response: " + errorResponse);
	}

	private Page<Description> runSearch(String term) {
		MultiSearchDescriptionCriteria criteria = (MultiSearchDescriptionCriteria) new MultiSearchDescriptionCriteria().term(term);
		return multiSearchService.findDescriptions(criteria, PageRequest.of(0, 10));
	}

	private Page<Description> runSearch(String term, Boolean conceptActive) {
		if (conceptActive == null) {
			return runSearch(term);
		} else {
			MultiSearchDescriptionCriteria criteria = (MultiSearchDescriptionCriteria) new MultiSearchDescriptionCriteria().term(term).conceptActive(conceptActive);
			return multiSearchService.findDescriptions(criteria, PageRequest.of(0, 10));
		}
	}
}