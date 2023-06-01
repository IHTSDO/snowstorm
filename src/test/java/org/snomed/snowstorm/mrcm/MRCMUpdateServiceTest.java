package org.snomed.snowstorm.mrcm;

import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.domain.Branch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.config.Config;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.data.services.*;
import org.snomed.snowstorm.mrcm.model.ContentType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.*;

import static org.junit.Assert.*;
import static org.snomed.snowstorm.core.data.domain.Concepts.*;

@Testcontainers
@ExtendWith(SpringExtension.class)
class MRCMUpdateServiceTest extends AbstractTest {

	@Autowired
	private BranchService branchService;

	@Autowired
	private ReferenceSetMemberService memberService;

	@Autowired
	private ConceptService conceptService;

	@Autowired
	private MRCMService mrcmService;

	@Autowired
	private BranchMergeService branchMergeService;

	@Autowired
	private CodeSystemService codeSystemService;

	private ServiceTestUtil testUtil;

	@BeforeEach
	void setUp() {
		testUtil = new ServiceTestUtil(conceptService);
	}

	@Test
	void testUpdatingMRCMRulesAndTemplates() throws Exception {

		Branch branch = branchService.create("MAIN/MRCM");
		final String branchPath = branch.getPath();
		testUtil.createConceptWithPathIdAndTerm(branchPath, "255234002", "After");
		testUtil.createConceptWithPathIdAndTerm(branchPath, "272379006", "Event (event)");
		testUtil.createConceptWithPathIdAndTerm(branchPath, "404684003", "Clinical finding (finding)");

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
		memberService.createMembers(branchPath, mrcmMembers);

		// verify attribute range
		range = memberService.findMember(branchPath, range.getMemberId());
		assertNotNull(range);

		assertEquals("<< 272379006 |Event (event)| OR << 404684003 |Clinical finding (finding)| OR << 71388002 |Procedure (procedure)|",
				range.getAdditionalFields().get("rangeConstraint"));

		String expected = "(<< 404684003 |Clinical finding (finding)|: [0..1] 255234002 |After| = (<< 272379006 |Event (event)| OR << 404684003 |Clinical finding (finding)| OR << 71388002 |Procedure (procedure)|))" +
				" OR (<< 272379006 |Event (event)|: [0..*] { [0..1] 255234002 |After| = (<< 272379006 |Event (event)| OR << 404684003 |Clinical finding (finding)| OR << 71388002 |Procedure (procedure)|) })";
		assertEquals(expected, range.getAdditionalField("attributeRule"));

		// verify domain templates
		eventDomain = memberService.findMember(branchPath, eventDomain.getMemberId());
		assertNotNull(eventDomain);
		assertEquals("[[+id(<< 272379006 |Event (event)|)]]: [[0..*]] { [[0..1]] 255234002 |After| = [[+id(<< 272379006 |Event (event)| OR << 404684003 |Clinical finding (finding)| OR << 71388002 |Procedure (procedure)|)]] }",
				eventDomain.getAdditionalField("domainTemplateForPrecoordination"));

		assertEquals("[[+scg(<< 272379006 |Event (event)|)]]: [[0..*]] { [[0..1]] 255234002 |After| = [[+scg(<< 272379006 |Event (event)| OR << 404684003 |Clinical finding (finding)| OR << 71388002 |Procedure (procedure)|)]] }",
				eventDomain.getAdditionalField("domainTemplateForPostcoordination"));

		clinicalFindingDomain = memberService.findMember(branchPath, clinicalFindingDomain.getMemberId());
		assertNotNull(clinicalFindingDomain);
		assertNull(clinicalFindingDomain.getEffectiveTimeI());
		assertNull(clinicalFindingDomain.getEffectiveTime());
		assertEquals("[[+id(<< 404684003 |Clinical finding (finding)|)]]: [[0..1]] 255234002 |After| = [[+id(<< 272379006 |Event (event)| OR << 404684003 |Clinical finding (finding)| OR << 71388002 |Procedure (procedure)|)]]",
				clinicalFindingDomain.getAdditionalField("domainTemplateForPrecoordination"));
		assertEquals("[[+scg(<< 404684003 |Clinical finding (finding)|)]]: [[0..1]] 255234002 |After| = [[+scg(<< 272379006 |Event (event)| OR << 404684003 |Clinical finding (finding)| OR << 71388002 |Procedure (procedure)|)]]",
				clinicalFindingDomain.getAdditionalField("domainTemplateForPostcoordination"));

		final Collection<ConceptMini> attributeConceptMinis =
				mrcmService.retrieveDomainAttributeConceptMinis(ContentType.PRECOORDINATED, true, Collections.singleton(404684003L), branchPath, Config.DEFAULT_LANGUAGE_DIALECTS);
		assertEquals(1, attributeConceptMinis.size());
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

	@Test
	void testModuleChangesWhenExtensionModifiesMRCM() throws ServiceException {
		Concept concept;

		// Create starting hierarchy (incl MRCM)
		concept = new Concept(CONCEPT_MODEL_DATA_ATTRIBUTE)
				.addDescription(new Description("Concept model data attribute (attribute)").setTypeId(FSN))
				.addDescription(new Description("Concept model data attribute").setTypeId(SYNONYM))
				.addAxiom(new Relationship(ISA, CONCEPT_MODEL_ATTRIBUTE));
		conceptService.create(concept, "MAIN");

		concept = new Concept("373873005")
				.addDescription(new Description("Pharmaceutical / biologic product (product)").setTypeId(FSN))
				.addDescription(new Description("Pharmaceutical / biologic product").setTypeId(SYNONYM))
				.addAxiom(new Relationship(ISA, SNOMEDCT_ROOT));
		concept = conceptService.create(concept, "MAIN");
		String biologicProductId = concept.getConceptId();

		String biologicProductDomainId = memberService.createMember("MAIN", new ReferenceSetMember(null, null, true,
				CORE_MODULE, REFSET_MRCM_DOMAIN_INTERNATIONAL, biologicProductId)
				.setAdditionalField("domainConstraint", "<< 373873005 |Pharmaceutical / biologic product (product)|")
				.setAdditionalField("parentDomain", null)
				.setAdditionalField("proximalPrimitiveConstraint", null)
				.setAdditionalField("proximalPrimitiveRefinement", null)
				.setAdditionalField("guideURL", "")).getMemberId();

		// International authors new attribute (incl MRCM)
		String intProject = "MAIN/project";
		branchService.create(intProject);
		String intTaskA = "MAIN/project/taskA";
		branchService.create(intTaskA);

		concept = new Concept()
				.addDescription(new Description("Has strength (attribute)").setTypeId(FSN))
				.addDescription(new Description("Has strength").setTypeId(SYNONYM))
				.addAxiom(new Relationship(ISA, CONCEPT_MODEL_DATA_ATTRIBUTE));
		concept = conceptService.create(concept, intTaskA);
		String hasStrengthId = concept.getConceptId();

		String strengthAttributeId = memberService.createMember(intTaskA, new ReferenceSetMember(null, null, true,
				CORE_MODULE, REFSET_MRCM_ATTRIBUTE_DOMAIN_INTERNATIONAL, hasStrengthId)
				.setAdditionalField("domainId", biologicProductId)
				.setAdditionalField("grouped", "1")
				.setAdditionalField("attributeCardinality", "0..*")
				.setAdditionalField("attributeInGroupCardinality", "0..1")
				.setAdditionalField("ruleStrengthId", "723597001")
				.setAdditionalField("contentTypeId", "723596005")).getMemberId();

		String strengthRangeId = memberService.createMember(intTaskA, new ReferenceSetMember(null, null, true,
				CORE_MODULE, REFSET_MRCM_ATTRIBUTE_RANGE_INTERNATIONAL, hasStrengthId)
				.setAdditionalField("rangeConstraint", "dec(>#0..)")
				.setAdditionalField("attributeRule", null)
				.setAdditionalField("ruleStrengthId", "723597001")
				.setAdditionalField("contentTypeId", "723596005")).getMemberId();

		// International promotes work to MAIN & versions
		branchMergeService.mergeBranchSync(intTaskA, intProject, Collections.emptySet());
		branchMergeService.mergeBranchSync(intProject, "MAIN", Collections.emptySet());

		codeSystemService.createCodeSystem(new CodeSystem("SNOMEDCT", "MAIN"));
		codeSystemService.createVersion(codeSystemService.find("SNOMEDCT"), 20220131, "20220131");

		// Create new Extension
		codeSystemService.createCodeSystem(new CodeSystem("SNOMEDCT-XX", "MAIN/SNOMEDCT-XX"));
		concept = conceptService.create(
				new Concept()
						.addDescription(new Description("Extension maintained module").setTypeId(FSN))
						.addDescription(new Description("Extension maintained").setTypeId(SYNONYM))
						.addAxiom(new Relationship(ISA, MODULE)),
				"MAIN/SNOMEDCT-XX"
		);
		String extDefaultModule = concept.getConceptId();
		branchService.updateMetadata("MAIN/SNOMEDCT-XX", Map.of(Config.DEFAULT_MODULE_ID_KEY, extDefaultModule));

		// Extension authors new attribute (incl MRCM)
		String extProject = "MAIN/SNOMEDCT-XX/project";
		branchService.create(extProject);
		String extTaskA = "MAIN/SNOMEDCT-XX/project/taskA";
		branchService.create(extTaskA);

		concept = new Concept()
				.addDescription(new Description("Occurrence (attribute)").setTypeId(FSN))
				.addDescription(new Description("Occurrence").setTypeId(SYNONYM))
				.addAxiom(new Relationship(ISA, CONCEPT_MODEL_DATA_ATTRIBUTE));
		concept = conceptService.create(concept, extTaskA);
		String occurrenceId = concept.getConceptId();

		String occurrenceAttributeId = memberService.createMember(extTaskA, new ReferenceSetMember(null, null, true,
				extDefaultModule, REFSET_MRCM_ATTRIBUTE_DOMAIN_INTERNATIONAL, occurrenceId)
				.setAdditionalField("domainId", biologicProductId)
				.setAdditionalField("grouped", "1")
				.setAdditionalField("attributeCardinality", "0..*")
				.setAdditionalField("attributeInGroupCardinality", "0..1")
				.setAdditionalField("ruleStrengthId", "723597001")
				.setAdditionalField("contentTypeId", "723596005")).getMemberId();

		String occurrenceRangeId = memberService.createMember(extTaskA, new ReferenceSetMember(null, null, true,
				extDefaultModule, REFSET_MRCM_ATTRIBUTE_RANGE_INTERNATIONAL, occurrenceId)
				.setAdditionalField("rangeConstraint", "dec(>#1..)")
				.setAdditionalField("attributeRule", null)
				.setAdditionalField("ruleStrengthId", "723597001")
				.setAdditionalField("contentTypeId", "723596005")).getMemberId();

		// Assert members belong to International
		ReferenceSetMember member = memberService.findMember(extTaskA, strengthAttributeId);
		assertEquals(CORE_MODULE, member.getModuleId());

		member = memberService.findMember(extTaskA, strengthRangeId);
		assertEquals(CORE_MODULE, member.getModuleId());

		// Assert members belong to Extension
		member = memberService.findMember(extTaskA, occurrenceAttributeId);
		assertEquals(extDefaultModule, member.getModuleId());

		member = memberService.findMember(extTaskA, occurrenceRangeId);
		assertEquals(extDefaultModule, member.getModuleId());

		// Assert member moved to Extension & templates updated
		member = memberService.findMember(extTaskA, biologicProductDomainId);
		assertEquals(extDefaultModule, member.getModuleId());
		assertTrue(member.getReleasedEffectiveTime().equals(20220131));

		String domainTemplateForPrecoordination = member.getAdditionalField("domainTemplateForPrecoordination");
		assertEquals(2, Arrays.asList(domainTemplateForPrecoordination.split(",")).size());
		assertTrue(domainTemplateForPrecoordination.contains(String.format("[[0..1]] %s |Occurrence| = [[+dec(>#1..)]]", occurrenceId)));
		assertTrue(domainTemplateForPrecoordination.contains(String.format("[[0..1]] %s |Has strength| = [[+dec(>#0..)]]", hasStrengthId)));

		String domainTemplateForPostcoordination = member.getAdditionalField("domainTemplateForPostcoordination");
		assertEquals(2, Arrays.asList(domainTemplateForPrecoordination.split(",")).size());
		assertTrue(domainTemplateForPostcoordination.contains(String.format("[[0..1]] %s |Occurrence| = [[+dec(>#1..)]]", occurrenceId)));
		assertTrue(domainTemplateForPostcoordination.contains(String.format("[[0..1]] %s |Has strength| = [[+dec(>#0..)]]", hasStrengthId)));
	}

	@Test
	void testInternationalDoesNotLoseModuleWhenModifyingMRCM() throws ServiceException {
		Concept concept;

		// Create starting hierarchy (incl MRCM)
		concept = new Concept(CONCEPT_MODEL_DATA_ATTRIBUTE)
				.addDescription(new Description("Concept model data attribute (attribute)").setTypeId(FSN))
				.addDescription(new Description("Concept model data attribute").setTypeId(SYNONYM))
				.addAxiom(new Relationship(ISA, CONCEPT_MODEL_ATTRIBUTE));
		conceptService.create(concept, "MAIN");

		concept = new Concept("373873005")
				.addDescription(new Description("Pharmaceutical / biologic product (product)").setTypeId(FSN))
				.addDescription(new Description("Pharmaceutical / biologic product").setTypeId(SYNONYM))
				.addAxiom(new Relationship(ISA, SNOMEDCT_ROOT));
		concept = conceptService.create(concept, "MAIN");
		String biologicProductId = concept.getConceptId();

		String biologicProductDomainId = memberService.createMember("MAIN", new ReferenceSetMember(null, null, true,
				CORE_MODULE, REFSET_MRCM_DOMAIN_INTERNATIONAL, biologicProductId)
				.setAdditionalField("domainConstraint", "<< 373873005 |Pharmaceutical / biologic product (product)|")
				.setAdditionalField("parentDomain", null)
				.setAdditionalField("proximalPrimitiveConstraint", null)
				.setAdditionalField("proximalPrimitiveRefinement", null)
				.setAdditionalField("guideURL", "")).getMemberId();

		// International authors new attribute (incl MRCM)
		concept = new Concept()
				.addDescription(new Description("Has strength (attribute)").setTypeId(FSN))
				.addDescription(new Description("Has strength").setTypeId(SYNONYM))
				.addAxiom(new Relationship(ISA, CONCEPT_MODEL_DATA_ATTRIBUTE));
		concept = conceptService.create(concept, "MAIN");
		String hasStrengthId = concept.getConceptId();

		String strengthAttributeId = memberService.createMember("MAIN", new ReferenceSetMember(null, null, true,
				CORE_MODULE, REFSET_MRCM_ATTRIBUTE_DOMAIN_INTERNATIONAL, hasStrengthId)
				.setAdditionalField("domainId", biologicProductId)
				.setAdditionalField("grouped", "1")
				.setAdditionalField("attributeCardinality", "0..*")
				.setAdditionalField("attributeInGroupCardinality", "0..1")
				.setAdditionalField("ruleStrengthId", "723597001")
				.setAdditionalField("contentTypeId", "723596005")).getMemberId();

		String strengthRangeId = memberService.createMember("MAIN", new ReferenceSetMember(null, null, true,
				CORE_MODULE, REFSET_MRCM_ATTRIBUTE_RANGE_INTERNATIONAL, hasStrengthId)
				.setAdditionalField("rangeConstraint", "dec(>#0..)")
				.setAdditionalField("attributeRule", null)
				.setAdditionalField("ruleStrengthId", "723597001")
				.setAdditionalField("contentTypeId", "723596005")).getMemberId();

		// International versions
		codeSystemService.createCodeSystem(new CodeSystem("SNOMEDCT", "MAIN"));
		codeSystemService.createVersion(codeSystemService.find("SNOMEDCT"), 20220131, "20220131");

		// International authors new attribute (incl MRCM)
		concept = new Concept()
				.addDescription(new Description("Occurrence (attribute)").setTypeId(FSN))
				.addDescription(new Description("Occurrence").setTypeId(SYNONYM))
				.addAxiom(new Relationship(ISA, CONCEPT_MODEL_DATA_ATTRIBUTE));
		concept = conceptService.create(concept, "MAIN");
		String occurrenceId = concept.getConceptId();

		String occurrenceAttributeId = memberService.createMember("MAIN", new ReferenceSetMember(null, null, true,
				CORE_MODULE, REFSET_MRCM_ATTRIBUTE_DOMAIN_INTERNATIONAL, occurrenceId)
				.setAdditionalField("domainId", biologicProductId)
				.setAdditionalField("grouped", "1")
				.setAdditionalField("attributeCardinality", "0..*")
				.setAdditionalField("attributeInGroupCardinality", "0..1")
				.setAdditionalField("ruleStrengthId", "723597001")
				.setAdditionalField("contentTypeId", "723596005")).getMemberId();

		String occurrenceRangeId = memberService.createMember("MAIN", new ReferenceSetMember(null, null, true,
				CORE_MODULE, REFSET_MRCM_ATTRIBUTE_RANGE_INTERNATIONAL, occurrenceId)
				.setAdditionalField("rangeConstraint", "dec(>#1..)")
				.setAdditionalField("attributeRule", null)
				.setAdditionalField("ruleStrengthId", "723597001")
				.setAdditionalField("contentTypeId", "723596005")).getMemberId();

		// Assert members belong to International
		ReferenceSetMember member = memberService.findMember("MAIN", strengthAttributeId);
		assertEquals(CORE_MODULE, member.getModuleId());

		member = memberService.findMember("MAIN", strengthRangeId);
		assertEquals(CORE_MODULE, member.getModuleId());

		// Assert members belong to Extension
		member = memberService.findMember("MAIN", occurrenceAttributeId);
		assertEquals(CORE_MODULE, member.getModuleId());

		member = memberService.findMember("MAIN", occurrenceRangeId);
		assertEquals(CORE_MODULE, member.getModuleId());

		// Assert member moved to Extension & templates updated
		member = memberService.findMember("MAIN", biologicProductDomainId);
		assertEquals(CORE_MODULE, member.getModuleId());
		assertTrue(member.getReleasedEffectiveTime().equals(20220131));

		String domainTemplateForPrecoordination = member.getAdditionalField("domainTemplateForPrecoordination");
		assertEquals(2, Arrays.asList(domainTemplateForPrecoordination.split(",")).size());
		assertTrue(domainTemplateForPrecoordination.contains(String.format("[[0..1]] %s |Occurrence| = [[+dec(>#1..)]]", occurrenceId)));
		assertTrue(domainTemplateForPrecoordination.contains(String.format("[[0..1]] %s |Has strength| = [[+dec(>#0..)]]", hasStrengthId)));

		String domainTemplateForPostcoordination = member.getAdditionalField("domainTemplateForPostcoordination");
		assertEquals(2, Arrays.asList(domainTemplateForPrecoordination.split(",")).size());
		assertTrue(domainTemplateForPostcoordination.contains(String.format("[[0..1]] %s |Occurrence| = [[+dec(>#1..)]]", occurrenceId)));
		assertTrue(domainTemplateForPostcoordination.contains(String.format("[[0..1]] %s |Has strength| = [[+dec(>#0..)]]", hasStrengthId)));
	}
}
