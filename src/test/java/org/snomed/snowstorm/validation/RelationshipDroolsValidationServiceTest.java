package org.snomed.snowstorm.validation;

import io.kaicode.elasticvc.api.BranchCriteria;
import io.kaicode.elasticvc.api.VersionControlHelper;
import org.junit.Assert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.core.data.domain.Concepts;
import org.snomed.snowstorm.core.data.domain.Relationship;
import org.snomed.snowstorm.core.data.services.ConceptService;
import org.snomed.snowstorm.core.data.services.QueryService;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.springframework.beans.factory.annotation.Autowired;

class RelationshipDroolsValidationServiceTest extends AbstractTest {

	@Autowired
	private ConceptService conceptService;

	@Autowired
	private VersionControlHelper versionControlHelper;

	@Autowired
	private QueryService queryService;

	private RelationshipDroolsValidationService service;

	@BeforeEach
	void setup() throws ServiceException {
		String branchPath = "MAIN";
		conceptService.create(new Concept("100001")
						.addAxiom(new Relationship(Concepts.ISA, Concepts.SNOMEDCT_ROOT)), branchPath);

		BranchCriteria branchCriteria = versionControlHelper.getBranchCriteria(branchPath);
		DisposableQueryService disposableQueryService = new DisposableQueryService(queryService, branchPath, branchCriteria);
		service = new RelationshipDroolsValidationService(disposableQueryService);
	}

	@Test
	void hasActiveInboundStatedRelationship() throws Exception {
		Assert.assertTrue(service.hasActiveInboundStatedRelationship(Concepts.SNOMEDCT_ROOT, Concepts.ISA));
		Assert.assertFalse(service.hasActiveInboundStatedRelationship(Concepts.SNOMEDCT_ROOT, "10000123"));
		Assert.assertFalse(service.hasActiveInboundStatedRelationship("100002", Concepts.ISA));
	}

}
