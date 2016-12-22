package org.ihtsdo.elasticsnomed.services;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.Sets;
import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.api.VersionControlHelper;
import io.kaicode.elasticvc.domain.Branch;
import org.ihtsdo.elasticsnomed.domain.Concept;
import org.ihtsdo.elasticsnomed.domain.Description;
import org.ihtsdo.elasticsnomed.domain.ReferenceSetMember;
import org.ihtsdo.elasticsnomed.domain.Relationship;
import io.kaicode.elasticvc.api.ComponentService;
import org.ihtsdo.elasticsnomed.domain.review.*;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.core.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.data.util.CloseableIterator;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static java.lang.Long.parseLong;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

@Service
public class BranchReviewService {

	@Autowired
	private BranchService branchService;

	@Autowired
	private VersionControlHelper versionControlHelper;

	@Autowired
	private ConceptService conceptService;

	@Autowired
	private BranchMergeService branchMergeService;

	@Autowired
	private ElasticsearchTemplate elasticsearchTemplate;

	private final ExecutorService executorService = Executors.newCachedThreadPool();

	private final Map<String, BranchReview> reviewIndex = new HashMap<>();

	// TODO: Move to elasticsearch storage to allow access from any termserver instance in a cluster
	private final Cache<String, BranchReview> reviewStore = CacheBuilder.newBuilder()
			.expireAfterWrite(12, TimeUnit.HOURS)
			.removalListener(removalNotification -> {
				// When review expires remove from index map
				BranchReview review = (BranchReview) removalNotification.getValue();
				final BranchState source = review.getSource();
				final BranchState target = review.getTarget();
				final String reviewIndexKey = getReviewIndexKey(source.getPath(), source.getHeadTimestamp(), target.getPath(), target.getHeadTimestamp());
				reviewIndex.remove(reviewIndexKey);
			})
			.build();

	private final Cache<String, MergeReview> mergeReviewStore = CacheBuilder.newBuilder()
			.expireAfterWrite(12, TimeUnit.HOURS)
			.build();

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public MergeReview createMergeReview(String source, String target) {
		final Branch sourceBranch = branchService.findBranchOrThrow(source);
		final Branch targetBranch = branchService.findBranchOrThrow(target);
		if (!sourceBranch.isParent(targetBranch)) {
			throw new IllegalArgumentException("A merge review should only be used during rebase, not promotion.");
		}
		final BranchReview sourceToTarget = getCreateReview(sourceBranch, targetBranch);
		final BranchReview targetToSource = getCreateReview(targetBranch, sourceBranch);
		final MergeReview mergeReview = new MergeReview(UUID.randomUUID().toString(), source, target,
				sourceToTarget.getId(), targetToSource.getId());
		mergeReview.setStatus(ReviewStatus.PENDING);
		executorService.submit((Runnable) () -> {
			sourceToTarget.getChanges();
			targetToSource.getChanges();
			mergeReview.setStatus(ReviewStatus.CURRENT);
		});
		mergeReviewStore.put(mergeReview.getId(), mergeReview);
		return mergeReview;
	}

	public MergeReview getMergeReview(String id) {
		final MergeReview mergeReview = mergeReviewStore.getIfPresent(id);
		if (mergeReview != null && mergeReview.getStatus() == ReviewStatus.CURRENT) {
			// Only check one merge review - they will both have the same status.
			final ReviewStatus newStatus = reviewStore.getIfPresent(mergeReview.getSourceToTargetReviewId()).getStatus();
			mergeReview.setStatus(newStatus);
		}
		return mergeReview;
	}

	public MergeReview getMergeReviewOrThrow(String id) {
		final MergeReview mergeReview = getMergeReview(id);
		if (mergeReview == null) {
			throw new IllegalArgumentException("Merge review " + id + " does not exist.");
		}
		return mergeReview;
	}

	public Collection<MergeReviewConceptVersions> getMergeReviewConflictingConcepts(String id) {
		final MergeReview mergeReview = getMergeReviewOrThrow(id);
		assertMergeReviewCurrent(mergeReview);

		final Sets.SetView<Long> conceptsChangedInBoth = getConflictingConceptIds(mergeReview);

		final Collection<Concept> conceptOnSource = conceptService.find(mergeReview.getSourcePath(), conceptsChangedInBoth);
		final Collection<Concept> conceptOnTarget = conceptService.find(mergeReview.getTargetPath(), conceptsChangedInBoth);

		final Map<Long, MergeReviewConceptVersions> conflicts = new HashMap<>();
		conceptOnSource.stream().forEach(concept -> conflicts.put(concept.getConceptIdAsLong(), new MergeReviewConceptVersions(concept)));
		conceptOnTarget.stream().forEach(targetConcept -> {
			final MergeReviewConceptVersions conceptVersions = conflicts.get(targetConcept.getConceptIdAsLong());
			conceptVersions.setTargetConcept(targetConcept);
			conceptVersions.setAutoMergedConcept(autoMergeConcept(conceptVersions.getSourceConcept(), targetConcept));
		});

		return conflicts.values();
	}

	public void applyMergeReview(String mergeReviewId) {
		final MergeReview mergeReview = getMergeReviewOrThrow(mergeReviewId);
		assertMergeReviewCurrent(mergeReview);

		// Check all conflicts manually merged
		final Map<Long, Concept> manuallyMergedConcepts = mergeReview.getManuallyMergedConcepts();
		final Sets.SetView<Long> conflictingConceptIds = getConflictingConceptIds(mergeReview);
		final Set<Long> conflictsRemaining = new HashSet<>(conflictingConceptIds);
		conflictsRemaining.removeAll(manuallyMergedConcepts.keySet());
		if (!conflictsRemaining.isEmpty()) {
			throw new IllegalStateException("Not all conflicting concepts have been resolved. Unresolved: " + conflictsRemaining);
		}
		if (manuallyMergedConcepts.keySet().size() > conflictingConceptIds.size()) {
			throw new IllegalStateException("There are more manually merged concepts than conflicts. Can not proceed.");
		}

		// Perform rebase merge
		branchMergeService.mergeBranchSync(mergeReview.getSourcePath(), mergeReview.getTargetPath(), manuallyMergedConcepts.values());
	}

	private void assertMergeReviewCurrent(MergeReview mergeReview) {
		if (mergeReview.getStatus() != ReviewStatus.CURRENT) {
			throw new IllegalStateException("Merge review state is not current");
		}
	}

	private Sets.SetView<Long> getConflictingConceptIds(MergeReview mergeReview) {
		final BranchReview sourceToTargetReview = reviewStore.getIfPresent(mergeReview.getSourceToTargetReviewId());
		final BranchReview targetToSourceReview = reviewStore.getIfPresent(mergeReview.getSourceToTargetReviewId());
		return Sets.intersection(sourceToTargetReview.getChanges().getChangedConcepts(), targetToSourceReview.getChanges().getChangedConcepts());
	}

	private Concept autoMergeConcept(Concept sourceConcept, Concept targetConcept) {
		// TODO - Is providing an auto-merged concept required?
		return sourceConcept;
	}

	public BranchReview getCreateReview(String source, String target) {
		final Branch sourceBranch = branchService.findBranchOrThrow(source);
		final Branch targetBranch = branchService.findBranchOrThrow(target);
		return getCreateReview(sourceBranch, targetBranch);
	}

	public BranchReview getCreateReview(Branch sourceBranch, Branch targetBranch) {
		final String reviewIndexKey = getReviewIndexKey(sourceBranch.getFatPath(), sourceBranch.getHeadTimestamp(),
				targetBranch.getFatPath(), targetBranch.getHeadTimestamp());

		final BranchReview existingReview = reviewIndex.get(reviewIndexKey);
		if (existingReview != null) {
			return existingReview;
		}

		return createReview(sourceBranch, targetBranch, reviewIndexKey);
	}

	private BranchReview createReview(Branch sourceBranch, Branch targetBranch, String reviewIndexKey) {
		// Validate arguments
		if (!branchService.branchesHaveParentChildRelationship(sourceBranch, targetBranch)) {
			throw new IllegalArgumentException("The source or target branch must be the direct parent of the other.");
		}

		// Create review
		final BranchReview branchReview = new BranchReview(UUID.randomUUID().toString(), new Date(), ReviewStatus.CURRENT,
				new BranchState(sourceBranch), new BranchState(targetBranch), sourceBranch.isParent(targetBranch));

		reviewStore.put(branchReview.getId(), branchReview);
		reviewIndex.put(reviewIndexKey, branchReview);
		return branchReview;
	}

	public BranchReview getBranchReview(String reviewId) {
		final BranchReview branchReview = reviewStore.getIfPresent(reviewId);

		if (branchReview != null) {
			branchReview.setStatus(
					isBranchStateCurrent(branchReview.getSource())
							&& isBranchStateCurrent(branchReview.getTarget()) ? ReviewStatus.CURRENT : ReviewStatus.STALE);
		}

		return branchReview;
	}

	public boolean isBranchStateCurrent(BranchState branchState) {
		final Branch branch = branchService.findBranchOrThrow(branchState.getPath());
		return branch.getBase().getTime() == branchState.getBaseTimestamp() && branch.getHead().getTime() == branchState.getHeadTimestamp();
	}

	public BranchReviewConceptChanges getBranchReviewConceptChanges(String reviewId) {
		final BranchReview review = getBranchReview(reviewId);

		if (review == null) {
			return null;
		}

		if (review.getStatus() != ReviewStatus.CURRENT) {
			throw new IllegalStateException("Branch review is not current.");
		}

		if (review.getChanges() == null) {
			synchronized (review) {
				// Still null after we acquire the lock?
				if (review.getChanges() == null) {
					final Branch source = branchService.findBranchOrThrow(review.getSource().getPath());
					final Branch target = branchService.findBranchOrThrow(review.getTarget().getPath());

					// source = A
					// target = A/B
					// start = target base

					// source = A/B
					// target = A
					// start = source lastPromotion

					Date start;
					if (review.isSourceIsParent()) {
						start = target.getBase();
					} else {
						start = source.getLastPromotion();
					}
					review.setChanges(createConceptChangeReportOnBranchForTimeRange(source.getFatPath(), start, source.getHead(), review.isSourceIsParent()));
				}
			}
		}
		return review.getChanges();
	}

	public BranchReviewConceptChanges createConceptChangeReportOnBranchForTimeRange(String path, Date start, Date end, boolean sourceIsParent) {

		// Find components of each type that are on the target branch and have been ended on the source branch
		final Set<Long> conceptsWithEndedVersions = new HashSet<>();
		final Set<Long> conceptsWithNewVersions = new HashSet<>();
		final Set<Long> conceptsWithComponentChange = new HashSet<>();
		if (!sourceIsParent) {
			// Technique: Iterate child's 'versionsReplaced' set
			final Set<String> versionsReplaced = branchService.findBranchOrThrow(path).getVersionsReplaced();
			try (final CloseableIterator<Concept> stream = elasticsearchTemplate.stream(componentsReplacedCriteria(versionsReplaced), Concept.class)) {
				stream.forEachRemaining(concept -> conceptsWithEndedVersions.add(parseLong(concept.getConceptId())));
			}
			try (final CloseableIterator<Description> stream = elasticsearchTemplate.stream(componentsReplacedCriteria(versionsReplaced), Description.class)) {
				stream.forEachRemaining(description -> conceptsWithComponentChange.add(parseLong(description.getConceptId())));
			}
			try (final CloseableIterator<Relationship> stream = elasticsearchTemplate.stream(componentsReplacedCriteria(versionsReplaced), Relationship.class)) {
				stream.forEachRemaining(relationship -> conceptsWithComponentChange.add(parseLong(relationship.getSourceId())));
			}
			Set<Long> descriptionIds = new HashSet<>();
			try (final CloseableIterator<ReferenceSetMember> stream =
						 elasticsearchTemplate.stream(componentsReplacedCriteria(versionsReplaced), ReferenceSetMember.class)) {
				stream.forEachRemaining(member -> descriptionIds.add(parseLong(member.getReferencedComponentId())));
			}
			try (final CloseableIterator<Description> stream =
						 elasticsearchTemplate.stream(new NativeSearchQueryBuilder()
								 .withQuery(boolQuery()
										 .must(versionControlHelper.getBranchCriteria(path))
										 .must(termsQuery("descriptionId", descriptionIds)))
								 .withPageable(ComponentService.LARGE_PAGE)
								 .build(), Description.class)) {
				stream.forEachRemaining(description -> conceptsWithComponentChange.add(parseLong(description.getConceptId())));
			}
		}

		// Find new versions of each component type and collect the conceptId they relate to
		final BoolQueryBuilder branchUpdatesCriteria = versionControlHelper.getUpdatesOnBranchDuringRangeCriteria(path, start, end);
		try (final CloseableIterator<Concept> stream =
					 elasticsearchTemplate.stream(newSearchQuery(branchUpdatesCriteria), Concept.class)) {
			stream.forEachRemaining(concept -> {
				final long conceptId = parseLong(concept.getConceptId());
				if (concept.getEnd() == null) {
					conceptsWithNewVersions.add(conceptId);
				} else {
					conceptsWithEndedVersions.add(conceptId);
				}
			});
		}
		try (final CloseableIterator<Description> stream =
					 elasticsearchTemplate.stream(newSearchQuery(branchUpdatesCriteria), Description.class)) {
			stream.forEachRemaining(description -> conceptsWithComponentChange.add(parseLong(description.getConceptId())));
		}
		try (final CloseableIterator<Relationship> stream =
					 elasticsearchTemplate.stream(newSearchQuery(branchUpdatesCriteria), Relationship.class)) {
			stream.forEachRemaining(relationship -> conceptsWithComponentChange.add(parseLong(relationship.getSourceId())));
		}
		Set<Long> descriptionIds = new HashSet<>();
		try (final CloseableIterator<ReferenceSetMember> stream =
					 elasticsearchTemplate.stream(newSearchQuery(branchUpdatesCriteria), ReferenceSetMember.class)) {
			stream.forEachRemaining(member -> descriptionIds.add(parseLong(member.getReferencedComponentId())));
		}
		// Fetch descriptions from any visible branch to get the lang refset's concept id.
		try (final CloseableIterator<Description> stream =
					 elasticsearchTemplate.stream(new NativeSearchQueryBuilder()
				.withQuery(boolQuery()
						.must(versionControlHelper.getBranchCriteria(path))
						.must(termsQuery("descriptionId", descriptionIds)))
				.withPageable(ComponentService.LARGE_PAGE)
				.build(), Description.class)) {
			stream.forEachRemaining(description -> conceptsWithComponentChange.add(parseLong(description.getConceptId())));
		}

		final Sets.SetView<Long> conceptsDeleted = Sets.difference(conceptsWithEndedVersions, conceptsWithNewVersions);
		final Sets.SetView<Long> conceptsCreated = Sets.difference(conceptsWithNewVersions, conceptsWithEndedVersions);
		final Sets.SetView<Long> conceptsModified = Sets.difference(Sets.difference(conceptsWithComponentChange, conceptsCreated), conceptsDeleted);

		return new BranchReviewConceptChanges(null, conceptsCreated, conceptsModified, conceptsDeleted);
	}

	private String getReviewIndexKey(String sourcePath, Long sourceHeadTimestamp, String targetPath, Long headTimestamp) {
		return sourcePath + "@" + sourceHeadTimestamp + "->" + targetPath + "@" + headTimestamp;
	}

	private NativeSearchQuery componentsReplacedCriteria(Set<String> versionsReplaced) {
		return new NativeSearchQueryBuilder()
				.withQuery(boolQuery()
						.must(termsQuery("_id", versionsReplaced)))
				.withPageable(ComponentService.LARGE_PAGE)
				.build();
	}

	private NativeSearchQuery newSearchQuery(BoolQueryBuilder branchUpdatesCriteria) {
		return new NativeSearchQueryBuilder()
					.withQuery(boolQuery().must(branchUpdatesCriteria))
					.withPageable(ComponentService.LARGE_PAGE)
					.build();
	}
}
