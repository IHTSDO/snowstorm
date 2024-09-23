package org.snomed.snowstorm.core.rf2.rf2import;

import co.elastic.clients.elasticsearch._types.aggregations.*;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.SourceConfig;
import co.elastic.clients.json.JsonData;
import io.kaicode.elasticvc.api.BranchCriteria;
import io.kaicode.elasticvc.api.VersionControlHelper;
import io.kaicode.elasticvc.domain.DomainEntity;
import io.kaicode.elasticvc.helper.SortBuilders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.util.AggregationUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders.bool;
import static io.kaicode.elasticvc.api.ComponentService.LARGE_PAGE;
import static io.kaicode.elasticvc.helper.QueryHelper.existsQuery;
import static io.kaicode.elasticvc.helper.QueryHelper.termQuery;
import static org.snomed.snowstorm.core.data.domain.ReferenceSetMember.Fields.REFSET_ID;
import static org.snomed.snowstorm.core.data.domain.SnomedComponent.Fields.EFFECTIVE_TIME;

@Service
public class MostRecentEffectiveTimeFinder {
    private static final Logger logger = LoggerFactory.getLogger(MostRecentEffectiveTimeFinder.class);

    private final ElasticsearchOperations elasticsearchOperations;

    private final VersionControlHelper versionControlHelper;

    public MostRecentEffectiveTimeFinder(ElasticsearchOperations elasticsearchOperations, VersionControlHelper versionControlHelper) {
        this.elasticsearchOperations = elasticsearchOperations;
        this.versionControlHelper = versionControlHelper;
    }

    /**
     * Find the most recent effective time for each module on a given branch.
     * @param branchPath branch path
     * @param searchMDRSOnly Set it to true to search refset members with refset id 900000000000534007 only to find the most recent effective time.
     *                       Otherwise, it will run aggregations on all components.
     * @return Map of module id to effective time
     */
    public Map<String, Integer> getEffectiveTimeByModuleId(String branchPath, boolean searchMDRSOnly) {
        if (searchMDRSOnly) {
            return getEffectiveTimeByModuleIdFromMDRS(branchPath);
        }
        Map<Class<? extends DomainEntity<?>>, Map<String, Integer>> latestEffectiveTimeByModuleId = findLatestEffectiveTimeByModuleId(branchPath);
        // Get values from latestEffectiveTimeByModuleId map and return most recent effective time for each module in a single map
        Map<String, Integer> effectiveTimeByModuleId = new HashMap<>();
        if (latestEffectiveTimeByModuleId.isEmpty()) {
            return effectiveTimeByModuleId;
        }
        for (Map<String, Integer> latestEffectiveTimeForComponent : latestEffectiveTimeByModuleId.values()) {
            for (Map.Entry<String, Integer> entry : latestEffectiveTimeForComponent.entrySet()) {
                String moduleId = entry.getKey();
                Integer effectiveTime = entry.getValue();
                if (effectiveTimeByModuleId.containsKey(moduleId)) {
                    Integer existingEffectiveTime = effectiveTimeByModuleId.get(moduleId);
                    if (effectiveTime > existingEffectiveTime) {
                        effectiveTimeByModuleId.put(moduleId, effectiveTime);
                    }
                } else {
                    effectiveTimeByModuleId.put(moduleId, effectiveTime);
                }
            }
        }
        return effectiveTimeByModuleId;
    }


    /**
     * Find the most recent effective time for each module on a given branch by searching MDRS refset members only.
     * @param branchPath branch path
     * @return Map of module id to effective time
     */

    private Map<String, Integer> getEffectiveTimeByModuleIdFromMDRS(String branchPath) {
        NativeQuery query = new NativeQueryBuilder()
                .withQuery(bool(b -> b
                        .must(versionControlHelper.getBranchCriteria(branchPath).getEntityBranchCriteria(ReferenceSetMember.class))
                        .must(termQuery(REFSET_ID, Concepts.MODULE_DEPENDENCY_REFERENCE_SET))
                        .must(existsQuery(EFFECTIVE_TIME))
                ))
                .withSort(SortBuilders.fieldSortDesc(EFFECTIVE_TIME))
                .withPageable(LARGE_PAGE)
                .build();
        query.setTrackTotalHits(true);
        SearchHits<ReferenceSetMember> results = elasticsearchOperations.search(query, ReferenceSetMember.class);
        Map<String, Integer> effectiveTimeByModuleId = new HashMap<>();
        if (results.hasSearchHits()) {
            logger.info("Processing {} MDRS refset results on branch {} to find most recent effective time by module id", results.getTotalHits(), branchPath);
            results.getSearchHits().forEach(hit -> {
                ReferenceSetMember referenceSetMember = hit.getContent();
                String moduleId = referenceSetMember.getModuleId();
                Integer effectiveTime = referenceSetMember.getEffectiveTimeI();
                if (moduleId != null && effectiveTime != null) {
                    effectiveTimeByModuleId.putIfAbsent(moduleId, effectiveTime);
                }
            });
        }
        if (!effectiveTimeByModuleId.isEmpty() && effectiveTimeByModuleId.get(Concepts.CORE_MODULE) != null) {
               // There is no MRDS refet member entry with module id as model module
               // But we can add model module effective time the same as core module's
                effectiveTimeByModuleId.put(Concepts.MODEL_MODULE, effectiveTimeByModuleId.get(Concepts.CORE_MODULE));
        }
        return effectiveTimeByModuleId;
    }


    private Map<Class<? extends DomainEntity<?>>, Map<String, Integer>> findLatestEffectiveTimeByModuleId(String branchPath) {
        Map<Class<? extends DomainEntity<?>>, Map<String, Integer>> latestEffectiveTimeByModuleId = new HashMap<>();
        BranchCriteria branchCriteria = versionControlHelper.getBranchCriteria(branchPath);
        // Run aggregations to find the latest effectiveTime for each module on a given branch
        List<Class<? extends DomainEntity<?>>> componentClasses = Arrays.asList(Concept.class, Description.class, Relationship.class, ReferenceSetMember.class, Identifier.class);
        String latestEffectiveTime = "latest_effective_time";
        String byModule = "by_module";

        for (Class<? extends DomainEntity<?>> componentClass : componentClasses) {
            Aggregation termsAggregation = new Aggregation.Builder().terms(AggregationBuilders.terms().field(SnomedComponent.Fields.MODULE_ID).size(500).build()._toAggregation().terms())
                    .aggregations(latestEffectiveTime, AggregationBuilders.topHits().source(SourceConfig.of(sc -> sc
                                    .filter(f -> f.includes(SnomedComponent.Fields.MODULE_ID, EFFECTIVE_TIME))))
                            .sort(SortBuilders.fieldSortDesc(EFFECTIVE_TIME)).size(1).build()._toAggregation()).build();

            // We could add .must(termQuery(SnomedComponent.Fields.PATH, branchCriteria.getBranchPath())) to limit the search to the branch only
            // but it won't work for edition snapshot
            NativeQuery query = new NativeQueryBuilder()
                    .withQuery(bool(b -> b
                            .must(branchCriteria.getEntityBranchCriteria(componentClass))
                            .must(existsQuery(EFFECTIVE_TIME))
                    ))
                    .withAggregation(byModule, termsAggregation)

                    .withPageable(PageRequest.of(0,1))
                    .build();
            // No need to fetch any documents just aggregations only
            query.setMaxResults(0);
            SearchHits<? extends DomainEntity<?>> results = elasticsearchOperations.search(query, componentClass);
            Map<String, Integer> latestEffectiveTimeByModuleIdForComponent = new HashMap<>();
            if (!results.hasAggregations()) {
                continue;
            }
            logger.info("Processing latest effectiveTime by moduleId aggregation results for component: {}", componentClass.getSimpleName());
            AggregationUtils.getAggregations(results.getAggregations()).get(byModule).getAggregate().sterms().buckets().array().forEach(bucket -> {
                String moduleId = bucket.key().stringValue();
                if (bucket.docCount() > 0 && bucket.aggregations().containsKey(latestEffectiveTime)) {
                    TopHitsAggregate topHitsAggregate = bucket.aggregations().get(latestEffectiveTime).topHits();
                    Integer mostRecent = getEffectiveTimeFromTopHits(topHitsAggregate);
                    if (mostRecent != null) {
                        logger.info("Latest effectiveTime: {} found for module id: {}",mostRecent, moduleId);
                        latestEffectiveTimeByModuleIdForComponent.put(moduleId, mostRecent);
                    }
                }
            });

            if (!latestEffectiveTimeByModuleIdForComponent.isEmpty()) {
                latestEffectiveTimeByModuleId.put(componentClass, latestEffectiveTimeByModuleIdForComponent);
            }
        }
        return latestEffectiveTimeByModuleId;
    }


    private Integer getEffectiveTimeFromTopHits(TopHitsAggregate topHitsAggregate) {
        if (topHitsAggregate == null || topHitsAggregate.hits() == null) {
            return null;
        }
        List<Hit<JsonData>> hits = topHitsAggregate.hits().hits();
        if (hits.isEmpty()) {
            return null;
        }
        Hit<JsonData> firstHit = hits.get(0);
        if (firstHit.source() == null) {
            return null;
        }
        JsonData source = firstHit.source();
        try {
            return source.toJson().asJsonObject().getInt(EFFECTIVE_TIME);
        } catch (Exception e) {
            logger.error("Error parsing effective time from hit: {}", source, e);
            return null;
        }
    }
}
