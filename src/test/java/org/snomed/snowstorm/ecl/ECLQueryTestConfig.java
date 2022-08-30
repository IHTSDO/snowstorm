package org.snomed.snowstorm.ecl;


import io.kaicode.elasticvc.api.BranchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.TestConfig;
import org.snomed.snowstorm.core.data.services.CodeSystemService;
import org.snomed.snowstorm.core.data.services.ConceptService;
import org.snomed.snowstorm.core.data.services.PermissionService;
import org.snomed.snowstorm.core.data.services.ReferenceSetMemberService;
import org.snomed.snowstorm.core.data.services.classification.ClassificationService;
import org.springframework.beans.factory.annotation.Autowired;

public abstract class ECLQueryTestConfig extends TestConfig {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	@Autowired
	BranchService branchService;

	@Autowired
	ConceptService conceptService;

	@Autowired
	ReferenceSetMemberService memberService;

	@Autowired
	CodeSystemService codeSystemService;

	@Autowired
	ClassificationService classificationService;

	@Autowired
	PermissionService permissionService;

	public void deleteAll() throws InterruptedException {
		try {
			branchService.deleteAll();
			conceptService.deleteAll();
			codeSystemService.deleteAll();
			classificationService.deleteAll();
			permissionService.deleteAll();
		} catch (Exception e) {
			logger.error(e.getMessage(),e);
			e.printStackTrace();
			throw e;
		}
	}
}
