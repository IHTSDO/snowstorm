package org.snomed.snowstorm.core.data.services;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.Sets;
import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.api.ComponentService;
import io.kaicode.elasticvc.api.VersionControlHelper;
import io.kaicode.elasticvc.domain.Branch;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.TermsQueryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.data.domain.review.*;
import org.snomed.snowstorm.core.util.TimerUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.data.util.CloseableIterator;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.lang.Long.parseLong;
import static org.elasticsearch.index.query.QueryBuilders.*;

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
	private ElasticsearchOperations elasticsearchTemplate;

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
		executorService.submit(() -> {
			try {
				getBranchReviewConceptChanges(sourceToTarget.getId());
				getBranchReviewConceptChanges(targetToSource.getId());
				mergeReview.setStatus(ReviewStatus.CURRENT);
			} catch (Exception e) {
				mergeReview.setStatus(ReviewStatus.FAILED);
				logger.error("Collecting branch review changes failed.", e);
			}
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

	public Collection<MergeReviewConceptVersions> getMergeReviewConflictingConcepts(String id, List<String> languageCodes) {
		final MergeReview mergeReview = getMergeReviewOrThrow(id);
		assertMergeReviewCurrent(mergeReview);

		final Set<Long> conceptsChangedInBoth = getConflictingConceptIds(mergeReview);

		final Map<Long, MergeReviewConceptVersions> conflicts = new HashMap<>();
		if (!conceptsChangedInBoth.isEmpty()) {

			final Map<Long, Concept> conceptOnSource = conceptService.find(mergeReview.getSourcePath(), conceptsChangedInBoth, languageCodes)
					.stream().collect(Collectors.toMap(Concept::getConceptIdAsLong, Function.identity()));

			final Map<Long, Concept> conceptOnTarget = conceptService.find(mergeReview.getTargetPath(), conceptsChangedInBoth, languageCodes)
					.stream().collect(Collectors.toMap(Concept::getConceptIdAsLong, Function.identity()));

			conceptsChangedInBoth.forEach(conceptId -> {
				Concept sourceVersion = conceptOnSource.get(conceptId);
				Concept targetVersion = conceptOnTarget.get(conceptId);
				MergeReviewConceptVersions mergeVersion = new MergeReviewConceptVersions(sourceVersion, targetVersion);
				if (sourceVersion != null && targetVersion != null) {
					mergeVersion.setAutoMergedConcept(autoMergeConcept(sourceVersion, targetVersion));
				}
				conflicts.put(conceptId, mergeVersion);
			});
		}
		return conflicts.values();
	}

	public void applyMergeReview(String mergeReviewId) throws ServiceException {
		final MergeReview mergeReview = getMergeReviewOrThrow(mergeReviewId);
		assertMergeReviewCurrent(mergeReview);

		// Check all conflicts manually merged
		final Map<Long, Concept> manuallyMergedConcepts = mergeReview.getManuallyMergedConcepts();
		final Set<Long> conflictingConceptIds = getConflictingConceptIds(mergeReview);
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

	private Set<Long> getConflictingConceptIds(MergeReview mergeReview) {
		final BranchReview sourceToTargetReview = reviewStore.getIfPresent(mergeReview.getSourceToTargetReviewId());
		final BranchReview targetToSourceReview = reviewStore.getIfPresent(mergeReview.getTargetToSourceReviewId());
		BranchReviewConceptChanges sourceToTargetReviewChanges = sourceToTargetReview.getChanges();
		BranchReviewConceptChanges targetToSourceReviewChanges = targetToSourceReview.getChanges();
		return Sets.intersection(
				Sets.union(sourceToTargetReviewChanges.getChangedConcepts(), sourceToTargetReviewChanges.getDeletedConcepts()),
				Sets.union(targetToSourceReviewChanges.getChangedConcepts(), targetToSourceReviewChanges.getDeletedConcepts()));
	}

	private Concept autoMergeConcept(Concept sourceConcept, Concept targetConcept) {
		final Concept mergedConcept = new Concept();

		// In each component favour the source version unless only the target is unpublished
		Concept winningConcept = sourceConcept.getEffectiveTimeI() != null && targetConcept.getEffectiveTimeI() == null ? targetConcept : sourceConcept;

		// Set directly owned values
		mergedConcept.setConceptId(winningConcept.getConceptId());
		mergedConcept.setActive(winningConcept.isActive());
		mergedConcept.setDefinitionStatus(winningConcept.getDefinitionStatus());
		mergedConcept.setEffectiveTimeI(winningConcept.getEffectiveTimeI());
		mergedConcept.setModuleId(winningConcept.getModuleId());

		mergedConcept.setInactivationIndicator(winningConcept.getInactivationIndicator());
		mergedConcept.setAssociationTargets(winningConcept.getAssociationTargets());

		// Merge Descriptions
		mergedConcept.setDescriptions(mergeComponentSets(sourceConcept.getDescriptions(), targetConcept.getDescriptions()));

		// Merge Relationships
		mergedConcept.setRelationships(mergeComponentSets(sourceConcept.getRelationships(), targetConcept.getRelationships()));

		// Merge Axioms
		mergedConcept.setClassAxioms(mergeComponentSets(sourceConcept.getClassAxioms(), targetConcept.getClassAxioms()));
		mergedConcept.setGciAxioms(mergeComponentSets(sourceConcept.getGciAxioms(), targetConcept.getGciAxioms()));

		return mergedConcept;
	}

	private <T extends IdAndEffectiveTimeComponent> Set<T> mergeComponentSets(Set<T> sourceDescriptions, Set<T> targetDescriptions) {
		final Set<T> mergedDescriptions = new HashSet<>(sourceDescriptions);
		for (final T targetDescription : targetDescriptions) {
			if (targetDescription.getEffectiveTimeI() == null) {
				if (mergedDescriptions.contains(targetDescription)) {
					Optional<T> sourceDescription = mergedDescriptions.stream()
							.filter(otherDescription -> otherDescription.getId().equals(targetDescription.getId())).findFirst();
					if (sourceDescription.isPresent() && sourceDescription.get().getEffectiveTimeI() != null) {
						// Only target description is unpublished, replace.
						mergedDescriptions.add(targetDescription);
					}
				} else {
					// Target description is new and not yet promoted to source.
					mergedDescriptions.add(targetDescription);
				}
			}
		}
		return mergedDescriptions;
	}

	public BranchReview getCreateReview(String source, String target) {
		final Branch sourceBranch = branchService.findBranchOrThrow(source);
		final Branch targetBranch = branchService.findBranchOrThrow(target);
		return getCreateReview(sourceBranch, targetBranch);
	}

	public BranchReview getCreateReview(Branch sourceBranch, Branch targetBranch) {
		final String reviewIndexKey = getReviewIndexKey(sourceBranch.getPath(), sourceBranch.getHeadTimestamp(),
				targetBranch.getPath(), targetBranch.getHeadTimestamp());

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
	public BranchReview getBranchReviewOrThrow(String reviewId) {
		final BranchReview branchReview = getBranchReview(reviewId);
		if (branchReview == null) {
			throw new IllegalArgumentException("Branch review " + reviewId + " does not exist.");
		}
		return branchReview;

	}

	public boolean isBranchStateCurrent(BranchState branchState) {
		final Branch branch = branchService.findBranchOrThrow(branchState.getPath());
		return branch.getBase().getTime() == branchState.getBaseTimestamp() && branch.getHead().getTime() == branchState.getHeadTimestamp();
	}

	public BranchReviewConceptChanges getBranchReviewConceptChanges(String reviewId) {
		final BranchReview review = getBranchReviewOrThrow(reviewId);

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

					// source = A------
					// target =    \----A/B
					// start = target base

					// target = A--------
					// source =    \--^--A/B
					// start = source lastPromotion or base

					Date start;
					if (review.isSourceIsParent()) {
						start = target.getBase();
					} else {
						start = source.getLastPromotion();
						if (start == null) {
							start = source.getBase();
						}
					}

					// Look for changes in the range starting a millisecond after
					start.setTime(start.getTime() + 1);

					review.setChanges(createConceptChangeReportOnBranchForTimeRange(source.getPath(), start, source.getHead(), review.isSourceIsParent()));
				}
			}
		}
		return review.getChanges();
	}

	public BranchReviewConceptChanges createConceptChangeReportOnBranchForTimeRange(String path, Date start, Date end, boolean sourceIsParent) {

		logger.info("Creating change report: branch {} time range {} to {}", path, start, end);

		// Find components of each type that are on the target branch and have been ended on the source branch
		final Set<Long> conceptsWithEndedVersions = new LongOpenHashSet();
		final Set<Long> conceptsWithNewVersions = new LongOpenHashSet();
		final Set<Long> conceptsWithComponentChange = new LongOpenHashSet();
		final Map<String, String> referenceComponentIdToConceptMap = new HashMap<>();
		Branch branch = branchService.findBranchOrThrow(path);
		if (!sourceIsParent) {
			logger.debug("Collecting versions replaced for change report: branch {} time range {} to {}", path, start, end);
			// Technique: Iterate child's 'versionsReplaced' set
			try (final CloseableIterator<Concept> stream = elasticsearchTemplate.stream(componentsReplacedCriteria(branch.getVersionsReplaced(Concept.class), Concept.Fields.CONCEPT_ID).build(), Concept.class)) {
				stream.forEachRemaining(concept -> conceptsWithEndedVersions.add(parseLong(concept.getConceptId())));
			}
			NativeSearchQueryBuilder fsnQuery = componentsReplacedCriteria(branch.getVersionsReplaced(Description.class), Description.Fields.CONCEPT_ID)
					.withFilter(boolQuery().must(existsQuery(Description.Fields.TAG)));
			try (final CloseableIterator<Description> stream = elasticsearchTemplate.stream(fsnQuery.build(), Description.class)) {
				stream.forEachRemaining(description -> conceptsWithComponentChange.add(parseLong(description.getConceptId())));
			}
			try (final CloseableIterator<Relationship> stream = elasticsearchTemplate.stream(componentsReplacedCriteria(branch.getVersionsReplaced(Relationship.class), Relationship.Fields.SOURCE_ID).build(), Relationship.class)) {
				stream.forEachRemaining(relationship -> conceptsWithComponentChange.add(parseLong(relationship.getSourceId())));
			}

			// Refsets with the internal "conceptId" field are related to a concept in terms of authoring
			NativeSearchQueryBuilder refsetQuery = componentsReplacedCriteria(branch.getVersionsReplaced(ReferenceSetMember.class),
					ReferenceSetMember.Fields.REFERENCED_COMPONENT_ID, ReferenceSetMember.Fields.CONCEPT_ID)
					.withFilter(boolQuery().must(existsQuery(ReferenceSetMember.Fields.CONCEPT_ID)));
			try (final CloseableIterator<ReferenceSetMember> stream = elasticsearchTemplate.stream(refsetQuery.build(), ReferenceSetMember.class)) {
				stream.forEachRemaining(member -> referenceComponentIdToConceptMap.put(member.getReferencedComponentId(), member.getConceptId()));
			}
		}

		// Find new versions of each component type and collect the conceptId they relate to
		logger.debug("Collecting concept changes for change report: branch {} time range {} to {}", path, start, end);
		final BoolQueryBuilder branchUpdatesCriteria = versionControlHelper.getUpdatesOnBranchDuringRangeCriteria(path, start, end);

		TimerUtil timerUtil = new TimerUtil("Collecting changes");
		NativeSearchQuery conceptsWithNewVersionsQuery = new NativeSearchQueryBuilder()
				.withQuery(boolQuery().must(branchUpdatesCriteria).mustNot(existsQuery("end")))
				.withPageable(ComponentService.LARGE_PAGE)
				.withFields(Concept.Fields.CONCEPT_ID)// This triggers the fast results mapper
				.build();
		AtomicLong concepts = new AtomicLong();
		try (final CloseableIterator<Concept> stream = elasticsearchTemplate.stream(conceptsWithNewVersionsQuery, Concept.class)) {
			stream.forEachRemaining(concept -> {
				final long conceptId = parseLong(concept.getConceptId());
				conceptsWithNewVersions.add(conceptId);
				concepts.incrementAndGet();
			});
		}
		timerUtil.checkpoint("concepts " + concepts.get());

		NativeSearchQuery conceptsWithEndedVersionsQuery = new NativeSearchQueryBuilder()
				.withQuery(boolQuery().must(branchUpdatesCriteria).must(existsQuery("end")))
				.withPageable(ComponentService.LARGE_PAGE)
				.withFields(Concept.Fields.CONCEPT_ID)// This triggers the fast results mapper
				.build();
		AtomicLong conceptsEnded = new AtomicLong();
		try (final CloseableIterator<Concept> stream = elasticsearchTemplate.stream(conceptsWithEndedVersionsQuery, Concept.class)) {
			stream.forEachRemaining(concept -> {
				final long conceptId = parseLong(concept.getConceptId());
				conceptsWithEndedVersions.add(conceptId);
			});
		}
		timerUtil.checkpoint("conceptsEnded " + conceptsEnded.get());

		logger.debug("Collecting description changes for change report: branch {} time range {} to {}", path, start, end);
		AtomicLong descriptions = new AtomicLong();
		NativeSearchQuery descQuery = newSearchQuery(branchUpdatesCriteria)
				.withFilter(boolQuery().must(existsQuery(Description.Fields.TAG)))
				.withFields(Description.Fields.CONCEPT_ID)// This triggers the fast results mapper
				.build();
		try (final CloseableIterator<Description> stream = elasticsearchTemplate.stream(descQuery, Description.class)) {
			stream.forEachRemaining(description -> {
				conceptsWithComponentChange.add(parseLong(description.getConceptId()));
				descriptions.incrementAndGet();
			});
		}
		timerUtil.checkpoint("descriptions " + descriptions.get());

		logger.debug("Collecting relationship changes for change report: branch {} time range {} to {}", path, start, end);
		AtomicLong relationships = new AtomicLong();
		NativeSearchQuery relQuery = newSearchQuery(branchUpdatesCriteria)
				.withFields(Relationship.Fields.SOURCE_ID)// This triggers the fast results mapper
				.build();
		try (final CloseableIterator<Relationship> stream = elasticsearchTemplate.stream(relQuery, Relationship.class)) {
			stream.forEachRemaining(relationship -> {
				conceptsWithComponentChange.add(parseLong(relationship.getSourceId()));
				relationships.incrementAndGet();
			});
		}
		timerUtil.checkpoint("relationships " + relationships.get());

		logger.debug("Collecting refset member changes for change report: branch {} time range {} to {}", path, start, end);
		NativeSearchQuery memberQuery = newSearchQuery(branchUpdatesCriteria)
				.withFilter(boolQuery().must(existsQuery(ReferenceSetMember.Fields.CONCEPT_ID)))
				.withFields(ReferenceSetMember.Fields.REFERENCED_COMPONENT_ID, ReferenceSetMember.Fields.CONCEPT_ID)
				.build();
		try (final CloseableIterator<ReferenceSetMember> stream = elasticsearchTemplate.stream(memberQuery, ReferenceSetMember.class)) {
			stream.forEachRemaining(member -> referenceComponentIdToConceptMap.put(member.getReferencedComponentId(), member.getConceptId()));
		}

		// Filter out changes for Synonyms
		List<String> descriptionIds = new ArrayList<>();
		NativeSearchQueryBuilder synonymQuery = new NativeSearchQueryBuilder()
				.withQuery(versionControlHelper.getBranchCriteria(branch).getEntityBranchCriteria(Description.class))
				.withFilter(boolQuery()
						.mustNot(existsQuery(Description.Fields.TAG))
						.must(termsQuery(Description.Fields.DESCRIPTION_ID, referenceComponentIdToConceptMap.keySet())));
		try (final CloseableIterator<Description> stream = elasticsearchTemplate.stream(synonymQuery.build(), Description.class)) {
			stream.forEachRemaining(description -> descriptionIds.add(description.getDescriptionId()));
		}

		Set<String> changedComponents = referenceComponentIdToConceptMap.keySet()
				.stream()
				.filter(r -> !descriptionIds.contains(r))
				.collect(Collectors.toSet());

		for (String componentId : changedComponents) {
			conceptsWithComponentChange.add(parseLong(referenceComponentIdToConceptMap.get(componentId)));
		}

		final Sets.SetView<Long> conceptsDeleted = Sets.difference(conceptsWithEndedVersions, conceptsWithNewVersions);
		final Sets.SetView<Long> conceptsCreated = Sets.difference(conceptsWithNewVersions, conceptsWithEndedVersions);
		final Sets.SetView<Long> conceptsModified = Sets.difference(Sets.difference(conceptsWithComponentChange, conceptsCreated), conceptsDeleted);

		logger.info("Change report complete for branch {} time range {} to {}", path, start, end);

		return new BranchReviewConceptChanges(null, conceptsCreated, conceptsModified, conceptsDeleted);
	}

	private String getReviewIndexKey(String sourcePath, Long sourceHeadTimestamp, String targetPath, Long headTimestamp) {
		return sourcePath + "@" + sourceHeadTimestamp + "->" + targetPath + "@" + headTimestamp;
	}

	private NativeSearchQueryBuilder componentsReplacedCriteria(Set<String> versionsReplaced, String... limitFieldsFetched) {
		NativeSearchQueryBuilder builder = new NativeSearchQueryBuilder()
				.withQuery(boolQuery()
						.must(termsQuery("_id", versionsReplaced))
						.mustNot(getNonStatedRelationshipClause()))
				.withPageable(ComponentService.LARGE_PAGE);
		if (limitFieldsFetched.length > 0) {
			builder.withFields(limitFieldsFetched);
		}
		return builder;
	}

	private NativeSearchQueryBuilder newSearchQuery(BoolQueryBuilder branchUpdatesCriteria) {
		return new NativeSearchQueryBuilder()
				.withQuery(boolQuery()
						.must(branchUpdatesCriteria)
						.mustNot(getNonStatedRelationshipClause()))
				.withPageable(ComponentService.LARGE_PAGE);
	}

	private TermsQueryBuilder getNonStatedRelationshipClause() {
		return termsQuery(Relationship.Fields.CHARACTERISTIC_TYPE_ID, Concepts.INFERRED_RELATIONSHIP, Concepts.ADDITIONAL_RELATIONSHIP);
	}
}
