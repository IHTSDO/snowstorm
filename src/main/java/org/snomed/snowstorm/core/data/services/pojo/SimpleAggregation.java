package org.snomed.snowstorm.core.data.services.pojo;

import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.search.aggregations.Aggregation;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class SimpleAggregation implements Aggregation {

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
	public Map<String, Object> getMetadata() {
		return null;
	}

	@Override
	public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
		return null;
	}
}
