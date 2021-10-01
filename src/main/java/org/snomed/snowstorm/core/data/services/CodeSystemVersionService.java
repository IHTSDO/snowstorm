package org.snomed.snowstorm.core.data.services;

import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.api.PathUtil;
import io.kaicode.elasticvc.domain.Branch;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.core.data.domain.CodeSystem;
import org.snomed.snowstorm.core.data.domain.CodeSystemVersion;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

@Component
public class CodeSystemVersionService {
    private static final Logger LOGGER = LoggerFactory.getLogger(CodeSystemVersionService.class);
    private static final String SNOMEDCT = "SNOMEDCT";

    /*
     * K => CodeSystemVersion.getShortName() + "_" + CodeSystemVersion.getEffectiveDate() + "_" + Branch..getHeadTimestamp()
     * V => CodeSystemVersion.getDependantVersionEffectiveTime() or 0
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
     */
    public void initDependantVersionCache(List<CodeSystem> codeSystems) {
        for (CodeSystem codeSystem : codeSystems) {
            CodeSystemVersion latestVersion = codeSystem.getLatestVersion();
            if (latestVersion == null || SNOMEDCT.equals(latestVersion.getShortName())) {
                continue;
            }
			populateDependantVersion(latestVersion);
			String cacheKey = buildKeyForCache(latestVersion, branchService.findLatest(latestVersion.getBranchPath()).getHeadTimestamp());
			final Integer dependantVersion = latestVersion.getDependantVersionEffectiveTime();
			dependantVersionCache.put(cacheKey, dependantVersion != null ? dependantVersion : 0);
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
    public void populateDependantVersion(CodeSystemVersion codeSystemVersion) {
        if (codeSystemVersion == null) {
            return;
        }

        LOGGER.debug("Looking for {}'s dependency version.", codeSystemVersion);
		final String cacheKey = buildKeyForCache(codeSystemVersion, branchService.findLatest(codeSystemVersion.getBranchPath()).getHeadTimestamp());
		Integer dependantVersion = dependantVersionCache.computeIfAbsent(cacheKey, key -> {
			LOGGER.debug("CodeSystemVersion '{}' not found in cache.", codeSystemVersion);
			Optional<Integer> optionalDependency = doGetDependantVersionForCodeSystemVersion(codeSystemVersion);
			if (optionalDependency.isEmpty()) {
				LOGGER.debug("No dependency version found for '{}'.", codeSystemVersion);
			}
			return optionalDependency.orElse(0);
		});

		codeSystemVersion.setDependantVersionEffectiveTime(dependantVersion != 0 ? dependantVersion : null);
    }

    private Optional<Integer> doGetDependantVersionForCodeSystemVersion(CodeSystemVersion codeSystemVersion) {

		if (PathUtil.isRoot(codeSystemVersion.getParentBranchPath())) {
			// Root code system has no dependant version in version control
			return Optional.empty();
		}

        /*
         * Get the base timestamp of branch associated with CodeSystemVersion
         * */
        long targetBaseTimestamp = branchService.findBranchOrThrow(codeSystemVersion.getBranchPath()).getBaseTimestamp();

        /*
         * Find parent branch, where the head matches base
         * */
		final BoolQueryBuilder query = boolQuery()
				.must(termQuery(Branch.Fields.PATH, codeSystemVersion.getParentBranchPath()))
				.must(termQuery("head", targetBaseTimestamp));
		final SearchHit<Branch> targetBranchHit = elasticsearchOperations.searchOne(new NativeSearchQueryBuilder().withQuery(query).build(), Branch.class);
		final Branch versionCommit = targetBranchHit != null ? targetBranchHit.getContent() : null;

        if (versionCommit == null) {
            return Optional.empty();
        }

		final Integer effectiveTime = codeSystemService.getVersionEffectiveTime(
				PathUtil.getParentPath(versionCommit.getPath()), versionCommit.getBase(), codeSystemVersion.getShortName());
		return effectiveTime != null ? Optional.of(effectiveTime) : Optional.empty();
    }

    private String buildKeyForCache(CodeSystemVersion codeSystemVersion, long headTimestamp) {
        return codeSystemVersion.getShortName() + "_" + codeSystemVersion.getEffectiveDate() + "_" + headTimestamp;
    }
}
