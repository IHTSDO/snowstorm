package org.snomed.snowstorm.core.data.services;

import io.kaicode.elasticvc.api.*;
import io.kaicode.elasticvc.domain.Commit;
import io.kaicode.elasticvc.domain.Metadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.core.data.domain.Concepts;
import org.snomed.snowstorm.core.data.domain.ReferenceSetMember;
import org.snomed.snowstorm.core.util.SPathUtil;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders.bool;
import static io.kaicode.elasticvc.domain.Commit.CommitType.CONTENT;
import static io.kaicode.elasticvc.domain.Commit.CommitType.REBASE;
import static io.kaicode.elasticvc.helper.QueryHelper.termQuery;
import static org.snomed.snowstorm.core.data.domain.SnomedComponent.Fields.ACTIVE;

@Service
public class AdditionalDependencyUpdateService extends ComponentService implements CommitListener {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final VersionControlHelper versionControlHelper;
    private final ElasticsearchOperations elasticsearchOperations;
    private final ModuleDependencyService moduleDependencyService;
    private final BranchService branchService;

    public AdditionalDependencyUpdateService(VersionControlHelper versionControlHelper, ElasticsearchOperations elasticsearchOperations, 
                                             ModuleDependencyService moduleDependencyService, BranchService branchService) {
        this.versionControlHelper = versionControlHelper;
        this.elasticsearchOperations = elasticsearchOperations;
        this.moduleDependencyService = moduleDependencyService;
        this.branchService = branchService;
    }

    @Override
    public void preCommitCompletion(Commit commit) throws IllegalStateException {
        // Check MDRS changes on extension CodeSystem branches
        if (SPathUtil.isCodeSystemBranch(commit.getBranch().getPath())
                && !commit.getBranch().getPath().equals("MAIN")
                && (commit.getCommitType() == CONTENT || commit.getCommitType() == REBASE)) {

            // Update extension CodeSystem branch metadata with additional dependency
            performUpdate(commit);
        }
    }

    private boolean hasMdrsChanges(Commit commit) {
        BranchCriteria criteria = versionControlHelper.getBranchCriteriaChangesAndDeletionsWithinOpenCommitOnly(commit);
        Set<ReferenceSetMember> mdrsMembers = fetchMdrsMembers(criteria);
        return !mdrsMembers.isEmpty();
    }
    private void performUpdate(Commit commit) {
        // Only performUpdate when there are MDRS changes on commit
        if (!hasMdrsChanges(commit)) {
            logger.debug("No MDRS changes found in commit on branch {}", commit.getBranch().getPath());
            return;
        }
        BranchCriteria changesOnBranchCriteria = versionControlHelper.getChangesOnBranchIncludingOpenCommit(commit);
        Set<ReferenceSetMember> allMdrsMembers = fetchMdrsMembers(changesOnBranchCriteria);
        Map<String, String> moduleToEffectiveTime = mapDependentModuleToEffectiveTime(allMdrsMembers);
        Set<String> dependentBranches = resolveAdditionalDependentBranches(moduleToEffectiveTime);

        if (!dependentBranches.isEmpty()) {
            validateBranchesExist(dependentBranches);
            upsertAdditionalDependencies(commit, dependentBranches);
        } else {
            removeAllAdditionalDependencies(commit);
        }
    }

    private Set<ReferenceSetMember> fetchMdrsMembers(BranchCriteria criteria) {
        Set<ReferenceSetMember> members = new HashSet<>();
        try (var searchResults = elasticsearchOperations.searchForStream(
                new NativeQueryBuilder()
                        .withQuery(bool(b -> b
                                .must(criteria.getEntityBranchCriteria(ReferenceSetMember.class))
                                .must(termQuery(ACTIVE, true))
                                .must(termQuery(ReferenceSetMember.Fields.REFSET_ID, Concepts.MODULE_DEPENDENCY_REFERENCE_SET)))
                        )
                        .withPageable(LARGE_PAGE)
                        .build(), ReferenceSetMember.class)) {
            searchResults.forEachRemaining(hit -> members.add(hit.getContent()));
        }
        return members;
    }

    private Map<String, String> mapDependentModuleToEffectiveTime(Set<ReferenceSetMember> members) {
        Map<String, String> moduleToTime = new HashMap<>();
        members.stream()
                .filter(ReferenceSetMember::isActive)
                .forEach(member -> moduleToTime.put(member.getReferencedComponentId(), member.getAdditionalField(ReferenceSetMember.MDRSFields.TARGET_EFFECTIVE_TIME)));
        return moduleToTime;
    }

    private Set<String> resolveAdditionalDependentBranches(Map<String, String> moduleToEffectiveTime) {
        Map<String, String> codeSystemsByModule = moduleDependencyService.getCodeSystemBranchByModuleId(moduleToEffectiveTime.keySet());
        Set<String> dependentBranches = new HashSet<>();
        codeSystemsByModule.forEach((module, branch) -> {
            if (!"MAIN".equals(branch)) {
                dependentBranches.add(branch + PathUtil.SEPARATOR + formatEffectiveTime(moduleToEffectiveTime.get(module)));
            }
        });
        return dependentBranches;
    }

    private void validateBranchesExist(Set<String> branches) {
        branches.forEach(branch -> {
            if (!branchService.exists(branch)) {
                throw new IllegalStateException("Failed to find versioned branch " + branch);
            }
        });
    }

   private static String formatEffectiveTime(String effectiveTime) {
        DateTimeFormatter inputFormat = DateTimeFormatter.ofPattern("yyyyMMdd");
        DateTimeFormatter outputFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        LocalDate date = LocalDate.parse(effectiveTime, inputFormat);
        return date.format(outputFormat);
    }

    private void removeAllAdditionalDependencies(Commit commit) {
        Metadata newMetadata = commit.getBranch().getMetadata();
        if (newMetadata.containsKey(VersionControlHelper.ADDITIONAL_DEPENDENT_BRANCHES)) {
            newMetadata.remove(VersionControlHelper.ADDITIONAL_DEPENDENT_BRANCHES);
            branchService.updateMetadata(commit.getBranch().getPath(), newMetadata);
        }
    }

    private void upsertAdditionalDependencies(Commit commit, Set<String> dependentVersionBranches) {
        Map<String, Object> metadataToInsert = new HashMap<>();
        metadataToInsert.put(VersionControlHelper.ADDITIONAL_DEPENDENT_BRANCHES, dependentVersionBranches);
        Metadata newMetadata = commit.getBranch().getMetadata();
        newMetadata.putAll(metadataToInsert);
        // Need to create a new branch and update branch metadata avoid caching
        branchService.updateMetadata(commit.getBranch().getPath(), newMetadata);
    }
}
