package org.snomed.snowstorm.extension;

import io.kaicode.elasticvc.api.BranchService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.TestConfig;
import org.snomed.snowstorm.core.data.domain.CodeSystem;
import org.snomed.snowstorm.core.data.domain.ReferenceSetMember;
import org.snomed.snowstorm.core.data.services.BranchMetadataKeys;
import org.snomed.snowstorm.core.data.services.CodeSystemService;
import org.snomed.snowstorm.core.data.services.ReferenceSetMemberService;
import org.snomed.snowstorm.core.data.services.pojo.MemberSearchRequest;
import org.snomed.snowstorm.core.rf2.rf2import.ImportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
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

	private CodeSystem snomedct;

	private CodeSystem snomedctNZ;

	@Before
	public void setUp() {
		snomedct = new CodeSystem("SNOMEDCT", "MAIN");
		codeSystemService.createCodeSystem(snomedct);
		ReferenceSetMember enGbLanguageMember = new ReferenceSetMember(null,null, true,
				"900000000000207008", "900000000000508004", "675173018");
		enGbLanguageMember.setAdditionalField(ReferenceSetMember.LanguageFields.ACCEPTABILITY_ID, "900000000000548007");
		enGbLanguageMember.release(new Integer("20190731"));
		ReferenceSetMember enUsLanguageMember = new ReferenceSetMember(null, null, true,
				"900000000000207008","900000000000509007", "675173018");
		enUsLanguageMember.setAdditionalField(ReferenceSetMember.LanguageFields.ACCEPTABILITY_ID, "900000000000549004");
		enUsLanguageMember.release(new Integer("20190731"));
		referenceSetMemberService.createMembers(snomedct.getBranchPath(), new HashSet<>(Arrays.asList(enGbLanguageMember, enUsLanguageMember)));
		codeSystemService.createVersion(snomedct, new Integer("20190731"), "2019-07-31 release");

		ReferenceSetMember enGbLanguageMemberLatest = new ReferenceSetMember(null, null, true,
				"900000000000207008", "900000000000508004", "675173020");
		enGbLanguageMemberLatest.setAdditionalField(ReferenceSetMember.LanguageFields.ACCEPTABILITY_ID, "900000000000548007");
		enGbLanguageMemberLatest.release(new Integer("20200131"));
		ReferenceSetMember enUsLanguageMemberLatest = new ReferenceSetMember(null, null, true,
				"900000000000207008", "900000000000509007", "675173020");
		enUsLanguageMemberLatest.setAdditionalField(ReferenceSetMember.LanguageFields.ACCEPTABILITY_ID, "900000000000549004");

		ReferenceSetMember inactiveEnGbLanguageMember = new ReferenceSetMember(null, null, false,
				"900000000000207008", "900000000000508004", "2913577019");
		enGbLanguageMemberLatest.setAdditionalField(ReferenceSetMember.LanguageFields.ACCEPTABILITY_ID, "900000000000548007");
		enGbLanguageMemberLatest.release(new Integer("20200131"));
		enUsLanguageMemberLatest.release(new Integer("20200131"));
		inactiveEnGbLanguageMember.release(new Integer("20200131"));
		referenceSetMemberService.createMembers(snomedct.getBranchPath(), new HashSet<>(Arrays.asList(enGbLanguageMemberLatest, enUsLanguageMemberLatest, inactiveEnGbLanguageMember)));
		codeSystemService.createVersion(snomedct, new Integer("20200131"), "2020-01-31 release");

		snomedctNZ = new CodeSystem("SNOMEDCT-NZ", "MAIN/SNOMEDCT-NZ");
		snomedctNZ.setDependantVersionEffectiveTime(new Integer("20200131"));
		codeSystemService.createCodeSystem(snomedctNZ);
		Map<String, String> metaData = new HashMap<>();
		metaData.put(BranchMetadataKeys.DEPENDENCY_PACKAGE, "International_Release.zip");
		metaData.put(BranchMetadataKeys.PREVIOUS_PACKAGE, "NZExtension_Release.zip");
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


	}

	@Test
	public void testGenerateAdditionalLanguageRefsetsForTheFirstTime() {
		// check extension is upgraded to the dependent release
		MemberSearchRequest searchRequest = new MemberSearchRequest();
		searchRequest.referenceSet("900000000000508004");

		Page<ReferenceSetMember> updatedResult =  referenceSetMemberService.findMembers(snomedctNZ.getBranchPath(), searchRequest, PageRequest.of(0, 10));
		assertNotNull(updatedResult);
		assertEquals(3, updatedResult.getContent().size());

		updatedResult = referenceSetMemberService.findMembers(snomedctNZ.getBranchPath(), "675173020", PageRequest.of(0, 10));
		assertNotNull(updatedResult);
		assertEquals(2, updatedResult.getContent().size());

		extensionAdditionalLanguageRefsetUpgradeService.generateAdditionalLanguageRefsetDelta(snomedctNZ, snomedctNZ.getBranchPath(), true);

		updatedResult = referenceSetMemberService.findMembers(snomedctNZ.getBranchPath(), searchRequest, PageRequest.of(0, 10));
		assertNotNull(updatedResult);
		assertEquals(3, updatedResult.getContent().size());

		searchRequest = new MemberSearchRequest();
		searchRequest.referenceSet("271000210107");
		updatedResult =  referenceSetMemberService.findMembers(snomedctNZ.getBranchPath(), searchRequest, PageRequest.of(0, 10));
		assertNotNull(updatedResult);
		// first time only copies active components
		assertEquals(2, updatedResult.getContent().size());
		// check effective time, module id and reference set id
		for (ReferenceSetMember member : updatedResult.getContent()) {
			assertNull(member.getEffectiveTimeI());
			assertEquals("21000210109", member.getModuleId());
			assertEquals("271000210107", member.getRefsetId());
		}

		updatedResult = referenceSetMemberService.findMembers(snomedctNZ.getBranchPath(), "675173018", PageRequest.of(0, 10));
		assertNotNull(updatedResult);
		// 2 from INT and 1 fom NZ
		assertEquals(3, updatedResult.getContent().size());
	}


	@Test
	public void testGenerateAdditionalLanguageRefsetsWithDeltaOnly() {
		// check extension is upgraded to the dependent release
		MemberSearchRequest searchRequest = new MemberSearchRequest();
		searchRequest.referenceSet("900000000000508004");

		Page<ReferenceSetMember> updatedResult =  referenceSetMemberService.findMembers(snomedctNZ.getBranchPath(), searchRequest, PageRequest.of(0, 10));
		assertNotNull(updatedResult);
		assertEquals(3, updatedResult.getContent().size());

		updatedResult = referenceSetMemberService.findMembers(snomedctNZ.getBranchPath(), "675173018", PageRequest.of(0, 10));
		assertNotNull(updatedResult);
		assertEquals(2, updatedResult.getContent().size());

		ReferenceSetMember enNzLanguageMember = new ReferenceSetMember(null, 20190931, true,
				"21000210109", "271000210107", "675173020");
		enNzLanguageMember.setAdditionalField(ReferenceSetMember.LanguageFields.ACCEPTABILITY_ID, "900000000000549004");
		enNzLanguageMember.release(new Integer("20190931"));

		enNzLanguageMember = referenceSetMemberService.createMember(snomedctNZ.getBranchPath(), enNzLanguageMember);
		enNzLanguageMember = referenceSetMemberService.findMember(snomedctNZ.getBranchPath(), enNzLanguageMember.getMemberId());
		assertEquals("900000000000549004", enNzLanguageMember.getAdditionalField(ReferenceSetMember.LanguageFields.ACCEPTABILITY_ID));

		extensionAdditionalLanguageRefsetUpgradeService.generateAdditionalLanguageRefsetDelta(snomedctNZ, snomedctNZ.getBranchPath(), false);

		enNzLanguageMember = referenceSetMemberService.findMember(snomedctNZ.getBranchPath(), enNzLanguageMember.getMemberId());
		// current implementation always takes the latest version from the international release
		assertEquals("900000000000548007", enNzLanguageMember.getAdditionalField(ReferenceSetMember.LanguageFields.ACCEPTABILITY_ID));

		updatedResult = referenceSetMemberService.findMembers(snomedctNZ.getBranchPath(), searchRequest, PageRequest.of(0, 10));
		assertNotNull(updatedResult);
		assertEquals(3, updatedResult.getContent().size());

		searchRequest = new MemberSearchRequest();
		searchRequest.referenceSet("271000210107");
		updatedResult =  referenceSetMemberService.findMembers(snomedctNZ.getBranchPath(), searchRequest, PageRequest.of(0, 10));
		assertNotNull(updatedResult);
		assertEquals(2, updatedResult.getContent().size());
		// check effective time, module id and reference set id
		for (ReferenceSetMember member : updatedResult.getContent()) {
			assertNull(member.getEffectiveTimeI());
			assertEquals("21000210109", member.getModuleId());
			assertEquals("271000210107", member.getRefsetId());
			if (!member.isActive()) {
				assertEquals("2913577019", member.getReferencedComponentId());
			} else {
				assertEquals("675173020", member.getReferencedComponentId());
			}
		}
	}
}
