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
import org.snomed.snowstorm.core.data.domain.*;
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

import static org.junit.jupiter.api.Assertions.*;

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

		ReferenceSetMember enGbLanguageMemberLatest = new ReferenceSetMember(UUID.randomUUID().toString(), null, true,
				"900000000000207008", "900000000000508004", "675173019");
		enGbLanguageMemberLatest.setAdditionalField(ReferenceSetMember.LanguageFields.ACCEPTABILITY_ID, "900000000000548007");

		ReferenceSetMember enUsLanguageMemberLatest = new ReferenceSetMember(UUID.randomUUID().toString(), null, true,
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
		List<Map<String, String>> requiredLanguageRefsets = objectMapper.readValue("""
                [{
                        "default": "false",
                        "en": "900000000000508004",
                        "readOnly": "true",
                        "dialectName": "en-gb"
                      },
                      {
                        "default": "true",
                        "en": "271000210107",
                        "dialectName": "en-nz"
                      },
                      {
                        "default": "false",
                        "mi": "291000210106"
                      },
                      {
                        "default": "false",
                        "en": "900000000000509007",
                        "dialectName": "en-us"
                      }
                    ]""", listTypeReference);
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
		Concept conceptA = constructTestConcept("100002", "71183010");
		conceptService.create(conceptA, MAIN);
		codeSystemService.createVersion(snomedct, 20200228, "international release 20200228");

		Concept conceptB = constructTestConcept("100003", "75183010");
		conceptService.create(conceptB, MAIN);
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
		List<ReferenceSetMember> published = updatedResult.get().filter(SnomedComponent::isReleased).collect(Collectors.toList());
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

	@Test
	void testGenerateAdditionalLanguageRefsetsWithDeltaOnly_shouldNotUpdateOrAddNewAdditionalRefsetMember_ifAlreadyExistAPreferredTermInExtension() throws ServiceException {
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
		Concept conceptA = constructTestConcept("100002", "288524019");

		// Add TEXT Definition to concept A
		Description textDefinitionDesc = constructTestTextDefinitionDescription("282524019");
		conceptA.addDescription(textDefinitionDesc);

		conceptService.create(conceptA, MAIN);
		codeSystemService.createVersion(snomedct, 20200228, "international release 20200228");

		Concept conceptB = constructTestConcept("100003", "268524019");
		conceptService.create(conceptB, MAIN);
		codeSystemService.createVersion(snomedct, 20200331, "international release 20200331");

		// roll up upgrade extension
		String jobId = codeSystemUpgradeService.createJob(snomedctNZ.getShortName(), 20200331);
		codeSystemUpgradeService.upgrade(jobId, snomedctNZ, 20200331, false);

		snomedctNZ = codeSystemService.find(snomedctNZ.getShortName());

		// Find INT concept and add an extension PREFERRED term
		Concept concept = conceptService.find("100002", snomedctNZ.getBranchPath());
		ReferenceSetMember extensionLanguageMember = new ReferenceSetMember(UUID.randomUUID().toString(),null, true,
				"21000210109", "271000210107", "2156578010");
		extensionLanguageMember.setAdditionalField(ReferenceSetMember.LanguageFields.ACCEPTABILITY_ID, "900000000000548007");
		Description description = new Description("588524019", "58852401 testing");
		description.addLanguageRefsetMember(extensionLanguageMember);
		concept.addDescription(description);
		conceptService.update(concept, snomedctNZ.getBranchPath());

		// This step will not copy the EN-GB from concept 100002 as there is a existing extension PREFERRED term
		extensionAdditionalLanguageRefsetUpgradeService.generateAdditionalLanguageRefsetDelta(snomedctNZ, snomedctNZ.getBranchPath(), "900000000000508004",false);
		updatedResult =  referenceSetMemberService.findMembers(snomedctNZ.getBranchPath(), nzEnSearchRequest, PageRequest.of(0, 10));
		assertNotNull(updatedResult);
		assertEquals(4, updatedResult.getContent().size());
		// Only 1 is released
		List<ReferenceSetMember> published = updatedResult.get().filter(referenceSetMember -> referenceSetMember.isReleased()).collect(Collectors.toList());
		assertEquals(1, published.size());
		assertEquals(nzExistingMemberId, published.get(0).getMemberId());

		assertEquals(1, updatedResult.get().filter(referenceSetMember -> referenceSetMember.getReferencedComponentId().equals("268524019")).collect(Collectors.toList()).size());
		assertEquals(1, updatedResult.get().filter(referenceSetMember -> referenceSetMember.getReferencedComponentId().equals("588524019")).collect(Collectors.toList()).size());
		assertEquals(1, updatedResult.get().filter(referenceSetMember -> referenceSetMember.getReferencedComponentId().equals("282524019")).collect(Collectors.toList()).size());
		assertEquals(0, updatedResult.get().filter(referenceSetMember -> referenceSetMember.getReferencedComponentId().equals("288524019")).collect(Collectors.toList()).size());
	}


	@Test
	void testUpgradeHavingBothActiveAndInactivePublishedComponentsForTheSameDescriptionId() throws ServiceException {
		// Create a new description with language refset member with 900000000000549004 for nz-en
		ReferenceSetMember extensionLanguageMember = new ReferenceSetMember(UUID.randomUUID().toString(),null, true,
				"21000210109", "271000210107", "675173019");
		extensionLanguageMember.setAdditionalField(ReferenceSetMember.LanguageFields.ACCEPTABILITY_ID, "900000000000549004");
		referenceSetMemberService.createMember(snomedctNZ.getBranchPath(), extensionLanguageMember);

		// Version NZ extension
		codeSystemService.createVersion(snomedctNZ, 20190630, "NZ release");

		// Inactive previous member and create a new member
		referenceSetMemberService.findMembers(snomedctNZ.getBranchPath(), "675173019", PageRequest.of(0, 10)).forEach(member -> {
			member.setActive(false);
			referenceSetMemberService.updateMember(snomedctNZ.getBranchPath(), member);
		});

		// Version NZ extension
		codeSystemService.createVersion(snomedctNZ, 20190731, "NZ release");

		// Create a new nz-en language refset member with a different UUID
		extensionLanguageMember = new ReferenceSetMember(UUID.randomUUID().toString(),null, true,
				"21000210109", "271000210107", "675173019");
		extensionLanguageMember.setAdditionalField(ReferenceSetMember.LanguageFields.ACCEPTABILITY_ID, "900000000000549004");
		referenceSetMemberService.createMember(snomedctNZ.getBranchPath(), extensionLanguageMember);

		conceptService.find("100001", snomedctNZ.getBranchPath()).getDescriptions().forEach(desc -> {
			if (desc.getDescriptionId().equals("675173019")) {
				assertEquals(3, desc.getLangRefsetMembersMap().values().size());
			}
		});

		// Add new en-gb in international for 2 monthly releases before upgrade NZ extension
		Concept conceptA = constructTestConcept("100002", "71183010");
		conceptService.create(conceptA, MAIN);
		codeSystemService.createVersion(snomedct, 20200228, "international release 20200228");

		Concept conceptB = constructTestConcept("100003", "75183010");
		conceptService.create(conceptB, MAIN);

		// Update acceptability for core refset component from preferred to acceptable
		MemberSearchRequest searchRequest = new MemberSearchRequest();
		searchRequest.referenceSet("900000000000508004");
		searchRequest.referencedComponentId("675173019");
		referenceSetMemberService.findMembers(snomedct.getBranchPath(), searchRequest, PageRequest.of(0, 10)).forEach(member -> {
			member.setAdditionalField(ReferenceSetMember.LanguageFields.ACCEPTABILITY_ID,"900000000000549004");
			referenceSetMemberService.updateMember(snomedct.getBranchPath(), member);
		});
		codeSystemService.createVersion(snomedct, 20200331, "international release 20200331");

		// roll up upgrade extension
		String jobId = codeSystemUpgradeService.createJob(snomedctNZ.getShortName(), 20200331);
		codeSystemUpgradeService.upgrade(jobId, snomedctNZ, 20200331, false);

		snomedctNZ = codeSystemService.find(snomedctNZ.getShortName());
		extensionAdditionalLanguageRefsetUpgradeService.generateAdditionalLanguageRefsetDelta(snomedctNZ, snomedctNZ.getBranchPath(), "900000000000508004",false);

		// Verify results after upgrade
		Concept conceptAfterUpgrade = conceptService.find("100001", Config.DEFAULT_LANGUAGE_DIALECTS, snomedctNZ.getBranchPath());
		final String nzMemberId = extensionLanguageMember.getMemberId();
		conceptAfterUpgrade.getDescriptions().forEach(desc -> {
			if (desc.getDescriptionId().equals("675173019")) {
				assertEquals(4, desc.getLangRefsetMembers().size());
				desc.getLangRefsetMembers().forEach(member -> {
					if (member.getRefsetId().equals("271000210107") && member.isActive()) {
						assertEquals("900000000000549004", member.getAdditionalField(ReferenceSetMember.LanguageFields.ACCEPTABILITY_ID));
						// Check the member id is the same as the one created before upgrade
						assertEquals(nzMemberId, member.getMemberId());
					}
				});
				assertEquals(3, desc.getLangRefsetMembersMap().values().size());
				desc.getAcceptabilityMapFromLangRefsetMembers().forEach((key, value) -> {
					if (key.equals("900000000000508004")) {
						assertEquals(Concepts.ACCEPTABLE_CONSTANT, value);
					} else if (key.equals("271000210107")) {
						assertEquals(Concepts.ACCEPTABLE_CONSTANT, value);
					}
				});
			}
		});
	}


	@Test
	void testUpgradeHavingInactivePublishedAndActiveUnpublishedMembersForTheSameDescriptionId() throws ServiceException {
		// Create a new description with language refset member with 900000000000549004 for nz-en
		ReferenceSetMember extensionLanguageMember = new ReferenceSetMember(UUID.randomUUID().toString(),null, true,
				"21000210109", "271000210107", "675173019");
		extensionLanguageMember.setAdditionalField(ReferenceSetMember.LanguageFields.ACCEPTABILITY_ID, Concepts.ACCEPTABLE);
		referenceSetMemberService.createMember(snomedctNZ.getBranchPath(), extensionLanguageMember);

		// Version NZ extension
		codeSystemService.createVersion(snomedctNZ, 20190630, "NZ release");

		// Inactive previous member and create a new member
		referenceSetMemberService.findMembers(snomedctNZ.getBranchPath(), "675173019", PageRequest.of(0, 10)).forEach(member -> {
			member.setActive(false);
			referenceSetMemberService.updateMember(snomedctNZ.getBranchPath(), member);
		});

		// Version NZ extension
		codeSystemService.createVersion(snomedctNZ, 20190731, "NZ release");

		// Create a new nz-en language refset member with a different UUID
		extensionLanguageMember = new ReferenceSetMember(UUID.randomUUID().toString(),null, true,
				"21000210109", "271000210107", "675173019");
		extensionLanguageMember.setAdditionalField(ReferenceSetMember.LanguageFields.ACCEPTABILITY_ID, Concepts.PREFERRED);

		referenceSetMemberService.createMember(snomedctNZ.getBranchPath(), extensionLanguageMember);

		conceptService.find("100001", snomedctNZ.getBranchPath()).getDescriptions().forEach(desc -> {
			if (desc.getDescriptionId().equals("675173019")) {
				assertEquals(3, desc.getLangRefsetMembersMap().values().size());
			}
		});

		// Add new en-gb in international for 2 monthly releases before upgrade NZ extension
		Concept conceptA = constructTestConcept("100002", "71183010");
		conceptService.create(conceptA, MAIN);
		codeSystemService.createVersion(snomedct, 20200228, "international release 20200228");

		Concept conceptB = constructTestConcept("100003", "75183010");
		conceptService.create(conceptB, MAIN);

		// Update acceptability for core refset component from preferred to acceptable
		MemberSearchRequest searchRequest = new MemberSearchRequest();
		searchRequest.referenceSet("900000000000508004");
		searchRequest.referencedComponentId("675173019");
		referenceSetMemberService.findMembers(snomedct.getBranchPath(), searchRequest, PageRequest.of(0, 10)).forEach(member -> {
			member.setAdditionalField(ReferenceSetMember.LanguageFields.ACCEPTABILITY_ID,"900000000000549004");
			referenceSetMemberService.updateMember(snomedct.getBranchPath(), member);
		});
		codeSystemService.createVersion(snomedct, 20200331, "international release 20200331");

		// roll up upgrade extension
		String jobId = codeSystemUpgradeService.createJob(snomedctNZ.getShortName(), 20200331);
		codeSystemUpgradeService.upgrade(jobId, snomedctNZ, 20200331, false);

		snomedctNZ = codeSystemService.find(snomedctNZ.getShortName());
		extensionAdditionalLanguageRefsetUpgradeService.generateAdditionalLanguageRefsetDelta(snomedctNZ, snomedctNZ.getBranchPath(), "900000000000508004",false);

		// Verify results after upgrade
		Concept conceptAfterUpgrade = conceptService.find("100001", Config.DEFAULT_LANGUAGE_DIALECTS, snomedctNZ.getBranchPath());
		final String nzMemberId = extensionLanguageMember.getMemberId();
		conceptAfterUpgrade.getDescriptions().forEach(desc -> {
			if (desc.getDescriptionId().equals("675173019")) {
				assertEquals(4, desc.getLangRefsetMembers().size());
				desc.getLangRefsetMembers().forEach(member -> {
					if (member.getRefsetId().equals("271000210107") && member.isActive()) {
						assertEquals("900000000000549004", member.getAdditionalField(ReferenceSetMember.LanguageFields.ACCEPTABILITY_ID));
						// Check the member id is the same as the one created before upgrade
						assertEquals(nzMemberId, member.getMemberId());
					}
				});
				assertEquals(3, desc.getLangRefsetMembersMap().values().size());
				desc.getAcceptabilityMapFromLangRefsetMembers().forEach((key, value) -> {
					if (key.equals("900000000000508004")) {
						assertEquals(Concepts.ACCEPTABLE_CONSTANT, value);
					} else if (key.equals("271000210107")) {
						assertEquals(Concepts.ACCEPTABLE_CONSTANT, value);
					}
				});
			}
		});
	}
	@NotNull
	private static Description constructTestTextDefinitionDescription(String descriptionId) {
		ReferenceSetMember enGbLanguageMember = new ReferenceSetMember(null,null, true,
				"900000000000207008", "900000000000508004", descriptionId);
		enGbLanguageMember.setAdditionalField(ReferenceSetMember.LanguageFields.ACCEPTABILITY_ID, "900000000000548007");
		ReferenceSetMember enUsLanguageMember = new ReferenceSetMember(null, null, true,
				"900000000000207008","900000000000509007", descriptionId);
		enUsLanguageMember.setAdditionalField(ReferenceSetMember.LanguageFields.ACCEPTABILITY_ID, "900000000000549004");
		Description description = new Description("282524019", descriptionId + " testing");
		description.setTypeId(Concepts.TEXT_DEFINITION);
		description.addLanguageRefsetMember(enGbLanguageMember);
		description.addLanguageRefsetMember(enUsLanguageMember);
		return description;
	}
}
