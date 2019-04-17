package org.snomed.snowstorm.core.data.services;

import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.api.ComponentService;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.TestConfig;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.rest.View;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.Assert.*;
import static org.snomed.snowstorm.core.data.domain.Concepts.*;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = TestConfig.class)
public class ConceptServiceTest extends AbstractTest {

	@Autowired
	private BranchService branchService;

	@Autowired
	private ConceptService conceptService;

	@Autowired
	private DescriptionService descriptionService;

	@Autowired
	private RelationshipService relationshipService;

	@Autowired
	private ReferenceSetMemberService referenceSetMemberService;

	@Autowired
	private ReleaseService releaseService;

	@Autowired
	private QueryService queryService;

	private ServiceTestUtil testUtil;

	private final Logger logger = LoggerFactory.getLogger(getClass());
	private ObjectMapper objectMapper;

	@Before
	public void setup() {
		branchService.create("MAIN");
		testUtil = new ServiceTestUtil(conceptService);
		objectMapper = new ObjectMapper();
		DeserializationConfig deserializationConfig = objectMapper.getDeserializationConfig().without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
		objectMapper.setConfig(deserializationConfig);
	}

	@Test
	public void testConceptCreationBranchingVisibility() throws ServiceException {
		Assert.assertNull("Concept 100001 does not exist on MAIN.", conceptService.find("100001", "MAIN"));

		conceptService.create(new Concept("100001", "10000111"), "MAIN");

		final Concept c1 = conceptService.find("100001", "MAIN");
		Assert.assertNotNull("Concept 100001 exists on MAIN.", c1);
		assertEquals("MAIN", c1.getPath());
		assertEquals("10000111", c1.getModuleId());

		branchService.create("MAIN/A");
		conceptService.create(new Concept("100002", "10000222"), "MAIN/A");
		Assert.assertNull("Concept 100002 does not exist on MAIN.", conceptService.find("100002", "MAIN"));
		Assert.assertNotNull("Concept 100002 exists on branch A.", conceptService.find("100002", "MAIN/A"));
		Assert.assertNotNull("Concept 100001 is accessible on branch A because of the base time.", conceptService.find("100001", "MAIN/A"));

		conceptService.create(new Concept("100003", "10000333"), "MAIN");
		Assert.assertNull("Concept 100003 is not accessible on branch A because created after branching.", conceptService.find("100003", "MAIN/A"));
		Assert.assertNotNull(conceptService.find("100003", "MAIN"));
	}

	@Test
	public void testDeleteDescription() throws ServiceException {
		final Concept concept = conceptService.create(
				new Concept("100001")
						.addDescription(new Description("100001", "one"))
						.addDescription(new Description("100002", "two"))
						.addDescription(new Description("100003", "three"))
				, "MAIN");

		assertEquals(3, concept.getDescriptions().size());
		assertEquals(3, conceptService.find("100001", "MAIN").getDescriptions().size());

		branchService.create("MAIN/one");
		branchService.create("MAIN/one/one-1");
		branchService.create("MAIN/two");

		concept.getDescriptions().remove(new Description("100002", ""));
		final Concept updatedConcept = conceptService.update(concept, "MAIN/one");

		assertEquals(2, updatedConcept.getDescriptions().size());
		assertEquals(2, conceptService.find("100001", "MAIN/one").getDescriptions().size());
		assertEquals(3, conceptService.find("100001", "MAIN").getDescriptions().size());
		assertEquals(3, conceptService.find("100001", "MAIN/one/one-1").getDescriptions().size());
		assertEquals(3, conceptService.find("100001", "MAIN/two").getDescriptions().size());
	}

	@Test
	public void testDeleteLangMembersDuringDescriptionDeletion() throws ServiceException {
		Concept concept = new Concept("10000123");
		Description fsn = fsn("Is a (attribute)");
		conceptService.create(concept
				.addDescription(
						fsn
								.addLanguageRefsetMember(Concepts.GB_EN_LANG_REFSET, Concepts.PREFERRED)
								.addLanguageRefsetMember(Concepts.US_EN_LANG_REFSET, Concepts.PREFERRED)

				), "MAIN");
		String descriptionId = fsn.getDescriptionId();
		Assert.assertNotNull(descriptionId);

		List<ReferenceSetMember> acceptabilityMembers = referenceSetMemberService.findMembers("MAIN", descriptionId, PageRequest.of(0, 10)).getContent();
		Assert.assertEquals(2, acceptabilityMembers.size());
		Assert.assertTrue(acceptabilityMembers.get(0).isActive());
		Assert.assertTrue(acceptabilityMembers.get(1).isActive());

		concept.getDescriptions().clear();

		conceptService.update(concept, "MAIN");

		List<ReferenceSetMember> acceptabilityMembersAfterDescriptionDeletion = referenceSetMemberService.findMembers("MAIN", descriptionId, PageRequest.of(0, 10)).getContent();
		Assert.assertEquals(0, acceptabilityMembersAfterDescriptionDeletion.size());
	}

	@Test
	public void testDescriptionInactivation() throws ServiceException {
		Concept concept = new Concept("10000123");
		Description fsn = fsn("Is a (attribute)");
		conceptService.create(concept
				.addDescription(
						fsn
								.addLanguageRefsetMember(Concepts.GB_EN_LANG_REFSET, Concepts.PREFERRED)
								.addLanguageRefsetMember(Concepts.US_EN_LANG_REFSET, Concepts.PREFERRED)

				), "MAIN");
		String descriptionId = fsn.getDescriptionId();
		Assert.assertNotNull(descriptionId);

		List<ReferenceSetMember> acceptabilityMembers = referenceSetMemberService.findMembers("MAIN", descriptionId, PageRequest.of(0, 10)).getContent();
		Assert.assertEquals(2, acceptabilityMembers.size());
		Assert.assertTrue(acceptabilityMembers.get(0).isActive());
		Assert.assertTrue(acceptabilityMembers.get(1).isActive());

		Description descriptionToInactivate = concept.getDescriptions().iterator().next();
		descriptionToInactivate.setActive(false);
		descriptionToInactivate.setInactivationIndicator(Concepts.inactivationIndicatorNames.get(Concepts.OUTDATED));
		Map<String, Set<String>> associationTargets = new HashMap<>();
		associationTargets.put("REFERS_TO", Collections.singleton("321667001"));
		descriptionToInactivate.setAssociationTargets(associationTargets);

		Concept updated = conceptService.update(concept, "MAIN");
		Assert.assertEquals(1, updated.getDescriptions().size());
		Description updatedDescription = updated.getDescriptions().iterator().next();
		assertFalse(updatedDescription.isActive());
		Assert.assertEquals(Concepts.inactivationIndicatorNames.get(Concepts.OUTDATED), updatedDescription.getInactivationIndicator());
		ReferenceSetMember inactivationIndicatorMember = updatedDescription.getInactivationIndicatorMember();
		Assert.assertNotNull(inactivationIndicatorMember);
		Assert.assertEquals(Concepts.DESCRIPTION_INACTIVATION_INDICATOR_REFERENCE_SET, inactivationIndicatorMember.getRefsetId());
		Assert.assertEquals(updatedDescription.getDescriptionId(), inactivationIndicatorMember.getReferencedComponentId());
		Assert.assertEquals(Concepts.OUTDATED, inactivationIndicatorMember.getAdditionalField("valueId"));
		Set<ReferenceSetMember> associationTargetMembers = updatedDescription.getAssociationTargetMembers();
		Assert.assertNotNull(associationTargetMembers);
		assertEquals(1, associationTargetMembers.size());
		ReferenceSetMember associationTargetMember = associationTargetMembers.iterator().next();
		assertEquals(Concepts.REFSET_REFERS_TO_ASSOCIATION, associationTargetMember.getRefsetId());
		assertTrue(associationTargetMember.isActive());
		assertEquals(descriptionToInactivate.getDescriptionId(), associationTargetMember.getReferencedComponentId());
		assertEquals("321667001", associationTargetMember.getAdditionalField("targetComponentId"));

		List<ReferenceSetMember> membersAfterDescriptionInactivation = referenceSetMemberService.findMembers("MAIN", descriptionId, PageRequest.of(0, 10)).getContent();
		Assert.assertEquals(2, membersAfterDescriptionInactivation.size());
		boolean descriptionInactivationIndicatorMemberFound = false;
		boolean refersToMemberFound = false;
		for (ReferenceSetMember actualMember : membersAfterDescriptionInactivation) {
			if (Concepts.DESCRIPTION_INACTIVATION_INDICATOR_REFERENCE_SET.equals(actualMember.getRefsetId())) {
				descriptionInactivationIndicatorMemberFound = true;
			}
			if (Concepts.REFSET_REFERS_TO_ASSOCIATION.equals(actualMember.getRefsetId())) {
				refersToMemberFound = true;
			}
		}
		assertTrue(descriptionInactivationIndicatorMemberFound);
		assertTrue(refersToMemberFound);
	}

	@Test
	public void testCreateDeleteRelationship() throws ServiceException {
		conceptService.create(new Concept(ISA).setDefinitionStatusId(PRIMITIVE).addDescription(fsn("Is a (attribute)")), "MAIN");
		conceptService.create(new Concept(SNOMEDCT_ROOT).setDefinitionStatusId(PRIMITIVE).addDescription(fsn("SNOMED CT Concept")), "MAIN");

		final Concept concept = conceptService.create(
				new Concept("100001")
						.addRelationship(new Relationship("100001", ISA, SNOMEDCT_ROOT))
						.addRelationship(new Relationship("100002", ISA, SNOMEDCT_ROOT))
						.addRelationship(new Relationship("100003", ISA, SNOMEDCT_ROOT))
				, "MAIN");

		Relationship createdRelationship = concept.getRelationships().iterator().next();
		assertNotNull(createdRelationship);
		assertNotNull(createdRelationship.type());
		assertEquals("Creation response should contain FSN within relationship type", "Is a (attribute)", createdRelationship.type().getFsn());
		assertEquals("Creation response should contain definition status within relationship type", "PRIMITIVE", createdRelationship.type().getDefinitionStatus());
		assertEquals("Creation response should contain FSN within relationship target", "SNOMED CT Concept", createdRelationship.target().getFsn());
		assertEquals("Creation response should contain definition status within relationship target", "PRIMITIVE", createdRelationship.target().getDefinitionStatus());

		assertEquals(3, concept.getRelationships().size());
		Concept foundConcept = conceptService.find("100001", "MAIN");
		assertEquals(3, foundConcept.getRelationships().size());

		Relationship foundRelationship = foundConcept.getRelationships().iterator().next();
		assertEquals("Find response should contain FSN within relationship type", "Is a (attribute)", foundRelationship.type().getFsn());
		assertEquals("Find response should contain definition status within relationship type", "PRIMITIVE", foundRelationship.type().getDefinitionStatus());
		assertEquals("Find response should contain FSN within relationship target", "SNOMED CT Concept", foundRelationship.target().getFsn());
		assertEquals("Find response should contain definition status within relationship target", "PRIMITIVE", foundRelationship.target().getDefinitionStatus());

		concept.getRelationships().remove(new Relationship("100003"));
		final Concept updatedConcept = conceptService.update(concept, "MAIN");

		assertEquals(2, updatedConcept.getRelationships().size());
		assertEquals(2, conceptService.find("100001", "MAIN").getRelationships().size());

		Relationship updatedRelationship = foundConcept.getRelationships().iterator().next();
		assertEquals("Update response should contain FSN within relationship type", "Is a (attribute)", updatedRelationship.type().getFsn());
		assertEquals("Update response should contain definition status within relationship type", "PRIMITIVE", updatedRelationship.type().getDefinitionStatus());
		assertEquals("Update response should contain FSN within relationship target", "SNOMED CT Concept", updatedRelationship.target().getFsn());
		assertEquals("Update response should contain definition status within relationship target", "PRIMITIVE", updatedRelationship.target().getDefinitionStatus());
	}

	@Test
	public void testMultipleConceptVersionsOnOneBranch() throws ServiceException {
		assertEquals(0, conceptService.findAll("MAIN", ServiceTestUtil.PAGE_REQUEST).getTotalElements());
		conceptService.create(new Concept("100001", "10000111"), "MAIN");

		final Concept concept1 = conceptService.find("100001", "MAIN");
		assertEquals("10000111", concept1.getModuleId());
		assertEquals(1, conceptService.findAll("MAIN", ServiceTestUtil.PAGE_REQUEST).getTotalElements());

		conceptService.update(new Concept("100001", "10000222"), "MAIN");

		final Concept concept1Version2 = conceptService.find("100001", "MAIN");
		assertEquals("10000222", concept1Version2.getModuleId());
		assertEquals(1, conceptService.findAll("MAIN", ServiceTestUtil.PAGE_REQUEST).getTotalElements());
	}

	@Test
	public void testUpdateExistingConceptOnNewBranch() throws ServiceException {
		conceptService.create(new Concept("100001", "10000111"), "MAIN");

		branchService.create("MAIN/A");

		conceptService.update(new Concept("100001", "10000222"), "MAIN/A");

		assertEquals("10000111", conceptService.find("100001", "MAIN").getModuleId());
		assertEquals("10000222", conceptService.find("100001", "MAIN/A").getModuleId());
	}

	@Test
	public void testOnlyUpdateWhatChanged() throws ServiceException {
		final Integer effectiveTime = 20160731;

		conceptService.create(new Concept("100001", effectiveTime, true, Concepts.CORE_MODULE, Concepts.PRIMITIVE)
				.addDescription(new Description("1000011", effectiveTime, true, Concepts.CORE_MODULE, null, "en",
						Concepts.FSN, "My Concept (finding)", Concepts.INITIAL_CHARACTER_CASE_INSENSITIVE))
				.addDescription(new Description("1000012", effectiveTime, true, Concepts.CORE_MODULE, null, "en",
						Concepts.SYNONYM, "My Concept", Concepts.INITIAL_CHARACTER_CASE_INSENSITIVE)),
				"MAIN");

		final Concept conceptAfterSave = conceptService.find("100001", "MAIN");

		conceptAfterSave.getDescription("1000011").setActive(false);
		conceptService.update(conceptAfterSave, "MAIN");

		final Concept conceptAfterUpdate = conceptService.find("100001", "MAIN");

		assertEquals("Concept document should not have been updated.",
				conceptAfterSave.getInternalId(), conceptAfterUpdate.getInternalId());
		assertEquals("Synonym document should not have been updated.",
				conceptAfterSave.getDescription("1000012").getInternalId(), conceptAfterUpdate.getDescription("1000012").getInternalId());
		Assert.assertNotEquals("FSN document should have been updated.",
				conceptAfterSave.getDescription("1000011").getInternalId(), conceptAfterUpdate.getDescription("1000011").getInternalId());

	}

	@Test
	public void testFindConceptOnParentBranchUsingBaseVersion() throws ServiceException {
		conceptService.create(new Concept("100001", "10000111"), "MAIN");
		conceptService.update(new Concept("100001", "10000222"), "MAIN");

		branchService.create("MAIN/A");

		conceptService.update(new Concept("100001", "10000333"), "MAIN");

		assertEquals("10000333", conceptService.find("100001", "MAIN").getModuleId());
		assertEquals("10000222", conceptService.find("100001", "MAIN/A").getModuleId());

		branchService.create("MAIN/A/A1");

		assertEquals("10000222", conceptService.find("100001", "MAIN/A/A1").getModuleId());

		conceptService.update(new Concept("100001", "10000333"), "MAIN/A");

		assertEquals("10000222", conceptService.find("100001", "MAIN/A/A1").getModuleId());
	}

	@Test
	public void testListConceptsOnGrandchildBranchWithUpdateOnChildBranch() throws ServiceException {
		conceptService.create(new Concept("100001", "10000111"), "MAIN");
		assertEquals("10000111", conceptService.find("100001", "MAIN").getModuleId());

		branchService.create("MAIN/A");
		branchService.create("MAIN/A/A1");
		conceptService.update(new Concept("100001", "10000222"), "MAIN/A");
		branchService.create("MAIN/A/A2");

		assertEquals("10000111", conceptService.find("100001", "MAIN").getModuleId());
		assertEquals("10000222", conceptService.find("100001", "MAIN/A").getModuleId());
		assertEquals("10000111", conceptService.find("100001", "MAIN/A/A1").getModuleId());
		assertEquals("10000222", conceptService.find("100001", "MAIN/A/A2").getModuleId());

		final Page<Concept> allOnGrandChild = conceptService.findAll("MAIN/A/A1", ServiceTestUtil.PAGE_REQUEST);
		assertEquals(1, allOnGrandChild.getTotalElements());
		assertEquals("10000111", allOnGrandChild.getContent().get(0).getModuleId());

		final Page<Concept> allOnChild = conceptService.findAll("MAIN/A", ServiceTestUtil.PAGE_REQUEST);
		assertEquals(1, allOnChild.getTotalElements());
		assertEquals("10000222", allOnChild.getContent().get(0).getModuleId());

		conceptService.update(new Concept("100001", "10000333"), "MAIN/A");

		final Page<Concept> allOnChildAfterSecondUpdate = conceptService.findAll("MAIN/A", ServiceTestUtil.PAGE_REQUEST);
		assertEquals(1, allOnChildAfterSecondUpdate.getTotalElements());
		assertEquals("10000333", allOnChildAfterSecondUpdate.getContent().get(0).getModuleId());

		assertEquals("10000111", conceptService.find("100001", "MAIN").getModuleId());
		assertEquals("10000333", conceptService.find("100001", "MAIN/A").getModuleId());
		assertEquals("10000111", conceptService.find("100001", "MAIN/A/A1").getModuleId());
		assertEquals("10000222", conceptService.find("100001", "MAIN/A/A2").getModuleId());
	}

	@Test
	public void testSaveConceptWithDescription() throws ServiceException {
		final Concept concept = new Concept("50960005", 20020131, true, "900000000000207008", "900000000000074008");
		concept.addDescription(new Description("84923010", 20020131, true, "900000000000207008", "50960005", "en", "900000000000013009", "Bleeding", "900000000000020002"));
		conceptService.create(concept, "MAIN");

		final Concept savedConcept = conceptService.find("50960005", "MAIN");
		Assert.assertNotNull(savedConcept);
		assertEquals(1, savedConcept.getDescriptions().size());
		final Description description = savedConcept.getDescriptions().iterator().next();
		assertEquals("84923010", description.getDescriptionId());
		assertEquals(0, description.getAcceptabilityMapFromLangRefsetMembers().size());
	}

	@Test
	public void testSaveConceptWithAxioms() throws ServiceException {
		String path = "MAIN";
		final Concept concept = new Concept("50960005", 20020131, true, Concepts.CORE_MODULE, "900000000000074008");
		concept.addAxiom(new Axiom(null, Concepts.FULLY_DEFINED, Sets.newHashSet(new Relationship(Concepts.ISA, "10000100"), new Relationship("10000200", "10000300"))).setModuleId(CORE_MODULE));
		concept.addGeneralConceptInclusionAxiom(new Axiom(null, Concepts.PRIMITIVE, Sets.newHashSet(new Relationship(Concepts.ISA, "10000500"), new Relationship("10000600", "10000700"))).setModuleId(CORE_MODULE));
		conceptService.create(concept, path);
		assertEquals(1, conceptService.find(concept.getConceptId(), path).getClassAxioms().size());

		final Concept savedConcept = conceptService.find("50960005", path);
		Assert.assertNotNull(savedConcept);
		assertEquals(1, savedConcept.getClassAxioms().size());
		assertEquals(1, savedConcept.getGciAxioms().size());
		Axiom axiom = savedConcept.getClassAxioms().iterator().next();
		assertEquals(Concepts.FULLY_DEFINED, axiom.getDefinitionStatusId());
		assertEquals(Concepts.CORE_MODULE, axiom.getModuleId());
		List<Relationship> relationships = new ArrayList<>(axiom.getRelationships());
		assertEquals(2, relationships.size());
		relationships.sort(Comparator.comparing(Relationship::getTypeId));
		assertEquals("10000200", relationships.get(0).getTypeId());
		assertEquals("10000300", relationships.get(0).getDestinationId());
		assertEquals(Concepts.ISA, relationships.get(1).getTypeId());
		assertEquals("10000100", relationships.get(1).getDestinationId());
		Axiom gciAxiom = savedConcept.getGciAxioms().iterator().next();
		assertEquals(Concepts.PRIMITIVE, gciAxiom.getDefinitionStatusId());
		assertEquals("Concept and class axiom should have the same definition status",
				axiom.getDefinitionStatusId(), savedConcept.getDefinitionStatusId());
		assertEquals(Concepts.CORE_MODULE, savedConcept.getModuleId());

		Page<ReferenceSetMember> members = referenceSetMemberService.findMembers(path, true, Concepts.OWL_AXIOM_REFERENCE_SET, savedConcept.getConceptId(), null, null, PageRequest.of(0, 10));
		assertEquals(2, members.getTotalElements());
		String axiomId = axiom.getAxiomId();
		ReferenceSetMember referenceSetMember = members.getContent().stream().filter(member -> member.getMemberId().equals(axiomId)).collect(Collectors.toList()).get(0);
		assertEquals("EquivalentClasses(:50960005 ObjectIntersectionOf(:10000100 ObjectSomeValuesFrom(:609096000 ObjectSomeValuesFrom(:10000200 :10000300))))",
				referenceSetMember.getAdditionalField(ReferenceSetMember.OwlExpressionFields.OWL_EXPRESSION));
		String memberId = referenceSetMember.getMemberId();

		Concept updatedConcept = conceptService.update(concept, path);
		assertEquals(1, conceptService.find(concept.getConceptId(), path).getClassAxioms().size());
		axiom = updatedConcept.getClassAxioms().iterator().next();
		assertEquals("Member id should be kept after update if no changes to OWL expression.", memberId, axiom.getReferenceSetMember().getMemberId());

		String axiomMemberInternalId = axiom.getReferenceSetMember().getInternalId();
		updatedConcept = conceptService.update(concept, path);
		axiom = updatedConcept.getClassAxioms().iterator().next();
		assertEquals("A new state of the axiom member should not be created if there are no changes.",
				axiomMemberInternalId, axiom.getReferenceSetMember().getInternalId());

		axiom.setDefinitionStatusId(Concepts.PRIMITIVE);
		updatedConcept = conceptService.update(concept, path);
		axiom = updatedConcept.getClassAxioms().iterator().next();
		assertEquals("Member id should not be changed after changing the OWL expression.", memberId, axiom.getReferenceSetMember().getMemberId());
		assertEquals("SubClassOf(:50960005 ObjectIntersectionOf(:10000100 ObjectSomeValuesFrom(:609096000 ObjectSomeValuesFrom(:10000200 :10000300))))",
				axiom.getReferenceSetMember().getAdditionalField(ReferenceSetMember.OwlExpressionFields.OWL_EXPRESSION));
		assertEquals("Concept and class axiom should have the same definition status",
				axiom.getDefinitionStatusId(), updatedConcept.getDefinitionStatusId());

		concept.getClassAxioms().clear();
		concept.getGciAxioms().clear();
		conceptService.update(concept, path);
		assertEquals(0, referenceSetMemberService.findMembers(path, true, Concepts.OWL_AXIOM_REFERENCE_SET, savedConcept.getConceptId(), null, null, PageRequest.of(0, 10)).getTotalElements());
	}

	@Test
	public void testConceptInactivation() throws ServiceException {
		String path = "MAIN";
		conceptService.batchCreate(Lists.newArrayList(new Concept("107658001"), new Concept("116680003")), path);
		final Concept concept = new Concept("50960005", 20020131, true, "900000000000207008", "900000000000074008");
		concept.addDescription(new Description("84923010", 20020131, true, "900000000000207008", "50960005", "en", "900000000000013009", "Bleeding", "900000000000020002"));
		concept.addRelationship(new Relationship(ISA, "107658001"));
		Concept savedConcept = conceptService.create(concept, path);

		savedConcept.setActive(false);

		// Set inactivation indicator using strings
		savedConcept.setInactivationIndicator(Concepts.inactivationIndicatorNames.get(Concepts.DUPLICATE));
		assertNull(savedConcept.getInactivationIndicatorMember());

		// Set association target using strings
		HashMap<String, Set<String>> associationTargetStrings = new HashMap<>();
		associationTargetStrings.put(Concepts.historicalAssociationNames.get(Concepts.REFSET_SAME_AS_ASSOCIATION), Collections.singleton("87100004"));
		savedConcept.setAssociationTargets(associationTargetStrings);
		assertNull(savedConcept.getAssociationTargetMembers());

		Concept inactiveConcept = conceptService.update(savedConcept, path);
		Concept foundInactiveConcept = conceptService.find(savedConcept.getConceptId(), path);

		assertFalse(inactiveConcept.isActive());

		// Check inactivation indicator string
		assertEquals("DUPLICATE", inactiveConcept.getInactivationIndicator());
		assertEquals("DUPLICATE", foundInactiveConcept.getInactivationIndicator());

		// Check inactivation indicator reference set member was created
		ReferenceSetMember inactivationIndicatorMember = inactiveConcept.getInactivationIndicatorMember();
		assertNotNull(inactivationIndicatorMember);
		assertTrue(inactivationIndicatorMember.isActive());
		assertEquals(Concepts.CONCEPT_INACTIVATION_INDICATOR_REFERENCE_SET, inactivationIndicatorMember.getRefsetId());
		assertEquals(Concepts.DUPLICATE, inactivationIndicatorMember.getAdditionalField("valueId"));

		// Check association target strings
		Map<String, Set<String>> associationTargetsAfter = inactiveConcept.getAssociationTargets();
		assertNotNull(associationTargetsAfter);
		assertEquals(1, associationTargetsAfter.size());
		assertEquals(Collections.singleton("87100004"), associationTargetsAfter.get(Concepts.historicalAssociationNames.get(Concepts.REFSET_SAME_AS_ASSOCIATION)));

		// Check association target reference set member was created
		Set<ReferenceSetMember> associationTargetMembers = inactiveConcept.getAssociationTargetMembers();
		assertNotNull(associationTargetMembers);
		assertEquals(1, associationTargetMembers.size());
		ReferenceSetMember associationTargetMember = associationTargetMembers.iterator().next();
		assertTrue(associationTargetMember.isActive());
		assertEquals(Concepts.REFSET_SAME_AS_ASSOCIATION, associationTargetMember.getRefsetId());
		assertEquals(concept.getModuleId(), associationTargetMember.getModuleId());
		assertEquals(concept.getId(), associationTargetMember.getReferencedComponentId());
		assertEquals("87100004", associationTargetMember.getAdditionalField("targetComponentId"));

		assertFalse(inactiveConcept.getRelationships().iterator().next().isActive());
		assertTrue(inactiveConcept.getDescriptions().iterator().next().isActive());
	}

	@Test
	public void testSaveConceptWithDescriptionAndAcceptabilityTogether() throws ServiceException {
		final Concept concept = new Concept("50960005", 20020131, true, "900000000000207008", "900000000000074008");
		concept.addDescription(
				new Description("84923010", 20020131, true, "900000000000207008", "50960005", "en", "900000000000013009", "Bleeding", "900000000000020002")
						.addLanguageRefsetMember("900000000000509007", Concepts.PREFERRED)
		);
		conceptService.create(concept, "MAIN");

		final Concept savedConcept = conceptService.find("50960005", "MAIN");
		Assert.assertNotNull(savedConcept);
		assertEquals(1, savedConcept.getDescriptions().size());
		final Description description = savedConcept.getDescriptions().iterator().next();
		assertEquals("84923010", description.getDescriptionId());
		final Map<String, ReferenceSetMember> members = description.getLangRefsetMembers();
		assertEquals(1, members.size());
		assertEquals(Concepts.PREFERRED, members.get("900000000000509007").getAdditionalField("acceptabilityId"));
	}

	@Test
	public void testChangeDescriptionAcceptabilityOnChildBranch() throws ServiceException {
		final Concept concept = new Concept("50960005", 20020131, true, "900000000000207008", "900000000000074008");
		concept.addDescription(
				new Description("84923010", 20020131, true, "900000000000207008", "50960005", "en", "900000000000013009", "Bleeding", "900000000000020002")
						.addLanguageRefsetMember("900000000000509007", Concepts.PREFERRED)
		);
		conceptService.create(concept, "MAIN");

		// Check acceptability on MAIN
		final Concept savedConcept1 = conceptService.find("50960005", "MAIN");
		final Description description1 = savedConcept1.getDescriptions().iterator().next();
		final Map<String, ReferenceSetMember> members1 = description1.getLangRefsetMembers();
		assertEquals(Concepts.PREFERRED, members1.get("900000000000509007").getAdditionalField("acceptabilityId"));

		// Update acceptability on MAIN/branch1
		description1.addLanguageRefsetMember("900000000000509007", Concepts.ACCEPTABLE);
		branchService.create("MAIN/branch1");
		conceptService.update(savedConcept1, "MAIN/branch1");

		// Check acceptability on MAIN/branch1
		logger.info("Loading updated concept on MAIN/branch1");
		final Concept savedConcept2 = conceptService.find("50960005", "MAIN/branch1");
		final Description description2 = savedConcept2.getDescriptions().iterator().next();
		final Map<String, ReferenceSetMember> members2 = description2.getLangRefsetMembers();
		assertEquals(Concepts.ACCEPTABLE, members2.get("900000000000509007").getAdditionalField("acceptabilityId"));

		// Check acceptability still the same on MAIN
		final Concept savedConcept3 = conceptService.find("50960005", "MAIN");
		final Description description3 = savedConcept3.getDescriptions().iterator().next();
		final Map<String, ReferenceSetMember> members3 = description3.getLangRefsetMembers();
		assertEquals(Concepts.PREFERRED, members3.get("900000000000509007").getAdditionalField("acceptabilityId"));
	}

	@Test
	public void testChangeDescriptionCaseSignificance() throws ServiceException, IOException {
		String conceptId = "50960005";
		Concept concept = new Concept(conceptId, 20020131, true, "900000000000207008", "900000000000074008");
		String descriptionId = "84923010";
		concept.addDescription(
				new Description(descriptionId, 20020131, true, "900000000000207008", conceptId, "en", "900000000000013009", "Bleeding",
						Concepts.INITIAL_CHARACTER_CASE_INSENSITIVE).addLanguageRefsetMember("900000000000509007", Concepts.PREFERRED)
		);
		String path = "MAIN";
		concept = conceptService.create(concept, path);
		concept = convertToJsonAndBack(concept);

		// Check case significance and acceptability
		Description description = concept.getDescriptions().iterator().next();
		assertEquals(Concepts.INITIAL_CHARACTER_CASE_INSENSITIVE, description.getCaseSignificanceId());
		assertEquals("PREFERRED", description.getAcceptabilityMap().get("900000000000509007"));
		Page<ReferenceSetMember> membersPage = referenceSetMemberService.findMembers(path, descriptionId, ComponentService.LARGE_PAGE);
		assertEquals(1L, membersPage.getTotalElements());

		// Update case significance
		description.setCaseSignificance("ENTIRE_TERM_CASE_SENSITIVE");
		String testPath = "MAIN/test";
		branchService.create(testPath);
		concept = conceptService.update(concept, testPath);
		concept = convertToJsonAndBack(concept);

		// Check case significance and acceptability
		description = concept.getDescriptions().iterator().next();
		assertEquals(Concepts.ENTIRE_TERM_CASE_SENSITIVE, description.getCaseSignificanceId());
		assertEquals("PREFERRED", description.getAcceptabilityMap().get("900000000000509007"));
		membersPage = referenceSetMemberService.findMembers(testPath, descriptionId, ComponentService.LARGE_PAGE);
		assertEquals(1L, membersPage.getTotalElements());

		// Update case significance
		description.setCaseSignificanceId(Concepts.CASE_INSENSITIVE);
		concept = conceptService.update(concept, testPath);
		concept = convertToJsonAndBack(concept);

		// Check case significance and acceptability
		description = concept.getDescriptions().iterator().next();
		assertEquals(Concepts.CASE_INSENSITIVE, description.getCaseSignificanceId());
		assertEquals("PREFERRED", description.getAcceptabilityMap().get("900000000000509007"));
		membersPage = referenceSetMemberService.findMembers(testPath, descriptionId, ComponentService.LARGE_PAGE);
		assertEquals(1L, membersPage.getTotalElements());
	}

	private Concept convertToJsonAndBack(Concept concept) throws IOException {
		String conceptJson = objectMapper.writerWithView(View.Component.class).writeValueAsString(concept);
		concept = objectMapper.readerWithView(View.Component.class).forType(Concept.class).readValue(conceptJson);
		return concept;
	}

	@Test
	public void testInactivateDescriptionAcceptability() throws ServiceException {
		final Concept concept = new Concept("50960005", 20020131, true, "900000000000207008", "900000000000074008");
		// Add acceptability with released refset member
		concept.addDescription(
				new Description("84923010", 20020131, true, "900000000000207008", "50960005", "en", "900000000000013009", "Bleeding", "900000000000020002")
						.addLanguageRefsetMember("900000000000509007", Concepts.PREFERRED)
		);
		conceptService.create(concept, "MAIN");
		releaseService.createVersion(20170731, "MAIN");

		// Check acceptability
		final Concept savedConcept1 = conceptService.find("50960005", "MAIN");
		final Description description1 = savedConcept1.getDescriptions().iterator().next();
		final Map<String, ReferenceSetMember> members1 = description1.getLangRefsetMembers();
		assertEquals(Concepts.PREFERRED, members1.get("900000000000509007").getAdditionalField("acceptabilityId"));
		assertTrue(members1.get("900000000000509007").isReleased());
		assertTrue(members1.get("900000000000509007").isActive());
		assertNotNull(members1.get("900000000000509007").getEffectiveTime());

		assertEquals(1, description1.getAcceptabilityMap().size());

		// Remove acceptability in next request
		members1.clear();
		conceptService.update(savedConcept1, "MAIN");

		// Check acceptability is inactive
		logger.info("Loading updated concept");
		final Concept savedConcept2 = conceptService.find("50960005", "MAIN");
		final Description description2 = savedConcept2.getDescriptions().iterator().next();
		final Map<String, ReferenceSetMember> members2 = description2.getLangRefsetMembers();
		assertEquals(1, members2.size());
		assertFalse(members2.get("900000000000509007").isActive());
		assertNull(members2.get("900000000000509007").getEffectiveTime());

		// Check that acceptability map is empty
		assertEquals(0, description2.getAcceptabilityMap().size());
	}

	@Test
	public void testLatestVersionMatch() throws ServiceException {
		testUtil.createConceptWithPathIdAndTerms("MAIN", "100001", "Heart");

		assertEquals(1, descriptionService.findDescriptionsWithAggregations("MAIN", "Heart", ServiceTestUtil.PAGE_REQUEST).getNumberOfElements());
		assertEquals(0, descriptionService.findDescriptionsWithAggregations("MAIN", "Bone", ServiceTestUtil.PAGE_REQUEST).getNumberOfElements());

		// Create branch (base point is now)
		branchService.create("MAIN/A");

		// Make further changes ahead of A's base point on MAIN
		final Concept concept = conceptService.find("100001", "MAIN");
		concept.getDescriptions().iterator().next().setTerm("Bone");
		conceptService.update(concept, "MAIN");

		assertEquals(0, descriptionService.findDescriptionsWithAggregations("MAIN", "Heart", ServiceTestUtil.PAGE_REQUEST).getNumberOfElements());
		assertEquals(1, descriptionService.findDescriptionsWithAggregations("MAIN", "Bone", ServiceTestUtil.PAGE_REQUEST).getNumberOfElements());

		printAllDescriptions("MAIN");
		printAllDescriptions("MAIN/A");

		assertEquals("Branch A should see old version of concept because of old base point.", 1, descriptionService.findDescriptionsWithAggregations("MAIN/A", "Heart", ServiceTestUtil.PAGE_REQUEST).getNumberOfElements());
		assertEquals("Branch A should not see new version of concept because of old base point.", 0, descriptionService.findDescriptionsWithAggregations("MAIN/A", "Bone", ServiceTestUtil.PAGE_REQUEST).getNumberOfElements());

		final Concept concept1 = conceptService.find("100001", "MAIN");
		assertEquals(1, concept1.getDescriptions().size());
	}

	@Test
	public void testRestoreEffectiveTime() throws ServiceException {
		final Integer effectiveTime = 20170131;
		final String conceptId = "50960005";
		final String originalModuleId = "900000000000207008";
		final String path = "MAIN";

		// Create concept
		final Concept concept = new Concept(conceptId, null, true, originalModuleId, "900000000000074008")
				.addDescription(new Description("10000123", null, true, originalModuleId, conceptId, "en",
						Concepts.FSN, "Pizza", Concepts.CASE_INSENSITIVE).addLanguageRefsetMember(Concepts.GB_EN_LANG_REFSET, Concepts.PREFERRED));
		conceptService.create(concept, path);

		// Run release process
		releaseService.createVersion(effectiveTime, path);

		// Check that release process applied correctly
		final Concept savedConcept = conceptService.find(conceptId, path);
		assertEquals(effectiveTime, savedConcept.getEffectiveTimeI());
		assertEquals(effectiveTime, savedConcept.getReleasedEffectiveTime());
		assertEquals("true|900000000000207008|900000000000074008", savedConcept.getReleaseHash());
		Assert.assertTrue(savedConcept.isReleased());

		Description savedDescription = savedConcept.getDescriptions().iterator().next();
		assertEquals(effectiveTime, savedDescription.getEffectiveTimeI());
		assertEquals(effectiveTime, savedDescription.getReleasedEffectiveTime());
		assertEquals("true|Pizza|900000000000207008|en|900000000000003001|900000000000448009", savedDescription.getReleaseHash());

		ReferenceSetMember savedMember = savedDescription.getLangRefsetMembers().values().iterator().next();
		assertEquals(effectiveTime, savedMember.getEffectiveTimeI());
		assertEquals(effectiveTime, savedMember.getReleasedEffectiveTime());
		assertEquals("true|900000000000207008|acceptabilityId|900000000000548007", savedMember.getReleaseHash());

		// Change concept, description and member
		savedConcept.setModuleId("10000123");
		savedDescription.setCaseSignificanceId(Concepts.ENTIRE_TERM_CASE_SENSITIVE);
		savedMember.setAdditionalField(ReferenceSetMember.LanguageFields.ACCEPTABILITY_ID, Concepts.ACCEPTABLE);
		conceptService.update(savedConcept, "MAIN");

		// effectiveTimes cleared
		final Concept conceptAfterUpdate = conceptService.find(conceptId, path);
		Assert.assertNull(conceptAfterUpdate.getEffectiveTimeI());
		assertEquals(effectiveTime, conceptAfterUpdate.getReleasedEffectiveTime());
		Assert.assertTrue(conceptAfterUpdate.isReleased());
		Description descriptionAfterUpdate = conceptAfterUpdate.getDescriptions().iterator().next();
		Assert.assertNull(descriptionAfterUpdate.getEffectiveTimeI());
		Assert.assertNull(descriptionAfterUpdate.getLangRefsetMembers().values().iterator().next().getEffectiveTimeI());

		// Change concept back
		conceptAfterUpdate.setModuleId(originalModuleId);
		conceptService.update(conceptAfterUpdate, "MAIN");

		// Concept effectiveTime restored
		Concept conceptWithRestoredDate = conceptService.find(conceptId, path);
		assertEquals(effectiveTime, conceptWithRestoredDate.getEffectiveTimeI());
		assertEquals(effectiveTime, conceptWithRestoredDate.getReleasedEffectiveTime());
		Assert.assertTrue(conceptWithRestoredDate.isReleased());

		// Change description back
		conceptWithRestoredDate.getDescriptions().iterator().next().setCaseSignificanceId(CASE_INSENSITIVE);
		conceptService.update(conceptWithRestoredDate, "MAIN");

		// Description effectiveTime restored
		conceptWithRestoredDate = conceptService.find(conceptId, path);
		assertEquals(effectiveTime, conceptWithRestoredDate.getDescriptions().iterator().next().getEffectiveTimeI());

		// Change lang member back
		ReferenceSetMember member = conceptWithRestoredDate.getDescriptions().iterator().next().getLangRefsetMembers().values().iterator().next();
		member.setAdditionalField(ReferenceSetMember.LanguageFields.ACCEPTABILITY_ID, Concepts.PREFERRED);
		conceptService.update(conceptWithRestoredDate, "MAIN");

		// Lang member effectiveTime restored
		conceptWithRestoredDate = conceptService.find(conceptId, path);
		ReferenceSetMember memberWithRestoredDate = conceptWithRestoredDate.getDescriptions().iterator().next().getLangRefsetMembers().values().iterator().next();
		assertEquals(effectiveTime, memberWithRestoredDate.getEffectiveTimeI());
	}

	// Uncomment to run - takes around 45 seconds.
//	@Test
	public void testCreateUpdate10KConcepts() throws ServiceException {
		branchService.create("MAIN/A");
		conceptService.create(new Concept(SNOMEDCT_ROOT), "MAIN/A");

		List<Concept> concepts = new ArrayList<>();
		final int tenThousand = 10 * 1000;
		for (int i = 0; i < tenThousand; i++) {
			concepts.add(
					new Concept(null, Concepts.CORE_MODULE)
							.addDescription(new Description("Concept " + i))
							.addDescription(new Description("Concept " + i + "(finding)"))
							.addRelationship(new Relationship(Concepts.ISA, SNOMEDCT_ROOT))
			);
		}

		final Iterable<Concept> conceptsCreated = conceptService.batchCreate(concepts, "MAIN/A");

		final Page<Concept> page = conceptService.findAll("MAIN/A", PageRequest.of(0, 100));
		assertEquals(concepts.size() + 1, page.getTotalElements());
		assertEquals(Concepts.CORE_MODULE, page.getContent().get(50).getModuleId());

		Page<ConceptMini> conceptDescendants = queryService.findDescendantsAsConceptMinis(SNOMEDCT_ROOT, "MAIN/A", Relationship.CharacteristicType.stated, PageRequest.of(0, 50));
		assertEquals(10 * 1000, conceptDescendants.getTotalElements());

		List<Relationship> inboundRelationships = relationshipService.findInboundRelationships(SNOMEDCT_ROOT, "MAIN/A", Relationship.CharacteristicType.stated).getContent();
		assertEquals(10 * 1000, inboundRelationships.size());

		final String anotherModule = "123123";
		List<Concept> toUpdate = new ArrayList<>();
		conceptsCreated.forEach(concept -> {
			concept.setModuleId(anotherModule);
			toUpdate.add(concept);
		});

		conceptService.createUpdate(toUpdate, "MAIN/A");

		final Page<Concept> pageAfterUpdate = conceptService.findAll("MAIN/A", PageRequest.of(0, 100));
		assertEquals(tenThousand + 1, pageAfterUpdate.getTotalElements());
		Concept someConcept = pageAfterUpdate.getContent().get(50);
		if (someConcept.getId().equals(SNOMEDCT_ROOT)) {
			someConcept = pageAfterUpdate.getContent().get(51);
		}
		assertEquals(anotherModule, someConcept.getModuleId());
		assertEquals(1, someConcept.getRelationships().size());

		// Move all concepts in hierarchy
		conceptService.create(new Concept(Concepts.CLINICAL_FINDING).addRelationship(new Relationship(ISA, SNOMEDCT_ROOT)), "MAIN/A");
		concepts.forEach(c -> {
			c.getRelationships().iterator().next().setActive(false);
			c.addRelationship(new Relationship(ISA, Concepts.CLINICAL_FINDING));
		});
		conceptService.createUpdate(concepts, "MAIN/A");
	}

	private void printAllDescriptions(String path) {
		final Page<Description> descriptions = descriptionService.findDescriptionsWithAggregations(path, null, ServiceTestUtil.PAGE_REQUEST);
		logger.info("Description on " + path);
		for (Description description : descriptions) {
			logger.info("{}", description);
		}
	}

	private Description fsn(String term) {
		Description description = new Description(term);
		description.setTypeId(FSN);
		return description;
	}

}
