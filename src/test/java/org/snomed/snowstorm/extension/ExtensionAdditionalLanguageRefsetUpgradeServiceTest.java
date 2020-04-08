package org.snomed.snowstorm.extension;

import io.kaicode.elasticvc.api.BranchService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.snomed.otf.snomedboot.testutil.ZipUtil;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.TestConfig;
import org.snomed.snowstorm.core.data.domain.CodeSystem;
import org.snomed.snowstorm.core.data.domain.ReferenceSetMember;
import org.snomed.snowstorm.core.data.services.BranchMetadataKeys;
import org.snomed.snowstorm.core.data.services.CodeSystemService;
import org.snomed.snowstorm.core.data.services.ReferenceSetMemberService;
import org.snomed.snowstorm.core.data.services.pojo.MemberSearchRequest;
import org.snomed.snowstorm.core.rf2.RF2Type;
import org.snomed.snowstorm.core.rf2.rf2import.ImportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = TestConfig.class)
public class ExtensionAdditionalLanguageRefsetUpgradeServiceTest extends AbstractTest {

	@Autowired
	private ExtensionAdditionalLanguageRefsetUpgradeService extensionAdditionalLanguageRefsetUpgradeService;

	@Autowired
	private CodeSystemService codeSystemService;

	@Autowired
	private BranchService branchService;

	@Autowired
	private ImportService importService;

	@Autowired
	private ReferenceSetMemberService referenceSetMemberService;

	private File dependencyPackage;

	private File previousPackage;



	@Before
	public void setUp() throws Exception {
		dependencyPackage = ZipUtil.zipDirectoryRemovingCommentsAndBlankLines("src/test/resources/dummy-snomed-content/SnomedCT_MiniRF2_Base_snapshot");
		previousPackage = ZipUtil.zipDirectoryRemovingCommentsAndBlankLines("src/test/resources/dummy-snomed-content/SnomedCT_MiniRF2_Extension_snapshot");
	}

	@After
	public void tearDown() {
		super.defaultTearDown();
		if (dependencyPackage != null && dependencyPackage.exists()) {
			dependencyPackage.delete();
		}
		if (previousPackage != null && previousPackage.exists()) {
			previousPackage.delete();
		}
	}

	@Test
	public void testGenerateAdditionalLanguageRefsets() throws Exception {

		CodeSystem snomedct = new CodeSystem("SNOMEDCT", "MAIN");
		codeSystemService.createCodeSystem(snomedct);
		String jobId = importService.createJob(RF2Type.SNAPSHOT, snomedct.getBranchPath(), true, false);
		importService.importArchive(jobId, new FileInputStream(dependencyPackage));
		CodeSystem snomedctNZ = new CodeSystem("SNOMEDCT-NZ", "MAIN/SNOMEDCT-NZ");
		codeSystemService.createCodeSystem(snomedctNZ);
		String importjobId = importService.createJob(RF2Type.SNAPSHOT, snomedct.getBranchPath(), true, false);
		importService.importArchive(importjobId, new FileInputStream(previousPackage));

		Map<String, String> metaData = new HashMap<>();
		metaData.put(BranchMetadataKeys.DEPENDENCY_PACKAGE, dependencyPackage.getAbsolutePath());
		metaData.put(BranchMetadataKeys.PREVIOUS_PACKAGE, previousPackage.getAbsolutePath());
		metaData.put("defaultModuleId", "21000210109");
		metaData.put("defaultNamespace", "1000210");
		metaData.put("shortname", "NZ");
		metaData.put("requiredLanguageRefsets", "[{\n" +
				"        \"default\": \"false\",\n" +
				"        \"en\": \"900000000000508004\",\n" +
				"        \"readOnly\": \"true\",\n" +
				"        \"dialectName\": \"en-gb\"\n" +
				"      },\n" +
				"      {\n" +
				"        \"default\": \"true\",\n" +
				"        \"en\": \"271000210107\",\n" +
				"        \"dialectName\": \"en-nz\"\n" +
				"      },\n" +
				"      {\n" +
				"        \"default\": \"false\",\n" +
				"        \"mi\": \"291000210106\"\n" +
				"      },\n" +
				"      {\n" +
				"        \"default\": \"false\",\n" +
				"        \"en\": \"900000000000509007\",\n" +
				"        \"dialectName\": \"en-us\"\n" +
				"      }\n" +
				"    ]");
		branchService.updateMetadata("MAIN/SNOMEDCT-NZ", metaData);

		// check extension is upgraded to the dependent release
		ReferenceSetMember member = referenceSetMemberService.findMember(snomedctNZ.getBranchPath(), "9e143c2c-9f5b-462a-ac92-5bebf8a9ca03");
		assertNotNull(member);
		assertEquals("9e143c2c-9f5b-462a-ac92-5bebf8a9ca03", member.getMemberId());

		MemberSearchRequest searchRequest = new MemberSearchRequest();
		searchRequest.referenceSet("900000000000508004");

		Page<ReferenceSetMember> updatedResult =  referenceSetMemberService.findMembers(snomedctNZ.getBranchPath(), searchRequest, PageRequest.of(0, 10));
		assertNotNull(updatedResult);
		assertEquals(1, updatedResult.getContent().size());

		updatedResult = referenceSetMemberService.findMembers(snomedctNZ.getBranchPath(), "675173018", PageRequest.of(0, 10));
		assertNotNull(updatedResult);
		assertEquals(2, updatedResult.getContent().size());

		extensionAdditionalLanguageRefsetUpgradeService.generateAdditionalLanguageRefsetDelta(snomedctNZ, snomedctNZ.getBranchPath(), true);

		updatedResult = referenceSetMemberService.findMembers(snomedctNZ.getBranchPath(), searchRequest, PageRequest.of(0, 10));
		assertNotNull(updatedResult);
		assertEquals(1, updatedResult.getContent().size());
		assertEquals("9e143c2c-9f5b-462a-ac92-5bebf8a9ca03", updatedResult.getContent().get(0).getMemberId());


		searchRequest = new MemberSearchRequest();
		searchRequest.referenceSet("271000210107");
		updatedResult =  referenceSetMemberService.findMembers(snomedctNZ.getBranchPath(), searchRequest, PageRequest.of(0, 10));
		assertNotNull(updatedResult);
		assertEquals(1, updatedResult.getContent().size());
		assertEquals("675173018", updatedResult.getContent().get(0).getReferencedComponentId());
		assertEquals("21000210109", updatedResult.getContent().get(0).getModuleId());
		assertNotEquals("9e143c2c-9f5b-462a-ac92-5bebf8a9ca03", updatedResult.getContent().get(0).getMemberId());

		updatedResult = referenceSetMemberService.findMembers(snomedctNZ.getBranchPath(), "675173018", PageRequest.of(0, 10));
		assertNotNull(updatedResult);
		assertEquals(3, updatedResult.getContent().size());
	}
}
