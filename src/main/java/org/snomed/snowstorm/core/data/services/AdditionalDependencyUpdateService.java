package org.snomed.snowstorm.core.data.services;

import io.kaicode.elasticvc.api.*;
import io.kaicode.elasticvc.domain.Commit;
import io.kaicode.elasticvc.domain.Metadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.core.data.domain.ReferenceSetMember;
import org.snomed.snowstorm.core.util.SPathUtil;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static io.kaicode.elasticvc.domain.Commit.CommitType.CONTENT;
import static io.kaicode.elasticvc.domain.Commit.CommitType.REBASE;

@Service
public class AdditionalDependencyUpdateService extends ComponentService implements CommitListener {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final VersionControlHelper versionControlHelper;
    private final ModuleDependencyService moduleDependencyService;
    private final BranchService branchService;

    public AdditionalDependencyUpdateService(VersionControlHelper versionControlHelper, ModuleDependencyService moduleDependencyService, BranchService branchService) {
        this.versionControlHelper = versionControlHelper;
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
        Set<ReferenceSetMember> mdrsMembers = moduleDependencyService.fetchMdrsMembers(criteria);
        return !mdrsMembers.isEmpty();
    }
    private void performUpdate(Commit commit) {
        // Only performUpdate when there are MDRS changes on commit
        if (!hasMdrsChanges(commit)) {
            logger.debug("No MDRS changes found in commit on branch {}", commit.getBranch().getPath());
            return;
        }
        BranchCriteria changesOnBranchCriteria = versionControlHelper.getChangesOnBranchIncludingOpenCommit(commit);
        Set<ReferenceSetMember> allMdrsMembers = moduleDependencyService.fetchMdrsMembers(changesOnBranchCriteria);
        Map<String, String> moduleToEffectiveTime = mapDependentModuleToEffectiveTime(allMdrsMembers);
        Set<String> dependentBranches = resolveAdditionalDependentBranches(commit.getBranch().getPath(), moduleToEffectiveTime);

        if (!dependentBranches.isEmpty()) {
            validateBranchesExist(dependentBranches);
            upsertAdditionalDependencies(commit, dependentBranches);
        } else {
            removeAllAdditionalDependencies(commit);
        }
    }

    private Map<String, String> mapDependentModuleToEffectiveTime(Set<ReferenceSetMember> members) {
        Map<String, String> moduleToTime = new HashMap<>();
        members.stream()
                .filter(ReferenceSetMember::isActive)
                .forEach(member -> {
                    String effectiveTime = member.getAdditionalField(ReferenceSetMember.MDRSFields.TARGET_EFFECTIVE_TIME);
                    if (effectiveTime != null && !effectiveTime.trim().isEmpty()) {
                        moduleToTime.put(member.getReferencedComponentId(), effectiveTime);
                    }
                });
        return moduleToTime;
    }

    private Set<String> resolveAdditionalDependentBranches(String currentCodeSystemBranchPath, Map<String, String> moduleToEffectiveTime) {
        Map<String, String> codeSystemsByModule = moduleDependencyService.getCodeSystemBranchByModuleId(moduleToEffectiveTime.keySet());
        Set<String> dependentBranches = new HashSet<>();
        codeSystemsByModule.forEach((module, branch) -> {
            if (!"MAIN".equals(branch) && !currentCodeSystemBranchPath.equals(branch)) {
                String effectiveTime = moduleToEffectiveTime.get(module);
                if (effectiveTime != null && !effectiveTime.trim().isEmpty()) {
                    dependentBranches.add(branch + PathUtil.SEPARATOR + formatEffectiveTime(effectiveTime));
                }
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
        if (effectiveTime == null || effectiveTime.trim().isEmpty()) {
            throw new IllegalArgumentException("Effective time cannot be null or empty");
        }
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
