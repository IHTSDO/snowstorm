package org.snomed.snowstorm.core.data.services;

import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.api.ComponentService;
import io.kaicode.elasticvc.domain.Branch;
import io.kaicode.elasticvc.domain.Commit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.config.Config;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.data.services.pojo.AsyncConceptChangeBatch;
import org.snomed.snowstorm.core.data.services.pojo.DescriptionCriteria;
import org.snomed.snowstorm.core.data.services.pojo.MemberSearchRequest;
import org.snomed.snowstorm.core.pojo.BranchTimepoint;
import org.snomed.snowstorm.core.pojo.LanguageDialect;
import org.snomed.snowstorm.ecl.ECLQueryService;
import org.snomed.snowstorm.rest.View;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static io.kaicode.elasticvc.api.ComponentService.LARGE_PAGE;
import static io.kaicode.elasticvc.helper.QueryHelper.termQuery;
import static org.junit.jupiter.api.Assertions.*;
import static org.snomed.snowstorm.config.Config.DEFAULT_LANGUAGE_DIALECTS;
import static org.snomed.snowstorm.core.data.domain.Concepts.*;

class ConceptServiceTest extends AbstractTest {

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

	@Autowired
	private BranchMergeService branchMergeService;

	@Autowired
	private CodeSystemService codeSystemService;

	@Autowired
	private ElasticsearchOperations elasticsearchOperations;

	@Autowired
	private ECLQueryService eclQueryService;

	private ServiceTestUtil testUtil;

	private final Logger logger = LoggerFactory.getLogger(getClass());
	private ObjectMapper objectMapper;
	private CodeSystem codeSystem;

	@BeforeEach
	void setup() {
		testUtil = new ServiceTestUtil(conceptService);
		objectMapper = new ObjectMapper();
		DeserializationConfig deserializationConfig = objectMapper.getDeserializationConfig().without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
		objectMapper.setConfig(deserializationConfig);
		codeSystem = new CodeSystem("SNOMEDCT", "MAIN");
		codeSystemService.createCodeSystem(codeSystem);
	}

	@Test
	void testConceptCreationBranchingVisibility() throws ServiceException {
		assertNull(conceptService.find("100001", "MAIN"), "Concept 100001 does not exist on MAIN.");

		conceptService.create(new Concept("100001", "10000111"), "MAIN");

		final Concept c1 = conceptService.find("100001", "MAIN");
		assertNotNull(c1, "Concept 100001 exists on MAIN.");
		assertEquals("MAIN", c1.getPath());
		assertEquals("10000111", c1.getModuleId());

		branchService.create("MAIN/A");
		conceptService.create(new Concept("100002", "10000222"), "MAIN/A");
		assertNull(conceptService.find("100002", "MAIN"), "Concept 100002 does not exist on MAIN.");
		assertNotNull(conceptService.find("100002", "MAIN/A"), "Concept 100002 exists on branch A.");
		assertNotNull(conceptService.find("100001", "MAIN/A"), "Concept 100001 is accessible on branch A because of the base time.");

		conceptService.create(new Concept("100003", "10000333"), "MAIN");
		assertNull(conceptService.find("100003", "MAIN/A"), "Concept 100003 is not accessible on branch A because created after branching.");
		assertNotNull(conceptService.find("100003", "MAIN"));
	}

	@Test
	void testDeleteDescription() throws ServiceException {
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
	void testDeleteLangMembersDuringDescriptionDeletion() throws ServiceException {
		Concept concept = new Concept("10000123");
		Description fsn = fsn("Is a (attribute)");
		conceptService.create(concept
				.addDescription(
						fsn
								.addLanguageRefsetMember(Concepts.GB_EN_LANG_REFSET, Concepts.PREFERRED)
								.addLanguageRefsetMember(Concepts.US_EN_LANG_REFSET, Concepts.PREFERRED)

				), "MAIN");
		String descriptionId = fsn.getDescriptionId();
		assertNotNull(descriptionId);

		List<ReferenceSetMember> acceptabilityMembers = referenceSetMemberService.findMembers("MAIN", descriptionId, PageRequest.of(0, 10)).getContent();
		assertEquals(2, acceptabilityMembers.size());
		assertTrue(acceptabilityMembers.get(0).isActive());
		assertTrue(acceptabilityMembers.get(1).isActive());

		concept.getDescriptions().clear();

		conceptService.update(concept, "MAIN");

		List<ReferenceSetMember> acceptabilityMembersAfterDescriptionDeletion = referenceSetMemberService.findMembers("MAIN", descriptionId, PageRequest.of(0, 10)).getContent();
		assertEquals(0, acceptabilityMembersAfterDescriptionDeletion.size());
	}

	@Test
	void testDescriptionInactivation() throws ServiceException {
		Concept concept = new Concept("10000123");
		Description fsn = fsn("Is a (attribute)");
		conceptService.create(concept
				.addDescription(
						fsn
								.addLanguageRefsetMember(Concepts.GB_EN_LANG_REFSET, Concepts.PREFERRED)
								.addLanguageRefsetMember(Concepts.US_EN_LANG_REFSET, Concepts.PREFERRED)

				), "MAIN");
		String descriptionId = fsn.getDescriptionId();
		assertNotNull(descriptionId);

		List<ReferenceSetMember> acceptabilityMembers = referenceSetMemberService.findMembers("MAIN", descriptionId, PageRequest.of(0, 10)).getContent();
		assertEquals(2, acceptabilityMembers.size());
		assertTrue(acceptabilityMembers.get(0).isActive());
		assertTrue(acceptabilityMembers.get(1).isActive());

		Description descriptionToInactivate = concept.getDescriptions().iterator().next();
		descriptionToInactivate.setActive(false);
		descriptionToInactivate.setInactivationIndicator(Concepts.inactivationIndicatorNames.get(Concepts.OUTDATED));
		Map<String, Set<String>> associationTargets = new HashMap<>();
		associationTargets.put("REFERS_TO", Collections.singleton("321667001"));
		descriptionToInactivate.setAssociationTargets(associationTargets);

		conceptService.update(concept, "MAIN");
		Concept updated = conceptService.find(concept.getConceptId(), "MAIN");
		assertEquals(1, updated.getDescriptions().size());
		Description updatedDescription = updated.getDescriptions().iterator().next();
		assertFalse(updatedDescription.isActive());
		assertEquals(Concepts.inactivationIndicatorNames.get(Concepts.OUTDATED), updatedDescription.getInactivationIndicator());
		ReferenceSetMember inactivationIndicatorMember = updatedDescription.getInactivationIndicatorMember();
		assertNotNull(inactivationIndicatorMember);
		assertEquals(Concepts.DESCRIPTION_INACTIVATION_INDICATOR_REFERENCE_SET, inactivationIndicatorMember.getRefsetId());
		assertEquals(updatedDescription.getDescriptionId(), inactivationIndicatorMember.getReferencedComponentId());
		assertEquals(Concepts.OUTDATED, inactivationIndicatorMember.getAdditionalField("valueId"));
		Collection<ReferenceSetMember> associationTargetMembers = updatedDescription.getAssociationTargetMembers();
		assertNotNull(associationTargetMembers);
		assertEquals(1, associationTargetMembers.size());
		ReferenceSetMember associationTargetMember = associationTargetMembers.iterator().next();
		assertEquals(Concepts.REFSET_REFERS_TO_ASSOCIATION, associationTargetMember.getRefsetId());
		assertTrue(associationTargetMember.isActive());
		assertEquals(descriptionToInactivate.getDescriptionId(), associationTargetMember.getReferencedComponentId());
		assertEquals("321667001", associationTargetMember.getAdditionalField("targetComponentId"));

		List<ReferenceSetMember> membersAfterDescriptionInactivation = referenceSetMemberService.findMembers("MAIN", descriptionId, PageRequest.of(0, 10)).getContent();
		assertEquals(2, membersAfterDescriptionInactivation.size());
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
	void testCreateDeleteConcept() throws ServiceException {
		String path = "MAIN";
		conceptService.create(new Concept(ISA).setDefinitionStatusId(PRIMITIVE).addDescription(fsn("Is a (attribute)")), path);
		conceptService.create(new Concept(SNOMEDCT_ROOT).setDefinitionStatusId(PRIMITIVE).addDescription(fsn("SNOMED CT Concept")), path);

		String conceptId = "100001";

		Annotation annotation = new Annotation();
		annotation.setModuleId("900000000000207008");
		annotation.setTypeId("123456");
		annotation.setValue("Annotation with language");
		annotation.setLanguageDialectCode("en");

		conceptService.create(new Concept(conceptId).addAxiom(new Relationship(ISA, SNOMEDCT_ROOT)).addAnnotation(annotation), path);

		assertEquals(2, referenceSetMemberService.findMembers(path, conceptId, ComponentService.LARGE_PAGE).getTotalElements());

		conceptService.deleteConceptAndComponents(conceptId, path, false);

		assertEquals(0, referenceSetMemberService.findMembers(path, conceptId, ComponentService.LARGE_PAGE).getTotalElements());
	}

	@Test
	void testCreateDeleteRelationship() throws ServiceException {
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
		assertEquals("Is a (attribute)", createdRelationship.type().getFsnTerm(), "Creation response should contain FSN within relationship type");
		assertEquals("PRIMITIVE", createdRelationship.type().getDefinitionStatus(), "Creation response should contain definition status within relationship type");
		assertEquals("SNOMED CT Concept", createdRelationship.target().getFsnTerm(), "Creation response should contain FSN within relationship target");
		assertEquals("PRIMITIVE", createdRelationship.target().getDefinitionStatus(), "Creation response should contain definition status within relationship target");

		assertEquals(3, concept.getRelationships().size());
		Concept foundConcept = conceptService.find("100001", "MAIN");
		assertEquals(3, foundConcept.getRelationships().size());

		Relationship foundRelationship = foundConcept.getRelationships().iterator().next();
		assertEquals("Is a (attribute)", foundRelationship.type().getFsnTerm(), "Find response should contain FSN within relationship type");
		assertEquals("PRIMITIVE", foundRelationship.type().getDefinitionStatus(), "Find response should contain definition status within relationship type");
		assertEquals("SNOMED CT Concept", foundRelationship.target().getFsnTerm(), "Find response should contain FSN within relationship target");
		assertEquals("PRIMITIVE", foundRelationship.target().getDefinitionStatus(), "Find response should contain definition status within relationship target");

		concept.getRelationships().remove(new Relationship("100003"));
		final Concept updatedConcept = conceptService.update(concept, "MAIN");

		assertEquals(2, updatedConcept.getRelationships().size());
		assertEquals(2, conceptService.find("100001", "MAIN").getRelationships().size());

		Relationship updatedRelationship = foundConcept.getRelationships().iterator().next();
		assertEquals("Is a (attribute)", updatedRelationship.type().getFsnTerm(), "Update response should contain FSN within relationship type");
		assertEquals("PRIMITIVE", updatedRelationship.type().getDefinitionStatus(), "Update response should contain definition status within relationship type");
		assertEquals("SNOMED CT Concept", updatedRelationship.target().getFsnTerm(), "Update response should contain FSN within relationship target");
		assertEquals("PRIMITIVE", updatedRelationship.target().getDefinitionStatus(), "Update response should contain definition status within relationship target");
	}

	@Test
	void testMultipleConceptVersionsOnOneBranch() throws ServiceException {
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
	void testUpdateExistingConceptOnNewBranch() throws ServiceException {
		conceptService.create(new Concept("100001", "10000111"), "MAIN");

		branchService.create("MAIN/A");

		conceptService.update(new Concept("100001", "10000222"), "MAIN/A");

		assertEquals("10000111", conceptService.find("100001", "MAIN").getModuleId());
		assertEquals("10000222", conceptService.find("100001", "MAIN/A").getModuleId());
	}

	@Test
	void testUpdateBatchOfReleasedConceptsOnNewBranch() throws ServiceException {
		List<Concept> batch = Lists.newArrayList(
				new Concept("100001", "10000111"),
				new Concept("100002", "10000111"),
				new Concept("100003", "10000111"),
				new Concept("100004", "10000111"),
				new Concept("100005", "10000111"),
				new Concept("100006", "10000111"),
				new Concept("100007", "10000111"),
				new Concept("100008", "10000111"),
				new Concept("100009", "10000111"),
				new Concept("1000010", "10000111")
		);

		conceptService.createUpdate(batch, "MAIN");

		codeSystemService.createVersion(codeSystem, 20210131, "Jan");

		branchService.create("MAIN/A");
		final String task = "MAIN/A/A-1";
		branchService.create(task);

		final String newModuleId = "10000222";
		batch.forEach(concept -> {
			concept.setModuleId(newModuleId);
			concept.clearReleaseDetails();
		});
		batch.stream().limit(5).forEach(concept -> concept.setActive(false));

		conceptService.createUpdate(batch, task);
		final Page<Concept> all = conceptService.findAll(task, ComponentService.LARGE_PAGE);
		final List<Concept> allAfterUpdate = all.getContent();
		assertEquals(10, allAfterUpdate.size());
		for (Concept concept : allAfterUpdate) {
			assertEquals(newModuleId, concept.getModuleId());
			assertTrue(concept.isReleased());
		}
	}

	@Test
	void testOnlyUpdateWhatChanged() throws ServiceException {
		final Integer effectiveTime = 20160731;

		conceptService.create(new Concept("100001", effectiveTime, true, Concepts.CORE_MODULE, Concepts.PRIMITIVE)
				.addDescription(new Description("1000011", effectiveTime, true, Concepts.CORE_MODULE, null, "en",
						Concepts.FSN, "My Concept (finding)", Concepts.INITIAL_CHARACTER_CASE_INSENSITIVE))
				.addDescription(new Description("1000012", effectiveTime, true, Concepts.CORE_MODULE, null, "en",
						Concepts.SYNONYM, "My Concept", Concepts.INITIAL_CHARACTER_CASE_INSENSITIVE)),
				"MAIN");

		final Concept conceptAfterSave = conceptService.find("100001", "MAIN");

		Description fsn = conceptAfterSave.getDescription("1000011");
		fsn.setActive(false);
		String fsnInternalId = fsn.getInternalId();
		conceptService.update(conceptAfterSave, "MAIN");
		final Concept conceptAfterUpdate = conceptService.find("100001", "MAIN");

		assertEquals(conceptAfterSave.getInternalId(), conceptAfterUpdate.getInternalId(),
				"Concept document should not have been updated.");
		assertEquals(conceptAfterSave.getDescription("1000012").getInternalId(), conceptAfterUpdate.getDescription("1000012").getInternalId(),
				"Synonym document should not have been updated.");
		assertNotEquals(fsnInternalId, conceptAfterUpdate.getDescription("1000011").getInternalId(),
				"FSN document should have been updated.");
	}

	@Test
	void testFindConceptOnParentBranchUsingBaseVersion() throws ServiceException {
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
	void testListConceptsOnGrandchildBranchWithUpdateOnChildBranch() throws ServiceException {
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
	void testGetExistingConceptsForSaveDuringRebaseCommit() throws ServiceException {
		conceptService.create(new Concept("100001"), "MAIN");
		conceptService.create(new Concept("100002"), "MAIN");
		conceptService.create(new Concept("100003"), "MAIN");

		branchService.create("MAIN/A");
		branchService.create("MAIN/A/A1");
		branchService.create("MAIN/A/A2");

		// Create a new version on MAIN/A/A1
		conceptService.update(new Concept("100003", "10000111"), "MAIN/A/A1");

		// Create a new version on MAIN/A/A2 and promote to project
		conceptService.update(new Concept("100003", "10000222"), "MAIN/A/A2");
		branchMergeService.mergeBranchSync("MAIN/A/A2", "MAIN/A", Collections.singletonList(conceptService.find("100003", "MAIN/A/A2")));

		final List<Long> conceptIds = Arrays.asList(100003L, 100001L, 100002L);
		final Page<Concept> conceptsOnProject = conceptService.find(conceptIds, DEFAULT_LANGUAGE_DIALECTS, "MAIN/A", ServiceTestUtil.PAGE_REQUEST);
		assertEquals(conceptIds.size(), conceptsOnProject.getTotalElements());
		assertEquals(conceptIds.size(), conceptsOnProject.get().collect(Collectors.toSet()).size());

		final Page<Concept> conceptsOnTaskA1 = conceptService.find(conceptIds, DEFAULT_LANGUAGE_DIALECTS, "MAIN/A/A1", ServiceTestUtil.PAGE_REQUEST);
		assertEquals(conceptIds.size(), conceptsOnTaskA1.getTotalElements());
		Collection<Concept> concepts = conceptsOnTaskA1.get().collect(Collectors.toSet());
		assertEquals(conceptIds.size(), concepts.size());

		// Rebase commit updates existing target branch(task A1) base timestamp to the latest source branch(project A) head timestamp
		// Because of this when searching during rebase commit, there are multiple version of documents for concept 100003.
		// One is from project (newly promoted from task A2) and the other is from task A1
		// This can cause concepts to be missing due to a bug found in getExistingConceptsForSave method.
		Commit commit = branchService.openRebaseCommit("MAIN/A/A1", "Rebase task A1 with project A");
		Map<String, Concept> conceptMap = conceptService.getExistingConceptsForSave(concepts, commit);
		assertEquals(conceptIds.size(), conceptMap.keySet().size());
	}


	@Test
	void testSaveConceptWithDescription() throws ServiceException {
		final Concept concept = new Concept("50960005", 20020131, true, "900000000000207008", "900000000000074008");
		concept.addDescription(new Description("84923010", 20020131, true, "900000000000207008", "50960005", "en", FSN,
				"Bleeding (morphologic abnormality)", "900000000000020002").addLanguageRefsetMember(US_EN_LANG_REFSET, PREFERRED));
		Concept savedConcept = conceptService.create(concept, DEFAULT_LANGUAGE_DIALECTS, "MAIN");
		assertEquals("Bleeding (morphologic abnormality)", savedConcept.getFsn().getTerm());

		savedConcept = conceptService.find("50960005", "MAIN");
		assertNotNull(savedConcept);
		assertEquals("Bleeding (morphologic abnormality)", savedConcept.getFsn().getTerm());
		assertEquals(1, savedConcept.getDescriptions().size());
		Description description = savedConcept.getDescriptions().iterator().next();
		assertEquals("84923010", description.getDescriptionId());
		assertEquals(1, description.getAcceptabilityMapFromLangRefsetMembers().size());

		description.clearLanguageRefsetMembers();
		Map<String, String> acceptabilityMap =  new HashMap<>();
		acceptabilityMap.put(US_EN_LANG_REFSET, PREFERRED_CONSTANT);
		description.setAcceptabilityMap(acceptabilityMap);

		Concept updatedConcept = conceptService.update(savedConcept, DEFAULT_LANGUAGE_DIALECTS, "MAIN");
		assertEquals("Bleeding (morphologic abnormality)", updatedConcept.getFsn().getTerm());
		assertEquals(1, updatedConcept.getDescriptions().size());
		description = updatedConcept.getDescriptions().iterator().next();
		assertEquals("84923010", description.getDescriptionId());
		assertEquals(1, description.getAcceptabilityMapFromLangRefsetMembers().size());
	}

	@Test
	void testSaveConceptWithAnnotation() throws ServiceException {
		final Concept concept = new Concept("50960005", 20020131, true, "900000000000207008", "900000000000074008");
		concept.addDescription(new Description("84923010", 20020131, true, "900000000000207008", "50960005", "en", FSN,
				"Bleeding (morphologic abnormality)", "900000000000020002").addLanguageRefsetMember(US_EN_LANG_REFSET, PREFERRED));
		Annotation annotation1 = new Annotation();
		annotation1.setModuleId("900000000000207008");
		annotation1.setTypeId("123456");
		annotation1.setValue("Annotation with language");
		annotation1.setLanguageDialectCode("en");

		Annotation annotation2 = new Annotation();
		annotation2.setModuleId("900000000000207008");
		annotation2.setTypeId("123456789");
		annotation2.setValue("Annotation without language");

		concept.addAnnotation(annotation1);
		concept.addAnnotation(annotation2);
		conceptService.create(concept, DEFAULT_LANGUAGE_DIALECTS, "MAIN");

		Concept savedConcept = conceptService.find("50960005", "MAIN");
		assertEquals(2, savedConcept.getAnnotations().size());
		for (Annotation annotation : savedConcept.getAnnotations()) {
			assertEquals(Concepts.ANNOTATION_REFERENCE_SET, annotation.getRefsetId());
			if (annotation.getLanguageDialectCode() != null) {
				assertEquals("123456", annotation.getTypeId());
				assertEquals("en", annotation.getLanguageDialectCode());
				assertEquals("Annotation with language", annotation.getValue());
			} else {
				assertEquals("123456789", annotation.getTypeId());
				assertEquals("Annotation without language", annotation.getValue());
			}
		}
	}

	@Test
	void testSaveConceptWithAxioms() throws ServiceException {
		String path = "MAIN";
		final Concept concept = new Concept("50960005", 20020131, true, Concepts.CORE_MODULE, "900000000000074008");
		concept.addAxiom(new Axiom(null, Concepts.FULLY_DEFINED, Sets.newHashSet(new Relationship(Concepts.ISA, "10000100"), new Relationship("10000200", "10000300"))).setModuleId(CORE_MODULE));
		concept.addGeneralConceptInclusionAxiom(new Axiom(null, Concepts.PRIMITIVE, Sets.newHashSet(new Relationship(Concepts.ISA, "10000500"), new Relationship("10000600", "10000700"))).setModuleId(CORE_MODULE));
		conceptService.create(concept, path);
		assertEquals(1, conceptService.find(concept.getConceptId(), path).getClassAxioms().size());

		final Concept savedConcept = conceptService.find("50960005", path);
		assertNotNull(savedConcept);
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
		assertEquals(axiom.getDefinitionStatusId(), savedConcept.getDefinitionStatusId(),
				"Concept and class axiom should have the same definition status");
		assertEquals(Concepts.CORE_MODULE, savedConcept.getModuleId());

		Page<ReferenceSetMember> members = referenceSetMemberService.findMembers(path,
				new MemberSearchRequest().active(true).referenceSet(Concepts.OWL_AXIOM_REFERENCE_SET).referencedComponentId(savedConcept.getConceptId()), PageRequest.of(0, 10));
		assertEquals(2, members.getTotalElements());
		String axiomId = axiom.getAxiomId();
		ReferenceSetMember referenceSetMember = members.getContent().stream().filter(member -> member.getMemberId().equals(axiomId)).collect(Collectors.toList()).get(0);
		assertEquals("EquivalentClasses(:50960005 ObjectIntersectionOf(:10000100 ObjectSomeValuesFrom(:609096000 ObjectSomeValuesFrom(:10000200 :10000300))))",
				referenceSetMember.getAdditionalField(ReferenceSetMember.OwlExpressionFields.OWL_EXPRESSION));
		String memberId = referenceSetMember.getMemberId();

		Concept updatedConcept = conceptService.update(concept, path);
		assertEquals(1, conceptService.find(concept.getConceptId(), path).getClassAxioms().size());
		axiom = updatedConcept.getClassAxioms().iterator().next();
		assertEquals(memberId, axiom.getReferenceSetMember().getMemberId(),
				"Member id should be kept after update if no changes to OWL expression.");

		String axiomMemberInternalId = axiom.getReferenceSetMember().getInternalId();
		updatedConcept = conceptService.update(concept, path);
		axiom = updatedConcept.getClassAxioms().iterator().next();
		assertEquals(axiomMemberInternalId, axiom.getReferenceSetMember().getInternalId(),
				"A new state of the axiom member should not be created if there are no changes.");

		axiom.setDefinitionStatusId(Concepts.PRIMITIVE);
		updatedConcept = conceptService.update(concept, path);
		axiom = updatedConcept.getClassAxioms().iterator().next();
		assertEquals(memberId, axiom.getReferenceSetMember().getMemberId(),
				"Member id should not be changed after changing the OWL expression.");
		assertEquals("SubClassOf(:50960005 ObjectIntersectionOf(:10000100 ObjectSomeValuesFrom(:609096000 ObjectSomeValuesFrom(:10000200 :10000300))))",
				axiom.getReferenceSetMember().getAdditionalField(ReferenceSetMember.OwlExpressionFields.OWL_EXPRESSION));
		assertEquals(axiom.getDefinitionStatusId(), updatedConcept.getDefinitionStatusId(),
				"Concept and class axiom should have the same definition status");

		concept.getClassAxioms().clear();
		concept.getGciAxioms().clear();
		conceptService.update(concept, path);
		assertEquals(0, referenceSetMemberService.findMembers(path,
				new MemberSearchRequest().active(true).referenceSet(Concepts.OWL_AXIOM_REFERENCE_SET).referencedComponentId(savedConcept.getConceptId()), PageRequest.of(0, 10)).getTotalElements());
	}

	@Test
	void testGciWithOneRelationshipError() throws ServiceException {
		try {
			conceptService.create(new Concept()
							.addAxiom(new Relationship(ISA, CLINICAL_FINDING))
							.addGeneralConceptInclusionAxiom(new Relationship(ISA, "131148009"))
					, "MAIN");
			fail("IllegalArgumentException should have been thrown.");
		} catch (ServiceException e) {
			fail("IllegalArgumentException should have been thrown.");
		} catch (IllegalArgumentException e) {
			assertEquals("The relationships of a GCI axiom must include at least one parent and one attribute.", e.getMessage());
		}

		try {
			conceptService.create(new Concept()
							.addAxiom(new Relationship(ISA, CLINICAL_FINDING))
							.addGeneralConceptInclusionAxiom(new Relationship(ISA, "131148009"), new Relationship(ISA, CLINICAL_FINDING))
					, "MAIN");
			fail("IllegalArgumentException should have been thrown.");
		} catch (ServiceException e) {
			fail("IllegalArgumentException should have been thrown.");
		} catch (IllegalArgumentException e) {
			assertEquals("The relationships of a GCI axiom must include at least one parent and one attribute.", e.getMessage());
		}

		conceptService.create(new Concept()
						.addAxiom(new Relationship(ISA, CLINICAL_FINDING))
						.addGeneralConceptInclusionAxiom(new Relationship(ISA, "131148009"), new Relationship(FINDING_SITE, HEART_STRUCTURE))
				, "MAIN");
	}

	@Test
	void testConceptInactivationThenVersionThenReasonChange() throws ServiceException {
		String path = "MAIN";
		conceptService.batchCreate(Lists.newArrayList(new Concept("107658001"), new Concept("116680003")), path);
		final Concept concept = new Concept("50960005", 20020131, true, "900000000000207008", "900000000000074008");
		concept.addDescription(new Description("84923010", 20020131, true, "900000000000207008", "50960005", "en", "900000000000013009", "Bleeding", "900000000000020002"));
		Description inactiveDescriptionCreate = new Description("Another");
		inactiveDescriptionCreate.setActive(false);
		concept.addDescription(inactiveDescriptionCreate);
		concept.addAxiom(new Relationship(ISA, "107658001"));
		Concept savedConcept = conceptService.create(concept, path);

		codeSystemService.createVersion(codeSystem, 20200131, "");

		// Add description after versioning and before making inactive
		savedConcept.addDescription(new Description("Unversioned description"));
		savedConcept = conceptService.update(savedConcept, path);

		// Make concept inactive
		savedConcept.setActive(false);

		// Set inactivation indicator using strings
		savedConcept.setInactivationIndicator(Concepts.inactivationIndicatorNames.get(Concepts.DUPLICATE));
		assertNull(savedConcept.getInactivationIndicatorMember());

		// Set association target using strings
		HashMap<String, Set<String>> associationTargetStrings = new HashMap<>();
		associationTargetStrings.put(Concepts.historicalAssociationNames.get(Concepts.REFSET_SAME_AS_ASSOCIATION), Collections.singleton("87100004"));
		savedConcept.setAssociationTargets(associationTargetStrings);
		assertTrue(savedConcept.getAssociationTargetMembers().isEmpty());

		Concept inactiveConcept = conceptService.update(savedConcept, path);
		Concept foundInactiveConcept = conceptService.find(savedConcept.getConceptId(), path);

		assertFalse(inactiveConcept.isActive());

		// Check inactivation indicator string
		assertEquals("DUPLICATE", inactiveConcept.getInactivationIndicator());
		assertEquals("DUPLICATE", foundInactiveConcept.getInactivationIndicator());

		// Check inactivation indicator reference set member was created
		ReferenceSetMember inactivationIndicatorMember = foundInactiveConcept.getInactivationIndicatorMember();
		assertNotNull(inactivationIndicatorMember);
		assertTrue(inactivationIndicatorMember.isActive());
		assertEquals(Concepts.CONCEPT_INACTIVATION_INDICATOR_REFERENCE_SET, inactivationIndicatorMember.getRefsetId());
		assertEquals(Concepts.DUPLICATE, inactivationIndicatorMember.getAdditionalField("valueId"));

		// Check association target strings
		Map<String, Set<String>> associationTargetsAfter = foundInactiveConcept.getAssociationTargets();
		assertNotNull(associationTargetsAfter);
		assertEquals(1, associationTargetsAfter.size());
		assertEquals(Collections.singleton("87100004"), associationTargetsAfter.get(Concepts.historicalAssociationNames.get(Concepts.REFSET_SAME_AS_ASSOCIATION)));

		// Check association target reference set member was created
		Collection<ReferenceSetMember> associationTargetMembers = foundInactiveConcept.getAssociationTargetMembers();
		assertNotNull(associationTargetMembers);
		assertEquals(1, associationTargetMembers.size());
		ReferenceSetMember associationTargetMember = associationTargetMembers.iterator().next();
		assertTrue(associationTargetMember.isActive());
		assertEquals(Concepts.REFSET_SAME_AS_ASSOCIATION, associationTargetMember.getRefsetId());
		assertEquals(concept.getModuleId(), associationTargetMember.getModuleId());
		assertEquals(concept.getId(), associationTargetMember.getReferencedComponentId());
		assertEquals("87100004", associationTargetMember.getAdditionalField("targetComponentId"));

		Set<Description> descriptions = inactiveConcept.getDescriptions();
		List<Description> activeDescriptions = descriptions.stream().filter(Description::isActive).sorted(Comparator.comparing(Description::getTerm)).collect(Collectors.toList());
		assertEquals(2, activeDescriptions.size(),
				"Two descriptions are still active");
		assertEquals("CONCEPT_NON_CURRENT", activeDescriptions.get(0).getInactivationIndicator(),
				"Active descriptions automatically have inactivation indicator");
		assertEquals("CONCEPT_NON_CURRENT", activeDescriptions.get(1).getInactivationIndicator(),
				"Active descriptions automatically have inactivation indicator");
		Description unversionedDescription = activeDescriptions.get(1);
		assertEquals("Unversioned description", unversionedDescription.getTerm(),
				"Unversioned description is still active and has not been deleted");
		assertTrue(unversionedDescription.isActive(),
				"Unversioned description is still active and has not been deleted");

		// Assert that inactive descriptions also have concept non current indicator applied automatically too.
		Optional<Description> inactiveDescription = descriptions.stream().filter(d -> !d.isActive()).findFirst();
		assertTrue(inactiveDescription.isPresent(), "One description is still inactive");
		assertEquals("CONCEPT_NON_CURRENT", inactiveDescription.get().getInactivationIndicator(),
				"Inactive description automatically has inactivation indicator");

		assertFalse(inactiveConcept.getClassAxioms().iterator().next().isActive(),
				"Axiom is inactive.");

		codeSystemService.createVersion(codeSystem, 20200731, "");

		inactiveConcept = simulateRestTransfer(conceptService.find(inactiveConcept.getId(), path));
		inactiveConcept.setInactivationIndicator("OUTDATED");
		conceptService.update(inactiveConcept, path);

		inactiveConcept = conceptService.find(inactiveConcept.getId(), path);
		assertEquals("OUTDATED", inactiveConcept.getInactivationIndicator());
	}

	@Test
	void testConceptInactivationThenVersionThenAddDescription() throws ServiceException {
		String path = "MAIN";
		branchService.updateMetadata(path, Map.of(BranchMetadataKeys.DEFAULT_MODULE_ID, "900000000000207008"));
		conceptService.batchCreate(Lists.newArrayList(new Concept("107658001"), new Concept("116680003")), path);
		final Concept concept = new Concept("50960005", 20020131, true, "900000000000207008", "900000000000074008");
		concept.addDescription(new Description("84923010", 20020131, true, "900000000000207008", "50960005", "en", "900000000000013009", "Bleeding", "900000000000020002"));
		concept.addAxiom(new Relationship(ISA, "107658001"));
		Concept savedConcept = conceptService.create(concept, path);

		codeSystemService.createVersion(codeSystem, 20200131, "");

		// Make concept inactive
		savedConcept.setActive(false);

		conceptService.update(savedConcept, path);
		Concept foundInactiveConcept = conceptService.find(savedConcept.getConceptId(), path);

		assertFalse(foundInactiveConcept.isActive());

		foundInactiveConcept.addDescription(new Description("New").setModuleId(null));
		conceptService.update(foundInactiveConcept, path);
		foundInactiveConcept = conceptService.find(savedConcept.getConceptId(), path);
		Optional<Description> inactiveDescriptionOptional = foundInactiveConcept.getDescriptions().stream().filter(d -> d.getTerm().equals("New")).findFirst();
		assertTrue(inactiveDescriptionOptional.isPresent());
		Description inactiveDescription = inactiveDescriptionOptional.get();
		assertEquals("900000000000207008", inactiveDescription.getModuleId());

		// Check description inactivation indicator reference set member was created
		Page<ReferenceSetMember> descriptionInactivationMembers = 
				referenceSetMemberService.findMembers(path, new MemberSearchRequest()
						.referenceSet(DESCRIPTION_INACTIVATION_INDICATOR_REFERENCE_SET)
						.referencedComponentId(inactiveDescription.getDescriptionId()),
						PageRequest.of(0, 1));
		assertEquals(1, descriptionInactivationMembers.getTotalElements());
		ReferenceSetMember descriptionInactivationIndicatorMember = descriptionInactivationMembers.get().iterator().next();
		assertNotNull(descriptionInactivationIndicatorMember);
		assertTrue(descriptionInactivationIndicatorMember.isActive());
		assertEquals(Concepts.DESCRIPTION_INACTIVATION_INDICATOR_REFERENCE_SET, descriptionInactivationIndicatorMember.getRefsetId());
		assertEquals(CONCEPT_NON_CURRENT, descriptionInactivationIndicatorMember.getAdditionalField("valueId"));
		assertEquals("900000000000207008", descriptionInactivationIndicatorMember.getModuleId());
	}
	
	@Test
	void testConceptReactivationInExtension() throws ServiceException {
		String path = "MAIN";
		String extensionModule = "45991000052106";
		conceptService.batchCreate(Lists.newArrayList(new Concept("107658001"), new Concept("116680003")), path);
		final Concept concept = new Concept("50960005", 20020131, true, "900000000000207008", "900000000000074008");
		Description desc = new Description("84923010", 20020131, true, "900000000000207008", "50960005", "en", "900000000000013009", "Bleeding", "900000000000020002");
		
		concept.addDescription(desc);
		concept.addAxiom(new Relationship(ISA, "107658001"));
		Concept savedConcept = conceptService.create(concept, path);

		// Make concept inactive
		savedConcept.setActive(false);

		// Set inactivation indicators using strings
		savedConcept.setInactivationIndicator(Concepts.inactivationIndicatorNames.get(Concepts.DUPLICATE));
		desc.setInactivationIndicator(Concepts.inactivationIndicatorNames.get(Concepts.CONCEPT_NON_CURRENT));

		// Set association target using strings
		HashMap<String, Set<String>> associationTargetStrings = new HashMap<>();
		associationTargetStrings.put(Concepts.historicalAssociationNames.get(Concepts.REFSET_SAME_AS_ASSOCIATION), Collections.singleton("87100004"));
		savedConcept.setAssociationTargets(associationTargetStrings);
		assertTrue(savedConcept.getAssociationTargetMembers().isEmpty());
		savedConcept.setInactivationIndicator("OUTDATED");
		
		Concept transferredConcept = simulateRestTransfer(savedConcept);
		conceptService.update(transferredConcept, path);
		
		//Check that the inactivation indicator and hist assoc got created
		Page<ReferenceSetMember> page = referenceSetMemberService.findMembers("MAIN", transferredConcept.getConceptId(), LARGE_PAGE);
		assertEquals(2, page.getContent().size());
		
		codeSystemService.createVersion(codeSystem, 20200131, "");
		
		//Now create an extension code system based off this 
		String extensionBranchPath = "MAIN/SNOMEDCT-SE";
		codeSystemService.createCodeSystem(new CodeSystem("SNOMEDCT-SE", extensionBranchPath));
		branchService.updateMetadata(extensionBranchPath, ImmutableMap.of(Config.DEFAULT_MODULE_ID_KEY, extensionModule, Config.DEFAULT_NAMESPACE_KEY, "1000052"));

		//Now we're going to REactivate that concept in an extension, so the concept itself will move module
		//as will the inactivation indicators which will themselves be INactivated.

		//First verify we can find our concept
		Concept foundInactiveConcept = conceptService.find(savedConcept.getConceptId(), extensionBranchPath);
		assertFalse(foundInactiveConcept.isActive());
		
		//Now let's reactivate that concept on the extension branch
		foundInactiveConcept.setActive(true);
		foundInactiveConcept.setInactivationIndicator(null);
		foundInactiveConcept.setAssociationTargets(null);
		foundInactiveConcept.setModuleId(extensionModule);
		
		//We need to do this REST simulation otherwise the refsetMembers are still on the concept and 
		//take priority over, say, the inactivation indicator String value.
		transferredConcept = simulateRestTransfer(foundInactiveConcept);
		conceptService.update(transferredConcept, extensionBranchPath);
		
		//Now pick it up again
		Concept foundReactivatedConcept = conceptService.find(savedConcept.getConceptId(), extensionBranchPath);
		assertEquals(extensionModule, foundReactivatedConcept.getModuleId());
		
		//Now the inactivation indicator and historical association should have been INACTIVED (since the concept is now active)
		//And that change should have happened in the extension module
		page = referenceSetMemberService.findMembers(extensionBranchPath, savedConcept.getConceptId(), LARGE_PAGE);
		assertEquals(2, page.getContent().size());
		for (ReferenceSetMember rm : page.getContent()) {
			assertFalse(rm.isActive());
			assertEquals(extensionModule, rm.getModuleId());
		}
		
		//Now the description itself will stay active in the international module,
		//but it's Concept Non Current indicator will inactive, again in the extension module
		for (Description d : foundReactivatedConcept.getDescriptions()) {
			assertTrue(d.isActive());
			assertEquals(Concepts.CORE_MODULE, d.getModuleId());
			page = referenceSetMemberService.findMembers(extensionBranchPath, d.getDescriptionId(), LARGE_PAGE);
			assertEquals(1, page.getContent().size());
			for (ReferenceSetMember rm : page.getContent()) {
				//This is just the CNC indicator.  We didn't set any LangRefset members.
				assertFalse(rm.isActive());
				assertEquals(extensionModule, rm.getModuleId());
			}
		}
	}

	@Test
	void testConceptReinactivationOfVersionedConceptWithSameReasonAndAssociation() throws ServiceException, IOException {
		String path = "MAIN";
		conceptService.batchCreate(Lists.newArrayList(new Concept("107658001"), new Concept("116680003")), path);
		Concept concept = new Concept("50960005", 20020131, true, "900000000000207008", "900000000000074008");
		concept.addDescription(new Description("84923010", 20020131, true, "900000000000207008", "50960005", "en", "900000000000013009", "Bleeding", "900000000000020002"));
		concept.addAxiom(new Relationship(ISA, "107658001"));
		concept = conceptService.create(concept, path);

		// Version code system
		codeSystemService.createVersion(codeSystem, 20190731, "");

		// Make concept inactive
		concept.setActive(false);
		concept.setInactivationIndicator(Concepts.inactivationIndicatorNames.get(Concepts.DUPLICATE));
		HashMap<String, Set<String>> associationTargetStrings = new HashMap<>();
		associationTargetStrings.put(Concepts.historicalAssociationNames.get(Concepts.REFSET_SAME_AS_ASSOCIATION), Collections.singleton("87100004"));
		concept.setAssociationTargets(associationTargetStrings);
		concept = conceptService.update(concept, path);

		// Check inactivation indicator string
		concept = conceptService.find(concept.getId(), path);
		assertEquals("DUPLICATE", concept.getInactivationIndicator());

		// Check inactivation indicator reference set member was created
		ReferenceSetMember inactivationIndicatorMember = concept.getInactivationIndicatorMember();
		assertNotNull(inactivationIndicatorMember);
		assertTrue(inactivationIndicatorMember.isActive());
		assertEquals(Concepts.CONCEPT_INACTIVATION_INDICATOR_REFERENCE_SET, inactivationIndicatorMember.getRefsetId());
		assertEquals(Concepts.DUPLICATE, inactivationIndicatorMember.getAdditionalField("valueId"));

		// Check association target reference set member was created
		Collection<ReferenceSetMember> associationTargetMembers = concept.getAssociationTargetMembers();
		assertNotNull(associationTargetMembers);
		assertEquals(1, associationTargetMembers.size());
		ReferenceSetMember associationTargetMember = associationTargetMembers.iterator().next();
		assertTrue(associationTargetMember.isActive());
		assertEquals(Concepts.REFSET_SAME_AS_ASSOCIATION, associationTargetMember.getRefsetId());
		assertEquals(concept.getModuleId(), associationTargetMember.getModuleId());
		assertEquals(concept.getId(), associationTargetMember.getReferencedComponentId());
		assertEquals("87100004", associationTargetMember.getAdditionalField("targetComponentId"));

		Set<Description> descriptions = concept.getDescriptions();
		List<Description> activeDescriptions = descriptions.stream().filter(Description::isActive).sorted(Comparator.comparing(Description::getTerm)).collect(Collectors.toList());
		assertEquals(1, activeDescriptions.size(),
				"One descriptions are still active");
		assertEquals("CONCEPT_NON_CURRENT", activeDescriptions.get(0).getInactivationIndicator(),
				"Active descriptions automatically have inactivation indicator");
		final ReferenceSetMember descriptionInactivationIndicatorMember = activeDescriptions.get(0).getInactivationIndicatorMember();

		assertFalse(concept.getClassAxioms().iterator().next().isActive(), "Axiom is inactive.");

		// Version code system
		codeSystemService.createVersion(codeSystem, 20200131, "");

		// Make concept active again
		concept = simulateRestTransfer(conceptService.find(concept.getId(), path));
		concept.setActive(true);
		concept.getClassAxioms().iterator().next().setActive(true);
		concept.setInactivationIndicator(null);
		concept.setAssociationTargets(null);
		conceptService.update(concept, path);

		concept = conceptService.find(concept.getId(), path);
		assertNull(concept.getInactivationIndicator());
		assertEquals(1, concept.getInactivationIndicatorMembers().size());
		assertFalse(concept.getInactivationIndicatorMembers().stream().anyMatch(ReferenceSetMember::isActive));
		assertEquals(1, concept.getAssociationTargetMembers().size());
		assertFalse(concept.getAssociationTargetMembers().stream().anyMatch(ReferenceSetMember::isActive));

		// Make concept inactive again to check that the same inactivation reason and association refset members are used
		concept = convertToJsonAndBack(concept);
		concept.setActive(false);
		concept.setInactivationIndicator(Concepts.inactivationIndicatorNames.get(Concepts.DUPLICATE));
		associationTargetStrings = new HashMap<>();
		associationTargetStrings.put(Concepts.historicalAssociationNames.get(Concepts.REFSET_SAME_AS_ASSOCIATION), Collections.singleton("87100004"));
		concept.setAssociationTargets(associationTargetStrings);
		conceptService.update(concept, path);
		concept = conceptService.find(concept.getId(), path);

		assertEquals("DUPLICATE", concept.getInactivationIndicator());
		final Collection<ReferenceSetMember> secondTimeInactivationIndicatorMembers = concept.getInactivationIndicatorMembers();
		assertEquals(1, secondTimeInactivationIndicatorMembers.size());
		ReferenceSetMember secondTimeInactivationIndicatorMember = secondTimeInactivationIndicatorMembers.iterator().next();
		assertNotNull(secondTimeInactivationIndicatorMember);
		assertTrue(secondTimeInactivationIndicatorMember.isActive());
		assertEquals(Concepts.CONCEPT_INACTIVATION_INDICATOR_REFERENCE_SET, secondTimeInactivationIndicatorMember.getRefsetId());
		assertEquals(Concepts.DUPLICATE, secondTimeInactivationIndicatorMember.getAdditionalField("valueId"));
		assertEquals(inactivationIndicatorMember.getId(), secondTimeInactivationIndicatorMember.getId(),
				"Original inactivation indicator refset member must be reused because the value is the same.");

		final Map<String, Set<String>> associationTargets = concept.getAssociationTargets();
		assertEquals(1, associationTargets.size());
		assertEquals("{SAME_AS=[87100004]}", associationTargets.toString());
		associationTargetMembers = concept.getAssociationTargetMembers();
		assertNotNull(associationTargetMembers);
		assertEquals(1, associationTargetMembers.size());
		ReferenceSetMember secondTimeAssociationTargetMember = associationTargetMembers.iterator().next();
		assertTrue(secondTimeAssociationTargetMember.isActive());
		assertEquals(Concepts.REFSET_SAME_AS_ASSOCIATION, secondTimeAssociationTargetMember.getRefsetId());
		assertEquals(concept.getModuleId(), secondTimeAssociationTargetMember.getModuleId());
		assertEquals(concept.getId(), secondTimeAssociationTargetMember.getReferencedComponentId());
		assertEquals("87100004", secondTimeAssociationTargetMember.getAdditionalField("targetComponentId"));
		assertEquals(associationTargetMember.getId(), secondTimeAssociationTargetMember.getId(),
				"Original association refset member must be reused because the value is the same.");

		// Same for the description inactivation indicator
		activeDescriptions = concept.getDescriptions().stream().filter(Description::isActive).sorted(Comparator.comparing(Description::getTerm)).collect(Collectors.toList());
		assertEquals(1, activeDescriptions.size());
		assertEquals("CONCEPT_NON_CURRENT", activeDescriptions.get(0).getInactivationIndicator(),
				"Active descriptions automatically have inactivation indicator");
		final ReferenceSetMember secondTimeDescriptionInactivationIndicatorMember = activeDescriptions.get(0).getInactivationIndicatorMember();
		assertEquals(descriptionInactivationIndicatorMember.getId(), secondTimeDescriptionInactivationIndicatorMember.getId(),
				"Original inactivation indicator refset member must be reused because the value is the same.");
	}
	
	@Test
	void testPreviouslyUsedIndicatorAndAssociationAreReusedWherePossible() throws ServiceException, IOException {
		/* When a refeset member is available to be reused, Snowstorm should pick this up and make that member active
		 * rather than creating a new one (which then shows up as an unnecessary duplicate to our validation).
		 * Now the value in attribute value is (probably) mutable so those can be picked up and modified for inactivation indicators
		 * but refsetId is not mutable, so historical associations can only be reused if they are of the same association type */
		
		String path = "MAIN";
		conceptService.batchCreate(Lists.newArrayList(new Concept("107658001"), new Concept("116680003")), path);
		Concept concept = new Concept("50960005", 20020131, true, "900000000000207008", "900000000000074008");
		concept.addDescription(new Description("84923010", 20020131, true, "900000000000207008", "50960005", "en", "900000000000013009", "Bleeding", "900000000000020002"));
		concept.addAxiom(new Relationship(ISA, "107658001"));
		concept = conceptService.create(concept, path);

		// Version code system
		codeSystemService.createVersion(codeSystem, 20190731, "");

		// Make concept inactive
		concept.setActive(false);
		concept.setInactivationIndicator(Concepts.inactivationIndicatorNames.get(Concepts.DUPLICATE));
		HashMap<String, Set<String>> associationTargetStrings = new HashMap<>();
		associationTargetStrings.put(Concepts.historicalAssociationNames.get(Concepts.REFSET_SAME_AS_ASSOCIATION), Collections.singleton("87100004"));
		concept.setAssociationTargets(associationTargetStrings);
		concept = conceptService.update(concept, path);

		// Check inactivation indicator string
		concept = conceptService.find(concept.getId(), path);
		assertEquals("DUPLICATE", concept.getInactivationIndicator());

		// Check inactivation indicator reference set member was created
		ReferenceSetMember inactivationIndicatorMember = concept.getInactivationIndicatorMember();
		assertNotNull(inactivationIndicatorMember);
		assertEquals(Concepts.DUPLICATE, inactivationIndicatorMember.getAdditionalField("valueId"));

		// Check association target reference set member was created
		Collection<ReferenceSetMember> associationTargetMembers = concept.getAssociationTargetMembers();
		assertNotNull(associationTargetMembers);
		assertEquals(1, associationTargetMembers.size());
		ReferenceSetMember associationTargetMember = associationTargetMembers.iterator().next();
		assertEquals(Concepts.REFSET_SAME_AS_ASSOCIATION, associationTargetMember.getRefsetId());

		// Version code system
		codeSystemService.createVersion(codeSystem, 20200131, "");
		
		// Now change the inactivation reason and association.
		// We expect to change the refset member for the inactivation indicator, but will need
		// a new historical association
		concept = simulateRestTransfer(conceptService.find(concept.getId(), path));
		concept.setInactivationIndicator(Concepts.inactivationIndicatorNames.get(Concepts.ERRONEOUS));
		associationTargetStrings = new HashMap<>();
		associationTargetStrings.put(Concepts.historicalAssociationNames.get(Concepts.REFSET_ALTERNATIVE_ASSOCIATION), Collections.singleton("87100004"));
		concept.setAssociationTargets(associationTargetStrings);
		concept = conceptService.update(concept, path);
		
		// Test that we still have the same inactivation indicator, but we should have a new association
		ReferenceSetMember inactivationIndicatorMember2 = concept.getInactivationIndicatorMember();
		assertEquals(inactivationIndicatorMember.getId(), inactivationIndicatorMember2.getId(), "Inactivation indicator refset member should be re-used.");
		
		//Recover the concept afresh because the update method only re-connects active refset members
		concept = conceptService.find(concept.getId(), path);
		
		associationTargetMembers = concept.getAssociationTargetMembers();
		assertNotNull(associationTargetMembers);
		assertEquals(2, associationTargetMembers.size(), "Existing associations should be reused.  Expect 1 per refsetId");
		for (ReferenceSetMember assoc : associationTargetMembers)  {
			//Is the the old one we've replaced or the new one?
			if (assoc.getRefsetId().equals(Concepts.REFSET_SAME_AS_ASSOCIATION)) {
				assertFalse(assoc.isActive());
				assertEquals(associationTargetMember.getId(), assoc.getId());
			} else if (assoc.getRefsetId().equals(Concepts.REFSET_ALTERNATIVE_ASSOCIATION)) {
				assertTrue(assoc.isActive());
				assertNotEquals(associationTargetMember.getId(), assoc.getId());
			} else {
				assertTrue(false, "Association of unexpected type.  Only created SameAs and Alternative");
			}
		}
		
		// Version code system
		codeSystemService.createVersion(codeSystem, 20200731, "");
		
		//NOW, lets version that and put the original values back and confirm that we reuse the original refset members
		concept = simulateRestTransfer(conceptService.find(concept.getId(), path));
		concept.setInactivationIndicator(Concepts.inactivationIndicatorNames.get(Concepts.DUPLICATE));
		associationTargetStrings = new HashMap<>();
		associationTargetStrings.put(Concepts.historicalAssociationNames.get(Concepts.REFSET_SAME_AS_ASSOCIATION), Collections.singleton("87100004"));
		concept.setAssociationTargets(associationTargetStrings);
		concept = conceptService.update(concept, path);
		
		//Recover the concept afresh because the update method only re-connects active refset members
		concept = conceptService.find(concept.getId(), path);

		// Test that we still (again) have the same inactivation indicator, and we haven't gained a new association
		// Furthermore, the association that's now active should be the same one that we originally had way up above
		ReferenceSetMember inactivationIndicatorMember3 = concept.getInactivationIndicatorMember();
		assertEquals(inactivationIndicatorMember.getId(), inactivationIndicatorMember3.getId(), "Inactivation indicator refset member should always be re-used.");
		
		associationTargetMembers = concept.getAssociationTargetMembers();
		assertNotNull(associationTargetMembers);
		assertEquals(2, associationTargetMembers.size(), "No need to create a new association refset member if one can be re-used");
		for (ReferenceSetMember assoc : associationTargetMembers)  {
			//The original association should now be active
			if (assoc.getRefsetId().equals(Concepts.REFSET_SAME_AS_ASSOCIATION)) {
				assertTrue(assoc.isActive());
				assertEquals(associationTargetMember.getId(), assoc.getId());
			} else if (assoc.getRefsetId().equals(Concepts.REFSET_ALTERNATIVE_ASSOCIATION)) {
				assertFalse(assoc.isActive());
				assertNotEquals(associationTargetMember.getId(), assoc.getId());
			} else {
				assertTrue(false, "Association of unexpected type.  Only created SameAs and Alternative");
			}
		}
	}

	@Test
	void testPreviouslyUsedIndicatorFromMultipleExistingInactiveInactivationIndicatorMembers() throws ServiceException {
		/* When multiple refeset member are available to be reused, Snowstorm should pick only one up and make that member active
		 * rather than creating a new one (which then shows up as an unnecessary duplicate to our validation). */

		String path = "MAIN";
		conceptService.batchCreate(Lists.newArrayList(new Concept("107658001"), new Concept("116680003")), path);
		Concept concept = new Concept("50960005", 20020131, true, "900000000000207008", "900000000000074008");
		concept.addDescription(new Description("84923010", 20020131, true, "900000000000207008", "50960005", "en", "900000000000013009", "Bleeding", "900000000000020002"));
		concept.addAxiom(new Relationship(ISA, "107658001"));
		concept = conceptService.create(concept, path);


		// Version code system
		codeSystemService.createVersion(codeSystem, 20190731, "");
		concept = conceptService.find("107658001", path);

		// Assume there are multiple existing inactive Inactivation Indicator Refset Members
		ReferenceSetMember inactiveInactivationIndicatorRefsetMember1 = new ReferenceSetMember();
		inactiveInactivationIndicatorRefsetMember1.setMemberId("2c1fd4bc-8504-46e9-a78c-0a854574f943");
		inactiveInactivationIndicatorRefsetMember1.setReleaseHash(concept.getReleaseHash());
		inactiveInactivationIndicatorRefsetMember1.setReleased(true);
		inactiveInactivationIndicatorRefsetMember1.setActive(false);
		inactiveInactivationIndicatorRefsetMember1.setRefsetId("900000000000489007");
		inactiveInactivationIndicatorRefsetMember1.setAdditionalField(ReferenceSetMember.AttributeValueFields.VALUE_ID, OUTDATED);
		inactiveInactivationIndicatorRefsetMember1.setReferencedComponentId("107658001");
		referenceSetMemberService.createMember(path, inactiveInactivationIndicatorRefsetMember1);

		ReferenceSetMember inactiveInactivationIndicatorRefsetMember2 = new ReferenceSetMember();
		inactiveInactivationIndicatorRefsetMember2.setMemberId("f4738dfe-82f2-421e-bff7-7eb55fd71af5");
		inactiveInactivationIndicatorRefsetMember2.setReleaseHash(concept.getReleaseHash());
		inactiveInactivationIndicatorRefsetMember2.setReleased(true);
		inactiveInactivationIndicatorRefsetMember2.setActive(false);
		inactiveInactivationIndicatorRefsetMember2.setRefsetId("900000000000489007");
		inactiveInactivationIndicatorRefsetMember2.setAdditionalField(ReferenceSetMember.AttributeValueFields.VALUE_ID, DUPLICATE);
		inactiveInactivationIndicatorRefsetMember2.setReferencedComponentId("107658001");
		referenceSetMemberService.createMember(path, inactiveInactivationIndicatorRefsetMember2);

		// Make concept inactive
		concept.setActive(false);
		concept.setInactivationIndicator(Concepts.inactivationIndicatorNames.get(CONCEPT_NON_CURRENT));
		concept = conceptService.update(concept, path);

		// Check inactivation indicator string
		concept = conceptService.find(concept.getId(), path);
		assertEquals("CONCEPT_NON_CURRENT", concept.getInactivationIndicator());

		// Check inactivation indicator reference set member was re-used
		assertEquals(2, concept.getInactivationIndicatorMembers().size());
		ReferenceSetMember newlyActiveinactivationIndicatorMember = concept.getInactivationIndicatorMembers().stream().filter(SnomedComponent::isActive).findFirst().orElse(null);
		assertNotNull(newlyActiveinactivationIndicatorMember);
		assertEquals(Concepts.CONCEPT_NON_CURRENT, newlyActiveinactivationIndicatorMember.getAdditionalField("valueId"));

		// Check the newly active one must be one of inactive inactivation indicator reference set members
		assertTrue("2c1fd4bc-8504-46e9-a78c-0a854574f943".equals(newlyActiveinactivationIndicatorMember.getMemberId()) || "f4738dfe-82f2-421e-bff7-7eb55fd71af5".equals(newlyActiveinactivationIndicatorMember.getMemberId()));

		// Check the remaining inactive inactivation indicator reference set member stays the same as original
		ReferenceSetMember remainingInactiveInactivationIndicatorMember = concept.getInactivationIndicatorMembers().stream().filter(member -> !member.isActive()).findFirst().orElse(null);
		if("2c1fd4bc-8504-46e9-a78c-0a854574f943".equals(remainingInactiveInactivationIndicatorMember.getMemberId())) {
			assertEquals(inactiveInactivationIndicatorRefsetMember1.getReleaseHash(), remainingInactiveInactivationIndicatorMember.getReleaseHash());
			assertEquals(inactiveInactivationIndicatorRefsetMember1.isActive(), remainingInactiveInactivationIndicatorMember.isActive());
			assertEquals(inactiveInactivationIndicatorRefsetMember1.isReleased(), remainingInactiveInactivationIndicatorMember.isReleased());
			assertEquals(inactiveInactivationIndicatorRefsetMember1.getAdditionalField(ReferenceSetMember.AttributeValueFields.VALUE_ID), remainingInactiveInactivationIndicatorMember.getAdditionalField(ReferenceSetMember.AttributeValueFields.VALUE_ID));
			assertEquals(inactiveInactivationIndicatorRefsetMember1.getReferencedComponentId(), remainingInactiveInactivationIndicatorMember.getReferencedComponentId());
			assertEquals(inactiveInactivationIndicatorRefsetMember1.getRefsetId(), remainingInactiveInactivationIndicatorMember.getRefsetId());
		} else {
			assertEquals(inactiveInactivationIndicatorRefsetMember2.getReleaseHash(), remainingInactiveInactivationIndicatorMember.getReleaseHash());
			assertEquals(inactiveInactivationIndicatorRefsetMember2.isActive(), remainingInactiveInactivationIndicatorMember.isActive());
			assertEquals(inactiveInactivationIndicatorRefsetMember2.isReleased(), remainingInactiveInactivationIndicatorMember.isReleased());
			assertEquals(inactiveInactivationIndicatorRefsetMember2.getAdditionalField(ReferenceSetMember.AttributeValueFields.VALUE_ID), remainingInactiveInactivationIndicatorMember.getAdditionalField(ReferenceSetMember.AttributeValueFields.VALUE_ID));
			assertEquals(inactiveInactivationIndicatorRefsetMember2.getReferencedComponentId(), remainingInactiveInactivationIndicatorMember.getReferencedComponentId());
			assertEquals(inactiveInactivationIndicatorRefsetMember2.getRefsetId(), remainingInactiveInactivationIndicatorMember.getRefsetId());
		}
	}

	@Test
	void testPreviouslyUsedAssociationsAreReusedAndTargetNotOverwritten() throws ServiceException {
		/* When targetId is changed for one of the historical association refset members and the rest are inactivated
		 * then targetIds of the inactivated refset members should not be overwritten by the new targetId value. */

		// Create concept
		String path = "MAIN";
		conceptService.batchCreate(Lists.newArrayList(new Concept("107658001"), new Concept("116680003")), path);
		Concept concept = new Concept("50960005", 20020131, true, "900000000000207008", "900000000000074008");
		concept.addDescription(new Description("84923010", 20020131, true, "900000000000207008", "50960005", "en", "900000000000013009", "Bleeding", "900000000000020002"));
		concept.addAxiom(new Relationship(ISA, "107658001"));
		conceptService.create(concept, path);

		// Version code system
		codeSystemService.createVersion(codeSystem, 20190731, "");

		// Make concept inactive as ambiguous with 4 association targets: 87100004, 767171001, 133845002, and 47351003
		concept = conceptService.find(concept.getConceptId(), path);
		concept.setActive(false);
		concept.setInactivationIndicator(Concepts.inactivationIndicatorNames.get(Concepts.AMBIGUOUS));
		Map<String, Set<String>> associationTargetStrings = new HashMap<>();
		associationTargetStrings.put(Concepts.historicalAssociationNames.get(Concepts.REFSET_POSSIBLY_EQUIVALENT_TO_ASSOCIATION), Sets.newHashSet("87100004", "767171001", "133845002", "47351003"));
		concept.setAssociationTargets(associationTargetStrings);
		conceptService.update(concept, path);

		// Version code system
		codeSystemService.createVersion(codeSystem, 20190831, "");

		// Inactivate association target 87100004
		concept = conceptService.find(concept.getConceptId(), path);
		List<ReferenceSetMember> associationTargetMembers = concept.getAssociationTargetMembers();
		ReferenceSetMember associationTargetMember = associationTargetMembers.stream()
				.filter(member -> "87100004".equals(member.getAdditionalField("targetComponentId")))
				.findAny().get();
		associationTargetMember.setActive(false);
		referenceSetMemberService.updateMember(path, associationTargetMember);

		// Version code system
		codeSystemService.createVersion(codeSystem, 20190931, "");

		// Change association target to 125255003
		Concept updateConcept = new Concept(concept.getConceptId());
		updateConcept.setActive(false);
		updateConcept.setInactivationIndicator(Concepts.inactivationIndicatorNames.get(Concepts.AMBIGUOUS));
		associationTargetStrings = new HashMap<>();
		associationTargetStrings.put(Concepts.historicalAssociationNames.get(Concepts.REFSET_POSSIBLY_EQUIVALENT_TO_ASSOCIATION), Sets.newHashSet("125255003"));
		updateConcept.setAssociationTargets(associationTargetStrings);
		conceptService.update(updateConcept, path);

		// Check that inactivated targets are not overwritten with the new value
		concept = conceptService.find(concept.getConceptId(), path);
		associationTargetMembers = concept.getAssociationTargetMembers();

		assertEquals(4, associationTargetMembers.size());

		Optional<ReferenceSetMember> optionalReferenceSetMember = associationTargetMembers.stream()
				.filter(member -> "767171001".equals(member.getAdditionalField("targetComponentId")))
				.findAny();
		assertTrue(optionalReferenceSetMember.isPresent());
		assertFalse(optionalReferenceSetMember.get().isActive());

		optionalReferenceSetMember = associationTargetMembers.stream()
				.filter(member -> "133845002".equals(member.getAdditionalField("targetComponentId")))
				.findAny();
		assertTrue(optionalReferenceSetMember.isPresent());
		assertFalse(optionalReferenceSetMember.get().isActive());

		optionalReferenceSetMember = associationTargetMembers.stream()
				.filter(member -> "47351003".equals(member.getAdditionalField("targetComponentId")))
				.findAny();
		assertTrue(optionalReferenceSetMember.isPresent());
		assertFalse(optionalReferenceSetMember.get().isActive());

		optionalReferenceSetMember = associationTargetMembers.stream()
				.filter(member -> "87100004".equals(member.getAdditionalField("targetComponentId")))
				.findAny();
		assertTrue(optionalReferenceSetMember.isEmpty());

		optionalReferenceSetMember = associationTargetMembers.stream()
				.filter(member -> "125255003".equals(member.getAdditionalField("targetComponentId")))
				.findAny();
		assertTrue(optionalReferenceSetMember.isPresent());
		assertTrue(optionalReferenceSetMember.get().isActive());
	}

	@Test
	void testCreateObjectAttribute() throws ServiceException {
		conceptService.create(new Concept(CONCEPT_MODEL_OBJECT_ATTRIBUTE)
						.addFSN("Concept model object attribute (attribute)")
						.addAxiom(new Axiom().setRelationships(Collections.singleton(new Relationship(ISA, CONCEPT_MODEL_ATTRIBUTE))))
				, "MAIN");

		Concept newAttributeConcept = new Concept("813815325507419009")
				.addAxiom(new Axiom().setRelationships(Collections.singleton(new Relationship(ISA, CONCEPT_MODEL_OBJECT_ATTRIBUTE))))
				.addFSN("New attribute (attribute)");
		newAttributeConcept = conceptService.create(newAttributeConcept, "MAIN");
		Axiom axiom = newAttributeConcept.getClassAxioms().iterator().next();
		assertEquals("SubObjectPropertyOf(:813815325507419009 :762705008)", axiom.getReferenceSetMember().getAdditionalField(ReferenceSetMember.OwlExpressionFields.OWL_EXPRESSION));
		assertEquals(PRIMITIVE, axiom.getDefinitionStatusId());
	}

	@Test
	void testCreateDataAttribute() throws ServiceException {
		conceptService.create(new Concept(CONCEPT_MODEL_DATA_ATTRIBUTE)
						.addFSN("Concept model data attribute (attribute)")
						.addAxiom(new Axiom().setRelationships(Collections.singleton(new Relationship(ISA, CONCEPT_MODEL_ATTRIBUTE))))
				, "MAIN");

		Concept newAttributeConcept = new Concept("10123456789001")
				.addAxiom(new Axiom().setRelationships(Collections.singleton(new Relationship(ISA, CONCEPT_MODEL_DATA_ATTRIBUTE))))
				.addFSN("New data attribute (attribute)");
		newAttributeConcept = conceptService.create(newAttributeConcept, "MAIN");
		Axiom axiom = newAttributeConcept.getClassAxioms().iterator().next();
		assertEquals("SubDataPropertyOf(:10123456789001 :762706009)", axiom.getReferenceSetMember().getAdditionalField(ReferenceSetMember.OwlExpressionFields.OWL_EXPRESSION));
		assertEquals(PRIMITIVE, axiom.getDefinitionStatusId());

		Concept newChildDataAttributeConcept = new Concept("20123456789001")
				.addAxiom(new Axiom().setRelationships(Collections.singleton(new Relationship(ISA, "10123456789001"))))
				.addFSN("New child data attribute (attribute)");
		newChildDataAttributeConcept = conceptService.create(newChildDataAttributeConcept, "MAIN");
		axiom = newChildDataAttributeConcept.getClassAxioms().iterator().next();
		assertEquals("SubDataPropertyOf(:20123456789001 :10123456789001)", axiom.getReferenceSetMember().getAdditionalField(ReferenceSetMember.OwlExpressionFields.OWL_EXPRESSION));
		assertEquals(PRIMITIVE, axiom.getDefinitionStatusId());
	}

	@Test
	public void testCreateConceptWithConcreteIntValue() throws ServiceException {
		//given
		final Concept inConcept = new Concept("12345678910");
		inConcept.addAxiom(
				new Relationship(ISA, "12345"),
				Relationship.newConcrete("1234567891011", ConcreteValue.newInteger("#1"))
		);
		createRangeConstraint("1234567891011", "dec(>#0..)");
		//when
		conceptService.create(inConcept, "MAIN");
		final Concept outConcept = conceptService.find("12345678910", "MAIN");
		final Set<Axiom> classAxioms = outConcept.getClassAxioms();
		final int size = classAxioms.size();
		final String value = classAxioms.iterator().next().getRelationships().iterator().next().getValue();

		//then
		assertEquals(1, size);
		assertEquals("#1", value);
	}

	@Test
	public void testCreateConceptWithConcreteDecValue() throws ServiceException {
		//given
		final Concept inConcept = new Concept("12345678910");
		inConcept.addAxiom(
				new Relationship(ISA, "12345"),
				Relationship.newConcrete("1234567891011", ConcreteValue.newDecimal("#3.14"))
		);
		createRangeConstraint("1234567891011", "dec(>#0..)");

		//when
		conceptService.create(inConcept, "MAIN");
		final Concept outConcept = conceptService.find("12345678910", "MAIN");
		final Set<Axiom> classAxioms = outConcept.getClassAxioms();
		final int size = classAxioms.size();
		final String value = classAxioms.iterator().next().getRelationships().iterator().next().getValue();

		//then
		assertEquals(1, size);
		assertEquals("#3.14", value);
	}

	@Test
	public void testCreateConceptWithConcreteStrValue() throws ServiceException {
		//given
		final Concept inConcept = new Concept("12345678910");
		inConcept.addAxiom(
				new Relationship(ISA, "12345"),
				Relationship.newConcrete("1234567891012", ConcreteValue.newString("\"Two tablets in morning.\""))
		);
		createRangeConstraint("1234567891012", "str(\"tablets\")");

		//when
		conceptService.create(inConcept, "MAIN");
		final Concept outConcept = conceptService.find("12345678910", "MAIN");
		final Set<Axiom> classAxioms = outConcept.getClassAxioms();
		final int size = classAxioms.size();
		final String value = classAxioms.iterator().next().getRelationships().iterator().next().getValue();

		//then
		assertEquals(1, size);
		assertEquals("\"Two tablets in morning.\"", value);
	}

	@Test
	void testSaveConceptWithDescriptionAndAcceptabilityTogether() throws ServiceException {
		final Concept concept = new Concept("50960005", 20020131, true, "900000000000207008", "900000000000074008");
		concept.addDescription(
				new Description("84923010", 20020131, true, "900000000000207008", "50960005", "en", "900000000000013009", "Bleeding", "900000000000020002")
						.addLanguageRefsetMember("900000000000509007", Concepts.PREFERRED)
		);
		conceptService.create(concept, "MAIN");

		final Concept savedConcept = conceptService.find("50960005", "MAIN");
		assertNotNull(savedConcept);
		assertEquals(1, savedConcept.getDescriptions().size());
		final Description description = savedConcept.getDescriptions().iterator().next();
		assertEquals("84923010", description.getDescriptionId());
		final Map<String, ReferenceSetMember> members = description.getLangRefsetMembersFirstValuesMap();
		assertEquals(1, members.size());
		assertEquals(Concepts.PREFERRED, members.get("900000000000509007").getAdditionalField("acceptabilityId"));
	}

	@Test
	void testChangeDescriptionAcceptabilityOnChildBranch() throws ServiceException {
		final Concept concept = new Concept("50960005", 20020131, true, "900000000000207008", "900000000000074008");
		concept.addDescription(
				new Description("84923010", 20020131, true, "900000000000207008", "50960005", "en", "900000000000013009", "Bleeding", "900000000000020002")
						.addLanguageRefsetMember("900000000000509007", Concepts.PREFERRED)
		);
		conceptService.create(concept, "MAIN");

		// Check acceptability on MAIN
		final Concept savedConcept1 = conceptService.find("50960005", "MAIN");
		final Description description1 = savedConcept1.getDescriptions().iterator().next();
		final Map<String, ReferenceSetMember> members1 = description1.getLangRefsetMembersFirstValuesMap();
		assertEquals(Concepts.PREFERRED, members1.get("900000000000509007").getAdditionalField("acceptabilityId"));

		// Update acceptability on MAIN/branch1
		description1.addLanguageRefsetMember("900000000000509007", Concepts.ACCEPTABLE);
		branchService.create("MAIN/branch1");
		conceptService.update(savedConcept1, "MAIN/branch1");

		// Check acceptability on MAIN/branch1
		logger.info("Loading updated concept on MAIN/branch1");
		final Concept savedConcept2 = conceptService.find("50960005", "MAIN/branch1");
		final Description description2 = savedConcept2.getDescriptions().iterator().next();
		final Map<String, ReferenceSetMember> members2 = description2.getLangRefsetMembersFirstValuesMap();
		assertEquals(Concepts.ACCEPTABLE, members2.get("900000000000509007").getAdditionalField("acceptabilityId"));

		// Check acceptability still the same on MAIN
		final Concept savedConcept3 = conceptService.find("50960005", "MAIN");
		final Description description3 = savedConcept3.getDescriptions().iterator().next();
		final Map<String, ReferenceSetMember> members3 = description3.getLangRefsetMembersFirstValuesMap();
		assertEquals(Concepts.PREFERRED, members3.get("900000000000509007").getAdditionalField("acceptabilityId"));
	}

	@Test
	void testChangeDescriptionCaseSignificance() throws ServiceException, IOException {
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
	void testInactivateDescriptionAcceptability() throws ServiceException {
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
		final Map<String, ReferenceSetMember> members1 = description1.getLangRefsetMembersFirstValuesMap();
		assertEquals(Concepts.PREFERRED, members1.get("900000000000509007").getAdditionalField("acceptabilityId"));
		assertTrue(members1.get("900000000000509007").isReleased());
		assertTrue(members1.get("900000000000509007").isActive());
		assertNotNull(members1.get("900000000000509007").getEffectiveTime());

		assertEquals(1, description1.getAcceptabilityMap().size());

		// Remove acceptability in next request
		description1.getLangRefsetMembersMap().clear();
		conceptService.update(savedConcept1, "MAIN");

		// Check acceptability is inactive
		logger.info("Loading updated concept");
		final Concept savedConcept2 = conceptService.find("50960005", "MAIN");
		final Description description2 = savedConcept2.getDescriptions().iterator().next();
		final Map<String, ReferenceSetMember> members2 = description2.getLangRefsetMembersFirstValuesMap();
		assertEquals(1, members2.size());
		assertFalse(members2.get("900000000000509007").isActive());
		assertNull(members2.get("900000000000509007").getEffectiveTime());

		// Check that acceptability map is empty
		assertEquals(0, description2.getAcceptabilityMap().size());
	}

	@Test
	void testInactivateDescriptionAcceptabilityViaDescriptionInactivation() throws ServiceException {
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
		final Map<String, ReferenceSetMember> members1 = description1.getLangRefsetMembersFirstValuesMap();
		assertEquals(Concepts.PREFERRED, members1.get("900000000000509007").getAdditionalField("acceptabilityId"));
		assertTrue(members1.get("900000000000509007").isReleased());
		assertTrue(members1.get("900000000000509007").isActive());
		assertNotNull(members1.get("900000000000509007").getEffectiveTime());

		assertEquals(1, description1.getAcceptabilityMap().size());

		// Make description inactive and save
		description1.setActive(false);
		conceptService.update(savedConcept1, "MAIN");

		// Check acceptability is inactive
		logger.info("Loading updated concept");
		final Concept savedConcept2 = conceptService.find("50960005", "MAIN");
		final Description description2 = savedConcept2.getDescriptions().iterator().next();
		final Map<String, ReferenceSetMember> members2 = description2.getLangRefsetMembersFirstValuesMap();
		assertEquals(1, members2.size());
		assertFalse(members2.get("900000000000509007").isActive());
		assertNull(members2.get("900000000000509007").getEffectiveTime());

		// Check that acceptability map is empty
		assertEquals(0, description2.getAcceptabilityMap().size());
	}

	@Test
	void testInactivateDescriptionMerge() throws ServiceException {
		final Concept concept = new Concept("50960005", 20020131, true, "900000000000207008", "900000000000074008");
		// Add acceptability with released refset member
		final String descriptionId = "84923010";
		concept.addDescription(
				new Description(descriptionId, 20020131, true, "900000000000207008", "50960005", "en", "900000000000013009", "Bleeding", "900000000000020002")
						.addLanguageRefsetMember(US_EN_LANG_REFSET, Concepts.PREFERRED)
						.addLanguageRefsetMember(GB_EN_LANG_REFSET, Concepts.PREFERRED)
		);
		conceptService.create(concept, "MAIN");
		releaseService.createVersion(20170731, "MAIN");

		// Check acceptability
		final Concept savedConcept1 = conceptService.find("50960005", "MAIN");
		List<ReferenceSetMember> releasedMembers = referenceSetMemberService.findMembers("MAIN", descriptionId, ComponentService.LARGE_PAGE).getContent();
		assertEquals(2, releasedMembers.size());
		assertEquals(2L, releasedMembers.stream().filter(SnomedComponent::isReleased).count());
		assertEquals(2L, releasedMembers.stream().filter(SnomedComponent::isActive).count());

		// Make description inactive and save
		final Description description = savedConcept1.getDescriptions().iterator().next();
		description.setActive(false);
		conceptService.update(savedConcept1, "MAIN");

		// Check acceptability is inactive
		logger.info("Loading updated concept");
		final Concept savedConcept2 = conceptService.find("50960005", "MAIN");
		final Description description2 = savedConcept2.getDescriptions().iterator().next();
		final Map<String, ReferenceSetMember> members2 = description2.getLangRefsetMembersFirstValuesMap();
		assertEquals(2, members2.size());
		assertFalse(members2.get(US_EN_LANG_REFSET).isActive());
		assertNull(members2.get(US_EN_LANG_REFSET).getEffectiveTime());
		assertFalse(members2.get(GB_EN_LANG_REFSET).isActive());
		assertNull(members2.get(GB_EN_LANG_REFSET).getEffectiveTime());
		releasedMembers = referenceSetMemberService.findMembers("MAIN", descriptionId, ComponentService.LARGE_PAGE).getContent();
		assertEquals(2, releasedMembers.size());
		assertEquals(2L, releasedMembers.stream().filter(SnomedComponent::isReleased).count());
		assertEquals(0L, releasedMembers.stream().filter(SnomedComponent::isActive).count());

		// Check that acceptability map is empty
		assertEquals(0, description2.getAcceptabilityMap().size());
	}

	@Test
	void testLatestVersionMatch() throws ServiceException {
		testUtil.createConceptWithPathIdAndTerm("MAIN", "100001", "Heart");

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

		assertEquals(1, descriptionService.findDescriptionsWithAggregations("MAIN/A", "Heart", ServiceTestUtil.PAGE_REQUEST).getNumberOfElements(),
				"Branch A should see old version of concept because of old base point.");
		assertEquals(0, descriptionService.findDescriptionsWithAggregations("MAIN/A", "Bone", ServiceTestUtil.PAGE_REQUEST).getNumberOfElements(),
				"Branch A should not see new version of concept because of old base point.");

		final Concept concept1 = conceptService.find("100001", "MAIN");
		assertEquals(1, concept1.getDescriptions().size());
	}

	@Test
	void testRestoreEffectiveTime() throws ServiceException {
		final Integer effectiveTime = 20170131;
		final String conceptId = "50960005";
		final String originalModuleId = "900000000000207008";
		final String path = "MAIN";

		// Create concept
		final Concept concept = new Concept(conceptId, null, true, originalModuleId, "900000000000074008")
				.addDescription(new Description("10000013", null, true, originalModuleId, conceptId, "en",
						Concepts.FSN, "Pizza", Concepts.CASE_INSENSITIVE).addLanguageRefsetMember(Concepts.GB_EN_LANG_REFSET, Concepts.PREFERRED))
				.addRelationship(new Relationship(ISA, SNOMEDCT_ROOT).setInferred(true));
		conceptService.create(concept, path);

		// Run release process
		releaseService.createVersion(effectiveTime, path);

		// Check that release process applied correctly
		final Concept savedConcept = conceptService.find(conceptId, path);
		assertEquals(effectiveTime, savedConcept.getEffectiveTimeI());
		assertEquals(effectiveTime, savedConcept.getReleasedEffectiveTime());
		assertEquals("true|900000000000207008|900000000000074008", savedConcept.getReleaseHash());
		assertTrue(savedConcept.isReleased());

		Description savedDescription = savedConcept.getDescriptions().iterator().next();
		assertEquals(effectiveTime, savedDescription.getEffectiveTimeI());
		assertEquals(effectiveTime, savedDescription.getReleasedEffectiveTime());
		assertEquals("true|Pizza|900000000000207008|en|900000000000003001|900000000000448009", savedDescription.getReleaseHash());

		ReferenceSetMember savedMember = savedDescription.getLangRefsetMembersFirstValuesMap().values().iterator().next();
		assertEquals(effectiveTime, savedMember.getEffectiveTimeI());
		assertEquals(effectiveTime, savedMember.getReleasedEffectiveTime());
		assertEquals("true|900000000000207008|acceptabilityId|900000000000548007", savedMember.getReleaseHash());

		// Change concept, description, member and relationship
		savedConcept.setModuleId("10000123");
		savedDescription.setCaseSignificanceId(Concepts.ENTIRE_TERM_CASE_SENSITIVE);
		savedMember.setAdditionalField(ReferenceSetMember.LanguageFields.ACCEPTABILITY_ID, Concepts.ACCEPTABLE);
		Relationship savedRelationship = savedConcept.getRelationships().iterator().next();
		savedRelationship.setGroupId(1);
		conceptService.update(savedConcept, "MAIN");

		// effectiveTimes cleared
		final Concept conceptAfterUpdate = conceptService.find(conceptId, path);
		assertNull(conceptAfterUpdate.getEffectiveTimeI());
		assertEquals(effectiveTime, conceptAfterUpdate.getReleasedEffectiveTime());
		assertTrue(conceptAfterUpdate.isReleased());
		Description descriptionAfterUpdate = conceptAfterUpdate.getDescriptions().iterator().next();
		assertNull(descriptionAfterUpdate.getEffectiveTimeI());
		assertNull(descriptionAfterUpdate.getLangRefsetMembersFirstValuesMap().values().iterator().next().getEffectiveTimeI());
		assertNull(conceptAfterUpdate.getRelationships().iterator().next().getEffectiveTimeI());

		// Change concept back
		conceptAfterUpdate.setModuleId(originalModuleId);
		conceptService.update(conceptAfterUpdate, "MAIN");

		// Concept effectiveTime restored
		Concept conceptWithRestoredDate = conceptService.find(conceptId, path);
		assertEquals(effectiveTime, conceptWithRestoredDate.getEffectiveTimeI());
		assertEquals(effectiveTime, conceptWithRestoredDate.getReleasedEffectiveTime());
		assertTrue(conceptWithRestoredDate.isReleased());

		// Change description back
		conceptWithRestoredDate.getDescriptions().iterator().next().setCaseSignificanceId(CASE_INSENSITIVE);
		conceptService.update(conceptWithRestoredDate, "MAIN");

		// Description effectiveTime restored
		conceptWithRestoredDate = conceptService.find(conceptId, path);
		assertEquals(effectiveTime, conceptWithRestoredDate.getDescriptions().iterator().next().getEffectiveTimeI());

		// Change lang member back
		ReferenceSetMember member = conceptWithRestoredDate.getDescriptions().iterator().next().getLangRefsetMembersFirstValuesMap().values().iterator().next();
		member.setAdditionalField(ReferenceSetMember.LanguageFields.ACCEPTABILITY_ID, Concepts.PREFERRED);
		conceptService.update(conceptWithRestoredDate, "MAIN");

		// Lang member effectiveTime restored
		conceptWithRestoredDate = conceptService.find(conceptId, path);
		ReferenceSetMember memberWithRestoredDate = conceptWithRestoredDate.getDescriptions().iterator().next().getLangRefsetMembersFirstValuesMap().values().iterator().next();
		assertEquals(effectiveTime, memberWithRestoredDate.getEffectiveTimeI());

		// Change relationship back
		assertEquals(1, conceptWithRestoredDate.getRelationships().size());
		Relationship relationship = conceptWithRestoredDate.getRelationships().iterator().next();
		assertNull(relationship.getEffectiveTimeI());
		relationship.setReleasedEffectiveTime(null);// Clear fields to simulate an API call.
		relationship.setReleaseHash(null);
		relationship.setGroupId(0);
		Concept updatedConceptFromResponse = conceptService.update(conceptWithRestoredDate, "MAIN");
		assertNotNull(updatedConceptFromResponse.getRelationships().iterator().next().getEffectiveTimeI());
	}

	@Test
	void testCreateUpdate10KConcepts() throws ServiceException {
		branchService.create("MAIN/A");
		conceptService.create(new Concept(SNOMEDCT_ROOT), "MAIN/A");

		List<Concept> concepts = new ArrayList<>();
		final int tenThousandOneHundred = 10_100;
		for (int i = 0; i < tenThousandOneHundred; i++) {
			concepts.add(
					new Concept(null, Concepts.CORE_MODULE)
							.addDescription(new Description("Concept " + i))
							.addDescription(new Description("Concept " + i + "(finding)"))
							.addAxiom(new Relationship(Concepts.ISA, SNOMEDCT_ROOT))
			);
		}

		final Iterable<Concept> conceptsCreated = conceptService.batchCreate(concepts, "MAIN/A");

		final Page<Concept> page = conceptService.findAll("MAIN/A", PageRequest.of(0, 100));
		assertEquals(tenThousandOneHundred + 1, page.getTotalElements());
		assertEquals(Concepts.CORE_MODULE, page.getContent().get(50).getModuleId());

		Page<ConceptMini> conceptDescendants = queryService.search(queryService.createQueryBuilder(true).ecl("<" + SNOMEDCT_ROOT), "MAIN/A", PageRequest.of(0, 50));
		assertEquals(tenThousandOneHundred, conceptDescendants.getTotalElements());

		// Double search with filtered results to test ECL cache on more than 10K results
		eclQueryService.setEclCacheEnabled(true);
		String conceptId = conceptDescendants.getContent().get(0).getConceptId();
		conceptDescendants = queryService.search(queryService.createQueryBuilder(true).ecl("<" + SNOMEDCT_ROOT).conceptIds(Collections.singleton(conceptId)), "MAIN/A", PageRequest.of(0, 50));
		assertEquals(1, conceptDescendants.getTotalElements());
		conceptDescendants = queryService.search(queryService.createQueryBuilder(true).ecl("<" + SNOMEDCT_ROOT).conceptIds(Collections.singleton(conceptId)), "MAIN/A", PageRequest.of(0, 50));
		assertEquals(1, conceptDescendants.getTotalElements());
		eclQueryService.setEclCacheEnabled(false);

		Page<Description> descriptions = descriptionService.findDescriptions("MAIN/A", null, null, null, PageRequest.of(0, 50));
		assertEquals(20_200, descriptions.getTotalElements());

		final String anotherModule = "123123";
		List<Concept> toUpdate = new ArrayList<>();
		conceptsCreated.forEach(concept -> {
			concept.setModuleId(anotherModule);
			toUpdate.add(concept);
		});

		conceptService.createUpdate(toUpdate, "MAIN/A");

		final Page<Concept> pageAfterUpdate = conceptService.findAll("MAIN/A", PageRequest.of(0, 100));
		assertEquals(tenThousandOneHundred + 1, pageAfterUpdate.getTotalElements());
		Concept someConcept = pageAfterUpdate.getContent().get(50);
		if (someConcept.getId().equals(SNOMEDCT_ROOT)) {
			someConcept = pageAfterUpdate.getContent().get(51);
		}
		assertEquals(anotherModule, someConcept.getModuleId());
		assertEquals(1, someConcept.getClassAxioms().size());
	}

	@Test
	void testLoadConceptFromParentBranchUsingBaseTimepoint() throws ServiceException {
		List<LanguageDialect> en = DEFAULT_LANGUAGE_DIALECTS;
		String conceptId = "100001";

		// Concept on MAIN with 1 description
		testUtil.createConceptWithPathIdAndTerm("MAIN", conceptId, "Heart");

		// Create branch A
		branchService.create("MAIN/A");
		assertEquals(1, conceptService.find(conceptId, en, new BranchTimepoint("MAIN/A")).getDescriptions().size());

		// Add a second description on A
		String branch = "MAIN/A";
		conceptService.update(conceptService.find(conceptId, branch).addDescription(new Description("Another A")), branch);
		assertEquals(2, conceptService.find(conceptId, en, new BranchTimepoint("MAIN/A")).getDescriptions().size());

		// Concept using base version still has 1
		assertEquals(1, conceptService.find(conceptId, en, new BranchTimepoint("MAIN/A", "^")).getDescriptions().size());

		// Add a second description on MAIN
		branch = "MAIN";
		conceptService.update(conceptService.find(conceptId, branch).addDescription(new Description("Another MAIN")), branch);

		// Concept using base version still has 1
		assertEquals(1, conceptService.find(conceptId, en, new BranchTimepoint("MAIN/A", "^")).getDescriptions().size());

		// Rebase A
		branchMergeService.mergeBranchSync("MAIN", "MAIN/A", Collections.emptySet());

		// A now has 3 descriptions
		assertEquals(3, conceptService.find(conceptId, en, new BranchTimepoint("MAIN/A")).getDescriptions().size());

		// Base version should now have 2 descriptions because the base of the branch moved when the rebase happened. It's the 2 descriptions on MAIN.
		assertEquals(2, conceptService.find(conceptId, en, new BranchTimepoint("MAIN/A", "^")).getDescriptions().size());
		assertEquals("[Heart, Another MAIN]", conceptService.find(conceptId, en, new BranchTimepoint("MAIN/A", "^")).getDescriptions().stream()
				.sorted(Comparator.comparing(Description::getTermLen)).map(Description::getTerm).collect(Collectors.toList()).toString());
	}

	@Test
	void testUpdateDescriptionInactivationIndicatorRefsetsWithNoChanges() throws ServiceException {
		String conceptId = "148176006";
		String descriptionId = "231971010";

		// Concept on MAIN with 1 inactive description
		final Concept concept = new Concept(conceptId);
		Description inactiveDescription = new Description(descriptionId,"Menopause: LH, FSH checked");
		inactiveDescription.setActive(false);

		ReferenceSetMember conceptNonCurrentIndicatorRefset = new ReferenceSetMember();
		conceptNonCurrentIndicatorRefset.setMemberId("9138e35b-6fed-5c76-a75e-ea7c3061b41e");
		conceptNonCurrentIndicatorRefset.setAdditionalField("valueId", "900000000000495008");
		conceptNonCurrentIndicatorRefset.setActive(false);
		conceptNonCurrentIndicatorRefset.setReleased(true);
		conceptNonCurrentIndicatorRefset.setModuleId("900000000000207008");
		conceptNonCurrentIndicatorRefset.setRefsetId("900000000000490003");
		conceptNonCurrentIndicatorRefset.setReferencedComponentId("231971010");
		conceptNonCurrentIndicatorRefset.setReleasedEffectiveTime(20150731);
		conceptNonCurrentIndicatorRefset.release(20150731);

		ReferenceSetMember erroneousIndicatorRefset = new ReferenceSetMember();
		erroneousIndicatorRefset.setMemberId("9badf4d9-a88e-4118-9883-89ff6219dfe3");
		erroneousIndicatorRefset.setAdditionalField("valueId", "900000000000485001");
		erroneousIndicatorRefset.setActive(true);
		erroneousIndicatorRefset.setReleased(true);
		erroneousIndicatorRefset.setModuleId("900000000000207008");
		erroneousIndicatorRefset.setRefsetId("900000000000490003");
		erroneousIndicatorRefset.setReferencedComponentId("231971010");
		erroneousIndicatorRefset.setReleasedEffectiveTime(20150731);
		erroneousIndicatorRefset.release(20150731);

		conceptService.create(concept.addDescription(inactiveDescription), "MAIN");

		// Add 2 Description inactivation indicator member reference sets with 1 active and 1 inactive
		referenceSetMemberService.createMember("MAIN", conceptNonCurrentIndicatorRefset);
		referenceSetMemberService.createMember("MAIN", erroneousIndicatorRefset);


		List<ReferenceSetMember> inactivationIndicatorMembers = referenceSetMemberService.findMembers("MAIN", descriptionId, PageRequest.of(0, 10)).getContent();
		assertEquals(2, inactivationIndicatorMembers.size());
		Concept actualConcept = conceptService.find(conceptId, DEFAULT_LANGUAGE_DIALECTS, new BranchTimepoint("MAIN"));

		assertEquals(1, actualConcept.getDescriptions().size());
		assertEquals(2, actualConcept.getDescriptions().iterator().next().getInactivationIndicatorMembers().size());

		actualConcept = simulateRestTransfer(actualConcept);
		conceptService.createUpdate(Collections.singletonList(actualConcept), "MAIN");
		Concept conceptAfterUpdate = conceptService.find(actualConcept.getConceptId(), "MAIN");
		assertEquals(2, conceptAfterUpdate.getDescriptions().iterator().next().getInactivationIndicatorMembers().size());
	}

	@Test
	void testUpdateConceptWithInactiveDescriptionsNoComponentSaves() throws ServiceException {
		// Create concept
		Concept concept = new Concept().addFSN("Heart");
		assertEquals(1, concept.getDescriptions().iterator().next().getAcceptabilityMap().size());
		String branch = "MAIN";
		conceptService.create(concept, branch);

		// Version code system
		codeSystemService.createVersion(codeSystem, 20220101, "");

		// Inactivate description (will make lang refset members inactive)
		concept = simulateRestTransfer(conceptService.find(concept.getConceptId(), branch));
		concept.getDescriptions().iterator().next().setActive(false);
		conceptService.update(concept, branch);
		concept = simulateRestTransfer(conceptService.find(concept.getConceptId(), branch));

		// Further updates must not result in any component changes
		conceptService.update(concept, branch);

		Branch latestCommit = branchService.findLatest(branch);
		NativeQuery query = new NativeQueryBuilder().withQuery(termQuery("start", latestCommit.getHeadTimestamp())).build();
		assertEquals(0, elasticsearchOperations.count(query, Concept.class));
		assertEquals(0, elasticsearchOperations.count(query, Relationship.class));
		assertEquals(0, elasticsearchOperations.count(query, Description.class));
		assertEquals(0, elasticsearchOperations.count(query, ReferenceSetMember.class));
	}

	@Test
	void testTypeAndTargetWhenSavingRelationship() throws Exception {
		conceptService.create(new Concept(ISA).setDefinitionStatusId(PRIMITIVE).addDescription(fsn("Is a (attribute)")), "MAIN");
		conceptService.create(new Concept(SNOMEDCT_ROOT).setDefinitionStatusId(PRIMITIVE).addDescription(fsn("SNOMED CT Concept")), "MAIN");

		Concept concept = conceptService.create(
				new Concept("100001")
						.addRelationship(new Relationship("100001", ISA, SNOMEDCT_ROOT))
						.addRelationship(new Relationship("100002", ISA, CLINICAL_FINDING))
				, "MAIN");

		concept = conceptService.find(concept.getConceptId(), "MAIN");
		assertNotNull(concept);
		Relationship relationship = concept.getRelationship("100002");
		assertNotNull(relationship.getTarget());
		assertNotNull(relationship.getType());
		// Use repository directly to make sure transient fields are not stored
		Relationship storedRelationship = relationshipService.findRelationship("MAIN", "100002");
		assertNotNull(storedRelationship);
		assertNull(storedRelationship.getTarget());
		assertNull(storedRelationship.getType());

		// update concept with relationships fully loaded
		relationship.setActive(false);
		relationship.setRelationshipGroup(1);
		relationship.setGroupOrder(2);
		relationship.setAttributeOrder(Short.valueOf("1"));
		relationship.setSource(new ConceptMini("100001", DEFAULT_LANGUAGE_DIALECTS));
		conceptService.update(concept, "MAIN");
		// make sure transient fields are not stored after updating
		storedRelationship = relationshipService.findRelationship("MAIN", "100002");
		assertNotNull(storedRelationship);
		assertNull(storedRelationship.getTarget());
		assertNull(storedRelationship.getType());
		assertNull(storedRelationship.getSource());
		assertNull(storedRelationship.getAttributeOrder());
		// when group order is null and the value returned is the relationship group
		assertEquals(storedRelationship.getRelationshipGroup(), storedRelationship.getGroupOrder());

		// make sure view is not affected
		concept = conceptService.find(concept.getConceptId(), "MAIN");
		relationship = concept.getRelationship("100002");
		assertNotNull(relationship.getTarget());
		assertNotNull(relationship.getType());
	}

	@Test
	public void testDuplicateAxiomsDoNotReplaceEachOther() throws ServiceException {
		conceptService.create(new Concept(ISA).setDefinitionStatusId(PRIMITIVE).addDescription(fsn("Is a (attribute)")), "MAIN");
		conceptService.create(new Concept(SNOMEDCT_ROOT).setDefinitionStatusId(PRIMITIVE).addDescription(fsn("SNOMED CT Concept")), "MAIN");

		Concept concept = conceptService.create(
				new Concept("100001")
						.addDescription(new Description("Event (event)").setTypeId(Concepts.FSN))
						.addAxiom(new Relationship(ISA, SNOMEDCT_ROOT))
				, "MAIN");

		concept = conceptService.find(concept.getConceptId(), "MAIN");
		assertEquals(1, concept.getClassAxioms().size());

		concept.addAxiom(new Relationship(ISA, SNOMEDCT_ROOT));
		conceptService.update(concept, "MAIN");
		concept = conceptService.find(concept.getConceptId(), "MAIN");
		assertEquals(2, concept.getClassAxioms().size());
	}

	@Test
	public void testBulkJobAxiomSerialisationError() {
		String batchId = conceptService.newCreateUpdateAsyncJob();
		conceptService.createUpdateAsync(batchId, "MAIN", Collections.singletonList(new Concept().addAxiom(new Relationship(FINDING_SITE, HEART_STRUCTURE))),
				SecurityContextHolder.getContext());

		assertTrue(waitUntil(() -> conceptService.getBatchConceptChange(batchId).getStatus() == AsyncConceptChangeBatch.Status.FAILED, 10));
		AsyncConceptChangeBatch batchConceptChange = conceptService.getBatchConceptChange(batchId);
		assertEquals(AsyncConceptChangeBatch.Status.FAILED, batchConceptChange.getStatus());
		assertEquals("Failed to convert axiom to an OWL expression.", batchConceptChange.getMessage());
	}

	// Effective time and module ID tests
	@Test
	public void testUpdateExistingDescriptionThenRevert_ShouldNotChangeEffectiveTime() throws ServiceException {
		// Create a concept with a description on MAIN
		conceptService.create(new Concept("100001")
				.addDescription(new Description("100001","caseSignificanceId")
						.setCaseSignificanceId(CASE_INSENSITIVE)), "MAIN");

		// Version MAIN
		codeSystemService.createVersion(codeSystem, 20220131, "January 2022");

		// Create a task under MAIN
		branchService.create("MAIN/A");

		Concept concept = conceptService.find("100001", "MAIN/A");
		Description description = concept.getDescription("100001");
		assertEquals(CASE_INSENSITIVE, description.getCaseSignificanceId());
		assertNotNull(description.getEffectiveTime());

		// Change the caseSignificanceId for a description and save on task.
		description.setCaseSignificanceId(ENTIRE_TERM_CASE_SENSITIVE);
		conceptService.update(concept, "MAIN/A");

		// The effective time should be cleared.
		concept = conceptService.find("100001", "MAIN/A");
		description = concept.getDescription("100001");
		assertEquals(ENTIRE_TERM_CASE_SENSITIVE, description.getCaseSignificanceId());
		assertNull(description.getEffectiveTime());

		// Revert the change.
		description.setCaseSignificanceId(CASE_INSENSITIVE);
		conceptService.update(concept, "MAIN/A");

		// The effective time should NOT be cleared.
		concept = conceptService.find("100001", "MAIN/A");
		description = concept.getDescription("100001");
		assertEquals(CASE_INSENSITIVE, description.getCaseSignificanceId());
		assertNotNull(description.getEffectiveTime());
	}

	@Test
	void testUpdateExistingDescriptionInExtensionThenRevert_ShouldNotChangeEffectiveTime() throws ServiceException {
		// Create a concept on MAIN
		conceptService.create(new Concept("100001"), "MAIN");

		// Version MAIN
		codeSystemService.createVersion(codeSystem, 20220131, "January 2022");

		// Create extension code system SNOMEDCT-BE
		CodeSystem extension = codeSystemService.createCodeSystem(new CodeSystem("SNOMEDCT-BE", "MAIN/SNOMEDCT-BE"));
		branchService.updateMetadata("MAIN/SNOMEDCT-BE", ImmutableMap.of(Config.DEFAULT_MODULE_ID_KEY, "11000172109", Config.DEFAULT_NAMESPACE_KEY, "1000172"));

		// Add a description in extension
		Concept concept = conceptService.find("100001", "MAIN/SNOMEDCT-BE");
		Description description = new Description("100001","caseSignificanceId")
				.setModuleId("11000172109")
				.setLanguageCode("fr")
				.setCaseSignificanceId(CASE_INSENSITIVE);
		conceptService.update(concept.addDescription(description), "MAIN/SNOMEDCT-BE");

		// Version extension
		codeSystemService.createVersion(extension, 20220228, "February 2022");

		// Create a task under MAIN/SNOMEDCT-BE
		branchService.create("MAIN/SNOMEDCT-BE/BE");

		concept = conceptService.find("100001", "MAIN/SNOMEDCT-BE/BE");
		description = concept.getDescription("100001");
		assertEquals(CASE_INSENSITIVE, description.getCaseSignificanceId());
		assertNotNull(description.getEffectiveTime());

		// Change the caseSignificanceId for a description and save on task.
		description.setCaseSignificanceId(ENTIRE_TERM_CASE_SENSITIVE);
		conceptService.update(concept, "MAIN/SNOMEDCT-BE/BE");

		// The effective time should be cleared.
		concept = conceptService.find("100001", "MAIN/SNOMEDCT-BE/BE");
		description = concept.getDescription("100001");
		assertEquals(ENTIRE_TERM_CASE_SENSITIVE, description.getCaseSignificanceId());
		assertNull(description.getEffectiveTime());

		// Revert the change.
		description.setCaseSignificanceId(CASE_INSENSITIVE);
		conceptService.update(concept, "MAIN/SNOMEDCT-BE/BE");

		// The effective time should NOT be cleared.
		concept = conceptService.find("100001", "MAIN/SNOMEDCT-BE/BE");
		description = concept.getDescription("100001");
		assertEquals(CASE_INSENSITIVE, description.getCaseSignificanceId());
		assertNotNull(description.getEffectiveTime());
	}

	@Test
	void testUpdatePublishedInternationalInferredRelationshipInExtension_ShouldClearEffectiveTime_ShouldChangeModuleId() throws ServiceException {
		// Create a new relationship on MAIN
		conceptService.create(new Concept(ISA).setDefinitionStatusId(PRIMITIVE).addDescription(fsn("Is a (attribute)")), "MAIN");
		conceptService.create(new Concept(SNOMEDCT_ROOT).setDefinitionStatusId(PRIMITIVE).addDescription(fsn("SNOMED CT Concept")), "MAIN");
		conceptService.create(new Concept("100001").addRelationship(new Relationship("100001", ISA, SNOMEDCT_ROOT)), "MAIN");

		// Version MAIN
		codeSystemService.createVersion(codeSystem, 20220131, "January 2022");

		// Create extension code system SNOMEDCT-BE
		CodeSystem extension = codeSystemService.createCodeSystem(new CodeSystem("SNOMEDCT-BE", "MAIN/SNOMEDCT-BE"));
		branchService.updateMetadata("MAIN/SNOMEDCT-BE", ImmutableMap.of(Config.DEFAULT_MODULE_ID_KEY, "11000172109", Config.DEFAULT_NAMESPACE_KEY, "1000172"));

		// Version extension
		codeSystemService.createVersion(extension, 20220228, "February 2022");

		// Create a task under MAIN/SNOMEDCT-BE
		branchService.create("MAIN/SNOMEDCT-BE/BE");

		Concept concept = conceptService.find("100001", "MAIN/SNOMEDCT-BE/BE");
		Relationship relationship = concept.getRelationships().iterator().next();

		assertEquals(relationship.getReleasedEffectiveTime(), relationship.getEffectiveTimeI());
		assertEquals(concept.getModuleId(), relationship.getModuleId());

		// Inactivate the relationship
		relationship.setActive(false);
		conceptService.update(concept, "MAIN/SNOMEDCT-BE/BE");

		concept = conceptService.find("100001", "MAIN/SNOMEDCT-BE/BE");
		relationship = concept.getRelationships().iterator().next();

		assertNull(relationship.getEffectiveTimeI());
		assertEquals("11000172109", relationship.getModuleId());
	}

	@Test
	void testRevertPublishedInternationalInferredRelationshipInExtension_ShouldNotChangeEffectiveTimeOrModuleId() throws ServiceException {
		// Create a new relationship on MAIN
		conceptService.create(new Concept(ISA).setDefinitionStatusId(PRIMITIVE).addDescription(fsn("Is a (attribute)")), "MAIN");
		conceptService.create(new Concept(SNOMEDCT_ROOT).setDefinitionStatusId(PRIMITIVE).addDescription(fsn("SNOMED CT Concept")), "MAIN");
		conceptService.create(new Concept("100001").addRelationship(new Relationship("100001", ISA, SNOMEDCT_ROOT)), "MAIN");

		// Version MAIN
		codeSystemService.createVersion(codeSystem, 20220131, "January 2022");

		// Create extension code system SNOMEDCT-BE
		CodeSystem extension = codeSystemService.createCodeSystem(new CodeSystem("SNOMEDCT-BE", "MAIN/SNOMEDCT-BE"));
		branchService.updateMetadata("MAIN/SNOMEDCT-BE", ImmutableMap.of(Config.DEFAULT_MODULE_ID_KEY, "11000172109", Config.DEFAULT_NAMESPACE_KEY, "1000172"));

		// Version extension
		codeSystemService.createVersion(extension, 20220228, "February 2022");

		// Create a task under MAIN/SNOMEDCT-BE
		branchService.create("MAIN/SNOMEDCT-BE/BE");

		Concept concept = conceptService.find("100001", "MAIN/SNOMEDCT-BE/BE");
		Relationship relationship = concept.getRelationships().iterator().next();

		assertEquals(relationship.getReleasedEffectiveTime(), relationship.getEffectiveTimeI());
		assertEquals(concept.getModuleId(), relationship.getModuleId());

		// Inactivate the relationship
		relationship.setActive(false);
		conceptService.update(concept, "MAIN/SNOMEDCT-BE/BE");

		// Revert the change
		relationship.setActive(true);
		conceptService.update(concept, "MAIN/SNOMEDCT-BE/BE");

		concept = conceptService.find("100001", "MAIN/SNOMEDCT-BE/BE");
		relationship = concept.getRelationships().iterator().next();

		assertEquals(relationship.getReleasedEffectiveTime(), relationship.getEffectiveTimeI());
		assertEquals(concept.getModuleId(), relationship.getModuleId());
	}

	@Test
	void testUpdatePublishedInternationalComponent_ShouldClearEffectiveTime_ShouldNotChangeModuleId() throws ServiceException {
		// Create a concept on MAIN
		conceptService.create(new Concept("100001"), "MAIN");

		// Version MAIN
		codeSystemService.createVersion(codeSystem, 20220131, "January 2022");

		// Create a task under MAIN
		branchService.create("MAIN/A");

		Concept concept = conceptService.find("100001", "MAIN/A");
		assertEquals(concept.getReleasedEffectiveTime(), concept.getEffectiveTimeI());
		assertEquals(CORE_MODULE, concept.getModuleId());

		// Inactivate the concept
		concept.setActive(false);
		conceptService.update(concept, "MAIN/A");

		// Effective time should be cleared, module ID should not change
		concept = conceptService.find("100001", "MAIN/A");
		assertNull(concept.getEffectiveTimeI());
		assertEquals(CORE_MODULE, concept.getModuleId());
	}

	@Test
	void testRevertPublishedInternationalComponent_ShoulNotChangeEffectiveTimeOrModuleId() throws ServiceException {
		// Create a concept on MAIN
		conceptService.create(new Concept("100001"), "MAIN");

		// Version MAIN
		codeSystemService.createVersion(codeSystem, 20220131, "January 2022");

		// Create a task under MAIN
		branchService.create("MAIN/A");

		Concept concept = conceptService.find("100001", "MAIN/A");
		assertEquals(concept.getReleasedEffectiveTime(), concept.getEffectiveTimeI());
		assertEquals(CORE_MODULE, concept.getModuleId());

		// Inactivate the concept
		concept.setActive(false);
		conceptService.update(concept, "MAIN/A");

		// Revert the change
		concept.setActive(true);
		conceptService.update(concept, "MAIN/A");

		// Effective time should be cleared, module ID should not change
		concept = conceptService.find("100001", "MAIN/A");
		assertEquals(concept.getReleasedEffectiveTime(), concept.getEffectiveTimeI());
		assertEquals(CORE_MODULE, concept.getModuleId());
	}

	@Test
	void testUpdatePublishedExtensionComponent_ShouldClearEffectiveTime_ShouldNotChangeModuleId() throws ServiceException {
		// Create extension code system SNOMEDCT-BE
		CodeSystem extension = codeSystemService.createCodeSystem(new CodeSystem("SNOMEDCT-BE", "MAIN/SNOMEDCT-BE"));
		branchService.updateMetadata("MAIN/SNOMEDCT-BE", ImmutableMap.of(Config.DEFAULT_MODULE_ID_KEY, "11000172109", Config.DEFAULT_NAMESPACE_KEY, "1000172"));

		// Create a concept on MAIN/SNOMEDCT-BE
		conceptService.create(new Concept("100001", "11000172109"), "MAIN/SNOMEDCT-BE");

		// Version extension
		codeSystemService.createVersion(extension, 20220228, "February 2022");

		// Create a task under MAIN/SNOMEDCT-BE
		branchService.create("MAIN/SNOMEDCT-BE/BE");

		Concept concept = conceptService.find("100001", "MAIN/SNOMEDCT-BE/BE");
		assertEquals(concept.getReleasedEffectiveTime(), concept.getEffectiveTimeI());
		assertEquals("11000172109", concept.getModuleId());

		// Inactivate the concept
		concept.setActive(false);
		conceptService.update(concept, "MAIN/SNOMEDCT-BE/BE");

		// Effective time should be cleared, module ID should change to the default module ID of the extension branch
		concept = conceptService.find("100001", "MAIN/SNOMEDCT-BE/BE");
		assertNull(concept.getEffectiveTimeI());
		assertEquals("11000172109", concept.getModuleId());
	}

	@Test
	void testRevertPublishedExtensionComponent_ShoulNotChangeEffectiveTimeOrModuleId() throws ServiceException {
		// Create extension code system SNOMEDCT-BE
		CodeSystem extension = codeSystemService.createCodeSystem(new CodeSystem("SNOMEDCT-BE", "MAIN/SNOMEDCT-BE"));
		branchService.updateMetadata("MAIN/SNOMEDCT-BE", ImmutableMap.of(Config.DEFAULT_MODULE_ID_KEY, "11000172109", Config.DEFAULT_NAMESPACE_KEY, "1000172"));

		// Create a concept on MAIN/SNOMEDCT-BE
		conceptService.create(new Concept("100001", "11000172109"), "MAIN/SNOMEDCT-BE");

		// Version extension
		codeSystemService.createVersion(extension, 20220228, "February 2022");

		// Create a task under MAIN/SNOMEDCT-BE
		branchService.create("MAIN/SNOMEDCT-BE/BE");

		Concept concept = conceptService.find("100001", "MAIN/SNOMEDCT-BE/BE");
		assertEquals(concept.getReleasedEffectiveTime(), concept.getEffectiveTimeI());
		assertEquals("11000172109", concept.getModuleId());

		// Inactivate the concept
		concept.setActive(false);
		conceptService.update(concept, "MAIN/SNOMEDCT-BE/BE");

		// Revert the change
		concept.setActive(true);
		conceptService.update(concept, "MAIN/SNOMEDCT-BE/BE");

		// Effective time should be cleared, module ID should not change
		concept = conceptService.find("100001", "MAIN/SNOMEDCT-BE/BE");
		assertEquals(concept.getReleasedEffectiveTime(), concept.getEffectiveTimeI());
		assertEquals("11000172109", concept.getModuleId());
	}

	@Test
	void testUpdateUnpublishedInternationalComponent_ShouldNotChangeEffectiveTimeOrModuleId() throws ServiceException {
		// Create a concept on MAIN
		conceptService.create(new Concept("100001"), "MAIN");

		// Create a task under MAIN
		branchService.create("MAIN/A");

		Concept concept = conceptService.find("100001", "MAIN/A");

		// Inactivate the concept
		concept.setActive(false);
		conceptService.update(concept, "MAIN/A");

		// Effective time and module ID should not change
		concept = conceptService.find("100001", "MAIN/A");
		assertNull(concept.getEffectiveTimeI());
		assertEquals(CORE_MODULE, concept.getModuleId());
	}

	@Test
	void testRevertUnpublishedInternationalComponent_ShouldNotChangeEffectiveTimeOrModuleId() throws ServiceException {
		// Create a concept on MAIN
		conceptService.create(new Concept("100001"), "MAIN");

		// Create a task under MAIN
		branchService.create("MAIN/A");

		Concept concept = conceptService.find("100001", "MAIN/A");

		// Inactivate the concept
		concept.setActive(false);
		conceptService.update(concept, "MAIN/A");

		// Revert the change
		concept.setActive(true);
		conceptService.update(concept, "MAIN/A");

		// Effective time and module ID should not change
		concept = conceptService.find("100001", "MAIN/A");
		assertNull(concept.getEffectiveTimeI());
		assertEquals(CORE_MODULE, concept.getModuleId());
	}

	@Test
	void testUpdateUnpublishedExtensionComponent_ShouldNotChangeEffectiveTimeOrModuleId() throws ServiceException {
		// Create extension code system SNOMEDCT-BE
		codeSystemService.createCodeSystem(new CodeSystem("SNOMEDCT-BE", "MAIN/SNOMEDCT-BE"));
		branchService.updateMetadata("MAIN/SNOMEDCT-BE", ImmutableMap.of(Config.DEFAULT_MODULE_ID_KEY, "11000172109", Config.DEFAULT_NAMESPACE_KEY, "1000172"));

		// Create a concept on MAIN/SNOMEDCT-BE
		conceptService.create(new Concept("100001", "11000172109"), "MAIN/SNOMEDCT-BE");

		// Create a task under MAIN/SNOMEDCT-BE
		branchService.create("MAIN/SNOMEDCT-BE/BE");

		Concept concept = conceptService.find("100001", "MAIN/SNOMEDCT-BE/BE");

		// Inactivate the concept
		concept.setActive(false);
		conceptService.update(concept, "MAIN/SNOMEDCT-BE/BE");

		// Effective time should be cleared, module ID should change to the default module ID of the extension branch
		concept = conceptService.find("100001", "MAIN/SNOMEDCT-BE/BE");
		assertNull(concept.getEffectiveTimeI());
		assertEquals("11000172109", concept.getModuleId());
	}

	@Test
	void testRevertUnpublishedExtensionComponent_ShoulNotChangeEffectiveTimeOrModuleId() throws ServiceException {
		// Create extension code system SNOMEDCT-BE
		codeSystemService.createCodeSystem(new CodeSystem("SNOMEDCT-BE", "MAIN/SNOMEDCT-BE"));
		branchService.updateMetadata("MAIN/SNOMEDCT-BE", ImmutableMap.of(Config.DEFAULT_MODULE_ID_KEY, "11000172109", Config.DEFAULT_NAMESPACE_KEY, "1000172"));

		// Create a concept on MAIN/SNOMEDCT-BE
		conceptService.create(new Concept("100001", "11000172109"), "MAIN/SNOMEDCT-BE");

		// Create a task under MAIN/SNOMEDCT-BE
		branchService.create("MAIN/SNOMEDCT-BE/BE");

		Concept concept = conceptService.find("100001", "MAIN/SNOMEDCT-BE/BE");

		// Inactivate the concept
		concept.setActive(false);
		conceptService.update(concept, "MAIN/SNOMEDCT-BE/BE");

		// Revert the change
		concept.setActive(true);
		conceptService.update(concept, "MAIN/SNOMEDCT-BE/BE");

		// Effective time and module ID should not change
		concept = conceptService.find("100001", "MAIN/SNOMEDCT-BE/BE");
		assertNull(concept.getEffectiveTimeI());
		assertEquals("11000172109", concept.getModuleId());
	}

	@Test
	void testCreateNewInternationalComponent_EffectiveTimeIsNull_CoreModuleId() throws ServiceException {
		// Create a concept on MAIN
		conceptService.create(new Concept("100001"), "MAIN");

		// Create a task under MAIN
		branchService.create("MAIN/A");

		Concept concept = conceptService.find("100001", "MAIN/A");
		assertNull(concept.getEffectiveTimeI());
		assertEquals(CORE_MODULE, concept.getModuleId());
	}

	@Test
	void testCreateNewExtensionComponent_EffectiveTimeIsNull_DefaultModuleId() throws ServiceException {
		// Create extension code system SNOMEDCT-BE
		codeSystemService.createCodeSystem(new CodeSystem("SNOMEDCT-BE", "MAIN/SNOMEDCT-BE"));
		branchService.updateMetadata("MAIN/SNOMEDCT-BE", ImmutableMap.of(Config.DEFAULT_MODULE_ID_KEY, "11000172109", Config.DEFAULT_NAMESPACE_KEY, "1000172"));

		// Create a concept on MAIN/SNOMEDCT-BE
		conceptService.create(new Concept("100001", "11000172109"), "MAIN/SNOMEDCT-BE");

		// Create a task under MAIN/SNOMEDCT-BE
		branchService.create("MAIN/SNOMEDCT-BE/BE");

		Concept concept = conceptService.find("100001", "MAIN/SNOMEDCT-BE/BE");
		assertNull(concept.getEffectiveTimeI());
		assertEquals("11000172109", concept.getModuleId());
	}

	@Test
	void testECLWithMemberOfQueries() throws ServiceException {
		Concept concept;
		String intMain = "MAIN";
		String ecl;
		Page<ConceptMini> page;
		List<String> conceptIds;
		ReferenceSetMember referenceSetMember;

		// Create reference sets
		concept = new Concept()
				.addDescription(new Description("Reference set (reference set)").setTypeId(FSN))
				.addDescription(new Description("Reference set").setTypeId(SYNONYM))
				.addAxiom(new Relationship(ISA, SNOMEDCT_ROOT))
				.addRelationship(new Relationship(ISA, SNOMEDCT_ROOT));
		concept = conceptService.create(concept, intMain);
		String referenceSetId = concept.getConceptId();

		concept = new Concept()
				.addDescription(new Description("Vehicles (reference set)").setTypeId(FSN))
				.addDescription(new Description("Vehicles reference set").setTypeId(SYNONYM))
				.addAxiom(new Relationship(ISA, REFSET_SIMPLE))
				.addRelationship(new Relationship(ISA, referenceSetId));
		concept = conceptService.create(concept, intMain);
		String vehiclesReferenceSetId = concept.getConceptId();

		// Create concepts
		concept = new Concept()
				.addDescription(new Description("Vehicle (vehicle)").setTypeId(FSN))
				.addDescription(new Description("Vehicle").setTypeId(SYNONYM))
				.addAxiom(new Relationship(ISA, SNOMEDCT_ROOT))
				.addRelationship(new Relationship(ISA, SNOMEDCT_ROOT));
		concept = conceptService.create(concept, intMain);
		String vehicleId = concept.getConceptId();

		concept = new Concept()
				.addDescription(new Description("Car (vehicle)").setTypeId(FSN))
				.addDescription(new Description("Car").setTypeId(SYNONYM))
				.addAxiom(new Relationship(ISA, SNOMEDCT_ROOT))
				.addRelationship(new Relationship(ISA, vehicleId));
		concept = conceptService.create(concept, intMain);
		String carId = concept.getConceptId();

		// Born inactive
		concept = new Concept()
				.addDescription(new Description("Motorised vehicle (vehicle)").setTypeId(FSN))
				.addDescription(new Description("Motorised vehicle").setTypeId(SYNONYM))
				.addAxiom(new Relationship(ISA, SNOMEDCT_ROOT).setActive(false))
				.addRelationship(new Relationship(ISA, vehicleId).setActive(false))
				.setActive(false);
		concept = conceptService.create(concept, intMain);
		String motorisedVehicleId = concept.getConceptId();

		// Add concepts to reference set
		referenceSetMember = new ReferenceSetMember().setRefsetId(vehiclesReferenceSetId).setModuleId(CORE_MODULE).setReferencedComponentId(vehicleId);
		referenceSetMemberService.createMember(intMain, referenceSetMember);

		referenceSetMember = new ReferenceSetMember().setRefsetId(vehiclesReferenceSetId).setModuleId(CORE_MODULE).setReferencedComponentId(carId);
		referenceSetMemberService.createMember(intMain, referenceSetMember);

		referenceSetMember = new ReferenceSetMember().setRefsetId(vehiclesReferenceSetId).setModuleId(CORE_MODULE).setReferencedComponentId(motorisedVehicleId);
		referenceSetMemberService.createMember(intMain, referenceSetMember); // User error; should be removed

		// Version
		codeSystem = codeSystemService.find("SNOMEDCT");
		codeSystemService.createVersion(codeSystem, 20240101, "20240101");

		// Assert versioned
		concept = conceptService.find(vehicleId, intMain);
		assertReleased(concept, 20240101);
		assertTrue(concept.isActive());

		concept = conceptService.find(carId, intMain);
		assertReleased(concept, 20240101);
		assertTrue(concept.isActive());

		concept = conceptService.find(motorisedVehicleId, intMain);
		assertReleased(concept, 20240101);
		assertFalse(concept.isActive());

		// Assert query/semantic index is as expected
		ecl = String.format("< %s", SNOMEDCT_ROOT);
		page = queryService.search(queryService.createQueryBuilder(false).ecl(ecl), intMain, PageRequest.of(0, 50));
		assertEquals(4, page.getTotalElements());
		conceptIds = page.getContent().stream().map(ConceptMini::getConceptId).toList();
		assertTrue(conceptIds.contains(referenceSetId));
		assertTrue(conceptIds.contains(vehiclesReferenceSetId));
		assertTrue(conceptIds.contains(vehicleId));
		assertTrue(conceptIds.contains(carId));
		assertFalse(conceptIds.contains(motorisedVehicleId)); // Excluded as inactive

		// Assert ecl statements return same response
		ecl = String.format("^ %s", vehiclesReferenceSetId);
		page = queryService.search(queryService.createQueryBuilder(false).ecl(ecl), intMain, PageRequest.of(0, 50));
		assertEquals(3, page.getTotalElements());
		conceptIds = page.getContent().stream().map(ConceptMini::getConceptId).toList();
		assertTrue(conceptIds.contains(vehicleId));
		assertTrue(conceptIds.contains(carId));
		assertTrue(conceptIds.contains(motorisedVehicleId)); // Concept is inactive, but its member is not

		ecl = String.format("(^ %s)", vehiclesReferenceSetId);
		page = queryService.search(queryService.createQueryBuilder(false).ecl(ecl), intMain, PageRequest.of(0, 50));
		assertEquals(3, page.getTotalElements());
		conceptIds = page.getContent().stream().map(ConceptMini::getConceptId).toList();
		assertTrue(conceptIds.contains(vehicleId));
		assertTrue(conceptIds.contains(carId));
		assertTrue(conceptIds.contains(motorisedVehicleId));  // Concept is inactive, but its member is not

		// Compound queries the second part should return no results for testing purpose only
		// Disjunction with and without brackets
		ecl = String.format("(^ %s) OR (<< 423857001 |Structure of half of body lateral to midsagittal plane (body structure)|)", vehiclesReferenceSetId);
		page = queryService.search(queryService.createQueryBuilder(false).ecl(ecl), intMain, PageRequest.of(0, 50));
		assertEquals(3, page.getTotalElements());

		ecl = String.format("^ %s OR << 423857001 |Structure of half of body lateral to midsagittal plane (body structure)|", vehiclesReferenceSetId);
		page = queryService.search(queryService.createQueryBuilder(false).ecl(ecl), intMain, PageRequest.of(0, 50));
		assertEquals(3, page.getTotalElements());

		// Conjunction with and without brackets
		ecl = String.format("(^ %s) AND (<< 423857001 |Structure of half of body lateral to midsagittal plane (body structure)|)", vehiclesReferenceSetId);
		page = queryService.search(queryService.createQueryBuilder(false).ecl(ecl), intMain, PageRequest.of(0, 50));
		assertEquals(0, page.getTotalElements());

		ecl = String.format("^ %s AND << 423857001 |Structure of half of body lateral to midsagittal plane (body structure)|", vehiclesReferenceSetId);
		page = queryService.search(queryService.createQueryBuilder(false).ecl(ecl), intMain, PageRequest.of(0, 50));
		assertEquals(0, page.getTotalElements());

		// Exclusion with and without brackets
		ecl = String.format("(^ %s) MINUS (<< 423857001 |Structure of half of body lateral to midsagittal plane (body structure)|)", vehiclesReferenceSetId);
		page = queryService.search(queryService.createQueryBuilder(false).ecl(ecl), intMain, PageRequest.of(0, 50));
		assertEquals(3, page.getTotalElements());

		ecl = String.format("^ %s MINUS << 423857001 |Structure of half of body lateral to midsagittal plane (body structure)|", vehiclesReferenceSetId);
		page = queryService.search(queryService.createQueryBuilder(false).ecl(ecl), intMain, PageRequest.of(0, 50));
		assertEquals(3, page.getTotalElements());


		// Extra testings for nested expression constraints
		ecl = String.format("<< (^ %s)", vehiclesReferenceSetId);
		page = queryService.search(queryService.createQueryBuilder(false).ecl(ecl), intMain, PageRequest.of(0, 50));
		assertEquals(2, page.getTotalElements());

		ecl = String.format("<<^ %s", vehiclesReferenceSetId);
		page = queryService.search(queryService.createQueryBuilder(false).ecl(ecl), intMain, PageRequest.of(0, 50));
		assertEquals(2, page.getTotalElements());

		ecl = String.format("^(<<%s)", vehiclesReferenceSetId);
		page = queryService.search(queryService.createQueryBuilder(false).ecl(ecl), intMain, PageRequest.of(0, 50));
		assertEquals(3, page.getTotalElements());
	}

	@Test
	void testUpdatingInactivatesDuplicateLangMember() throws ServiceException {
		Concept concept;
		String intMain = "MAIN";
		Map<String, String> preferred = Map.of(US_EN_LANG_REFSET, descriptionAcceptabilityNames.get(PREFERRED), GB_EN_LANG_REFSET, descriptionAcceptabilityNames.get(PREFERRED));
		Map<String, String> acceptable = Map.of(US_EN_LANG_REFSET, descriptionAcceptabilityNames.get(ACCEPTABLE), GB_EN_LANG_REFSET, descriptionAcceptabilityNames.get(ACCEPTABLE));

		// Create concept
		concept = new Concept()
				.addDescription(new Description("Vehicle (vehicle)").setTypeId(FSN).setAcceptabilityMap(preferred))
				.addDescription(new Description("Vehicle").setTypeId(SYNONYM).setAcceptabilityMap(preferred))
				.addAxiom(new Relationship(ISA, SNOMEDCT_ROOT))
				.addRelationship(new Relationship(ISA, SNOMEDCT_ROOT));
		concept = conceptService.create(concept, intMain);
		String conceptId = concept.getConceptId();

		// Accidentally create duplicate
		ReferenceSetMember duplicate = new ReferenceSetMember();
		duplicate.setRefsetId(US_EN_LANG_REFSET);
		duplicate.setModuleId(CORE_MODULE);
		duplicate.setReferencedComponentId(getDescriptionByTerm(concept, "Vehicle").getId());
		duplicate.setAdditionalField(ReferenceSetMember.LanguageFields.ACCEPTABILITY_ID, ACCEPTABLE);
		referenceSetMemberService.createMember(intMain, duplicate);

		// Version
		codeSystem = codeSystemService.find("SNOMEDCT");
		codeSystemService.createVersion(codeSystem, 20240101, "20240101");

		// Add synonym
		concept = conceptService.find(conceptId, intMain);
		concept.addDescription(new Description("Motorised vehicle").setTypeId(SYNONYM).setAcceptabilityMap(acceptable));
		conceptService.update(concept, intMain);

		// Assert
		concept = conceptService.find(conceptId, intMain);
		Set<ReferenceSetMember> usLangMembers = getDescriptionByTerm(concept, "Vehicle").getLangRefsetMembersMap().get(US_EN_LANG_REFSET);
		assertEquals(2, usLangMembers.size());
		List<ReferenceSetMember> active = usLangMembers.stream().filter(ReferenceSetMember::isActive).collect(Collectors.toList());
		List<ReferenceSetMember> inactive = usLangMembers.stream().filter(r -> {return !r.isActive();}).collect(Collectors.toList());
		List<ReferenceSetMember> nullET = usLangMembers.stream().filter(r -> {return r.getEffectiveTime() == null;}).collect(Collectors.toList());
		assertEquals(1, active.size());
		assertEquals(1, inactive.size());
		assertEquals(1, nullET.size());
		assertNotEquals(active.iterator().next().getId(), nullET.iterator().next().getId());
	}

	@Test
	void testAddingTranslationInactivatesDuplicateLangMember() throws ServiceException {
		Concept concept;
		String intMain = "MAIN";
		String extMain = "MAIN/SNOMEDCT-XX";
		Map<String, String> preferred = Map.of(US_EN_LANG_REFSET, descriptionAcceptabilityNames.get(PREFERRED), GB_EN_LANG_REFSET, descriptionAcceptabilityNames.get(PREFERRED));
		Map<String, String> acceptable = Map.of(US_EN_LANG_REFSET, descriptionAcceptabilityNames.get(ACCEPTABLE), GB_EN_LANG_REFSET, descriptionAcceptabilityNames.get(ACCEPTABLE));
		CodeSystem codeSystem;

		// Create International concept
		concept = new Concept()
				.addDescription(new Description("Vehicle (vehicle)").setTypeId(FSN).setAcceptabilityMap(preferred))
				.addDescription(new Description("Vehicle").setTypeId(SYNONYM).setAcceptabilityMap(preferred))
				.addAxiom(new Relationship(ISA, SNOMEDCT_ROOT))
				.addRelationship(new Relationship(ISA, SNOMEDCT_ROOT));
		concept = conceptService.create(concept, intMain);
		String conceptId = concept.getConceptId();

		// Version International
		codeSystem = codeSystemService.find("SNOMEDCT");
		codeSystemService.createVersion(codeSystem, 20240101, "20240101");

		// Create Extension
		codeSystem = codeSystemService.createCodeSystem(new CodeSystem("SNOMEDCT-XX", extMain));
		concept = conceptService.create(
				new Concept()
						.addDescription(new Description("Extension module (module)").setTypeId(FSN).setAcceptabilityMap(preferred))
						.addDescription(new Description("Extension module").setTypeId(SYNONYM).setAcceptabilityMap(preferred))
						.addAxiom(new Relationship(ISA, MODULE)),
				extMain
		);
		String extModuleA = concept.getConceptId();
		concept = conceptService.create(
				new Concept()
						.addDescription(new Description("Extension medicine module (module)").setTypeId(FSN).setAcceptabilityMap(preferred))
						.addDescription(new Description("Extension medicine module").setAcceptabilityMap(preferred))
						.addAxiom(new Relationship(ISA, MODULE)),
				extMain
		);
		String extModuleB = concept.getConceptId();
		branchService.updateMetadata(extMain, Map.of(Config.DEFAULT_MODULE_ID_KEY, extModuleA, Config.EXPECTED_EXTENSION_MODULES, List.of(extModuleA, extModuleB)));

		// Extension accidentally creates duplicate lang member
		concept = conceptService.find(conceptId, extMain);
		ReferenceSetMember duplicate = new ReferenceSetMember();
		duplicate.setRefsetId(US_EN_LANG_REFSET);
		duplicate.setModuleId(extModuleA);
		duplicate.setReferencedComponentId(getDescriptionByTerm(concept, "Vehicle").getId());
		duplicate.setAdditionalField(ReferenceSetMember.LanguageFields.ACCEPTABILITY_ID, ACCEPTABLE);
		referenceSetMemberService.createMember(extMain, duplicate);

		duplicate = new ReferenceSetMember();
		duplicate.setRefsetId(US_EN_LANG_REFSET);
		duplicate.setModuleId(extModuleA);
		duplicate.setReferencedComponentId(getDescriptionByTerm(concept, "Vehicle").getId());
		duplicate.setAdditionalField(ReferenceSetMember.LanguageFields.ACCEPTABILITY_ID, ACCEPTABLE);
		referenceSetMemberService.createMember(extMain, duplicate);

		// Version Extension
		codeSystem = codeSystemService.find("SNOMEDCT-XX");
		codeSystemService.createVersion(codeSystem, 20240102, "20240102");

		// Extension adds translation
		concept = conceptService.find(conceptId, intMain);
		concept.addDescription(new Description("Fordon").setTypeId(SYNONYM).setAcceptabilityMap(acceptable));
		conceptService.update(concept, extMain);

		// Assert
		concept = conceptService.find(conceptId, extMain);
		Set<ReferenceSetMember> usLangMembers = getDescriptionByTerm(concept, "Vehicle").getLangRefsetMembersMap().get(US_EN_LANG_REFSET);
		assertEquals(3, usLangMembers.size());
		List<ReferenceSetMember> active = usLangMembers.stream().filter(ReferenceSetMember::isActive).collect(Collectors.toList());
		List<ReferenceSetMember> inactive = usLangMembers.stream().filter(r -> {return !r.isActive();}).collect(Collectors.toList());
		List<ReferenceSetMember> nullET = usLangMembers.stream().filter(r -> {return r.getEffectiveTime() == null;}).collect(Collectors.toList());
		assertEquals(1, active.size());
		assertEquals(2, inactive.size());
		assertEquals(2, nullET.size());
		assertNotEquals(active.iterator().next().getId(), nullET.iterator().next().getId());
		assertEquals(PREFERRED, active.iterator().next().getAdditionalField(ReferenceSetMember.LanguageFields.ACCEPTABILITY_ID));
		assertEquals(ACCEPTABLE, nullET.iterator().next().getAdditionalField(ReferenceSetMember.LanguageFields.ACCEPTABILITY_ID));
	}

	@Test
	void testAddingTranslationDeletesDuplicateLangMember() throws ServiceException {
		Concept concept;
		String intMain = "MAIN";
		String extMain = "MAIN/SNOMEDCT-XX";
		Map<String, String> preferred = Map.of(US_EN_LANG_REFSET, descriptionAcceptabilityNames.get(PREFERRED), GB_EN_LANG_REFSET, descriptionAcceptabilityNames.get(PREFERRED));
		Map<String, String> acceptable = Map.of(US_EN_LANG_REFSET, descriptionAcceptabilityNames.get(ACCEPTABLE), GB_EN_LANG_REFSET, descriptionAcceptabilityNames.get(ACCEPTABLE));
		CodeSystem codeSystem;

		// Create International concept
		concept = new Concept()
				.addDescription(new Description("Vehicle (vehicle)").setTypeId(FSN).setAcceptabilityMap(preferred))
				.addDescription(new Description("Vehicle").setTypeId(SYNONYM).setAcceptabilityMap(preferred))
				.addAxiom(new Relationship(ISA, SNOMEDCT_ROOT))
				.addRelationship(new Relationship(ISA, SNOMEDCT_ROOT));
		concept = conceptService.create(concept, intMain);
		String conceptId = concept.getConceptId();

		// Version International
		codeSystem = codeSystemService.find("SNOMEDCT");
		codeSystemService.createVersion(codeSystem, 20240101, "20240101");

		// Create Extension
		codeSystem = codeSystemService.createCodeSystem(new CodeSystem("SNOMEDCT-XX", extMain));
		concept = conceptService.create(
				new Concept()
						.addDescription(new Description("Extension module (module)").setTypeId(FSN).setAcceptabilityMap(preferred))
						.addDescription(new Description("Extension module").setTypeId(SYNONYM).setAcceptabilityMap(preferred))
						.addAxiom(new Relationship(ISA, MODULE)),
				extMain
		);
		String extModuleA = concept.getConceptId();
		concept = conceptService.create(
				new Concept()
						.addDescription(new Description("Extension medicine module (module)").setTypeId(FSN).setAcceptabilityMap(preferred))
						.addDescription(new Description("Extension medicine module").setAcceptabilityMap(preferred))
						.addAxiom(new Relationship(ISA, MODULE)),
				extMain
		);
		String extModuleB = concept.getConceptId();
		branchService.updateMetadata(extMain, Map.of(Config.DEFAULT_MODULE_ID_KEY, extModuleA, Config.EXPECTED_EXTENSION_MODULES, List.of(extModuleA, extModuleB)));

		// Extension accidentally creates duplicate lang member
		concept = conceptService.find(conceptId, extMain);
		ReferenceSetMember duplicate = new ReferenceSetMember();
		duplicate.setRefsetId(US_EN_LANG_REFSET);
		duplicate.setModuleId(extModuleA);
		duplicate.setReferencedComponentId(getDescriptionByTerm(concept, "Vehicle").getId());
		duplicate.setAdditionalField(ReferenceSetMember.LanguageFields.ACCEPTABILITY_ID, ACCEPTABLE);
		referenceSetMemberService.createMember(extMain, duplicate);

		duplicate = new ReferenceSetMember();
		duplicate.setRefsetId(US_EN_LANG_REFSET);
		duplicate.setModuleId(extModuleA);
		duplicate.setReferencedComponentId(getDescriptionByTerm(concept, "Vehicle").getId());
		duplicate.setAdditionalField(ReferenceSetMember.LanguageFields.ACCEPTABILITY_ID, ACCEPTABLE);
		referenceSetMemberService.createMember(extMain, duplicate);

		// Extension adds translation
		concept = conceptService.find(conceptId, intMain);
		concept.addDescription(new Description("Fordon").setTypeId(SYNONYM).setAcceptabilityMap(acceptable));
		conceptService.update(concept, extMain);

		// Assert
		concept = conceptService.find(conceptId, extMain);
		Set<ReferenceSetMember> usLangMembers = getDescriptionByTerm(concept, "Vehicle").getLangRefsetMembersMap().get(US_EN_LANG_REFSET);
		assertEquals(1, usLangMembers.size());
		List<ReferenceSetMember> active = usLangMembers.stream().filter(ReferenceSetMember::isActive).collect(Collectors.toList());
		List<ReferenceSetMember> inactive = usLangMembers.stream().filter(r -> {return !r.isActive();}).collect(Collectors.toList());
		List<ReferenceSetMember> nullET = usLangMembers.stream().filter(r -> {return r.getEffectiveTime() == null;}).collect(Collectors.toList());
		assertEquals(1, active.size());
		assertEquals(0, inactive.size());
		assertEquals(0, nullET.size());
		assertEquals(PREFERRED, active.iterator().next().getAdditionalField(ReferenceSetMember.LanguageFields.ACCEPTABILITY_ID));
	}

	@Test
	void testAddingTranslationDoesNotReactivateDuplicateLangMember() throws ServiceException {
		Concept concept;
		String intMain = "MAIN";
		String extMain = "MAIN/SNOMEDCT-XX";
		Map<String, String> preferred = Map.of(US_EN_LANG_REFSET, descriptionAcceptabilityNames.get(PREFERRED), GB_EN_LANG_REFSET, descriptionAcceptabilityNames.get(PREFERRED));
		Map<String, String> acceptable = Map.of(US_EN_LANG_REFSET, descriptionAcceptabilityNames.get(ACCEPTABLE), GB_EN_LANG_REFSET, descriptionAcceptabilityNames.get(ACCEPTABLE));
		CodeSystem codeSystem;

		// Create International concept
		concept = new Concept()
				.addDescription(new Description("Vehicle (vehicle)").setTypeId(FSN).setAcceptabilityMap(preferred))
				.addDescription(new Description("Vehicle").setTypeId(SYNONYM).setAcceptabilityMap(preferred))
				.addAxiom(new Relationship(ISA, SNOMEDCT_ROOT))
				.addRelationship(new Relationship(ISA, SNOMEDCT_ROOT));
		concept = conceptService.create(concept, intMain);
		String conceptId = concept.getConceptId();

		// Version International
		codeSystem = codeSystemService.find("SNOMEDCT");
		codeSystemService.createVersion(codeSystem, 20240101, "20240101");

		// Create Extension
		codeSystem = codeSystemService.createCodeSystem(new CodeSystem("SNOMEDCT-XX", extMain));
		concept = conceptService.create(
				new Concept()
						.addDescription(new Description("Extension module (module)").setTypeId(FSN).setAcceptabilityMap(preferred))
						.addDescription(new Description("Extension module").setTypeId(SYNONYM).setAcceptabilityMap(preferred))
						.addAxiom(new Relationship(ISA, MODULE)),
				extMain
		);
		String extModuleA = concept.getConceptId();
		concept = conceptService.create(
				new Concept()
						.addDescription(new Description("Extension medicine module (module)").setTypeId(FSN).setAcceptabilityMap(preferred))
						.addDescription(new Description("Extension medicine module").setAcceptabilityMap(preferred))
						.addAxiom(new Relationship(ISA, MODULE)),
				extMain
		);
		String extModuleB = concept.getConceptId();
		branchService.updateMetadata(extMain, Map.of(Config.DEFAULT_MODULE_ID_KEY, extModuleA, Config.EXPECTED_EXTENSION_MODULES, List.of(extModuleA, extModuleB)));

		// Extension accidentally creates duplicate lang member
		concept = conceptService.find(conceptId, extMain);
		ReferenceSetMember duplicate = new ReferenceSetMember();
		duplicate.setRefsetId(US_EN_LANG_REFSET);
		duplicate.setModuleId(extModuleA);
		duplicate.setReferencedComponentId(getDescriptionByTerm(concept, "Vehicle").getId());
		duplicate.setAdditionalField(ReferenceSetMember.LanguageFields.ACCEPTABILITY_ID, ACCEPTABLE);
		duplicate = referenceSetMemberService.createMember(extMain, duplicate);
		String duplicateA = duplicate.getMemberId();

		duplicate = new ReferenceSetMember();
		duplicate.setRefsetId(US_EN_LANG_REFSET);
		duplicate.setModuleId(extModuleA);
		duplicate.setReferencedComponentId(getDescriptionByTerm(concept, "Vehicle").getId());
		duplicate.setAdditionalField(ReferenceSetMember.LanguageFields.ACCEPTABILITY_ID, ACCEPTABLE);
		duplicate = referenceSetMemberService.createMember(extMain, duplicate);
		String duplicateB = duplicate.getMemberId();

		// Version Extension
		codeSystem = codeSystemService.find("SNOMEDCT-XX");
		codeSystemService.createVersion(codeSystem, 20240102, "20240102");

		// Inactivate duplicates
		duplicate = referenceSetMemberService.findMember(extMain, duplicateA);
		duplicate.setActive(false);
		referenceSetMemberService.updateMember(extMain, duplicate);

		duplicate = referenceSetMemberService.findMember(extMain, duplicateB);
		duplicate.setActive(false);
		referenceSetMemberService.updateMember(extMain, duplicate);

		// Version Extension
		codeSystem = codeSystemService.find("SNOMEDCT-XX");
		codeSystemService.createVersion(codeSystem, 20240103, "20240103");

		// Extension adds translation
		concept = conceptService.find(conceptId, intMain);
		concept.addDescription(new Description("Fordon").setTypeId(SYNONYM).setAcceptabilityMap(acceptable));
		conceptService.update(concept, extMain);

		// Assert
		concept = conceptService.find(conceptId, extMain);
		Set<ReferenceSetMember> usLangMembers = getDescriptionByTerm(concept, "Vehicle").getLangRefsetMembersMap().get(US_EN_LANG_REFSET);
		assertEquals(3, usLangMembers.size());

		List<ReferenceSetMember> active = usLangMembers.stream().filter(ReferenceSetMember::isActive).collect(Collectors.toList());
		List<ReferenceSetMember> inactive = usLangMembers.stream().filter(r -> {return !r.isActive();}).collect(Collectors.toList());
		List<ReferenceSetMember> nullET = usLangMembers.stream().filter(r -> {return r.getEffectiveTime() == null;
		}).collect(Collectors.toList());
		assertEquals(1, active.size());
		assertEquals(2, inactive.size());
		assertEquals(0, nullET.size());
		assertEquals(PREFERRED, active.iterator().next().getAdditionalField(ReferenceSetMember.LanguageFields.ACCEPTABILITY_ID));
	}

	@Test
	void testInvalidInactiveAxiomNotReactivated() throws ServiceException {
		String intMain = "MAIN";
		Map<String, String> intPreferred = Map.of(US_EN_LANG_REFSET, descriptionAcceptabilityNames.get(PREFERRED), GB_EN_LANG_REFSET, descriptionAcceptabilityNames.get(PREFERRED));
		Map<String, String> intAcceptable = Map.of(US_EN_LANG_REFSET, descriptionAcceptabilityNames.get(PREFERRED), GB_EN_LANG_REFSET, descriptionAcceptabilityNames.get(ACCEPTABLE));
		String ci = "CASE_INSENSITIVE";
		Concept concept;
		CodeSystem codeSystem;

		// Create Concept
		concept = new Concept()
				.addDescription(new Description("Vehicle (vehicle)").setTypeId(FSN).setCaseSignificance(ci).setAcceptabilityMap(intPreferred))
				.addDescription(new Description("Vehicle").setTypeId(SYNONYM).setCaseSignificance(ci).setAcceptabilityMap(intPreferred))
				.addAxiom(new Relationship(ISA, SNOMEDCT_ROOT));
		concept = conceptService.create(concept, intMain);
		String vehicleId = concept.getConceptId();

		// Version
		codeSystem = codeSystemService.find("SNOMEDCT");
		codeSystemService.createVersion(codeSystem, 20240101, "20240101");

		// Create type of Vehicle (purposely without Axiom)
		concept = new Concept()
				.addDescription(new Description("Car (vehicle)").setTypeId(FSN).setCaseSignificance(ci).setAcceptabilityMap(intPreferred))
				.addDescription(new Description("Car").setTypeId(SYNONYM).setCaseSignificance(ci).setAcceptabilityMap(intPreferred))
				.addRelationship(new Relationship(ISA, vehicleId));
		concept = conceptService.create(concept, intMain);
		String carId = concept.getConceptId();

		// Manually create Axiom incorrectly (formatted back to front)
		ReferenceSetMember axiom = new ReferenceSetMember(CORE_MODULE, OWL_AXIOM_REFERENCE_SET, concept.getConceptId());
		String backToFront = String.format("SubClassOf(:%s :%s)", vehicleId, carId); // Note: vehicleId -> carId
		axiom.setAdditionalField("owlExpression", backToFront);
		axiom = referenceSetMemberService.createMember(intMain, axiom);
		String invalidAxiomId = axiom.getId();

		// Assert formatted incorrectly
		axiom = referenceSetMemberService.findMember(intMain, axiom.getMemberId());
		String owlExpression = axiom.getAdditionalField("owlExpression");
		assertEquals(backToFront, owlExpression);

		// Version error
		codeSystem = codeSystemService.find("SNOMEDCT");
		codeSystemService.createVersion(codeSystem, 20240201, "20240201");

		// Fix mistake (replace invalid with valid)
		axiom = referenceSetMemberService.findMember(intMain, invalidAxiomId);
		axiom.setActive(false);
		axiom.updateEffectiveTime();
		referenceSetMemberService.updateMember(intMain, axiom);

		axiom = new ReferenceSetMember(CORE_MODULE, OWL_AXIOM_REFERENCE_SET, concept.getConceptId());
		String frontToBack = String.format("SubClassOf(:%s :%s)", carId, vehicleId);  // Note: carId -> vehicleId
		axiom.setAdditionalField("owlExpression", frontToBack);
		axiom = referenceSetMemberService.createMember(intMain, axiom);
		String validAxiomId = axiom.getId();

		// Version correction
		codeSystem = codeSystemService.find("SNOMEDCT");
		codeSystemService.createVersion(codeSystem, 20240301, "20240301");

		// Add synonym
		concept = conceptService.find(carId, intMain);
		concept.addDescription(new Description("Motorised vehicle").setTypeId(SYNONYM).setCaseSignificance(ci).setAcceptabilityMap(intPreferred));
		conceptService.update(concept, intMain);

		// Assert inactive axiom hasn't changed
		Axiom invalidAxiom = concept.getClassAxioms().stream().filter(cA -> cA.getAxiomId().equals(invalidAxiomId)).collect(Collectors.toList()).get(0);
		assertEquals(20240301, invalidAxiom.getEffectiveTimeI());
		assertEquals(backToFront, invalidAxiom.getReferenceSetMember().getAdditionalField("owlExpression"));
	}

	@Test
	void testActivatingInvalidAxiomThrowsException() throws ServiceException {
		String intMain = "MAIN";
		Map<String, String> intPreferred = Map.of(US_EN_LANG_REFSET, descriptionAcceptabilityNames.get(PREFERRED), GB_EN_LANG_REFSET, descriptionAcceptabilityNames.get(PREFERRED));
		Map<String, String> intAcceptable = Map.of(US_EN_LANG_REFSET, descriptionAcceptabilityNames.get(PREFERRED), GB_EN_LANG_REFSET, descriptionAcceptabilityNames.get(ACCEPTABLE));
		String ci = "CASE_INSENSITIVE";
		Concept concept;
		CodeSystem codeSystem;

		// Create Concept
		concept = new Concept()
				.addDescription(new Description("Vehicle (vehicle)").setTypeId(FSN).setCaseSignificance(ci).setAcceptabilityMap(intPreferred))
				.addDescription(new Description("Vehicle").setTypeId(SYNONYM).setCaseSignificance(ci).setAcceptabilityMap(intPreferred))
				.addAxiom(new Relationship(ISA, SNOMEDCT_ROOT));
		concept = conceptService.create(concept, intMain);
		String vehicleId = concept.getConceptId();

		// Version
		codeSystem = codeSystemService.find("SNOMEDCT");
		codeSystemService.createVersion(codeSystem, 20240101, "20240101");

		// Create type of Vehicle (purposely without Axiom)
		concept = new Concept()
				.addDescription(new Description("Car (vehicle)").setTypeId(FSN).setCaseSignificance(ci).setAcceptabilityMap(intPreferred))
				.addDescription(new Description("Car").setTypeId(SYNONYM).setCaseSignificance(ci).setAcceptabilityMap(intPreferred))
				.addRelationship(new Relationship(ISA, vehicleId));
		concept = conceptService.create(concept, intMain);
		String carId = concept.getConceptId();

		// Manually create Axiom incorrectly (formatted back to front)
		ReferenceSetMember axiom = new ReferenceSetMember(CORE_MODULE, OWL_AXIOM_REFERENCE_SET, concept.getConceptId());
		String backToFront = String.format("SubClassOf(:%s :%s)", vehicleId, carId); // Note: vehicleId -> carId
		axiom.setAdditionalField("owlExpression", backToFront);
		axiom = referenceSetMemberService.createMember(intMain, axiom);
		String invalidAxiomId = axiom.getId();

		// Assert formatted incorrectly
		axiom = referenceSetMemberService.findMember(intMain, axiom.getMemberId());
		String owlExpression = axiom.getAdditionalField("owlExpression");
		assertEquals(backToFront, owlExpression);

		// Version error
		codeSystem = codeSystemService.find("SNOMEDCT");
		codeSystemService.createVersion(codeSystem, 20240201, "20240201");

		// Fix mistake (replace invalid with valid)
		axiom = referenceSetMemberService.findMember(intMain, invalidAxiomId);
		axiom.setActive(false);
		axiom.updateEffectiveTime();
		referenceSetMemberService.updateMember(intMain, axiom);

		axiom = new ReferenceSetMember(CORE_MODULE, OWL_AXIOM_REFERENCE_SET, concept.getConceptId());
		String frontToBack = String.format("SubClassOf(:%s :%s)", carId, vehicleId);  // Note: carId -> vehicleId
		axiom.setAdditionalField("owlExpression", frontToBack);
		axiom = referenceSetMemberService.createMember(intMain, axiom);
		String validAxiomId = axiom.getId();

		// Version correction
		codeSystem = codeSystemService.find("SNOMEDCT");
		codeSystemService.createVersion(codeSystem, 20240301, "20240301");

		// Inactivate concept
		concept = conceptService.find(carId, intMain);
		concept.setActive(false);
		concept.updateEffectiveTime();
		conceptService.update(concept, intMain);

		// Version inactivation
		codeSystem = codeSystemService.find("SNOMEDCT");
		codeSystemService.createVersion(codeSystem, 20240401, "20240401");

		// Assert inactive
		concept = conceptService.find(carId, intMain);
		assertFalse(concept.isActive());
		assertNotNull(concept.getEffectiveTime());

		// Reactivate concept
		concept = conceptService.find(carId, intMain);
		concept.setActive(true);
		concept.updateEffectiveTime();
		concept = conceptService.update(concept, intMain);

		// Reactivate valid axiom (allowed)
		for (Axiom classAxiom : concept.getClassAxioms()) {
			if (validAxiomId.equals(classAxiom.getAxiomId())) {
				classAxiom.setActive(true);
			}
		}
		concept = conceptService.update(concept, intMain);

		// Reactivate invalid axiom (not allowed)
		for (Axiom classAxiom : concept.getClassAxioms()) {
			if (invalidAxiomId.equals(classAxiom.getAxiomId())) {
				classAxiom.setActive(true);
			}
		}

		Concept finalConcept = concept;
		assertThrows(IllegalStateException.class, () -> {
			conceptService.update(finalConcept, intMain);
		});
	}

	@Test
	void copyConcepts_ShouldThrowException_WhenTargetIsVersionedBranch() throws ServiceException {
		String intMain = "MAIN";
		String extMain = "MAIN/SNOMEDCT-XX";
		Map<String, String> intPreferred = Map.of(US_EN_LANG_REFSET, descriptionAcceptabilityNames.get(PREFERRED), GB_EN_LANG_REFSET, descriptionAcceptabilityNames.get(PREFERRED));
		Map<String, String> intAcceptable = Map.of(US_EN_LANG_REFSET, descriptionAcceptabilityNames.get(PREFERRED), GB_EN_LANG_REFSET, descriptionAcceptabilityNames.get(ACCEPTABLE));
		String ci = "CASE_INSENSITIVE";
		Concept concept;
		Description description;
		CodeSystem codeSystem;

		// Create International Concept
		concept = new Concept()
				.addDescription(new Description("Medicine (medicine)").setTypeId(FSN).setCaseSignificance(ci).setAcceptabilityMap(intPreferred))
				.addDescription(new Description("Medicine").setTypeId(SYNONYM).setCaseSignificance(ci).setAcceptabilityMap(intPreferred))
				.addAxiom(new Relationship(ISA, SNOMEDCT_ROOT))
				.addRelationship(new Relationship(ISA, SNOMEDCT_ROOT));
		concept = conceptService.create(concept, intMain);
		String medicineId = concept.getConceptId();

		// Version International
		codeSystem = codeSystemService.find("SNOMEDCT");
		codeSystemService.createVersion(codeSystem, 20240101, "20240101");

		// Create Extension
		codeSystem = codeSystemService.createCodeSystem(new CodeSystem("SNOMEDCT-XX", extMain));
		concept = conceptService.create(
				new Concept()
						.addDescription(new Description("Extension module (module)").setTypeId(FSN).setCaseSignificance(ci).setAcceptabilityMap(intPreferred))
						.addDescription(new Description("Extension module").setTypeId(SYNONYM).setCaseSignificance(ci).setAcceptabilityMap(intPreferred))
						.addAxiom(new Relationship(ISA, MODULE)),
				extMain
		);
		String extModuleA = concept.getConceptId();
		branchService.updateMetadata(extMain, Map.of(Config.DEFAULT_MODULE_ID_KEY, extModuleA, Config.EXPECTED_EXTENSION_MODULES, List.of(extModuleA)));

		// Create Extension concept
		concept = new Concept()
				.setModuleId(extModuleA)
				.addDescription(new Description("Paracetamol (medicine)").setTypeId(FSN).setCaseSignificance(ci).setAcceptabilityMap(intPreferred))
				.addDescription(new Description("Paracetamol").setTypeId(SYNONYM).setCaseSignificance(ci).setAcceptabilityMap(intPreferred))
				.addAxiom(new Relationship(ISA, medicineId))
				.addRelationship(new Relationship(ISA, medicineId));
		concept = conceptService.create(concept, extMain);
		String paracetamolId = concept.getConceptId();

		// Version Extension
		codeSystem = codeSystemService.find("SNOMEDCT-XX");
		codeSystemService.createVersion(codeSystem, 20240102, "20240102");

		// Copy Extension to International
		assertThrows(ServiceException.class, () -> {
			conceptService.copyConcepts("<< " + paracetamolId, extMain, "MAIN/2024-01-01", true);
		});

		// Assert copying failed
		concept = conceptService.find(paracetamolId, "MAIN/2024-01-01");
		assertNull(concept);
	}

	private Description getDescriptionByTerm(Concept concept, String term) {
		if (concept == null) {
			return null;
		}

		for (Description description : concept.getDescriptions()) {
			if (term.equals(description.getTerm())) {
				return description;
			}
		}

		return null;
	}

	private void assertReleased(Concept concept, int effectiveTime) {
		assertTrue(concept.isReleased());
		assertEquals(effectiveTime, concept.getReleasedEffectiveTime());
		assertEquals(effectiveTime, concept.getEffectiveTimeI());
	}

	private boolean waitUntil(Supplier<Boolean> supplier, int maxSecondsToWait) {
		try {
			int sleptSeconds = 0;
			while (sleptSeconds < maxSecondsToWait && supplier.get() == Boolean.FALSE) {
				Thread.sleep(1_000);
				sleptSeconds++;
			}
			return sleptSeconds < maxSecondsToWait;
		} catch (InterruptedException e) {
			throw new RuntimeServiceException("Sleep interrupted.", e);
		}
	}

	private void printAllDescriptions(String path) throws TooCostlyException {
		final Page<Description> descriptions = descriptionService.findDescriptionsWithAggregations(path, new DescriptionCriteria(), ServiceTestUtil.PAGE_REQUEST);
		logger.info("Description on " + path);
		for (Description description : descriptions) {
			logger.info("{}", description);
		}
	}

	private Description fsn(String term) {
		Description description = new Description(term);
		description.setTypeId(FSN);
		description.addLanguageRefsetMember(US_EN_LANG_REFSET, PREFERRED);
		return description;
	}

}