package org.snomed.snowstorm.core.data.services;

import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import io.kaicode.elasticvc.api.*;
import io.kaicode.elasticvc.domain.Commit;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.core.data.domain.QueryConcept;
import org.snomed.snowstorm.core.data.domain.ReferenceSetMember;
import org.snomed.snowstorm.core.data.domain.ReferencedConceptsLookup;
import org.snomed.snowstorm.core.data.repositories.ReferencedConceptsLookupRepository;
import org.snomed.snowstorm.core.util.AggregationUtils;
import org.snomed.snowstorm.core.util.SPathUtil;
import org.snomed.snowstorm.ecl.ReferencedConceptsLookupService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.SearchHitsIterator;
import org.springframework.data.elasticsearch.core.query.FetchSourceFilter;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders.bool;
import static io.kaicode.elasticvc.helper.QueryHelper.termQuery;
import static io.kaicode.elasticvc.helper.QueryHelper.termsQuery;
import static java.lang.Long.parseLong;
import static org.snomed.snowstorm.core.data.domain.ReferenceSetMember.Fields.*;
import static org.snomed.snowstorm.core.data.domain.ReferencedConceptsLookup.Type.EXCLUDE;
import static org.snomed.snowstorm.core.data.domain.ReferencedConceptsLookup.Type.INCLUDE;

@Service
public class ReferencedConceptsLookupUpdateService extends ComponentService implements CommitListener  {

    @Value("${ecl.concepts-lookup.refset.ids}")
    private String refsetIdsForLookup;

    @Value("${ecl.concepts-lookup.generation.threshold}")
    private int conceptsLookupGenerationThreshold;

    @Value("${ecl.concepts-lookup.enabled}")
    private boolean conceptsLookupEnabled;
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final VersionControlHelper versionControlHelper;
    private final ElasticsearchOperations elasticsearchOperations;
    private final ReferencedConceptsLookupRepository refsetConceptsLookupRepository;
    private final ReferencedConceptsLookupService refsetConceptsLookupService;
    private final BranchService branchService;
    private final BranchMetadataHelper branchMetadataHelper;

    @Autowired
    public ReferencedConceptsLookupUpdateService(VersionControlHelper versionControlHelper,
                                                 ElasticsearchOperations elasticsearchOperations,
                                                 ReferencedConceptsLookupRepository refsetConceptsLookupRepository,
                                                 ReferencedConceptsLookupService refsetConceptsLookupService,
                                                 BranchService branchService,
                                                 BranchMetadataHelper branchMetadataHelper) {
        this.versionControlHelper = versionControlHelper;
        this.elasticsearchOperations = elasticsearchOperations;
        this.refsetConceptsLookupRepository = refsetConceptsLookupRepository;
        this.refsetConceptsLookupService = refsetConceptsLookupService;
        this.branchService = branchService;
        this.branchMetadataHelper = branchMetadataHelper;
    }

    @Override
    public void preCommitCompletion(Commit commit) throws IllegalStateException {
        if (!conceptsLookupEnabled) {
            return;
        }
        try {
            Commit.CommitType commitType = commit.getCommitType();
            if (commitType == Commit.CommitType.PROMOTION) {
                updateOnPromotion(commit);
            } else if (commitType == Commit.CommitType.CONTENT) {
                rebuildOnSave(commit);
            } else {
                rebuildOnRebase(commit);
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to update refset concepts lookup on commit.", e);
        }
    }

    private void rebuildOnRebase(Commit commit) {
        List<ReferencedConceptsLookup> existingLookups = refsetConceptsLookupService.getConceptsLookups(
                versionControlHelper.getBranchCriteria(commit.getBranch()), false);
        Set<String> existingRefsetIds = existingLookups.stream().map(ReferencedConceptsLookup::getRefsetId).collect(Collectors.toSet());
        logger.debug("Lookups found for refsets {} on branch {}", existingRefsetIds, commit.getBranch().getPath());
        existingRefsetIds.forEach(refsetId -> rebuildConceptsLookup(commit, parseLong(refsetId), false));
    }

    private void updateOnPromotion(Commit commit) {
        // Step 1: Create a map from refsetId to ReferencedConceptsLookup for targetLookups
        // Fetch existing lookups from target branch for the same refset ids
        BranchCriteria sourceBranchOnly = versionControlHelper.getChangesOnBranchCriteria(commit.getSourceBranchPath());
        List<ReferencedConceptsLookup> sourceLookups = refsetConceptsLookupService.getConceptsLookups(sourceBranchOnly);
        Set<Long> refsetIds = sourceLookups.stream().map(lookup -> Long.parseLong(lookup.getRefsetId())).collect(Collectors.toSet());
        BranchCriteria targetBranchOnly = versionControlHelper.getChangesOnBranchCriteria(commit.getBranch());
        List<ReferencedConceptsLookup> targetLookups = refsetConceptsLookupService.getConceptsLookups(targetBranchOnly, refsetIds);
        Map<Long, ReferencedConceptsLookup> targetIncludeLookupMap = constructMapByRefsetId(targetLookups, INCLUDE);
        Map<Long, ReferencedConceptsLookup> targetExcludeLookupMap = constructMapByRefsetId(targetLookups, EXCLUDE);

        // Step 2: Iterate through sourceLookups and update or create targetLookups
        List<ReferencedConceptsLookup> newTargetLookups = new ArrayList<>();
        for (ReferencedConceptsLookup sourceLookup : sourceLookups) {
            newTargetLookups.addAll(createOrUpdateLookups(sourceLookup, targetIncludeLookupMap, targetExcludeLookupMap));
        }
        // Step 5: Save new targetLookups
        newTargetLookups.forEach(target -> {
            target.setPath(commit.getBranch().getPath());
            target.setTotal(target.getConceptIds().size());
            target.setStart(commit.getTimepoint());
        });
        // Step 6: Expire existing lookups and save new ones
        targetLookups.forEach(lookup -> lookup.setEnd(commit.getTimepoint()));
        sourceLookups.forEach(lookup -> lookup.setEnd(commit.getTimepoint()));
        refsetConceptsLookupRepository.saveAll(newTargetLookups);
        refsetConceptsLookupRepository.saveAll(targetLookups);
        refsetConceptsLookupRepository.saveAll(sourceLookups);
    }

    private List<ReferencedConceptsLookup> createOrUpdateLookups(ReferencedConceptsLookup sourceLookup,
                                                                 Map<Long, ReferencedConceptsLookup> targetIncludeLookupMap,
                                                                 Map<Long, ReferencedConceptsLookup> targetExcludeLookupMap) {
        Long refsetId = Long.parseLong(sourceLookup.getRefsetId());
        List<ReferencedConceptsLookup> newTargetLookups = new ArrayList<>();
        ReferencedConceptsLookup targetLookup = clone(targetIncludeLookupMap.get(refsetId));
        if (targetLookup != null) {
            // Step 3: Update existing targetLookup based on sourceLookup
            newTargetLookups.add(targetLookup);
            if (INCLUDE == sourceLookup.getType()) {
                // Add conceptIds from sourceLookup to targetLookup
                targetLookup.getConceptIds().addAll(sourceLookup.getConceptIds());
            } else if (EXCLUDE == sourceLookup.getType()) {
                // Remove conceptIds from targetLookup that are present in sourceLookup
                if (targetLookup.getConceptIds().containsAll(sourceLookup.getConceptIds())) {
                    targetLookup.getConceptIds().removeAll(sourceLookup.getConceptIds());
                } else {
                    // Need to create a EXCLUDE lookup with the difference
                    Set<Long> remaining = new HashSet<>(targetLookup.getConceptIds());
                    remaining.removeAll(sourceLookup.getConceptIds());
                    ReferencedConceptsLookup exclude = new ReferencedConceptsLookup();
                    exclude.setRefsetId(sourceLookup.getRefsetId());
                    if (targetExcludeLookupMap.containsKey(refsetId)) {
                        remaining.addAll(targetExcludeLookupMap.get(refsetId).getConceptIds());
                    }
                    exclude.setConceptIds(remaining);
                    exclude.setType(EXCLUDE);
                    newTargetLookups.add(exclude);
                }
            }
        } else {
            // Step 4: Create a new targetLookup if it doesn't exist
            ReferencedConceptsLookup newLookup = new ReferencedConceptsLookup();
            newLookup.setRefsetId(sourceLookup.getRefsetId());
            newLookup.setType(sourceLookup.getType());
            newLookup.setConceptIds(new HashSet<>(sourceLookup.getConceptIds())); // Copy conceptIds
            newTargetLookups.add(newLookup);
        }
        return newTargetLookups;
    }

    @NotNull
    private static Map<Long, ReferencedConceptsLookup> constructMapByRefsetId(List<ReferencedConceptsLookup> targetLookups, ReferencedConceptsLookup.Type type) {
        return targetLookups.stream()
                .filter(lookup -> lookup.getType().equals(type))
                .collect(Collectors.toMap(
                        lookup -> Long.parseLong(lookup.getRefsetId()),
                        lookup -> lookup
                ));
    }

    private ReferencedConceptsLookup clone(ReferencedConceptsLookup sourceLookup) {
        if (sourceLookup != null) {
            ReferencedConceptsLookup targetLookup = new ReferencedConceptsLookup();
            targetLookup.setRefsetId(sourceLookup.getRefsetId());
            targetLookup.setType(sourceLookup.getType());
            if (sourceLookup.getConceptIds() != null) {
                targetLookup.setConceptIds(new HashSet<>(sourceLookup.getConceptIds()));
            }
            return targetLookup;
        }
        return null;
    }

    public Map<String, Integer> rebuild(String branchPath, List<Long> refsetIds, boolean dryRun) {
        if (!conceptsLookupEnabled) {
            logger.info("Concepts lookup rebuild is disabled.");
            return Collections.emptyMap();
        }
        try (Commit commit = branchService.openCommit(branchPath, branchMetadataHelper.getBranchLockMetadata("Rebuilding refset concepts lookup."))) {
            if (refsetIds == null || refsetIds.isEmpty()) {
                throw new IllegalArgumentException("Refset IDs must be provided when updating refset concepts lookup.");
            }
            Set<Long> allowedRefsetIds = getSelfOrDescendantOf(versionControlHelper.getBranchCriteriaIncludingOpenCommit(commit), getRefsetIdsFromConfig());
            if (!allowedRefsetIds.containsAll(refsetIds)) {
                throw new IllegalArgumentException("Refset IDs must be self or descendant of " + refsetIdsForLookup);
            }
            final Map<String, Integer> updateCounts = new HashMap<>();
            refsetIds.forEach(refsetId -> rebuildConceptsLookup(commit, refsetId, dryRun).forEach((key, value) -> updateCounts.put(refsetId + "-" + key, value))
            );
            if (!dryRun && updateCounts.values().stream().anyMatch(updateCount -> updateCount > 0)) {
                commit.markSuccessful();
            } else {
                logger.info("{} so rolling back the empty commit on {}.", dryRun ? "Dry run mode" : "No refset concept ids lookup changes required", branchPath);
                // When a commit is not marked as successful it is rolled back automatically when closed.
            }
            return updateCounts;
        }
    }


    Set<Long> getRefsetIdsFromConfig() {
        return Stream.of(refsetIdsForLookup.split(","))
                .map(String::trim)
                .map(Long::parseLong)
                .collect(Collectors.toSet());
    }

    private void rebuildOnSave(Commit commit) {
        // Check any reference set changes on the branch for configured refset ids
        Set<Long> refsetsToGenerateLookup = getSelfOrDescendantOf(versionControlHelper.getBranchCriteriaIncludingOpenCommit(commit), getRefsetIdsFromConfig());
        if (hasRefsetMemberChanges(commit, refsetsToGenerateLookup) ) {
            Set<String> refsetsUpdated = getRefsetIdsWithChanges(commit, refsetsToGenerateLookup);
            logger.info("Changes found for refsets {} on branch {} as per configured as self or descendant of {} ", refsetsUpdated,
                    commit.getBranch().getPath(), getRefsetIdsFromConfig());
            logger.info("Start building concepts lookup on branch {} ", commit.getBranch().getPath());
            refsetsUpdated.forEach(refsetId -> rebuildConceptsLookup(commit, parseLong(refsetId), false));
            logger.info("Building concepts lookup completed on branch {} ", commit.getBranch().getPath());
        } else {
            logger.info("No further concepts lookups rebuilding required as no reference set member changes found on branch {} for self or descendant of {} "
                    , commit.getBranch().getPath(), getRefsetIdsFromConfig());
        }
    }

    private boolean hasRefsetMemberChanges(Commit commit, Set<Long> refsetIds) {
        NativeQuery nativeQuery = new NativeQueryBuilder()
                .withQuery(bool(b -> b
                        .must(versionControlHelper.getBranchCriteriaChangesAndDeletionsWithinOpenCommitOnly(commit).getEntityBranchCriteria(ReferenceSetMember.class))
                        .must(termsQuery(REFSET_ID, refsetIds)))
                )
                .withSourceFilter(new FetchSourceFilter(new String[]{REFSET_ID}, null))
                .withPageable(Pageable.ofSize(1))
                .build();
        SearchHits<ReferenceSetMember> searchHits = elasticsearchOperations.search(nativeQuery, ReferenceSetMember.class);
        return searchHits.getTotalHits() > 0;
    }

    private Set<String> getRefsetIdsWithChanges(Commit commit, Set<Long> refsetIds) {
        NativeQuery nativeQuery = new NativeQueryBuilder()
                .withQuery(bool(b -> b
                        .must(versionControlHelper.getBranchCriteriaChangesAndDeletionsWithinOpenCommitOnly(commit)
                                .getEntityBranchCriteria(ReferenceSetMember.class))
                        .must(termsQuery(REFSET_ID, refsetIds))
                )).withAggregation("unique_refset_ids",
                                Aggregation.of(a -> a
                                        .terms(t -> t
                                                .field(REFSET_ID)
                                                .size(refsetIds.size())
                                        )
                                )
                        )
                        .withSourceFilter(new FetchSourceFilter(new String[]{}, null)) // Exclude source fields
                        .withPageable(Pageable.ofSize(1))
                        .build();

        SearchHits<ReferenceSetMember> searchHits = elasticsearchOperations.search(nativeQuery, ReferenceSetMember.class);
        // Extract aggregation results
        Set<String> result = new HashSet<>();
        AggregationUtils.getAggregations(searchHits.getAggregations(), "unique_refset_ids")
                .forEach(agg -> agg.getAggregate().sterms().buckets().array().forEach(bucket -> result.add(bucket.key().stringValue())));
        return result;
    }

    Map<String, Integer> rebuildConceptsLookup(Commit commit, Long refsetId, boolean dryRun) {
        final Set<Long> conceptIdsToAdd = new HashSet<>();
        final Set<Long> conceptIdsToRemove = new HashSet<>();
        Map<String, Integer> updateCount = new HashMap<>();
        if (refsetId == null) {
            throw new IllegalArgumentException("RefsetId must be provided when updating concepts lookup.");
        }
        performRebuild(commit, refsetId, conceptIdsToAdd, conceptIdsToRemove);
        if (dryRun) {
            updateCount.put(INCLUDE.name(), conceptIdsToAdd.size());
            updateCount.put(EXCLUDE.name(), conceptIdsToRemove.size());
            return updateCount;
        }
        if (!conceptIdsToAdd.isEmpty() || !conceptIdsToRemove.isEmpty()) {
            // GET existing lookups
            final List<ReferencedConceptsLookup> existingLookups = refsetConceptsLookupService.getConceptsLookups(
                    versionControlHelper.getBranchCriteria(commit.getBranch()), List.of(refsetId));
            if (conceptIdsToAdd.size() < conceptsLookupGenerationThreshold && existingLookups.isEmpty()) {
                logger.info("Referenced concepts lookup is not updated for {} refset with {} concepts to add as it is below the threshold of {}."
                        , refsetId, conceptIdsToAdd.size(), conceptsLookupGenerationThreshold);
                return updateCount;
            }
            // Check if lookups have changed
            List<ReferencedConceptsLookup> existingLookupsOnBranch = existingLookups.stream()
                    .filter(lookup -> lookup.getPath().equals(commit.getBranch().getPath())).toList();

            if (!haveLookUpsChanged(existingLookupsOnBranch, conceptIdsToAdd, conceptIdsToRemove)) {
                logger.info("Lookups are already up to date for {} refset on branch {}", refsetId, commit.getBranch().getPath());
                return updateCount;
            }
            // Expire existing lookups
            // To Use update query to update end date instead of fetching the whole document for more performance
            existingLookupsOnBranch.forEach(onBranchLookup -> onBranchLookup.setEnd(commit.getTimepoint()));
            refsetConceptsLookupRepository.saveAll(existingLookupsOnBranch);
            if (!conceptIdsToAdd.isEmpty()) {
                createAndSaveLookup(INCLUDE, refsetId, conceptIdsToAdd, commit);
                updateCount.put(INCLUDE.name(), conceptIdsToAdd.size());
            }
            if (!conceptIdsToRemove.isEmpty()) {
                createAndSaveLookup(EXCLUDE, refsetId, conceptIdsToRemove, commit);
                updateCount.put(EXCLUDE.name(), conceptIdsToRemove.size());
            }
        }
        return updateCount;
    }

    private void performRebuild(Commit commit, Long refsetId, Set<Long> conceptIdsToAdd, Set<Long> conceptIdsToRemove) {
        boolean completeRebuild = SPathUtil.isCodeSystemBranch(commit.getBranch().getPath());
        if (completeRebuild) {
           rebuildOnCodeSystemBranch(commit, refsetId, conceptIdsToAdd);
        } else {
            rebuildOnBranch(commit, refsetId, conceptIdsToAdd, conceptIdsToRemove);
        }
        if (!conceptIdsToAdd.isEmpty() || !conceptIdsToRemove.isEmpty()) {
            logger.info("Found {} concepts to add and {} concepts to remove for reference set {}", conceptIdsToAdd.size(), conceptIdsToRemove.size(), refsetId);
        }
    }

    private void rebuildOnCodeSystemBranch(Commit commit, Long refsetId, Set<Long> conceptIdsToAdd) {
        BranchCriteria branchCriteria = versionControlHelper.getBranchCriteriaIncludingOpenCommit(commit);
        if (commit.isRebase()) {
            branchCriteria = versionControlHelper.getChangesOnBranchIncludingOpenCommit(commit);
        }
        conceptIdsToAdd.addAll(getReferencedConceptsForRefsetId(branchCriteria, refsetId));
    }

    private void rebuildOnBranch(Commit commit, Long refsetId, Set<Long> conceptIdsToAdd, Set<Long> conceptIdsToRemove) {
        final BranchCriteria branchCriteria = versionControlHelper.getChangesOnBranchIncludingOpenCommit(commit);
        NativeQuery memberQuery = new NativeQueryBuilder()
                .withQuery(bool(b -> b.must(branchCriteria.getEntityBranchCriteria(ReferenceSetMember.class))
                        .must(termQuery(REFSET_ID, refsetId)))
                )
                .withSourceFilter(new FetchSourceFilter(new String[]{MEMBER_ID, REFERENCED_COMPONENT_ID, ACTIVE}, null))
                .withPageable(LARGE_PAGE)
                .build();
        Set<String> memberIds = new HashSet<>();
        try (final SearchHitsIterator<ReferenceSetMember> searchHitsIterator = elasticsearchOperations.searchForStream(memberQuery, ReferenceSetMember.class)) {
            searchHitsIterator.forEachRemaining(hit -> {
                final ReferenceSetMember referenceSetMember = hit.getContent();
                memberIds.add(referenceSetMember.getMemberId());
                if (referenceSetMember.isActive()) {
                    conceptIdsToAdd.add(parseLong(referenceSetMember.getReferencedComponentId()));
                } else {
                    conceptIdsToRemove.add(parseLong(referenceSetMember.getReferencedComponentId()));
                }
            });
        }
        // Check deleted members
        Set<ReferenceSetMember> changedMembers = getChangedMembersFromVersionReplaced(commit.getEntityVersionsReplaced()
                .getOrDefault(ReferenceSetMember.class.getSimpleName(), Collections.emptySet()), refsetId);
        final AtomicInteger deletedCounter = new AtomicInteger();
        changedMembers.forEach(referenceSetMember -> {
            if (!memberIds.contains(referenceSetMember.getMemberId())) {
                deletedCounter.incrementAndGet();
                conceptIdsToRemove.add(parseLong(referenceSetMember.getReferencedComponentId()));
            }});
        if (deletedCounter.get() > 0) {
            logger.info("Deleted members count: {} on branch {}", deletedCounter.get(), commit.getBranch().getPath());
        }
    }

    private boolean haveLookUpsChanged(List<ReferencedConceptsLookup> existingLookups, Set<Long> conceptIdsToAdd, Set<Long> conceptIdsToRemove) {
        if (existingLookups.isEmpty() && (!conceptIdsToAdd.isEmpty() || !conceptIdsToRemove.isEmpty())) {
            return true;
        }
        for (ReferencedConceptsLookup existingLookup : existingLookups) {
            if (existingLookup.getType() == INCLUDE && !existingLookup.getConceptIds().equals(conceptIdsToAdd)) {
                return true;
            }
            if (existingLookup.getType() == EXCLUDE && !existingLookup.getConceptIds().equals(conceptIdsToRemove)) {
                return true;
            }
        }
        return false;
    }

    private void createAndSaveLookup(ReferencedConceptsLookup.Type type, Long refsetId, Set<Long> conceptIds, Commit commit) {
        logger.info("Updating referenced concepts {} lookup for {} refset with {} concepts.", type, refsetId, conceptIds.size());
        final ReferencedConceptsLookup conceptsLookup = new ReferencedConceptsLookup(String.valueOf(refsetId), conceptIds, type);
        conceptsLookup.setStart(commit.getTimepoint());
        conceptsLookup.setPath(commit.getBranch().getPath());
        refsetConceptsLookupRepository.save(conceptsLookup);
    }

    private Set<ReferenceSetMember> getChangedMembersFromVersionReplaced(Set<String> refsetMemberDeletionsToProcess, Long refsetId) {
        if (refsetMemberDeletionsToProcess.isEmpty()) {
            return Collections.emptySet();
        }
        Set<ReferenceSetMember> referenceSetMembers = new HashSet<>();
        NativeQuery query = new NativeQueryBuilder()
                .withQuery(bool(b -> b
                        .must(termsQuery("_id", refsetMemberDeletionsToProcess))
                        .must(termQuery(REFSET_ID, refsetId))))
                .withSourceFilter(new FetchSourceFilter(new String[]{MEMBER_ID, REFERENCED_COMPONENT_ID}, null))
                .withPageable(Pageable.ofSize(refsetMemberDeletionsToProcess.size()))
                .build();
        SearchHits<ReferenceSetMember> referenceSetMemberSearchHits = elasticsearchOperations.search(query, ReferenceSetMember.class);
        referenceSetMemberSearchHits.forEach(hit -> referenceSetMembers.add(hit.getContent()));
        return referenceSetMembers;
    }

    private Set<Long> getReferencedConceptsForRefsetId(BranchCriteria criteria, final Long refsetId) {
        final Set<Long> conceptIds = new HashSet<>();
        try (final SearchHitsIterator<ReferenceSetMember> searchHitsIterator = elasticsearchOperations.searchForStream(new NativeQueryBuilder()
                .withQuery(bool(b -> b
                        .must(criteria.getEntityBranchCriteria(ReferenceSetMember.class))
                        .must(termQuery(REFSET_ID, refsetId))))
                .withSourceFilter(new FetchSourceFilter(new String[]{REFERENCED_COMPONENT_ID}, null))
                .withPageable(LARGE_PAGE).build(), ReferenceSetMember.class)) {
            searchHitsIterator.forEachRemaining(hit -> conceptIds.add(parseLong(hit.getContent().getReferencedComponentId())));
        }
        return conceptIds;
    }


    private Set<Long> getSelfOrDescendantOf(BranchCriteria branchCriteria, Set<Long> refsetIds) {
        final Set<Long> results = new HashSet<>(refsetIds);
        try (final SearchHitsIterator<QueryConcept> queryConceptSearchHitsIterator = elasticsearchOperations.searchForStream(new NativeQueryBuilder()
                .withQuery(bool(b -> b
                        .must(branchCriteria.getEntityBranchCriteria(QueryConcept.class))
                        .must(termQuery(QueryConcept.Fields.STATED, true))
                        .must(termsQuery(QueryConcept.Fields.ANCESTORS, refsetIds))))
                .withSourceFilter(new FetchSourceFilter(new String[]{QueryConcept.Fields.CONCEPT_ID}, null))
                .withPageable(LARGE_PAGE).build(), QueryConcept.class)) {
            queryConceptSearchHitsIterator.forEachRemaining(hit -> results.add(hit.getContent().getConceptIdL()));
        }
        return results;
    }
}