package org.snomed.snowstorm.core.data.services;

import io.kaicode.elasticvc.api.BranchService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.TestConfig;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.data.services.pojo.MemberSearchRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.Set;

import static org.junit.Assert.*;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = TestConfig.class)
public class InactivationUpgradeServiceTest extends AbstractTest {
	@Autowired
	private CodeSystemService codeSystemService;

	@Autowired
	private ConceptService conceptService;

	@Autowired
	private BranchService branchService;

	@Autowired
	private ReferenceSetMemberService referenceSetMemberService;

	@Autowired
	private InactivationUpgradeService inactivationUpgradeService;

	private ServiceTestUtil testUtil;

	private final CodeSystem SNOMEDCT = new CodeSystem("SNOMEDCT", "MAIN");
	private final CodeSystem SNOMEDCT_DK = new CodeSystem("SNOMEDCT-DK", "MAIN/SNOMEDCT-DK");

	@Before
	public void setUp() {
		codeSystemService.createCodeSystem(SNOMEDCT);
		codeSystemService.createCodeSystem(SNOMEDCT_DK);
		testUtil = new ServiceTestUtil(conceptService);
	}

	@Test
	public void testFindAndUpdateInactivationIndicators() throws Exception {
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

		inactivationUpgradeService.findAndUpdateInactivationIndicators(SNOMEDCT);
		members = referenceSetMemberService.findMembers(MAIN, new MemberSearchRequest().referenceSet("900000000000490003"), PageRequest.of(0, 10));
		assertEquals(1, members.getContent().size());

		// verify inactivation refset member
		ReferenceSetMember inactivationMember = members.getContent().get(0);
		assertNotNull(inactivationMember.getMemberId());
		assertNull(inactivationMember.getEffectiveTime());
		assertFalse(inactivationMember.isReleased());
		assertTrue(inactivationMember.isActive());
		assertEquals(publishedDescription.getModuleId(), inactivationMember.getModuleId());
		assertEquals(Concepts.DESCRIPTION_INACTIVATION_INDICATOR_REFERENCE_SET, inactivationMember.getRefsetId());
		assertEquals(publishedDescription.getDescriptionId(), inactivationMember.getReferencedComponentId());
		assertEquals(publishedDescription.getConceptId(), inactivationMember.getConceptId());

		// make sure published description is still active and published and unpublished description is deleted.
		conceptA = conceptService.find(conceptA.getConceptId(), SNOMEDCT.getBranchPath());
		descriptions = conceptA.getDescriptions();
		assertEquals(1, descriptions.size());
		Description remaining = descriptions.iterator().next();
		assertEquals(publishedDescription.getDescriptionId(), remaining.getDescriptionId());
		assertEquals(publishedDescription.getConceptId(), remaining.getConceptId());
		assertTrue(remaining.isActive());
		assertTrue(remaining.isReleased());
	}
}
