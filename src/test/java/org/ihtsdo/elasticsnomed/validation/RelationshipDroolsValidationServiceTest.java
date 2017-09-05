package org.ihtsdo.elasticsnomed.validation;

import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.api.VersionControlHelper;

import org.ihtsdo.elasticsnomed.TestConfig;
import org.ihtsdo.elasticsnomed.core.data.domain.Concept;
import org.ihtsdo.elasticsnomed.core.data.domain.Concepts;
import org.ihtsdo.elasticsnomed.core.data.domain.Relationship;
import org.ihtsdo.elasticsnomed.core.data.services.ConceptService;
import org.ihtsdo.elasticsnomed.core.data.services.ServiceException;
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
public class RelationshipDroolsValidationServiceTest {

	@Autowired
	private BranchService branchService;

	@Autowired
	private ConceptService conceptService;

	@Autowired
	private VersionControlHelper versionControlHelper;

	@Autowired
	private ElasticsearchOperations elasticsearchOperations;

	private RelationshipDroolsValidationService service;

	@Before
	public void setup() throws ServiceException {
		String branchPath = "MAIN";
		branchService.create(branchPath);
		conceptService.create(new Concept("1").addRelationship(
				new Relationship(Concepts.ISA, Concepts.SNOMEDCT_ROOT)),
				branchPath);

		service = new RelationshipDroolsValidationService(versionControlHelper.getBranchCriteria(branchPath), elasticsearchOperations);
	}

	@Test
	public void hasActiveInboundStatedRelationship() throws Exception {
		Assert.assertTrue(service.hasActiveInboundStatedRelationship("1", Concepts.ISA));
		Assert.assertFalse(service.hasActiveInboundStatedRelationship("1", "123"));
		Assert.assertFalse(service.hasActiveInboundStatedRelationship("2", Concepts.ISA));
	}

	@After
	public void tearDown() {
		conceptService.deleteAll();
		branchService.deleteAll();
	}

}
