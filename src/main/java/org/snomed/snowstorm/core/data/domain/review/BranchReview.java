package org.snomed.snowstorm.core.data.domain.review;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.*;

import jakarta.validation.constraints.NotNull;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

@Document(indexName = "#{@indexNameProvider.indexName('branch-review')}")
@Setting(settingPath = "elasticsearch-settings.json")
public class BranchReview {

	@Id
	@Field(type = FieldType.Keyword)
	@NotNull
	private String id;

	@Field(type = FieldType.Boolean)
	private boolean sourceIsParent;

	@Field(type = FieldType.Date, format = DateFormat.date_optional_time)
	private Date lastUpdated;

	@Field(type = FieldType.Keyword)
	private ReviewStatus status;

	@Field(type = FieldType.Nested)
	private BranchState source;

	@Field(type = FieldType.Nested)
	private BranchState target;

	private Set<Long> changedConcepts;

	public BranchReview() {
	}

	public BranchReview(String id, Date lastUpdated, ReviewStatus status, BranchState source, BranchState target, boolean sourceIsParent) {
		this.id = id;
		this.lastUpdated = lastUpdated;
		this.status = status;
		this.source = source;
		this.target = target;
		this.sourceIsParent = sourceIsParent;
	}

	public String getId() {
		return id;
	}

	public Date getLastUpdated() {
		return lastUpdated;
	}

	public ReviewStatus getStatus() {
		return status;
	}

	public void setStatus(ReviewStatus status) {
		this.status = status;
	}

	public BranchState getSource() {
		return source;
	}

	public BranchState getTarget() {
		return target;
	}

	public boolean isSourceParent() {
		return sourceIsParent;
	}

	public Set<Long> getChangedConcepts() {
		return changedConcepts;
	}

	public void setChangedConcepts(Set<Long> changedConcepts) {
		this.changedConcepts = changedConcepts;
	}

	public void addChangedConcepts(Set<Long> changedConcepts) {
		if (this.changedConcepts == null) {
			this.changedConcepts = new HashSet<>();
		}

		this.changedConcepts.addAll(changedConcepts);
	}
}
