package org.ihtsdo.elasticsnomed.core.data.domain.classification;

import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldIndex;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.util.Date;

/**
 * Represents an active concept with fields to assist logical searching.
 */
@Document(type = "classification", indexName = "classifications", shards = 8)
public class Classification {

	public enum Status {

		SCHEDULED, RUNNING, FAILED, COMPLETED(true), SAVED(true), SAVE_FAILED(true);

		boolean resultsAvailable;

		Status(boolean resultsAvailable) {
			this.resultsAvailable = resultsAvailable;
		}

		Status() {
			this(false);
		}

		public boolean isResultsAvailable() {
			return resultsAvailable;
		}
	}

	public interface Fields {
		String PATH = "path";
		String STATUS = "status";
		String CREATION_DATE = "creationDate";
	}

	@Field(type = FieldType.String, index = FieldIndex.not_analyzed)
	private String id;

	@Field(type = FieldType.String, index = FieldIndex.not_analyzed)
	private String path;

	@Field(type = FieldType.String, index = FieldIndex.not_analyzed)
	private Status status;

	@Field(type = FieldType.String, index = FieldIndex.not_analyzed)
	private String errorMessage;

	@Field(type = FieldType.String, index = FieldIndex.not_analyzed)
	private String reasonerId;

	@Field(type = FieldType.String, index = FieldIndex.not_analyzed)
	private String userId;

	@Field(type = FieldType.Date, index = FieldIndex.not_analyzed)
	private Date creationDate;

	@Field(type = FieldType.Date, index = FieldIndex.not_analyzed)
	private Date completionDate;

	@Field(type = FieldType.Date, index = FieldIndex.not_analyzed)
	private Date lastCommitDate;

	@Field(type = FieldType.Boolean, index = FieldIndex.not_analyzed)
	private Boolean inferredRelationshipChangesFound;

	@Field(type = FieldType.Boolean, index = FieldIndex.not_analyzed)
	private Boolean redundantStatedRelationshipsFound;

	@Field(type = FieldType.Boolean, index = FieldIndex.not_analyzed)
	private Boolean equivalentConceptsFound;

	public Classification() {
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getPath() {
		return path;
	}

	public void setPath(String path) {
		this.path = path;
	}

	public Status getStatus() {
		return status;
	}

	public void setStatus(Status status) {
		this.status = status;
	}

	public String getErrorMessage() {
		return errorMessage;
	}

	public void setErrorMessage(String errorMessage) {
		this.errorMessage = errorMessage;
	}

	public String getReasonerId() {
		return reasonerId;
	}

	public void setReasonerId(String reasonerId) {
		this.reasonerId = reasonerId;
	}

	public String getUserId() {
		return userId;
	}

	public void setUserId(String userId) {
		this.userId = userId;
	}

	public Date getCreationDate() {
		return creationDate;
	}

	public void setCreationDate(Date creationDate) {
		this.creationDate = creationDate;
	}

	public Date getCompletionDate() {
		return completionDate;
	}

	public void setCompletionDate(Date completionDate) {
		this.completionDate = completionDate;
	}

	public Date getLastCommitDate() {
		return lastCommitDate;
	}

	public void setLastCommitDate(Date lastCommitDate) {
		this.lastCommitDate = lastCommitDate;
	}

	public Boolean getInferredRelationshipChangesFound() {
		return inferredRelationshipChangesFound;
	}

	public void setInferredRelationshipChangesFound(Boolean inferredRelationshipChangesFound) {
		this.inferredRelationshipChangesFound = inferredRelationshipChangesFound;
	}

	public Boolean getRedundantStatedRelationshipsFound() {
		return redundantStatedRelationshipsFound;
	}

	public void setRedundantStatedRelationshipsFound(Boolean redundantStatedRelationshipsFound) {
		this.redundantStatedRelationshipsFound = redundantStatedRelationshipsFound;
	}

	public Boolean getEquivalentConceptsFound() {
		return equivalentConceptsFound;
	}

	public void setEquivalentConceptsFound(Boolean equivalentConceptsFound) {
		this.equivalentConceptsFound = equivalentConceptsFound;
	}
}
