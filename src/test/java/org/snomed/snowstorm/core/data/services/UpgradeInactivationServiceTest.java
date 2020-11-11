package org.snomed.snowstorm.core.data.services;

import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.domain.Commit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.TestConfig;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.data.services.pojo.MemberSearchRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = TestConfig.class)
class UpgradeInactivationServiceTest extends AbstractTest {
	@Autowired
	private CodeSystemService codeSystemService;

	@Autowired
	private ConceptService conceptService;

	@Autowired
	private BranchService branchService;

	@Autowired
	private ReferenceSetMemberService referenceSetMemberService;

	@Autowired
	private UpgradeInactivationService upgradeInactivationService;

	@Autowired
	private ConceptUpdateHelper conceptUpdateHelper;

	private ServiceTestUtil testUtil;

	private final CodeSystem SNOMEDCT = new CodeSystem("SNOMEDCT", "MAIN");

	@BeforeEach
	void setUp() {
		codeSystemService.createCodeSystem(SNOMEDCT);
		testUtil = new ServiceTestUtil(conceptService);
	}

	@Test
	void testFindAndUpdateInactivationIndicators() throws Exception {
		// add content
		Concept conceptA = testUtil.createConceptWithPathIdAndTermWithLang("MAIN", "100000", "Inactivation testing", "en");
		// version
		codeSystemService.createVersion(SNOMEDCT, Integer.valueOf("20200131"), "20200131 International Release");

		// inactivate concept
		conceptA = conceptService.find(conceptA.getConceptId(), MAIN);
		Set<Description> descriptions = conceptA.getDescriptions();
		assertNotNull(descriptions);
		assertEquals(1, descriptions.size());
		Description publishedDescription = descriptions.iterator().next();

		Description unpublished = new Description("new term");
		conceptA.addDescription(unpublished);
		conceptA.setActive(false);
		conceptService.update(conceptA, MAIN);

		conceptA = conceptService.find(conceptA.getConceptId(), MAIN);
		descriptions = conceptA.getDescriptions();
		assertNotNull(descriptions);
		assertEquals(2, descriptions.size());

		Page<ReferenceSetMember> members = referenceSetMemberService.findMembers(MAIN, new MemberSearchRequest().referenceSet("900000000000490003"), PageRequest.of(0, 10));
		assertNotNull(members);
		assertEquals(2, members.getContent().size());

		// delete description inactivation indicator members for testing
		referenceSetMemberService.deleteMember(MAIN, members.getContent().get(0).getMemberId());
		referenceSetMemberService.deleteMember(MAIN, members.getContent().get(1).getMemberId());

		members = referenceSetMemberService.findMembers(MAIN, new MemberSearchRequest().referenceSet("900000000000490003")
				.referencedComponentId(publishedDescription.getDescriptionId()), PageRequest.of(0, 10));
		assertEquals(0, members.getContent().size());

		upgradeInactivationService.findAndUpdateDescriptionsInactivation(SNOMEDCT);
		members = referenceSetMemberService.findMembers(MAIN, new MemberSearchRequest().referenceSet("900000000000490003"), PageRequest.of(0, 10));
		assertEquals(2, members.getContent().size());

		// verify inactivation refset member
		ReferenceSetMember inactivationMember = members.getContent().get(0);
		assertNotNull(inactivationMember.getMemberId());
		assertNull(inactivationMember.getEffectiveTime());
		assertFalse(inactivationMember.isReleased());
		assertTrue(inactivationMember.isActive());
		assertEquals(publishedDescription.getModuleId(), inactivationMember.getModuleId());
		assertEquals(Concepts.DESCRIPTION_INACTIVATION_INDICATOR_REFERENCE_SET, inactivationMember.getRefsetId());
		assertEquals(Concepts.CONCEPT_NON_CURRENT, inactivationMember.getAdditionalField("valueId"));
		assertEquals(publishedDescription.getDescriptionId(), inactivationMember.getReferencedComponentId());
		assertEquals(publishedDescription.getConceptId(), inactivationMember.getConceptId());

		// make sure published description is still active and published and unpublished description is deleted.
		conceptA = conceptService.find(conceptA.getConceptId(), SNOMEDCT.getBranchPath());
		List<Description> descriptionList = new ArrayList<>(conceptA.getDescriptions());
		assertEquals(2, descriptionList.size());
		assertTrue(descriptionList.get(0).isActive());
		assertTrue(descriptionList.get(1).isActive());
	}

	@Test
	void testFindAndUpdateLanguageRefsetsForInactiveDescriptions() throws Exception {
		// add concept
		Concept conceptA = testUtil.createConceptWithPathIdAndTermWithLang(MAIN, "100000", "Inactivation testing", "en");
		Set<Description> descriptions = conceptA.getDescriptions();
		for (Description description : descriptions) {
			description.addLanguageRefsetMember(Concepts.US_EN_LANG_REFSET, Concepts.PREFERRED);
		}
		conceptService.update(conceptA, MAIN);

		// version
		codeSystemService.createVersion(SNOMEDCT, Integer.valueOf("20200731"), "20200731 International Release");
		// inactivate descriptions
		conceptA = conceptService.find(conceptA.getConceptId(), MAIN);
		descriptions = conceptA.getDescriptions();
		assertNotNull(descriptions);
		assertEquals(1, descriptions.size());
		Description published = descriptions.iterator().next();
		MemberSearchRequest searchRequest = new MemberSearchRequest().referencedComponentId(published.getDescriptionId());
		Page<ReferenceSetMember> members = referenceSetMemberService.findMembers(MAIN, searchRequest, PageRequest.of(0, 2));
		assertNotNull(members);
		assertEquals(1, members.getContent().size());
		String usPreferredMemberId = members.getContent().iterator().next().getMemberId();

		// add a new en-gb language refset member
		published.addLanguageRefsetMember(Concepts.GB_EN_LANG_REFSET, Concepts.ACCEPTABLE);
		conceptService.update(conceptA, MAIN);

		// there are should be 2 language refset members
		members = referenceSetMemberService.findMembers(MAIN, searchRequest, PageRequest.of(0, 3));
		assertNotNull(members);
		assertEquals(2, members.getContent().size());

		// inactivate description
		conceptA = conceptService.find(conceptA.getConceptId(), MAIN);
		published = conceptA.getDescriptions().iterator().next();
		try (Commit commit = branchService.openCommit(MAIN)) {
			published.setActive(false);
			published.markChanged();
			conceptUpdateHelper.doSaveBatchComponents(Arrays.asList(published), Description.class, commit);
			commit.markSuccessful();
		}

		// check description is inactive
		conceptA = conceptService.find(conceptA.getConceptId(), MAIN);
		assertFalse(conceptA.getDescriptions().iterator().next().isActive());

		// there are should be 2 language refset members
		members = referenceSetMemberService.findMembers(MAIN, searchRequest, PageRequest.of(0, 3));
		assertNotNull(members);
		assertEquals(2, members.getContent().size());
		for (ReferenceSetMember member : members) {
			if (member.isReleased()) {
				assertEquals(usPreferredMemberId, member.getMemberId());
			}
			assertTrue(member.isActive());
		}

		// test upgrade service
		upgradeInactivationService.findAndUpdateLanguageRefsets(SNOMEDCT);

		// check one language refset is inactivated
		members = referenceSetMemberService.findMembers(MAIN, searchRequest, PageRequest.of(0, 2));
		assertNotNull(members);
		assertEquals(1, members.getContent().size());
		for (ReferenceSetMember member : members) {
			assertTrue(member.isReleased());
			assertFalse(member.isActive());
			assertEquals(usPreferredMemberId, member.getMemberId());
		}
	}

}
