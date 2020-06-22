package org.snomed.snowstorm.core.data.services.pojo;

import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.search.aggregations.Aggregation;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SimpleAggregation implements Aggregation {

	private final String name;
	private Map<String, Long> buckets = new ConcurrentHashMap <>();

	public SimpleAggregation(String name) {
		this.name = name;
	}
	public SimpleAggregation(String name, String bucket, long count) {
		this(name);
		buckets.put(bucket, count);
	}

	@Override
	public String getName() {
		return name;
	}

	public Map<String, Long> getBuckets() {
		return buckets;
	}

	public void addBucket(String bucket, long count) {
		buckets.put(bucket, count);
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
