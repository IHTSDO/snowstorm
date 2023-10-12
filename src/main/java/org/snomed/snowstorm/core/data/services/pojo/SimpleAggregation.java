package org.snomed.snowstorm.core.data.services.pojo;

import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import org.springframework.data.elasticsearch.client.elc.Aggregation;

import java.util.HashMap;
import java.util.Map;

public class SimpleAggregation extends Aggregation {

	private final String bucket;
	private final long count;

	public SimpleAggregation(String name, String bucket, long count) {
		super(name, Aggregate.of(a -> a.bucketMetricValue(bm -> bm.keys(bucket).value(count))));
		this.bucket = bucket;
		this.count = count;
	}


	public Map<String, Long> getBuckets() {
		Map<String, Long> buckets = new HashMap<>();
		buckets.put(bucket, count);
		return buckets;
	}
}
