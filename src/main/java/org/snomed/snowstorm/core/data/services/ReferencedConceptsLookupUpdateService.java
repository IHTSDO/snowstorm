package org.snomed.snowstorm.core.data.services;

import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import io.kaicode.elasticvc.api.*;
import io.kaicode.elasticvc.domain.Commit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.data.repositories.ReferencedConceptsLookupRepository;
import org.snomed.snowstorm.core.util.AggregationUtils;
import org.snomed.snowstorm.core.util.SPathUtil;
import org.snomed.snowstorm.ecl.ECLQueryService;
import org.snomed.snowstorm.ecl.ReferencedConceptsLookupService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.UncategorizedElasticsearchException;
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
    private final ECLQueryService eclQueryService;

    @Autowired
    public ReferencedConceptsLookupUpdateService(VersionControlHelper versionControlHelper,
                                                 ElasticsearchOperations elasticsearchOperations,
                                                 ReferencedConceptsLookupRepository refsetConceptsLookupRepository,
                                                 ReferencedConceptsLookupService refsetConceptsLookupService,
                                                 BranchService branchService,
                                                 ECLQueryService eclQueryService,
                                                 BranchMetadataHelper branchMetadataHelper) {
        this.versionControlHelper = versionControlHelper;
        this.elasticsearchOperations = elasticsearchOperations;
        this.refsetConceptsLookupRepository = refsetConceptsLookupRepository;
        this.refsetConceptsLookupService = refsetConceptsLookupService;
        this.branchService = branchService;
        this.eclQueryService = eclQueryService;
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
        existingRefsetIds.forEach(refsetId -> rebuildConceptsLookup(commit, parseLong(refsetId), false, false));
    }

    private void updateOnPromotion(Commit commit) {
        BranchCriteria sourceBranchOnly = versionControlHelper.getChangesOnBranchCriteria(commit.getSourceBranchPath());
        List<ReferencedConceptsLookup> sourceLookups = refsetConceptsLookupService.getConceptsLookups(sourceBranchOnly);
        if (sourceLookups.isEmpty()) {
            return;
        }
        logger.info("Found {} lookups on branch {} to promote", sourceLookups.size(), commit.getSourceBranchPath());
        Set<Long> refsetIds = sourceLookups.stream().map(lookup -> Long.parseLong(lookup.getRefsetId())).collect(Collectors.toSet());
        BranchCriteria targetBranchOnly = versionControlHelper.getChangesOnBranchCriteria(commit.getBranch());
        List<ReferencedConceptsLookup> existingTargetLookups = refsetConceptsLookupService.getConceptsLookups(targetBranchOnly, refsetIds);
        Map<Long, ReferencedConceptsLookup> targetIncludeLookupMap = constructMapByRefsetId(existingTargetLookups, INCLUDE);
        Map<Long, ReferencedConceptsLookup> targetExcludeLookupMap = constructMapByRefsetId(existingTargetLookups, EXCLUDE);

        List<ReferencedConceptsLookup> newTargetLookups = constructNewTargetLookupsFromSource(sourceLookups, targetIncludeLookupMap, targetExcludeLookupMap);
        // Consolidate lookups for INCLUDE and EXCLUDE to remove overlap concepts by refsetId
        consolidateLookups(newTargetLookups);
        List<ReferencedConceptsLookup> lookupsToSave = new ArrayList<>();
        newTargetLookups.forEach(target -> {
            if (isLookupChanged(target, targetIncludeLookupMap, targetExcludeLookupMap) && !target.getConceptIds().isEmpty()) {
                target.setPath(commit.getBranch().getPath());
                target.setTotal(target.getConceptIds().size());
                target.setStart(commit.getTimepoint());
                lookupsToSave.add(target);
            }
        });

        existingTargetLookups.stream()
                .filter(ReferencedConceptsLookup::isChanged)
                .forEach(lookup -> {
                    lookup.setEnd(commit.getTimepoint());
                    lookupsToSave.add(lookup);
                });

        if (!lookupsToSave.isEmpty()) {
            logger.info("Updating {} lookups on branch {}", lookupsToSave.size(), commit.getBranch().getPath());
        }
        sourceLookups.forEach(lookup -> lookup.setEnd(commit.getTimepoint()));
        lookupsToSave.addAll(sourceLookups);
        refsetConceptsLookupRepository.saveAll(lookupsToSave);
        logger.info("Completed saving {} concepts lookups on promotion.", lookupsToSave.size());
    }

    private void consolidateLookups(List<ReferencedConceptsLookup> newTargetLookups) {
        Map<Long, ReferencedConceptsLookup> includeLookupMap = constructMapByRefsetId(newTargetLookups, INCLUDE);
        Map<Long, ReferencedConceptsLookup> excludeLookupMap = constructMapByRefsetId(newTargetLookups, EXCLUDE);

        for (ReferencedConceptsLookup includeLookup : includeLookupMap.values()) {
            ReferencedConceptsLookup excludeLookup = excludeLookupMap.get(parseLong(includeLookup.getRefsetId()));
            if (excludeLookup != null) {
                Set<Long> intersection = new HashSet<>(excludeLookup.getConceptIds());
                intersection.retainAll(includeLookup.getConceptIds());
                includeLookup.getConceptIds().removeAll(intersection);
                excludeLookup.getConceptIds().removeAll(intersection);
            }
        }
    }

    private boolean isLookupChanged(ReferencedConceptsLookup target,
                                    Map<Long, ReferencedConceptsLookup> targetIncludeLookupMap,
                                    Map<Long, ReferencedConceptsLookup> targetExcludeLookupMap) {
        Map<Long, ReferencedConceptsLookup> lookupMap = target.getType() == INCLUDE ? targetIncludeLookupMap : targetExcludeLookupMap;
        ReferencedConceptsLookup existing = lookupMap.get(parseLong(target.getRefsetId()));
        if (existing == null) {
            return true;
        }
        if (!existing.getConceptIds().equals(target.getConceptIds())) {
            existing.setChanged(true);
            return true;
        }
        return false;
    }

    private List<ReferencedConceptsLookup> constructNewTargetLookupsFromSource(List<ReferencedConceptsLookup> sourceLookups,
                                                                               Map<Long, ReferencedConceptsLookup> targetIncludeLookupMap,
                                                                               Map<Long, ReferencedConceptsLookup> targetExcludeLookupMap) {
        Map<Long, List<ReferencedConceptsLookup>> sourceLookupsByRefsetId = constructMapByRefsetId(sourceLookups);
        List<ReferencedConceptsLookup> newTargetLookups = new ArrayList<>();

        sourceLookupsByRefsetId.forEach((refsetId, lookups) -> {
            boolean sourceContainBothTypes = lookups.stream().anyMatch(lookup -> lookup.getType() == INCLUDE) &&
                    lookups.stream().anyMatch(lookup -> lookup.getType() == EXCLUDE);

            lookups.forEach(sourceLookup -> cloneAndUpdate(targetIncludeLookupMap, targetExcludeLookupMap, refsetId, sourceLookup, newTargetLookups, sourceContainBothTypes));
        });
        return newTargetLookups;
    }

    private void cloneAndUpdate(Map<Long, ReferencedConceptsLookup> targetIncludeLookupMap, Map<Long, ReferencedConceptsLookup> targetExcludeLookupMap, Long refsetId, ReferencedConceptsLookup sourceLookup, List<ReferencedConceptsLookup> newTargetLookups, boolean sourceContainBothTypes) {
        ReferencedConceptsLookup newTargetLookup = clone(sourceLookup.getType() == INCLUDE ? targetIncludeLookupMap.get(refsetId) : targetExcludeLookupMap.get(refsetId));
        if (newTargetLookup == null) {
            newTargetLookup = new ReferencedConceptsLookup(sourceLookup.getRefsetId(), new HashSet<>(sourceLookup.getConceptIds()), sourceLookup.getType());
        } else {
            newTargetLookup.getConceptIds().addAll(sourceLookup.getConceptIds());
            newTargetLookup.setTotal(newTargetLookup.getConceptIds().size());
        }
        newTargetLookups.add(newTargetLookup);
        if (!sourceContainBothTypes) {
            ReferencedConceptsLookup otherTypeLookup = sourceLookup.getType() == INCLUDE ? targetExcludeLookupMap.get(refsetId) : targetIncludeLookupMap.get(refsetId);
            if (otherTypeLookup != null) {
                newTargetLookups.add(clone(otherTypeLookup));
            }
        }
    }

    private static Map<Long, ReferencedConceptsLookup> constructMapByRefsetId(Collection<ReferencedConceptsLookup> targetLookups, ReferencedConceptsLookup.Type type) {
        return targetLookups.stream()
                .filter(lookup -> lookup.getType().equals(type))
                .collect(Collectors.toMap(
                        lookup -> Long.parseLong(lookup.getRefsetId()),
                        lookup -> lookup
                ));
    }

    private static Map<Long, List<ReferencedConceptsLookup>> constructMapByRefsetId(List<ReferencedConceptsLookup> targetLookups) {

        return targetLookups.stream()
                .collect(Collectors.groupingBy(
                        lookup -> Long.parseLong(lookup.getRefsetId())
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

    public Map<String, Integer> rebuild(String branchPath, List<Long> refsetIds, boolean disableThresholdCheck, boolean dryRun) {
        if (!conceptsLookupEnabled) {
            logger.info("Concepts lookup rebuild is disabled.");
            return Collections.emptyMap();
        }
        if (disableThresholdCheck) {
            logger.info("Threshold checking is disabled.");
        }
        try (Commit commit = branchService.openCommit(branchPath, branchMetadataHelper.getBranchLockMetadata("Rebuilding referenced concepts lookup."))) {
            Set<Long> allowedRefsetIds = getSelfOrDescendantOf(versionControlHelper.getBranchCriteriaIncludingOpenCommit(commit), getRefsetIdsFromConfig());
            logger.info("Refset ids to generate lookup for: {}", refsetIdsForLookup);
            if (refsetIds == null || refsetIds.isEmpty()) {
                refsetIds = new ArrayList<>(allowedRefsetIds);
            } else if (!allowedRefsetIds.containsAll(refsetIds)) {
                throw new IllegalArgumentException("Refset IDs must be self or descendant of " + refsetIdsForLookup);
            }
            final Map<String, Integer> updateCounts = new HashMap<>();
            logger.info("Start rebuilding concepts lookup on branch {}", branchPath);
            refsetIds.forEach(refsetId -> rebuildConceptsLookup(commit, refsetId, disableThresholdCheck, dryRun).forEach((key, value) -> updateCounts.put(refsetId + "-" + key, value))
            );
            if (!dryRun && updateCounts.values().stream().anyMatch(updateCount -> updateCount > 0)) {
                commit.markSuccessful();
            } else {
                logger.info("{} so rolling back the empty commit on {}.", dryRun ? "Dry run mode" : "No refset concept ids lookup changes required", branchPath);
                // When a commit is not marked as successful it is rolled back automatically when closed.
            }
            logger.info("Complete rebuilding concepts lookup on branch {}", branchPath);
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
            refsetsUpdated.forEach(refsetId -> rebuildConceptsLookup(commit, parseLong(refsetId), false, false));
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

    Map<String, Integer> rebuildConceptsLookup(Commit commit, Long refsetId, boolean disableThresholdCheck, boolean dryRun) {
        final Set<Long> conceptIdsToAdd = new HashSet<>();
        final Set<Long> conceptIdsToRemove = new HashSet<>();
        Map<String, Integer> updateCount = new HashMap<>();
        if (refsetId == null) {
            throw new IllegalArgumentException("Refset Id must not be null when updating concepts lookup.");
        }
        performRebuild(commit, refsetId, conceptIdsToAdd, conceptIdsToRemove);
        if (!conceptIdsToAdd.isEmpty() || !conceptIdsToRemove.isEmpty()) {
            // GET existing lookups including parent branch
            final List<ReferencedConceptsLookup> existingLookups = refsetConceptsLookupService.getConceptsLookups(
                    versionControlHelper.getBranchCriteria(commit.getBranch()), List.of(refsetId));
            if (existingLookups.isEmpty() && shouldSkipUpdate(conceptIdsToAdd, conceptIdsToRemove, disableThresholdCheck, refsetId, commit)) {
                return updateCount;
            }

            // Check if lookups have changed
            List<ReferencedConceptsLookup> existingLookupsOnBranch = existingLookups.stream()
                    .filter(lookup -> lookup.getPath().equals(commit.getBranch().getPath())).toList();

            if (!lookupsChanged(existingLookupsOnBranch, conceptIdsToAdd, conceptIdsToRemove)) {
                logger.info("Lookups are already up to date for {} refset on branch {}", refsetId, commit.getBranch().getPath());
                return updateCount;
            }

            if (dryRun) {
                reportUpdateCountForDryRun(conceptIdsToAdd, updateCount, conceptIdsToRemove);
                return updateCount;
            }
            // Not dry run expire existing lookups
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

    private boolean shouldSkipUpdate(Set<Long> conceptIdsToAdd, Set<Long> conceptIdsToRemove, boolean disableThresholdCheck, Long refsetId, Commit commit) {
        if (!PathUtil.isRoot(commit.getBranch().getPath()) && hasReferencedMemberInParentBranch(refsetId, commit)) {
            logger.info("Parent branch has members for refset {} but no existing concepts lookup found therefore no further rebuilding is required on branch {}.",
                    refsetId, commit.getBranch().getPath());
            return true;
        }
        if (!disableThresholdCheck && Math.max(conceptIdsToAdd.size(), conceptIdsToRemove.size()) < conceptsLookupGenerationThreshold) {
            logger.info("Referenced concepts lookup is not updated for refset {} because changes are below the threshold of {}.", refsetId, conceptsLookupGenerationThreshold);
            return true;
        }
        return false;
    }

    private boolean hasReferencedMemberInParentBranch(Long refsetId, Commit commit) {
        // To check if the parent branch has members for the refset
        BranchCriteria parentBranchCriteria = versionControlHelper.getBranchCriteriaForParentBranchAtBranchBaseTimepoint(commit.getBranch().getPath());
        NativeQuery query = new NativeQueryBuilder()
                .withQuery(bool(b -> b
                        .must(parentBranchCriteria.getEntityBranchCriteria(ReferenceSetMember.class))
                        .must(termQuery(REFSET_ID, refsetId))
                        .must(termQuery(ACTIVE, true))
                ))
                .withPageable(Pageable.ofSize(1))
                .build();
        SearchHits<ReferenceSetMember> searchHits = elasticsearchOperations.search(query, ReferenceSetMember.class);
        return searchHits.getTotalHits() > 0;
    }

    private static void reportUpdateCountForDryRun(Set<Long> conceptIdsToAdd, Map<String, Integer> updateCount, Set<Long> conceptIdsToRemove) {
        if (!conceptIdsToAdd.isEmpty()) {
            updateCount.put(INCLUDE.name(), conceptIdsToAdd.size());
        }
        if (!conceptIdsToRemove.isEmpty()) {
            updateCount.put(EXCLUDE.name(), conceptIdsToRemove.size());
        }
    }

    private void performRebuild(Commit commit, Long refsetId, Set<Long> conceptIdsToAdd, Set<Long> conceptIdsToRemove) {
        boolean completeRebuild = SPathUtil.isCodeSystemBranch(commit.getBranch().getPath());
        if (completeRebuild) {
            BranchCriteria branchCriteria = versionControlHelper.getChangesOnBranchIncludingOpenCommit(commit);
            conceptIdsToAdd.addAll(getReferencedConceptsForRefsetId(branchCriteria, refsetId));
        } else {
            rebuildOnBranchChangesOnly(commit, refsetId, conceptIdsToAdd, conceptIdsToRemove);
        }
        if (!conceptIdsToAdd.isEmpty() || !conceptIdsToRemove.isEmpty()) {
            logger.info("Found {} concepts to add and {} concepts to remove for reference set {}", conceptIdsToAdd.size(), conceptIdsToRemove.size(), refsetId);
        }
    }


    private void rebuildOnBranchChangesOnly(Commit commit, Long refsetId, Set<Long> conceptIdsToAdd, Set<Long> conceptIdsToRemove) {
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
        Set<String> membersReplaced = commit.getBranch().getVersionsReplaced().getOrDefault(ReferenceSetMember.class.getSimpleName(), new HashSet<>());
        membersReplaced.addAll(commit.getEntityVersionsReplaced()
                .getOrDefault(ReferenceSetMember.class.getSimpleName(), Collections.emptySet()));

        Set<ReferenceSetMember> changedMembers = getChangedMembersFromVersionReplaced(membersReplaced, refsetId);
        final AtomicInteger deletedCounter = new AtomicInteger();
        changedMembers.forEach(referenceSetMember -> {
            if (!memberIds.contains(referenceSetMember.getMemberId())) {
                deletedCounter.incrementAndGet();
                conceptIdsToRemove.add(parseLong(referenceSetMember.getReferencedComponentId()));
            }});
        if (deletedCounter.get() > 0) {
            logger.info("Deleted members count: {} on branch {}", deletedCounter.get(), commit.getBranch().getPath());
        }
        // Ignore concepts to remove when same concepts also exist in the concepts to add list
        conceptIdsToRemove.removeAll(conceptIdsToAdd);
        // Check whether there are any active members for referenced concept ids for the same refset in case of duplicates or mapping
        Set<Long> componentIdsWithActiveMembers = fetchReferencedComponentIdsWithActiveMembers(refsetId, conceptIdsToRemove, commit);
        conceptIdsToRemove.removeAll(componentIdsWithActiveMembers);
    }

    private Set<Long> fetchReferencedComponentIdsWithActiveMembers(Long refsetId, Set<Long> conceptIdsToRemove, Commit commit) {
        if (conceptIdsToRemove.isEmpty()) {
            return Collections.emptySet();
        }
        final BranchCriteria branchCriteria = versionControlHelper.getBranchCriteriaIncludingOpenCommit(commit);
        NativeQuery memberQuery = new NativeQueryBuilder()
                .withQuery(bool(b -> b.must(branchCriteria.getEntityBranchCriteria(ReferenceSetMember.class))
                                .must(termQuery(REFSET_ID, refsetId))
                                .must(termQuery(ACTIVE, true))
                                .must(termsQuery(REFERENCED_COMPONENT_ID, conceptIdsToRemove))
                        )
                )
                .withSourceFilter(new FetchSourceFilter(new String[]{REFERENCED_COMPONENT_ID, MEMBER_ID}, null))
                .withPageable(LARGE_PAGE)
                .build();
        Set<Long> componentWithActiveMembers = new HashSet<>();
        String branchPath = commit.getBranch().getPath();
        try (final SearchHitsIterator<ReferenceSetMember> searchHitsIterator = elasticsearchOperations.searchForStream(memberQuery, ReferenceSetMember.class)) {
            searchHitsIterator.forEachRemaining(hit -> {
                logger.info("Concept id {} is still referenced by active member {} for refset {} when searched on branch {}",
                        hit.getContent().getReferencedComponentId(), hit.getContent().getMemberId(), refsetId, branchPath);
                componentWithActiveMembers.add(parseLong(hit.getContent().getReferencedComponentId()));
            });
        }
        return componentWithActiveMembers;
    }

    private boolean lookupsChanged(List<ReferencedConceptsLookup> existingLookups, Set<Long> conceptIdsToAdd, Set<Long> conceptIdsToRemove) {
        if (existingLookups.isEmpty() && (!conceptIdsToAdd.isEmpty() || !conceptIdsToRemove.isEmpty())) {
            return true;
        }
        boolean isChanged = false;
        if (!conceptIdsToAdd.isEmpty()) {
            Optional<ReferencedConceptsLookup> includeLookup = existingLookups.stream().filter(lookup -> lookup.getType() == INCLUDE).findAny();
            if (includeLookup.isEmpty() || !includeLookup.get().getConceptIds().equals(conceptIdsToAdd)) {
                isChanged = true;
            }
        }
        if (!conceptIdsToRemove.isEmpty()) {
            Optional<ReferencedConceptsLookup> excludeLookup = existingLookups.stream().filter(lookup -> lookup.getType() == EXCLUDE).findAny();
            if (excludeLookup.isEmpty() || !excludeLookup.get().getConceptIds().equals(conceptIdsToRemove)) {
                isChanged = true;
            }
        }
        return isChanged;
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
        try {
            SearchHits<ReferenceSetMember> referenceSetMemberSearchHits = elasticsearchOperations.search(query, ReferenceSetMember.class);
            referenceSetMemberSearchHits.forEach(hit -> referenceSetMembers.add(hit.getContent()));
            return referenceSetMembers;
        } catch (UncategorizedElasticsearchException e) {
            String msg = "Failed to fetch deleted reference set members for refset " + refsetId;
            logger.warn("{} via ES _id using query {}", msg, query);
            throw new IllegalStateException(msg, e);
        }
    }

    private Set<Long> getReferencedConceptsForRefsetId(BranchCriteria criteria, final Long refsetId) {
        final Set<Long> conceptIds = new HashSet<>();
        try (final SearchHitsIterator<ReferenceSetMember> searchHitsIterator = elasticsearchOperations.searchForStream(new NativeQueryBuilder()
                .withQuery(bool(b -> b
                        .must(criteria.getEntityBranchCriteria(ReferenceSetMember.class))
                        .must(termQuery(REFSET_ID, refsetId))
                        .must(termQuery(ACTIVE, true))))
                .withSourceFilter(new FetchSourceFilter(new String[]{REFERENCED_COMPONENT_ID}, null))
                .withPageable(LARGE_PAGE).build(), ReferenceSetMember.class)) {
            searchHitsIterator.forEachRemaining(hit -> conceptIds.add(parseLong(hit.getContent().getReferencedComponentId())));
        }
        return conceptIds;
    }


    private Set<Long> getSelfOrDescendantOf(BranchCriteria branchCriteria, Set<Long> refsetIds) {
        // Build self or descendant of refset concept ids
        StringBuilder builder = new StringBuilder();
        boolean isFirst = true;
        for (Long refsetId : refsetIds) {
            if (!isFirst) {
                builder.append(" OR ");
            }
            builder.append("<<");
            builder.append(refsetId);
            isFirst = false;
        }
        Page<Long> results = eclQueryService.selectConceptIds(builder.toString(), branchCriteria, false, LARGE_PAGE);
        return new HashSet<>(results.getContent());
    }


    public void remove(String path, List<Long> refsetIds) {
        if (!conceptsLookupEnabled) {
            logger.info("Concepts lookup is disabled.");
        }
        try (Commit commit = branchService.openCommit(path, branchMetadataHelper.getBranchLockMetadata("Removing referenced concepts lookup."))) {
            logger.info("Start removing concepts lookup on branch {} for refsetIds {}", path, refsetIds);
            List<ReferencedConceptsLookup> lookupsToEnd = refsetConceptsLookupService.getConceptsLookups(versionControlHelper.getBranchCriteria(commit.getBranch()), refsetIds);
            if (!lookupsToEnd.isEmpty()) {
                logger.info("Find total {} concepts lookups to remove", lookupsToEnd.size());
                lookupsToEnd.forEach(lookup -> lookup.setEnd(commit.getTimepoint()));
                refsetConceptsLookupRepository.saveAll(lookupsToEnd);
                commit.markSuccessful();
                logger.info("Complete removing {} concepts lookup on branch {} for refsetIds {}", lookupsToEnd.size(), path, refsetIds);
            } else {
                logger.info("No concepts lookup found to remove on branch {} for refsetIds {}", path, refsetIds);
            }
        }
    }
}