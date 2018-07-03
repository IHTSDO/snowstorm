package org.snomed.snowstorm.rest.pojo;

import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedStringTerms;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.snomed.snowstorm.rest.converter.AggregationNameConverter;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.core.aggregation.AggregatedPage;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PageWithFilters<T> extends PageImpl<T> {

	private Map<String, Map<String, Long>> filters;

	public PageWithFilters(List<T> results, PageRequest pageRequest, long totalElements, Aggregations aggregations, AggregationNameConverter... nameConverters) {
		this(results, pageRequest, totalElements, createFilters(aggregations, nameConverters));
	}

	public PageWithFilters(List<T> content, Pageable pageable, long total, Map<String, Map<String, Long>> filters) {
		super(content, pageable, total);
		this.filters = filters;
	}

	public PageWithFilters(AggregatedPage<T> page) {
		this(page.getContent(), page.getPageable(), page.getTotalElements(), createFilters(page.getAggregations()));
	}

	public static Map<String, Map<String, Long>> createFilters(Aggregations aggregations, AggregationNameConverter... nameConverters) {
		Map<String, Map<String, Long>> filters = new HashMap<>();
		Map<String, Aggregation> aggregationMap = aggregations.getAsMap();
		for (String aggregationGroup : aggregationMap.keySet()) {
			HashMap<String, Long> values = new HashMap<>();
			filters.put(aggregationGroup, values);
			AggregationNameConverter aggNameConverter = null;
			for (AggregationNameConverter nameConverter : nameConverters) {
				if (nameConverter.canConvert(aggregationGroup)) {
					aggNameConverter = nameConverter;
				}
			}
			Aggregation aggregation = aggregationMap.get(aggregationGroup);
			if (aggregation instanceof ParsedStringTerms) {
				ParsedStringTerms termsBucketAggregation = (ParsedStringTerms) aggregation;
				for (Terms.Bucket bucket : termsBucketAggregation.getBuckets()) {
					String aggregationBucketName = bucket.getKeyAsString();
					if (aggNameConverter != null) {
						aggregationBucketName = aggNameConverter.convert(aggregationBucketName);
					}
					values.put(aggregationBucketName, bucket.getDocCount());
				}
			}
		}
		return filters;
	}

	public Map<String, Map<String, Long>> getFilters() {
		return filters;
	}
}
