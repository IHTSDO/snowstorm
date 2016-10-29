package com.kaicube.snomed.elasticsnomed.services;

import com.kaicube.elasticversioncontrol.api.BranchService;
import com.kaicube.elasticversioncontrol.api.VersionControlHelper;
import com.kaicube.elasticversioncontrol.domain.Branch;
import com.kaicube.elasticversioncontrol.domain.Commit;
import com.kaicube.elasticversioncontrol.domain.DomainEntity;
import com.kaicube.snomed.elasticsnomed.domain.BranchMergeJob;
import com.kaicube.snomed.elasticsnomed.domain.Concept;
import com.kaicube.snomed.elasticsnomed.domain.JobStatus;
import com.kaicube.snomed.elasticsnomed.domain.SnomedComponent;
import com.kaicube.snomed.elasticsnomed.domain.review.BranchReview;
import com.kaicube.snomed.elasticsnomed.domain.review.ReviewStatus;
import com.kaicube.snomed.elasticsnomed.rest.pojo.MergeRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.core.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.data.elasticsearch.repository.ElasticsearchCrudRepository;
import org.springframework.data.util.CloseableIterator;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class BranchMergeService {

	@Autowired
	private BranchService branchService;

	@Autowired
	private BranchReviewService reviewService;

	@Autowired
	private ConceptService conceptService;

	@Autowired
	private VersionControlHelper versionControlHelper;

	@Autowired
	private ElasticsearchTemplate elasticsearchTemplate;

	private final ExecutorService executorService = Executors.newCachedThreadPool();
	private static final String USE_BRANCH_REVIEW = "The target branch is diverged, please use the branch review endpoint instead.";
	private static final Logger logger = LoggerFactory.getLogger(BranchMergeService.class);

	public BranchMergeJob mergeBranchAsync(MergeRequest mergeRequest) {
		final String source = mergeRequest.getSource();
		final String target = mergeRequest.getTarget();
		BranchReview branchReview = null;
		if (mergeRequest.getReviewId() != null) {
			branchReview = checkBranchReview(mergeRequest, source, target);
		}

		final Branch targetBranch = branchService.findBranchOrThrow(target);
		if (targetBranch.getState() == Branch.BranchState.DIVERGED && branchReview == null) {
			throw new IllegalArgumentException(USE_BRANCH_REVIEW);
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
			} else if (targetBranch.getState() == Branch.BranchState.DIVERGED && manuallyMergedConcepts == null) {
				throw new IllegalArgumentException(USE_BRANCH_REVIEW);
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

			final Map<Class<? extends SnomedComponent>, ElasticsearchCrudRepository> componentTypeRepoMap = conceptService.getComponentTypeRepoMap();
			componentTypeRepoMap.entrySet().parallelStream().forEach(entry ->  promoteEntities(source, commit, entry.getKey(), entry.getValue()));
		}
		branchService.completeCommit(commit);
	}

	private <T extends SnomedComponent> void promoteEntities(String source, Commit commit, Class<T> entityClass, ElasticsearchCrudRepository<T, String> entityRepository) {
		// Load all entities on source
		List<T> toPromote = new ArrayList<>();
		try (final CloseableIterator<T> entities = elasticsearchTemplate.stream(new NativeSearchQueryBuilder()
				.withQuery(versionControlHelper.getChangesOnBranchCriteria(source))
				.withPageable(ConceptService.LARGE_PAGE)
				.build(), entityClass)) {

			entities.forEachRemaining(toPromote::add);
			if (toPromote.isEmpty()) {
				return;
			}
			logger.info("Promoting batch of {} {}.", toPromote.size(), entityClass);

			// End entities on source
			toPromote.forEach(concept -> concept.setEnd(commit.getTimepoint()));
			entityRepository.save(toPromote);

			// Save concept on target
			toPromote.forEach(DomainEntity::markChanged);
			conceptService.doSaveBatchComponents(toPromote, entityClass, commit);
		}
	}

	private BranchReview checkBranchReview(MergeRequest mergeRequest, String sourceBranchPath, String targetBranchPath) {
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
		return branchReview;
	}
}
