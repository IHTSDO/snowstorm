package org.snomed.snowstorm.core.data.services;

import io.kaicode.elasticvc.api.BranchService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.TestConfig;
import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.core.data.domain.Concepts;
import org.snomed.snowstorm.core.data.domain.ReferenceSetMember;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import static org.junit.Assert.assertEquals;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = TestConfig.class)
public class ReferenceSetMemberServiceTest extends AbstractTest {

	@Autowired
	private ReferenceSetMemberService memberService;

	@Autowired
	private ConceptService conceptService;

	@Autowired
	private BranchService branchService;

	private static final String MAIN = "MAIN";
	private static final PageRequest PAGE = PageRequest.of(0, 10);

	@Before
	public void setup() throws ServiceException {
		branchService.create(MAIN);
		conceptService.create(new Concept(Concepts.CLINICAL_FINDING), MAIN);
	}

	@Test
	public void createFindDeleteMember() throws Exception {
		assertEquals(0, memberService.findMembers(MAIN, Concepts.CLINICAL_FINDING, PAGE).getTotalElements());

		memberService.createMember(
				MAIN, new ReferenceSetMember<>(Concepts.CORE_MODULE, Concepts.CONCEPT_INACTIVATION_INDICATOR_REFERENCE_SET, Concepts.CLINICAL_FINDING));

		Page<ReferenceSetMember> members = memberService.findMembers(MAIN, Concepts.CLINICAL_FINDING, PAGE);
		assertEquals(1, members.getTotalElements());

		memberService.deleteMember(MAIN, members.getContent().get(0).getMemberId());

		assertEquals(0, memberService.findMembers(MAIN, Concepts.CLINICAL_FINDING, PAGE).getTotalElements());
	}

}
