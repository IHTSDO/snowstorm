package org.snomed.snowstorm.core.data.services;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.TestConfig;
import org.snomed.snowstorm.core.data.domain.CodeSystem;
import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.core.data.domain.Concepts;
import org.snomed.snowstorm.core.data.domain.Description;
import org.snomed.snowstorm.core.data.services.pojo.DescriptionCriteria;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = TestConfig.class)
public class MultiSearchServiceTest extends AbstractTest {

	@Autowired
	private MultiSearchService multiSearchService;

	@Autowired
	private CodeSystemService codeSystemService;

	@Autowired
	private CodeSystemUpgradeService codeSystemUpgradeService;

	@Autowired
	private ConceptService conceptService;

	private ServiceTestUtil testUtil;

	@Before
	public void setup() {
		testUtil = new ServiceTestUtil(conceptService);
	}

	@Test
	public void testFindDescriptions() throws ServiceException {
		CodeSystem codeSystemInternational = new CodeSystem("SNOMEDCT", "MAIN");
		codeSystemService.createCodeSystem(codeSystemInternational);
		testUtil.createConceptWithPathIdAndTerm("MAIN", Concepts.CLINICAL_FINDING, "Clinical finding");
		String term = "fin";
		assertEquals("Nothing found because code system is not versioned", 0, runSearch(term).getTotalElements());
		codeSystemService.createVersion(codeSystemInternational, 20190731, " Int 2019-07-31");

		CodeSystem codeSystemBE = new CodeSystem("SNOMEDCT-BE", "MAIN/SNOMEDCT-BE");
		codeSystemService.createCodeSystem(codeSystemBE);
		testUtil.createConceptWithPathIdAndTerm("MAIN/SNOMEDCT-BE", "123123404684003", "Some finding");
		codeSystemUpgradeService.upgrade(codeSystemBE, 20190731);

		assertEquals("Only one found because SNOMEDCT-BE code system is not versioned yet", 1, runSearch(term).getTotalElements());
		codeSystemService.createVersion(codeSystemBE, 20190931, "");

		Page<Description> descriptions = runSearch(term);
		assertEquals("1 International and 1 BE result found.", 2, descriptions.getTotalElements());

		// inactivation of International concept
		Concept clinicalFinding = conceptService.find(Concepts.CLINICAL_FINDING, "MAIN");
		clinicalFinding.setActive(false);
		conceptService.update(clinicalFinding, "MAIN");
		codeSystemService.createVersion(codeSystemInternational, 20200131, "");

		clinicalFinding = conceptService.find(Concepts.CLINICAL_FINDING, "MAIN");
		assertFalse(clinicalFinding.isActive());

		// search both
		descriptions = runSearch(term);
		assertEquals("Only version from BE and 1 from International version", 2, descriptions.getTotalElements());
		assertEquals("Some finding", descriptions.getContent().get(0).getTerm());
		assertEquals("MAIN/SNOMEDCT-BE", descriptions.getContent().get(0).getPath());
		assertEquals("Clinical finding", descriptions.getContent().get(1).getTerm());
		assertEquals("MAIN", descriptions.getContent().get(1).getPath());

		// search with active concept only
		descriptions = runSearch(term, true);
		assertEquals("Only one version for BE as International version is inactive", 1, descriptions.getTotalElements());
		assertEquals("Some finding", descriptions.getContent().get(0).getTerm());
		assertEquals("MAIN/SNOMEDCT-BE", descriptions.getContent().get(0).getPath());

		// search with inactive concept only
		descriptions = runSearch(term, false);
		assertEquals("Only one version as International version is inactivated", 1, descriptions.getTotalElements());
		assertEquals("Clinical finding", descriptions.getContent().get(0).getTerm());
		assertEquals("MAIN", descriptions.getContent().get(0).getPath());

	}

	private Page<Description> runSearch(String term) {
		DescriptionCriteria criteria = new DescriptionCriteria().term(term);
		return multiSearchService.findDescriptions(criteria, PageRequest.of(0, 10));
	}

	private Page<Description> runSearch(String term, Boolean conceptActive) {
		if (conceptActive == null) {
			return runSearch(term);
		} else {
			DescriptionCriteria criteria = new DescriptionCriteria().term(term).conceptActive(conceptActive);
			return multiSearchService.findDescriptions(criteria, PageRequest.of(0, 10));
		}
	}
}