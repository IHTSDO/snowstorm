package org.snomed.snowstorm.core.data.services;

import com.google.common.collect.Iterables;
import io.kaicode.elasticvc.api.*;
import io.kaicode.elasticvc.domain.Branch;
import io.kaicode.elasticvc.domain.Commit;
import io.kaicode.elasticvc.domain.DomainEntity;
import io.kaicode.elasticvc.domain.Entity;
import io.kaicode.elasticvc.repositories.BranchRepository;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.data.domain.review.MergeReview;
import org.snomed.snowstorm.core.data.domain.review.ReviewStatus;
import org.snomed.snowstorm.core.data.repositories.*;
import org.snomed.snowstorm.core.data.services.pojo.IntegrityIssueReport;
import org.snomed.snowstorm.rest.pojo.MergeRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHitsIterator;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ExecutorService;
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

	@Autowired
	private BranchRepository branchRepository;

	private BranchReviewService branchReviewService;

	@Autowired
	private ExecutorService executorService;

	private static final String USE_MERGE_REVIEW = "The target branch is diverged, please use the merge review endpoint instead.";
	private static final Logger logger = LoggerFactory.getLogger(BranchMergeService.class);

	public BranchMergeJob mergeBranchAsync(MergeRequest mergeRequest) {
		final String source = mergeRequest.getSource();
		final String target = mergeRequest.getTarget();

		if (codeSystemService.codeSystemExistsOnBranch(source) && codeSystemService.codeSystemExistsOnBranch(target)) {
			throw new IllegalArgumentException("It looks like you are attempting to upgrade a code system. " +
					"Please use the code system upgrade operation for this.");
		}

		MergeReview mergeReview;
		if (mergeRequest.getReviewId() != null) {
			mergeReview = checkMergeReviewCurrent(mergeRequest.getReviewId());

			if (!mergeReview.getSourcePath().equals(source)
					|| !mergeReview.getTargetPath().equals(target)) {
				throw new IllegalArgumentException("The source and target branches of the specified merge review do not match the " +
						"source and target branches of this merge.");
			}
		} else {
			mergeReview = null;
		}

		// Rebase
		if (source.equals(PathUtil.getParentPath(target))) {
			final Branch targetBranch = branchService.findBranchOrThrow(target);
			if (targetBranch.getState() == Branch.BranchState.DIVERGED && mergeReview == null) {
				throw new IllegalArgumentException(USE_MERGE_REVIEW);
			}
		}

		BranchMergeJob mergeJob = new BranchMergeJob(source, target, JobStatus.SCHEDULED);
		branchMergeJobRepository.save(mergeJob);
		mergeJob.setStartDate(new Date());
		mergeJob.setStatus(JobStatus.IN_PROGRESS);
		branchMergeJobRepository.save(mergeJob);
		executorService.submit(() -> {
			try {
				if (mergeReview != null) {
					branchReviewService.applyMergeReview(mergeReview);
				} else {
					mergeBranchSync(source, target, null);
				}
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
			final Map<Class<? extends SnomedComponent>, ElasticsearchRepository> componentTypeRepoMap = domainEntityConfiguration.getComponentTypeRepositoryMap();
			componentTypeRepoMap.entrySet().parallelStream().forEach(entry -> copyChangesOnBranchToCommit(source, commit, entry.getKey(), entry.getValue(), "Migrating", false));
			commit.markSuccessful();
		}
	}

	public void mergeBranchSync(String source, String target, Collection<Concept> manuallyMergedConcepts) throws ServiceException {
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
				throw new IllegalArgumentException(USE_MERGE_REVIEW);
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
							.filter(not(Concept::isDeleted)).collect(Collectors.toSet()), commit, true);
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
				final Map<Class<? extends DomainEntity>, ElasticsearchRepository> componentTypeRepoMap = domainEntityConfiguration.getAllTypeRepositoryMap();
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
		try (SearchHitsIterator<T> stream = elasticsearchTemplate.searchForStream(changesQueryBuilder.build(), componentClass)) {
			stream.forEachRemaining(hit -> componentsChangedOnBranch.add(hit.getContent().getId()));
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
		try (SearchHitsIterator<T> stream = elasticsearchTemplate.searchForStream(parentQueryBuilder.build(), componentClass)) {
			stream.forEachRemaining(hit -> duplicateComponents.add(hit.getContent().getId()));
		}

		if (!duplicateComponents.isEmpty()) {
			// Favor the version of the component which has already been promoted by ending the version on this branch.
			ElasticsearchRepository repository = domainEntityConfiguration.getComponentTypeRepositoryMap().get(componentClass);
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
			fixDuplicateComponents(targetBranch, branchCriteriaIncludingOpenCommit, true, new HashMap<>());
			commit.markSuccessful();
		}
	}

	void fixDuplicateComponents(String branch, BranchCriteria branchCriteria, boolean endThisVersion, Map<Class, Set<String>> fixesApplied) {
		fixDuplicateComponentsOfType(branch, branchCriteria, Concept.class, Concept.Fields.CONCEPT_ID, conceptRepository, endThisVersion, fixesApplied);
		fixDuplicateComponentsOfType(branch, branchCriteria, Description.class, Description.Fields.DESCRIPTION_ID, descriptionRepository, endThisVersion, fixesApplied);
		fixDuplicateComponentsOfType(branch, branchCriteria, Relationship.class, Relationship.Fields.RELATIONSHIP_ID, relationshipRepository, endThisVersion, fixesApplied);
		fixDuplicateComponentsOfType(branch, branchCriteria, ReferenceSetMember.class, ReferenceSetMember.Fields.MEMBER_ID, referenceSetMemberRepository, endThisVersion, fixesApplied);
	}

	private void fixDuplicateComponentsOfType(String branch, BranchCriteria branchCriteria, Class<? extends SnomedComponent> clazz, String idField,
			ElasticsearchRepository repository, boolean endThisVersion, Map<Class, Set<String>> fixesApplied) {

		logger.info("Searching for duplicate {} records on {}", clazz.getSimpleName(), branch);
		BoolQueryBuilder entityBranchCriteria = branchCriteria.getEntityBranchCriteria(clazz);

		// Find components on extension branch
		Set<String> ids = new HashSet<>();
		try (SearchHitsIterator<? extends SnomedComponent> conceptStream = elasticsearchTemplate.searchForStream(new NativeSearchQueryBuilder()
				.withQuery(boolQuery().must(entityBranchCriteria)
						.must(termQuery("path", branch)))
				.withPageable(ComponentService.LARGE_PAGE)
				.withFields(idField).build(), clazz)) {
			conceptStream.forEachRemaining(c -> ids.add(c.getContent().getId()));
		}

		// Find donated components where the extension version is not ended
		Set<String> duplicateIds = new HashSet<>();
		for (List<String> idsBatch : Iterables.partition(ids, 10_000)) {
			try (SearchHitsIterator<? extends SnomedComponent> conceptStream = elasticsearchTemplate.searchForStream(new NativeSearchQueryBuilder()
					.withQuery(boolQuery().must(entityBranchCriteria)
							.mustNot(termQuery("path", branch)))
					.withFilter(termsQuery(idField, idsBatch))
					.withPageable(ComponentService.LARGE_PAGE)
					.withFields(idField).build(), clazz)) {
				conceptStream.forEachRemaining(c -> {
					if(ids.contains(c.getContent().getId())) {
						duplicateIds.add(c.getContent().getId());
					}
				});
			}
		}

		logger.info("Found {} duplicate {} records: {}", duplicateIds.size(), clazz.getSimpleName(), duplicateIds);

		// End duplicate components using the commit timestamp of the donated content
		List<String> internalIdsToHide = new ArrayList<>();
		for (List<String> duplicateIdsBatch : Iterables.partition(duplicateIds, 10_000)) {
			List<? extends SnomedComponent> intVersions = elasticsearchTemplate.search(new NativeSearchQueryBuilder()
					.withQuery(boolQuery().must(entityBranchCriteria)
							.must(termsQuery(idField, duplicateIdsBatch))
							.mustNot(termQuery("path", branch)))
					.withPageable(LARGE_PAGE)
					.build(), clazz)
					.stream().map(SearchHit::getContent)
					.collect(Collectors.toList());

			for (SnomedComponent intVersion : intVersions) {
				if (endThisVersion) {
					Date donatedVersionCommitTimepoint = intVersion.getStart();

					String duplicateId = intVersion.getId();
					List<? extends SnomedComponent> extensionVersionList = elasticsearchTemplate.search(new NativeSearchQueryBuilder()
							.withQuery(boolQuery().must(entityBranchCriteria)
									.must(termQuery(idField, duplicateId))
									.must(termQuery("path", branch)))
							.build(), clazz)
							.stream().map(SearchHit::getContent)
							.collect(Collectors.toList());
					if (extensionVersionList.size() != 1) {
						throw new IllegalStateException(String.format("During fix stage expecting 1 extension version but found %s for id %s", extensionVersionList.size(), clazz));
					}
					SnomedComponent extensionVersion = extensionVersionList.get(0);
					extensionVersion.setEnd(donatedVersionCommitTimepoint);
					repository.save(extensionVersion);
					logger.info("Ended {} on {} at timepoint {} to match {} version start date.", duplicateId, branch, donatedVersionCommitTimepoint, intVersion.getPath());
				} else {
					// Hide parent version
					internalIdsToHide.add(intVersion.getInternalId());
				}
			}
		}

		if (!endThisVersion && !internalIdsToHide.isEmpty()) {
			// Hide parent version using version replaced map on branch
			Branch latestBranch = branchService.findLatest(branch);
			latestBranch.addVersionsReplaced(Collections.singletonMap(clazz.getSimpleName(), new HashSet<>(internalIdsToHide)));
			branchRepository.save(latestBranch);
			logger.info("Updated branch with an additional {} {} versions to hide on ancestor branch.", internalIdsToHide.size(), clazz.getSimpleName());
		}

		fixesApplied.put(clazz, duplicateIds);
	}

	public List<Branch> findChildBranches(String path, boolean immediateChildren, PageRequest pageRequest) {
		// If only immediate children then the path and everything else besides the "/" character (one level only)
		// OR
		// Matches the path and everything else
		String regexp = immediateChildren ? path + "/" + "([^/]*){1}" : path + "/.*";

		NativeSearchQuery build = new NativeSearchQueryBuilder()
				.withQuery(boolQuery()
						.mustNot(existsQuery("end"))
						.must(regexpQuery("path", regexp))
				)
				.withSort(new FieldSortBuilder("path"))
				.withPageable(pageRequest).build();

		return elasticsearchTemplate.search(build, Branch.class).stream().map(SearchHit::getContent).collect(Collectors.toList());
	}

	private <T extends DomainEntity> void promoteEntities(String source, Commit commit, Class<T> entityClass,
			ElasticsearchRepository<T, String> entityRepository, Map<String, Set<String>> versionsReplaced) {

		final String targetPath = commit.getBranch().getPath();

		// End entities on target which have been replaced on source branch
		List<T> toEnd = new ArrayList<>();
		String entityClassName = entityClass.getSimpleName();
		for (List<String> versionsReplacedSegment : Iterables.partition(versionsReplaced.getOrDefault(entityClassName, Collections.emptySet()), 1000)) {
			try (final SearchHitsIterator<T> entitiesToEnd = elasticsearchTemplate.searchForStream(new NativeSearchQueryBuilder()
					.withQuery(boolQuery()
							.must(termQuery("path", targetPath))
							.must(termsQuery("_id", versionsReplacedSegment))
					)
					.withPageable(ConceptService.LARGE_PAGE)
					.build(), entityClass)) {

				entitiesToEnd.forEachRemaining(entity -> {
					if (entity.getContent().getEnd() == null) {
						toEnd.add(entity.getContent());
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

			// Reduce versionsReplaced map by removing those component versions which have now been ended on the parent branch.
			commit.getVersionsReplacedForPromotion().getOrDefault(entityClassName, Collections.emptySet()).removeAll(toEnd.stream().map(Entity::getInternalId).collect(Collectors.toList()));

			logger.debug("Ended {} {}", versionsReplaced.size(), entityClassName);
		}

		copyChangesOnBranchToCommit(source, commit, entityClass, entityRepository, "Promoting", true);
	}

	private <T extends DomainEntity> void copyChangesOnBranchToCommit(String source, Commit commit, Class<T> entityClass,
			ElasticsearchRepository<T, String> entityRepository, String logAction, boolean endEntitiesOnSource) {

		// Load all entities on source
		try (final SearchHitsIterator<T> entities = elasticsearchTemplate.searchForStream(new NativeSearchQueryBuilder()
				.withQuery(versionControlHelper.getChangesOnBranchCriteria(source).getEntityBranchCriteria(entityClass))
				.withPageable(ConceptService.LARGE_PAGE)
				.build(), entityClass)) {

			List<T> toPromote = new ArrayList<>();
			entities.forEachRemaining(hit -> toPromote.add(hit.getContent()));
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

	private MergeReview checkMergeReviewCurrent(String mergeReviewId) {
		MergeReview mergeReview = reviewService.getMergeReviewOrThrow(mergeReviewId);
		if (mergeReview.getStatus() != ReviewStatus.CURRENT) {
			throw new IllegalStateException("Merge review is not in CURRENT status.");
		}
		return mergeReview;
	}

	public void setBranchReviewService(BranchReviewService branchReviewService) {
		this.branchReviewService = branchReviewService;
	}

}
