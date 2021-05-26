package org.snomed.snowstorm.mrcm;

import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.domain.Branch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.data.services.ConceptService;
import org.snomed.snowstorm.core.data.services.ReferenceSetMemberService;
import org.snomed.snowstorm.core.data.services.ServiceTestUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

@Testcontainers
@ExtendWith(SpringExtension.class)
class MRCMUpdateServiceTest extends AbstractTest {

	@Autowired
	private BranchService branchService;

	@Autowired
	private ReferenceSetMemberService memberService;

	@Autowired
	private ConceptService conceptService;

	private ServiceTestUtil testUtil;

	@BeforeEach
	void setUp() {
		testUtil = new ServiceTestUtil(conceptService);
	}

	@Test
	void testUpdatingMRCMRulesAndTemplates() throws Exception {

		Branch branch = branchService.create("MAIN/MRCM");
		testUtil.createConceptWithPathIdAndTerm(branch.getPath(),"255234002", "After");
		testUtil.createConceptWithPathIdAndTerm(branch.getPath(),"272379006", "Event (event)");
		testUtil.createConceptWithPathIdAndTerm(branch.getPath(),"404684003", "Clinical finding (finding)");

		ReferenceSetMember eventDomain = new ReferenceSetMember(null, null,true,
				Concepts.CORE_MODULE, Concepts.REFSET_MRCM_DOMAIN_INTERNATIONAL,"272379006")
				.setAdditionalField("domainConstraint", "<< 272379006 |Event (event)|")
				.setAdditionalField("parentDomain", null)
				.setAdditionalField("proximalPrimitiveConstraint", "<< 272379006 |Event (event)|")
				.setAdditionalField("proximalPrimitiveRefinement", null)
				.setAdditionalField("guideURL", "");

		ReferenceSetMember clinicalFindingDomain = new ReferenceSetMember(null, 20200309, true,
				Concepts.CORE_MODULE, Concepts.REFSET_MRCM_DOMAIN_INTERNATIONAL,"404684003")
				.setAdditionalField("domainConstraint", "<< 404684003 |Clinical finding (finding)|")
				.setAdditionalField("parentDomain", null)
				.setAdditionalField("proximalPrimitiveConstraint", "<< 404684003 |Clinical finding (finding)|")
				.setAdditionalField("proximalPrimitiveRefinement", null)
				.setAdditionalField("domainTemplateForPrecoordination", "")
				.setAdditionalField("domainTemplateForPostcoordination", null)
				.setAdditionalField("guideURL", "");

		ReferenceSetMember event = new ReferenceSetMember(null, null,true,
				Concepts.CORE_MODULE, Concepts.REFSET_MRCM_ATTRIBUTE_DOMAIN_INTERNATIONAL,"255234002")
				.setAdditionalField("domainId", "272379006")
				.setAdditionalField("grouped", "1")
				.setAdditionalField("attributeCardinality", "0..*")
				.setAdditionalField("attributeInGroupCardinality", "0..1")
				.setAdditionalField("ruleStrengthId", "723597001")
				.setAdditionalField("contentTypeId", "723596005");

		ReferenceSetMember clinicFinding = new ReferenceSetMember(null, null,true,
				Concepts.CORE_MODULE, Concepts.REFSET_MRCM_ATTRIBUTE_DOMAIN_INTERNATIONAL,"255234002")
				.setAdditionalField("domainId", "404684003")
				.setAdditionalField("attributeCardinality", "0..1")
				.setAdditionalField("attributeInGroupCardinality", "0..0")
				.setAdditionalField("ruleStrengthId", "723597001")
				.setAdditionalField("contentTypeId", "723596005");

		ReferenceSetMember range = new ReferenceSetMember(null, null,true,
				Concepts.CORE_MODULE, Concepts.REFSET_MRCM_ATTRIBUTE_RANGE_INTERNATIONAL,"255234002")
				.setAdditionalField("rangeConstraint", "<< 404684003 |Clinical finding (finding)| OR << 71388002 |Procedure (procedure)| OR << 272379006 |Event (event)|")
				.setAdditionalField("attributeRule", null)
				.setAdditionalField("ruleStrengthId", "723597001")
				.setAdditionalField("contentTypeId", "723596005");

		Set<ReferenceSetMember> mrcmMembers = new HashSet<>();
		mrcmMembers.add(eventDomain);
		mrcmMembers.add(clinicalFindingDomain);
		mrcmMembers.add(event);
		mrcmMembers.add(clinicFinding);
		mrcmMembers.add(range);
		memberService.createMembers(branch.getPath(), mrcmMembers);

		// verify attribute range
		range = memberService.findMember(branch.getPath(), range.getMemberId());
		assertNotNull(range);

		assertEquals("<< 272379006 |Event (event)| OR << 404684003 |Clinical finding (finding)| OR << 71388002 |Procedure (procedure)|",
				range.getAdditionalFields().get("rangeConstraint"));

		String expected = "(<< 404684003 |Clinical finding (finding)|: [0..1] 255234002 |After| = (<< 272379006 |Event (event)| OR << 404684003 |Clinical finding (finding)| OR << 71388002 |Procedure (procedure)|))" +
				" OR (<< 272379006 |Event (event)|: [0..*] { [0..1] 255234002 |After| = (<< 272379006 |Event (event)| OR << 404684003 |Clinical finding (finding)| OR << 71388002 |Procedure (procedure)|) })";
		assertEquals(expected, range.getAdditionalField("attributeRule"));

		// verify domain templates
		eventDomain = memberService.findMember(branch.getPath(), eventDomain.getMemberId());
		assertNotNull(eventDomain);
		assertEquals("[[+id(<< 272379006 |Event (event)|)]]: [[0..*]] { [[0..1]] 255234002 |After| = [[+id(<< 272379006 |Event (event)| OR << 404684003 |Clinical finding (finding)| OR << 71388002 |Procedure (procedure)|)]] }",
				eventDomain.getAdditionalField("domainTemplateForPrecoordination"));

		assertEquals("[[+scg(<< 272379006 |Event (event)|)]]: [[0..*]] { [[0..1]] 255234002 |After| = [[+scg(<< 272379006 |Event (event)| OR << 404684003 |Clinical finding (finding)| OR << 71388002 |Procedure (procedure)|)]] }",
				eventDomain.getAdditionalField("domainTemplateForPostcoordination"));

		clinicalFindingDomain = memberService.findMember(branch.getPath(), clinicalFindingDomain.getMemberId());
		assertNotNull(clinicalFindingDomain);
		assertNull(clinicalFindingDomain.getEffectiveTimeI());
		assertNull(clinicalFindingDomain.getEffectiveTime());
		assertEquals("[[+id(<< 404684003 |Clinical finding (finding)|)]]: [[0..1]] 255234002 |After| = [[+id(<< 272379006 |Event (event)| OR << 404684003 |Clinical finding (finding)| OR << 71388002 |Procedure (procedure)|)]]",
				clinicalFindingDomain.getAdditionalField("domainTemplateForPrecoordination"));
		assertEquals("[[+scg(<< 404684003 |Clinical finding (finding)|)]]: [[0..1]] 255234002 |After| = [[+scg(<< 272379006 |Event (event)| OR << 404684003 |Clinical finding (finding)| OR << 71388002 |Procedure (procedure)|)]]",
				clinicalFindingDomain.getAdditionalField("domainTemplateForPostcoordination"));
	}

	@Test
	void testCreatingMRCMDomainAttributeWithoutRange() throws Exception {
		Branch branch = branchService.create("MAIN/MRCM");

		testUtil.createConceptWithPathIdAndTerm(branch.getPath(),"255234002", "After");
		testUtil.createConceptWithPathIdAndTerm(branch.getPath(),"272379006", "Event (event)");
		testUtil.createConceptWithPathIdAndTerm(branch.getPath(),"404684003", "Clinical finding (finding)");

		ReferenceSetMember eventDomain = new ReferenceSetMember(null, null,true,
				Concepts.CORE_MODULE, Concepts.REFSET_MRCM_DOMAIN_INTERNATIONAL,"272379006")
				.setAdditionalField("domainConstraint", "<< 272379006 |Event (event)|")
				.setAdditionalField("parentDomain", null)
				.setAdditionalField("proximalPrimitiveConstraint", "<< 272379006 |Event (event)|")
				.setAdditionalField("proximalPrimitiveRefinement", null)
				.setAdditionalField("guideURL", "");

		ReferenceSetMember event = new ReferenceSetMember(null, null,true,
				Concepts.CORE_MODULE, Concepts.REFSET_MRCM_ATTRIBUTE_DOMAIN_INTERNATIONAL,"255234002")
				.setAdditionalField("domainId", "272379006")
				.setAdditionalField("grouped", "1")
				.setAdditionalField("attributeCardinality", "0..*")
				.setAdditionalField("attributeInGroupCardinality", "0..1")
				.setAdditionalField("ruleStrengthId", "723597001")
				.setAdditionalField("contentTypeId", "723596005");

		Set<ReferenceSetMember> mrcmMembers = new HashSet<>();
		mrcmMembers.add(eventDomain);
		mrcmMembers.add(event);
		memberService.createMembers(branch.getPath(), mrcmMembers);

		eventDomain = memberService.findMember(branch.getPath(), eventDomain.getMemberId());
		assertNotNull(eventDomain);
		assertNotNull(eventDomain.getAdditionalField("domainTemplateForPrecoordination"));
		assertNotNull(eventDomain.getAdditionalField("domainTemplateForPostcoordination"));

		event = memberService.findMember(branch.getPath(), event.getMemberId());
		assertNotNull(event);
	}


	@Test
	void testConcreteValueAttribute() throws Exception {
		Branch branch = branchService.create("MAIN/MRCM");

		testUtil.createConceptWithPathIdAndTerm(branch.getPath(), Concepts.CONCEPT_MODEL_DATA_ATTRIBUTE, "Concept model data attribute (attribute)");
		Concept strengthConcept = new Concept("3264475007");
		strengthConcept.addDescription(new Description("CD has presentation strength numerator value"));
		strengthConcept.addAxiom(new Relationship(Concepts.ISA, Concepts.CONCEPT_MODEL_DATA_ATTRIBUTE));
		conceptService.create(strengthConcept, branch.getPath());
		testUtil.createConceptWithPathIdAndTerm(branch.getPath(),"373873005", "Pharmaceutical / biologic product (product)");

		ReferenceSetMember biologicProductDomain = new ReferenceSetMember(null, null,true,
				Concepts.CORE_MODULE, Concepts.REFSET_MRCM_DOMAIN_INTERNATIONAL,"373873005")
				.setAdditionalField("domainConstraint", "<< 373873005 |Pharmaceutical / biologic product (product)|")
				.setAdditionalField("parentDomain", null)
				.setAdditionalField("proximalPrimitiveConstraint", null)
				.setAdditionalField("proximalPrimitiveRefinement", null)
				.setAdditionalField("guideURL", "");

		ReferenceSetMember strengthNumeratorAttribute = new ReferenceSetMember(null, null,true,
				Concepts.CORE_MODULE, Concepts.REFSET_MRCM_ATTRIBUTE_DOMAIN_INTERNATIONAL,"3264475007")
				.setAdditionalField("domainId", "373873005")
				.setAdditionalField("grouped", "1")
				.setAdditionalField("attributeCardinality", "0..*")
				.setAdditionalField("attributeInGroupCardinality", "0..1")
				.setAdditionalField("ruleStrengthId", "723597001")
				.setAdditionalField("contentTypeId", "723596005");

		ReferenceSetMember range = new ReferenceSetMember(null, null,true,
				Concepts.CORE_MODULE, Concepts.REFSET_MRCM_ATTRIBUTE_RANGE_INTERNATIONAL,"3264475007")
				.setAdditionalField("rangeConstraint", "dec(>#0..)")
				.setAdditionalField("attributeRule", null)
				.setAdditionalField("ruleStrengthId", "723597001")
				.setAdditionalField("contentTypeId", "723596005");

		Set<ReferenceSetMember> mrcmMembers = new HashSet<>();
		mrcmMembers.add(biologicProductDomain);
		mrcmMembers.add(strengthNumeratorAttribute);
		mrcmMembers.add(range);
		memberService.createMembers(branch.getPath(), mrcmMembers);

		range = memberService.findMember(branch.getPath(), range.getMemberId());
		assertEquals("dec(>#0..)", range.getAdditionalField("rangeConstraint"));
		assertEquals("<< 373873005 |Pharmaceutical / biologic product (product)|: [0..*] { [0..1] 3264475007 |CD has presentation strength numerator value| > #0 }",
				range.getAdditionalField("attributeRule"));
		biologicProductDomain = memberService.findMember(branch.getPath(), biologicProductDomain.getMemberId());
		assertNotNull(biologicProductDomain);
		assertEquals("[[0..*]] { [[0..1]] 3264475007 |CD has presentation strength numerator value| = [[+dec(>#0..)]] }",
				biologicProductDomain.getAdditionalField("domainTemplateForPrecoordination"));
		assertNotNull("[[0..*]] { [[0..1]] 3264475007 |CD has presentation strength numerator value| = [[+dec(>#0..)]] }",
				biologicProductDomain.getAdditionalField("domainTemplateForPostcoordination"));
		strengthNumeratorAttribute = memberService.findMember(branch.getPath(), strengthNumeratorAttribute.getMemberId());
		assertNotNull(strengthNumeratorAttribute);
	}
}
