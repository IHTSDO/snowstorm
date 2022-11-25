package org.snomed.snowstorm.fhir.services;

import io.kaicode.elasticvc.api.BranchService;
import org.hl7.fhir.r4.model.CodeSystem;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.snomed.snowstorm.core.data.services.AdminOperationsService;
import org.snomed.snowstorm.core.data.services.CodeSystemService;
import org.snomed.snowstorm.core.data.services.SBranchService;
import org.snomed.snowstorm.fhir.domain.FHIRCodeSystemVersion;
import org.springframework.beans.factory.annotation.Autowired;

import static org.hl7.fhir.r4.model.OperationOutcome.IssueType.INVARIANT;
import static org.hl7.fhir.r4.model.OperationOutcome.IssueType.NOTFOUND;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class FHIRCodeSystemServiceTest extends AbstractFHIRTest {

	@Autowired
	private FHIRCodeSystemService codeSystemService;

	@Autowired
	private CodeSystemService snomedCodeSystemService;

	@Autowired
	private AdminOperationsService adminOperationsService;

	@BeforeEach
	void beforeEachTest() {
		org.snomed.snowstorm.core.data.domain.CodeSystem codeSystem = snomedCodeSystemService.find("SNOMEDCT-WK-EXP");
		if (codeSystem != null) {
			snomedCodeSystemService.deleteCodeSystemAndVersions(codeSystem);
			adminOperationsService.hardDeleteBranch(codeSystem.getBranchPath());
		}
	}

	@Test
	void createUpdate() {
		CodeSystem codeSystem = new CodeSystem();
		codeSystem.setUrl("http://example-test.com/");
		// no version
		FHIRCodeSystemVersion saved = codeSystemService.createUpdate(codeSystem);
		assertNotNull(saved.getId());
		assertEquals("0", saved.getVersion());

		// Attempt to change id
		codeSystem.setId("another");
		try {
			codeSystemService.createUpdate(codeSystem);
			fail(SHOULD_HAVE_THROWN_EXCEPTION_BEFORE_THIS_LINE);
		} catch (SnowstormFHIRServerResponseException e) {
			assertEquals(INVARIANT, e.getIssueCode());
		}
	}

	@Test
	void createSupplementNotSnomed() {
		CodeSystem codeSystem = new CodeSystem();
		codeSystem.setUrl("http://example-test.com/");
		codeSystem.setContent(CodeSystem.CodeSystemContentMode.SUPPLEMENT);
		try {
			codeSystemService.createUpdate(codeSystem);
			fail(SHOULD_HAVE_THROWN_EXCEPTION_BEFORE_THIS_LINE);
		} catch (SnowstormFHIRServerResponseException e) {
			assertEquals(OperationOutcome.IssueType.NOTSUPPORTED, e.getIssueCode());
		}
	}

	@Test
	void createSupplementSnomedNoSupplement() {
		CodeSystem codeSystem = new CodeSystem();
		codeSystem.setUrl("http://snomed.info/sct");
		codeSystem.setContent(CodeSystem.CodeSystemContentMode.SUPPLEMENT);
		try {
			codeSystemService.createUpdate(codeSystem);
			fail(SHOULD_HAVE_THROWN_EXCEPTION_BEFORE_THIS_LINE);
		} catch (SnowstormFHIRServerResponseException e) {
			assertEquals(INVARIANT, e.getIssueCode());
		}
	}

	@Test
	void createSupplementSnomedSupplementDoesNotExist() {
		CodeSystem codeSystem = new CodeSystem();
		codeSystem.setUrl("http://snomed.info/sct");
		codeSystem.setSupplements("http://snomed.info/sct|http://snomed.info/sct/900000000000207008/version/20010130");
		codeSystem.setContent(CodeSystem.CodeSystemContentMode.SUPPLEMENT);
		try {
			codeSystemService.createUpdate(codeSystem);
			fail(SHOULD_HAVE_THROWN_EXCEPTION_BEFORE_THIS_LINE);
		} catch (SnowstormFHIRServerResponseException e) {
			assertEquals(NOTFOUND, e.getIssueCode());
		}
	}

	@Test
	void createSupplementSnomedSupplement() {
		CodeSystem codeSystem = new CodeSystem();
		codeSystem.setUrl("http://snomed.info/sct");
		codeSystem.setVersion("http://snomed.info/xsct/11234007108");
		codeSystem.setSupplements("http://snomed.info/sct|http://snomed.info/sct/1234000008/version/20190731");
		codeSystem.setContent(CodeSystem.CodeSystemContentMode.SUPPLEMENT);
		FHIRCodeSystemVersion saved = codeSystemService.createUpdate(codeSystem);
		assertEquals("http://snomed.info/xsct/11234007108", saved.getVersion());
		assertEquals("11234007108", saved.getSnomedCodeSystem().getUriModuleId());
	}

	@Test
	void createSupplementSnomedSupplementNoModule() {
		CodeSystem codeSystem = new CodeSystem();
		codeSystem.setUrl("http://snomed.info/sct");
		codeSystem.setVersion("http://snomed.info/xsct");
		codeSystem.setSupplements("http://snomed.info/sct|http://snomed.info/sct/1234000008/version/20190731");
		codeSystem.setContent(CodeSystem.CodeSystemContentMode.SUPPLEMENT);
		try {
			codeSystemService.createUpdate(codeSystem);
			fail(SHOULD_HAVE_THROWN_EXCEPTION_BEFORE_THIS_LINE);
		} catch (SnowstormFHIRServerResponseException e) {
			assertEquals("The version parameter for the 'http://snomed.info/sct' system must use the format 'http://snomed.info/sct/[sctid]' or " +
					"http://snomed.info/sct/[sctid]/version/[YYYYMMDD]. Version provided does not match: 'http://snomed.info/xsct'.", e.getMessage());
			assertEquals(INVARIANT, e.getIssueCode());
		}
	}

	@Test
	void createSupplementSnomedSupplementNamespaceInsteadOfModule() {
		CodeSystem codeSystem = new CodeSystem();
		codeSystem.setUrl("http://snomed.info/sct");
		codeSystem.setVersion("http://snomed.info/xsct/1000003");// Demo namespace "1000003"
		codeSystem.setSupplements("http://snomed.info/sct|http://snomed.info/sct/1234000008/version/20190731");
		codeSystem.setContent(CodeSystem.CodeSystemContentMode.SUPPLEMENT);
		try {
			codeSystemService.createUpdate(codeSystem);
			fail(SHOULD_HAVE_THROWN_EXCEPTION_BEFORE_THIS_LINE);
		} catch (SnowstormFHIRServerResponseException e) {
			assertEquals(INVARIANT, e.getIssueCode());
			assertTrue(e.getMessage().startsWith("The URL of this SNOMED CT CodeSystem supplement must have a version that follows the SNOMED CT URI standard and includes a module id. " +
					"If a namespace was given in the version URL then the module id '11000003104' could be used. "), "Actual message: " + e.getMessage());
		}
	}
}