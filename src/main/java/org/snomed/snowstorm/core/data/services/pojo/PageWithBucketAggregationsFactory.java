package org.snomed.snowstorm.core.data.services.pojo;

import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedStringTerms;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.springframework.data.elasticsearch.core.aggregation.AggregatedPage;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PageWithBucketAggregationsFactory {

	public static <T> PageWithBucketAggregations<T> createPage(AggregatedPage<T> aggregatedPage, List<Aggregation> allAggregations) {
		Map<String, Map<String, Long>> buckets = createBuckets(new Aggregations(allAggregations));
		return new PageWithBucketAggregations<>(aggregatedPage.getContent(), aggregatedPage.getPageable(), aggregatedPage.getTotalElements(), buckets);
	}

	private static Map<String, Map<String, Long>> createBuckets(Aggregations aggregations) {
		Map<String, Map<String, Long>> bucketAggregations = new HashMap<>();
		Map<String, Aggregation> aggregationMap = aggregations.getAsMap();
		for (String aggregationGroup : aggregationMap.keySet()) {
			Aggregation aggregation = aggregationMap.get(aggregationGroup);
			if (aggregation instanceof SimpleAggregation) {
				SimpleAggregation simpleAggregation = (SimpleAggregation) aggregation;
				bucketAggregations.put(simpleAggregation.getName(), simpleAggregation.getBuckets());
			} else {
				HashMap<String, Long> values = new HashMap<>();
				bucketAggregations.put(aggregationGroup, values);
				if (aggregation instanceof ParsedStringTerms) {
					ParsedStringTerms termsBucketAggregation = (ParsedStringTerms) aggregation;
					for (Terms.Bucket bucket : termsBucketAggregation.getBuckets()) {
						String aggregationBucketName = bucket.getKeyAsString();
						values.put(aggregationBucketName, bucket.getDocCount());
					}
				}
			}
		}
		return bucketAggregations;
	}
}
