package org.snomed.snowstorm.core.data.services;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import io.kaicode.elasticvc.api.BranchCriteria;
import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.api.VersionControlHelper;
import io.kaicode.elasticvc.domain.Branch;
import org.assertj.core.util.Maps;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.junit.After;
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
import org.snomed.snowstorm.core.data.services.pojo.MemberSearchRequest;
import org.snomed.snowstorm.core.data.services.traceability.Activity;
import org.snomed.snowstorm.rest.pojo.MergeRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.elasticsearch.core.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.*;
import java.util.stream.Collectors;

import static io.kaicode.elasticvc.api.ComponentService.LARGE_PAGE;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;
import static org.junit.Assert.*;
import static org.snomed.snowstorm.TestConfig.DEFAULT_LANGUAGE_CODES;
import static org.snomed.snowstorm.core.data.domain.review.ReviewStatus.CURRENT;
import static org.snomed.snowstorm.core.data.domain.review.ReviewStatus.PENDING;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = TestConfig.class)
public class BranchMergeServiceTest extends AbstractTest {

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
	private ElasticsearchTemplate elasticsearchTemplate;

	@Autowired
	private VersionControlHelper versionControlHelper;

	private List<Activity> activities;

	@Autowired
	private CodeSystemService codeSystemService;

	@Before
	public void setup() {
		conceptService.deleteAll();

		Map<String, String> metadata = new HashMap<>();
		metadata.put(BranchMetadataKeys.ASSERTION_GROUP_NAMES, "common-authoring");
		branchService.updateMetadata("MAIN", metadata);
		branchService.create("MAIN/A");
		branchService.create("MAIN/A/A1");
		branchService.create("MAIN/A/A2");
		branchService.create("MAIN/C");
		traceabilityLogService.setEnabled(true);
		activities = new ArrayList<>();
		traceabilityLogService.setActivityConsumer(activities::add);
	}

	@After
	public void tearDown() {
		conceptService.deleteAll();
		traceabilityLogService.setEnabled(false);
	}

	@Test
	public void testNewConceptWithoutComponentsRebaseAndPromotion() throws Exception {
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
		assertEquals("System performed merge of MAIN/A/A1 to MAIN/A", getLatestTraceabilityCommitComment());
		assertBranchStateAndConceptVisibility("MAIN", Branch.BranchState.UP_TO_DATE, conceptId, false);
		assertBranchStateAndConceptVisibility("MAIN/A", Branch.BranchState.FORWARD, conceptId, true);
		assertBranchStateAndConceptVisibility("MAIN/A/A1", Branch.BranchState.UP_TO_DATE, conceptId, true);
		assertBranchStateAndConceptVisibility("MAIN/A/A2", Branch.BranchState.BEHIND, conceptId, false);
		assertBranchStateAndConceptVisibility("MAIN/C", Branch.BranchState.UP_TO_DATE, conceptId, false);

		// Rebase to A2
		assertEquals("Before rebase versions.", 1, branchService.findAllVersions("MAIN/A/A2", LARGE_PAGE).getTotalElements());
		Date beforeRebaseTimepoint = new Date();
		Branch branchA2BeforeRebase = branchService.findAtTimepointOrThrow("MAIN/A/A2", beforeRebaseTimepoint);

		branchMergeService.mergeBranchSync("MAIN/A", "MAIN/A/A2", null);

		assertEquals("Before rebase, another version of the branch created.", 2, branchService.findAllVersions("MAIN/A/A2", LARGE_PAGE).getTotalElements());
		assertEquals("The base timepoint of the original version of the branch should not have changed.",
				branchA2BeforeRebase.getBase(), branchService.findAtTimepointOrThrow("MAIN/A/A2", beforeRebaseTimepoint).getBase());

		assertEquals("System performed merge of MAIN/A to MAIN/A/A2", getLatestTraceabilityCommitComment());
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
		assertEquals(1, mainBranch.getMetadata().size());
		assertEquals("common-authoring", mainBranch.getMetadata().get(BranchMetadataKeys.ASSERTION_GROUP_NAMES));
	}

	private String getLatestTraceabilityCommitComment() {
		return activities.get(activities.size() - 1).getCommitComment();
	}

	@Test
	public void testPromotionOfConceptAndDescriptions() throws ServiceException {
		assertBranchState("MAIN", Branch.BranchState.UP_TO_DATE);
		assertBranchState("MAIN/A", Branch.BranchState.UP_TO_DATE);
		assertBranchState("MAIN/A/A1", Branch.BranchState.UP_TO_DATE);
		assertBranchState("MAIN/A/A2", Branch.BranchState.UP_TO_DATE);
		assertBranchState("MAIN/C", Branch.BranchState.UP_TO_DATE);

		// - Create a concepts on A1 and A2
		final String concept1 = "10000100";
		final String concept2 = "10000200";
		conceptService.create(new Concept(concept1)
						.addDescription(new Description("One")),
				"MAIN/A/A1");
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
		assertEquals(1, concept1OnA1.getDescriptions().size());
		assertConceptNotVisible("MAIN/A/A1", concept2);

		// MAIN/A/A2 can see concept2 but not concept1
		final Concept concept2OnA2 = assertBranchStateAndConceptVisibility("MAIN/A/A2", Branch.BranchState.FORWARD, concept2, true);
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
		assertEquals(1, concept1OnA.getDescriptions().size());
		assertConceptNotVisible("MAIN/A", concept2);

		// MAIN/A/A1 is up-to-date and can still see concept1 but not concept2
		concept1OnA1 = assertBranchStateAndConceptVisibility("MAIN/A/A1", Branch.BranchState.UP_TO_DATE, concept1, true);
		assertEquals(1, concept1OnA1.getDescriptions().size());
		assertConceptNotVisible("MAIN/A/A1", concept2);

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
	public void testRebaseCapturesChangesAcrossBranchesForTransitiveClosureIncrementalUpdate() throws ServiceException {
		assertBranchState("MAIN", Branch.BranchState.UP_TO_DATE);
		assertBranchState("MAIN/A", Branch.BranchState.UP_TO_DATE);
		assertBranchState("MAIN/A/A1", Branch.BranchState.UP_TO_DATE);

		// MAIN: 100001 Root
		System.out.println("// MAIN: 1 Root");
		conceptService.create(new Concept("100001"), "MAIN");

		// MAIN: 100002 -> 100001
		System.out.println("// MAIN: 100002 -> 100001");
		conceptService.create(new Concept("100002").addRelationship(new Relationship(Concepts.ISA, "100001")), "MAIN");

		// Rebase MAIN/A
		System.out.println("// Rebase MAIN/A");
		assertBranchState("MAIN/A", Branch.BranchState.BEHIND);
		branchMergeService.mergeBranchSync("MAIN", "MAIN/A", null);

		// MAIN: 4 -> 1
		System.out.println("// MAIN: 100004 -> 100001");
		conceptService.create(new Concept("100004").addRelationship(new Relationship(Concepts.ISA, "100001")), "MAIN");

		// MAIN: 2 -> 4
		System.out.println("// MAIN: 100002 -> 100004");
		conceptService.update(new Concept("100002").addRelationship(new Relationship(Concepts.ISA, "100004")), "MAIN");
		Assert.assertEquals(Sets.newHashSet(100001L, 100004L), queryService.findAncestorIds("100002", "MAIN", true));

		// MAIN/A: 3 -> 2
		System.out.println("// MAIN/A: 100003 -> 100002");
		conceptService.create(new Concept("100003").addRelationship(new Relationship(Concepts.ISA, "100002")), "MAIN/A");
		Assert.assertEquals(Sets.newHashSet(100001L, 100002L), queryService.findAncestorIds("100003", "MAIN/A", true));

		// Rebase MAIN/A
		System.out.println("// Rebase MAIN/A");
		assertBranchState("MAIN/A", Branch.BranchState.DIVERGED);

		branchMergeService.mergeBranchSync("MAIN", "MAIN/A", Collections.emptySet());

		Assert.assertEquals(Sets.newHashSet(100001L, 100004L), queryService.findAncestorIds("100002", "MAIN/A", true));
		Assert.assertEquals(Sets.newHashSet(100001L, 100004L, 100002L), queryService.findAncestorIds("100003", "MAIN/A", true));
	}

/*
	// TODO: Disabled until production behaviour can be fixed.
	@Test(expected = IllegalArgumentException.class)
	public void testConflictWithoutManualMergeSupplied() throws ServiceException {
		final String concept1 = "100";
		final Concept concept = new Concept(concept1);
		conceptService.batchCreate(concept, "MAIN/A/A1");
		conceptService.batchCreate(concept, "MAIN/A");
		branchMergeService.mergeBranchSync("MAIN/A", "MAIN/A/A1", null);
	}
*/

	@Test
	public void testConflictConceptMergeChangesFromLeftIncludingStatedModeling() throws ServiceException {
		// Create concepts to be used in relationships
		conceptService.createUpdate(Lists.newArrayList(
				new Concept(Concepts.SNOMEDCT_ROOT),
				new Concept(Concepts.ISA).addRelationship(new Relationship(Concepts.ISA, Concepts.SNOMEDCT_ROOT)),
				new Concept(Concepts.CLINICAL_FINDING).addRelationship(new Relationship(Concepts.ISA, Concepts.SNOMEDCT_ROOT)),
				new Concept("131148009").addRelationship(new Relationship(Concepts.ISA, Concepts.SNOMEDCT_ROOT)),
				new Concept("313413008").addRelationship(new Relationship(Concepts.ISA, Concepts.SNOMEDCT_ROOT))
		), "MAIN");
		branchMergeService.mergeBranchSync("MAIN", "MAIN/A", Collections.emptySet());
		branchMergeService.mergeBranchSync("MAIN/A", "MAIN/A/A1", Collections.emptySet());
		branchMergeService.mergeBranchSync("MAIN/A", "MAIN/A/A2", Collections.emptySet());

		// Setup merge scenario
		final String conceptId = "10000100";
		final Description description = new Description("One");

		Concept leftConcept = new Concept(conceptId, "100009002")
				.addDescription(description)
				.addRelationship(new Relationship(Concepts.ISA, Concepts.CLINICAL_FINDING))
				.addRelationship(new Relationship(Concepts.ISA, "131148009"));
		setupConflictSituation(
				new Concept(conceptId, "100009001")
						.addDescription(description)
						.addRelationship(new Relationship(Concepts.ISA, Concepts.CLINICAL_FINDING)),
				leftConcept,
				new Concept(conceptId, "100009003")
						.addDescription(description)
						.addRelationship(new Relationship(Concepts.ISA, Concepts.CLINICAL_FINDING))
						.addRelationship(new Relationship(Concepts.ISA, "313413008"))
		);

		// Rebase the diverged branch supplying the manually merged concept
		branchMergeService.mergeBranchSync("MAIN/A", "MAIN/A/A2", Collections.singleton(leftConcept));

		assertEquals("100009002", conceptService.find(conceptId, "MAIN/A/A2").getModuleId());

		// Promote the branch (no conflicts at this point)
		branchMergeService.mergeBranchSync("MAIN/A/A2", "MAIN/A", null);
		assertEquals("100009002", conceptService.find(conceptId, "MAIN/A").getModuleId());
		Set<Long> ancestorIds = queryService.findAncestorIds(conceptId, "MAIN/A", true);
		assertEquals(toLongSet(Concepts.SNOMEDCT_ROOT, Concepts.CLINICAL_FINDING, "131148009"), ancestorIds);
	}

	@Test
	public void testAutomaticMergeOfConceptDoubleInactivation() throws ServiceException {
		// The same concept is made inactive on two different branches with different inactivation reasons and historical associations.
		// The concept comes up in the rebase review and the picked version should be kept.
		// The redundant inactivation reason and historical association must be removed.

		// Create concepts to be used in test
		String conceptAId = "131148009";
		String conceptBId = "313413008";
		conceptService.createUpdate(Lists.newArrayList(
				new Concept(Concepts.SNOMEDCT_ROOT),
				new Concept(Concepts.ISA).addRelationship(new Relationship(Concepts.ISA, Concepts.SNOMEDCT_ROOT)),
				new Concept(Concepts.CLINICAL_FINDING).addRelationship(new Relationship(Concepts.ISA, Concepts.SNOMEDCT_ROOT)),
				new Concept(conceptAId).addRelationship(new Relationship(Concepts.ISA, Concepts.CLINICAL_FINDING)).addDescription(new Description("thingamajig")),
				new Concept(conceptBId).addRelationship(new Relationship(Concepts.ISA, Concepts.CLINICAL_FINDING))
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
			System.out.println(referenceSetMember.getReferencedComponentId() + " - " + referenceSetMember.getRefsetId() + " - " + referenceSetMember.toString());
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
		concept.setInactivationIndicator("OUTDATED");
		concept.setAssociationTargets(Maps.newHashMap("REPLACED_BY", Sets.newHashSet(conceptBId)));
		conceptService.update(concept, taskA2);
		assertEquals(2, memberService.findMembers(taskA2, conceptAId, LARGE_PAGE).getTotalElements());
		assertEquals(1, memberService.findMembers(taskA2, descriptionInactivationMemberSearchRequest, LARGE_PAGE).getTotalElements());

		// Promote task A1
		assertEquals(0, memberService.findMembers("MAIN/A", conceptAId, LARGE_PAGE).getTotalElements());
		branchMergeService.mergeBranchSync(taskA1, "MAIN/A", Collections.emptySet());
		assertEquals(2, memberService.findMembers("MAIN/A", conceptAId, LARGE_PAGE).getTotalElements());
		assertEquals(1, memberService.findMembers(taskA1, descriptionInactivationMemberSearchRequest, LARGE_PAGE).getTotalElements());

		// Rebase the diverged branch supplying the A2 concept version as the manually merged concept
		concept.getInactivationIndicatorMembers().clear();
		concept.getDescriptions().iterator().next().getInactivationIndicatorMembers().clear();
		branchMergeService.mergeBranchSync("MAIN/A", taskA2, Collections.singleton(concept));
		Page<ReferenceSetMember> members = memberService.findMembers(taskA2, conceptAId, LARGE_PAGE);
		members.getContent().forEach(System.out::println);
		assertEquals(2, members.getTotalElements());
		assertEquals(1, memberService.findMembers(taskA2, descriptionInactivationMemberSearchRequest, LARGE_PAGE).getTotalElements());
	}

	@Test
	public void testAutomaticMergeOfInferredAdditions() throws ServiceException {
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
		return elasticsearchTemplate.queryForList(
				new NativeSearchQueryBuilder().withQuery(query).build(), Description.class);
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
	public void testAutomaticMergeOfInferredChange() throws ServiceException {
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

		List<Relationship> rels = elasticsearchTemplate.queryForList(new NativeSearchQueryBuilder()
				.withQuery(boolQuery()
						.must(termsQuery(Relationship.Fields.SOURCE_ID, conceptId))
						.must(termsQuery(Relationship.Fields.CHARACTERISTIC_TYPE_ID, Concepts.INFERRED_RELATIONSHIP)))
				.withPageable(LARGE_PAGE)
				.withSort(SortBuilders.fieldSort("start")).build(), Relationship.class);
		for (Relationship rel : rels) {
			System.out.println(rel);
		}

		List<QueryConcept> queryConceptsAcrossBranches = elasticsearchTemplate.queryForList(new NativeSearchQueryBuilder()
				.withQuery(boolQuery()
						.must(termsQuery(QueryConcept.Fields.CONCEPT_ID, conceptId))
						.must(termsQuery(QueryConcept.Fields.STATED, false)))
				.withPageable(LARGE_PAGE)
				.withSort(SortBuilders.fieldSort("start")).build(), QueryConcept.class);
		for (QueryConcept queryConceptsAcrossBranch : queryConceptsAcrossBranches) {
			System.out.println(queryConceptsAcrossBranch);
		}

		List<Branch> branches = elasticsearchTemplate.queryForList(new NativeSearchQueryBuilder()
				.withSort(SortBuilders.fieldSort("start"))
				.withPageable(LARGE_PAGE)
				.build(), Branch.class);
		for (Branch branch : branches) {
			System.out.println(branch);
		}

		assertEquals(1, queryService.eclSearch(conceptId, false, "MAIN/A/A1", LARGE_PAGE).getTotalElements());

		branchMergeService.mergeBranchSync("MAIN/A/A1", "MAIN/A", Collections.emptySet());
		assertEquals(1, countRelationships("MAIN/A", conceptId, Relationship.CharacteristicType.inferred));
	}

	@Test
	public void testAutomaticMergeOfSynonymChange() throws ServiceException {
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
	public void testAutomaticMergeOfRefsetMemberChange() throws ServiceException {
		String referencedComponent = Concepts.CLINICAL_FINDING;
		ReferenceSetMember member = memberService.createMember("MAIN",
				new ReferenceSetMember(Concepts.CORE_MODULE, Concepts.REFSET_MRCM_ATTRIBUTE_DOMAIN_INTERNATIONAL, referencedComponent));

		String memberId = member.getMemberId();
		branchMergeService.mergeBranchSync("MAIN", "MAIN/A", Collections.emptySet());
		branchMergeService.mergeBranchSync("MAIN/A", "MAIN/A/A1", Collections.emptySet());

		// Update member module on MAIN/A
		setMemeberModule(memberId, "MAIN/A", Concepts.MODEL_MODULE);

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

	public void setMemeberModule(String memberId, String branch, String module) {
		ReferenceSetMember member;
		member = memberService.findMember(branch, memberId);
		member.setModuleId(module);
		memberService.updateMember(branch, member);
	}

	public long countMembers(String referencedComponent, String branch) {
		return memberService.findMembers(branch, referencedComponent, LARGE_PAGE).getTotalElements();
	}


	@Test
	public void testConflictConceptMergeChangesFromRight() throws ServiceException {
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
		Assert.assertEquals("100009003", conceptFromMergedA2);

		// Promote the branch (no conflicts at this point)
		branchMergeService.mergeBranchSync("MAIN/A/A2", "MAIN/A", null);
		assertEquals("100009003", conceptService.find(conceptId, "MAIN/A").getModuleId());
	}

	@Test
	public void testConflictConceptMergeChangesFromRightSameChangeOnBothSides() throws ServiceException {
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
		Assert.assertEquals("100009003", conceptFromMergedA2);

		// Promote the branch (no conflicts at this point)
		branchMergeService.mergeBranchSync("MAIN/A/A2", "MAIN/A", null);
		assertEquals("100009003", conceptService.find(conceptId, "MAIN/A").getModuleId());
	}

	@Test
	public void testConflictConceptMergeChangesFromNowhere() throws ServiceException {
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
	public void testConflictDescriptionsNewOnBothSides() throws ServiceException {
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
	public void testConflictDescriptionInactiveWithDifferentReasonOnBothSides() throws ServiceException, InterruptedException {
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
		assertTrue(descriptionA2.isReleased());
		assertFalse(descriptionA2.isActive());
		assertEquals("OUTDATED", descriptionA2.getInactivationIndicator());
		assertNull(descriptionA2.getAssociationTargets());
		descriptionA2 = conceptService.find(conceptId, "MAIN/A/A2").getDescriptions().iterator().next();
		assertTrue(descriptionA2.isReleased());
		assertFalse(descriptionA2.isActive());
		assertEquals("OUTDATED", descriptionA2.getInactivationIndicator());
		assertNull(descriptionA2.getAssociationTargets());


		MergeReview mergeReview = reviewService.createMergeReview("MAIN/A", "MAIN/A/A2");
		// Wait for completion
		for (int i = 0; mergeReview.getStatus() == PENDING && i < 20; i++) {
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
		Concept rightSideConcept = conceptVersions.get(2);
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

	private Description inactive(Description description, String reason, String association, String associationTarget) {
		description.setActive(false);
		description.setInactivationIndicator(reason);
		if (association != null) {
			description.setAssociationTargets(Maps.newHashMap(association, Sets.newHashSet(associationTarget)));
		}
		return description;
	}

	@Test
	public void testConflictDescriptionsNewOnBothSidesAllDeletedInManualMerge() throws ServiceException {
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
	public void testConcurrentPromotionBlockedByBranchLock() throws ServiceException, InterruptedException {
		conceptService.create(new Concept("10000100").addDescription(new Description("100001")), "MAIN/A");
		conceptService.create(new Concept("10000100").addDescription(new Description("100002")), "MAIN/C");

		BranchMergeJob branchMergeJobA = branchMergeService.mergeBranchAsync(new MergeRequest("MAIN/A", "MAIN", "Promote A", null));
		BranchMergeJob branchMergeJobC = branchMergeService.mergeBranchAsync(new MergeRequest("MAIN/C", "MAIN", "Promote C", null));

		Thread.sleep(2000);

		List<BranchMergeJob> jobs = Lists.newArrayList(branchMergeJobA, branchMergeJobC);
		List<BranchMergeJob> completeJobs = jobs.stream().filter(job -> job.getStatus() == JobStatus.COMPLETED).collect(Collectors.toList());
		List<BranchMergeJob> failedJobs = jobs.stream().filter(job -> job.getStatus() == JobStatus.FAILED).collect(Collectors.toList());

		assertEquals(1, completeJobs.size());
		assertEquals(1, failedJobs.size());
		assertEquals("Branch MAIN is already locked", failedJobs.get(0).getMessage());
	}

	@Test
	public void testCreateMergeReviewConceptDeletedOnChildAcceptDeleted() throws InterruptedException, ServiceException {
		createConcept("10000100", "MAIN");
		branchService.create("MAIN/A1");

		// Update concept 10000100 description on A
		Concept concept = conceptService.find("10000100", "MAIN");
		getDescription(concept, true).setCaseSignificance("INITIAL_CHARACTER_CASE_INSENSITIVE");
		conceptService.update(concept, "MAIN");

		// Delete concept 10000100 on MAIN/A
		conceptService.deleteConceptAndComponents("10000100", "MAIN/A1", false);

		String source = "MAIN";
		String target = "MAIN/A1";
		MergeReview review = getMergeReviewInCurrentState(source, target);

		assertEquals(ReviewStatus.CURRENT, review.getStatus());

		Collection<MergeReviewConceptVersions> mergeReviewConflictingConcepts = reviewService.getMergeReviewConflictingConcepts(review.getId(), DEFAULT_LANGUAGE_CODES);
		assertEquals(1, mergeReviewConflictingConcepts.size());

		// Check concept is only there on the source side.
		MergeReviewConceptVersions mergeReviewConceptVersions = mergeReviewConflictingConcepts.iterator().next();
		assertNotNull(mergeReviewConceptVersions.getSourceConcept());
		assertEquals("10000100", mergeReviewConceptVersions.getSourceConcept().getConceptId());
		assertNull(mergeReviewConceptVersions.getTargetConcept());
		assertNull(mergeReviewConceptVersions.getAutoMergedConcept());

		// Accept the deleted version
		reviewService.persistManualMergeConceptDeletion(review.getId(), 10000100L);

		reviewService.applyMergeReview(review.getId());

		assertNull("Concept should be deleted after the merge.", conceptService.find("10000100", "MAIN/A1"));
	}

	@Test
	public void testCreateMergeReviewConceptDeletedOnParentAcceptDeleted() throws InterruptedException, ServiceException {
		createConcept("10000100", "MAIN");
		branchService.create("MAIN/A1");

		// Update concept 10000100 description on A
		Concept concept = conceptService.find("10000100", "MAIN/A1");
		getDescription(concept, true).setCaseSignificance("INITIAL_CHARACTER_CASE_INSENSITIVE");
		conceptService.update(concept, "MAIN/A1");

		// Delete concept 10000100 on MAIN
		conceptService.deleteConceptAndComponents("10000100", "MAIN", false);

		String source = "MAIN";
		String target = "MAIN/A1";
		MergeReview review = getMergeReviewInCurrentState(source, target);

		assertEquals(ReviewStatus.CURRENT, review.getStatus());

		Collection<MergeReviewConceptVersions> mergeReviewConflictingConcepts = reviewService.getMergeReviewConflictingConcepts(review.getId(), DEFAULT_LANGUAGE_CODES);
		assertEquals(1, mergeReviewConflictingConcepts.size());

		// Check concept is only there on the source side.
		MergeReviewConceptVersions mergeReviewConceptVersions = mergeReviewConflictingConcepts.iterator().next();
		assertNull(mergeReviewConceptVersions.getSourceConcept());
		assertNotNull(mergeReviewConceptVersions.getTargetConcept());
		assertEquals("10000100", mergeReviewConceptVersions.getTargetConcept().getConceptId());
		assertNull(mergeReviewConceptVersions.getAutoMergedConcept());

		// Accept the deleted version
		reviewService.persistManualMergeConceptDeletion(review.getId(), 10000100L);

		reviewService.applyMergeReview(review.getId());

		assertNull("Concept should be deleted after the merge.", conceptService.find("10000100", "MAIN/A1"));
	}

	@Test
	public void testCreateMergeReviewConceptDeletedOnChildAcceptUpdated() throws InterruptedException, ServiceException {
		createConcept("10000100", "MAIN");
		branchService.create("MAIN/A1");

		// Update concept 10000100 description on A
		Concept concept = conceptService.find("10000100", "MAIN");
		getDescription(concept, true).setCaseSignificance("INITIAL_CHARACTER_CASE_INSENSITIVE");
		conceptService.update(concept, "MAIN");

		// Delete concept 10000100 on MAIN/A
		conceptService.deleteConceptAndComponents("10000100", "MAIN/A1", false);

		String source = "MAIN";
		String target = "MAIN/A1";
		MergeReview review = getMergeReviewInCurrentState(source, target);

		assertEquals(ReviewStatus.CURRENT, review.getStatus());

		Collection<MergeReviewConceptVersions> mergeReviewConflictingConcepts = reviewService.getMergeReviewConflictingConcepts(review.getId(), DEFAULT_LANGUAGE_CODES);
		assertEquals(1, mergeReviewConflictingConcepts.size());

		// Check concept is only there on the source side.
		MergeReviewConceptVersions mergeReviewConceptVersions = mergeReviewConflictingConcepts.iterator().next();
		assertNotNull(mergeReviewConceptVersions.getSourceConcept());
		assertEquals("10000100", mergeReviewConceptVersions.getSourceConcept().getConceptId());
		assertNull(mergeReviewConceptVersions.getTargetConcept());
		assertNull(mergeReviewConceptVersions.getAutoMergedConcept());

		// Accept the updated
		reviewService.persistManuallyMergedConcept(review.getId(), mergeReviewConceptVersions.getSourceConcept().getConceptIdAsLong(), mergeReviewConceptVersions.getSourceConcept());

		reviewService.applyMergeReview(review.getId());

		assertNotNull("Concept should be restored after the merge.", conceptService.find("10000100", "MAIN/A1"));
	}

	@Test
	public void testCreateMergeReviewConceptDeletedOnParentAcceptUpdated() throws InterruptedException, ServiceException {
		createConcept("10000100", "MAIN");
		branchService.create("MAIN/A1");

		// Update concept 10000100 description on A
		Concept concept = conceptService.find("10000100", "MAIN/A1");
		getDescription(concept, true).setCaseSignificance("INITIAL_CHARACTER_CASE_INSENSITIVE");
		conceptService.update(concept, "MAIN/A1");

		// Delete concept 10000100 on MAIN
		conceptService.deleteConceptAndComponents("10000100", "MAIN", false);

		String source = "MAIN";
		String target = "MAIN/A1";
		MergeReview review = getMergeReviewInCurrentState(source, target);

		assertEquals(ReviewStatus.CURRENT, review.getStatus());

		Collection<MergeReviewConceptVersions> mergeReviewConflictingConcepts = reviewService.getMergeReviewConflictingConcepts(review.getId(), DEFAULT_LANGUAGE_CODES);
		assertEquals(1, mergeReviewConflictingConcepts.size());

		// Check concept is only there on the source side.
		MergeReviewConceptVersions mergeReviewConceptVersions = mergeReviewConflictingConcepts.iterator().next();
		assertNull(mergeReviewConceptVersions.getSourceConcept());
		assertNotNull(mergeReviewConceptVersions.getTargetConcept());
		assertEquals("10000100", mergeReviewConceptVersions.getTargetConcept().getConceptId());
		assertNull(mergeReviewConceptVersions.getAutoMergedConcept());

		// Accept the updated version
		reviewService.persistManuallyMergedConcept(review.getId(), mergeReviewConceptVersions.getTargetConcept().getConceptIdAsLong(), mergeReviewConceptVersions.getTargetConcept());

		reviewService.applyMergeReview(review.getId());

		assertNotNull("Concept should be restored after the merge.", conceptService.find("10000100", "MAIN/A1"));
	}

	private MergeReview getMergeReviewInCurrentState(String source, String target) throws InterruptedException {
		MergeReview review = reviewService.createMergeReview(source, target);

		long maxWait = 10;
		long cumulativeWait = 0;
		while (review.getStatus() == PENDING && cumulativeWait < maxWait) {
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
	 * @return
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

		if (versionCodeSystemAfterFirstSave) {
			codeSystemService.createCodeSystem(new CodeSystem("SNOMEDCT", "MAIN/A"));
			CodeSystem codeSystem = codeSystemService.find("SNOMEDCT");
			codeSystemService.createVersion(codeSystem, 20200131, "Version");
		}

		// - Rebase A1 and A2
		branchMergeService.mergeBranchSync("MAIN/A", "MAIN/A/A1", null);
		branchMergeService.mergeBranchSync("MAIN/A", "MAIN/A/A2", null);

		// Update concept on A1 and promote
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
		Assert.assertNull(conceptService.find(conceptId, path));
	}

	private void assertBranchState(String path, Branch.BranchState expectedBranchState) {
		assertEquals(expectedBranchState, branchService.findLatest(path).getState());
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
