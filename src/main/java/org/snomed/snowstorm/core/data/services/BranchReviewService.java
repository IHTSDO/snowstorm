package org.snomed.snowstorm.core.data.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.google.common.collect.Sets;
import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.api.PathUtil;
import io.kaicode.elasticvc.domain.Branch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.core.data.domain.CodeSystem;
import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.core.data.domain.review.*;
import org.snomed.snowstorm.core.data.repositories.BranchReviewRepository;
import org.snomed.snowstorm.core.data.repositories.ManuallyMergedConceptRepository;
import org.snomed.snowstorm.core.data.repositories.MergeReviewRepository;
import org.snomed.snowstorm.core.pojo.LanguageDialect;
import org.snomed.snowstorm.core.util.TimerUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static io.kaicode.elasticvc.api.ComponentService.LARGE_PAGE;

@Service
public class BranchReviewService {

	@Autowired
	private BranchService branchService;

	@Autowired
	private SBranchService sBranchService;

	@Autowired
	private ConceptService conceptService;

	@Autowired
	private BranchMergeService branchMergeService;

	@Autowired
	private BranchReviewRepository branchReviewRepository;

	@Autowired
	private MergeReviewRepository mergeReviewRepository;

	@Autowired
	private ManuallyMergedConceptRepository manuallyMergedConceptRepository;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private ExecutorService executorService;

	@Autowired
	private AutoMerger autoMerger;

	@Autowired
	private ConceptChangeHelper conceptChangeHelper;

	@Autowired
	private CodeSystemService codeSystemService;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	@PostConstruct
	public void init() {
		branchMergeService.setBranchReviewService(this);
	}

	public MergeReview createMergeReview(String source, String target) {
		TimerUtil timer = new TimerUtil("Create Merge Review");
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

		final SecurityContext securityContext = SecurityContextHolder.getContext();
		executorService.submit(() -> {
			SecurityContextHolder.setContext(securityContext);
			try {
				lookupBranchReviewConceptChanges(sourceToTarget);
				lookupBranchReviewConceptChanges(targetToSource);
				joinContradictoryChanges(sourceToTarget, targetToSource, sourceBranch, targetBranch);
				mergeReview.setStatus(ReviewStatus.CURRENT);
				mergeReviewRepository.save(mergeReview);
			} catch (Exception e) {
				mergeReview.setStatus(ReviewStatus.FAILED);
				logger.error("Collecting branch review changes failed.", e);
				mergeReviewRepository.save(mergeReview);
			}
		});
		mergeReviewRepository.save(mergeReview);
		timer.checkpoint("Merge review created");
		return mergeReview;
	}

	private void joinContradictoryChanges(BranchReview sourceToTarget, BranchReview targetToSource, Branch sourceBranch, Branch targetBranch) {
		Set<Long> contradictoryChanges = conceptChangeHelper.getConceptsWithContradictoryChanges(sourceBranch, targetBranch);
		if (!contradictoryChanges.isEmpty()) {
			sourceToTarget.addChangedConcepts(contradictoryChanges);
			targetToSource.addChangedConcepts(contradictoryChanges);

			branchReviewRepository.save(sourceToTarget);
			branchReviewRepository.save(targetToSource);
		}
	}

	public MergeReview getMergeReview(String id) {
		final MergeReview mergeReview = mergeReviewRepository.findById(id).orElse(null);
		if (mergeReview != null && mergeReview.getStatus() == ReviewStatus.CURRENT) {
			// Only check one branch review - they will both have the same status.
			String sourceToTargetReviewId = mergeReview.getSourceToTargetReviewId();
			BranchReview branchReview = getBranchReview(sourceToTargetReviewId);
			if (branchReview != null) {
				mergeReview.setStatus(branchReview.getStatus());
			} else {
				mergeReview.setStatus(ReviewStatus.FAILED);
				mergeReview.setMessage("Branch merge not found in store. (" + sourceToTargetReviewId + ")");
			}
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

	public Collection<MergeReviewConceptVersions> getMergeReviewConflictingConcepts(String id, List<LanguageDialect> languageDialects) {
		final MergeReview mergeReview = getMergeReviewOrThrow(id);
		assertMergeReviewCurrent(mergeReview);

		final Set<Long> conceptsChangedInBoth = getConflictingConceptIds(mergeReview);
		boolean targetBranchVersionBehind = isTargetBranchVersionBehind(mergeReview);
		final Map<Long, MergeReviewConceptVersions> conflicts = new HashMap<>();
		if (!conceptsChangedInBoth.isEmpty()) {

			final Map<Long, Concept> conceptOnSource = conceptService.find(mergeReview.getSourcePath(), conceptsChangedInBoth, languageDialects)
					.stream().collect(Collectors.toMap(Concept::getConceptIdAsLong, Function.identity()));

			final Map<Long, Concept> conceptOnTarget = conceptService.find(mergeReview.getTargetPath(), conceptsChangedInBoth, languageDialects)
					.stream().collect(Collectors.toMap(Concept::getConceptIdAsLong, Function.identity()));

			conceptsChangedInBoth.forEach(conceptId -> {
				Concept sourceVersion = conceptOnSource.get(conceptId);
				Concept targetVersion = conceptOnTarget.get(conceptId);
				MergeReviewConceptVersions mergeVersion = new MergeReviewConceptVersions(sourceVersion, targetVersion);
				if (sourceVersion == null && targetVersion == null) {
					// Both are deleted, no conflict.
					persistManualMergeConceptDeletion(mergeReview, conceptId);
					logger.info("Concept {} deleted on both sides of the merge. Excluding from merge review {}.", conceptId, id);
				} else if (sourceVersion != null && sourceVersion.isReleased() && targetVersion == null) {
					// Deleted somewhere, whilst simultaneously versioned elsewhere.
					persistManualMergeConceptDeletion(mergeReview, conceptId);
					logger.info("Concept {} versioned somewhere whilst deleted elsewhere. Excluding from merge review {}.", conceptId, id);
				} else {
					if (sourceVersion != null && targetVersion != null) {
						// Neither deleted, auto-merge.
						mergeVersion.setAutoMergedConcept(autoMerger.autoMerge(sourceVersion, targetVersion, mergeReview.getTargetPath()));
						if (sourceVersion.isReleased()) {
							mergeVersion.setTargetConceptVersionBehind(targetBranchVersionBehind);
						}
					}
					conflicts.put(conceptId, mergeVersion);
				}
			});
		}
		return conflicts.values();
	}

	private boolean isTargetBranchVersionBehind(MergeReview mergeReview) {
		String sourcePath = mergeReview.getSourcePath();
		String targetPath = mergeReview.getTargetPath();
		Branch originalSourceBranch = getOriginatingTopLevelBranch(sourcePath);
		Branch originalTargetBranch = getOriginatingTopLevelBranch(targetPath);

		long sourceTopLevelBaseTimestamp = originalSourceBranch.getBaseTimestamp();
		long targetTopLevelBaseTimestamp = originalTargetBranch.getBaseTimestamp();
		if (targetTopLevelBaseTimestamp < sourceTopLevelBaseTimestamp) {
			return true;
		}

		long sourceTopLevelHeadTimestamp = originalSourceBranch.getHeadTimestamp();
		long targetTopLevelHeadTimestamp = originalTargetBranch.getHeadTimestamp();
		if (sourceTopLevelHeadTimestamp == targetTopLevelHeadTimestamp || sourceTopLevelHeadTimestamp < targetTopLevelHeadTimestamp) {
			return false;
		}

		CodeSystem codeSystem = codeSystemService.findClosestCodeSystemUsingAnyBranch(sourcePath, false);
		if (codeSystem == null) {
			return false;
		}

		// If no versions exist between date range, then the target Branch is not a version behind.
		return !codeSystemService.findVersionsByCodeSystemAndBaseTimepointRange(codeSystem, targetTopLevelHeadTimestamp, sourceTopLevelHeadTimestamp).isEmpty();
	}

	private Branch getOriginatingTopLevelBranch(String path) {
		if (isCodeSystemBranch(path)) {
			return branchService.findBranchOrThrow(path);
		}

		Branch originatingParentBranch = null;
		String tmpPath = path;
		Branch tmpBranch = branchService.findLatest(tmpPath);
		boolean findingTopLevelBaseTimestamp = true;
		while (findingTopLevelBaseTimestamp) {
			String parentPath = PathUtil.getParentPath(tmpPath);
			Branch parentBranch = sBranchService.findByPathAndHeadTimepoint(parentPath, tmpBranch.getBase().getTime());

			if (isCodeSystemBranch(parentPath)) {
				findingTopLevelBaseTimestamp = false;
				originatingParentBranch = parentBranch;
			}

			tmpPath = parentPath;
			tmpBranch = parentBranch;
		}

		return originatingParentBranch;
	}

	private boolean isCodeSystemBranch(String path) {
		return path.equals("MAIN") || path.startsWith("SNOMEDCT-", path.lastIndexOf("/") + 1);
	}

	public void applyMergeReview(MergeReview mergeReview) throws ServiceException {
		assertMergeReviewCurrent(mergeReview);

		// Check all conflicts manually merged
		List<ManuallyMergedConcept> manuallyMergedConcepts = manuallyMergedConceptRepository.findByMergeReviewId(mergeReview.getId(), LARGE_PAGE).getContent();
		Set<Long> manuallyMergedConceptIds = manuallyMergedConcepts.stream().map(ManuallyMergedConcept::getConceptId).collect(Collectors.toSet());
		final Set<Long> conflictingConceptIds = getConflictingConceptIds(mergeReview);
		final Set<Long> conflictsRemaining = new HashSet<>(conflictingConceptIds);
		conflictsRemaining.removeAll(manuallyMergedConceptIds);

		if (!conflictsRemaining.isEmpty()) {
			throw new IllegalStateException("Not all conflicting concepts have been resolved. Unresolved: " + conflictsRemaining);
		}
		if (manuallyMergedConceptIds.size() > conflictingConceptIds.size()) {
			throw new IllegalStateException("There are more manually merged concepts than conflicts. Can not proceed.");
		}

		// Perform rebase merge
		ManuallyMergedConcept manuallyMergedConcept = null;
		List<Concept> concepts = new ArrayList<>();
		ObjectReader conceptReader = objectMapper.readerFor(Concept.class);
		try {
			for (ManuallyMergedConcept mmc : manuallyMergedConcepts) {
				manuallyMergedConcept = mmc;
				Concept concept = null;

				String conceptJson = manuallyMergedConcept.getConceptJson();
				if (conceptJson != null) {
					concept = conceptReader.readValue(conceptJson);
				}

				Concept sourceConcept = conceptService.find(String.valueOf(manuallyMergedConcept.getConceptId()), mergeReview.getSourcePath());
				boolean sourceConceptVersioned = sourceConcept != null && sourceConcept.isReleased() && sourceConcept.getReleasedEffectiveTime() != null;
				if (sourceConceptVersioned && concept == null) {
					concept = sourceConcept;
				} else if (sourceConceptVersioned && !concept.isReleased() && concept.getReleasedEffectiveTime() == null) {
					concept = autoMerger.autoMerge(sourceConcept, concept, mergeReview.getTargetPath());
				} else if (manuallyMergedConcept.isDeleted()) {
					concept = new Concept(manuallyMergedConcept.getConceptId().toString());
					concept.markDeleted();
				}

				concepts.add(concept);
			}
		} catch (IOException e) {
			throw new ServiceException("Failed to deserialise manually merged concept from temp store. mergeReview:" + mergeReview.getId() + ", conceptId:" + manuallyMergedConcept.getConceptId(), e);
		}
		branchMergeService.mergeBranchSync(mergeReview.getSourcePath(), mergeReview.getTargetPath(), concepts);
	}

	private void assertMergeReviewCurrent(MergeReview mergeReview) {
		if (mergeReview.getStatus() != ReviewStatus.CURRENT) {
			throw new IllegalStateException("Merge review state is not " + ReviewStatus.CURRENT);
		}
	}

	private Set<Long> getConflictingConceptIds(MergeReview mergeReview) {
		Supplier<NotFoundException> notFoundExceptionSupplier = () -> new NotFoundException("BranchReview not found with id " + mergeReview.getSourceToTargetReviewId());
		final BranchReview sourceToTargetReview = branchReviewRepository.findById(mergeReview.getSourceToTargetReviewId()).orElseThrow(notFoundExceptionSupplier);
		final BranchReview targetToSourceReview = branchReviewRepository.findById(mergeReview.getTargetToSourceReviewId()).orElseThrow(notFoundExceptionSupplier);
		Set<Long> sourceToTargetReviewChanges = sourceToTargetReview.getChangedConcepts();
		Set<Long> targetToSourceReviewChanges = targetToSourceReview.getChangedConcepts();
		return Sets.intersection(sourceToTargetReviewChanges, targetToSourceReviewChanges);
	}

	public BranchReview getCreateReview(String source, String target) {
		final Branch sourceBranch = branchService.findBranchOrThrow(source);
		final Branch targetBranch = branchService.findBranchOrThrow(target);
		BranchReview review = getCreateReview(sourceBranch, targetBranch);

		if (review.getStatus() == ReviewStatus.PENDING) {
			final SecurityContext securityContext = SecurityContextHolder.getContext();
			executorService.submit(() -> {
				SecurityContextHolder.setContext(securityContext);
				try {
					lookupBranchReviewConceptChanges(review);
				} catch (Exception e) {
					logger.error("Branch review failed.", e);
				}
			});
		}

		return review;
	}

	private BranchReview getCreateReview(Branch sourceBranch, Branch targetBranch) {
		BranchReview existingReview = branchReviewRepository.findBySourceAndTargetPathsAndStates(
				sourceBranch.getPath(), sourceBranch.getBase().getTime(), sourceBranch.getHead().getTime(),
				targetBranch.getPath(), targetBranch.getBase().getTime(), targetBranch.getHead().getTime());
		if (existingReview != null) {
			return existingReview;
		}

		// Validate arguments
		if (!branchService.branchesHaveParentChildRelationship(sourceBranch, targetBranch)) {
			throw new IllegalArgumentException("The source or target branch must be the direct parent of the other.");
		}

		// Create review
		final BranchReview branchReview = new BranchReview(UUID.randomUUID().toString(), new Date(), ReviewStatus.PENDING,
				new BranchState(sourceBranch), new BranchState(targetBranch), sourceBranch.isParent(targetBranch));

		return branchReviewRepository.save(branchReview);
	}

	public BranchReview getBranchReview(String reviewId) {
		final BranchReview branchReview = branchReviewRepository.findById(reviewId).orElse(null);

		if (branchReview != null) {
			branchReview.setStatus(
					isBranchStateCurrent(branchReview.getSource())
							&& isBranchStateCurrent(branchReview.getTarget()) ? branchReview.getStatus() : ReviewStatus.STALE);
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

	private boolean isBranchStateCurrent(BranchState branchState) {
		final Branch branch = branchService.findBranchOrThrow(branchState.getPath());
		return branch.getBase().getTime() == branchState.getBaseTimestamp() && branch.getHead().getTime() == branchState.getHeadTimestamp();
	}

	private void lookupBranchReviewConceptChanges(BranchReview branchReview) {
		final Branch source = branchService.findBranchOrThrow(branchReview.getSource().getPath());
		final Branch target = branchService.findBranchOrThrow(branchReview.getTarget().getPath());

		// source = A------
		// target =    \----A/B
		// start = target base

		// target = A--------
		// source =    \--^--A/B
		// start = source lastPromotion or base

		Set<Long> changedConcepts = new HashSet<>();
		changedConcepts.addAll(conceptChangeHelper.getConceptsChangedBetweenTimeRange(source.getPath(), getStart(branchReview, source, target), source.getHead(), branchReview.isSourceParent()));
		branchReview.setStatus(ReviewStatus.CURRENT);
		branchReview.setChangedConcepts(changedConcepts);
		branchReviewRepository.save(branchReview);
	}

	private Date getStart(BranchReview branchReview, Branch source, Branch target) {
		Date start;
		if (branchReview.isSourceParent()) {
			start = target.getBase();
		} else {
			start = source.getLastPromotion();
			if (start == null) {
				start = source.getCreation();
			}
		}

		// Look for changes in the range starting a millisecond after
		start.setTime(start.getTime() + 1);

		return start;
	}

	/**
	 * Persist manually merged concept in a temp store to be applied to the branch when the merge review is applied.
	 */
	@PreAuthorize("hasPermission('AUTHOR', #mergeReview.targetPath)")
	public void persistManuallyMergedConcept(MergeReview mergeReview, Long conceptId, Concept manuallyMergedConcept) throws ServiceException {
		if (!conceptId.equals(manuallyMergedConcept.getConceptIdAsLong())) {
			throw new IllegalArgumentException("conceptId in request path does not match the conceptId in the request body.");
		}
		try {
			String conceptJson = objectMapper.writeValueAsString(manuallyMergedConcept);
			manuallyMergedConceptRepository.save(new ManuallyMergedConcept(mergeReview.getId(), conceptId, conceptJson, false));
		} catch (IOException e) {
			throw new ServiceException("Failed to serialise manually merged concept.", e);
		}
	}

	/**
	 * Persist manually merged concept deletion in a temp store to be applied to the branch when the merge review is applied.
	 */
	@PreAuthorize("hasPermission('AUTHOR', #mergeReview.targetPath)")
	public void persistManualMergeConceptDeletion(MergeReview mergeReview, Long conceptId) {
		manuallyMergedConceptRepository.save(new ManuallyMergedConcept(mergeReview.getId(), conceptId, null, true));
	}
}
