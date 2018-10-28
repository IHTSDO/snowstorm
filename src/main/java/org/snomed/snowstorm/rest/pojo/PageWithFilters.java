package org.snomed.snowstorm.rest.pojo;

import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedStringTerms;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.snomed.snowstorm.rest.converter.AggregationNameConverter;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.core.aggregation.AggregatedPage;

import java.io.IOException;
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
			Aggregation aggregation = aggregationMap.get(aggregationGroup);
			if (aggregation instanceof SimpleAggregation) {
				SimpleAggregation simpleAggregation = (SimpleAggregation) aggregation;
				filters.put(simpleAggregation.getName(), simpleAggregation.getBuckets());
			} else {
				HashMap<String, Long> values = new HashMap<>();
				filters.put(aggregationGroup, values);
				AggregationNameConverter aggNameConverter = null;
				for (AggregationNameConverter nameConverter : nameConverters) {
					if (nameConverter.canConvert(aggregationGroup)) {
						aggNameConverter = nameConverter;
					}
				}
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
		}
		return filters;
	}

	public Map<String, Map<String, Long>> getFilters() {
		return filters;
	}

	public static class SimpleAggregation implements Aggregation {

		private final String name;
		private final String bucket;
		private final long count;

		public SimpleAggregation(String name, String bucket, long count) {
			this.name = name;
			this.bucket = bucket;
			this.count = count;
		}

		@Override
		public String getName() {
			return name;
		}

		public Map<String, Long> getBuckets() {
			Map<String, Long> buckets = new HashMap<>();
			buckets.put(bucket, count);
			return buckets;
		}

		@Override
		public String getType() {
			return null;
		}

		@Override
		public Map<String, Object> getMetaData() {
			return null;
		}

		@Override
		public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
			return null;
		}
	}
}
