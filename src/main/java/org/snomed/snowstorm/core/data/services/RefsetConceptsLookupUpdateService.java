package org.snomed.snowstorm.core.data.services;

import io.kaicode.elasticvc.api.*;
import io.kaicode.elasticvc.domain.Branch;
import io.kaicode.elasticvc.domain.Commit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.core.data.domain.ReferenceSetMember;
import org.snomed.snowstorm.core.data.domain.RefsetConceptsLookup;
import org.snomed.snowstorm.core.data.repositories.RefsetConceptsLookupRepository;
import org.snomed.snowstorm.core.util.SPathUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHitsIterator;
import org.springframework.data.elasticsearch.core.query.FetchSourceFilter;
import org.springframework.stereotype.Service;

import java.util.*;

import static co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders.bool;
import static io.kaicode.elasticvc.helper.QueryHelper.termQuery;
import static java.lang.Long.parseLong;
import static org.snomed.snowstorm.core.data.domain.ReferenceSetMember.Fields.REFERENCED_COMPONENT_ID;
import static org.snomed.snowstorm.core.data.domain.ReferenceSetMember.Fields.REFSET_ID;

@Service
public class RefsetConceptsLookupUpdateService extends ComponentService implements CommitListener  {

    @Autowired
    private VersionControlHelper versionControlHelper;

    @Autowired
    private ElasticsearchOperations elasticsearchOperations;

    @Autowired
    private RefsetConceptsLookupRepository refsetConceptsLookupRepository;

    @Autowired
    private BranchService branchService;

    @Autowired
    private BranchMetadataHelper branchMetadataHelper;

    private final Logger logger = LoggerFactory.getLogger(getClass());
    @Override
    public void preCommitCompletion(Commit commit) throws IllegalStateException {
        // TODO: Implement this method

    }

    public Map<String, Integer> rebuild(String branchPath, String refsetId, boolean dryRun) throws ServiceException {

        try (Commit commit = branchService.openCommit(branchPath, branchMetadataHelper.getBranchLockMetadata("Rebuilding refset concepts lookup."))) {
            final Map<String, Integer> updateCounts = rebuildRefsetConceptsLookup(commit, refsetId, dryRun);
            if (!dryRun && updateCounts.values().stream().anyMatch(updateCount -> updateCount > 0)) {
                commit.markSuccessful();
            } else {
                logger.info("{} so rolling back the empty commit on {}.", dryRun ? "Dry run mode" : "No refset concept ids lookup changes required", branchPath);
                // When a commit is not marked as successful it is rolled back automatically when closed.
            }
            return updateCounts;
        }
    }

    private Map<String, Integer> rebuildRefsetConceptsLookup(Commit commit, String refsetId, boolean dryRun) throws ServiceException {
        Branch branch = commit.getBranch();
        Set<String> refsetMemberDeletionsToProcess = branch.getVersionsReplaced(ReferenceSetMember.class);
        boolean completeRebuild = SPathUtil.isCodeSystemBranch(branch.getPath());
        Map<String, Integer> updateCount = new HashMap<>();
        if (!completeRebuild) {
            // Recreate lookup using new parent base point + content on this branch
            if (dryRun) {
                throw new IllegalArgumentException("dryRun flag can only be used when rebuilding the refset concepts lookup of the CodeSystem branch.");
            }
            updateCount.put(RefsetConceptsLookup.Type.INCLUDE.name(), updateRefsetConceptIdsLookup(RefsetConceptsLookup.Type.INCLUDE, refsetMemberDeletionsToProcess, commit, true, false, dryRun, refsetId));
            updateCount.put(RefsetConceptsLookup.Type.EXCLUDE.name(), updateRefsetConceptIdsLookup(RefsetConceptsLookup.Type.EXCLUDE, refsetMemberDeletionsToProcess, commit, true, false, dryRun, refsetId));
        } else {
            // Complete rebuild only for INCLUDE refset concepts lookup
            updateCount.put(RefsetConceptsLookup.Type.INCLUDE.name(), updateRefsetConceptIdsLookup(RefsetConceptsLookup.Type.INCLUDE, refsetMemberDeletionsToProcess, commit, false, true, dryRun, refsetId));
        }
        return updateCount;
    }

    private Integer updateRefsetConceptIdsLookup(RefsetConceptsLookup.Type type, Set<String> refsetMemberDeletionsToProcess, Commit commit, boolean rebuild, boolean completeRebuild, boolean dryRun, String refsetId) {
        final BranchCriteria branchCriteria = versionControlHelper.getBranchCriteriaChangesAndDeletionsWithinOpenCommitOnly(commit);
        final Set<Long> conceptIds = new HashSet<>();
        if (refsetId == null) {
            throw new IllegalArgumentException("RefsetId must be provided when updating refset concepts lookup.");
        }
        if (completeRebuild) {
            conceptIds.addAll(getReferencedConceptsForRefsetId(versionControlHelper.getBranchCriteria(commit.getBranch()), parseLong(refsetId)));
        } else {
            // TODO need to add logic for collecting specific refset ids required for the refset concepts lookup
            try (final SearchHitsIterator<ReferenceSetMember> refsetMemberships = getRefsetMembershipChanges(commit)) {
                while (refsetMemberships.hasNext()) {
                    final ReferenceSetMember refsetMembership = refsetMemberships.next().getContent();
                    if (refsetMemberDeletionsToProcess.contains(refsetMembership.getId())) {
                        conceptIds.remove(parseLong(refsetMembership.getReferencedComponentId()));
                    } else if (refsetMembership.isActive()) {
                        conceptIds.add(parseLong(refsetMembership.getReferencedComponentId()));
                    }
                }
            }
        }
        if (!dryRun) {
            logger.info("Updating refset concepts lookup for {} refset with {} concepts.", refsetId, conceptIds.size());
            final RefsetConceptsLookup refsetConceptsLookup = new RefsetConceptsLookup(refsetId, conceptIds, RefsetConceptsLookup.Type.INCLUDE);
            refsetConceptsLookup.setStart(commit.getTimepoint());
            refsetConceptsLookup.setPath(commit.getBranch().getPath());
            refsetConceptsLookupRepository.save(refsetConceptsLookup);
            logger.info("Refset concepts lookup updated for {} refset with {} concepts.", refsetId, conceptIds.size());
        }
        return conceptIds.size();
    }

    private SearchHitsIterator<ReferenceSetMember> getRefsetMembershipChanges(Commit commit) {
        final BranchCriteria branchCriteria = versionControlHelper.getBranchCriteriaChangesAndDeletionsWithinOpenCommitOnly(commit);
        return elasticsearchOperations.searchForStream(new NativeQueryBuilder().withQuery(branchCriteria.getEntityBranchCriteria(ReferenceSetMember.class)).build(), ReferenceSetMember.class);
    }
    private Set<Long> getReferencedConceptsForRefsetId(BranchCriteria criteria, final Long refsetId) {
        final Set<Long> conceptIds = new HashSet<>();
        try (final SearchHitsIterator<ReferenceSetMember> refsetMemberships = elasticsearchOperations.searchForStream(new NativeQueryBuilder()
                .withQuery(bool(b -> b
                        .must(criteria.getEntityBranchCriteria(ReferenceSetMember.class))
                        .must(termQuery(ReferenceSetMember.Fields.ACTIVE, true))
                        .must(termQuery(REFSET_ID, refsetId))))
                .withSourceFilter(new FetchSourceFilter(new String[]{REFERENCED_COMPONENT_ID}, null))
                .withPageable(LARGE_PAGE).build(), ReferenceSetMember.class)) {
            while (refsetMemberships.hasNext()) {
                conceptIds.add(parseLong(refsetMemberships.next().getContent().getReferencedComponentId()));
            }
        }
        return conceptIds;
    }
}