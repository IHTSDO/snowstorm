package org.snomed.snowstorm.core.data.services;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.Iterables;
import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.api.VersionControlHelper;
import io.kaicode.elasticvc.domain.Branch;
import io.kaicode.elasticvc.domain.Commit;
import io.kaicode.elasticvc.domain.DomainEntity;
import io.kaicode.elasticvc.domain.Entity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.core.data.domain.BranchMergeJob;
import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.core.data.domain.JobStatus;
import org.snomed.snowstorm.core.data.domain.SnomedComponent;
import org.snomed.snowstorm.core.data.domain.review.BranchReview;
import org.snomed.snowstorm.core.data.domain.review.ReviewStatus;
import org.snomed.snowstorm.core.data.services.pojo.IntegrityIssueReport;
import org.snomed.snowstorm.rest.pojo.MergeRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.data.elasticsearch.repository.ElasticsearchCrudRepository;
import org.springframework.data.util.CloseableIterator;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.elasticsearch.index.query.QueryBuilders.*;

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
	private ElasticsearchOperations elasticsearchTemplate;

	@Autowired
	private DomainEntityConfiguration domainEntityConfiguration;

	@Autowired
	private IntegrityService integrityService;

	// TODO: Move to persistent storage to prepare for autoscaling
	private final Cache<String, BranchMergeJob> branchMergeJobStore = CacheBuilder.newBuilder()
			.expireAfterWrite(12, TimeUnit.HOURS)
			.build();

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

		// TODO: The current production authoring platform currently does not pass the review id through
//		final Branch targetBranch = branchService.findBranchOrThrow(target);
//		if (targetBranch.getState() == Branch.BranchState.DIVERGED && branchReview == null) {
//			throw new IllegalArgumentException(USE_BRANCH_REVIEW);
//		}

		BranchMergeJob mergeJob = new BranchMergeJob(source, target, JobStatus.SCHEDULED);
		executorService.submit(() -> {
			mergeJob.setStartDate(new Date());
			mergeJob.setStatus(JobStatus.IN_PROGRESS);
			try {
				mergeBranchSync(source, target, null);
				mergeJob.setStatus(JobStatus.COMPLETED);
				mergeJob.setEndDate(new Date());
			} catch (Exception e) {
				mergeJob.setStatus(JobStatus.FAILED);
				mergeJob.setMessage(e.getMessage());
				logger.error("Failed to merge branch",e);
			}
		});
		branchMergeJobStore.put(mergeJob.getId(), mergeJob);

		return mergeJob;
	}

	public BranchMergeJob getBranchMergeJobOrThrow(String id) {
		BranchMergeJob mergeJob = branchMergeJobStore.getIfPresent(id);
		if (mergeJob == null) {
			throw new NotFoundException("Branch merge job not found.");
		}
		return mergeJob;
	}

	public void mergeBranchSync(String source, String target, Collection<Concept> manuallyMergedConcepts) throws ServiceException {
		mergeBranchSync(source, target, manuallyMergedConcepts, false);
	}

	public void mergeBranchSync(String source, String target, Collection<Concept> manuallyMergedConcepts, boolean permissive) throws ServiceException {
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
			if (sourceBranch.getHeadTimestamp() == targetBranch.getBaseTimestamp() && !permissive) {
				throw new IllegalStateException("This rebase is not meaningful, the child branch already has the parent's changes.");
			} else if (targetBranch.getState() == Branch.BranchState.DIVERGED && manuallyMergedConcepts == null && !permissive) {
//				throw new IllegalArgumentException(USE_BRANCH_REVIEW); // TODO: The current production authoring platform currently does not pass the review id through
			}
		} else {
			// Promotion
			if (!sourceBranch.isContainsContent() && !permissive) {
				throw new IllegalStateException("This promotion is not meaningful, the child branch does not have any unpromoted changes.");
			}
			if (sourceBranch.getBaseTimestamp() != targetBranch.getHeadTimestamp() && !permissive) {
				throw new IllegalStateException("Child branch must be rebased before promoted.");
			}
		}

		// TODO: Lock both branches
		if (rebase) {
			// Rebase
			logger.info("Performing rebase {} -> {}", source, target);
			try (Commit commit = branchService.openRebaseCommit(targetBranch.getPath())) {
				if (manuallyMergedConcepts != null && !manuallyMergedConcepts.isEmpty()) {
					conceptService.updateWithinCommit(manuallyMergedConcepts, commit);
				}
				commit.markSuccessful();
			}
		} else {
			// Promotion

			logger.info("Integrity check before promotion of {}", source);
			IntegrityIssueReport issueReport = integrityService.findChangedComponentsWithBadIntegrity(sourceBranch);
			if (!issueReport.isEmpty()) {
				logger.error("Aborting promotion of {}. Integrity issues found: {}", source, issueReport);
				throw new ServiceException("Aborting promotion of " + source + ". Integrity issues found.");
			}

			logger.info("Performing promotion {} -> {}", source, target);
			try (Commit commit = branchService.openPromotionCommit(targetBranch.getPath(), source)) {
				final Set<String> versionsReplaced = sourceBranch.getVersionsReplaced();
				final Map<Class<? extends SnomedComponent>, ElasticsearchCrudRepository> componentTypeRepoMap = domainEntityConfiguration.getComponentTypeRepositoryMap();
				componentTypeRepoMap.entrySet().parallelStream().forEach(entry -> promoteEntities(source, commit, entry.getKey(), entry.getValue(), versionsReplaced));
				commit.markSuccessful();
			}
		}
	}

	private <T extends SnomedComponent> void promoteEntities(String source, Commit commit, Class<T> entityClass,
			ElasticsearchCrudRepository<T, String> entityRepository, Set<String> versionsReplaced) {

		final String targetPath = commit.getBranch().getPath();

		// End entities on target which have been replaced on source branch
		List<T> toEnd = new ArrayList<>();
		for (List<String> versionsReplacedSegment : Iterables.partition(versionsReplaced, 1)) {
			try (final CloseableIterator<T> entitiesToEnd = elasticsearchTemplate.stream(new NativeSearchQueryBuilder()
					.withQuery(boolQuery()
							.must(termQuery("path", targetPath))
							.must(termsQuery("_id", versionsReplacedSegment))
					)
					.withPageable(ConceptService.LARGE_PAGE)
					.build(), entityClass)) {

				entitiesToEnd.forEachRemaining(entity -> {
					if (entity.getEnd() == null) {
						toEnd.add(entity);
					}
				});
			}
		}
		if (!toEnd.isEmpty()) {
			// End entities on target
			toEnd.forEach(entity -> entity.setEnd(commit.getTimepoint()));
			entityRepository.saveAll(toEnd);

			commit.getEntityVersionsReplaced().removeAll(toEnd.stream().map(Entity::getInternalId).collect(Collectors.toList()));

			logger.debug("Ended {} {}", versionsReplaced.size(), entityClass.getSimpleName());
		}

		// Load all entities on source
		try (final CloseableIterator<T> entities = elasticsearchTemplate.stream(new NativeSearchQueryBuilder()
				.withQuery(versionControlHelper.getChangesOnBranchCriteria(source))
				.withPageable(ConceptService.LARGE_PAGE)
				.build(), entityClass)) {

			List<T> toPromote = new ArrayList<>();
			entities.forEachRemaining(toPromote::add);
			if (toPromote.isEmpty()) {
				return;
			}
			logger.info("Promoting {} {}", toPromote.size(), entityClass.getSimpleName());

			// End entities on source
			toPromote.forEach(entity -> entity.setEnd(commit.getTimepoint()));
			entityRepository.saveAll(toPromote);

			// Save entities on target
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
