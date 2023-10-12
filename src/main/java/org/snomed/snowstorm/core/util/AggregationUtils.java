package org.snomed.snowstorm.core.util;

import co.elastic.clients.elasticsearch._types.aggregations.Buckets;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsAggregate;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import org.snomed.snowstorm.core.data.services.pojo.SimpleAggregation;
import org.springframework.data.elasticsearch.client.elc.Aggregation;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregation;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregations;
import org.springframework.data.elasticsearch.core.AggregationsContainer;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class AggregationUtils {

    public static Map<String, Aggregation> getAggregations(AggregationsContainer<?> aggregations) {
        if (aggregations != null) {
            return ((ElasticsearchAggregations) aggregations).aggregations().stream()
                    .map(ElasticsearchAggregation::aggregation)
                    .collect(Collectors.toMap(Aggregation::getName, Function.identity()));
        }
        return Collections.emptyMap();
    }

    public static List<Aggregation> getAggregations(AggregationsContainer<?> aggregations, String aggregationName) {
        if (aggregations != null) {
            return ((ElasticsearchAggregations) aggregations).aggregations().stream()
                    .map(ElasticsearchAggregation::aggregation)
                    .filter(a -> a.getName().equals(aggregationName)).toList();
        }
        return Collections.emptyList();
    }


    public static Map<String, Map<String, Long>> createBuckets(List<Aggregation> aggregations) {
        Map<String, Map<String, Long>> bucketAggregations = new HashMap<>();
        if (aggregations == null) {
            return bucketAggregations;
        }
        Map<String, Aggregation> aggregationMap = aggregations.stream().collect(Collectors.toMap(Aggregation::getName, Function.identity()));
        for (String aggregationGroup : aggregationMap.keySet()) {
            Aggregation aggregation = aggregationMap.get(aggregationGroup);
            if (aggregation instanceof SimpleAggregation simpleAggregation) {
                bucketAggregations.put(simpleAggregation.getName(), simpleAggregation.getBuckets());
            } else {
                HashMap<String, Long> values = new HashMap<>();
                bucketAggregations.put(aggregationGroup, values);
                StringTermsAggregate parsedStringTerms = aggregation.getAggregate().sterms();
                Buckets<StringTermsBucket> buckets = parsedStringTerms.buckets();
                buckets.array().forEach(bucket -> values.put(bucket.key().stringValue(), bucket.docCount()));
            }
        }
        return bucketAggregations;
    }

}
