package org.snomed.snowstorm.extension;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kaicode.elasticvc.api.BranchService;
import org.jetbrains.annotations.NotNull;
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
import org.snomed.snowstorm.core.data.services.*;
import org.snomed.snowstorm.core.data.services.pojo.MemberSearchRequest;
import org.snomed.snowstorm.core.rf2.rf2import.ImportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;
import java.util.stream.Collectors;

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

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private CodeSystemVersionService codeSystemVersionService;

	@Autowired
	private CodeSystemUpgradeService codeSystemUpgradeService;

	private CodeSystem snomedct;

	private CodeSystem snomedctNZ;

	@BeforeEach
	void setUp() throws Exception {
		snomedct = new CodeSystem("SNOMEDCT", "MAIN");
		codeSystemService.createCodeSystem(snomedct);

		Concept concept = constructTestConcept("100001", "675173018");

		conceptService.create(concept, snomedct.getBranchPath());
		codeSystemService.createVersion(snomedct, 20190731, "2019-07-31 release");

		ReferenceSetMember enGbLanguageMemberLatest = new ReferenceSetMember(null, null, true,
				"900000000000207008", "900000000000508004", "675173019");
		enGbLanguageMemberLatest.setAdditionalField(ReferenceSetMember.LanguageFields.ACCEPTABILITY_ID, "900000000000548007");

		ReferenceSetMember enUsLanguageMemberLatest = new ReferenceSetMember(null, null, true,
				"900000000000207008", "900000000000509007", "675173019");
		enUsLanguageMemberLatest.setAdditionalField(ReferenceSetMember.LanguageFields.ACCEPTABILITY_ID, "900000000000549004");

		Description latestDescription = new Description("675173019", "new term testing");
		latestDescription.addLanguageRefsetMember(enGbLanguageMemberLatest);
		latestDescription.addLanguageRefsetMember(enUsLanguageMemberLatest);
		concept = conceptService.find("100001", snomedct.getBranchPath());
		concept.addDescription(latestDescription);
		concept = conceptService.update(concept, snomedct.getBranchPath());

		// this is for testing complete copy as only active ones are copied.
		Description existing = concept.getDescription("675173018");
		ReferenceSetMember existingGbMember = existing.getLangRefsetMembersFirstValuesMap().get("900000000000508004");
		existingGbMember.setActive(false);
		referenceSetMemberService.updateMember("MAIN", existingGbMember);

		assertEquals(1, concept.getDescription("675173018").getAcceptabilityMap().keySet().size());

		MemberSearchRequest searchRequest = new MemberSearchRequest();
		searchRequest.referenceSet("900000000000508004");

		Page<ReferenceSetMember> updatedResult =  referenceSetMemberService.findMembers(snomedct.getBranchPath(), searchRequest, PageRequest.of(0, 10));
		assertNotNull(updatedResult);
		assertEquals(2, updatedResult.getContent().size());

		codeSystemService.createVersion(snomedct, 20200131, "2020-01-31 release");

		snomedctNZ = new CodeSystem("SNOMEDCT-NZ", "MAIN/SNOMEDCT-NZ");
		snomedctNZ.setDependantVersionEffectiveTime(20200131);
		codeSystemService.createCodeSystem(snomedctNZ);
		Map<String, Object> metaData = new HashMap<>();
		metaData.put(BranchMetadataKeys.DEPENDENCY_PACKAGE, "International_Release.zip");
		metaData.put(BranchMetadataKeys.PREVIOUS_PACKAGE, "NZExtension_Release.zip");
		metaData.put("defaultModuleId", "21000210109");
		metaData.put("defaultNamespace", "1000210");
		metaData.put("shortname", "NZ");
		final TypeReference<List<Map<String, String>>> listTypeReference = new TypeReference<>() {};
		List<Map<String, String>> requiredLanguageRefsets = objectMapper.readValue("[{\n" +
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
				"    ]", listTypeReference);
		metaData.put("requiredLanguageRefsets", requiredLanguageRefsets);

		branchService.updateMetadata("MAIN/SNOMEDCT-NZ", metaData);
		List<CodeSystem> codeSystems = codeSystemService.findAll();
		codeSystemVersionService.initDependantVersionCache(codeSystems);
	}

	@NotNull
	private static Concept constructTestConcept(String conceptId, String descriptionId) {
		ReferenceSetMember enGbLanguageMember = new ReferenceSetMember(null,null, true,
				"900000000000207008", "900000000000508004", descriptionId);
		enGbLanguageMember.setAdditionalField(ReferenceSetMember.LanguageFields.ACCEPTABILITY_ID, "900000000000548007");
		ReferenceSetMember enUsLanguageMember = new ReferenceSetMember(null, null, true,
				"900000000000207008","900000000000509007", descriptionId);
		enUsLanguageMember.setAdditionalField(ReferenceSetMember.LanguageFields.ACCEPTABILITY_ID, "900000000000549004");
		Concept concept = new Concept(conceptId);
		Description description = new Description(descriptionId, descriptionId + " testing");
		description.addLanguageRefsetMember(enGbLanguageMember);
		description.addLanguageRefsetMember(enUsLanguageMember);
		concept.addDescription(description);
		return concept;
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
			assertEquals("100001", member.getConceptId());
		}

		updatedResult = referenceSetMemberService.findMembers(snomedctNZ.getBranchPath(), "675173019", PageRequest.of(0, 10));
		assertNotNull(updatedResult);
		// 2 from INT and 1 fom NZ
		assertEquals(3, updatedResult.getContent().size());

		Concept concept = conceptService.find("100001", Config.DEFAULT_LANGUAGE_DIALECTS, snomedctNZ.getBranchPath());
		Set<String> refsetIds = concept.getDescription("675173019").getLangRefsetMembersMap().keySet();
		assertEquals(3, refsetIds.size());
		assertTrue(refsetIds.contains("271000210107"));
	}


	@Test
	void testGenerateAdditionalLanguageRefsetsWithDeltaOnly() throws ServiceException {
		// Complete copy for the first time
		extensionAdditionalLanguageRefsetUpgradeService.generateAdditionalLanguageRefsetDelta(snomedctNZ, snomedctNZ.getBranchPath(), "900000000000508004", true);
		// search for nz-en
		MemberSearchRequest nzEnSearchRequest = new MemberSearchRequest();
		nzEnSearchRequest.referenceSet("271000210107");
		Page<ReferenceSetMember> updatedResult =  referenceSetMemberService.findMembers(snomedctNZ.getBranchPath(), nzEnSearchRequest, PageRequest.of(0, 10));
		assertNotNull(updatedResult);
		assertEquals(1, updatedResult.getContent().size());
		String nzExistingMemberId = updatedResult.getContent().get(0).getMemberId();
		// Version NZ extension
		codeSystemService.createVersion(snomedctNZ, 20200331, "NZ release");
		// Add new en-gb in international for 2 monthly releases
		conceptService.create(constructTestConcept("100002", "675173020"), MAIN);
		codeSystemService.createVersion(snomedct, 20200228, "international release 20200228");

		conceptService.create(constructTestConcept("100003", "675173021"), MAIN);
		codeSystemService.createVersion(snomedct, 20200331, "international release 20200331");

		// roll up upgrade extension
		String jobId = codeSystemUpgradeService.createJob(snomedctNZ.getShortName(), 20200331);
		codeSystemUpgradeService.upgrade(jobId, snomedctNZ, 20200331, false);

		snomedctNZ = codeSystemService.find(snomedctNZ.getShortName());
		extensionAdditionalLanguageRefsetUpgradeService.generateAdditionalLanguageRefsetDelta(snomedctNZ, snomedctNZ.getBranchPath(), "900000000000508004",false);
		updatedResult =  referenceSetMemberService.findMembers(snomedctNZ.getBranchPath(), nzEnSearchRequest, PageRequest.of(0, 10));
		assertNotNull(updatedResult);
		assertEquals(3, updatedResult.getContent().size());
		// Only 1 is released
		List<ReferenceSetMember> published = updatedResult.get().filter(referenceSetMember -> referenceSetMember.isReleased()).collect(Collectors.toList());
		assertEquals(1, published.size());
		assertEquals(nzExistingMemberId, published.get(0).getMemberId());

		for (ReferenceSetMember member : updatedResult.getContent()) {
			assertEquals("21000210109", member.getModuleId());
			assertEquals("271000210107", member.getRefsetId());

			if (member.getMemberId().equals(nzExistingMemberId)) {
				assertEquals("20200331", member.getEffectiveTime());
			} else {
				assertNull(member.getEffectiveTimeI());
			}
		}
	}
}
