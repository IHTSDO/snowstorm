package org.snomed.snowstorm.core.data.services;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import io.kaicode.elasticvc.api.BranchCriteria;
import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.api.VersionControlHelper;
import io.kaicode.elasticvc.domain.Branch;
import io.kaicode.elasticvc.domain.Commit;
import io.kaicode.elasticvc.domain.Metadata;
import org.assertj.core.util.Maps;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.data.domain.review.BranchReview;
import org.snomed.snowstorm.core.data.domain.review.MergeReview;
import org.snomed.snowstorm.core.data.domain.review.MergeReviewConceptVersions;
import org.snomed.snowstorm.core.data.domain.review.ReviewStatus;
import org.snomed.snowstorm.core.data.services.pojo.MemberSearchRequest;
import org.snomed.snowstorm.core.data.services.traceability.Activity;
import org.snomed.snowstorm.core.data.services.traceability.TraceabilityConsumer;
import org.snomed.snowstorm.core.data.services.traceability.TraceabilityLogService;
import org.snomed.snowstorm.rest.pojo.MergeRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static io.kaicode.elasticvc.api.ComponentService.LARGE_PAGE;
import static java.util.function.Predicate.not;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.snomed.snowstorm.config.Config.DEFAULT_LANGUAGE_DIALECTS;
import static org.snomed.snowstorm.core.data.domain.Concepts.*;
import static org.snomed.snowstorm.core.data.domain.review.ReviewStatus.CURRENT;
import static org.snomed.snowstorm.core.data.domain.review.ReviewStatus.PENDING;
import static org.snomed.snowstorm.core.data.services.CodeSystemService.SNOMEDCT;

@ExtendWith(SpringExtension.class)
class BranchMergeServiceTest extends AbstractTest {

	@Autowired
	private BranchMergeService branchMergeService;

	@Autowired
	private BranchReviewService reviewService;

	@Autowired
	private BranchService branchService;

	@Autowired
	private ConceptService conceptService;

	@Autowired
	private DescriptionService descriptionService;

	@Autowired
	private RelationshipService relationshipService;

	@Autowired
	private ReferenceSetMemberService memberService;

	@Autowired
	private QueryService queryService;

	@Autowired
	private TraceabilityLogService traceabilityLogService;

	@Autowired
	private ElasticsearchRestTemplate elasticsearchTemplate;

	@Autowired
	private VersionControlHelper versionControlHelper;

	@Autowired
	private AdminOperationsService adminOperationsService;

	private List<Activity> activities;

	private Map<String, Branch> childBranches;

	@Autowired
	private CodeSystemService codeSystemService;
	
	@BeforeEach
	void setup() throws ServiceException, InterruptedException {
		conceptService.deleteAll();

		branchService.updateMetadata(Branch.MAIN, new Metadata().putString(BranchMetadataKeys.ASSERTION_GROUP_NAMES, "common-authoring"));
		conceptService.create(new Concept(Concepts.SNOMEDCT_ROOT), Branch.MAIN);
		CodeSystem rootCS = new CodeSystem(SNOMEDCT, Branch.MAIN);
		codeSystemService.createCodeSystem(rootCS);
		codeSystemService.createVersion(rootCS, 20190131, "20190131");
		
		//If we have a parent code system we cannot create the codesystem on an existing branch
		codeSystemService.createCodeSystem(new CodeSystem("SNOMEDCT-A", "MAIN/A"));
		branchService.create("MAIN/A/A1");
		branchService.create("MAIN/A/A2");
		branchService.create("MAIN/C");


		traceabilityLogService.setEnabled(true);
		activities = new ArrayList<>();
		traceabilityLogService.setTraceabilityConsumer(new TraceabilityConsumer() {
			@Override
			public void accept(Activity activity) {
				activities.add(activity);
			}
		});

		// set up the branches for testing find children
		setUpForChildBranchesTest();
	}

	@AfterEach
	void tearDown() throws InterruptedException {
		conceptService.deleteAll();
		traceabilityLogService.setEnabled(false);
	}

	@Test
	void testNewConceptWithoutComponentsRebaseAndPromotion() throws Exception {
		assertBranchState("MAIN", Branch.BranchState.UP_TO_DATE);
		assertBranchState("MAIN/A", Branch.BranchState.UP_TO_DATE);
		assertBranchState("MAIN/A/A1", Branch.BranchState.UP_TO_DATE);
		assertBranchState("MAIN/A/A2", Branch.BranchState.UP_TO_DATE);
		assertBranchState("MAIN/C", Branch.BranchState.UP_TO_DATE);

		// Create concept on A1
		final String conceptId = "10000123";
		conceptService.create(new Concept(conceptId), "MAIN/A/A1");
		assertBranchStateAndConceptVisibility("MAIN", Branch.BranchState.UP_TO_DATE, conceptId, false);
		assertBranchStateAndConceptVisibility("MAIN/A", Branch.BranchState.UP_TO_DATE, conceptId, false);
		assertBranchStateAndConceptVisibility("MAIN/A/A1", Branch.BranchState.FORWARD, conceptId, true);
		assertBranchStateAndConceptVisibility("MAIN/A/A2", Branch.BranchState.UP_TO_DATE, conceptId, false);
		assertBranchStateAndConceptVisibility("MAIN/C", Branch.BranchState.UP_TO_DATE, conceptId, false);

		// Promote to A
		branchMergeService.mergeBranchSync("MAIN/A/A1", "MAIN/A", null);
		Activity activity = getLatestTraceabilityActivity();
		assertEquals(Activity.ActivityType.PROMOTION, activity.getActivityType());
		assertEquals("MAIN/A/A1", activity.getSourceBranch());
		assertEquals("MAIN/A", activity.getBranchPath());
		assertBranchStateAndConceptVisibility("MAIN", Branch.BranchState.UP_TO_DATE, conceptId, false);
		assertBranchStateAndConceptVisibility("MAIN/A", Branch.BranchState.FORWARD, conceptId, true);
		assertBranchStateAndConceptVisibility("MAIN/A/A1", Branch.BranchState.UP_TO_DATE, conceptId, true);
		assertBranchStateAndConceptVisibility("MAIN/A/A2", Branch.BranchState.BEHIND, conceptId, false);
		assertBranchStateAndConceptVisibility("MAIN/C", Branch.BranchState.UP_TO_DATE, conceptId, false);

		// Rebase to A2
		assertEquals(1, branchService.findAllVersions("MAIN/A/A2", LARGE_PAGE).getTotalElements(), "Before rebase versions.");
		Date beforeRebaseTimepoint = new Date();
		Branch branchA2BeforeRebase = branchService.findAtTimepointOrThrow("MAIN/A/A2", beforeRebaseTimepoint);

		branchMergeService.mergeBranchSync("MAIN/A", "MAIN/A/A2", null);

		assertEquals(2, branchService.findAllVersions("MAIN/A/A2", LARGE_PAGE).getTotalElements(), "Before rebase, another version of the branch created.");
		assertEquals(branchA2BeforeRebase.getBase(), branchService.findAtTimepointOrThrow("MAIN/A/A2", beforeRebaseTimepoint).getBase(),
				"The base timepoint of the original version of the branch should not have changed.");

		activity = getLatestTraceabilityActivity();
		assertEquals(Activity.ActivityType.REBASE, activity.getActivityType());
		assertEquals("MAIN/A", activity.getSourceBranch());
		assertEquals("MAIN/A/A2", activity.getBranchPath());
		assertBranchStateAndConceptVisibility("MAIN", Branch.BranchState.UP_TO_DATE, conceptId, false);
		assertBranchStateAndConceptVisibility("MAIN/A", Branch.BranchState.FORWARD, conceptId, true);
		assertBranchStateAndConceptVisibility("MAIN/A/A1", Branch.BranchState.UP_TO_DATE, conceptId, true);
		assertBranchStateAndConceptVisibility("MAIN/A/A2", Branch.BranchState.UP_TO_DATE, conceptId, true);
		assertBranchStateAndConceptVisibility("MAIN/C", Branch.BranchState.UP_TO_DATE, conceptId, false);

		// Promote to MAIN
		branchMergeService.mergeBranchSync("MAIN/A", "MAIN", null);
		assertBranchStateAndConceptVisibility("MAIN", Branch.BranchState.UP_TO_DATE, conceptId, true);
		assertBranchStateAndConceptVisibility("MAIN/A", Branch.BranchState.UP_TO_DATE, conceptId, true);
		assertBranchStateAndConceptVisibility("MAIN/A/A1", Branch.BranchState.BEHIND, conceptId, true);
		assertBranchStateAndConceptVisibility("MAIN/A/A2", Branch.BranchState.BEHIND, conceptId, true);
		assertBranchStateAndConceptVisibility("MAIN/C", Branch.BranchState.BEHIND, conceptId, false);

		// Delete from MAIN
		conceptService.deleteConceptAndComponents(conceptId, "MAIN", false);
		assertBranchStateAndConceptVisibility("MAIN", Branch.BranchState.UP_TO_DATE, conceptId, false);
		assertBranchStateAndConceptVisibility("MAIN/A", Branch.BranchState.BEHIND, conceptId, true);
		assertBranchStateAndConceptVisibility("MAIN/A/A1", Branch.BranchState.BEHIND, conceptId, true);
		assertBranchStateAndConceptVisibility("MAIN/A/A2", Branch.BranchState.BEHIND, conceptId, true);
		assertBranchStateAndConceptVisibility("MAIN/C", Branch.BranchState.BEHIND, conceptId, false);

		// Rebase to C
		branchMergeService.mergeBranchSync("MAIN", "MAIN/C", null);
		assertBranchStateAndConceptVisibility("MAIN", Branch.BranchState.UP_TO_DATE, conceptId, false);
		assertBranchStateAndConceptVisibility("MAIN/A", Branch.BranchState.BEHIND, conceptId, true);
		assertBranchStateAndConceptVisibility("MAIN/A/A1", Branch.BranchState.BEHIND, conceptId, true);
		assertBranchStateAndConceptVisibility("MAIN/A/A2", Branch.BranchState.BEHIND, conceptId, true);
		assertBranchStateAndConceptVisibility("MAIN/C", Branch.BranchState.UP_TO_DATE, conceptId, false);

		// Check MAIN metadata still present
		Branch mainBranch = branchService.findLatest("MAIN");
		assertNotNull(mainBranch.getMetadata());
		System.out.println(mainBranch.getMetadata());
		assertEquals(2, mainBranch.getMetadata().size());
		assertEquals("common-authoring", mainBranch.getMetadata().getString(BranchMetadataKeys.ASSERTION_GROUP_NAMES));

		adminOperationsService.hardDeleteBranch("MAIN/C");
		assertNull(branchService.findLatest("MAIN/C"));
		assertBranchStateAndConceptVisibility("MAIN", Branch.BranchState.UP_TO_DATE, conceptId, false);
		assertBranchStateAndConceptVisibility("MAIN/A", Branch.BranchState.BEHIND, conceptId, true);
		assertBranchStateAndConceptVisibility("MAIN/A/A1", Branch.BranchState.BEHIND, conceptId, true);
		assertBranchStateAndConceptVisibility("MAIN/A/A2", Branch.BranchState.BEHIND, conceptId, true);
	}

	private Activity getLatestTraceabilityActivity() {
		return activities.get(activities.size() - 1);
	}

	@Test
	void testPromotionOfConceptAndDescriptions() throws ServiceException {
		assertBranchState("MAIN", Branch.BranchState.UP_TO_DATE);
		assertBranchState("MAIN/A", Branch.BranchState.UP_TO_DATE);
		assertBranchState("MAIN/A/A1", Branch.BranchState.UP_TO_DATE);
		assertBranchState("MAIN/A/A2", Branch.BranchState.UP_TO_DATE);
		assertBranchState("MAIN/C", Branch.BranchState.UP_TO_DATE);

		// - Create a concepts on A1 and A2
		final String concept1 = "10000100";
		final String concept2 = "10000200";
		conceptService.create(new Concept(concept1)
						.addDescription(new Description("One"))
						.addAxiom(new Relationship(Concepts.ISA, Concepts.SNOMEDCT_ROOT)),
				"MAIN/A/A1");
		List<ConceptMini> content = queryService.eclSearch(">" + concept1, true, "MAIN/A/A1", PageRequest.of(0, 10)).getContent();
		assertEquals(1, content.size());
		assertEquals(Concepts.SNOMEDCT_ROOT, content.get(0).getConceptId());

		conceptService.create(new Concept(concept2)
						.addDescription(new Description("1000021", "Two1"))
						.addDescription(new Description("1000022", "Two2")),
				"MAIN/A/A2");

		// MAIN can't see concepts
		assertBranchStateAndConceptVisibility("MAIN", Branch.BranchState.UP_TO_DATE, concept1, false);
		assertConceptNotVisible("MAIN", concept2);

		// MAIN/A can't see concepts
		assertBranchStateAndConceptVisibility("MAIN/A", Branch.BranchState.UP_TO_DATE, concept1, false);
		assertConceptNotVisible("MAIN/A", concept2);

		// MAIN/A/A1 can see concept1 but not concept2
		Concept concept1OnA1 = assertBranchStateAndConceptVisibility("MAIN/A/A1", Branch.BranchState.FORWARD, concept1, true);
		assertNotNull(concept1OnA1);
		assertEquals(1, concept1OnA1.getDescriptions().size());
		assertConceptNotVisible("MAIN/A/A1", concept2);

		// MAIN/A/A2 can see concept2 but not concept1
		final Concept concept2OnA2 = assertBranchStateAndConceptVisibility("MAIN/A/A2", Branch.BranchState.FORWARD, concept2, true);
		assertNotNull(concept2OnA2);
		assertEquals(2, concept2OnA2.getDescriptions().size());
		assertConceptNotVisible("MAIN/A/A2", concept1);

		// MAIN/C can't see concepts
		assertBranchStateAndConceptVisibility("MAIN/C", Branch.BranchState.UP_TO_DATE, concept1, false);
		assertConceptNotVisible("MAIN/C", concept2);


		// - Promote A1 to A
		branchMergeService.mergeBranchSync("MAIN/A/A1", "MAIN/A", null);

		// MAIN can't see
		assertBranchStateAndConceptVisibility("MAIN", Branch.BranchState.UP_TO_DATE, concept1, false);

		// MAIN/A can see concept1 and descriptions but not concept2
		Concept concept1OnA = assertBranchStateAndConceptVisibility("MAIN/A", Branch.BranchState.FORWARD, concept1, true);
		assertNotNull(concept1OnA);
		assertEquals(1, concept1OnA.getDescriptions().size());
		assertConceptNotVisible("MAIN/A", concept2);

		// MAIN/A/A1 is up-to-date and can still see concept1 but not concept2
		concept1OnA1 = assertBranchStateAndConceptVisibility("MAIN/A/A1", Branch.BranchState.UP_TO_DATE, concept1, true);
		assertNotNull(concept1OnA1);
		assertEquals(1, concept1OnA1.getDescriptions().size());
		assertConceptNotVisible("MAIN/A/A1", concept2);
		content = queryService.eclSearch(">" + concept1, true, "MAIN/A/A1", PageRequest.of(0, 10)).getContent();
		assertEquals(1, content.size());
		assertEquals(Concepts.SNOMEDCT_ROOT, content.get(0).getConceptId());


		// MAIN/A/A2 is now diverged because there are changes on the branch and it's parent
		assertBranchStateAndConceptVisibility("MAIN/A/A2", Branch.BranchState.DIVERGED, concept2, true);

		// MAIN/C remains the same
		assertBranchState("MAIN/C", Branch.BranchState.UP_TO_DATE);


		// - Rebase A2
		final List<Concept> emptyListOfConflictMerges = Collections.emptyList();// Merge review will result in nothing to merge
		branchMergeService.mergeBranchSync("MAIN/A", "MAIN/A/A2", emptyListOfConflictMerges);

		// MAIN can't see
		assertBranchStateAndConceptVisibility("MAIN", Branch.BranchState.UP_TO_DATE, concept1, false);

		// MAIN/A can see concept1 and descriptions but not concept2
		concept1OnA = assertBranchStateAndConceptVisibility("MAIN/A", Branch.BranchState.FORWARD, concept1, true);
		assertNotNull(concept1OnA);
		assertEquals(1, concept1OnA.getDescriptions().size());
		assertConceptNotVisible("MAIN/A", concept2);

		// MAIN/A/A1 is up-to-date and can still see concept1 but not concept2
		assertBranchStateAndConceptVisibility("MAIN/A/A1", Branch.BranchState.UP_TO_DATE, concept1, true);
		assertConceptNotVisible("MAIN/A/A1", concept2);

		// MAIN/A/A2 is now forward because there are changes on the branch but now it's got it's parent's changes
		assertBranchStateAndConceptVisibility("MAIN/A/A2", Branch.BranchState.FORWARD, concept2, true);
		final Concept concept1OnA2 = assertConceptVisible("MAIN/A/A2", concept1);
		assertEquals(1, concept1OnA2.getDescriptions().size());

		// MAIN/C remains the same
		assertBranchState("MAIN/C", Branch.BranchState.UP_TO_DATE);


		// - Promote A to MAIN
		branchMergeService.mergeBranchSync("MAIN/A", "MAIN", null);

		// MAIN can see concept1! but not concept2 because it wasn't promoted to A before A was promoted
		assertBranchStateAndConceptVisibility("MAIN", Branch.BranchState.UP_TO_DATE, concept1, true);
		assertConceptNotVisible("MAIN", concept2);

		// MAIN/A is now up-to-date and can still see concept1 but not concept2
		assertBranchStateAndConceptVisibility("MAIN/A", Branch.BranchState.UP_TO_DATE, concept1, true);
		assertConceptNotVisible("MAIN/A", concept2);

		// MAIN/A/A1 is behind because there was a change on it's parent branch. That change was promotion.
		assertBranchStateAndConceptVisibility("MAIN/A/A1", Branch.BranchState.BEHIND, concept1, true);

		// MAIN/A/A2 is diverged because there was a change on A (promotion)
		assertBranchStateAndConceptVisibility("MAIN/A/A2", Branch.BranchState.DIVERGED, concept2, true);

		// MAIN/C is behind because MAIN has changes - concept1 just landed
		assertBranchState("MAIN/C", Branch.BranchState.BEHIND);


		// - Rebase A2 and promote to A
		branchMergeService.mergeBranchSync("MAIN/A", "MAIN/A/A2", emptyListOfConflictMerges);
		branchMergeService.mergeBranchSync("MAIN/A/A2", "MAIN/A", null);

		// MAIN can see concept1 but not concept2
		assertBranchStateAndConceptVisibility("MAIN", Branch.BranchState.UP_TO_DATE, concept1, true);
		assertConceptNotVisible("MAIN", concept2);

		// MAIN/A has changes again (concept2)
		assertBranchStateAndConceptVisibility("MAIN/A", Branch.BranchState.FORWARD, concept1, true);
		final Concept concept2OnA = assertConceptVisible("MAIN/A", concept2);
		assertEquals(2, concept2OnA.getDescriptions().size());

		// MAIN/A/A1 is still behind and can't see concept2
		assertBranchStateAndConceptVisibility("MAIN/A/A1", Branch.BranchState.BEHIND, concept1, true);
		assertConceptNotVisible("MAIN", concept2);

		assertBranchStateAndConceptVisibility("MAIN/A/A2", Branch.BranchState.UP_TO_DATE, concept2, true);

		assertBranchState("MAIN/C", Branch.BranchState.BEHIND);


		// - Promote A to MAIN
		branchMergeService.mergeBranchSync("MAIN/A", "MAIN", null);

		// MAIN can see concept1 and concept2
		assertBranchStateAndConceptVisibility("MAIN", Branch.BranchState.UP_TO_DATE, concept1, true);
		assertConceptVisible("MAIN", concept2);

		// MAIN/A is up to date
		assertBranchStateAndConceptVisibility("MAIN/A", Branch.BranchState.UP_TO_DATE, concept1, true);
		assertConceptVisible("MAIN/A", concept2);
	}

	@Test
	void testRebaseCapturesChangesAcrossBranchesForTransitiveClosureIncrementalUpdate() throws ServiceException {
		assertBranchState("MAIN", Branch.BranchState.UP_TO_DATE);
		assertBranchState("MAIN/A", Branch.BranchState.UP_TO_DATE);
		assertBranchState("MAIN/A/A1", Branch.BranchState.UP_TO_DATE);

		// MAIN: 100001 Root
		System.out.println("// MAIN: 1 Root");
		conceptService.create(new Concept("100001"), "MAIN");

		// MAIN: 100002 -> 100001
		System.out.println("// MAIN: 100002 -> 100001");
		conceptService.create(new Concept("100002").addAxiom(new Relationship(Concepts.ISA, "100001")), "MAIN");

		// Rebase MAIN/A
		System.out.println("// Rebase MAIN/A");
		assertBranchState("MAIN/A", Branch.BranchState.BEHIND);
		branchMergeService.mergeBranchSync("MAIN", "MAIN/A", null);

		// MAIN: 4 -> 1
		System.out.println("// MAIN: 100004 -> 100001");
		conceptService.create(new Concept("100004").addAxiom(new Relationship(Concepts.ISA, "100001")), "MAIN");

		// MAIN: 2 -> 4
		System.out.println("// MAIN: 100002 -> 100004");
		conceptService.update(new Concept("100002").addAxiom(new Relationship(Concepts.ISA, "100004")), "MAIN");
		assertEquals(Sets.newHashSet(100001L, 100004L), queryService.findAncestorIds("100002", "MAIN", true));

		// MAIN/A: 3 -> 2
		System.out.println("// MAIN/A: 100003 -> 100002");
		conceptService.create(new Concept("100003").addAxiom(new Relationship(Concepts.ISA, "100002")), "MAIN/A");
		assertEquals(Sets.newHashSet(100001L, 100002L), queryService.findAncestorIds("100003", "MAIN/A", true));

		// Rebase MAIN/A
		System.out.println("// Rebase MAIN/A");
		assertBranchState("MAIN/A", Branch.BranchState.DIVERGED);

		branchMergeService.mergeBranchSync("MAIN", "MAIN/A", Collections.emptySet());

		assertEquals(Sets.newHashSet(100001L, 100004L), queryService.findAncestorIds("100002", "MAIN/A", true));
		assertEquals(Sets.newHashSet(100001L, 100004L, 100002L), queryService.findAncestorIds("100003", "MAIN/A", true));
	}

	@Test
	void testConflictWithoutManualMergeSupplied() throws ServiceException {
		final String concept1 = "10000000";
		final Concept concept = new Concept(concept1);
		conceptService.batchCreate(Collections.singletonList(concept), "MAIN/A/A1");
		conceptService.batchCreate(Collections.singletonList(concept), "MAIN/A");
		try {
			branchMergeService.mergeBranchSync("MAIN/A", "MAIN/A/A1", null);
			fail("Should have thrown IllegalArgumentException");
		} catch (IllegalArgumentException e) {
			assertEquals("The target branch is diverged, please use the merge review endpoint instead.", e.getMessage());
		}
	}

	@Test
	void testConflictConceptMergeChangesFromLeftIncludingStatedModeling() throws ServiceException {
		// Create concepts to be used in relationships
		conceptService.createUpdate(Lists.newArrayList(
				new Concept(Concepts.SNOMEDCT_ROOT),
				new Concept(Concepts.ISA).addAxiom(new Relationship(Concepts.ISA, Concepts.SNOMEDCT_ROOT)),
				new Concept(Concepts.CLINICAL_FINDING).addAxiom(new Relationship(Concepts.ISA, Concepts.SNOMEDCT_ROOT)),
				new Concept("131148009").addAxiom(new Relationship(Concepts.ISA, Concepts.SNOMEDCT_ROOT)),
				new Concept("313413008").addAxiom(new Relationship(Concepts.ISA, Concepts.SNOMEDCT_ROOT))
		), "MAIN");
		branchMergeService.mergeBranchSync("MAIN", "MAIN/A", Collections.emptySet());
		branchMergeService.mergeBranchSync("MAIN/A", "MAIN/A/A1", Collections.emptySet());
		branchMergeService.mergeBranchSync("MAIN/A", "MAIN/A/A2", Collections.emptySet());

		// Setup merge scenario
		final String conceptId = "10000100";
		final Description description = new Description("One");

		Concept leftConcept = new Concept(conceptId, "100009002")
				.addDescription(description)
				.addAxiom(new Relationship(Concepts.ISA, Concepts.CLINICAL_FINDING), new Relationship(Concepts.ISA, "131148009"));
		setupConflictSituation(
				new Concept(conceptId, "100009001")
						.addDescription(description)
						.addAxiom(new Relationship(Concepts.ISA, Concepts.CLINICAL_FINDING)),
				leftConcept,
				new Concept(conceptId, "100009003")
						.addDescription(description)
						.addAxiom(new Relationship(Concepts.ISA, Concepts.CLINICAL_FINDING), new Relationship(Concepts.ISA, "313413008"))
		);

		// Rebase the diverged branch supplying the manually merged concept
		branchMergeService.mergeBranchSync("MAIN/A", "MAIN/A/A2", Collections.singleton(leftConcept));

		assertEquals("100009002", conceptService.find(conceptId, "MAIN/A/A2").getModuleId());
		assertEquals(toLongSet(Concepts.SNOMEDCT_ROOT, Concepts.CLINICAL_FINDING, "131148009"), queryService.findAncestorIds(conceptId, "MAIN/A/A2", true));

		// Promote the branch (no conflicts at this point)
		branchMergeService.mergeBranchSync("MAIN/A/A2", "MAIN/A", null);
		assertEquals("100009002", conceptService.find(conceptId, "MAIN/A").getModuleId());
		assertEquals(toLongSet(Concepts.SNOMEDCT_ROOT, Concepts.CLINICAL_FINDING, "131148009"), queryService.findAncestorIds(conceptId, "MAIN/A", true));
	}

	@Test
	void testAutomaticMergeOfConceptDoubleInactivationDifferentReasons() throws ServiceException {
		// The same concept is made inactive on two different branches with different inactivation reasons and historical associations.
		// The concept comes up in the rebase review and the picked version should be kept.
		// The redundant inactivation reason and historical association must be removed.

		// Create concepts to be used in test
		String conceptAId = "131148009";
		String conceptBId = "313413008";
		conceptService.createUpdate(Lists.newArrayList(
				new Concept(Concepts.SNOMEDCT_ROOT),
				new Concept(Concepts.ISA).addAxiom(new Relationship(Concepts.ISA, Concepts.SNOMEDCT_ROOT)),
				new Concept(Concepts.CLINICAL_FINDING).addAxiom(new Relationship(Concepts.ISA, Concepts.SNOMEDCT_ROOT)),
				new Concept(conceptAId).addAxiom(new Relationship(Concepts.ISA, Concepts.CLINICAL_FINDING)).addDescription(new Description("thingamajig")),
				new Concept(conceptBId).addAxiom(new Relationship(Concepts.ISA, Concepts.CLINICAL_FINDING))
		), "MAIN");
		branchMergeService.mergeBranchSync("MAIN", "MAIN/A", Collections.emptySet());
		String taskA1 = "MAIN/A/A1";
		branchMergeService.mergeBranchSync("MAIN/A", taskA1, Collections.emptySet());
		String taskA2 = "MAIN/A/A2";
		branchMergeService.mergeBranchSync("MAIN/A", taskA2, Collections.emptySet());

		// On branch A1 inactivate conceptA with AMBIGUOUS reason and Equivalent association
		Concept concept = simulateRestTransfer(conceptService.find(conceptAId, taskA1));
		concept.setActive(false);
		concept.setInactivationIndicator("AMBIGUOUS");
		concept.setAssociationTargets(Maps.newHashMap("POSSIBLY_EQUIVALENT_TO", Sets.newHashSet(conceptBId)));
		conceptService.update(concept, taskA1);

		System.out.println("All members");
		for (ReferenceSetMember referenceSetMember : memberService.findMembers(taskA1, new MemberSearchRequest(), LARGE_PAGE).getContent()) {
			System.out.println(referenceSetMember.getReferencedComponentId() + " - " + referenceSetMember.getRefsetId() + " - " + referenceSetMember);
		}
		System.out.println("All members end");

		assertEquals(2, memberService.findMembers(taskA1, conceptAId, LARGE_PAGE).getTotalElements());
		MemberSearchRequest descriptionInactivationMemberSearchRequest = new MemberSearchRequest()
				.referenceSet(Concepts.DESCRIPTION_INACTIVATION_INDICATOR_REFERENCE_SET)
				.referencedComponentId(concept.getDescriptions().iterator().next().getId());
		assertEquals(1, memberService.findMembers(taskA1, descriptionInactivationMemberSearchRequest, LARGE_PAGE).getTotalElements());

		// On branch A2 inactivate conceptA with OUTDATED reason and
		concept = simulateRestTransfer(conceptService.find(conceptAId, taskA2));
		concept.setActive(false);
		concept.setInactivationIndicator("OUTDATED");
		concept.setAssociationTargets(Maps.newHashMap("REPLACED_BY", Sets.newHashSet(conceptBId)));
		conceptService.update(concept, taskA2);
		assertEquals(2, memberService.findMembers(taskA2, conceptAId, LARGE_PAGE).getTotalElements());
		assertEquals(1, memberService.findMembers(taskA2, descriptionInactivationMemberSearchRequest, LARGE_PAGE).getTotalElements());

		// Promote task A1
		assertEquals(1, memberService.findMembers("MAIN/A", conceptAId, LARGE_PAGE).getTotalElements());
		branchMergeService.mergeBranchSync(taskA1, "MAIN/A", Collections.emptySet());
		assertEquals(2, memberService.findMembers("MAIN/A", conceptAId, LARGE_PAGE).getTotalElements());
		assertEquals(1, memberService.findMembers(taskA1, descriptionInactivationMemberSearchRequest, LARGE_PAGE).getTotalElements());

		// Rebase the diverged branch supplying the A2 concept version as the manually merged concept
		// Serialise and deserialise to simulate transfer over REST. References to specific refset members will be lost.
		concept = simulateRestTransfer(concept);
		branchMergeService.mergeBranchSync("MAIN/A", taskA2, Collections.singleton(concept));
		Page<ReferenceSetMember> members = memberService.findMembers(taskA2, conceptAId, LARGE_PAGE);
		members.getContent().forEach(System.out::println);
		assertEquals(2, members.getTotalElements());
		assertEquals(1, memberService.findMembers(taskA2, descriptionInactivationMemberSearchRequest, LARGE_PAGE).getTotalElements());
	}

	@Test
	void testManualMergeOfConceptDoubleInactivationAssociationChange() throws ServiceException {
		// The concept is already inactive with release two association reasons, one inactive one active
		// The concept association is changed on two tasks, the first task is promoted, the second rebased
		// The rebase has a conflict so a merged concept is required
		// The completed rebase must have only three association refset members,
		// the two existing released members as inactive and the one new association which survived the merge.

		// Create concepts to be used in test
		String conceptId = "100000000";
		String path = "MAIN";
		conceptService.createUpdate(Lists.newArrayList(
				new Concept(Concepts.SNOMEDCT_ROOT),
				new Concept(Concepts.ISA).addAxiom(new Relationship(Concepts.ISA, Concepts.SNOMEDCT_ROOT)),
				new Concept(Concepts.CLINICAL_FINDING).addAxiom(new Relationship(Concepts.ISA, Concepts.SNOMEDCT_ROOT)),
				new Concept(conceptId).addAxiom(new Relationship(Concepts.ISA, Concepts.CLINICAL_FINDING)).addDescription(new Description("thingamajig"))
		), path);

		//We must version now, otherwise the concept inactivation inactivates the axiom
		//and that would cause deletion if not published. 
		codeSystemService.createVersion(codeSystemService.find(SNOMEDCT), 20190731, "");
		
		Concept concept = simulateRestTransfer(conceptService.find(conceptId, path));
		concept.setActive(false);
		concept.setInactivationIndicator("ERRONEOUS");
		concept.setAssociationTargets(Maps.newHashMap("POSSIBLY_EQUIVALENT_TO", Sets.newHashSet(Concepts.CLINICAL_FINDING)));
		conceptService.update(concept, path);

		codeSystemService.createVersion(codeSystemService.find(SNOMEDCT), 20200131, "");

		Page<ReferenceSetMember> memberPage = memberService.findMembers(path, conceptId, PageRequest.of(0, 10));
		assertEquals(3, memberPage.getTotalElements(), "One axiom, one published association member, one published historic association.");

		branchService.create("MAIN/B");
		branchService.create("MAIN/B/B1");
		branchService.create("MAIN/B/B2");

		path = "MAIN/B/B1";
		concept = simulateRestTransfer(conceptService.find(conceptId, path));
		concept.setInactivationIndicator("AMBIGUOUS");
		concept.setAssociationTargets(Maps.newHashMap("POSSIBLY_EQUIVALENT_TO", Sets.newHashSet(Concepts.SNOMEDCT_ROOT)));
		conceptService.update(concept, path);
		for (ReferenceSetMember member : memberService.findMembers(path, conceptId, PageRequest.of(0, 10))) {
			System.out.println(member.toString());
		}
		assertEquals(5, memberService.findMembers(path, conceptId, PageRequest.of(0, 10)).getTotalElements(), "One axiom, one inactive published association, one inactive published historic association, " +
				"one new association and one new inactivation reason member."
		);

		path = "MAIN/B/B2";
		concept = simulateRestTransfer(conceptService.find(conceptId, path));
		concept.setInactivationIndicator("AMBIGUOUS");
		concept.setAssociationTargets(Maps.newHashMap("POSSIBLY_EQUIVALENT_TO", Sets.newHashSet(Concepts.ISA)));
		conceptService.update(concept, path);
		assertEquals(5, memberService.findMembers(path, conceptId, PageRequest.of(0, 10)).getTotalElements(), "One axiom, one inactive published association, one inactive published historic association, " +
				"one new association and one new inactivation reason member."
		);

		// Promote B2
		branchMergeService.mergeBranchSync("MAIN/B/B2", "MAIN/B", Collections.emptySet());

		// Rebase B1 with merge
		path = "MAIN/B/B1";
		concept = simulateRestTransfer(conceptService.find(conceptId, path));
		branchMergeService.mergeBranchSync("MAIN/B", "MAIN/B/B1", Collections.singleton(concept));

		for (ReferenceSetMember member : memberService.findMembers(path, conceptId, PageRequest.of(0, 10))) {
			System.out.println(member.toString());
		}
		Page<ReferenceSetMember> members = memberService.findMembers(path, conceptId, PageRequest.of(0, 10));

		// Check for duplicate UUIDs
		Map<String, AtomicInteger> memberIdCounts = new HashMap<>();
		members.forEach(member -> memberIdCounts.computeIfAbsent(member.getId(), (id) -> new AtomicInteger()).incrementAndGet());
		assertFalse(memberIdCounts.values().stream().anyMatch(value -> value.get() > 1), "No duplicate refset members.");

		assertEquals(5, members.getTotalElements(), "One axiom, one inactive published association, one inactive published historic association, " +
				"one new association and one new inactivation reason member.");
		concept = conceptService.find(conceptId, path);
		assertEquals("AMBIGUOUS", concept.getInactivationIndicator());
		assertEquals("{POSSIBLY_EQUIVALENT_TO=[138875005]}", concept.getAssociationTargets().toString());
	}

	@Test
	void testAutomaticMergeOfConceptDoubleInactivationSameReasons() throws ServiceException {
		// The same concept is made inactive on two different branches with the same inactivation reasons and historical associations.
		// The concept comes up in the rebase review and the picked version should be kept.
		// The redundant inactivation reason and historical association must be removed.

		// Create concepts to be used in test
		String conceptAId = "131148009";
		String conceptBId = "313413008";
		conceptService.createUpdate(Lists.newArrayList(
				new Concept(Concepts.SNOMEDCT_ROOT),
				new Concept(Concepts.ISA).addAxiom(new Relationship(Concepts.ISA, Concepts.SNOMEDCT_ROOT)),
				new Concept(Concepts.CLINICAL_FINDING).addAxiom(new Relationship(Concepts.ISA, Concepts.SNOMEDCT_ROOT)),
				new Concept(conceptAId).addAxiom(new Relationship(Concepts.ISA, Concepts.CLINICAL_FINDING)).addDescription(new Description("thingamajig")),
				new Concept(conceptBId).addAxiom(new Relationship(Concepts.ISA, Concepts.CLINICAL_FINDING))
		), "MAIN");
		branchMergeService.mergeBranchSync("MAIN", "MAIN/A", Collections.emptySet());
		String taskA1 = "MAIN/A/A1";
		branchMergeService.mergeBranchSync("MAIN/A", taskA1, Collections.emptySet());
		String taskA2 = "MAIN/A/A2";
		branchMergeService.mergeBranchSync("MAIN/A", taskA2, Collections.emptySet());

		// On branch A1 inactivate conceptA with AMBIGUOUS reason and Equivalent association
		Concept concept = conceptService.find(conceptAId, taskA1);
		concept.setActive(false);
		concept.setInactivationIndicator("AMBIGUOUS");
		concept.setAssociationTargets(Maps.newHashMap("POSSIBLY_EQUIVALENT_TO", Sets.newHashSet(conceptBId)));
		conceptService.update(concept, taskA1);

		System.out.println("All members");
		for (ReferenceSetMember referenceSetMember : memberService.findMembers(taskA1, new MemberSearchRequest(), LARGE_PAGE).getContent()) {
			System.out.println(referenceSetMember.getReferencedComponentId() + " - " + referenceSetMember.getRefsetId() + " - " + referenceSetMember);
		}
		System.out.println("All members end");

		assertEquals(2, memberService.findMembers(taskA1, conceptAId, LARGE_PAGE).getTotalElements());
		MemberSearchRequest descriptionInactivationMemberSearchRequest = new MemberSearchRequest()
				.referenceSet(Concepts.DESCRIPTION_INACTIVATION_INDICATOR_REFERENCE_SET)
				.referencedComponentId(concept.getDescriptions().iterator().next().getId());
		assertEquals(1, memberService.findMembers(taskA1, descriptionInactivationMemberSearchRequest, LARGE_PAGE).getTotalElements());

		// On branch A2 inactivate conceptA with OUTDATED reason and
		concept = conceptService.find(conceptAId, taskA2);
		concept.setActive(false);
		concept.setInactivationIndicator("AMBIGUOUS");
		concept.setAssociationTargets(Maps.newHashMap("POSSIBLY_EQUIVALENT_TO", Sets.newHashSet(conceptBId)));
		conceptService.update(concept, taskA2);
		assertEquals(2, memberService.findMembers(taskA2, conceptAId, LARGE_PAGE).getTotalElements());
		assertEquals(1, memberService.findMembers(taskA2, descriptionInactivationMemberSearchRequest, LARGE_PAGE).getTotalElements());

		// Promote task A1
		assertEquals(1, memberService.findMembers("MAIN/A", conceptAId, LARGE_PAGE).getTotalElements());
		branchMergeService.mergeBranchSync(taskA1, "MAIN/A", Collections.emptySet());
		assertEquals(2, memberService.findMembers("MAIN/A", conceptAId, LARGE_PAGE).getTotalElements());
		assertEquals(1, memberService.findMembers(taskA1, descriptionInactivationMemberSearchRequest, LARGE_PAGE).getTotalElements());

		// Rebase the diverged branch supplying the A2 concept version as the manually merged concept
		// Serialise and deserialise to simulate transfer over REST. References to specific refset members will be lost.
		concept = simulateRestTransfer(concept);
		branchMergeService.mergeBranchSync("MAIN/A", taskA2, Collections.singleton(concept));
		Page<ReferenceSetMember> members = memberService.findMembers(taskA2, conceptAId, LARGE_PAGE);
		members.getContent().forEach(System.out::println);
		assertEquals(2, members.getTotalElements());
		assertEquals(1, memberService.findMembers(taskA2, descriptionInactivationMemberSearchRequest, LARGE_PAGE).getTotalElements());
	}

	@Test
	void testAutomaticMergeOfConceptDoubleInactivationSameReasonsOneReleased() throws ServiceException {
		// The same concept is made inactive on two different branches with the same inactivation reasons and historical associations.
		// The first concept is promoted and released.
		// During merge the user attempts to keep the task version and replace the released version.
		// The released version must be kept despite the users intention.

		// Create concepts to be used in test
		String conceptAId = "131148009";
		String conceptBId = "313413008";
		conceptService.createUpdate(Lists.newArrayList(
				new Concept(Concepts.SNOMEDCT_ROOT),
				new Concept(Concepts.ISA).addAxiom(new Relationship(Concepts.ISA, Concepts.SNOMEDCT_ROOT)),
				new Concept(Concepts.CLINICAL_FINDING).addAxiom(new Relationship(Concepts.ISA, Concepts.SNOMEDCT_ROOT)),
				new Concept(conceptAId).addAxiom(new Relationship(Concepts.ISA, Concepts.CLINICAL_FINDING)).addDescription(new Description("thingamajig")),
				new Concept(conceptBId).addAxiom(new Relationship(Concepts.ISA, Concepts.CLINICAL_FINDING))
		), "MAIN");
		branchMergeService.mergeBranchSync("MAIN", "MAIN/A", Collections.emptySet());
		String taskA1 = "MAIN/A/A1";
		branchMergeService.mergeBranchSync("MAIN/A", taskA1, Collections.emptySet());
		String taskA2 = "MAIN/A/A2";
		branchMergeService.mergeBranchSync("MAIN/A", taskA2, Collections.emptySet());

		// On branch A1 inactivate conceptA with AMBIGUOUS reason and Equivalent association
		Concept concept = conceptService.find(conceptAId, taskA1);
		concept.setActive(false);
		concept.setInactivationIndicator("AMBIGUOUS");
		concept.setAssociationTargets(Maps.newHashMap("POSSIBLY_EQUIVALENT_TO", Sets.newHashSet(conceptBId)));
		conceptService.update(concept, taskA1);

		System.out.println("All members");
		for (ReferenceSetMember referenceSetMember : memberService.findMembers(taskA1, new MemberSearchRequest(), LARGE_PAGE).getContent()) {
			System.out.println(referenceSetMember.getReferencedComponentId() + " - " + referenceSetMember.getRefsetId() + " - " + referenceSetMember);
		}
		System.out.println("All members end");

		assertEquals(2, memberService.findMembers(taskA1, conceptAId, LARGE_PAGE).getTotalElements());
		MemberSearchRequest descriptionInactivationMemberSearchRequest = new MemberSearchRequest()
				.referenceSet(Concepts.DESCRIPTION_INACTIVATION_INDICATOR_REFERENCE_SET)
				.referencedComponentId(concept.getDescriptions().iterator().next().getId());
		assertEquals(1, memberService.findMembers(taskA1, descriptionInactivationMemberSearchRequest, LARGE_PAGE).getTotalElements());

		// On branch A2 inactivate conceptA with OUTDATED reason and
		concept = conceptService.find(conceptAId, taskA2);
		concept.setActive(false);
		concept.setInactivationIndicator("AMBIGUOUS");
		concept.setAssociationTargets(Maps.newHashMap("POSSIBLY_EQUIVALENT_TO", Sets.newHashSet(conceptBId)));
		conceptService.update(concept, taskA2);
		assertEquals(2, memberService.findMembers(taskA2, conceptAId, LARGE_PAGE).getTotalElements());
		assertEquals(1, memberService.findMembers(taskA2, descriptionInactivationMemberSearchRequest, LARGE_PAGE).getTotalElements());

		// Promote task A1
		assertEquals(1, memberService.findMembers("MAIN/A", conceptAId, LARGE_PAGE).getTotalElements());
		branchMergeService.mergeBranchSync(taskA1, "MAIN/A", Collections.emptySet());
		assertEquals(2, memberService.findMembers("MAIN/A", conceptAId, LARGE_PAGE).getTotalElements());
		assertEquals(1, memberService.findMembers(taskA1, descriptionInactivationMemberSearchRequest, LARGE_PAGE).getTotalElements());

		// Create and version code system
		//codeSystemService.createCodeSystem(new CodeSystem("SNOMEDCT-A", "MAIN/A"));
		CodeSystem codeSystem = codeSystemService.find("SNOMEDCT-A");
		codeSystemService.createVersion(codeSystem, 20210131, "Version");
		assertEquals("20210131", memberService.findMembers("MAIN/A", conceptAId, LARGE_PAGE).getContent().get(0).getEffectiveTime());

		// Rebase the diverged branch supplying the unversioned task concept version as the manually merged concept
		// Serialise and deserialise to simulate transfer over REST. References to specific refset members will be lost.
		concept = simulateRestTransfer(concept);
		branchMergeService.mergeBranchSync("MAIN/A", taskA2, Collections.singleton(concept));
		Page<ReferenceSetMember> members = memberService.findMembers(taskA2, new MemberSearchRequest().referenceSet(REFSET_POSSIBLY_EQUIVALENT_TO_ASSOCIATION), LARGE_PAGE);
		members.getContent().forEach(System.out::println);
		assertEquals(1, members.getTotalElements());
		assertEquals("20210131", members.getContent().get(0).getEffectiveTime(), "Already versioned content must be kept.");
	}

	@Test
	void testAutomaticMergeOfInferredAdditions() throws ServiceException {
		// Create concepts to be used in relationships
		String conceptId = "131148009";
		conceptService.createUpdate(Lists.newArrayList(
				new Concept(Concepts.SNOMEDCT_ROOT),
				new Concept(Concepts.ISA).addRelationship(new Relationship(Concepts.ISA, Concepts.SNOMEDCT_ROOT).setInferred(true)),
				new Concept(Concepts.CLINICAL_FINDING).addRelationship(new Relationship(Concepts.ISA, Concepts.SNOMEDCT_ROOT).setInferred(true)),
				new Concept(conceptId).addRelationship(new Relationship(Concepts.ISA, Concepts.SNOMEDCT_ROOT).setInferred(true)),
				new Concept("313413008").addRelationship(new Relationship(Concepts.ISA, Concepts.SNOMEDCT_ROOT).setInferred(true))
		), "MAIN");
		branchMergeService.mergeBranchSync("MAIN", "MAIN/A", Collections.emptySet());
		branchMergeService.mergeBranchSync("MAIN/A", "MAIN/A/A1", Collections.emptySet());
		assertEquals(1, countRelationships("MAIN/A/A1", conceptId, Relationship.CharacteristicType.inferred));
		assertEquals(toLongSet(Concepts.SNOMEDCT_ROOT),
				queryService.findAncestorIds(conceptId, "MAIN/A/A1", false));

		// Update inferred form on MAIN/A
		conceptService.update(new Concept(conceptId).addRelationship(new Relationship(Concepts.ISA, Concepts.CLINICAL_FINDING).setInferred(true)), "MAIN/A");
		assertEquals(1, countRelationships("MAIN/A/A1", conceptId, Relationship.CharacteristicType.inferred));
		assertEquals(toLongSet(Concepts.SNOMEDCT_ROOT),
				queryService.findAncestorIds(conceptId, "MAIN/A/A1", false));

		// Update inferred form on MAIN/A/A1
		conceptService.update(new Concept(conceptId).addRelationship(new Relationship(Concepts.ISA, "313413008").setInferred(true)), "MAIN/A/A1");
		assertEquals(1, countRelationships("MAIN/A/A1", conceptId, Relationship.CharacteristicType.inferred));
		assertEquals(toLongSet(Concepts.SNOMEDCT_ROOT, "313413008"),
				queryService.findAncestorIds(conceptId, "MAIN/A/A1", false));


		// Rebase the diverged branch. Inferred form should be merged automatically without conflicts.
		branchMergeService.mergeBranchSync("MAIN/A", "MAIN/A/A1", Collections.emptySet());

		assertEquals(2, countRelationships("MAIN/A/A1", conceptId, Relationship.CharacteristicType.inferred));
		assertEquals(toLongSet(Concepts.SNOMEDCT_ROOT, Concepts.CLINICAL_FINDING, "313413008"),
				queryService.findAncestorIds(conceptId, "MAIN/A/A1", false));

		branchMergeService.mergeBranchSync("MAIN/A/A1", "MAIN/A", Collections.emptySet());
		assertEquals(toLongSet(Concepts.SNOMEDCT_ROOT, Concepts.CLINICAL_FINDING, "313413008"),
				queryService.findAncestorIds(conceptId, "MAIN/A", false));
	}

	public int countDescriptions(String branchPath, String conceptId) {
		return getDescriptions(branchPath, conceptId).size();
	}

	public List<Description> getDescriptions(String branchPath, String conceptId) {
		final BranchCriteria branchCriteria = versionControlHelper.getBranchCriteria(branchPath);
		BoolQueryBuilder query = boolQuery().must(branchCriteria.getEntityBranchCriteria(Description.class))
				.must(termsQuery("conceptId", conceptId));
		return elasticsearchTemplate.search(
				new NativeSearchQueryBuilder().withQuery(query).build(), Description.class)
				.stream().map(SearchHit::getContent).collect(Collectors.toList());
	}

	public long countRelationships(String branchPath, String conceptId1, Relationship.CharacteristicType inferred) {
		return getRelationships(branchPath, conceptId1, inferred).getTotalElements();
	}

	public Page<Relationship> getRelationships(String branchPath, String conceptId1, Relationship.CharacteristicType inferred) {
		return relationshipService.findRelationships(branchPath, null, true, null, null, conceptId1, null, null, inferred, null, LARGE_PAGE);
	}

	public Set<Long> toLongSet(String... ids) {
		return Arrays.stream(ids).map(Long::parseLong).collect(Collectors.toSet());
	}

	@Test
	void testAutomaticMergeOfInferredChange() throws ServiceException {
		// Create concepts to be used in relationships
		String conceptId = "131148009";
		conceptService.createUpdate(Lists.newArrayList(
				new Concept(Concepts.SNOMEDCT_ROOT),
				new Concept(Concepts.ISA).addRelationship(new Relationship(Concepts.ISA, Concepts.SNOMEDCT_ROOT).setInferred(true)),
				new Concept(Concepts.CLINICAL_FINDING).addRelationship(new Relationship(Concepts.ISA, Concepts.SNOMEDCT_ROOT).setInferred(true)),
				new Concept(conceptId).addRelationship(new Relationship(Concepts.ISA, Concepts.SNOMEDCT_ROOT).setInferred(true)),
				new Concept("313413008").addRelationship(new Relationship(Concepts.ISA, Concepts.SNOMEDCT_ROOT).setInferred(true))
		), "MAIN");
		branchMergeService.mergeBranchSync("MAIN", "MAIN/A", Collections.emptySet());
		branchMergeService.mergeBranchSync("MAIN/A", "MAIN/A/A1", Collections.emptySet());
		queryService.findAncestorIds(conceptId, "MAIN/A/A1", false);

		// Update relationship module on MAIN/A
		Concept concept = conceptService.find(conceptId, "MAIN/A");
		concept.getRelationships().iterator().next().setModuleId("123000");
		conceptService.update(concept, "MAIN/A");

		// Update relationship module on MAIN/A/A1
		assertEquals(1, countRelationships("MAIN/A/A1", conceptId, Relationship.CharacteristicType.inferred));
		concept = conceptService.find(conceptId, "MAIN/A/A1");
		concept.getRelationships().iterator().next().setModuleId("456000");
		conceptService.update(concept, "MAIN/A/A1");
		assertEquals(1, countRelationships("MAIN/A/A1", conceptId, Relationship.CharacteristicType.inferred));
		assertEquals(1, queryService.eclSearch(conceptId, false, "MAIN/A/A1", LARGE_PAGE).getTotalElements());

		// Rebase the diverged branch. Inferred form should be merged automatically without conflicts.
		branchMergeService.mergeBranchSync("MAIN/A", "MAIN/A/A1", Collections.emptySet());
		assertEquals(1, countRelationships("MAIN/A/A1", conceptId, Relationship.CharacteristicType.inferred));

		List<Relationship> rels = elasticsearchTemplate.search(new NativeSearchQueryBuilder()
				.withQuery(boolQuery()
						.must(termsQuery(Relationship.Fields.SOURCE_ID, conceptId))
						.must(termsQuery(Relationship.Fields.CHARACTERISTIC_TYPE_ID, Concepts.INFERRED_RELATIONSHIP)))
				.withPageable(LARGE_PAGE)
				.withSort(SortBuilders.fieldSort("start")).build(), Relationship.class)
				.stream().map(SearchHit::getContent).collect(Collectors.toList());
		for (Relationship rel : rels) {
			System.out.println(rel);
		}

		List<QueryConcept> queryConceptsAcrossBranches = elasticsearchTemplate.search(new NativeSearchQueryBuilder()
				.withQuery(boolQuery()
						.must(termsQuery(QueryConcept.Fields.CONCEPT_ID, conceptId))
						.must(termsQuery(QueryConcept.Fields.STATED, false)))
				.withPageable(LARGE_PAGE)
				.withSort(SortBuilders.fieldSort("start")).build(), QueryConcept.class)
				.stream().map(SearchHit::getContent).collect(Collectors.toList());
		for (QueryConcept queryConceptsAcrossBranch : queryConceptsAcrossBranches) {
			System.out.println(queryConceptsAcrossBranch);
		}

		List<Branch> branches = elasticsearchTemplate.search(new NativeSearchQueryBuilder()
				.withSort(SortBuilders.fieldSort("start"))
				.withPageable(LARGE_PAGE)
				.build(), Branch.class)
				.stream().map(SearchHit::getContent).collect(Collectors.toList());

		branches.forEach(System.out::println);
		assertEquals(1, queryService.eclSearch(conceptId, false, "MAIN/A/A1", LARGE_PAGE).getTotalElements());

		branchMergeService.mergeBranchSync("MAIN/A/A1", "MAIN/A", Collections.emptySet());
		assertEquals(1, countRelationships("MAIN/A", conceptId, Relationship.CharacteristicType.inferred));
	}

	@Test
	void testAutomaticMergeOfSynonymChange() throws ServiceException {
		// Create concept
		String conceptId = "131148009";
		conceptService.createUpdate(Lists.newArrayList(
				new Concept(conceptId).addDescription(new Description("Some synonym").setTypeId(Concepts.SYNONYM).setCaseSignificanceId(Concepts.CASE_INSENSITIVE))
		), "MAIN");
		branchMergeService.mergeBranchSync("MAIN", "MAIN/A", Collections.emptySet());
		branchMergeService.mergeBranchSync("MAIN/A", "MAIN/A/A1", Collections.emptySet());

		// Update description case on MAIN/A
		Concept concept = conceptService.find(conceptId, "MAIN/A");
		concept.getDescriptions().iterator().next().setCaseSignificanceId(Concepts.INITIAL_CHARACTER_CASE_INSENSITIVE);
		conceptService.update(concept, "MAIN/A");

		// Update description case on MAIN/A/A1
		assertEquals(1, countDescriptions("MAIN/A/A1", conceptId));
		concept = conceptService.find(conceptId, "MAIN/A/A1");
		concept.getDescriptions().iterator().next().setCaseSignificanceId(Concepts.ENTIRE_TERM_CASE_SENSITIVE);
		conceptService.update(concept, "MAIN/A/A1");
		assertEquals(1, countDescriptions("MAIN/A/A1", conceptId));

		// Rebase the diverged branch. Descriptions should be merged automatically without conflicts.
		branchMergeService.mergeBranchSync("MAIN/A", "MAIN/A/A1", Collections.emptySet());
		List<Description> descriptions = getDescriptions("MAIN/A/A1", conceptId);
		for (Description description : descriptions) {
			System.out.println(description);
		}
		assertEquals(1, countDescriptions("MAIN/A/A1", conceptId));

		branchMergeService.mergeBranchSync("MAIN/A/A1", "MAIN/A", Collections.emptySet());
		assertEquals(1, countDescriptions("MAIN/A", conceptId));
	}

	@Test
	void testAutomaticMergeOfRefsetMemberChange() throws ServiceException {
		String referencedComponent = Concepts.CLINICAL_FINDING;
		ReferenceSetMember member = memberService.createMember("MAIN",
				new ReferenceSetMember(Concepts.CORE_MODULE, Concepts.REFSET_SIMPLE, referencedComponent));

		String memberId = member.getMemberId();
		branchMergeService.mergeBranchSync("MAIN", "MAIN/A", Collections.emptySet());
		branchMergeService.mergeBranchSync("MAIN/A", "MAIN/A/A1", Collections.emptySet());

		// Update member module on MAIN/A
		setMemeberModule(memberId, "MAIN/A", MODEL_MODULE);

		// Update member module on MAIN/A/A1
		assertEquals(1, countMembers(referencedComponent, "MAIN/A/A1"));
		setMemeberModule(memberId, "MAIN/A/A1", "1231230010");
		assertEquals(1, countMembers(referencedComponent, "MAIN/A/A1"));

		// Rebase the diverged branch. Members should be merged automatically without conflicts.
		branchMergeService.mergeBranchSync("MAIN/A", "MAIN/A/A1", Collections.emptySet());
		List<ReferenceSetMember> members = memberService.findMembers("MAIN/A/A1", referencedComponent, LARGE_PAGE).getContent();
		for (ReferenceSetMember m : members) {
			System.out.println(m);
		}
		assertEquals(1, countMembers(referencedComponent, "MAIN/A/A1"));

		branchMergeService.mergeBranchSync("MAIN/A/A1", "MAIN/A", Collections.emptySet());
		assertEquals(1, countMembers(referencedComponent, "MAIN/A"));
	}

	void setMemeberModule(String memberId, String branch, String module) {
		ReferenceSetMember member;
		member = memberService.findMember(branch, memberId);
		member.setModuleId(module);
		memberService.updateMember(branch, member);
	}

	public long countMembers(String referencedComponent, String branch) {
		return memberService.findMembers(branch, referencedComponent, LARGE_PAGE).getTotalElements();
	}


	@Test
	void testConflictConceptMergeChangesFromRight() throws ServiceException {
		final String conceptId = "10000100";
		final Description description = new Description("One");

		setupConflictSituation(
				new Concept(conceptId, "100009001").addDescription(description),
				new Concept(conceptId, "100009002").addDescription(description),
				new Concept(conceptId, "100009003").addDescription(description));

		// Rebase the diverged branch supplying the manually merged concept
		final Concept conceptFromRight = new Concept(conceptId, "100009003").addDescription(description);
		branchMergeService.mergeBranchSync("MAIN/A", "MAIN/A/A2", Collections.singleton(conceptFromRight));

		final String conceptFromMergedA2 = conceptService.find(conceptId, "MAIN/A/A2").getModuleId();
		assertEquals("100009003", conceptFromMergedA2);

		// Promote the branch (no conflicts at this point)
		branchMergeService.mergeBranchSync("MAIN/A/A2", "MAIN/A", null);
		assertEquals("100009003", conceptService.find(conceptId, "MAIN/A").getModuleId());
	}
	
	@Test
	void testConflictConceptMergeChangesFromRightWithVersioning() throws ServiceException {
		/* Recreate a situation where an inactivation is performed on two branches
		 * with the inactivation being published, but then the right hand (non-versioned)
		 * side being selected in the merge leading to an apparently new inactive state
		 * following a previous also inactive state
		 */
		final String conceptId = "10000100";
		final String descriptionId = "10000110";
		final String moduleId = "100009001";

		//Only rebase the first branch so we have an unversioned concept on the 2nd
		setupConflictSituationReleaseFirstVersion(
				new Concept(conceptId, moduleId).addDescription(new Description(descriptionId, "One")).setActive(false),
				new Concept(conceptId, moduleId).addDescription(new Description(descriptionId, "One")).setActive(false),
				new Concept(conceptId, moduleId).addDescription(new Description(descriptionId, "One")).setActive(true));

		Concept versionedConcept = conceptService.find(conceptId, "MAIN/A");
		String effectiveTime = versionedConcept.getEffectiveTime();
		assertNotNull(effectiveTime);
		assertTrue(versionedConcept.isReleased());
		
		versionedConcept = conceptService.find(conceptId, "MAIN/A/A1");
		effectiveTime = versionedConcept.getEffectiveTime();
		assertNotNull(effectiveTime);
		assertTrue(versionedConcept.isReleased());
		assertFalse(versionedConcept.isActive());
		final Set<Description> descriptions = versionedConcept.getDescriptions();
		for (Description description : descriptions) {
			System.out.println(description.getDescriptionId());
		}
		assertEquals(1, descriptions.size());
		
		//Branch A/A2 has not been rebased so concept is still unversioned there
		Concept unversionedConcept = conceptService.find(conceptId, "MAIN/A/A2");
		assertNull(unversionedConcept.getEffectiveTime());
		assertFalse(unversionedConcept.isReleased());
		
		// Rebase the diverged branch supplying the manually merged concept
		final Concept conceptFromRight = new Concept(conceptId, moduleId).addDescription(new Description(descriptionId, "One")).setActive(false);
		branchMergeService.mergeBranchSync("MAIN/A", "MAIN/A/A2", Collections.singleton(conceptFromRight));

		Concept rebasedConcept = conceptService.find(conceptId, "MAIN/A/A2");
		assertEquals(effectiveTime, rebasedConcept.getEffectiveTime());
		assertTrue(rebasedConcept.isReleased());
		
		//The description should also pick up the same time
		Description rebasedDescription = rebasedConcept.getDescriptions().iterator().next();
		assertEquals(effectiveTime, rebasedDescription.getEffectiveTime());
		assertTrue(rebasedDescription.isReleased());
		
		//TODO Check Relationships and other associated components are also versioned as expected
	}

	@Test
	void testConflictConceptMergeChangesFromRightSameChangeOnBothSides() throws ServiceException {
		final String conceptId = "10000100";

		setupConflictSituation(
				new Concept(conceptId, "100009001").addDescription(new Description("123123", "One")),
				new Concept(conceptId, "100009002").addDescription(new Description("123123", "One").setLang("dk")),
				new Concept(conceptId, "100009003").addDescription(new Description("123123", "One").setLang("dk")));

		// Rebase the diverged branch supplying the manually merged concept
		final Concept conceptFromRight = new Concept(conceptId, "100009003").addDescription(new Description("123123", "One").setLang("dk"));
		branchMergeService.mergeBranchSync("MAIN/A", "MAIN/A/A2", Collections.singleton(conceptFromRight));
		Description description = descriptionService.findDescription("MAIN/A/A2", "123123");
		assertEquals("dk", description.getLanguageCode());

		final String conceptFromMergedA2 = conceptService.find(conceptId, "MAIN/A/A2").getModuleId();
		assertEquals("100009003", conceptFromMergedA2);

		// Promote the branch (no conflicts at this point)
		branchMergeService.mergeBranchSync("MAIN/A/A2", "MAIN/A", null);
		assertEquals("100009003", conceptService.find(conceptId, "MAIN/A").getModuleId());
	}

	@Test
	void testConflictConceptMergeChangesFromRightExtraDescriptionFromLeftMustBeRemoved() throws ServiceException {
		final String conceptId = "10000100";

		setupConflictSituation(
				new Concept(conceptId, "100009001").addDescription(new Description("123123", "One")),
				new Concept(conceptId, "100009002").addDescription(new Description("123123", "One").setLang("dk")).addDescription(new Description("123124", "Two")),
				new Concept(conceptId, "100009003").addDescription(new Description("123123", "One").setLang("dk")));

		// Rebase the diverged branch supplying the manually merged concept
		final Concept conceptFromRight = new Concept(conceptId, "100009003").addDescription(new Description("123123", "One").setLang("dk"));
		branchMergeService.mergeBranchSync("MAIN/A", "MAIN/A/A2", Collections.singleton(conceptFromRight));
		Description description = descriptionService.findDescription("MAIN/A/A2", "123123");
		assertEquals("dk", description.getLanguageCode());

		final Concept conceptFromMergedA2 = conceptService.find(conceptId, "MAIN/A/A2");
		assertEquals("100009003", conceptFromMergedA2.getModuleId());
		assertEquals(1, descriptionService.findDescriptionsByConceptId("MAIN/A/A2", Collections.singleton(conceptId), true).size());

		// Promote the branch (no conflicts at this point)
		branchMergeService.mergeBranchSync("MAIN/A/A2", "MAIN/A", null);
		assertEquals("100009003", conceptService.find(conceptId, "MAIN/A").getModuleId());
	}

	@Test
	void testConflictConceptMergeChangesFromNowhere() throws ServiceException {
		final String conceptId = "10000100";
		final Description description = new Description("One");

		setupConflictSituation(
				new Concept(conceptId, "100009001").addDescription(description),
				new Concept(conceptId, "100009002").addDescription(description),
				new Concept(conceptId, "100009003").addDescription(description));

		// Rebase the diverged branch supplying the manually merged concept
		final Concept conceptFromRight = new Concept(conceptId, "100009099").addDescription(description);
		branchMergeService.mergeBranchSync("MAIN/A", "MAIN/A/A2", Collections.singleton(conceptFromRight));

		assertEquals("100009099", conceptService.find(conceptId, "MAIN/A/A2").getModuleId());

		// Promote the branch (no conflicts at this point)
		branchMergeService.mergeBranchSync("MAIN/A/A2", "MAIN/A", null);
		assertEquals("100009099", conceptService.find(conceptId, "MAIN/A").getModuleId());
	}

	@Test
	void testConflictDescriptionsNewOnBothSides() throws ServiceException {
		final String conceptId = "10000100";

		setupConflictSituation(
				new Concept(conceptId, "100009001").addDescription(new Description("10000101", "Orig")),

				new Concept(conceptId, "100009002").addDescription(new Description("10000101", "Orig"))
						.addDescription(new Description("10000201", "New Left")),

				new Concept(conceptId, "100009003").addDescription(new Description("10000101", "Orig"))
						.addDescription(new Description("10000301", "New Right")));

		// Rebase the diverged branch supplying the manually merged concept
		final Concept conceptMerge = new Concept(conceptId, "100009099");
		conceptMerge.addDescription(new Description("10000101", "Orig"));
		conceptMerge.addDescription(new Description("10000201", "New Left"));
		conceptMerge.addDescription(new Description("10000301", "New Right"));
		branchMergeService.mergeBranchSync("MAIN/A", "MAIN/A/A2", Collections.singleton(conceptMerge));

		assertEquals(3, conceptService.find(conceptId, "MAIN/A/A2").getDescriptions().size());

		// Promote the branch (no conflicts at this point)
		branchMergeService.mergeBranchSync("MAIN/A/A2", "MAIN/A", null);
		assertEquals(3, conceptService.find(conceptId, "MAIN/A").getDescriptions().size());
	}

	@Test
	void testConflictSamePTChangeOnBothSides() throws ServiceException {
		final String conceptId = "10000100";

		final Concept firstConcept = new Concept(conceptId)
				.addDescription(new Description("10000011", "Orig PT")
						.addAcceptability(US_EN_LANG_REFSET, Concepts.PREFERRED_CONSTANT))
				.addDescription(new Description("20000011", "Other")
						.addAcceptability(US_EN_LANG_REFSET, Concepts.ACCEPTABLE_CONSTANT));

		final Concept leftConcept = new Concept(conceptId)
				.addDescription(new Description("10000011", "Orig PT")
						.addAcceptability(US_EN_LANG_REFSET, Concepts.ACCEPTABLE_CONSTANT)
						.addAcceptability(Concepts.GB_EN_LANG_REFSET, Concepts.PREFERRED_CONSTANT)
				)
				.addDescription(new Description("20000011", "Other")
						.addAcceptability(US_EN_LANG_REFSET, Concepts.PREFERRED_CONSTANT)
						.addAcceptability(Concepts.GB_EN_LANG_REFSET, Concepts.ACCEPTABLE_CONSTANT)
				);

		final Concept rightConcept = new Concept(conceptId)
				.addDescription(new Description("10000011", "Orig PT")
						.addAcceptability(US_EN_LANG_REFSET, Concepts.ACCEPTABLE_CONSTANT)
						.addAcceptability(Concepts.GB_EN_LANG_REFSET, Concepts.ACCEPTABLE_CONSTANT)
				)
				.addDescription(new Description("20000011", "Other")
						.addAcceptability(US_EN_LANG_REFSET, Concepts.PREFERRED_CONSTANT)
						.addAcceptability(Concepts.GB_EN_LANG_REFSET, Concepts.PREFERRED_CONSTANT)
				);


		setupConflictSituation(firstConcept, leftConcept, rightConcept);

		Page<ReferenceSetMember> descriptionMembers = memberService.findMembers("MAIN/A/A2", "10000011", PageRequest.of(0, 10));
		assertEquals(2, descriptionMembers.getTotalElements(), printCollection(descriptionMembers));

		descriptionMembers = memberService.findMembers("MAIN/A/A2", "20000011", PageRequest.of(0, 10));
		assertEquals(2, descriptionMembers.getTotalElements(), printCollection(descriptionMembers));

		// Rebase the diverged branch supplying the manually merged concept
		branchMergeService.mergeBranchSync("MAIN/A", "MAIN/A/A2", Collections.singleton(rightConcept));

		descriptionMembers = memberService.findMembers("MAIN/A/A2", "10000011", PageRequest.of(0, 10));
		assertEquals(2, descriptionMembers.getTotalElements(), printCollection(descriptionMembers));

		descriptionMembers = memberService.findMembers("MAIN/A/A2", "20000011", PageRequest.of(0, 10));
		assertEquals(2, descriptionMembers.getTotalElements(), printCollection(descriptionMembers));

		// Promote the branch (no conflicts at this point)
		branchMergeService.mergeBranchSync("MAIN/A/A2", "MAIN/A", null);

		descriptionMembers = memberService.findMembers("MAIN/A", "10000011", PageRequest.of(0, 10));
		assertEquals(2, descriptionMembers.getTotalElements(), printCollection(descriptionMembers));

		descriptionMembers = memberService.findMembers("MAIN/A", "20000011", PageRequest.of(0, 10));
		assertEquals(2, descriptionMembers.getTotalElements(), printCollection(descriptionMembers));

	}

	private Supplier<String> printCollection(Page<ReferenceSetMember> finalDescriptionMembers) {
		return () -> {
			finalDescriptionMembers.stream().forEach(System.out::println);
			return "";
		};
	}

	@Test
	void testConflictDescriptionInactiveWithDifferentReasonOnBothSides() throws ServiceException, InterruptedException {
		final String conceptId = "100001000";

		// Release first version of the concept so that the inactive descriptions are not automatically deleted
		String descriptionId = "10000011";
		List<Concept> conceptVersions = setupConflictSituationReleaseFirstVersion(
				new Concept(conceptId).addDescription(new Description(descriptionId, "Orig")),
				new Concept(conceptId).addDescription(inactive(new Description(descriptionId, "Orig"), "NOT_SEMANTICALLY_EQUIVALENT", "REFERS_TO", "61462000")),
				new Concept(conceptId).addDescription(inactive(new Description(descriptionId, "Orig"), "OUTDATED", null, null))
		);

		Description descriptionA = descriptionService.findDescription("MAIN/A", descriptionId);
		assertTrue(descriptionA.isReleased());
		assertFalse(descriptionA.isActive());
		assertEquals("NOT_SEMANTICALLY_EQUIVALENT", descriptionA.getInactivationIndicator());
		assertEquals("{REFERS_TO=[61462000]}", descriptionA.getAssociationTargets().toString());

		descriptionA = conceptService.find(conceptId, "MAIN/A").getDescriptions().iterator().next();
		assertTrue(descriptionA.isReleased());
		assertFalse(descriptionA.isActive());
		assertEquals("NOT_SEMANTICALLY_EQUIVALENT", descriptionA.getInactivationIndicator());
		assertEquals("{REFERS_TO=[61462000]}", descriptionA.getAssociationTargets().toString());

		Description descriptionA2 = descriptionService.findDescription("MAIN/A/A2", descriptionId);
		assertFalse(descriptionA2.isReleased());
		assertFalse(descriptionA2.isActive());
		assertEquals("OUTDATED", descriptionA2.getInactivationIndicator());
		assertNull(descriptionA2.getAssociationTargets());
		descriptionA2 = conceptService.find(conceptId, "MAIN/A/A2").getDescriptions().iterator().next();
		assertFalse(descriptionA2.isReleased());
		assertFalse(descriptionA2.isActive());
		assertEquals("OUTDATED", descriptionA2.getInactivationIndicator());
		assertNull(descriptionA2.getAssociationTargets());


		MergeReview mergeReview = reviewService.createMergeReview("MAIN/A", "MAIN/A/A2");
		// Wait for completion
		for (int i = 0; mergeReview.getStatus() == PENDING && i < 20; i++) {
			//noinspection BusyWait
			Thread.sleep(500);
		}
		assertEquals(CURRENT, mergeReview.getStatus());
		BranchReview sourceBranchReview = reviewService.getBranchReview(mergeReview.getSourceToTargetReviewId());
		BranchReview targetBranchReview = reviewService.getBranchReview(mergeReview.getTargetToSourceReviewId());
		assertEquals(1, sourceBranchReview.getChangedConcepts().size());
		assertEquals("[100001000]", sourceBranchReview.getChangedConcepts().toString());
		assertEquals(1, targetBranchReview.getChangedConcepts().size());
		assertEquals("[100001000]", targetBranchReview.getChangedConcepts().toString());

		// Rebase the diverged branch supplying the manually merged concept
		Concept rightSideConcept = simulateRestTransfer(conceptVersions.get(2));
		branchMergeService.mergeBranchSync("MAIN/A", "MAIN/A/A2", Collections.singleton(rightSideConcept));

		Set<Description> descriptions = conceptService.find(conceptId, "MAIN/A/A2").getDescriptions();
		assertEquals(1, descriptions.size());
		Description description = descriptions.iterator().next();
		assertEquals("OUTDATED", description.getInactivationIndicator());
		assertNull(description.getAssociationTargets());

		// Promote the branch (no conflicts at this point)
		branchMergeService.mergeBranchSync("MAIN/A/A2", "MAIN/A", null);
		assertEquals(1, conceptService.find(conceptId, "MAIN/A").getDescriptions().size());
	}

	@Test
	void testConflictDescriptionInactiveWithDifferentAssociationOnBothSides() throws ServiceException {
		final String conceptId = "100001000";

		// Release first version of the concept so that the inactive descriptions are not automatically deleted
		String descriptionId = "10000011";
		List<Concept> conceptVersions = setupConflictSituationReleaseFirstVersion(
				new Concept(conceptId).addDescription(new Description(descriptionId, "Orig")
						.addLanguageRefsetMember(US_EN_LANG_REFSET, Concepts.PREFERRED)
						.addLanguageRefsetMember(Concepts.GB_EN_LANG_REFSET, Concepts.PREFERRED)
				),

				new Concept(conceptId).addDescription(inactive(new Description(descriptionId, "Orig"), "NOT_SEMANTICALLY_EQUIVALENT", "REFERS_TO", "10001000")),

				new Concept(conceptId).addDescription(inactive(new Description(descriptionId, "Orig"), "NOT_SEMANTICALLY_EQUIVALENT", "REFERS_TO", "10001000"))
		);

		// Rebase the diverged branch supplying the manually merged concept
		Concept rightSideConcept = simulateRestTransfer(conceptVersions.get(2));
		branchMergeService.mergeBranchSync("MAIN/A", "MAIN/A/A2", Collections.singleton(rightSideConcept));

		Set<Description> descriptions = conceptService.find(conceptId, "MAIN/A/A2").getDescriptions();
		assertEquals(1, descriptions.size());
		Description description = descriptions.iterator().next();
		assertEquals("NOT_SEMANTICALLY_EQUIVALENT", description.getInactivationIndicator());
		assertEquals("{REFERS_TO=[10001000]}", description.getAssociationTargets().toString());

		final Set<Description> descriptionsAfterMerge = descriptionService.findDescriptionsByConceptId("MAIN/A/A2", Collections.singleton(conceptId), true);
		assertEquals(1, descriptionsAfterMerge.size(), itemsToString("Expecting 1 description, got:", descriptionsAfterMerge));

		final Page<ReferenceSetMember> members = memberService.findMembers("MAIN/A/A2", descriptionId, LARGE_PAGE);
		assertEquals(4, members.getTotalElements(), itemsToString("Expecting 4 members, got:", members));

		// Promote the branch (no conflicts at this point)
		branchMergeService.mergeBranchSync("MAIN/A/A2", "MAIN/A", null);
		assertEquals(1, conceptService.find(conceptId, "MAIN/A").getDescriptions().size());
		assertEquals(4, memberService.findMembers("MAIN/A", descriptionId, LARGE_PAGE).getTotalElements());
	}

	@Test
	void testConflictConceptReleasedAndModifiedLangRefset() throws ServiceException {
		final String conceptId = "100001000";
		final String descriptionId = "10000011";

		// Release first version of the concept so that the inactive descriptions are not automatically deleted
		Concept releaseVersionConcept = new Concept(conceptId, Concepts.CORE_MODULE)
				.addDescription(new Description(descriptionId, "Orig")
						.addLanguageRefsetMember(US_EN_LANG_REFSET, Concepts.PREFERRED)
						.addLanguageRefsetMember(Concepts.GB_EN_LANG_REFSET, Concepts.PREFERRED));

		// Create concept on MAIN and version
		conceptService.create(releaseVersionConcept, "MAIN");
		codeSystemService.createVersion(codeSystemService.find(SNOMEDCT), 2021_07_01, "");
		releaseVersionConcept = conceptService.find(conceptId, "MAIN");
		assertEquals(2021_07_01, releaseVersionConcept.getReleasedEffectiveTime());
		assertEquals(2021_07_01, releaseVersionConcept.getDescription(descriptionId)
				.getLangRefsetMembersMap().get(US_EN_LANG_REFSET).iterator().next().getReleasedEffectiveTime());

		// Rebase MAIN/A
		branchMergeService.rebaseSync("MAIN/A", null);

		// Inactivate lang refset members on MAIN and version again
		releaseVersionConcept.setModuleId(MODEL_MODULE);
		releaseVersionConcept.getDescription(descriptionId).setActive(false);
		releaseVersionConcept.getDescription(descriptionId).clearLanguageRefsetMembers();
		conceptService.update(releaseVersionConcept, "MAIN");
		codeSystemService.createVersion(codeSystemService.find(SNOMEDCT), 2021_08_01, "");
		releaseVersionConcept = conceptService.find(conceptId, "MAIN");
		assertEquals(2021_08_01, releaseVersionConcept.getDescription(descriptionId)
				.getLangRefsetMembersMap().get(US_EN_LANG_REFSET).iterator().next().getReleasedEffectiveTime());

		// Inactivate lang refset members on MAIN/A
		Concept conceptOnA = conceptService.find(conceptId, "MAIN/A");
		conceptOnA.setModuleId(MODEL_MODULE);
		conceptOnA.getDescription(descriptionId).setActive(false);
		conceptOnA.getDescription(descriptionId).clearLanguageRefsetMembers();
		conceptService.update(conceptOnA, "MAIN/A");

		conceptOnA = conceptService.find(conceptId, "MAIN/A");
		assertEquals(2021_07_01, conceptOnA.getReleasedEffectiveTime());
		assertEquals(2021_07_01, conceptOnA.getDescription(descriptionId)
				.getLangRefsetMembersMap().get(US_EN_LANG_REFSET).iterator().next().getReleasedEffectiveTime());

		branchMergeService.rebaseSync("MAIN/A", Collections.singleton(conceptOnA));

		final Concept mergedConcept = conceptService.find(conceptId, "MAIN/A");
		assertEquals(2021_08_01, mergedConcept.getReleasedEffectiveTime());
		assertTrue(mergedConcept.isReleased());
		assertEquals(releaseVersionConcept.getReleasedEffectiveTime(), mergedConcept.getReleasedEffectiveTime());
		assertEquals(releaseVersionConcept.getReleaseHash(), mergedConcept.getReleaseHash());

		Set<Description> mergedDescriptions = mergedConcept.getDescriptions();
		assertEquals(1, mergedDescriptions.size());
		Description mergedDescription = mergedDescriptions.iterator().next();
		assertTrue(mergedDescription.isReleased());
		assertEquals(releaseVersionConcept.getDescription(descriptionId).getReleasedEffectiveTime(), mergedDescription.getReleasedEffectiveTime());
		assertEquals(releaseVersionConcept.getDescription(descriptionId).getReleaseHash(), mergedDescription.getReleaseHash());
		assertFalse(mergedDescription.isActive());

		final Set<ReferenceSetMember> mergedUsLang = mergedDescription.getLangRefsetMembersMap().get(US_EN_LANG_REFSET);
		assertEquals(1, mergedUsLang.size());
		final ReferenceSetMember mergedUsMember = mergedUsLang.iterator().next();
		assertTrue(mergedUsMember.isReleased());
		assertEquals(2021_08_01, mergedUsMember.getReleasedEffectiveTime());
		assertEquals(2021_08_01, mergedUsMember.getEffectiveTimeI());
		final Set<ReferenceSetMember> mergedGBLang = mergedDescription.getLangRefsetMembersMap().get(GB_EN_LANG_REFSET);
		assertEquals(1, mergedGBLang.size());
		assertEquals(2021_08_01, mergedGBLang.iterator().next().getEffectiveTimeI());
	}

	@Test
	void testConflictConceptReleasedAndModifiedHistoricalAssociation() throws ServiceException {
		final String conceptId = "100001000";
		final String descriptionId = "10000011";

		// Release first version of the concept
		Concept releaseVersionConcept = new Concept(conceptId, Concepts.CORE_MODULE)
				.addDescription(new Description(descriptionId, "Orig"));

		// Create concept on MAIN and create Version #0
		conceptService.create(releaseVersionConcept, "MAIN");
		codeSystemService.createVersion(codeSystemService.find(SNOMEDCT), 2021_07_01, "");
		releaseVersionConcept = simulateRestTransfer(conceptService.find(conceptId, "MAIN"));
		assertEquals(2021_07_01, releaseVersionConcept.getReleasedEffectiveTime());

		// Inactivate concept on MAIN and create Version #1 - with inactivation data
		releaseVersionConcept.setActive(false);
		releaseVersionConcept.setInactivationIndicator("DUPLICATE");
		releaseVersionConcept.setAssociationTargets(Map.of("POSSIBLY_EQUIVALENT_TO", Collections.singleton("100002000")));
		conceptService.update(releaseVersionConcept, "MAIN");
		codeSystemService.createVersion(codeSystemService.find(SNOMEDCT), 2021_08_01, "");
		releaseVersionConcept = simulateRestTransfer(conceptService.find(conceptId, "MAIN"));

		// Rebase MAIN/A
		branchMergeService.rebaseSync("MAIN/A", null);

		// Update inactivation details on MAIN/A (on Version #1)
		Concept conceptOnA = simulateRestTransfer(conceptService.find(conceptId, "MAIN/A"));
		conceptOnA.setActive(false);
		conceptOnA.setInactivationIndicator("OUTDATED");// Different indicator
		conceptOnA.setAssociationTargets(Map.of("POSSIBLY_EQUIVALENT_TO", Collections.singleton("100003000")));// Different target
		assertEquals(Map.of("POSSIBLY_EQUIVALENT_TO", Collections.singleton("100003000")), conceptOnA.getAssociationTargets());
		conceptService.update(conceptOnA, "MAIN/A");
		conceptOnA = simulateRestTransfer(conceptService.find(conceptId, "MAIN/A"));
		assertEquals(Map.of("POSSIBLY_EQUIVALENT_TO", Collections.singleton("100003000")), conceptOnA.getAssociationTargets());
		assertEquals(2021_08_01, conceptOnA.getReleasedEffectiveTime());

		// Update inactivation details on MAIN and create Version #2
		releaseVersionConcept.setActive(false);
		releaseVersionConcept.setInactivationIndicator("OUTDATED");
		releaseVersionConcept.setAssociationTargets(Map.of("POSSIBLY_EQUIVALENT_TO", Collections.singleton("100003000")));
		conceptService.update(releaseVersionConcept, "MAIN");
		codeSystemService.createVersion(codeSystemService.find(SNOMEDCT), 2021_09_01, "");

		// Rebase MAIN/A
		branchMergeService.rebaseSync("MAIN/A", Collections.singleton(conceptOnA));

		// Assert that releasedEffectiveTime == #2
		final Concept mergedConcept = conceptService.find(conceptId, "MAIN/A");
		assertEquals(2021_08_01, mergedConcept.getReleasedEffectiveTime());

		assertEquals("OUTDATED", mergedConcept.getInactivationIndicator());
		assertEquals(Map.of("POSSIBLY_EQUIVALENT_TO", Collections.singleton("100003000")), mergedConcept.getAssociationTargets());

		final Collection<ReferenceSetMember> inactivationIndicatorMembers = mergedConcept.getInactivationIndicatorMembers();
		assertEquals(2, inactivationIndicatorMembers.size());

		final ReferenceSetMember activeIndicatorMember = inactivationIndicatorMembers.stream().filter(ReferenceSetMember::isActive).findFirst().orElseThrow();
		assertTrue(activeIndicatorMember.isActive());
		assertTrue(activeIndicatorMember.isReleased());
		assertEquals(2021_09_01, activeIndicatorMember.getEffectiveTimeI());
		assertEquals(2021_09_01, activeIndicatorMember.getReleasedEffectiveTime());
		assertEquals("900000000000483008", activeIndicatorMember.getAdditionalField("valueId"));

		final ReferenceSetMember inactiveIndicatorMember = inactivationIndicatorMembers.stream().filter(not(ReferenceSetMember::isActive)).findFirst().orElseThrow();
		assertFalse(inactiveIndicatorMember.isActive());
		assertTrue(inactiveIndicatorMember.isReleased());
		assertEquals(2021_09_01, inactiveIndicatorMember.getEffectiveTimeI());
		assertEquals(2021_09_01, inactiveIndicatorMember.getReleasedEffectiveTime());
		assertEquals("900000000000482003", inactiveIndicatorMember.getAdditionalField("valueId"));


		final List<ReferenceSetMember> associationTargetMembers = mergedConcept.getAssociationTargetMembers();
		assertEquals(2, associationTargetMembers.size());

		final ReferenceSetMember activeAssocMember = associationTargetMembers.stream().filter(ReferenceSetMember::isActive).findFirst().orElseThrow();
		assertTrue(activeAssocMember.isActive());
		assertTrue(activeAssocMember.isReleased());
		assertEquals(2021_09_01, activeAssocMember.getReleasedEffectiveTime());

		final ReferenceSetMember inactiveAssocMember = associationTargetMembers.stream().filter(not(ReferenceSetMember::isActive)).findFirst().orElseThrow();
		assertFalse(inactiveAssocMember.isActive());
		assertTrue(inactiveAssocMember.isReleased());
		assertEquals(2021_09_01, inactiveAssocMember.getReleasedEffectiveTime());
	}

	private <T> Supplier<String> itemsToString(String message, Iterable<T> items) {
		return () -> {
			final StringBuilder builder = new StringBuilder(message);
			for (T member : items) {
				builder.append("\n").append(member.toString());
			}
			return builder.toString();
		};
	}

	private Description inactive(Description description, String reason, String association, String associationTarget) {
		description.setActive(false);
		description.setInactivationIndicator(reason);
		if (association != null) {
			description.setAssociationTargets(Maps.newHashMap(association, Sets.newHashSet(associationTarget)));
		}
		return description;
	}

	@Test
	void testConflictDescriptionsNewOnBothSidesAllDeletedInManualMerge() throws ServiceException {
		final String conceptId = "10000100";

		setupConflictSituation(
				new Concept(conceptId, "100009001").addDescription(new Description("10000101", "Orig")),

				new Concept(conceptId, "100009002").addDescription(new Description("10000101", "Orig"))
						.addDescription(new Description("10000201", "New Left")),

				new Concept(conceptId, "100009003").addDescription(new Description("10000101", "Orig"))
						.addDescription(new Description("10000301", "New Right")));

		assertEquals(2, conceptService.find(conceptId, "MAIN/A").getDescriptions().size());
		assertEquals(2, conceptService.find(conceptId, "MAIN/A/A2").getDescriptions().size());

		// Rebase the diverged branch supplying the manually merged concept
		final Concept conceptMerge = new Concept(conceptId, "100009099");
		branchMergeService.mergeBranchSync("MAIN/A", "MAIN/A/A2", Collections.singleton(conceptMerge));

		assertEquals(0, conceptService.find(conceptId, "MAIN/A/A2").getDescriptions().size());

		// Promote the branch (no conflicts at this point)
		branchMergeService.mergeBranchSync("MAIN/A/A2", "MAIN/A", null);
		assertEquals(0, conceptService.find(conceptId, "MAIN/A").getDescriptions().size());
	}

	@Test
	void testConcurrentPromotionBlockedByBranchLock() throws ServiceException, InterruptedException {
		conceptService.create(new Concept("10000100").addDescription(new Description("100001")), "MAIN/A/A1");
		conceptService.create(new Concept("10000100").addDescription(new Description("100002")), "MAIN/A/A2");

		BranchMergeJob branchMergeJobA = branchMergeService.mergeBranchAsync(new MergeRequest("MAIN/A/A1", "MAIN/A", "Promote A1", null));
		BranchMergeJob branchMergeJobC = branchMergeService.mergeBranchAsync(new MergeRequest("MAIN/A/A2", "MAIN/A", "Promote A2", null));

		Thread.sleep(2000);

		List<BranchMergeJob> jobs = Lists.newArrayList(branchMergeJobA, branchMergeJobC);
		List<BranchMergeJob> completeJobs = jobs.stream().filter(job -> job.getStatus() == JobStatus.COMPLETED).collect(Collectors.toList());
		List<BranchMergeJob> failedJobs = jobs.stream().filter(job -> job.getStatus() == JobStatus.FAILED).collect(Collectors.toList());

		assertEquals(1, completeJobs.size());
		assertEquals(1, failedJobs.size());
		assertEquals("Branch MAIN/A is already locked", failedJobs.get(0).getMessage());
	}

	@Test
	void testCreateMergeReviewConceptDeletedOnChildAcceptDeleted() throws InterruptedException, ServiceException {
		createConcept("10000100", "MAIN");
		branchService.create("MAIN/A1");

		// Update concept 10000100 description on A
		Concept concept = conceptService.find("10000100", "MAIN");
		getDescription(concept).setCaseSignificance("INITIAL_CHARACTER_CASE_INSENSITIVE");
		conceptService.update(concept, "MAIN");

		// Delete concept 10000100 on MAIN/A
		conceptService.deleteConceptAndComponents("10000100", "MAIN/A1", false);

		String source = "MAIN";
		String target = "MAIN/A1";
		MergeReview review = getMergeReviewInCurrentState(source, target);

		assertEquals(ReviewStatus.CURRENT, review.getStatus());

		Collection<MergeReviewConceptVersions> mergeReviewConflictingConcepts = reviewService.getMergeReviewConflictingConcepts(review.getId(), DEFAULT_LANGUAGE_DIALECTS);
		assertEquals(1, mergeReviewConflictingConcepts.size());

		// Check concept is only there on the source side.
		MergeReviewConceptVersions mergeReviewConceptVersions = mergeReviewConflictingConcepts.iterator().next();
		assertNotNull(mergeReviewConceptVersions.getSourceConcept());
		assertEquals("10000100", mergeReviewConceptVersions.getSourceConcept().getConceptId());
		assertNull(mergeReviewConceptVersions.getTargetConcept());
		assertNull(mergeReviewConceptVersions.getAutoMergedConcept());

		// Accept the deleted version
		reviewService.persistManualMergeConceptDeletion(reviewService.getMergeReviewOrThrow(review.getId()), 10000100L);

		reviewService.applyMergeReview(review);

		assertNull(conceptService.find("10000100", "MAIN/A1"), "Concept should be deleted after the merge.");
	}

	@Test
	void testCreateMergeReviewConceptDeletedOnParentAcceptDeleted() throws InterruptedException, ServiceException {
		createConcept("10000100", "MAIN");
		branchService.create("MAIN/A1");

		// Update concept 10000100 description on A
		Concept concept = conceptService.find("10000100", "MAIN/A1");
		getDescription(concept).setCaseSignificance("INITIAL_CHARACTER_CASE_INSENSITIVE");
		conceptService.update(concept, "MAIN/A1");

		// Delete concept 10000100 on MAIN
		conceptService.deleteConceptAndComponents("10000100", "MAIN", false);

		String source = "MAIN";
		String target = "MAIN/A1";
		MergeReview review = getMergeReviewInCurrentState(source, target);

		assertEquals(ReviewStatus.CURRENT, review.getStatus());

		Collection<MergeReviewConceptVersions> mergeReviewConflictingConcepts = reviewService.getMergeReviewConflictingConcepts(review.getId(), DEFAULT_LANGUAGE_DIALECTS);
		assertEquals(1, mergeReviewConflictingConcepts.size());

		// Check concept is only there on the source side.
		MergeReviewConceptVersions mergeReviewConceptVersions = mergeReviewConflictingConcepts.iterator().next();
		assertNull(mergeReviewConceptVersions.getSourceConcept());
		assertNotNull(mergeReviewConceptVersions.getTargetConcept());
		assertEquals("10000100", mergeReviewConceptVersions.getTargetConcept().getConceptId());
		assertNull(mergeReviewConceptVersions.getAutoMergedConcept());

		// Accept the deleted version
		reviewService.persistManualMergeConceptDeletion(reviewService.getMergeReviewOrThrow(review.getId()), 10000100L);

		reviewService.applyMergeReview(review);

		assertNull(conceptService.find("10000100", "MAIN/A1"), "Concept should be deleted after the merge.");
	}

	@Test
	void testCreateMergeReviewConceptDeletedOnChildAcceptUpdated() throws InterruptedException, ServiceException {
		createConcept("10000100", "MAIN");
		branchService.create("MAIN/A1");

		// Update concept 10000100 description on A
		Concept concept = conceptService.find("10000100", "MAIN");
		getDescription(concept).setCaseSignificance("INITIAL_CHARACTER_CASE_INSENSITIVE");
		conceptService.update(concept, "MAIN");

		// Delete concept 10000100 on MAIN/A
		conceptService.deleteConceptAndComponents("10000100", "MAIN/A1", false);

		String source = "MAIN";
		String target = "MAIN/A1";
		MergeReview review = getMergeReviewInCurrentState(source, target);

		assertEquals(ReviewStatus.CURRENT, review.getStatus());

		Collection<MergeReviewConceptVersions> mergeReviewConflictingConcepts = reviewService.getMergeReviewConflictingConcepts(review.getId(), DEFAULT_LANGUAGE_DIALECTS);
		assertEquals(1, mergeReviewConflictingConcepts.size());

		// Check concept is only there on the source side.
		MergeReviewConceptVersions mergeReviewConceptVersions = mergeReviewConflictingConcepts.iterator().next();
		assertNotNull(mergeReviewConceptVersions.getSourceConcept());
		assertEquals("10000100", mergeReviewConceptVersions.getSourceConcept().getConceptId());
		assertNull(mergeReviewConceptVersions.getTargetConcept());
		assertNull(mergeReviewConceptVersions.getAutoMergedConcept());

		// Accept the updated
		reviewService.persistManuallyMergedConcept(review, mergeReviewConceptVersions.getSourceConcept().getConceptIdAsLong(), mergeReviewConceptVersions.getSourceConcept());

		reviewService.applyMergeReview(review);

		assertNotNull(conceptService.find("10000100", "MAIN/A1"), "Concept should be restored after the merge.");
	}

	@Test
	void testCreateMergeReviewConceptDeletedOnParentAcceptUpdated() throws InterruptedException, ServiceException {
		createConcept("10000100", "MAIN");
		branchService.create("MAIN/A1");

		// Update concept 10000100 description on A
		Concept concept = conceptService.find("10000100", "MAIN/A1");
		getDescription(concept).setCaseSignificance("INITIAL_CHARACTER_CASE_INSENSITIVE");
		conceptService.update(concept, "MAIN/A1");

		// Delete concept 10000100 on MAIN
		conceptService.deleteConceptAndComponents("10000100", "MAIN", false);

		String source = "MAIN";
		String target = "MAIN/A1";
		MergeReview review = getMergeReviewInCurrentState(source, target);

		assertEquals(ReviewStatus.CURRENT, review.getStatus());

		Collection<MergeReviewConceptVersions> mergeReviewConflictingConcepts = reviewService.getMergeReviewConflictingConcepts(review.getId(), DEFAULT_LANGUAGE_DIALECTS);
		assertEquals(1, mergeReviewConflictingConcepts.size());

		// Check concept is only there on the source side.
		MergeReviewConceptVersions mergeReviewConceptVersions = mergeReviewConflictingConcepts.iterator().next();
		assertNull(mergeReviewConceptVersions.getSourceConcept());
		assertNotNull(mergeReviewConceptVersions.getTargetConcept());
		assertEquals("10000100", mergeReviewConceptVersions.getTargetConcept().getConceptId());
		assertNull(mergeReviewConceptVersions.getAutoMergedConcept());

		// Accept the updated version
		reviewService.persistManuallyMergedConcept(review, mergeReviewConceptVersions.getTargetConcept().getConceptIdAsLong(), mergeReviewConceptVersions.getTargetConcept());

		reviewService.applyMergeReview(review);

		assertNotNull(conceptService.find("10000100", "MAIN/A1"), "Concept should be restored after the merge.");
	}

	@Test
	void testFindChildBranches() {
		String testRootPath = "MAIN/CHILDREN";
		List<Branch> expectedBranches = new ArrayList<>(childBranches.values());

		// Assert expected number were returned with immediateChildren=false and they are returned as expected.
		List<Branch> branches = branchMergeService.findChildBranches(testRootPath, false, PageRequest.of(0, 100));
		assertTrue(expectedBranches.containsAll(branches), "All child branches were not returned correctly.");
	}

	@Test
	void testFindChildBranchesWithImmediateChildren() {
		String testRootPath = "MAIN/CHILDREN";
		List<Branch> expectedBranches = new ArrayList<>(childBranches.values());
		// remove the branches that should not be returned.
		expectedBranches.remove(childBranches.get(testRootPath + "/CHILD-1/CHILD-1"));
		expectedBranches.remove(childBranches.get(testRootPath + "/CHILD-1/CHILD-2"));

		// Assert expected number were returned with immediateChildren=true and they are returned as expected.
		List<Branch> branches = branchMergeService.findChildBranches(testRootPath, true, PageRequest.of(0, 100));
		assertTrue(expectedBranches.containsAll(branches), "All child branches were not returned correctly.");
	}

	@Test
	void testFindChildBranchesWithPaging() {
		String testRootPath = "MAIN/CHILDREN";
		List<Branch> expectedBranches = new ArrayList<>(childBranches.values());
		int pageSize = 5;
		int totalNumberOfPages = (int) Math.ceil((float) expectedBranches.size() / (float) pageSize);
		int lastPageSize = expectedBranches.size() % pageSize;
		IntStream.range(0, totalNumberOfPages).forEach(page -> {
			// check page
			int currentPageSize = pageSize;
			if (page == totalNumberOfPages - 1) {
				currentPageSize = lastPageSize;
			}
			List<Branch> pageBranches = branchMergeService.findChildBranches(
					testRootPath,
					false,
					PageRequest.of(page, pageSize));
			assertEquals(currentPageSize, pageBranches.size(), "Page returned incorrect number of branches");
			expectedBranches.removeAll(pageBranches);
		});
		assertEquals(0, expectedBranches.size(), "All expected branches not returned by the pages");
	}

	@Test
	void testVersionReplacedMapReducedDuringPromotionToMAIN() throws ServiceException {
		final String conceptId = "10000100";

		// Create concept on MAIN
		Concept concept = new Concept(conceptId).addDescription(new Description("One"));
		concept.setModuleId("100000000");
		conceptService.create(concept, "MAIN");

		// Update concept on MAIN/G
		branchService.create("MAIN/G");
		concept.setModuleId("200000000");
		conceptService.update(concept, "MAIN/G");

		// Update concept on MAIN/G/G1
		branchService.create("MAIN/G/G1");
		concept.setModuleId("300000000");
		conceptService.update(concept, "MAIN/G/G1");

		assertEquals(1, branchService.findBranchOrThrow("MAIN/G/G1").getVersionsReplacedCounts().get("Concept").intValue());
		assertEquals(1, branchService.findBranchOrThrow("MAIN/G").getVersionsReplacedCounts().get("Concept").intValue());
		assertEquals(0, branchService.findBranchOrThrow("MAIN").getVersionsReplacedCounts().get("Concept").intValue());

		// Blind promote MAIN/G/G1
		branchMergeService.mergeBranchSync("MAIN/G/G1", "MAIN/G", null);
		assertNull(branchService.findBranchOrThrow("MAIN/G/G1").getVersionsReplacedCounts().get("Concept"));
		assertEquals(1, branchService.findBranchOrThrow("MAIN/G").getVersionsReplacedCounts().get("Concept").intValue());
		assertEquals(0, branchService.findBranchOrThrow("MAIN").getVersionsReplacedCounts().get("Concept").intValue());

		// Blind promote MAIN/G
		branchMergeService.mergeBranchSync("MAIN/G", "MAIN", null);
		assertNull(branchService.findBranchOrThrow("MAIN/G/G1").getVersionsReplacedCounts().get("Concept"));
		assertNull(branchService.findBranchOrThrow("MAIN/G").getVersionsReplacedCounts().get("Concept"));
		assertEquals(0, branchService.findBranchOrThrow("MAIN").getVersionsReplacedCounts().get("Concept").intValue());
	}

	@Test
	void testVersionReplacedMapReducedDuringPromotionToExtensionBranch() throws ServiceException {
		final String conceptId = "10000100";

		// Create concept on MAIN/SNOMEDCT-DK
		Concept concept = new Concept(conceptId).addDescription(new Description("One"));
		concept.setModuleId("100000000");
		branchService.create("MAIN/SNOMEDCT-DK");
		conceptService.create(concept, "MAIN/SNOMEDCT-DK");

		// Update concept on MAIN/SNOMEDCT-DK/G
		branchService.create("MAIN/SNOMEDCT-DK/G");
		concept.setModuleId("200000000");
		conceptService.update(concept, "MAIN/SNOMEDCT-DK/G");

		// Update concept on MAIN/SNOMEDCT-DK/G/G1
		branchService.create("MAIN/SNOMEDCT-DK/G/G1");
		concept.setModuleId("300000000");
		conceptService.update(concept, "MAIN/SNOMEDCT-DK/G/G1");

		assertEquals(1, branchService.findBranchOrThrow("MAIN/SNOMEDCT-DK/G/G1").getVersionsReplacedCounts().get("Concept").intValue());
		assertEquals(1, branchService.findBranchOrThrow("MAIN/SNOMEDCT-DK/G").getVersionsReplacedCounts().get("Concept").intValue());
		assertEquals(0, branchService.findBranchOrThrow("MAIN/SNOMEDCT-DK").getVersionsReplacedCounts().get("Concept").intValue());

		// Blind promote MAIN/SNOMEDCT-DK/G/G1
		branchMergeService.mergeBranchSync("MAIN/SNOMEDCT-DK/G/G1", "MAIN/SNOMEDCT-DK/G", null);
		assertNull(branchService.findBranchOrThrow("MAIN/SNOMEDCT-DK/G/G1").getVersionsReplacedCounts().get("Concept"));
		assertEquals(1, branchService.findBranchOrThrow("MAIN/SNOMEDCT-DK/G").getVersionsReplacedCounts().get("Concept").intValue());
		assertEquals(0, branchService.findBranchOrThrow("MAIN/SNOMEDCT-DK").getVersionsReplacedCounts().get("Concept").intValue());

		// Blind promote MAIN/SNOMEDCT-DK/G
		branchMergeService.mergeBranchSync("MAIN/SNOMEDCT-DK/G", "MAIN/SNOMEDCT-DK", null);
		assertNull(branchService.findBranchOrThrow("MAIN/SNOMEDCT-DK/G/G1").getVersionsReplacedCounts().get("Concept"));
		assertNull(branchService.findBranchOrThrow("MAIN/SNOMEDCT-DK/G").getVersionsReplacedCounts().get("Concept"));
		assertEquals(0, branchService.findBranchOrThrow("MAIN/SNOMEDCT-DK").getVersionsReplacedCounts().get("Concept").intValue());
	}

	@Test
	void testPromotionAbortedIfBranchReviewIncomplete() throws ServiceException {
		assertBranchState("MAIN", Branch.BranchState.UP_TO_DATE);
		assertBranchState("MAIN/A", Branch.BranchState.UP_TO_DATE);
		assertBranchState("MAIN/A/A1", Branch.BranchState.UP_TO_DATE);
		givenBranchReviewIncomplete();

		// Create concept on A1
		final String conceptId = "10000123";
		conceptService.create(new Concept(conceptId), "MAIN/A/A1");
		assertBranchStateAndConceptVisibility("MAIN", Branch.BranchState.UP_TO_DATE, conceptId, false);
		assertBranchStateAndConceptVisibility("MAIN/A", Branch.BranchState.UP_TO_DATE, conceptId, false);
		assertBranchStateAndConceptVisibility("MAIN/A/A1", Branch.BranchState.FORWARD, conceptId, true);

		// Promotion should fail
		String message = assertThrows(RuntimeServiceException.class, () -> branchMergeService.mergeBranchSync("MAIN/A/A1", "MAIN/A", Collections.emptyList())).getMessage();
		assertEquals("Promotion blocked; not all criteria have been met.", message);
	}

	@Test
	void testAutoMergeWhenConceptDeletedSomewhereAndVersionedElsewhere() throws ServiceException, InterruptedException {
		String codeSystemShortName = "SNOMEDCT-TEST";
		String parentBranch = "MAIN/TEST";
		String childBranch = "MAIN/TEST/TEST-1";

		// Create Concept on CodeSystem
		CodeSystem codeSystem = codeSystemService.createCodeSystem(new CodeSystem(codeSystemShortName, parentBranch));
		Map<String, String> acceptabilityMap = Collections.singletonMap(US_EN_LANG_REFSET, descriptionAcceptabilityNames.get(ACCEPTABLE));
		Description pizzaFood = new Description("Pizza (food)").setTypeId(FSN).setCaseSignificance("CASE_INSENSITIVE").setAcceptabilityMap(acceptabilityMap);
		Description pizza = new Description("Pizza").setTypeId(SYNONYM).setCaseSignificance("CASE_INSENSITIVE").setAcceptabilityMap(acceptabilityMap);
		Relationship isA = new Relationship(ISA, SNOMEDCT_ROOT);
		Concept conceptOnParent = conceptService.create(new Concept().addDescription(pizzaFood).addDescription(pizza).addRelationship(isA), parentBranch);
		String conceptId = conceptOnParent.getId();

		// On child branch, delete Concept
		branchService.create(childBranch);
		assertNotNull(conceptService.find(conceptId, childBranch));
		conceptService.deleteConceptAndComponents(conceptId, childBranch, false);
		assertNull(conceptService.find(conceptId, childBranch));

		// On parent branch, version new Concept
		codeSystemService.createVersion(codeSystem, 20210812, "20210812");
		conceptOnParent = conceptService.find(conceptId, parentBranch);
		assertVersioned(conceptOnParent, 20210812);

		// Rebase
		MergeReview review = getMergeReviewInCurrentState(parentBranch, childBranch);
		// AP calls this endpoint to check for conflicts
		assertEquals(0, reviewService.getMergeReviewConflictingConcepts(review.getId(), new ArrayList<>()).size());

		// Finalise rebase
		reviewService.applyMergeReview(review);

		// Expected state of child branch
		assertVersioned(conceptService.find(conceptId, childBranch), 20210812);
	}

	@Test
	void testAutoMergeWhenDescriptionDeletedSomewhereAndVersionedElsewhere() throws ServiceException, InterruptedException {
		String codeSystemShortName = "SNOMEDCT-TEST";
		String parentBranch = "MAIN/TEST";
		String childBranch = "MAIN/TEST/TEST-1";

		// Create Concept on CodeSystem
		CodeSystem codeSystem = codeSystemService.createCodeSystem(new CodeSystem(codeSystemShortName, parentBranch));
		Map<String, String> acceptabilityMap = Collections.singletonMap(US_EN_LANG_REFSET, descriptionAcceptabilityNames.get(ACCEPTABLE));
		Description pizzaFood = new Description("Pizza (food)").setTypeId(FSN).setCaseSignificance("CASE_INSENSITIVE").setAcceptabilityMap(acceptabilityMap);
		Description pizza = new Description("Pizza").setTypeId(SYNONYM).setCaseSignificance("CASE_INSENSITIVE").setAcceptabilityMap(acceptabilityMap);
		Relationship isA = new Relationship(ISA, SNOMEDCT_ROOT);
		Concept conceptOnParent = conceptService.create(new Concept().addDescription(pizzaFood).addDescription(pizza).addRelationship(isA), parentBranch);
		String conceptId = conceptOnParent.getId();

		// On child branch, delete Description
		branchService.create(childBranch);
		Concept conceptOnChild = conceptService.find(conceptId, childBranch);
		pizza = getDescription(conceptOnChild, "Pizza");
		assertNotNull(descriptionService.findDescription(childBranch, pizza.getId()));
		descriptionService.deleteDescription(pizza, childBranch, false);
		assertNull(descriptionService.findDescription(childBranch, pizza.getId()));

		// On child branch, add new Description
		Description pie = new Description("Pie").setTypeId(SYNONYM).setCaseSignificance("CASE_INSENSITIVE").setAcceptabilityMap(acceptabilityMap);
		conceptOnChild = conceptService.find(conceptId, childBranch);
		conceptOnChild.addDescription(pie);
		conceptService.update(conceptOnChild, childBranch);
		conceptOnChild = conceptService.find(conceptId, childBranch);
		pie = getDescription(conceptOnChild, "Pie");
		assertNotNull(descriptionService.findDescription(childBranch, pie.getId()));

		// On parent branch, version new Concept
		codeSystemService.createVersion(codeSystem, 20210812, "20210812");
		conceptOnParent = conceptService.find(conceptId, parentBranch);
		assertVersioned(conceptOnParent, 20210812);
		assertVersioned(getDescription(conceptOnParent, "Pizza (food)"), 20210812);
		assertVersioned(getDescription(conceptOnParent, "Pizza"), 20210812);

		// Rebase
		MergeReview review = getMergeReviewInCurrentState(parentBranch, childBranch);
		// AP calls this endpoint to check for conflicts
		assertEquals(1, reviewService.getMergeReviewConflictingConcepts(review.getId(), new ArrayList<>()).size());

		// User selects the RHS (i.e. the one without versioned content)
		reviewService.persistManuallyMergedConcept(review, Long.parseLong(conceptId), conceptOnChild);

		// Finalise rebase
		reviewService.applyMergeReview(review);

		// Expected state of child branch
		conceptOnChild = conceptService.find(conceptId, childBranch);
		assertEquals(conceptOnParent, conceptOnChild);
		assertEquals(conceptOnParent.getReleaseHash(), conceptOnChild.getReleaseHash());

		assertVersioned(getDescription(conceptOnChild, "Pizza (food)"), 20210812);
		assertVersioned(getDescription(conceptOnChild, "Pizza"), 20210812);
		assertNotVersioned(getDescription(conceptOnChild, "Pie"));
	}

	@Test
	void testAutoMergeWhenAcceptabilityEditedSomewhereAndVersionedElsewhere() throws ServiceException, InterruptedException {
		String codeSystemShortName = "SNOMEDCT-TEST";
		String parentBranch = "MAIN/TEST";
		String childBranch = "MAIN/TEST/TEST-1";
		Page<ReferenceSetMember> members;

		// Create Concept on CodeSystem
		CodeSystem codeSystem = codeSystemService.createCodeSystem(new CodeSystem(codeSystemShortName, parentBranch));
		Map<String, String> acceptabilityMap = Collections.singletonMap(US_EN_LANG_REFSET, descriptionAcceptabilityNames.get(PREFERRED));
		Description pizzaFood = new Description("Pizza (food)").setTypeId(FSN).setCaseSignificance("CASE_INSENSITIVE").setAcceptabilityMap(acceptabilityMap);
		Description pizza = new Description("Pizza").setTypeId(SYNONYM).setCaseSignificance("CASE_INSENSITIVE").setAcceptabilityMap(acceptabilityMap);
		Description pizzaPie = new Description("Pizza pie").setTypeId(SYNONYM).setCaseSignificance("CASE_INSENSITIVE").setAcceptabilityMap(acceptabilityMap);
		Relationship isA = new Relationship(ISA, SNOMEDCT_ROOT);
		Concept conceptOnParent = conceptService.create(new Concept().addDescription(pizzaFood).addDescription(pizza).addDescription(pizzaPie).addRelationship(isA), parentBranch);
		String conceptId = conceptOnParent.getId();

		// On parent branch, version new Concept
		codeSystemService.createVersion(codeSystem, 20210812, "20210812");
		conceptOnParent = conceptService.find(conceptId, parentBranch);
		members = memberService.findMembers(parentBranch, new MemberSearchRequest().referencedComponentId(pizzaPie.getId()).referenceSet(US_EN_LANG_REFSET), PageRequest.of(0, 10));
		assertVersioned(conceptOnParent, 20210812);
		assertVersioned(getDescription(conceptOnParent, "Pizza (food)"), 20210812);
		assertVersioned(getDescription(conceptOnParent, "Pizza"), 20210812);
		assertVersioned(getDescription(conceptOnParent, "Pizza pie"), 20210812);
		assertVersioned(getMember(members), 20210812);

		// On child branch, change Acceptability of Pizza pie
		branchService.create(childBranch);
		Concept conceptOnChild = conceptService.find(conceptId, childBranch);
		pizzaPie = getDescription(conceptOnChild, "Pizza pie");
		pizzaPie.addLanguageRefsetMember(US_EN_LANG_REFSET, ACCEPTABLE);
		conceptService.update(conceptOnChild, childBranch);
		members = memberService.findMembers(childBranch, new MemberSearchRequest().referencedComponentId(pizzaPie.getId()).referenceSet(US_EN_LANG_REFSET), PageRequest.of(0, 10));
		assertVersionedButChanged(getMember(members), 20210812); // This will be 20210813 in parent version post re-versioning

		// On parent branch, inactivate Pizza pie
		conceptOnParent = conceptService.find(conceptId, parentBranch);
		pizzaPie = getDescription(conceptOnParent, "Pizza pie");
		inactive(pizzaPie, "OUTDATED", null, null);
		conceptService.update(conceptOnParent, parentBranch);

		// On parent branch, version Concept again (task branch is now behind a version)
		codeSystemService.createVersion(codeSystem, 20210813, "20210813");
		conceptOnParent = conceptService.find(conceptId, parentBranch);
		members = memberService.findMembers(parentBranch, new MemberSearchRequest().referencedComponentId(pizzaPie.getId()).referenceSet(US_EN_LANG_REFSET), PageRequest.of(0, 10));
		assertVersioned(conceptOnParent, 20210812); // Not changed
		assertVersioned(getDescription(conceptOnParent, "Pizza (food)"), 20210812); // Not changed
		assertVersioned(getDescription(conceptOnParent, "Pizza"), 20210812); // Not changed
		assertVersioned(getDescription(conceptOnParent, "Pizza pie"), 20210813); // Changed as inactivated
		assertFalse(getDescription(conceptOnParent, "Pizza pie").isActive());
		assertVersioned(getMember(members), 20210813); // Changed as inactivated
		assertFalse(getMember(members).isActive());

		// Rebase
		MergeReview review = getMergeReviewInCurrentState(parentBranch, childBranch);
		// AP calls this endpoint to check for conflicts
		assertEquals(0, reviewService.getMergeReviewConflictingConcepts(review.getId(), new ArrayList<>()).size());

		// Finalise rebase
		reviewService.applyMergeReview(review);
		members = memberService.findMembers(childBranch, new MemberSearchRequest().referencedComponentId(pizzaPie.getId()).referenceSet(US_EN_LANG_REFSET), PageRequest.of(0, 10));
		assertVersioned(getMember(members), 20210813); // Overwritten child's version.
		assertFalse(getMember(members).isActive());
	}

	@Test
	void testAutoMergeWhenAxiomDeletedSomewhereAndVersionedElsewhere() throws ServiceException, InterruptedException {
		String codeSystemShortName = "SNOMEDCT-TEST";
		String parentBranch = "MAIN/TEST";
		String childBranch = "MAIN/TEST/TEST-1";

		// Create Concept on CodeSystem
		CodeSystem codeSystem = codeSystemService.createCodeSystem(new CodeSystem(codeSystemShortName, parentBranch));
		Map<String, String> acceptabilityMap = Collections.singletonMap(US_EN_LANG_REFSET, descriptionAcceptabilityNames.get(ACCEPTABLE));
		Description pizzaFood = new Description("Pizza (food)").setTypeId(FSN).setCaseSignificance("CASE_INSENSITIVE").setAcceptabilityMap(acceptabilityMap);
		Description pizza = new Description("Pizza").setTypeId(SYNONYM).setCaseSignificance("CASE_INSENSITIVE").setAcceptabilityMap(acceptabilityMap);
		Relationship isA = new Relationship(ISA, SNOMEDCT_ROOT);
		Concept conceptOnParent = conceptService.create(new Concept().addDescription(pizzaFood).addDescription(pizza).addAxiom(isA), parentBranch);
		String conceptId = conceptOnParent.getId();

		// On child branch, delete Axiom
		branchService.create(childBranch);
		Concept conceptOnChild = conceptService.find(conceptId, childBranch);
		String axiomId = conceptOnChild.getClassAxioms().iterator().next().getAxiomId();
		assertNotNull(memberService.findMember(childBranch, axiomId));
		memberService.deleteMember(childBranch, axiomId);
		assertNull(memberService.findMember(childBranch, axiomId));

		// On child branch, add new Axiom
		conceptOnChild = conceptService.find(conceptId, childBranch);
		assertEquals(0, getAxioms(conceptOnChild, false).size());
		conceptOnChild.addAxiom(new Relationship(ISA, HEART_STRUCTURE));
		conceptService.update(conceptOnChild, childBranch);
		conceptOnChild = conceptService.find(conceptId, childBranch);
		assertEquals(1, getAxioms(conceptOnChild, false).size());

		// On parent branch, version new Concept
		codeSystemService.createVersion(codeSystem, 20210812, "20210812");
		conceptOnParent = conceptService.find(conceptId, parentBranch);
		assertVersioned(conceptOnParent, 20210812);
		assertVersioned(getAxioms(conceptOnParent, true), 20210812);

		// Rebase
		MergeReview review = getMergeReviewInCurrentState(parentBranch, childBranch);
		// AP calls this endpoint to check for conflicts
		assertEquals(1, reviewService.getMergeReviewConflictingConcepts(review.getId(), new ArrayList<>()).size());

		// User selects the RHS (i.e. the one without versioned content)
		reviewService.persistManuallyMergedConcept(review, Long.parseLong(conceptId), conceptOnChild);

		// Finalise rebase
		reviewService.applyMergeReview(review);

		// Expected state of child branch
		conceptOnChild = conceptService.find(conceptId, childBranch);
		assertEquals(conceptOnParent, conceptOnChild);
		assertEquals(conceptOnParent.getReleaseHash(), conceptOnChild.getReleaseHash());
		assertVersioned(conceptOnChild, 20210812);
		assertVersioned(getAxioms(conceptOnParent, true), 20210812);
		assertNotVersioned(getAxioms(conceptOnParent, false));
	}

	@Test
	void testAutoMergeWhenMemberDeletedSomewhereAndVersionedElsewhere() throws ServiceException, InterruptedException {
		String codeSystemShortName = "SNOMEDCT-TEST";
		String parentBranch = "MAIN/TEST";
		String childBranch = "MAIN/TEST/TEST-1";

		// Create Concept on CodeSystem
		CodeSystem codeSystem = codeSystemService.createCodeSystem(new CodeSystem(codeSystemShortName, parentBranch));
		Map<String, String> acceptabilityMap = Collections.singletonMap(US_EN_LANG_REFSET, descriptionAcceptabilityNames.get(ACCEPTABLE));
		Description pizzaFood = new Description("Pizza (food)").setTypeId(FSN).setCaseSignificance("CASE_INSENSITIVE").setAcceptabilityMap(acceptabilityMap);
		Description pizza = new Description("Pizza").setTypeId(SYNONYM).setCaseSignificance("CASE_INSENSITIVE").setAcceptabilityMap(acceptabilityMap);
		Relationship isA = new Relationship(ISA, SNOMEDCT_ROOT);
		Concept conceptOnParent = conceptService.create(new Concept().addDescription(pizzaFood).addDescription(pizza).addAxiom(isA), parentBranch);
		String conceptId = conceptOnParent.getId();

		// Add Concept to reference set
		pizza = getDescription(conceptOnParent, "Pizza");
		ReferenceSetMember referenceSetMember = new ReferenceSetMember(UUID.randomUUID().toString(), null, true, CORE_MODULE, GB_EN_LANG_REFSET, pizza.getId());
		memberService.createMembers(parentBranch, Set.of(referenceSetMember));
		Page<ReferenceSetMember> members = memberService.findMembers(parentBranch, new MemberSearchRequest().referencedComponentId(pizza.getId()).referenceSet(GB_EN_LANG_REFSET), PageRequest.of(0, 10));
		assertEquals(1, members.getContent().size());

		// On child branch, remove Concept from reference set
		branchService.create(childBranch);
		members = memberService.findMembers(childBranch, new MemberSearchRequest().referencedComponentId(pizza.getId()).referenceSet(GB_EN_LANG_REFSET), PageRequest.of(0, 10));
		assertEquals(1, members.getContent().size());
		memberService.deleteMember(childBranch, referenceSetMember.getId());
		members = memberService.findMembers(childBranch, new MemberSearchRequest().referencedComponentId(pizza.getId()).referenceSet(GB_EN_LANG_REFSET), PageRequest.of(0, 10));
		assertEquals(0, members.getContent().size());

		// On parent branch, version new Concept
		codeSystemService.createVersion(codeSystem, 20210812, "20210812");
		conceptOnParent = conceptService.find(conceptId, parentBranch);
		assertVersioned(conceptOnParent, 20210812);
		assertVersioned(getAxioms(conceptOnParent, true), 20210812);
		referenceSetMember = memberService.findMember(parentBranch, referenceSetMember.getId());
		members = memberService.findMembers(parentBranch, new MemberSearchRequest().referencedComponentId(pizza.getId()).referenceSet(GB_EN_LANG_REFSET), PageRequest.of(0, 10));
		assertEquals(1, members.getContent().size());
		assertVersioned(referenceSetMember, 20210812);

		// Rebase
		MergeReview review = getMergeReviewInCurrentState(parentBranch, childBranch);
		// AP calls this endpoint to check for conflicts
		assertEquals(0, reviewService.getMergeReviewConflictingConcepts(review.getId(), new ArrayList<>()).size());

		// Finalise rebase
		reviewService.applyMergeReview(review);

		// Expected state of child branch
		referenceSetMember = memberService.findMember(childBranch, referenceSetMember.getId());
		members = memberService.findMembers(childBranch, new MemberSearchRequest().referencedComponentId(pizza.getId()).referenceSet(GB_EN_LANG_REFSET), PageRequest.of(0, 10));
		assertEquals(1, members.getContent().size());
		assertVersioned(referenceSetMember, 20210812);
	}

	private void assertNotVersioned(Description description) {
		assertNull(description.getEffectiveTime());
		assertFalse(description.isReleased());
	}

	private void assertNotVersioned(Set<Axiom> axioms) {
		for (Axiom axiom : axioms) {
			assertFalse(axiom.isReleased());
			assertNull(axiom.getEffectiveTimeI());
		}
	}

	private void assertVersionedButChanged(ReferenceSetMember referenceSetMember, Integer releaseEffectiveTime){
		assertNull(referenceSetMember.getEffectiveTimeI());
		assertTrue(referenceSetMember.isReleased());
		assertEquals(referenceSetMember.getReleasedEffectiveTime(), releaseEffectiveTime);
	}

	private void assertVersioned(Concept concept, Integer version) {
		assertNotNull(concept);
		assertEquals(concept.getEffectiveTimeI(), version);
		assertTrue(concept.isReleased());
	}

	private void assertVersioned(Description description, Integer version) {
		assertNotNull(description);
		assertEquals(description.getEffectiveTimeI(), version);
		assertTrue(description.isReleased());
	}

	private void assertVersioned(Set<Axiom> axioms, Integer version) {
		assertNotNull(axioms);
		for (Axiom axiom : axioms) {
			assertEquals(version, axiom.getEffectiveTimeI());
			assertTrue(axiom.isReleased());
		}
	}

	private void assertVersioned(ReferenceSetMember referenceSetMember, Integer version) {
		assertNotNull(referenceSetMember);
		assertEquals(version, referenceSetMember.getEffectiveTimeI());
		assertTrue(referenceSetMember.isReleased());
	}

	private Set<Axiom> getAxioms(Concept concept, boolean released) {
		Set<Axiom> axioms = new HashSet<>();
		for (Axiom classAxiom : concept.getClassAxioms()) {
			if (classAxiom.isReleased() == released) {
				axioms.add(classAxiom);
			}
		}

		return axioms;
	}

	private Description getDescription(Concept concept, String term) {
		if (concept == null || concept.getDescriptions() == null || concept.getDescriptions().isEmpty()) {
			return null;
		}


		Set<Description> descriptions = concept.getDescriptions();
		for (Description description : descriptions) {
			if (term.equals(description.getTerm())) {
				return description;
			}
		}

		return null;
	}

	private ReferenceSetMember getMember(Page<ReferenceSetMember> page){
		return page.getContent().iterator().next();
	}

	private MergeReview getMergeReviewInCurrentState(String source, String target) throws InterruptedException {
		MergeReview review = reviewService.createMergeReview(source, target);

		long maxWait = 10;
		long cumulativeWait = 0;
		while (review.getStatus() == PENDING && cumulativeWait < maxWait) {
			//noinspection BusyWait
			Thread.sleep(1_000);
			cumulativeWait++;
		}
		return review;
	}

	/**
	 * Set up a content conflict situation.
	 * Three versions of the same concept should be given.
	 * parentConcept is saved to branch MAIN/A and rebased to MAIN/A/A2
	 * leftConcept is then saved and promoted from MAIN/A/A1 to MAIN/A.
	 * rightConcept is then saved to MAIN/A/A2.
	 * At that point MAIN/A/A2 has a conflict in the rebase.
	 * @return The list of concepts after save, parent, left and right.
	 */
	private List<Concept> setupConflictSituation(Concept parentConcept, Concept leftConcept, Concept rightConcept) throws ServiceException {
		return setupConflictSituation(parentConcept, leftConcept, rightConcept, false);
	}

	private List<Concept> setupConflictSituationReleaseFirstVersion(Concept parentConcept, Concept leftConcept, Concept rightConcept) throws ServiceException {
		return setupConflictSituation(parentConcept, leftConcept, rightConcept, true);
	}

	private List<Concept> setupConflictSituation(Concept parentConcept, Concept leftConcept, Concept rightConcept, boolean versionCodeSystemAfterFirstSave) throws ServiceException {

		assertBranchState("MAIN/A", Branch.BranchState.UP_TO_DATE);
		assertBranchState("MAIN/A/A1", Branch.BranchState.UP_TO_DATE);
		assertBranchState("MAIN/A/A2", Branch.BranchState.UP_TO_DATE);

		// - Create concept on A
		conceptService.create(parentConcept, "MAIN/A");
		branchMergeService.mergeBranchSync("MAIN/A", "MAIN/A/A2", null);

		if (versionCodeSystemAfterFirstSave) {
			CodeSystem codeSystem = codeSystemService.find("SNOMEDCT-A");
			codeSystemService.createVersion(codeSystem, 20200131, "Version");
		}

		// Update concept on A1 and promote
		branchMergeService.mergeBranchSync("MAIN/A", "MAIN/A/A1", null);
		conceptService.update(leftConcept, "MAIN/A/A1");
		branchMergeService.mergeBranchSync("MAIN/A/A1", "MAIN/A", null);

		// Update concept on A2
		conceptService.update(rightConcept, "MAIN/A/A2");
		assertBranchState("MAIN/A/A2", Branch.BranchState.DIVERGED);

		// Conflict setup complete - rebase A2 for conflict
		return Lists.newArrayList(parentConcept, leftConcept, rightConcept);
	}
	
	private Concept assertBranchStateAndConceptVisibility(String path, Branch.BranchState expectedBranchState, String conceptId, boolean expectedVisible) {
		assertBranchState(path, expectedBranchState);
		if (expectedVisible) {
			return assertConceptVisible(path, conceptId);
		} else {
			assertConceptNotVisible(path, conceptId);
		}
		return null;
	}

	private Concept assertConceptVisible(String path, String conceptId) {
		final Concept concept = conceptService.find(conceptId, path);
		assertEquals(conceptId, concept.getConceptId());
		return concept;
	}

	private void assertConceptNotVisible(String path, String conceptId) {
		assertNull(conceptService.find(conceptId, path));
	}

	private void assertBranchState(String path, Branch.BranchState expectedBranchState) {
		assertEquals(expectedBranchState, branchService.findLatest(path).getState());
	}

	@SuppressWarnings("SameParameterValue")
	private void createConcept(String conceptId, String path) throws ServiceException {
		conceptService.create(
				new Concept(conceptId)
						.addDescription(
								new Description("Heart")
										.setCaseSignificance("CASE_INSENSITIVE")
										.setAcceptabilityMap(Collections.singletonMap(US_EN_LANG_REFSET,
												Concepts.descriptionAcceptabilityNames.get(Concepts.ACCEPTABLE)))
						)
						.addDescription(
								new Description("Heart structure (body structure)")
										.setTypeId(Concepts.FSN)
										.setCaseSignificance("CASE_INSENSITIVE")
										.setAcceptabilityMap(Collections.singletonMap(US_EN_LANG_REFSET,
												Concepts.descriptionAcceptabilityNames.get(Concepts.ACCEPTABLE))))
						.addRelationship(
								new Relationship(Concepts.ISA, Concepts.SNOMEDCT_ROOT)
						),
				path);
	}

	private Description getDescription(Concept concept) {
		if (concept == null || concept.getDescriptions() == null || concept.getDescriptions().isEmpty()) {
			return null;
		}
		List<Description> descriptions = concept.getDescriptions().stream().filter(d -> Concepts.FSN.equals(d.getTypeId())).collect(Collectors.toList());
		if (descriptions.iterator().hasNext()) {
			return descriptions.iterator().next();
		}
		return null;
	}

	private void setUpForChildBranchesTest() {
		String testRootPath = "MAIN/CHILDREN";
		int initialNumberOfChildren = 12; // leave above 10
		childBranches = new HashMap<>();

		// Create the branches we want to check
		// MAIN/CHILDREN
		branchService.create(testRootPath);
		// MAIN/CHILDREN/1 MAIN/CHILDREN/2 ... initialNumberOfChildren
		IntStream.rangeClosed(1, initialNumberOfChildren).forEach(i -> {
			String aPath = testRootPath + "/CHILD-" + i;
			childBranches.put(aPath, branchService.create(aPath));
		});
		// MAIN/CHILDREN/1/1 & MAIN/CHILDREN/1/2
		String aPath = testRootPath + "/CHILD-1/CHILD-1";
		childBranches.put(aPath, branchService.create(aPath));
		aPath = testRootPath + "/CHILD-1/CHILD-2";
		childBranches.put(aPath, branchService.create(aPath));
	}

	private void givenBranchReviewIncomplete() {
		Mockito.doAnswer(invocationOnMock -> {
			Object[] arguments = invocationOnMock.getArguments();
			Commit commit = (Commit) arguments[0];
			if (commit.getCommitType().equals(Commit.CommitType.PROMOTION)) {
				throw new RuntimeServiceException("Promotion blocked; not all criteria have been met.");
			}
			return null;
		}).when(commitServiceHookClient).preCommitCompletion(any());
	}
}
