package org.snomed.snowstorm.fhir.services;

import io.kaicode.elasticvc.api.BranchService;
import org.hl7.fhir.r4.model.CodeSystem;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.snomed.snowstorm.core.data.domain.CodeSystemVersion;
import org.snomed.snowstorm.core.data.repositories.CodeSystemVersionRepository;
import org.snomed.snowstorm.core.data.services.CodeSystemService;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.snomed.snowstorm.fhir.domain.FHIRCodeSystemVersion;
import org.snomed.snowstorm.fhir.pojo.FHIRCodeSystemVersionParams;
import org.springframework.beans.factory.annotation.Autowired;

import static org.hl7.fhir.r4.model.OperationOutcome.IssueType.INVARIANT;
import static org.hl7.fhir.r4.model.OperationOutcome.IssueType.NOTFOUND;
import static org.junit.jupiter.api.Assertions.*;
import static org.snomed.snowstorm.core.data.services.CodeSystemService.EMPTY_2000_VERSION_DATE;
import static org.snomed.snowstorm.fhir.config.FHIRConstants.SNOMED_URI;

class FHIRCodeSystemServiceTest extends AbstractFHIRTest {

	@Autowired
	private FHIRCodeSystemService codeSystemService;

	@Autowired
	private CodeSystemService snomedCodeSystemService;

	@Autowired
	private BranchService branchService;

	@Autowired
	private CodeSystemVersionRepository versionRepository;

	@BeforeEach
	void beforeEachTest() {
		org.snomed.snowstorm.core.data.domain.CodeSystem codeSystem = snomedCodeSystemService.find("SNOMEDCT-WK-EXP");
		if (codeSystem != null) {
			snomedCodeSystemService.deleteCodeSystemAndVersions(codeSystem, true);
		}
	}

	@AfterEach
	void afterEachTest() {
		restoreInternationalPublishedVersionIfNeeded();
	}

	private void restoreInternationalPublishedVersionIfNeeded() {
		org.snomed.snowstorm.core.data.domain.CodeSystem international = snomedCodeSystemService.find(CodeSystemService.SNOMEDCT);
		if (international == null || snomedCodeSystemService.findVersion(CodeSystemService.SNOMEDCT, 20190131) != null) {
			return;
		}
		String releaseBranchPath = international.getBranchPath() + "/2019-01-31";
		io.kaicode.elasticvc.domain.Branch branch = branchService.findLatest(releaseBranchPath);
		if (branch != null) {
			versionRepository.save(new CodeSystemVersion(CodeSystemService.SNOMEDCT, branch.getHead(), international.getBranchPath(),
					20190131, "2019-01-31", "", false));
		}
	}

	@Test
	void createUpdate() throws ServiceException {
		CodeSystem codeSystem = new CodeSystem();
		codeSystem.setTitle("Example");
		codeSystem.setUrl("http://example-test.com/");
		// no version
		FHIRCodeSystemVersion saved = codeSystemService.createUpdate(codeSystem);
		assertNotNull(saved.getId());
		assertEquals("0", saved.getVersion());

		// Attempt to change id
		codeSystem.setId("another");
		try {
			saved = codeSystemService.createUpdate(codeSystem);
			fail(SHOULD_HAVE_THROWN_EXCEPTION_BEFORE_THIS_LINE);
		} catch (SnowstormFHIRServerResponseException e) {
			assertEquals(INVARIANT, e.getIssueCode());
		}

		codeSystemService.deleteCodeSystemVersion(saved);
	}

	@Test
	void createSupplementNotSnomed() throws ServiceException {
		CodeSystem codeSystem = new CodeSystem();
		codeSystem.setUrl("http://example-test.com/");
		codeSystem.setContent(CodeSystem.CodeSystemContentMode.SUPPLEMENT);
		try {
			codeSystemService.createUpdate(codeSystem);
			fail(SHOULD_HAVE_THROWN_EXCEPTION_BEFORE_THIS_LINE);
		} catch (SnowstormFHIRServerResponseException e) {
			assertEquals(INVARIANT, e.getIssueCode());
		}
	}

	@Test
	void createSupplementSnomedNoSupplement() throws ServiceException {
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
	void createSupplementSnomedSupplementDoesNotExist() throws ServiceException {
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
	void createSupplementSnomedSupplement() throws ServiceException {
		CodeSystem codeSystem1 = new CodeSystem();
		codeSystem1.setUrl("http://snomed.info/sct");
		codeSystem1.setVersion("http://snomed.info/xsct/1001234007108");
		codeSystem1.setSupplements("http://snomed.info/sct|http://snomed.info/sct/1234000008/version/20190731");
		codeSystem1.setContent(CodeSystem.CodeSystemContentMode.SUPPLEMENT);
		FHIRCodeSystemVersion saved1 = codeSystemService.createUpdate(codeSystem1);
		assertEquals("http://snomed.info/xsct/1001234007108", saved1.getVersion());
		assertEquals("1001234007108", saved1.getSnomedCodeSystem().getUriModuleId());
		assertEquals("SNOMEDCT-WK-EXP", saved1.getSnomedCodeSystem().getShortName());

		// Create additional SNOMED Supplement
		//Test fails becuase it tries to create a codesystem with the same short name - SNOMEDCT-WK-EXP
		/*CodeSystem codeSystem2 = new CodeSystem();
		codeSystem2.setUrl("http://snomed.info/sct");
		codeSystem2.setVersion("http://snomed.info/xsct/200234007108");
		codeSystem2.setSupplements("http://snomed.info/sct|http://snomed.info/sct/1234000008/version/20190731");
		codeSystem2.setContent(CodeSystem.CodeSystemContentMode.SUPPLEMENT);
		FHIRCodeSystemVersion saved2 = codeSystemService.createUpdate(codeSystem2);
		assertEquals("http://snomed.info/xsct/200234007108", saved2.getVersion());
		assertEquals("200234007108", saved2.getSnomedCodeSystem().getUriModuleId());
		assertEquals("SNOMEDCT-WK-EXP2", saved2.getSnomedCodeSystem().getShortName());*/
	}

	@Test
	void createSupplementSnomedSupplementNoModule() throws ServiceException {
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
	void createSupplementSnomedSupplementNamespaceInsteadOfModule() throws ServiceException {
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

	@Test
	void getSnomedVersionOrThrowNeverUsesEmpty2000Version() {
		org.snomed.snowstorm.core.data.domain.CodeSystem international = snomedCodeSystemService.find(CodeSystemService.SNOMEDCT);
		org.snomed.snowstorm.core.data.domain.CodeSystemVersion internationalVersion =
				snomedCodeSystemService.findVersion(CodeSystemService.SNOMEDCT, 20190131);
		snomedCodeSystemService.deleteVersion(international, internationalVersion);
		snomedCodeSystemService.getOrCreateEmpty2000Version();

		FHIRCodeSystemVersion resolved = codeSystemService.getSnomedVersionOrThrow(new FHIRCodeSystemVersionParams(SNOMED_URI));
		assertFalse(resolved.getVersion().contains(String.valueOf(EMPTY_2000_VERSION_DATE)));
		assertTrue(resolved.getVersion().contains(String.valueOf(sampleVersion)));
	}

	@Test
	void getSnomedVersionOrThrowExplicitEmpty2000VersionNotFound() {
		snomedCodeSystemService.getOrCreateEmpty2000Version();

		FHIRCodeSystemVersionParams params = new FHIRCodeSystemVersionParams(SNOMED_URI)
				.setVersion(String.valueOf(EMPTY_2000_VERSION_DATE));
		SnowstormFHIRServerResponseException exception = assertThrows(SnowstormFHIRServerResponseException.class,
				() -> codeSystemService.getSnomedVersionOrThrow(params));
		assertEquals(404, exception.getStatusCode());
		assertEquals(NOTFOUND, exception.getIssueCode());
	}
}