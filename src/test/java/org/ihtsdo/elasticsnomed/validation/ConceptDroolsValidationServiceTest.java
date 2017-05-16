package org.ihtsdo.elasticsnomed.validation;

import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.api.VersionControlHelper;
import org.ihtsdo.drools.exception.RuleExecutorException;
import org.ihtsdo.elasticsnomed.TestConfig;
import org.ihtsdo.elasticsnomed.core.data.domain.Concept;
import org.ihtsdo.elasticsnomed.core.data.domain.Concepts;
import org.ihtsdo.elasticsnomed.core.data.services.ConceptService;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = TestConfig.class)
public class ConceptDroolsValidationServiceTest {

	@Autowired
	private BranchService branchService;

	@Autowired
	private ConceptService conceptService;

	@Autowired
	private VersionControlHelper versionControlHelper;

	@Autowired
	private ElasticsearchOperations elasticsearchOperations;
	private ConceptDroolsValidationService validationService;

	@Before
	public void setup() {
		String branch = "MAIN";
		branchService.create(branch);
		conceptService.create(new Concept("1", null, true, null, Concepts.PRIMITIVE), branch);
		conceptService.create(new Concept("2", null, false, null, Concepts.PRIMITIVE), branch);

		validationService = new ConceptDroolsValidationService(branch, versionControlHelper.getBranchCriteria(branch), elasticsearchOperations);
	}

	@Test
	public void isActive() throws Exception {
		Assert.assertTrue(validationService.isActive("1"));
		Assert.assertFalse(validationService.isActive("2"));
		try {
			Assert.assertFalse(validationService.isActive("3"));
			Assert.fail("Should have thrown exception.");
		} catch (RuleExecutorException e) {
			// good
		}
	}

	@After
	public void tearDown() {
		conceptService.deleteAll();
		branchService.deleteAll();
	}

}
