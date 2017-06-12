package org.ihtsdo.elasticsnomed.core.data.services;

import com.google.common.collect.Sets;
import io.kaicode.elasticvc.api.BranchService;
import org.ihtsdo.elasticsnomed.TestConfig;
import org.ihtsdo.elasticsnomed.core.data.domain.*;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.*;
import java.util.stream.Collectors;

import static org.ihtsdo.elasticsnomed.core.data.domain.Concepts.*;
import static org.junit.Assert.assertEquals;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = TestConfig.class)
public class ConceptServiceTest {

	public static final PageRequest PAGE_REQUEST = new PageRequest(0, 100);

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

	private final Logger logger = LoggerFactory.getLogger(getClass());

	@Before
	public void setup() {
		branchService.create("MAIN");
	}

	@Test
	public void testConceptCreationBranchingVisibility() {
		Assert.assertNull("Concept 1 does not exist on MAIN.", conceptService.find("1", "MAIN"));

		conceptService.create(new Concept("1", "one"), "MAIN");

		final Concept c1 = conceptService.find("1", "MAIN");
		Assert.assertNotNull("Concept 1 exists on MAIN.", c1);
		assertEquals("MAIN", c1.getFatPath());
		assertEquals("one", c1.getModuleId());

		branchService.create("MAIN/A");
		conceptService.create(new Concept("2", "two"), "MAIN/A");
		Assert.assertNull("Concept 2 does not exist on MAIN.", conceptService.find("2", "MAIN"));
		Assert.assertNotNull("Concept 2 exists on branch A.", conceptService.find("2", "MAIN/A"));
		Assert.assertNotNull("Concept 1 is accessible on branch A because of the base time.", conceptService.find("1", "MAIN/A"));

		conceptService.create(new Concept("3", "three"), "MAIN");
		Assert.assertNull("Concept 3 is not accessible on branch A because created after branching.", conceptService.find("3", "MAIN/A"));
		Assert.assertNotNull(conceptService.find("3", "MAIN"));
	}

	@Test
	public void testDeleteDescription() {
		final Concept concept = conceptService.create(
				new Concept("1")
						.addDescription(new Description("1", "one"))
						.addDescription(new Description("2", "two"))
						.addDescription(new Description("3", "three"))
				, "MAIN");

		assertEquals(3, concept.getDescriptions().size());
		assertEquals(3, conceptService.find("1", "MAIN").getDescriptions().size());

		branchService.create("MAIN/one");
		branchService.create("MAIN/one/one-1");
		branchService.create("MAIN/two");

		concept.getDescriptions().remove(new Description("2", ""));
		final Concept updatedConcept = conceptService.update(concept, "MAIN/one");

		assertEquals(2, updatedConcept.getDescriptions().size());
		assertEquals(2, conceptService.find("1", "MAIN/one").getDescriptions().size());
		assertEquals(3, conceptService.find("1", "MAIN").getDescriptions().size());
		assertEquals(3, conceptService.find("1", "MAIN/one/one-1").getDescriptions().size());
		assertEquals(3, conceptService.find("1", "MAIN/two").getDescriptions().size());
	}

	@Test
	public void testDeleteLangMembersDuringDescriptionDeletion() {
		Concept concept = new Concept("123");
		Description fsn = fsn("Is a (attribute)");
		conceptService.create(concept
				.addDescription(
						fsn
								.addLanguageRefsetMember(Concepts.GB_EN_LANG_REFSET, Concepts.PREFERRED)
								.addLanguageRefsetMember(Concepts.US_EN_LANG_REFSET, Concepts.PREFERRED)

				), "MAIN");
		String descriptionId = fsn.getDescriptionId();
		Assert.assertNotNull(descriptionId);

		List<ReferenceSetMember> acceptabilityMembers = referenceSetMemberService.findMembers("MAIN", descriptionId, null, new PageRequest(0, 10)).getContent();
		Assert.assertEquals(2, acceptabilityMembers.size());
		Assert.assertTrue(acceptabilityMembers.get(0).isActive());
		Assert.assertTrue(acceptabilityMembers.get(1).isActive());

		concept.getDescriptions().clear();

		conceptService.update(concept, "MAIN");

		List<ReferenceSetMember> acceptabilityMembersAfterDescriptionDeletion = referenceSetMemberService.findMembers("MAIN", descriptionId, null, new PageRequest(0, 10)).getContent();
		Assert.assertEquals(0, acceptabilityMembersAfterDescriptionDeletion.size());
	}

	@Test
	public void testInactivateLangMembersDuringDescriptionInactivation() {
		Concept concept = new Concept("123");
		Description fsn = fsn("Is a (attribute)");
		conceptService.create(concept
				.addDescription(
						fsn
								.addLanguageRefsetMember(Concepts.GB_EN_LANG_REFSET, Concepts.PREFERRED)
								.addLanguageRefsetMember(Concepts.US_EN_LANG_REFSET, Concepts.PREFERRED)

				), "MAIN");
		String descriptionId = fsn.getDescriptionId();
		Assert.assertNotNull(descriptionId);

		List<ReferenceSetMember> acceptabilityMembers = referenceSetMemberService.findMembers("MAIN", descriptionId, null, new PageRequest(0, 10)).getContent();
		Assert.assertEquals(2, acceptabilityMembers.size());
		Assert.assertTrue(acceptabilityMembers.get(0).isActive());
		Assert.assertTrue(acceptabilityMembers.get(1).isActive());

		concept.getDescriptions().iterator().next().setActive(false);

		Concept updated = conceptService.update(concept, "MAIN");
		Assert.assertEquals(1, updated.getDescriptions().size());
		Description updatedDescription = updated.getDescriptions().iterator().next();
		Assert.assertFalse(updatedDescription.isActive());

		List<ReferenceSetMember> acceptabilityMembersAfterDescriptionDeletion = referenceSetMemberService.findMembers("MAIN", descriptionId, null, new PageRequest(0, 10)).getContent();
		Assert.assertEquals(0, acceptabilityMembersAfterDescriptionDeletion.size());
	}

	@Test
	public void testCreateDeleteRelationship() {
		conceptService.create(new Concept(ISA).setDefinitionStatusId(PRIMITIVE).addDescription(fsn("Is a (attribute)")), "MAIN");
		conceptService.create(new Concept(SNOMEDCT_ROOT).setDefinitionStatusId(PRIMITIVE).addDescription(fsn("SNOMED CT Concept")), "MAIN");

		final Concept concept = conceptService.create(
				new Concept("1")
						.addRelationship(new Relationship("1", ISA, SNOMEDCT_ROOT))
						.addRelationship(new Relationship("2", ISA, SNOMEDCT_ROOT))
						.addRelationship(new Relationship("3", ISA, SNOMEDCT_ROOT))
				, "MAIN");

		Relationship createdRelationship = concept.getRelationships().iterator().next();
		assertEquals("Creation response should contain FSN within relationship type", "Is a (attribute)", createdRelationship.type().getFsn());
		assertEquals("Creation response should contain definition status within relationship type", "PRIMITIVE", createdRelationship.type().getDefinitionStatus());
		assertEquals("Creation response should contain FSN within relationship target", "SNOMED CT Concept", createdRelationship.target().getFsn());
		assertEquals("Creation response should contain definition status within relationship target", "PRIMITIVE", createdRelationship.target().getDefinitionStatus());

		assertEquals(3, concept.getRelationships().size());
		Concept foundConcept = conceptService.find("1", "MAIN");
		assertEquals(3, foundConcept.getRelationships().size());

		Relationship foundRelationship = foundConcept.getRelationships().iterator().next();
		assertEquals("Find response should contain FSN within relationship type", "Is a (attribute)", foundRelationship.type().getFsn());
		assertEquals("Find response should contain definition status within relationship type", "PRIMITIVE", foundRelationship.type().getDefinitionStatus());
		assertEquals("Find response should contain FSN within relationship target", "SNOMED CT Concept", foundRelationship.target().getFsn());
		assertEquals("Find response should contain definition status within relationship target", "PRIMITIVE", foundRelationship.target().getDefinitionStatus());

		concept.getRelationships().remove(new Relationship("3"));
		final Concept updatedConcept = conceptService.update(concept, "MAIN");

		assertEquals(2, updatedConcept.getRelationships().size());
		assertEquals(2, conceptService.find("1", "MAIN").getRelationships().size());

		Relationship updatedRelationship = foundConcept.getRelationships().iterator().next();
		assertEquals("Update response should contain FSN within relationship type", "Is a (attribute)", updatedRelationship.type().getFsn());
		assertEquals("Update response should contain definition status within relationship type", "PRIMITIVE", updatedRelationship.type().getDefinitionStatus());
		assertEquals("Update response should contain FSN within relationship target", "SNOMED CT Concept", updatedRelationship.target().getFsn());
		assertEquals("Update response should contain definition status within relationship target", "PRIMITIVE", updatedRelationship.target().getDefinitionStatus());
	}

	@Test
	public void testMultipleConceptVersionsOnOneBranch() {
		assertEquals(0, conceptService.findAll("MAIN", PAGE_REQUEST).getTotalElements());
		conceptService.create(new Concept("1", "one"), "MAIN");

		final Concept concept1 = conceptService.find("1", "MAIN");
		assertEquals("one", concept1.getModuleId());
		assertEquals(1, conceptService.findAll("MAIN", PAGE_REQUEST).getTotalElements());

		conceptService.update(new Concept("1", "oneee"), "MAIN");

		final Concept concept1Version2 = conceptService.find("1", "MAIN");
		assertEquals("oneee", concept1Version2.getModuleId());
		assertEquals(1, conceptService.findAll("MAIN", PAGE_REQUEST).getTotalElements());
	}

	@Test
	public void testUpdateExistingConceptOnNewBranch() throws InterruptedException {
		conceptService.create(new Concept("1", "one"), "MAIN");

		branchService.create("MAIN/A");

		conceptService.update(new Concept("1", "one1"), "MAIN/A");

		assertEquals("one", conceptService.find("1", "MAIN").getModuleId());
		assertEquals("one1", conceptService.find("1", "MAIN/A").getModuleId());
	}

	@Test
	public void testOnlyUpdateWhatChanged() throws InterruptedException {
		final String effectiveTime = "20160731";

		conceptService.create(new Concept("1", effectiveTime, true, Concepts.CORE_MODULE, Concepts.PRIMITIVE)
				.addDescription(new Description("11", effectiveTime, true, Concepts.CORE_MODULE, null, "en",
						Concepts.FSN, "My Concept (finding)", Concepts.INITIAL_CHARACTER_CASE_INSENSITIVE))
				.addDescription(new Description("12", effectiveTime, true, Concepts.CORE_MODULE, null, "en",
						Concepts.SYNONYM, "My Concept", Concepts.INITIAL_CHARACTER_CASE_INSENSITIVE)),
				"MAIN");

		final Concept conceptAfterSave = conceptService.find("1", "MAIN");

		conceptAfterSave.getDescription("11").setActive(false);
		conceptService.update(conceptAfterSave, "MAIN");

		final Concept conceptAfterUpdate = conceptService.find("1", "MAIN");

		assertEquals("Concept document should not have been updated.",
				conceptAfterSave.getInternalId(), conceptAfterUpdate.getInternalId());
		assertEquals("Synonym document should not have been updated.",
				conceptAfterSave.getDescription("12").getInternalId(), conceptAfterUpdate.getDescription("12").getInternalId());
		Assert.assertNotEquals("FSN document should have been updated.",
				conceptAfterSave.getDescription("11").getInternalId(), conceptAfterUpdate.getDescription("11").getInternalId());

	}

	@Test
	public void testFindConceptOnParentBranchUsingBaseVersion() throws InterruptedException {
		conceptService.create(new Concept("1", "one"), "MAIN");
		conceptService.update(new Concept("1", "one1"), "MAIN");

		branchService.create("MAIN/A");

		conceptService.update(new Concept("1", "one2"), "MAIN");

		assertEquals("one2", conceptService.find("1", "MAIN").getModuleId());
		assertEquals("one1", conceptService.find("1", "MAIN/A").getModuleId());

		branchService.create("MAIN/A/A1");

		assertEquals("one1", conceptService.find("1", "MAIN/A/A1").getModuleId());

		conceptService.update(new Concept("1", "one3"), "MAIN/A");

		assertEquals("one1", conceptService.find("1", "MAIN/A/A1").getModuleId());
	}

	@Test
	public void testListConceptsOnGrandchildBranchWithUpdateOnChildBranch() {
		conceptService.create(new Concept("1", "orig value"), "MAIN");
		assertEquals("orig value", conceptService.find("1", "MAIN").getModuleId());

		branchService.create("MAIN/A");
		branchService.create("MAIN/A/A1");
		conceptService.update(new Concept("1", "updated value"), "MAIN/A");
		branchService.create("MAIN/A/A2");

		assertEquals("orig value", conceptService.find("1", "MAIN").getModuleId());
		assertEquals("updated value", conceptService.find("1", "MAIN/A").getModuleId());
		assertEquals("orig value", conceptService.find("1", "MAIN/A/A1").getModuleId());
		assertEquals("updated value", conceptService.find("1", "MAIN/A/A2").getModuleId());

		final Page<Concept> allOnGrandChild = conceptService.findAll("MAIN/A/A1", PAGE_REQUEST);
		assertEquals(1, allOnGrandChild.getTotalElements());
		assertEquals("orig value", allOnGrandChild.getContent().get(0).getModuleId());

		final Page<Concept> allOnChild = conceptService.findAll("MAIN/A", PAGE_REQUEST);
		assertEquals(1, allOnChild.getTotalElements());
		assertEquals("updated value", allOnChild.getContent().get(0).getModuleId());

		conceptService.update(new Concept("1", "updated value for A"), "MAIN/A");

		final Page<Concept> allOnChildAfterSecondUpdate = conceptService.findAll("MAIN/A", PAGE_REQUEST);
		assertEquals(1, allOnChildAfterSecondUpdate.getTotalElements());
		assertEquals("updated value for A", allOnChildAfterSecondUpdate.getContent().get(0).getModuleId());

		assertEquals("orig value", conceptService.find("1", "MAIN").getModuleId());
		assertEquals("updated value for A", conceptService.find("1", "MAIN/A").getModuleId());
		assertEquals("orig value", conceptService.find("1", "MAIN/A/A1").getModuleId());
		assertEquals("updated value", conceptService.find("1", "MAIN/A/A2").getModuleId());
	}

	@Test
	public void testSaveConceptWithDescription() {
		final Concept concept = new Concept("50960005", "20020131", true, "900000000000207008", "900000000000074008");
		concept.addDescription(new Description("84923010", "20020131", true, "900000000000207008", "50960005", "en", "900000000000013009", "Bleeding", "900000000000020002"));
		conceptService.create(concept, "MAIN");

		final Concept savedConcept = conceptService.find("50960005", "MAIN");
		Assert.assertNotNull(savedConcept);
		assertEquals(1, savedConcept.getDescriptions().size());
		final Description description = savedConcept.getDescriptions().iterator().next();
		assertEquals("84923010", description.getDescriptionId());
		assertEquals(0, description.getAcceptabilityMapFromLangRefsetMembers().size());
	}

	@Test
	public void testSaveConceptWithDescriptionAndAcceptabilityTogether() {
		final Concept concept = new Concept("50960005", "20020131", true, "900000000000207008", "900000000000074008");
		concept.addDescription(
				new Description("84923010", "20020131", true, "900000000000207008", "50960005", "en", "900000000000013009", "Bleeding", "900000000000020002")
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
	public void testChangeDescriptionAcceptabilityOnChildBranch() {
		final Concept concept = new Concept("50960005", "20020131", true, "900000000000207008", "900000000000074008");
		concept.addDescription(
				new Description("84923010", "20020131", true, "900000000000207008", "50960005", "en", "900000000000013009", "Bleeding", "900000000000020002")
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
	public void testDescriptionSearch() {
		createConceptWithPathIdAndTerms("MAIN", "1", "Heart");
		createConceptWithPathIdAndTerms("MAIN", "2", "Lung");
		createConceptWithPathIdAndTerms("MAIN", "6", "Foot cramps");
		createConceptWithPathIdAndTerms("MAIN", "7", "Foot cramp");
		createConceptWithPathIdAndTerms("MAIN", "3", "Foot bone");
		createConceptWithPathIdAndTerms("MAIN", "4", "Foot");
		createConceptWithPathIdAndTerms("MAIN", "5", "Footwear");

		final List<Description> content = descriptionService.findDescriptions("MAIN", "Foo* cr*", PAGE_REQUEST, conceptService).getContent();
		assertSearchResults(content, 5, "Foot cramp", "Foot cramps");

		final List<Description> fooMatches = descriptionService.findDescriptions("MAIN", "Foo*", PAGE_REQUEST, conceptService).getContent();
		assertSearchResults(fooMatches, 5);

		branchService.create("MAIN/A");

		createConceptWithPathIdAndTerms("MAIN/A", "8", "Foot care");

		final List<Description> footOnMain = descriptionService.findDescriptions("MAIN", "Foot", PAGE_REQUEST, conceptService).getContent();
		assertEquals(4, footOnMain.size());

		final List<Description> footOnA = descriptionService.findDescriptions("MAIN/A", "Foot", PAGE_REQUEST, conceptService).getContent();
		assertEquals(5, footOnA.size());
		Assert.assertTrue(toTermSet(footOnA).contains("Foot care"));
	}

	@Test
	public void testLatestVersionMatch() {
		createConceptWithPathIdAndTerms("MAIN", "1", "Heart");

		assertEquals(1, descriptionService.findDescriptions("MAIN", "Heart", PAGE_REQUEST, conceptService).getNumberOfElements());
		assertEquals(0, descriptionService.findDescriptions("MAIN", "Bone", PAGE_REQUEST, conceptService).getNumberOfElements());

		// Create branch (base point is now)
		branchService.create("MAIN/A");

		// Make further changes ahead of A's base point on MAIN
		final Concept concept = conceptService.find("1", "MAIN");
		concept.getDescriptions().iterator().next().setTerm("Bone");
		conceptService.update(concept, "MAIN");

		assertEquals(0, descriptionService.findDescriptions("MAIN", "Heart", PAGE_REQUEST, conceptService).getNumberOfElements());
		assertEquals(1, descriptionService.findDescriptions("MAIN", "Bone", PAGE_REQUEST, conceptService).getNumberOfElements());

		printAllDescriptions("MAIN");
		printAllDescriptions("MAIN/A");

		assertEquals("Branch A should see old version of concept because of old base point.", 1, descriptionService.findDescriptions("MAIN/A", "Heart", PAGE_REQUEST, conceptService).getNumberOfElements());
		assertEquals("Branch A should not see new version of concept because of old base point.", 0, descriptionService.findDescriptions("MAIN/A", "Bone", PAGE_REQUEST, conceptService).getNumberOfElements());

		final Concept concept1 = conceptService.find("1", "MAIN");
		assertEquals(1, concept1.getDescriptions().size());
	}

	@Test
	public void testRestoreEffectiveTime() {
		final String effectiveTime = "20170131";
		final String conceptId = "50960005";
		final String originalModuleId = "900000000000207008";
		final String path = "MAIN";

		final Concept concept = new Concept(conceptId, null, true, originalModuleId, "900000000000074008");
		conceptService.create(concept, path);
		conceptService.releaseSingleConceptForTest(concept, effectiveTime, path);

		final Concept savedConcept = conceptService.find(conceptId, path);
		assertEquals(effectiveTime, savedConcept.getEffectiveTime());
		assertEquals(effectiveTime, savedConcept.getReleasedEffectiveTime());
		assertEquals("true|900000000000207008|900000000000074008", savedConcept.getReleaseHash());
		Assert.assertTrue(savedConcept.isReleased());

		savedConcept.setModuleId("123");
		conceptService.update(savedConcept, "MAIN");

		final Concept conceptAfterUpdate = conceptService.find(conceptId, path);
		Assert.assertNull(conceptAfterUpdate.getEffectiveTime());
		assertEquals(effectiveTime, conceptAfterUpdate.getReleasedEffectiveTime());
		Assert.assertTrue(conceptAfterUpdate.isReleased());

		conceptAfterUpdate.setModuleId(originalModuleId);
		conceptService.update(conceptAfterUpdate, "MAIN");

		final Concept conceptWithRestoredDate = conceptService.find(conceptId, path);
		assertEquals(effectiveTime, conceptWithRestoredDate.getEffectiveTime());
		assertEquals(effectiveTime, conceptWithRestoredDate.getReleasedEffectiveTime());
		Assert.assertTrue(conceptWithRestoredDate.isReleased());
	}

	@Test
	public void testCreateUpdate10KConcepts() {
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

		final Iterable<Concept> conceptsCreated = conceptService.create(concepts, "MAIN/A");

		final Page<Concept> page = conceptService.findAll("MAIN/A", new PageRequest(0, 100));
		assertEquals(concepts.size() + 1, page.getTotalElements());
		assertEquals(Concepts.CORE_MODULE, page.getContent().get(50).getModuleId());

		Collection<ConceptMini> conceptDescendants = conceptService.findConceptDescendants(SNOMEDCT_ROOT, "MAIN/A", Relationship.CharacteristicType.stated);
		assertEquals(10 * 1000, conceptDescendants.size());

		List<Relationship> inboundRelationships = relationshipService.findInboundRelationships(SNOMEDCT_ROOT, "MAIN/A", Relationship.CharacteristicType.stated);
		assertEquals(10 * 1000, inboundRelationships.size());

		final String anotherModule = "123123";
		List<Concept> toUpdate = new ArrayList<>();
		conceptsCreated.forEach(concept -> {
			concept.setModuleId(anotherModule);
			toUpdate.add(concept);
		});

		conceptService.createUpdate(toUpdate, "MAIN/A");

		final Page<Concept> pageAfterUpdate = conceptService.findAll("MAIN/A", new PageRequest(0, 100));
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
		final Page<Description> descriptions = descriptionService.findDescriptions(path, null, PAGE_REQUEST, conceptService);
		logger.info("Description on " + path);
		for (Description description : descriptions) {
			logger.info("{}", description);
		}
	}

	private void assertSearchResults(List<Description> content, int expectedSize, String... topHitsUnordered) {
		try {
			assertEquals(expectedSize, content.size());
			final HashSet<String> topHitSet = Sets.newHashSet(topHitsUnordered);
			for (int i = 0; i < topHitsUnordered.length; i++) {
				final String term = content.get(i++).getTerm();
				Assert.assertTrue("Hit " + i + " '" + term + "' within expected top hits.", topHitSet.contains(term));
			}
		} catch (AssertionError e) {
			printAll(content);
			throw e;
		}
	}

	private void createConceptWithPathIdAndTerms(String path, String conceptId, String... terms) {
		final Concept concept = new Concept(conceptId);
		for (String term : terms) {
			final Description description = new Description(term);
			description.setDescriptionId(UUID.randomUUID().toString());
			concept.addDescription(description);
		}
		conceptService.create(concept, path);
	}

	private void printAll(List<Description> content) {
		logger.info(content.size() + " descriptions:");
		for (Description description : content) {
			logger.info(description.getTerm());
		}
	}

	private Set<String> toTermSet(Collection<Description> descriptions) {
		return descriptions.stream().map(Description::getTerm).collect(Collectors.toSet());
	}

	private Description fsn(String term) {
		Description description = new Description(term);
		description.setTypeId(FSN);
		return description;
	}

	@After
	public void tearDown() {
		conceptService.deleteAll();
		branchService.deleteAll();
	}
}