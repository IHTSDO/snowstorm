package org.snomed.snowstorm.core.data.services;

import org.junit.jupiter.api.Test;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.core.data.domain.CodeSystem;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CodeSystemServiceTest extends AbstractTest {

	@Autowired
	private CodeSystemService codeSystemService;

	@Test
	void createCodeSystems() {
		codeSystemService.createCodeSystem(new CodeSystem("SNOMEDCT", "MAIN").setOwner("SNOMED International"));

		assertEquals(1, codeSystemService.findAll().size());

		CodeSystem codeSystemBe = new CodeSystem("SNOMEDCT-BE", "MAIN/SNOMEDCT-BE");
		codeSystemService.createCodeSystem(codeSystemBe);

		assertEquals(2, codeSystemService.findAll().size());

		assertEquals(codeSystemBe, codeSystemService.find("SNOMEDCT-BE"));
	}

	@Test
	void createCodeSystemWithBadBranchPath() {
		codeSystemService.createCodeSystem(new CodeSystem("SNOMEDCT", "MAIN"));
		assertEquals(1, codeSystemService.findAll().size());
		CodeSystem codeSystemBe = new CodeSystem("SNOMEDCT-TEST", "MAIN.test");
		assertThrows(IllegalArgumentException.class, () -> codeSystemService.createCodeSystem(codeSystemBe));
	}

	@Test
	void testFindLatestImportedVersion() {
		CodeSystem codeSystem = new CodeSystem("SNOMEDCT", "MAIN");
		codeSystemService.createCodeSystem(codeSystem);
		codeSystemService.createVersion(codeSystem, 20190731, "");

		// Now version it again with a later date, and recover the most recent one
		codeSystemService.createVersion(codeSystem, 20200131, "");
		assertEquals(20200131, codeSystemService.findLatestImportedVersion("SNOMEDCT").getEffectiveDate().intValue());

		// Versions in the future will be returned with this method.
		codeSystemService.createVersion(codeSystem, 20990131, "");
		assertEquals(20990131, codeSystemService.findLatestImportedVersion("SNOMEDCT").getEffectiveDate().intValue());

	}

	@Test
	void testFindLatestEffectiveVersion() {
		CodeSystem codeSystem = new CodeSystem("SNOMEDCT", "MAIN");
		codeSystemService.createCodeSystem(codeSystem);
		codeSystemService.createVersion(codeSystem, 20190131, "");

		codeSystemService.createVersion(codeSystem, 20190701, "");
		assertEquals(20190701, codeSystemService.findLatestVisibleVersion("SNOMEDCT").getEffectiveDate().intValue());

		// Versions in the future will NOT be returned with this method.
		codeSystemService.createVersion(codeSystem, 20990131, "");
		assertEquals(20190701, codeSystemService.findLatestVisibleVersion("SNOMEDCT").getEffectiveDate().intValue());

		codeSystemService.setLatestVersionCanBeFuture(true);
		assertEquals(20990131, codeSystemService.findLatestVisibleVersion("SNOMEDCT").getEffectiveDate().intValue());
		codeSystemService.setLatestVersionCanBeFuture(false);
	}

}
