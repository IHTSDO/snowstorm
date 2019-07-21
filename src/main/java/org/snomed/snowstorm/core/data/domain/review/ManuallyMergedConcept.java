package org.snomed.snowstorm.core.data.domain.review;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;

import java.util.Date;

@Document(indexName = "manual-merge-concept")
public class ManuallyMergedConcept {

	@Id
	private String compositeId;
	private String mergeReviewId;
	private Long conceptId;
	private String conceptJson;
	private boolean deleted;
	private Date created;// To find and delete old docs

	public ManuallyMergedConcept() {
	}

	public ManuallyMergedConcept(String mergeReviewId, Long conceptId, String conceptJson, boolean deleted) {
		compositeId = mergeReviewId + "_" + conceptId;
		this.mergeReviewId = mergeReviewId;
		this.conceptId = conceptId;
		this.conceptJson = conceptJson;
		this.deleted = deleted;
		created = new Date();
	}

	public String getCompositeId() {
		return compositeId;
	}

	public String getMergeReviewId() {
		return mergeReviewId;
	}

	public Long getConceptId() {
		return conceptId;
	}

	public String getConceptJson() {
		return conceptJson;
	}

	public boolean isDeleted() {
		return deleted;
	}

	public Date getCreated() {
		return created;
	}
}
