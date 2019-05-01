package org.snomed.snowstorm.core.data.services;

import io.kaicode.elasticvc.api.BranchService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.TestConfig;
import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.core.data.domain.Concepts;
import org.snomed.snowstorm.core.data.domain.ReferenceSetMember;
import org.snomed.snowstorm.core.data.domain.Relationship;
import org.snomed.snowstorm.core.data.services.pojo.MemberSearchRequest;
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
		conceptService.deleteAll();
		branchService.create(MAIN);
		conceptService.create(new Concept(Concepts.SNOMEDCT_ROOT), MAIN);
		conceptService.create(new Concept(Concepts.CLINICAL_FINDING)
				.addRelationship(new Relationship(Concepts.ISA, Concepts.SNOMEDCT_ROOT)), MAIN);
		conceptService.create(new Concept(Concepts.REFSET_HISTORICAL_ASSOCIATION)
				.addRelationship(new Relationship(Concepts.ISA, Concepts.SNOMEDCT_ROOT)), MAIN);
		conceptService.create(new Concept(Concepts.REFSET_POSSIBLY_EQUIVALENT_TO_ASSOCIATION)
				.addRelationship(new Relationship(Concepts.ISA, Concepts.REFSET_HISTORICAL_ASSOCIATION)), MAIN);
	}

	@Test
	public void createFindDeleteMember() {
		assertEquals(0, memberService.findMembers(MAIN, Concepts.CLINICAL_FINDING, PAGE).getTotalElements());

		memberService.createMember(
				MAIN, new ReferenceSetMember(Concepts.CORE_MODULE, Concepts.REFSET_POSSIBLY_EQUIVALENT_TO_ASSOCIATION, Concepts.CLINICAL_FINDING));

		Page<ReferenceSetMember> members = memberService.findMembers(MAIN, Concepts.CLINICAL_FINDING, PAGE);
		assertEquals(1, members.getTotalElements());

		assertEquals(0, memberService.findMembers(MAIN, new MemberSearchRequest().active(true).referenceSet(Concepts.REFSET_HISTORICAL_ASSOCIATION), PAGE).getTotalElements());
		assertEquals(1, memberService.findMembers(MAIN, new MemberSearchRequest().active(true).referenceSet("<<" + Concepts.REFSET_HISTORICAL_ASSOCIATION), PAGE).getTotalElements());

		memberService.deleteMember(MAIN, members.getContent().get(0).getMemberId());

		assertEquals(0, memberService.findMembers(MAIN, Concepts.CLINICAL_FINDING, PAGE).getTotalElements());
	}

	@Test
	public void findMemberByOwlExpressionConceptId() {
		memberService.createMember(MAIN,
				new ReferenceSetMember(Concepts.CORE_MODULE, Concepts.OWL_AXIOM_REFERENCE_SET, "90253000")
						.setAdditionalField(ReferenceSetMember.OwlExpressionFields.OWL_EXPRESSION,
								"SubClassOf(" +
										":90253000 " +
										"ObjectIntersectionOf(:20484008 " +
											"ObjectSomeValuesFrom(:609096000 " +
												"ObjectIntersectionOf(ObjectSomeValuesFrom(:116676008 :68245003) ObjectSomeValuesFrom(:363698007 :280369009)))))"));

		assertEquals("Number not in axiom.", 0, findOwlMembers("999").getTotalElements());
		assertEquals("Number in left hand part of axiom.", 1, findOwlMembers("90253000").getTotalElements());
		assertEquals("Number in right hand part of axiom.", 1, findOwlMembers("116676008").getTotalElements());
		assertEquals("Partial number should not match.", 0, findOwlMembers("11667600").getTotalElements());
		assertEquals("Partial number should not match.", 0, findOwlMembers("9096000").getTotalElements());
	}

	@Test
	public void findMemberByOwlExpressionGCI() {
		memberService.createMember(MAIN,
				new ReferenceSetMember(Concepts.CORE_MODULE, Concepts.OWL_AXIOM_REFERENCE_SET, "90253000")
						.setAdditionalField(ReferenceSetMember.OwlExpressionFields.OWL_EXPRESSION,
								// Normal class axiom
								"SubClassOf(" +
										// Named concept on left hand side
										":90253000 " +
										// Expression on the right hand side
										"ObjectIntersectionOf(:20484008 " +
											"ObjectSomeValuesFrom(:609096000 " +
												"ObjectIntersectionOf(ObjectSomeValuesFrom(:116676008 :68245003) ObjectSomeValuesFrom(:363698007 :280369009)))))"));

		memberService.createMember(MAIN,
				new ReferenceSetMember(Concepts.CORE_MODULE, Concepts.OWL_AXIOM_REFERENCE_SET, "703264005")
						.setAdditionalField(ReferenceSetMember.OwlExpressionFields.OWL_EXPRESSION,
								// GCI axiom
								"SubClassOf(" +
										// Expression on the left hand side
										"ObjectIntersectionOf(:64859006 " +
											"ObjectSomeValuesFrom(:609096000 " +
												"ObjectSomeValuesFrom(:363698007 :272673000)) ObjectSomeValuesFrom(:609096000 ObjectSomeValuesFrom(:42752001 :64572001))) " +
										// Named concept on right hand side
										":703264005)"));

		assertEquals("Match normal class axiom.", 1, findOwlMembers("116676008", null).getTotalElements());
		assertEquals("Should not match normal class axiom.", 0, findOwlMembers("116676008", true).getTotalElements());
		assertEquals("Match normal class axiom.", 1, findOwlMembers("116676008", false).getTotalElements());

		assertEquals("Match GCI axiom.", 1, findOwlMembers("703264005", null).getTotalElements());
		assertEquals("Match GCI axiom.", 1, findOwlMembers("703264005", true).getTotalElements());
		assertEquals("Should not match GCI axiom.", 0, findOwlMembers("703264005", false).getTotalElements());
	}

	public Page<ReferenceSetMember> findOwlMembers(String owlExpressionConceptId) {
		return findOwlMembers(owlExpressionConceptId, null);
	}
	public Page<ReferenceSetMember> findOwlMembers(String owlExpressionConceptId, Boolean owlExpressionGCI) {
		return memberService.findMembers(
				MAIN,
				new MemberSearchRequest()
						.owlExpressionConceptId(owlExpressionConceptId)
						.owlExpressionGCI(owlExpressionGCI),
				PAGE);
	}

	@After
	public void tearDown() {
		conceptService.deleteAll();
	}

}
