package com.kaicube.snomed.elasticsnomed.services;

import com.kaicube.elasticversioncontrol.api.BranchService;
import com.kaicube.elasticversioncontrol.api.VersionControlHelper;
import com.kaicube.elasticversioncontrol.domain.Branch;
import com.kaicube.elasticversioncontrol.domain.Commit;
import com.kaicube.snomed.elasticsnomed.domain.BranchMergeJob;
import com.kaicube.snomed.elasticsnomed.domain.Concept;
import com.kaicube.snomed.elasticsnomed.domain.JobStatus;
import com.kaicube.snomed.elasticsnomed.domain.review.BranchReview;
import com.kaicube.snomed.elasticsnomed.domain.review.ReviewStatus;
import com.kaicube.snomed.elasticsnomed.repositories.ConceptRepository;
import com.kaicube.snomed.elasticsnomed.rest.pojo.MergeRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.data.util.CloseableIterator;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

@Service
public class BranchMergeService {

	@Autowired
	private BranchService branchService;

	@Autowired
	private BranchReviewService reviewService;

	@Autowired
	private ConceptService conceptService;

	@Autowired
	private ConceptRepository conceptRepository;

	@Autowired
	private VersionControlHelper versionControlHelper;

	@Autowired
	private ElasticsearchTemplate elasticsearchTemplate;

	private final ExecutorService executorService = Executors.newCachedThreadPool();

	private static final Logger logger = LoggerFactory.getLogger(BranchMergeService.class);

	public BranchMergeJob mergeBranchAsync(MergeRequest mergeRequest) {
		final String source = mergeRequest.getSource();
		final String target = mergeRequest.getTarget();
		if (mergeRequest.getReviewId() != null) {
			checkBranchReview(mergeRequest, source, target);
		}

		BranchMergeJob mergeJob = new BranchMergeJob(source, target, JobStatus.SCHEDULED);
		executorService.submit(() -> {
			mergeJob.setStartDate(new Date());
			mergeJob.setStatus(JobStatus.IN_PROGRESS);
			mergeBranchSync(source, target, null);
			mergeJob.setStatus(JobStatus.COMPLETED);
			mergeJob.setEndDate(new Date());
		});

		return mergeJob;
	}

	public void mergeBranchSync(String source, String target, Collection<Concept> manuallyMergedConcepts) {
		logger.info("Request merge {} -> {}", source, target);
		final Branch sourceBranch = branchService.findBranchOrThrow(source);
		final Branch targetBranch = branchService.findBranchOrThrow(target);

		// Validate arguments
		if (!branchService.branchesHaveParentChildRelationship(sourceBranch, targetBranch)) {
			throw new IllegalArgumentException("The source or target branch must be the direct parent of the other.");
		}

		// Validate branch states
		final boolean rebase = sourceBranch.isParent(targetBranch);
		if (rebase) {
			// Rebase
			if (sourceBranch.getHeadTimestamp() == targetBranch.getBaseTimestamp()) {
				throw new IllegalStateException("This rebase is not meaningful, the child branch already has the parent's changes.");
			}
		} else {
			// Promotion
			if (!sourceBranch.isContainsContent()) {
				throw new IllegalStateException("This promotion is not meaningful, the child branch does not have any unpromoted changes.");
			}
			if (sourceBranch.getBaseTimestamp() != targetBranch.getHeadTimestamp()) {
				throw new IllegalStateException("Child branch must be rebased before promoted.");
			}
		}

		// TODO: Lock both branches
		// TODO: Use commit rollback in catch block
		final Commit commit = branchService.openCommit(targetBranch.getPath());
		if (rebase) {
			// Rebase
			logger.info("Performing rebase {} -> {}", source, target);
			commit.setCommitType(Commit.CommitType.REBASE);
			if (manuallyMergedConcepts != null && !manuallyMergedConcepts.isEmpty()) {
				conceptService.updateWithinCommit(manuallyMergedConcepts, commit);
			}
		} else {
			// Promotion
			logger.info("Performing promotion {} -> {}", source, target);
			commit.setCommitType(Commit.CommitType.PROMOTION);
			commit.setSourceBranchPath(source);

			// Load all concepts on source
			Map<Long, Concept> conceptsToPromoteMap = new HashMap<>();
			final PageRequest firstThousand = new PageRequest(0, 1000);
			try (final CloseableIterator<Concept> concepts = elasticsearchTemplate.stream(new NativeSearchQueryBuilder()
					.withQuery(versionControlHelper.getChangesOnBranchCriteria(source))
					.withPageable(firstThousand)
					.build(), Concept.class)) {

				concepts.forEachRemaining(concept -> conceptsToPromoteMap.put(concept.getConceptIdAsLong(), concept));
			}
			logger.debug("Found {} concepts to promote.", conceptsToPromoteMap.size());

			if (conceptsToPromoteMap.isEmpty()) {
				return;
			}

			// Find same concepts viewable on target
			List<Concept> conceptsOnTargetToEnd = new ArrayList<>();
			try (final CloseableIterator<Concept> concepts = elasticsearchTemplate.stream(new NativeSearchQueryBuilder()
					.withQuery(boolQuery()
							.must(versionControlHelper.getBranchCriteria(target))
							.must(termsQuery("conceptId", conceptsToPromoteMap.keySet()))
					)
					.withPageable(firstThousand)
					.build(), Concept.class)) {

				concepts.forEachRemaining(concept -> {
					// If concept exists on target then end
					// else add to target versionsReplaced
					if (concept.getFatPath().equals(target)) {
						concept.setEnd(commit.getTimepoint());
						concept.clearInternalId();
						conceptsOnTargetToEnd.add(concept);
					} else {
						commit.addVersionReplaced(concept.getInternalId());
					}
				});
			}
			if (!conceptsOnTargetToEnd.isEmpty()) {
				conceptRepository.save(conceptsOnTargetToEnd);
				conceptsOnTargetToEnd.clear();
			}

			// End concepts on source
			final Collection<Concept> conceptsToPromote = conceptsToPromoteMap.values();
			conceptsToPromote.forEach(concept -> concept.setEnd(commit.getTimepoint()));
			conceptRepository.save(conceptsToPromote);

			// Save new concept versions on target
			conceptsToPromote.forEach(concept -> {
				concept.clearInternalId();
				concept.setPath(target);
				concept.setStart(commit.getTimepoint());
				concept.setEnd(null);
			});
			conceptRepository.save(conceptsToPromote);
		}
		branchService.completeCommit(commit);
	}

	private void checkBranchReview(MergeRequest mergeRequest, String sourceBranchPath, String targetBranchPath) {
		BranchReview branchReview = reviewService.getBranchReview(mergeRequest.getReviewId());
		if (branchReview == null) {
			throw new IllegalArgumentException("Branch review " + mergeRequest.getReviewId() + " does not exist.");
		}
		if (!branchReview.getSource().getPath().equals(sourceBranchPath)
				|| !branchReview.getTarget().getPath().equals(targetBranchPath)) {
			throw new IllegalArgumentException("The source and target branches of the specified branch review do not match the " +
					"source and target branches of this merge.");
		}
		if (!branchReview.getStatus().equals(ReviewStatus.CURRENT)) {
			throw new IllegalStateException("The specified branch review is not in CURRENT status.");
		}
	}
}
