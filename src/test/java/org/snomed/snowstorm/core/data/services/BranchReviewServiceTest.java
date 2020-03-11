package org.snomed.snowstorm.core.data.services;

import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.domain.Branch;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.TestConfig;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.data.domain.review.BranchReview;
import org.snomed.snowstorm.core.data.domain.review.MergeReview;
import org.snomed.snowstorm.core.data.domain.review.MergeReviewConceptVersions;
import org.snomed.snowstorm.core.data.domain.review.ReviewStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.core.ElasticsearchTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.Assert.*;
import static org.snomed.snowstorm.config.Config.DEFAULT_LANGUAGE_DIALECTS;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = TestConfig.class)
public class BranchReviewServiceTest extends AbstractTest {

	@Autowired
	private BranchReviewService reviewService;

	@Autowired
	private ConceptService conceptService;

	@Autowired
	private BranchService branchService;

	@Autowired
	private BranchMergeService mergeService;

	@Autowired
	private ElasticsearchTemplate elasticsearchTemplate;

	private Date setupStartTime;
	private Date setupEndTime;

	private static final Long[] EMPTY_ARRAY = new Long[]{};

	@Before
	public void setUp() throws Exception {
		branchService.deleteAll();
		conceptService.deleteAll();

		branchService.create("MAIN");
		setupStartTime = now();
		createConcept("10000100", "MAIN");
		branchService.create("MAIN/A");
		setupEndTime = now();
	}

	@Test
	public void testCreateMergeReview() throws InterruptedException, ServiceException {
		conceptService.create(new Concept(Concepts.SNOMEDCT_ROOT), "MAIN");
		createConcept("116680003", "MAIN");
		createConcept("10000200", "MAIN");
		createConcept("10000300", "MAIN");
		createConcept("10000400", "MAIN");
		createConcept("10000500", "MAIN");
		createConcept("10000600", "MAIN");
		createConcept("700000000", "MAIN");
		createConcept("800000000", "MAIN");
		createConcept("900000000", "MAIN");

		// Rebase A
		mergeService.mergeBranchSync("MAIN", "MAIN/A", Collections.emptySet());


		// Create MAIN/B
		branchService.create("MAIN/B");

		// Update concept 10000100 FSN description only on B
		Concept concept = conceptService.find("10000100", "MAIN/B");
		getDescription(concept, true).setCaseSignificance("INITIAL_CHARACTER_CASE_INSENSITIVE");
		conceptService.update(concept, "MAIN/B");

		// Update concept 10000200 description on B and A
		concept = conceptService.find("10000200", "MAIN/B");
		getDescription(concept, true).setCaseSignificance("INITIAL_CHARACTER_CASE_INSENSITIVE");
		conceptService.update(concept, "MAIN/B");
		concept = conceptService.find("10000200", "MAIN/A");
		getDescription(concept, true).setCaseSignificance("ENTIRE_TERM_CASE_SENSITIVE");
		conceptService.update(concept, "MAIN/A");


		// Update concept 10000300 lang refset only on B
		concept = conceptService.find("10000300", "MAIN/B");
		getDescription(concept, true).getAcceptabilityMapAndClearMembers().put(Concepts.US_EN_LANG_REFSET, Concepts.descriptionAcceptabilityNames.get(Concepts.PREFERRED));
		conceptService.update(concept, "MAIN/B");

		// Update concept 10000400 lang refset on B and A
		concept = conceptService.find("10000400", "MAIN/B");
		getDescription(concept, true).getAcceptabilityMapAndClearMembers().put(Concepts.US_EN_LANG_REFSET, Concepts.descriptionAcceptabilityNames.get(Concepts.PREFERRED));
		conceptService.update(concept, "MAIN/B");
		concept = conceptService.find("10000400", "MAIN/A");
		getDescription(concept, true).getAcceptabilityMapAndClearMembers().put(Concepts.US_EN_LANG_REFSET, Concepts.descriptionAcceptabilityNames.get(Concepts.PREFERRED));
		conceptService.update(concept, "MAIN/A");


		// Update concept 10000500 relationship only on B
		concept = conceptService.find("10000500", "MAIN/B");
		concept.getRelationships().iterator().next().setGroupId(1);
		conceptService.update(concept, "MAIN/B");

		// Update concept 10000600 stated relationship on B and A
		concept = conceptService.find("10000600", "MAIN/B");
		concept.getRelationships().iterator().next().setGroupId(1);
		conceptService.update(concept, "MAIN/B");
		concept = conceptService.find("10000600", "MAIN/A");
		concept.getRelationships().iterator().next().setGroupId(1);
		conceptService.update(concept, "MAIN/A");


		// Add concept 700000000 axiom only on B
		concept = conceptService.find("700000000", "MAIN/B");
		concept.addAxiom(new Axiom().setRelationships(Collections.singleton(new Relationship(Concepts.ISA, "10000200"))));
		conceptService.update(concept, "MAIN/B");

		// Add concept 800000000 axiom on B and A
		concept = conceptService.find("800000000", "MAIN/B");
		concept.addAxiom(new Axiom().setRelationships(Collections.singleton(new Relationship(Concepts.ISA, "10000200"))));
		conceptService.update(concept, "MAIN/B");
		concept = conceptService.find("800000000", "MAIN/A");
		concept.addAxiom(new Axiom().setRelationships(Collections.singleton(new Relationship(Concepts.ISA, "10000200"))));
		conceptService.update(concept, "MAIN/A");

		// Add concept 900000000 axiom on B and inferred relationship on A - this should be ignored
		concept = conceptService.find("900000000", "MAIN/B");
		concept.addAxiom(new Axiom().setRelationships(Collections.singleton(new Relationship(Concepts.ISA, "10000200"))));
		conceptService.update(concept, "MAIN/B");
		concept = conceptService.find("900000000", "MAIN/A");
		concept.addRelationship(new Relationship(Concepts.ISA, "10000200").setCharacteristicTypeId(Concepts.INFERRED_RELATIONSHIP));
		conceptService.update(concept, "MAIN/A");


		// Promote B to MAIN
		mergeService.mergeBranchSync("MAIN/B", "MAIN", Collections.emptySet());

		Branch latestBranchA = branchService.findAtTimepointOrThrow("MAIN/A", now());
		Map<String, Set<String>> versionsReplaced = latestBranchA.getVersionsReplaced();
		assertEquals(0, versionsReplaced.getOrDefault(Concept.class.getSimpleName(), Collections.emptySet()).size());
		assertEquals(1, versionsReplaced.getOrDefault(Description.class.getSimpleName(), Collections.emptySet()).size());

		MergeReview review = createMergeReviewAndWaitUntilCurrent("MAIN", "MAIN/A");

		// Test that if a review exists the existing one can be found using the source and target branch state.
//		assertEquals(review.getSourceToTargetReviewId(), reviewService.getCreateReview("MAIN", "MAIN/A").getId());

		BranchReview sourceToTargetReview = reviewService.getBranchReview(review.getSourceToTargetReviewId());
		assertReportEquals(sourceToTargetReview.getChangedConcepts(), new Long[]{10000100L, 10000200L, 10000300L, 10000400L, 10000500L, 10000600L, 700000000L, 800000000L, 900000000L});
		BranchReview targetToSourceReview = reviewService.getBranchReview(review.getTargetToSourceReviewId());
		assertReportEquals(targetToSourceReview.getChangedConcepts(), new Long[]{10000200L, 10000400L, 10000600L, 800000000L});

		Collection<MergeReviewConceptVersions> mergeReviewConflictingConcepts = reviewService.getMergeReviewConflictingConcepts(review.getId(), DEFAULT_LANGUAGE_DIALECTS);
		Set<String> conceptIds = mergeReviewConflictingConcepts.stream().map(conceptVersions -> conceptVersions.getSourceConcept().getId()).collect(Collectors.toCollection(TreeSet::new));
		assertEquals("[10000200, 10000400, 10000600, 800000000]", conceptIds.toString());

		// Check that the child to parent branch review has the same content after a rebase.
		mergeService.mergeBranchSync("MAIN", "MAIN/A", Collections.emptySet());
		review = createMergeReviewAndWaitUntilCurrent("MAIN", "MAIN/A");
		targetToSourceReview = reviewService.getBranchReview(review.getTargetToSourceReviewId());
		assertReportEquals(targetToSourceReview.getChangedConcepts(), new Long[]{10000200L, 10000400L, 10000600L, 800000000L});
	}

	@Test
	public void testConflictsFoundOnMidLevelBranches() throws InterruptedException, ServiceException {
		// The story:
		// Make change on third level branch MAIN/A/A1
		// Promote to second level MAIN/A
		// Make change to same concept on unrelated third level branch MAIN/B/B1
		// Promote to second level and then MAIN
		// Rebase second level branch - should see conflicts

		conceptService.create(new Concept(Concepts.SNOMEDCT_ROOT), "MAIN");

		// Rebase A
		mergeService.mergeBranchSync("MAIN", "MAIN/A", Collections.emptySet());

		// Create MAIN/A/A1
		branchService.create("MAIN/A/A1");

		// Create MAIN/B
		branchService.create("MAIN/B");

		// Create MAIN/B/B1
		branchService.create("MAIN/B/B1");

		// Change on A1 (axiom change)
		String workingBranch = "MAIN/A/A1";
		Concept concept = conceptService.find("10000100", workingBranch);
		concept.getClassAxioms().iterator().next().setDefinitionStatusId(Concepts.FULLY_DEFINED);
		conceptService.update(concept, workingBranch);

		// Promote A1 to A
		mergeService.mergeBranchSync("MAIN/A/A1", "MAIN/A", Collections.emptySet());

		// Change on B1 (FSN lang refset)
		workingBranch = "MAIN/B/B1";
		concept = conceptService.find("10000100", workingBranch);
		getDescription(concept, true).getLangRefsetMembers()
				.get(Concepts.US_EN_LANG_REFSET).setAdditionalField(ReferenceSetMember.LanguageFields.ACCEPTABILITY_ID, Concepts.PREFERRED);
		conceptService.update(concept, workingBranch);

		// Promote B1 to B and to MAIN
		mergeService.mergeBranchSync("MAIN/B/B1", "MAIN/B", Collections.emptySet());
		mergeService.mergeBranchSync("MAIN/B", "MAIN", Collections.emptySet());

		MergeReview review = createMergeReviewAndWaitUntilCurrent("MAIN", "MAIN/A");
		BranchReview sourceToTargetReview = reviewService.getBranchReview(review.getSourceToTargetReviewId());
		assertReportEquals(sourceToTargetReview.getChangedConcepts(), new Long[]{10000100L});
		BranchReview targetToSourceReview = reviewService.getBranchReview(review.getTargetToSourceReviewId());
		assertReportEquals(targetToSourceReview.getChangedConcepts(), new Long[]{10000100L});

		Collection<MergeReviewConceptVersions> mergeReviewConflictingConcepts = reviewService.getMergeReviewConflictingConcepts(review.getId(), DEFAULT_LANGUAGE_DIALECTS);
		Set<String> conceptIds = mergeReviewConflictingConcepts.stream().map(conceptVersions -> conceptVersions.getSourceConcept().getId()).collect(Collectors.toCollection(TreeSet::new));
		assertEquals("[10000100]", conceptIds.toString());
	}

	@Test
	public void testCreateMergeReviewWithConceptDeletedOnParentAndFsnUpdatedOnChild() throws InterruptedException, ServiceException {
		// Update concept 10000100 FSN on A
		Concept concept = conceptService.find("10000100", "MAIN/A");
		getDescription(concept, true).setCaseSignificance("INITIAL_CHARACTER_CASE_INSENSITIVE");
		conceptService.update(concept, "MAIN/A");

		// Delete concept 10000100 on MAIN
		conceptService.deleteConceptAndComponents("10000100", "MAIN", false);

		MergeReview review = createMergeReviewAndWaitUntilCurrent("MAIN", "MAIN/A");

		Collection<MergeReviewConceptVersions> mergeReviewConflictingConcepts = reviewService.getMergeReviewConflictingConcepts(review.getId(), DEFAULT_LANGUAGE_DIALECTS);
		assertEquals(1, mergeReviewConflictingConcepts.size());
		Set<String> conceptIds = mergeReviewConflictingConcepts.stream().map(conceptVersions -> conceptVersions.getTargetConcept().getId()).collect(Collectors.toSet());
		assertTrue(conceptIds.contains("10000100"));
	}

	@Test
	public void testCreateMergeReviewWithConceptDeletedOnParentAndSynonymUpdatedOnChild() throws InterruptedException, ServiceException {
		// Update concept 10000100 FSN on A
		Concept concept = conceptService.find("10000100", "MAIN/A");
		getDescription(concept, false).setCaseSignificance("INITIAL_CHARACTER_CASE_INSENSITIVE");
		conceptService.update(concept, "MAIN/A");

		// Delete concept 10000100 on MAIN
		conceptService.deleteConceptAndComponents("10000100", "MAIN", false);

		MergeReview review = createMergeReviewAndWaitUntilCurrent("MAIN", "MAIN/A");

		Collection<MergeReviewConceptVersions> mergeReviewConflictingConcepts = reviewService.getMergeReviewConflictingConcepts(review.getId(), DEFAULT_LANGUAGE_DIALECTS);
		assertEquals(0, mergeReviewConflictingConcepts.size());
	}

	@Test
	public void testCreateMergeReviewConceptDeletedOnChild() throws InterruptedException, ServiceException {
		// Update concept 10000100 description on A
		Concept concept = conceptService.find("10000100", "MAIN");
		getDescription(concept, true).setCaseSignificance("INITIAL_CHARACTER_CASE_INSENSITIVE");
		conceptService.update(concept, "MAIN");

		// Delete concept 10000100 on MAIN
		conceptService.deleteConceptAndComponents("10000100", "MAIN/A", false);

		MergeReview review = createMergeReviewAndWaitUntilCurrent("MAIN", "MAIN/A");

		Collection<MergeReviewConceptVersions> mergeReviewConflictingConcepts = reviewService.getMergeReviewConflictingConcepts(review.getId(), DEFAULT_LANGUAGE_DIALECTS);
		assertEquals(1, mergeReviewConflictingConcepts.size());
		Set<String> conceptIds = mergeReviewConflictingConcepts.stream().map(conceptVersions -> conceptVersions.getSourceConcept().getId()).collect(Collectors.toSet());
		assertTrue(conceptIds.contains("10000100"));
	}

	@Test
	public void testCreateConceptChangeReportOnBranchSinceTimepoint() throws Exception {
		// Assert report contains one new concept on MAIN since start of setup
		assertReportEquals(reviewService.createConceptChangeReportOnBranchForTimeRange("MAIN", setupStartTime, now(), true), new Long[]{10000100L});

		// Assert report contains no concepts on MAIN/A since setup
		assertReportEquals(reviewService.createConceptChangeReportOnBranchForTimeRange("MAIN/A", setupEndTime, now(), false), EMPTY_ARRAY);

		final Date beforeSecondCreation = now();
		createConcept("10000200", "MAIN/A");

		// Assert report contains no new concepts on MAIN/A since start of setup
		assertReportEquals(reviewService.createConceptChangeReportOnBranchForTimeRange("MAIN/A", setupEndTime, now(), false), new Long[]{10000200L});

		// Assert report contains one new concept on MAIN/A since timeA
		assertReportEquals(reviewService.createConceptChangeReportOnBranchForTimeRange("MAIN/A", beforeSecondCreation, now(), false), new Long[]{10000200L});

		final Date beforeDeletion = now();

		// Delete concept 100 from MAIN
		conceptService.deleteConceptAndComponents("10000100", "MAIN", false);

		final Date afterDeletion = now();

		// Assert report contains one deleted concept on MAIN
		assertReportEquals(reviewService.createConceptChangeReportOnBranchForTimeRange("MAIN", beforeDeletion, now(), true), new Long[]{10000100L});


		// Assert report contains no deleted concepts on MAIN before the deletion
		assertReportEquals(reviewService.createConceptChangeReportOnBranchForTimeRange("MAIN", beforeSecondCreation, beforeDeletion, true), EMPTY_ARRAY);

		// Assert report contains no deleted concepts on MAIN after the deletion
		assertReportEquals(reviewService.createConceptChangeReportOnBranchForTimeRange("MAIN", afterDeletion, now(), true), EMPTY_ARRAY);
	}

	@Test
	public void testDescriptionUpdateOnSameBranchInChangeReport() throws Exception {
		final String path = "MAIN";
		createConcept("10000200", path);
		createConcept("10000300", path);

		Date start = now();

		// Nothing changed since start
		assertReportEquals(reviewService.createConceptChangeReportOnBranchForTimeRange(path, start, now(), true), EMPTY_ARRAY);

		final Concept concept = conceptService.find("10000100", path);
		getDescription(concept, true).setCaseSignificanceId(Concepts.ENTIRE_TERM_CASE_SENSITIVE);
		conceptService.update(concept, path);

		// Concept updated from description change
		assertReportEquals(reviewService.createConceptChangeReportOnBranchForTimeRange(path, start, now(), true), new Long[] {10000100L});
	}

	@Test
	public void testDescriptionUpdateOnChildBranchInChangeReport() throws Exception {
		final String path = "MAIN/A";
		createConcept("10000200", path);
		createConcept("10000300", path);

		Date start = now();

		// Nothing changed since start
		assertReportEquals(reviewService.createConceptChangeReportOnBranchForTimeRange(path, start, now(), false), EMPTY_ARRAY);

		final Concept concept = conceptService.find("10000100", path);
		final Description description = getDescription(concept, true);
		description.setCaseSignificanceId(Concepts.ENTIRE_TERM_CASE_SENSITIVE);
		conceptService.update(concept, path);

		// Concept updated from description change
		assertReportEquals(reviewService.createConceptChangeReportOnBranchForTimeRange(path, start, now(), false), new Long[] {10000100L});
	}

	@Test
	public void testAxiomUpdateOnSameBranchInChangeReport() throws Exception {
		final String path = "MAIN";
		createConcept("10000200", path);
		createConcept("10000300", path);

		Date start = now();

		// Nothing changed since start
		assertReportEquals(reviewService.createConceptChangeReportOnBranchForTimeRange(path, start, now(), true), EMPTY_ARRAY);

		final Concept concept = conceptService.find("10000100", path);
		concept.getClassAxioms().iterator().next().setDefinitionStatusId(Concepts.FULLY_DEFINED);
		conceptService.update(concept, path);

		// Concept updated from axiom change
		assertReportEquals(reviewService.createConceptChangeReportOnBranchForTimeRange(path, start, now(), true), new Long[] {10000100L});
	}

	@Test
	public void testAxiomUpdateOnSameBranchNotMAINInChangeReport() throws Exception {
		final String path = "MAIN/A";
		createConcept("10000200", path);
		createConcept("10000300", path);

		Date start = now();

		// Nothing changed since start
		assertReportEquals(reviewService.createConceptChangeReportOnBranchForTimeRange(path, start, now(), true), EMPTY_ARRAY);

		final Concept concept = conceptService.find("10000100", path);
		concept.getClassAxioms().iterator().next().setDefinitionStatusId(Concepts.FULLY_DEFINED);
		conceptService.update(concept, path);

		// Concept updated from axiom change
		assertReportEquals(reviewService.createConceptChangeReportOnBranchForTimeRange(path, start, now(), true), new Long[] {10000100L});
	}

	@Test
	public void testAxiomUpdateOnGrandfatherBranchInChangeReport() throws Exception {
		branchService.create("MAIN/A/B");

		Date start = now();

		// Nothing changed since start
		assertReportEquals(reviewService.createConceptChangeReportOnBranchForTimeRange("MAIN/A/B", start, now(), true), EMPTY_ARRAY);

		final Concept concept = conceptService.find("10000100", "MAIN");
		concept.getClassAxioms().iterator().next().setDefinitionStatusId(Concepts.FULLY_DEFINED);
		conceptService.update(concept, "MAIN");

		mergeService.mergeBranchSync("MAIN", "MAIN/A", Collections.emptySet());

		assertReportEquals(reviewService.createConceptChangeReportOnBranchForTimeRange("MAIN/A", start, now(), true), new Long[] {10000100L});
		assertReportEquals(reviewService.createConceptChangeReportOnBranchForTimeRange("MAIN/A/B", start, now(), true), EMPTY_ARRAY);

		mergeService.mergeBranchSync("MAIN/A", "MAIN/A/B", Collections.emptySet());

		assertReportEquals(reviewService.createConceptChangeReportOnBranchForTimeRange("MAIN/A", start, now(), true), new Long[] {10000100L});
		assertReportEquals(reviewService.createConceptChangeReportOnBranchForTimeRange("MAIN/A/B", start, now(), true), new Long[] {10000100L});
	}

	@Test
	public void testAxiomUpdateOnChildBranchInChangeReport() throws Exception {
		final String path = "MAIN/A";
		createConcept("10000200", path);
		createConcept("10000300", path);

		Date start = now();

		// Nothing changed since start
		assertReportEquals(reviewService.createConceptChangeReportOnBranchForTimeRange(path, start, now(), false), EMPTY_ARRAY);

		final Concept concept = conceptService.find("10000100", path);
		concept.getClassAxioms().iterator().next().setDefinitionStatusId(Concepts.FULLY_DEFINED);
		conceptService.update(concept, path);

		// Concept updated from axiom change
		assertReportEquals(reviewService.createConceptChangeReportOnBranchForTimeRange(path, start, now(), false), new Long[] {10000100L});
	}

	@Test
	public void testLangRefsetUpdateOnChildBranchInChangeReport() throws Exception {
		final String path = "MAIN/A";

		Date start = now();

		// Nothing changed since start
		assertReportEquals(reviewService.createConceptChangeReportOnBranchForTimeRange(path, start, now(), false), EMPTY_ARRAY);

		final Concept concept = conceptService.find("10000100", path);
		final Description description = getDescription(concept, true);
		description.clearLanguageRefsetMembers();
		description.setAcceptabilityMap(Collections.singletonMap(Concepts.US_EN_LANG_REFSET,
				Concepts.descriptionAcceptabilityNames.get(Concepts.PREFERRED)));
		conceptService.update(concept, path);

		// Concept updated from lang refset change
		assertReportEquals(reviewService.createConceptChangeReportOnBranchForTimeRange(path, start, now(), false), new Long[] {10000100L});
	}

	@Test
	public void testLangRefsetDeletionOnChildBranchInChangeReport() throws Exception {
		final String path = "MAIN/A";

		Date start = now();

		// Nothing changed since start
		assertReportEquals(reviewService.createConceptChangeReportOnBranchForTimeRange(path, start, now(), false), EMPTY_ARRAY);

		final Concept concept = conceptService.find("10000100", path);
		final Description description = getDescription(concept, true);
		description.clearLanguageRefsetMembers();
		description.setAcceptabilityMap(new HashMap<>());
		conceptService.update(concept, path);

		// Concept updated from lang refset change
		assertReportEquals(reviewService.createConceptChangeReportOnBranchForTimeRange(path, start, now(), false), new Long[] {10000100L});
	}

	@Test
	public void testLangRefsetDeletionOnBranchInChangeReport() throws Exception {
		final String path = "MAIN";

		Date start = now();

		// Nothing changed since start
		assertReportEquals(reviewService.createConceptChangeReportOnBranchForTimeRange(path, start, now(), true), EMPTY_ARRAY);

		final Concept concept = conceptService.find("10000100", path);
		final Description next = getDescription(concept, true);
		next.clearLanguageRefsetMembers();
		next.setAcceptabilityMap(new HashMap<>());
		conceptService.update(concept, path);

		// Concept updated from lang refset change
		assertReportEquals(reviewService.createConceptChangeReportOnBranchForTimeRange(path, start, now(), true), new Long[] {10000100L});
	}

	private MergeReview createMergeReviewAndWaitUntilCurrent(String sourceBranch, String targetBranch) throws InterruptedException {
		MergeReview review = reviewService.createMergeReview(sourceBranch, targetBranch);

		long maxWait = 10;
		long cumulativeWait = 0;
		while (review.getStatus() == ReviewStatus.PENDING && cumulativeWait < maxWait) {
			Thread.sleep(1_000);
			cumulativeWait++;
		}

		assertEquals(ReviewStatus.CURRENT, review.getStatus());
		return review;
	}

	private Date now() {
		return new Date();
	}

	private void assertReportEquals(Set<Long> changedConcepts, Long[] expectedConceptsChanged) {
		List<Long> ids = new ArrayList<>(changedConcepts);
		ids.sort(null);
		System.out.println("changed " + Arrays.toString(ids.toArray()));
		Assert.assertArrayEquals("Concepts Changed", expectedConceptsChanged, ids.toArray());
	}

	private void createConcept(String conceptId, String path) throws ServiceException {
		conceptService.create(
				new Concept(conceptId)
						.addDescription(
								new Description("Heart")
										.setCaseSignificance("CASE_INSENSITIVE")
										.setAcceptabilityMap(Collections.singletonMap(Concepts.US_EN_LANG_REFSET,
												Concepts.descriptionAcceptabilityNames.get(Concepts.ACCEPTABLE)))
						)
						.addDescription(
								new Description("Heart structure (body structure)")
										.setTypeId(Concepts.FSN)
										.setCaseSignificance("CASE_INSENSITIVE")
										.setAcceptabilityMap(Collections.singletonMap(Concepts.US_EN_LANG_REFSET,
												Concepts.descriptionAcceptabilityNames.get(Concepts.ACCEPTABLE))))
						.addRelationship(
								new Relationship(Concepts.ISA, Concepts.SNOMEDCT_ROOT)
						)
						.addAxiom(
								new Relationship(Concepts.ISA, Concepts.SNOMEDCT_ROOT)
						),
				path);
	}

	private Description getDescription(Concept concept, boolean fetchFSN) {
		if (concept == null || concept.getDescriptions() == null || concept.getDescriptions().isEmpty()) {
			return null;
		}
		if (fetchFSN) {
			List<Description> descriptions = concept.getDescriptions().stream().filter(d -> Concepts.FSN.equals(d.getTypeId())).collect(Collectors.toList());
			if (descriptions.iterator().hasNext()) {
				return descriptions.iterator().next();
			}
		} else {
			List<Description> descriptions = concept.getDescriptions().stream().filter(d -> !Concepts.FSN.equals(d.getTypeId())).collect(Collectors.toList());
			if (descriptions.iterator().hasNext()) {
				return descriptions.iterator().next();
			}
		}
		return null;
	}

}
