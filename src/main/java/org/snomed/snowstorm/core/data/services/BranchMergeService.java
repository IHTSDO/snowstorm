package org.snomed.snowstorm.core.data.services;

import com.google.common.collect.Iterables;
import io.kaicode.elasticvc.api.*;
import io.kaicode.elasticvc.domain.Branch;
import io.kaicode.elasticvc.domain.Commit;
import io.kaicode.elasticvc.domain.DomainEntity;
import io.kaicode.elasticvc.domain.Entity;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.data.domain.review.BranchReview;
import org.snomed.snowstorm.core.data.domain.review.ReviewStatus;
import org.snomed.snowstorm.core.data.repositories.*;
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
import java.util.stream.Collectors;

import static io.kaicode.elasticvc.api.VersionControlHelper.LARGE_PAGE;
import static org.elasticsearch.index.query.QueryBuilders.*;
import static org.snomed.snowstorm.core.util.PredicateUtil.not;

@Service
public class BranchMergeService {

	@Autowired
	private BranchService branchService;

	@Autowired
	private BranchMetadataHelper branchMetadataHelper;

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
	private BranchMergeJobRepository branchMergeJobRepository;

	@Autowired
	private IntegrityService integrityService;

	@Autowired
	private CodeSystemService codeSystemService;

	@Autowired
	private ConceptRepository conceptRepository;

	@Autowired
	private DescriptionRepository descriptionRepository;

	@Autowired
	private RelationshipRepository relationshipRepository;

	@Autowired
	private ReferenceSetMemberRepository referenceSetMemberRepository;

	private final ExecutorService executorService = Executors.newCachedThreadPool();
	private static final String USE_BRANCH_REVIEW = "The target branch is diverged, please use the branch review endpoint instead.";
	private static final Logger logger = LoggerFactory.getLogger(BranchMergeService.class);

	public BranchMergeJob mergeBranchAsync(MergeRequest mergeRequest) {
		final String source = mergeRequest.getSource();
		final String target = mergeRequest.getTarget();

		if (codeSystemService.codeSystemExistsOnBranch(target)) {
			throw new IllegalArgumentException("It looks like you are attempting to upgrade a code system. " +
					"Please use the code system upgrade operation for this.");
		}

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
		branchMergeJobRepository.save(mergeJob);
		mergeJob.setStartDate(new Date());
		mergeJob.setStatus(JobStatus.IN_PROGRESS);
		branchMergeJobRepository.save(mergeJob);
		executorService.submit(() -> {
			try {
				mergeBranchSync(source, target, null);
				mergeJob.setStatus(JobStatus.COMPLETED);
				mergeJob.setEndDate(new Date());
				branchMergeJobRepository.save(mergeJob);
			} catch (IntegrityException e) {
				mergeJob.setStatus(JobStatus.CONFLICTS);
				mergeJob.setMessage(e.getMessage());
				mergeJob.setApiError(ApiErrorFactory.createErrorForMergeConflicts(e.getMessage(), e.getIntegrityIssueReport()));
				branchMergeJobRepository.save(mergeJob);
			} catch (Exception e) {
				mergeJob.setStatus(JobStatus.FAILED);
				mergeJob.setMessage(e.getMessage());
				branchMergeJobRepository.save(mergeJob);
				logger.error("Failed to merge branch",e);
			}
		});

		return mergeJob;
	}

	public BranchMergeJob getBranchMergeJobOrThrow(String id) {
		return branchMergeJobRepository.findById(id).orElseThrow(() -> new NotFoundException("Branch merge job not found."));
	}

	public void mergeBranchSync(String source, String target, Collection<Concept> manuallyMergedConcepts) throws ServiceException {
		mergeBranchSync(source, target, manuallyMergedConcepts, false);
	}

	/**
	 * Merge content from one branch to another without one being a parent of the other.
	 * This should probably only be used for code system upgrades/downgrades.
	 * @param source The branch to copy content from.
	 * @param target The branch to copy content to.
	 */
	void copyBranchToNewParent(String source, String target) {

		if (!branchService.exists(target)) {
			branchService.create(target);
		}

		try (Commit commit = branchService.openCommit(target, branchMetadataHelper.getBranchLockMetadata("Copying changes from " + source))) {
			logger.info("Performing migration {} -> {}", source, target);
			final Map<Class<? extends SnomedComponent>, ElasticsearchCrudRepository> componentTypeRepoMap = domainEntityConfiguration.getComponentTypeRepositoryMap();
			componentTypeRepoMap.entrySet().parallelStream().forEach(entry -> copyChangesOnBranchToCommit(source, commit, entry.getKey(), entry.getValue(), "Migrating", false));
			commit.markSuccessful();
		}
	}

	void mergeBranchSync(String source, String target, Collection<Concept> manuallyMergedConcepts, boolean permissive) throws ServiceException {
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

		if (rebase) {
			// Rebase
			logger.info("Performing rebase {} -> {}", source, target);
			// This just locks the target branch.
			// Content will be taken from the latest complete commit on the source branch.
			try (Commit commit = branchService.openRebaseCommit(targetBranch.getPath(), branchMetadataHelper.getBranchLockMetadata("Rebasing changes from " + source))) {
				if (manuallyMergedConcepts != null && !manuallyMergedConcepts.isEmpty()) {

					Set<String> conceptsToDelete = manuallyMergedConcepts.stream()
							.filter(Concept::isDeleted).map(Concept::getConceptId).collect(Collectors.toSet());
					if (!conceptsToDelete.isEmpty()) {
						Set<String> conceptsToDeleteWhichExistOnBranch = conceptService.findConceptMinis(commit.getBranch().getPath(), conceptsToDelete, null)
								.getResultsMap().values().stream().map(ConceptMini::getConceptId).collect(Collectors.toSet());
						conceptService.deleteConceptsAndComponentsWithinCommit(conceptsToDeleteWhichExistOnBranch, commit, false);
					}

					// Save merged version of manually merged concepts
					// This has the effect of ending both visible versions of these components which prevents us seeing duplicates on the branch
					conceptService.updateWithinCommit(manuallyMergedConcepts.stream()
							.filter(not(Concept::isDeleted)).collect(Collectors.toSet()), commit);
				}

				// Find and resolve duplicate component versions.
				// All components which would not trigger a merge-review should be included here.
				// - inferred relationships
				// - synonym descriptions
				// - non-concept refset members
				// (Semantic index entries on this branch will be cleared and rebuilt so no need to include those).
				BranchCriteria changesOnBranchIncludingOpenCommit = versionControlHelper.getChangesOnBranchIncludingOpenCommit(commit);
				BranchCriteria branchCriteriaIncludingOpenCommit = versionControlHelper.getBranchCriteriaIncludingOpenCommit(commit);
				// Merge inferred relationships
				removeRebaseDuplicateVersions(Relationship.class, boolQuery().must(termQuery(Relationship.Fields.CHARACTERISTIC_TYPE_ID, Concepts.INFERRED_RELATIONSHIP)),
						changesOnBranchIncludingOpenCommit, branchCriteriaIncludingOpenCommit, commit);
				// Merge descriptions (all types to be safe)
				removeRebaseDuplicateVersions(Description.class, boolQuery(), changesOnBranchIncludingOpenCommit, branchCriteriaIncludingOpenCommit, commit);
				// Merge non-concept reference set members
				removeRebaseDuplicateVersions(ReferenceSetMember.class, boolQuery().mustNot(existsQuery(ReferenceSetMember.Fields.CONCEPT_ID)), changesOnBranchIncludingOpenCommit, branchCriteriaIncludingOpenCommit, commit);

				commit.markSuccessful();
			}
		} else {
			// Promotion
			// Locks both branches until exiting this try block closes the commit
			try (Commit commit = branchService.openPromotionCommit(targetBranch.getPath(), source,
					branchMetadataHelper.getBranchLockMetadata("Promoting changes to " + targetBranch.getPath()),
					branchMetadataHelper.getBranchLockMetadata("Receiving promotion from " + source))) {

				logger.info("Integrity check before promotion of {}", source);
				IntegrityIssueReport issueReport = integrityService.findChangedComponentsWithBadIntegrity(sourceBranch);
				if (!issueReport.isEmpty()) {
					logger.error("Aborting promotion of {}. Integrity issues found: {}", source, issueReport);
					throw new IntegrityException("Aborting promotion of " + source + ". Integrity issues found.", issueReport);
					// Throwing an exception before marking the commit as successful automatically rolls back the commit
				}

				logger.info("Performing promotion {} -> {}", source, target);
				final Map<String, Set<String>> versionsReplaced = sourceBranch.getVersionsReplaced();
				final Map<Class<? extends SnomedComponent>, ElasticsearchCrudRepository> componentTypeRepoMap = domainEntityConfiguration.getComponentTypeRepositoryMap();
				componentTypeRepoMap.entrySet().parallelStream().forEach(entry -> promoteEntities(source, commit, entry.getKey(), entry.getValue(), versionsReplaced));
				commit.markSuccessful();
			}
		}
	}

	private <T extends SnomedComponent> void removeRebaseDuplicateVersions(Class<T> componentClass, QueryBuilder clause,
			BranchCriteria changesOnBranchCriteria, BranchCriteria branchCriteriaIncludingOpenCommit, Commit commit) throws ServiceException {

		String idField;
		try {
			idField = componentClass.newInstance().getIdField();
		} catch (InstantiationException | IllegalAccessException e) {
			throw new ServiceException("Failed to resolve id field of snomed component.", e);
		}

		// Gather components changed on branch
		String path = commit.getBranch().getPath();
		Set<String> componentsChangedOnBranch = new HashSet<>();
		NativeSearchQueryBuilder changesQueryBuilder = new NativeSearchQueryBuilder().withQuery(
				changesOnBranchCriteria.getEntityBranchCriteria(componentClass)
						.must(clause))
				.withFields(idField)
				.withPageable(LARGE_PAGE);
		try (CloseableIterator<T> stream = elasticsearchTemplate.stream(changesQueryBuilder.build(), componentClass)) {
			stream.forEachRemaining(component -> {
				componentsChangedOnBranch.add(component.getId());
			});
		}

		if (componentsChangedOnBranch.isEmpty()) {
			return;
		}

		// Find duplicate versions brought in by rebase
		Set<String> duplicateComponents = new HashSet<>();
		NativeSearchQueryBuilder parentQueryBuilder = new NativeSearchQueryBuilder().withQuery(
				branchCriteriaIncludingOpenCommit.getEntityBranchCriteria(componentClass)
						.must(clause)
						// Version must come from an ancestor branch
						.mustNot(termQuery("path", path)))
				.withFilter(termsQuery(idField, componentsChangedOnBranch))
				.withFields(idField)
				.withPageable(LARGE_PAGE);
		try (CloseableIterator<T> stream = elasticsearchTemplate.stream(parentQueryBuilder.build(), componentClass)) {
			stream.forEachRemaining(component -> {
				duplicateComponents.add(component.getId());
			});
		}

		if (!duplicateComponents.isEmpty()) {
			// Favor the version of the component which has already been promoted by ending the version on this branch.
			ElasticsearchCrudRepository repository = domainEntityConfiguration.getComponentTypeRepositoryMap().get(componentClass);
			logger.info("Taking parent version of {} {}s on {}", duplicateComponents.size(), componentClass.getSimpleName(), path);
			versionControlHelper.endOldVersionsOnThisBranch(componentClass, duplicateComponents, idField, clause, commit, repository);
		}
	}

	void rebaseToSpecificTimepointAndRemoveDuplicateContent(String sourceBranch, Date sourceTimepoint, String targetBranch, String targetLockMessage) {
		if (!sourceBranch.equals(PathUtil.getParentPath(targetBranch))) {
			throw new IllegalArgumentException("Source branch must be direct parent of the target for this operation.");
		}
		if (sourceTimepoint == null) {
			throw new IllegalArgumentException("No source timepoint given for rebase operation.");
		}
		try (Commit commit = branchService.openRebaseToSpecificParentTimepointCommit(targetBranch, sourceTimepoint, branchMetadataHelper.getBranchLockMetadata(targetLockMessage))) {
			BranchCriteria branchCriteriaIncludingOpenCommit = versionControlHelper.getBranchCriteriaIncludingOpenCommit(commit);
			findAndEndDonatedComponentsOfAllTypes(targetBranch, branchCriteriaIncludingOpenCommit, new HashMap<>());
		}
	}

	void findAndEndDonatedComponentsOfAllTypes(String branch, BranchCriteria branchCriteria, Map<Class, Set<String>> fixesApplied) {
		findAndEndDonatedComponents(branch, branchCriteria, Concept.class, Concept.Fields.CONCEPT_ID, conceptRepository, fixesApplied);
		findAndEndDonatedComponents(branch, branchCriteria, Description.class, Description.Fields.DESCRIPTION_ID, descriptionRepository, fixesApplied);
		findAndEndDonatedComponents(branch, branchCriteria, Relationship.class, Relationship.Fields.RELATIONSHIP_ID, relationshipRepository, fixesApplied);
		findAndEndDonatedComponents(branch, branchCriteria, ReferenceSetMember.class, ReferenceSetMember.Fields.MEMBER_ID, referenceSetMemberRepository, fixesApplied);
	}

	private void findAndEndDonatedComponents(String branch, BranchCriteria branchCriteria, Class<? extends SnomedComponent> clazz, String idField, ElasticsearchCrudRepository repository, Map<Class, Set<String>> fixesApplied) {
		logger.info("Searching for duplicate {} records on {}", clazz.getSimpleName(), branch);
		BoolQueryBuilder entityBranchCriteria = branchCriteria.getEntityBranchCriteria(clazz);

		// Find components on extension branch
		Set<String> ids = new HashSet<>();
		try (CloseableIterator<? extends SnomedComponent> conceptStream = elasticsearchTemplate.stream(new NativeSearchQueryBuilder()
				.withQuery(boolQuery().must(entityBranchCriteria)
						.must(termQuery("path", branch)))
				.withPageable(ComponentService.LARGE_PAGE)
				.withFields(idField).build(), clazz)) {
			conceptStream.forEachRemaining(c -> ids.add(c.getId()));
		}

		// Find donated components where the extension version is not ended
		Set<String> duplicateIds = new HashSet<>();
		try (CloseableIterator<? extends SnomedComponent> conceptStream = elasticsearchTemplate.stream(new NativeSearchQueryBuilder()
				.withQuery(boolQuery().must(entityBranchCriteria)
						.mustNot(termQuery("path", branch)))
				.withFilter(termsQuery(idField, ids))
				.withPageable(ComponentService.LARGE_PAGE)
				.withFields(idField).build(), clazz)) {
			conceptStream.forEachRemaining(c -> {
				if(ids.contains(c.getId())) {
					duplicateIds.add(c.getId());
				}
			});
		}

		logger.info("Found {} duplicate {} records: {}", duplicateIds.size(), clazz.getSimpleName(), duplicateIds);

		// End duplicate components using the commit timestamp of the donated content
		for (String duplicateId : duplicateIds) {
			List<? extends SnomedComponent> intVersionList = elasticsearchTemplate.queryForList(new NativeSearchQueryBuilder()
					.withQuery(boolQuery().must(entityBranchCriteria)
							.must(termQuery(idField, duplicateId))
							.mustNot(termQuery("path", branch)))
					.build(), clazz);
			if (intVersionList.size() != 1) {
				throw new IllegalStateException(String.format("During fix stage expecting 1 int version but found %s for id %s", intVersionList.size(), clazz));
			}
			SnomedComponent intVersion = intVersionList.get(0);
			Date donatedVersionCommitTimepoint = intVersion.getStart();

			List<? extends SnomedComponent> extensionVersionList = elasticsearchTemplate.queryForList(new NativeSearchQueryBuilder()
					.withQuery(boolQuery().must(entityBranchCriteria)
							.must(termQuery(idField, duplicateId))
							.must(termQuery("path", branch)))
					.build(), clazz);
			if (extensionVersionList.size() != 1) {
				throw new IllegalStateException(String.format("During fix stage expecting 1 extension version but found %s for id %s", extensionVersionList.size(), clazz));
			}
			SnomedComponent extensionVersion = extensionVersionList.get(0);
			extensionVersion.setEnd(donatedVersionCommitTimepoint);
			repository.save(extensionVersion);
			logger.info("Ended {} on {} at timepoint {} to match {} version start date.", duplicateId, branch, donatedVersionCommitTimepoint, intVersion.getPath());

			fixesApplied.put(clazz, duplicateIds);
		}
	}


	private <T extends SnomedComponent> void promoteEntities(String source, Commit commit, Class<T> entityClass,
			ElasticsearchCrudRepository<T, String> entityRepository, Map<String, Set<String>> versionsReplaced) {

		final String targetPath = commit.getBranch().getPath();

		// End entities on target which have been replaced on source branch
		List<T> toEnd = new ArrayList<>();
		String entityClassName = entityClass.getSimpleName();
		for (List<String> versionsReplacedSegment : Iterables.partition(versionsReplaced.getOrDefault(entityClassName, Collections.emptySet()), 1000)) {
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
			for (List<T> saveSegment : Iterables.partition(toEnd, conceptService.getSaveBatchSize())) {
				entityRepository.saveAll(saveSegment);
			}

			commit.getEntityVersionsReplaced().getOrDefault(entityClassName, Collections.emptySet()).removeAll(toEnd.stream().map(Entity::getInternalId).collect(Collectors.toList()));

			logger.debug("Ended {} {}", versionsReplaced.size(), entityClassName);
		}

		copyChangesOnBranchToCommit(source, commit, entityClass, entityRepository, "Promoting", true);
	}

	private <T extends SnomedComponent> void copyChangesOnBranchToCommit(String source, Commit commit, Class<T> entityClass,
			ElasticsearchCrudRepository<T, String> entityRepository, String logAction, boolean endEntitiesOnSource) {

		// Load all entities on source
		try (final CloseableIterator<T> entities = elasticsearchTemplate.stream(new NativeSearchQueryBuilder()
				.withQuery(versionControlHelper.getChangesOnBranchCriteria(source).getEntityBranchCriteria(entityClass))
				.withPageable(ConceptService.LARGE_PAGE)
				.build(), entityClass)) {

			List<T> toPromote = new ArrayList<>();
			entities.forEachRemaining(toPromote::add);
			if (toPromote.isEmpty()) {
				return;
			}
			logger.info(logAction + " {} {}", toPromote.size(), entityClass.getSimpleName());

			if (endEntitiesOnSource) {
				// End entities on source
				toPromote.forEach(entity -> entity.setEnd(commit.getTimepoint()));
				for (List<T> saveSegment : Iterables.partition(toPromote, conceptService.getSaveBatchSize())) {
					entityRepository.saveAll(saveSegment);
				}
			}

			// Save entities on target
			toPromote.forEach(DomainEntity::markChanged);
			conceptService.doSaveBatchComponents(toPromote, entityClass, commit);
		}
	}

	private BranchReview checkBranchReview(MergeRequest mergeRequest, String sourceBranchPath, String targetBranchPath) {
		BranchReview branchReview = reviewService.getBranchReviewOrThrow(mergeRequest.getReviewId());
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
