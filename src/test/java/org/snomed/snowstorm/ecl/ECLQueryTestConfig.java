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

public abstract class ECLQueryTestConfig  extends TestConfig {

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

	@Autowired
	ReferencedConceptsLookupService referencedConceptsLookupService;

	public void deleteAll() throws InterruptedException {
		try {
			branchService.deleteAll();
			conceptService.deleteAll();
			codeSystemService.deleteAll();
			classificationService.deleteAll();
			permissionService.deleteAll();
			// ConceptService.deleteAll does not clear the concepts lookup index. A lookup left on MAIN by an
			// earlier fixture still has no end date, so it matches the criteria of the MAIN rebuilt below and
			// answers member of queries with the previous fixture's members.
			referencedConceptsLookupService.deleteAll();
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			throw e;
		}
	}
}
