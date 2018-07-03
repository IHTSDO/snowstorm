package org.snomed.snowstorm.rest.pojo;

import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedStringTerms;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.core.aggregation.AggregatedPage;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PageWithFilters<T> extends PageImpl<T> {

	private Map<String, Map<String, Long>> filters;

	public PageWithFilters(List<T> results, PageRequest pageRequest, long totalElements, Aggregations aggregations) {
		this(results, pageRequest, totalElements, createFilters(aggregations));
	}

	public PageWithFilters(List<T> content, Pageable pageable, long total, Map<String, Map<String, Long>> filters) {
		super(content, pageable, total);
		this.filters = filters;
	}

	public PageWithFilters(AggregatedPage<T> page) {
		this(page.getContent(), page.getPageable(), page.getTotalElements(), createFilters(page.getAggregations()));
	}

	public static Map<String, Map<String, Long>> createFilters(Aggregations aggregations) {
		Map<String, Map<String, Long>> filters = new HashMap<>();
		Map<String, Aggregation> aggregationMap = aggregations.getAsMap();
		for (String key : aggregationMap.keySet()) {
			HashMap<String, Long> values = new HashMap<>();
			filters.put(key, values);
			Aggregation aggregation = aggregationMap.get(key);
			if (aggregation instanceof ParsedStringTerms) {
				ParsedStringTerms termsBucketAggregation = (ParsedStringTerms) aggregation;
				for (Terms.Bucket bucket : termsBucketAggregation.getBuckets()) {
					values.put(bucket.getKeyAsString(), bucket.getDocCount());
				}
			}
		}
		return filters;
	}

	public Map<String, Map<String, Long>> getFilters() {
		return filters;
	}
}
