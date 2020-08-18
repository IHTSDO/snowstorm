package org.snomed.snowstorm.extension;

import io.kaicode.elasticvc.api.BranchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.TestConfig;
import org.snomed.snowstorm.config.Config;
import org.snomed.snowstorm.core.data.domain.CodeSystem;
import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.core.data.domain.Description;
import org.snomed.snowstorm.core.data.domain.ReferenceSetMember;
import org.snomed.snowstorm.core.data.services.BranchMetadataKeys;
import org.snomed.snowstorm.core.data.services.CodeSystemService;
import org.snomed.snowstorm.core.data.services.ConceptService;
import org.snomed.snowstorm.core.data.services.ReferenceSetMemberService;
import org.snomed.snowstorm.core.data.services.pojo.MemberSearchRequest;
import org.snomed.snowstorm.core.rf2.rf2import.ImportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static org.junit.Assert.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = TestConfig.class)
class ExtensionAdditionalLanguageRefsetUpgradeServiceTest extends AbstractTest {

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

	@Autowired
	private ConceptService conceptService;

	private CodeSystem snomedct;

	private CodeSystem snomedctNZ;

	@BeforeEach
	void setUp() throws Exception {
		snomedct = new CodeSystem("SNOMEDCT", "MAIN");
		codeSystemService.createCodeSystem(snomedct);
		ReferenceSetMember enGbLanguageMember = new ReferenceSetMember(null,null, true,
				"900000000000207008", "900000000000508004", "675173018");
		enGbLanguageMember.setAdditionalField(ReferenceSetMember.LanguageFields.ACCEPTABILITY_ID, "900000000000548007");
		ReferenceSetMember enUsLanguageMember = new ReferenceSetMember(null, null, true,
				"900000000000207008","900000000000509007", "675173018");
		enUsLanguageMember.setAdditionalField(ReferenceSetMember.LanguageFields.ACCEPTABILITY_ID, "900000000000549004");
		Concept concept = new Concept("123456");
		Description description = new Description("675173018", "testing");
		description.addLanguageRefsetMember(enGbLanguageMember);
		description.addLanguageRefsetMember(enUsLanguageMember);
		concept.addDescription(description);

		conceptService.create(concept, snomedct.getBranchPath());
		codeSystemService.createVersion(snomedct, new Integer("20190731"), "2019-07-31 release");

		ReferenceSetMember enGbLanguageMemberLatest = new ReferenceSetMember(null, null, true,
				"900000000000207008", "900000000000508004", "675173019");
		enGbLanguageMemberLatest.setAdditionalField(ReferenceSetMember.LanguageFields.ACCEPTABILITY_ID, "900000000000548007");

		ReferenceSetMember enUsLanguageMemberLatest = new ReferenceSetMember(null, null, true,
				"900000000000207008", "900000000000509007", "675173019");
		enUsLanguageMemberLatest.setAdditionalField(ReferenceSetMember.LanguageFields.ACCEPTABILITY_ID, "900000000000549004");

		Description latestDescription = new Description("675173019", "new term testing");
		latestDescription.addLanguageRefsetMember(enGbLanguageMemberLatest);
		latestDescription.addLanguageRefsetMember(enUsLanguageMemberLatest);
		concept = conceptService.find("123456", snomedct.getBranchPath());
		concept.addDescription(latestDescription);
		concept = conceptService.update(concept, snomedct.getBranchPath());

		// this is for testing complete copy as only active ones are copied.
		Description existing = concept.getDescription("675173018");
		ReferenceSetMember existingGbMember = existing.getLangRefsetMembers().get("900000000000508004");
		existingGbMember.setActive(false);
		referenceSetMemberService.updateMember("MAIN", existingGbMember);

		assertEquals(1, concept.getDescription("675173018").getAcceptabilityMap().keySet().size());

		MemberSearchRequest searchRequest = new MemberSearchRequest();
		searchRequest.referenceSet("900000000000508004");

		Page<ReferenceSetMember> updatedResult =  referenceSetMemberService.findMembers(snomedct.getBranchPath(), searchRequest, PageRequest.of(0, 10));
		assertNotNull(updatedResult);
		assertEquals(2, updatedResult.getContent().size());

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
	void testGenerateAdditionalLanguageRefsetsWithCompleteCopy() {
		// check extension is upgraded to the dependent release
		MemberSearchRequest enGbSearchRequest = new MemberSearchRequest();
		enGbSearchRequest.referenceSet("900000000000508004");

		// only two en-eb one active and the other one inactive
		Page<ReferenceSetMember> updatedResult =  referenceSetMemberService.findMembers(snomedctNZ.getBranchPath(), enGbSearchRequest, PageRequest.of(0, 10));
		assertNotNull(updatedResult);
		assertEquals(2, updatedResult.getContent().size());

		// It should have two members for 675173019 (en and us)
		updatedResult = referenceSetMemberService.findMembers(snomedctNZ.getBranchPath(), "675173019", PageRequest.of(0, 10));
		assertNotNull(updatedResult);
		assertEquals(2, updatedResult.getContent().size());

		extensionAdditionalLanguageRefsetUpgradeService.generateAdditionalLanguageRefsetDelta(snomedctNZ, snomedctNZ.getBranchPath(), "900000000000508004", true);

		// after update there should still be 2 with en-gb
		updatedResult = referenceSetMemberService.findMembers(snomedctNZ.getBranchPath(), enGbSearchRequest, PageRequest.of(0, 10));
		assertNotNull(updatedResult);
		assertEquals(2, updatedResult.getContent().size());

		// search for nz-en
		MemberSearchRequest nzEnSearchRequest = new MemberSearchRequest();
		nzEnSearchRequest.referenceSet("271000210107");
		updatedResult =  referenceSetMemberService.findMembers(snomedctNZ.getBranchPath(), nzEnSearchRequest, PageRequest.of(0, 10));
		assertNotNull(updatedResult);
		// first time only copies active components
		assertEquals(1, updatedResult.getContent().size());
		// check effective time, module id and reference set id
		for (ReferenceSetMember member : updatedResult.getContent()) {
			assertNull(member.getEffectiveTimeI());
			assertEquals("21000210109", member.getModuleId());
			assertEquals("271000210107", member.getRefsetId());
			assertEquals("123456", member.getConceptId());
		}

		updatedResult = referenceSetMemberService.findMembers(snomedctNZ.getBranchPath(), "675173019", PageRequest.of(0, 10));
		assertNotNull(updatedResult);
		// 2 from INT and 1 fom NZ
		assertEquals(3, updatedResult.getContent().size());

		Concept concept = conceptService.find("123456", Config.DEFAULT_LANGUAGE_DIALECTS, snomedctNZ.getBranchPath());
		Set<String> refsetIds = concept.getDescription("675173019").getLangRefsetMembers().keySet();
		assertEquals(3, refsetIds.size());
		assertTrue(refsetIds.contains("271000210107"));
	}


	@Test
	void testGenerateAdditionalLanguageRefsetsWithDeltaOnly() {
		// check extension is upgraded to the dependent release
		MemberSearchRequest enGbSearchRequest = new MemberSearchRequest();
		enGbSearchRequest.referenceSet("900000000000508004");

		// only two for en-gb
		Page<ReferenceSetMember> updatedResult =  referenceSetMemberService.findMembers(snomedctNZ.getBranchPath(), enGbSearchRequest, PageRequest.of(0, 10));
		assertNotNull(updatedResult);
		assertEquals(2, updatedResult.getContent().size());


		// only two language members for description id 675173018
		updatedResult = referenceSetMemberService.findMembers(snomedctNZ.getBranchPath(), "675173018", PageRequest.of(0, 10));
		assertNotNull(updatedResult);
		assertEquals(2, updatedResult.getContent().size());

		// no en-nz components yet
		MemberSearchRequest enNzSearchRequest = new MemberSearchRequest();
		enNzSearchRequest.referenceSet("271000210107");
		updatedResult =  referenceSetMemberService.findMembers(snomedctNZ.getBranchPath(), enNzSearchRequest, PageRequest.of(0, 10));
		assertNotNull(updatedResult);
		assertEquals(0, updatedResult.getContent().size());

		// create a published version on the extension for previous release
		ReferenceSetMember enNzLanguageMember = new ReferenceSetMember(null, 20190931, true,
				"21000210109", "271000210107", "675173018");
		enNzLanguageMember.setAdditionalField(ReferenceSetMember.LanguageFields.ACCEPTABILITY_ID, "900000000000549004");
		enNzLanguageMember.release(new Integer("20190931"));

		enNzLanguageMember = referenceSetMemberService.createMember(snomedctNZ.getBranchPath(), enNzLanguageMember);
		enNzLanguageMember = referenceSetMemberService.findMember(snomedctNZ.getBranchPath(), enNzLanguageMember.getMemberId());
		assertEquals(true, enNzLanguageMember.isActive());

		extensionAdditionalLanguageRefsetUpgradeService.generateAdditionalLanguageRefsetDelta(snomedctNZ, snomedctNZ.getBranchPath(), "900000000000508004",false);

		enNzLanguageMember = referenceSetMemberService.findMember(snomedctNZ.getBranchPath(), enNzLanguageMember.getMemberId());
		// current implementation always takes the latest version from the international release
		assertEquals(false, enNzLanguageMember.isActive());

		// INT en-gb components not changed
		updatedResult = referenceSetMemberService.findMembers(snomedctNZ.getBranchPath(), enGbSearchRequest, PageRequest.of(0, 10));
		assertNotNull(updatedResult);
		assertEquals(2, updatedResult.getContent().size());

		// en-nz components should have two
		updatedResult =  referenceSetMemberService.findMembers(snomedctNZ.getBranchPath(), enNzSearchRequest, PageRequest.of(0, 10));
		assertNotNull(updatedResult);
		assertEquals(2, updatedResult.getContent().size());
		// check effective time, module id and reference set id
		for (ReferenceSetMember member : updatedResult.getContent()) {
			assertNull(member.getEffectiveTimeI());
			assertEquals("21000210109", member.getModuleId());
			assertEquals("271000210107", member.getRefsetId());
			assertEquals("123456", member.getConceptId());
			if (!member.isActive()) {
				assertEquals("675173018", member.getReferencedComponentId());
			} else {
				assertEquals("675173019", member.getReferencedComponentId());
			}
		}

		Concept concept = conceptService.find("123456", Config.DEFAULT_LANGUAGE_DIALECTS, snomedctNZ.getBranchPath());
		Set<String> refsetIds = concept.getDescription("675173019").getLangRefsetMembers().keySet();
		assertEquals(3, refsetIds.size());
		assertTrue(refsetIds.contains("271000210107"));
	}
}
