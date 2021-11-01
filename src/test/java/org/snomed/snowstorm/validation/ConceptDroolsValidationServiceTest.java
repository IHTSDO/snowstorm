package org.snomed.snowstorm.validation;

import io.kaicode.elasticvc.api.BranchCriteria;
import io.kaicode.elasticvc.api.VersionControlHelper;
import org.junit.Assert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.TestConfig;
import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.core.data.domain.Concepts;
import org.snomed.snowstorm.core.data.services.ConceptService;
import org.snomed.snowstorm.core.data.services.QueryService;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Collections;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = TestConfig.class)
class ConceptDroolsValidationServiceTest extends AbstractTest {

	@Autowired
	private ConceptService conceptService;

	@Autowired
	private VersionControlHelper versionControlHelper;

	@Autowired
	private ElasticsearchOperations elasticsearchOperations;

	@Autowired
	private QueryService queryService;

	private ConceptDroolsValidationService validationService;

	@BeforeEach
	void setup() throws ServiceException {
		String branch = "MAIN";
		conceptService.create(new Concept("100001", null, true, "10000111", Concepts.PRIMITIVE), branch);
		conceptService.create(new Concept("100002", null, false, "10000111", Concepts.PRIMITIVE), branch);

		BranchCriteria branchCriteria = versionControlHelper.getBranchCriteria(branch);
		DisposableQueryService disposableQueryService = new DisposableQueryService(queryService, branch, branchCriteria);
		validationService = new ConceptDroolsValidationService(branchCriteria, elasticsearchOperations, disposableQueryService, Collections.emptySet());
	}

	@Test
	void isActive() {
		Assert.assertTrue(validationService.isActive("100001"));
		Assert.assertFalse(validationService.isActive("100002"));
		Assert.assertFalse(validationService.isActive("100003"));
	}

}
