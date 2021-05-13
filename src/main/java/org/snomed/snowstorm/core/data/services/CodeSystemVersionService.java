package org.snomed.snowstorm.core.data.services;

import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.domain.Branch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.core.data.domain.CodeSystem;
import org.snomed.snowstorm.core.data.domain.CodeSystemVersion;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.elasticsearch.index.query.QueryBuilders.termQuery;

@Component
public class CodeSystemVersionService {
    private static final Logger LOGGER = LoggerFactory.getLogger(CodeSystemVersionService.class);
    private static final String SNOMEDCT = "SNOMEDCT";

    /*
     * K => CodeSystemVersion.toString()
     * V => CodeSystemVersion.getDependantVersionEffectiveTime()
     * */
    private final Map<String, Integer> dependantVersionCache = new HashMap<>();

    @Autowired
    private CodeSystemService codeSystemService;

    @Autowired
    private BranchService branchService;

    @Autowired
    private ElasticsearchOperations elasticsearchOperations;

    /**
     * Populate cache with the version of SCT the latest version
     * of the CodeSystem depends on.
     *
     * @param codeSystems The CodeSystems to process.
     */
    public void initDependantVersionCache(List<CodeSystem> codeSystems) {
        for (CodeSystem codeSystem : codeSystems) {
            CodeSystemVersion latestVersion = codeSystem.getLatestVersion();
            if (latestVersion == null || SNOMEDCT.equals(latestVersion.getShortName())) {
                continue;
            }
            this.doGetDependantVersionForCodeSystemVersion(latestVersion)
                .ifPresent(dependantVersion -> dependantVersionCache.putIfAbsent(latestVersion.toString(), dependantVersion));
        }
    }

    /**
     * Clear cache.
     */
    public void clearCache() {
        LOGGER.info("Clearing cache 'dependantVersionCache'.");
        this.dependantVersionCache.clear();
    }

    /**
     * Get the version of SCT the CodeSystemVersion depends on.
     *
     * @param codeSystemVersion The CodeSystemVersion to process.
     * @return The version of SCT the CodeSystemVersion depends on.
     */
    public Optional<Integer> getDependantVersionForCodeSystemVersion(CodeSystemVersion codeSystemVersion) {
        if (codeSystemVersion == null) {
            return Optional.empty();
        }

        String shortName = codeSystemVersion.getShortName();
        if (SNOMEDCT.equals(shortName)) {
            return Optional.empty();
        }

        LOGGER.debug("Looking for {}'s dependency version.", shortName);
        String codeSystemVersionString = codeSystemVersion.toString();
        Integer dependantVersion = dependantVersionCache.get(codeSystemVersionString);
        if (dependantVersion == null) {
            LOGGER.debug("CodeSystemVersion '{}' not found in cache.", codeSystemVersionString);
            Optional<Integer> optionalDependency = this.doGetDependantVersionForCodeSystemVersion(codeSystemVersion);
            if (optionalDependency.isEmpty()) {
                LOGGER.debug("No dependency version found for '{}'.", codeSystemVersionString);
                return Optional.empty();
            }

            LOGGER.debug("Adding '{}' to cache.", codeSystemVersionString);
            dependantVersion = optionalDependency.get();
            dependantVersionCache.put(codeSystemVersionString, dependantVersion);
        }

        return Optional.of(dependantVersion);
    }

    private Optional<Integer> doGetDependantVersionForCodeSystemVersion(CodeSystemVersion codeSystemVersion) {
        //International has been skipped at this point.
        String shortName = codeSystemVersion.getShortName();

        /*
         * Get the base timestamp of branch associated with CodeSystemVersion
         * */
        long targetBaseTimestamp = branchService.findBranchOrThrow(codeSystemVersion.getBranchPath()).getBaseTimestamp();

        /*
         * Find (grand) parent branch, where the head matches base
         * */
        List<Branch> targetBranches;
        Branch targetBranch = null;
        String targetBranchPath = codeSystemVersion.getParentBranchPath();
        boolean hasIntermediateDependency = !targetBranchPath.equals("MAIN/" + shortName);
        Pageable pageable = Pageable.unpaged();
        //Edge case where CodeSystem does not directly depend on International, i.e. AR and UY
        if (hasIntermediateDependency) {
            targetBranches = branchService.findAllVersions(targetBranchPath, pageable).getContent();
            for (Branch branch : targetBranches) {
                if (targetBaseTimestamp == branch.getHeadTimestamp()) {
                    targetBaseTimestamp = branch.getBaseTimestamp();
                    targetBranch = branch;
                    break;
                }
            }
            if (targetBranch == null) {
                return Optional.empty();
            }
        }

        targetBranches = branchService.findAllVersions(targetBranchPath, pageable).getContent();
        for (Branch branch : targetBranches) {
            if (targetBaseTimestamp == branch.getHeadTimestamp()) {
                targetBranch = branch;
                break;
            }
        }
        if (targetBranch == null) {
            return Optional.empty();
        }

        /*
         * Get CodeSystemVersion associated with release branch
         * */
        CodeSystem codeSystem = codeSystemService.find(shortName);
        String path = targetBranch.getPath();
        codeSystemService.setDependantVersionEffectiveTime(codeSystem, path, targetBranch); // Required for finding CodeSystemVersion
        String hyphenatedVersionString = getHyphenatedVersionString(codeSystem.getDependantVersionEffectiveTime());
        if (hyphenatedVersionString == null) {
            return Optional.empty();
        }

        List<CodeSystemVersion> codeSystemVersions = elasticsearchOperations
                .search(
                        new NativeSearchQueryBuilder()
                                .withQuery(termQuery(CodeSystemVersion.Fields.VERSION, hyphenatedVersionString))
                                .build(),
                        CodeSystemVersion.class
                )
                .get()
                .map(SearchHit::getContent)
                .collect(Collectors.toList());

        if (!codeSystemVersions.isEmpty()) {
            return Optional.of(codeSystemVersions.get(0).getEffectiveDate());
        }

        return Optional.empty();
    }

    private String getHyphenatedVersionString(Integer effectiveDate) {
        if (effectiveDate == null) {
            return null;
        }

        String effectiveDateString = effectiveDate.toString();
        return effectiveDateString.substring(0, 4) + "-" + effectiveDateString.substring(4, 6) + "-" + effectiveDateString.substring(6, 8);
    }
}
