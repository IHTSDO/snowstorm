package org.snomed.snowstorm.core.data.services;

import io.kaicode.elasticvc.api.BranchService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.TestConfig;
import org.snomed.snowstorm.core.data.domain.CodeSystem;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import static org.junit.Assert.assertEquals;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = TestConfig.class)
public class CodeSystemServiceTest extends AbstractTest {

	@Autowired
	private CodeSystemService codeSystemService;

	@Autowired
	private BranchService branchService;

	@Before
	public void setup() {
		branchService.create("MAIN");
	}

	@Test
	public void createCodeSystems() {
		codeSystemService.createCodeSystem(new CodeSystem("SNOMEDCT", "MAIN"));

		assertEquals(1, codeSystemService.findAll().size());

		CodeSystem codeSystemBe = new CodeSystem("SNOMEDCT-BE", "MAIN/SNOMEDCT-BE");
		codeSystemService.createCodeSystem(codeSystemBe);

		assertEquals(2, codeSystemService.findAll().size());

		assertEquals(codeSystemBe, codeSystemService.find("SNOMEDCT-BE"));
	}

}
