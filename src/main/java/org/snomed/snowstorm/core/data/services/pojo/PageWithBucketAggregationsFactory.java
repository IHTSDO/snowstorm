package org.snomed.snowstorm.core.data.services.pojo;

import co.elastic.clients.elasticsearch._types.aggregations.Buckets;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsAggregate;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.client.elc.Aggregation;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregation;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregations;
import org.springframework.data.elasticsearch.core.AggregationsContainer;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;


public class PageWithBucketAggregationsFactory {

	public static <T> PageWithBucketAggregations<T> createPage(SearchHits<T> searchHits, AggregationsContainer<?> aggregations, Pageable pageable) {
		Object[] searchAfter = null;
		if (!searchHits.isEmpty()) {
			searchAfter = searchHits.getSearchHit(searchHits.getSearchHits().size()-1).getSortValues().toArray();
		}
		Map<String, Map<String, Long>> buckets = createBuckets(aggregations);
		return new PageWithBucketAggregations<>(searchHits.get().map(SearchHit::getContent).collect(Collectors.toList()), pageable, searchHits.getTotalHits(), buckets, searchAfter);
	}

	public static <T> PageWithBucketAggregations<T> createPage(SearchHits<T> searchHits, List<Aggregation> aggregations, Pageable pageable) {
		Object[] searchAfter = null;
		if (!searchHits.isEmpty()) {
			searchAfter = searchHits.getSearchHit(searchHits.getSearchHits().size()-1).getSortValues().toArray();
		}
		Map<String, Map<String, Long>> buckets = createBuckets(aggregations);
		return new PageWithBucketAggregations<>(searchHits.get().map(SearchHit::getContent).collect(Collectors.toList()), pageable, searchHits.getTotalHits(), buckets, searchAfter);
	}

	public static <T> PageWithBucketAggregations<T> createPage(SearchHits<T> searchHits, Pageable pageable) {
		Object[] searchAfter = null;
		if (!searchHits.isEmpty()) {
			searchAfter = searchHits.getSearchHit(searchHits.getSearchHits().size()-1).getSortValues().toArray();
		}
		Map<String, Map<String, Long>> buckets = new HashMap<>();
		if (searchHits.hasAggregations()) {
			buckets = createBuckets(searchHits.getAggregations());
		}
		return new PageWithBucketAggregations<>(searchHits.get().map(SearchHit::getContent).collect(Collectors.toList()), pageable, searchHits.getTotalHits(), buckets, searchAfter);
	}


	private static Map<String, Map<String, Long>> createBuckets(AggregationsContainer<?> aggregations) {
		Map<String, Map<String, Long>> bucketAggregations = new HashMap<>();
		if (aggregations == null) {
			return bucketAggregations;
		}
		Map<String, Aggregation> aggregationMap = ((ElasticsearchAggregations) aggregations).aggregations().stream()
				.map(ElasticsearchAggregation::aggregation)
				.collect(Collectors.toMap(Aggregation::getName, Function.identity()));
		constructBuckets(aggregationMap, bucketAggregations);
		return bucketAggregations;
	}

	private static void constructBuckets(Map<String, Aggregation> aggregationMap, Map<String, Map<String, Long>> bucketAggregations) {
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
	}


	private static Map<String, Map<String, Long>> createBuckets(List<Aggregation> aggregations) {
		Map<String, Map<String, Long>> bucketAggregations = new HashMap<>();
		if (aggregations == null) {
			return bucketAggregations;
		}
		Map<String, Aggregation> aggregationMap = aggregations.stream().collect(Collectors.toMap(Aggregation::getName, Function.identity()));
		constructBuckets(aggregationMap, bucketAggregations);
		return bucketAggregations;
	}
}
