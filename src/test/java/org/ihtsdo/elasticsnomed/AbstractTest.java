package org.ihtsdo.elasticsnomed;

import io.kaicode.elasticvc.api.BranchService;
import org.ihtsdo.elasticsnomed.core.data.services.CodeSystemService;
import org.ihtsdo.elasticsnomed.core.data.services.ConceptService;
import org.junit.After;
import org.springframework.beans.factory.annotation.Autowired;

public abstract class AbstractTest {

	@Autowired
	private BranchService branchService;

	@Autowired
	private ConceptService conceptService;

	@Autowired
	private CodeSystemService codeSystemService;

	@After
	public void defaultTearDown() {
		branchService.deleteAll();
		conceptService.deleteAll();
		codeSystemService.deleteAll();
	}

}
