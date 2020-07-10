package org.snomed.snowstorm.core.data.services.pojo;

import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedStringTerms;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class PageWithBucketAggregationsFactory {

	public static <T> PageWithBucketAggregations<T> createPage(SearchHits<T> searchHits, Aggregations aggregations, Pageable pageable) {
		Map<String, Map<String, Long>> buckets = createBuckets(aggregations);
		return new PageWithBucketAggregations<>(searchHits.get().map(SearchHit::getContent).collect(Collectors.toList()), pageable, searchHits.getTotalHits(), buckets);
	}

	public static <T> PageWithBucketAggregations<T> createPage(SearchHits<T> searchHits, Pageable pageable) {
		Map<String, Map<String, Long>> buckets = new HashMap<>();
		if (searchHits.hasAggregations()) {
			buckets = createBuckets(searchHits.getAggregations());
		}
		return new PageWithBucketAggregations<>(searchHits.get().map(SearchHit::getContent).collect(Collectors.toList()), pageable, searchHits.getTotalHits(), buckets);
	}

	private static Map<String, Map<String, Long>> createBuckets(Aggregations aggregations) {
		Map<String, Map<String, Long>> bucketAggregations = new HashMap<>();
		if (aggregations == null) {
			return bucketAggregations;
		}
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
