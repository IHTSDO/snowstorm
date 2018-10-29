package org.snomed.snowstorm.core.data.services.pojo;

import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;

public class PageWithBucketAggregations<T> extends PageImpl<T> {

	private Map<String, Map<String, Long>> buckets;

	public PageWithBucketAggregations(List<T> content, Pageable pageable, long total, Map<String, Map<String, Long>> buckets) {
		super(content, pageable, total);
		this.buckets = buckets;
	}

	public Map<String, Map<String, Long>> getBuckets() {
		return buckets;
	}

}
