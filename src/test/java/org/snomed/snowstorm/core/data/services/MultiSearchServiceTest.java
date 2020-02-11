package org.snomed.snowstorm.core.data.services;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.TestConfig;
import org.snomed.snowstorm.core.data.domain.CodeSystem;
import org.snomed.snowstorm.core.data.domain.Concepts;
import org.snomed.snowstorm.core.data.domain.Description;
import org.snomed.snowstorm.core.data.services.pojo.DescriptionCriteria;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import static org.junit.Assert.assertEquals;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = TestConfig.class)
public class MultiSearchServiceTest extends AbstractTest {

	@Autowired
	private MultiSearchService multiSearchService;

	@Autowired
	private CodeSystemService codeSystemService;

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

		CodeSystem codeSystemBE = new CodeSystem("SNOMEDCT-BE", "MAIN/SNOMEDCT-BE");
		codeSystemService.createCodeSystem(codeSystemBE);
		testUtil.createConceptWithPathIdAndTerm("MAIN/SNOMEDCT-BE", "123123404684003", "Some finding");

		String term = "fin";
		assertEquals("Nothing found because code systems not versioned", 0, runSearch(term).getTotalElements());

		codeSystemService.createVersion(codeSystemInternational, 20200131, "");
		assertEquals("International result found.", 1, runSearch(term).getTotalElements());

		codeSystemService.upgrade("SNOMEDCT-BE", 20200131);
		codeSystemService.createVersion(codeSystemBE, 20200201, "");
		Page<Description> descriptions = runSearch(term);
		assertEquals("1 International and 1 BE result found.", 2, descriptions.getTotalElements());

		assertEquals("Some finding", descriptions.getContent().get(0).getTerm());
		assertEquals("MAIN/SNOMEDCT-BE", descriptions.getContent().get(0).getPath());
		assertEquals("Clinical finding", descriptions.getContent().get(1).getTerm());
		assertEquals("MAIN", descriptions.getContent().get(1).getPath());
	}

	public Page<Description> runSearch(String term) {
		return multiSearchService.findDescriptions(new DescriptionCriteria().term(term), PageRequest.of(0, 10));
	}
}