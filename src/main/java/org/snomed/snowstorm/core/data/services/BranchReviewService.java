package org.snomed.snowstorm.core.data.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.api.VersionControlHelper;
import io.kaicode.elasticvc.domain.Branch;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.data.domain.review.*;
import org.snomed.snowstorm.core.data.repositories.BranchReviewRepository;
import org.snomed.snowstorm.core.data.repositories.ManuallyMergedConceptRepository;
import org.snomed.snowstorm.core.data.repositories.MergeReviewRepository;
import org.snomed.snowstorm.core.pojo.LanguageDialect;
import org.snomed.snowstorm.core.util.TimerUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHitsIterator;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static io.kaicode.elasticvc.api.ComponentService.LARGE_PAGE;
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
	private BranchReviewRepository branchReviewRepository;

	@Autowired
	private MergeReviewRepository mergeReviewRepository;

	@Autowired
	private ManuallyMergedConceptRepository manuallyMergedConceptRepository;

	@Autowired
	private ElasticsearchOperations elasticsearchTemplate;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private ExecutorService executorService;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	@PostConstruct
	public void init() {
		branchMergeService.setBranchReviewService(this);
	}

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

		final SecurityContext securityContext = SecurityContextHolder.getContext();
		executorService.submit(() -> {
			SecurityContextHolder.setContext(securityContext);
			try {
				lookupBranchReviewConceptChanges(sourceToTarget);
				lookupBranchReviewConceptChanges(targetToSource);
				mergeReview.setStatus(ReviewStatus.CURRENT);
				mergeReviewRepository.save(mergeReview);
			} catch (Exception e) {
				mergeReview.setStatus(ReviewStatus.FAILED);
				logger.error("Collecting branch review changes failed.", e);
				mergeReviewRepository.save(mergeReview);
			}
		});
		mergeReviewRepository.save(mergeReview);
		return mergeReview;
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
				if (sourceVersion != null && targetVersion != null) {
					mergeVersion.setAutoMergedConcept(autoMergeConcept(sourceVersion, targetVersion));
					conflicts.put(conceptId, mergeVersion);
				} else {
					persistManualMergeConceptDeletion(mergeReview, conceptId);
					logger.info("Concept {} deleted on both sides of the merge. Excluding from merge review {}.", conceptId, id);
				}
			});
		}
		return conflicts.values();
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
		ObjectReader conceptReader = objectMapper.readerFor(Concept.class);
		ManuallyMergedConcept manuallyMergedConcept = null;
		List<Concept> concepts = new ArrayList<>();
		try {
			for (ManuallyMergedConcept mmc : manuallyMergedConcepts) {
				manuallyMergedConcept = mmc;
				Concept concept;
				if (manuallyMergedConcept.isDeleted()) {
					concept = new Concept(manuallyMergedConcept.getConceptId().toString());
					concept.markDeleted();
				} else {
					concept = conceptReader.readValue(manuallyMergedConcept.getConceptJson());
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

		Set<Long> changedConcepts = createConceptChangeReportOnBranchForTimeRange(source.getPath(), start, source.getHead(), branchReview.isSourceParent());
		branchReview.setStatus(ReviewStatus.CURRENT);
		branchReview.setChangedConcepts(changedConcepts);
		branchReviewRepository.save(branchReview);
	}

	Set<Long> createConceptChangeReportOnBranchForTimeRange(String path, Date start, Date end, boolean sourceIsParent) {

		logger.info("Creating change report: branch {} time range {} ({}) to {} ({})", path, start.getTime(), start, end.getTime(), end);

		List<Branch> startTimeSlice;
		List<Branch> endTimeSlice;
		if (sourceIsParent) {
			// The source branch is the parent, so we are counting content which could be rebased down.
			// This content can come from any ancestor branch.
			startTimeSlice = versionControlHelper.getTimeSlice(path, start);
			endTimeSlice = versionControlHelper.getTimeSlice(path, end);
		} else {
			// The source branch is the child, so we are counting content which could be promoted up.
			// This content will exist on this path only.
			startTimeSlice = Lists.newArrayList(branchService.findAtTimepointOrThrow(path, start));
			endTimeSlice = Lists.newArrayList(branchService.findAtTimepointOrThrow(path, end));
		}

		if (startTimeSlice.equals(endTimeSlice)) {
			return Collections.emptySet();
		}

		// Find components of each type that are on the target branch and have been ended on the source branch
		final Set<Long> changedConcepts = new LongOpenHashSet();
		final Map<Long, Long> referenceComponentIdToConceptMap = new Long2ObjectOpenHashMap<>();
		final Set<Long> preferredDescriptionIds = new LongOpenHashSet();
		Branch branch = branchService.findBranchOrThrow(path);
		logger.debug("Collecting versions replaced for change report: branch {} time range {} to {}", path, start, end);

		Map<String, Set<String>> changedVersionsReplaced = new HashMap<>();

		// Technique: Search for replaced versions
		// Work out changes in versions replaced between time slices
		Map<String, Set<String>> startVersionsReplaced = versionControlHelper.getAllVersionsReplaced(startTimeSlice);
		Map<String, Set<String>> endVersionsReplaced = versionControlHelper.getAllVersionsReplaced(endTimeSlice);
		for (String type : Sets.union(startVersionsReplaced.keySet(), endVersionsReplaced.keySet())) {
			changedVersionsReplaced.put(type, Sets.difference(
					endVersionsReplaced.getOrDefault(type, Collections.emptySet()),
					startVersionsReplaced.getOrDefault(type, Collections.emptySet())));
		}
		if (!changedVersionsReplaced.getOrDefault(Concept.class.getSimpleName(), Collections.emptySet()).isEmpty()) {
			try (final SearchHitsIterator<Concept> stream = elasticsearchTemplate.searchForStream(
					componentsReplacedCriteria(changedVersionsReplaced.get(Concept.class.getSimpleName()), Concept.Fields.CONCEPT_ID).build(), Concept.class)) {
				stream.forEachRemaining(hit -> changedConcepts.add(parseLong(hit.getContent().getConceptId())));
			}
		}
		if (!changedVersionsReplaced.getOrDefault(Description.class.getSimpleName(), Collections.emptySet()).isEmpty()) {
			NativeSearchQueryBuilder fsnQuery = componentsReplacedCriteria(changedVersionsReplaced.get(Description.class.getSimpleName()), Description.Fields.CONCEPT_ID)
					.withFilter(termQuery(Description.Fields.TYPE_ID, Concepts.FSN));
			try (final SearchHitsIterator<Description> stream = elasticsearchTemplate.searchForStream(fsnQuery.build(), Description.class)) {
				stream.forEachRemaining(hit -> changedConcepts.add(parseLong(hit.getContent().getConceptId())));
			}
		}
		if (!changedVersionsReplaced.getOrDefault(Relationship.class.getSimpleName(), Collections.emptySet()).isEmpty()) {
			try (final SearchHitsIterator<Relationship> stream = elasticsearchTemplate.searchForStream(
					componentsReplacedCriteria(changedVersionsReplaced.get(Relationship.class.getSimpleName()), Relationship.Fields.SOURCE_ID).build(), Relationship.class)) {
				stream.forEachRemaining(hit -> changedConcepts.add(parseLong(hit.getContent().getSourceId())));
			}
		}
		if (!changedVersionsReplaced.getOrDefault(ReferenceSetMember.class.getSimpleName(), Collections.emptySet()).isEmpty()) {
			// Refsets with the internal "conceptId" field are related to a concept in terms of authoring
			NativeSearchQueryBuilder refsetQuery = componentsReplacedCriteria(changedVersionsReplaced.get(ReferenceSetMember.class.getSimpleName()),
					ReferenceSetMember.Fields.REFERENCED_COMPONENT_ID, ReferenceSetMember.Fields.CONCEPT_ID)
					.withFilter(boolQuery().must(existsQuery(ReferenceSetMember.Fields.CONCEPT_ID)));
			try (final SearchHitsIterator<ReferenceSetMember> stream = elasticsearchTemplate.searchForStream(refsetQuery.build(), ReferenceSetMember.class)) {
				stream.forEachRemaining(hit -> referenceComponentIdToConceptMap.put(parseLong(hit.getContent().getReferencedComponentId()), parseLong(hit.getContent().getConceptId())));
			}
		}

		// Technique: Search for ended versions
		BoolQueryBuilder updatesDuringRange;
		if (sourceIsParent) {
			updatesDuringRange = versionControlHelper.getUpdatesOnBranchOrAncestorsDuringRangeQuery(path, start, end);
		} else {
			updatesDuringRange = versionControlHelper.getUpdatesOnBranchDuringRangeCriteria(path, start, end);
		}

		// Find new or ended versions of each component type and collect the conceptId they relate to
		logger.debug("Collecting concept changes for change report: branch {} time range {} to {}", path, start, end);

		TimerUtil timerUtil = new TimerUtil("Collecting changes");
		NativeSearchQuery conceptsWithNewVersionsQuery = new NativeSearchQueryBuilder()
				.withQuery(updatesDuringRange)
				.withPageable(LARGE_PAGE)
				.withSort(SortBuilders.fieldSort("start"))
				.withFields(Concept.Fields.CONCEPT_ID)
				.build();
		try (final SearchHitsIterator<Concept> stream = elasticsearchTemplate.searchForStream(conceptsWithNewVersionsQuery, Concept.class)) {
			stream.forEachRemaining(hit -> changedConcepts.add(parseLong(hit.getContent().getConceptId())));
		}

		logger.debug("Collecting description changes for change report: branch {} time range {} to {}", path, start, end);
		AtomicLong descriptions = new AtomicLong();
		NativeSearchQuery descQuery = newSearchQuery(updatesDuringRange)
				.withFilter(termQuery(Description.Fields.TYPE_ID, Concepts.FSN))
				.withFields(Description.Fields.CONCEPT_ID)
				.build();
		try (final SearchHitsIterator<Description> stream = elasticsearchTemplate.searchForStream(descQuery, Description.class)) {
			stream.forEachRemaining(hit -> {
				changedConcepts.add(parseLong(hit.getContent().getConceptId()));
				descriptions.incrementAndGet();
			});
		}
		timerUtil.checkpoint("descriptions " + descriptions.get());

		logger.debug("Collecting relationship changes for change report: branch {} time range {} to {}", path, start, end);
		AtomicLong relationships = new AtomicLong();
		NativeSearchQuery relQuery = newSearchQuery(updatesDuringRange)
				.withFields(Relationship.Fields.SOURCE_ID)
				.build();
		try (final SearchHitsIterator<Relationship> stream = elasticsearchTemplate.searchForStream(relQuery, Relationship.class)) {
			stream.forEachRemaining(hit -> {
				changedConcepts.add(parseLong(hit.getContent().getSourceId()));
				relationships.incrementAndGet();
			});
		}
		timerUtil.checkpoint("relationships " + relationships.get());

		logger.debug("Collecting refset member changes for change report: branch {} time range {} to {}", path, start, end);
		NativeSearchQuery memberQuery = newSearchQuery(updatesDuringRange)
				.withFilter(boolQuery().must(existsQuery(ReferenceSetMember.Fields.CONCEPT_ID)))
				.withFields(ReferenceSetMember.Fields.REFERENCED_COMPONENT_ID, ReferenceSetMember.Fields.CONCEPT_ID)
				.build();
		try (final SearchHitsIterator<ReferenceSetMember> stream = elasticsearchTemplate.searchForStream(memberQuery, ReferenceSetMember.class)) {
			stream.forEachRemaining(hit -> referenceComponentIdToConceptMap.put(parseLong(hit.getContent().getReferencedComponentId()), parseLong(hit.getContent().getConceptId())));
		}

		// Filter out changes for active Synonyms
		// Inactive synonym changes should be included to avoid inactivation indicator / association clashes
		List<Long> synonymAndTextDefIds = new LongArrayList();
		NativeSearchQueryBuilder synonymQuery = new NativeSearchQueryBuilder()
				.withQuery(versionControlHelper.getBranchCriteria(branch).getEntityBranchCriteria(Description.class))
				.withFilter(boolQuery()
						.mustNot(termQuery(Description.Fields.TYPE_ID, Concepts.FSN))
						.must(termsQuery(Description.Fields.DESCRIPTION_ID, referenceComponentIdToConceptMap.keySet()))
						.must(termQuery(Description.Fields.ACTIVE, true)));
		try (final SearchHitsIterator<Description> stream = elasticsearchTemplate.searchForStream(synonymQuery.build(), Description.class)) {
			stream.forEachRemaining(hit -> synonymAndTextDefIds.add(parseLong(hit.getContent().getDescriptionId())));
		}

		// Keep preferred terms if any
		NativeSearchQuery languageMemberQuery = newSearchQuery(updatesDuringRange)
				.withFilter(boolQuery()
						.must(existsQuery(ReferenceSetMember.Fields.CONCEPT_ID))
						.must(termsQuery(ReferenceSetMember.Fields.REFERENCED_COMPONENT_ID, synonymAndTextDefIds))
						.must(termsQuery(ReferenceSetMember.LanguageFields.ACCEPTABILITY_ID_FIELD_PATH, Concepts.PREFERRED)))
				.withFields(ReferenceSetMember.Fields.REFERENCED_COMPONENT_ID)
				.build();
		try (final SearchHitsIterator<ReferenceSetMember> stream = elasticsearchTemplate.searchForStream(languageMemberQuery, ReferenceSetMember.class)) {
			stream.forEachRemaining(hit -> preferredDescriptionIds.add(parseLong(hit.getContent().getReferencedComponentId())));
		}

		Set<Long> changedComponents = referenceComponentIdToConceptMap.keySet()
				.stream()
				.filter(r -> preferredDescriptionIds.contains(r) || !synonymAndTextDefIds.contains(r))
				.collect(Collectors.toSet());

		for (Long componentId : changedComponents) {
			changedConcepts.add(referenceComponentIdToConceptMap.get(componentId));
		}

		logger.info("Change report complete for branch {} time range {} to {}", path, start, end);

		return changedConcepts;
	}

	private NativeSearchQueryBuilder componentsReplacedCriteria(Set<String> versionsReplaced, String... limitFieldsFetched) {
		NativeSearchQueryBuilder builder = new NativeSearchQueryBuilder()
				.withQuery(boolQuery()
						.must(termsQuery("_id", versionsReplaced))
						.mustNot(getNonStatedRelationshipClause()))
				.withPageable(LARGE_PAGE);
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
				.withPageable(LARGE_PAGE);
	}

	private QueryBuilder getNonStatedRelationshipClause() {
		return termsQuery(Relationship.Fields.CHARACTERISTIC_TYPE_ID, Concepts.INFERRED_RELATIONSHIP, Concepts.ADDITIONAL_RELATIONSHIP);
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
