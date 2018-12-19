package org.snomed.snowstorm.core.data.services.pojo;

import com.fasterxml.jackson.annotation.JsonView;
import org.snomed.snowstorm.core.data.domain.ConceptMini;
import org.snomed.snowstorm.rest.View;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;

public class PageWithBucketAggregations<T> extends PageImpl<T> {

	private Map<String, Map<String, Long>> buckets;
	private Map<String, ConceptMini> bucketConcepts;

	public PageWithBucketAggregations(List<T> content, Pageable pageable, long total, Map<String, Map<String, Long>> buckets) {
		super(content, pageable, total);
		this.buckets = buckets;
	}

	@JsonView(value = View.Component.class)
	public Map<String, Map<String, Long>> getBuckets() {
		return buckets;
	}

	public void setBucketConcepts(Map<String, ConceptMini> bucketConcepts) {
		this.bucketConcepts = bucketConcepts;
	}

	@JsonView(value = View.Component.class)
	public Map<String, ConceptMini> getBucketConcepts() {
		return bucketConcepts;
	}
}
