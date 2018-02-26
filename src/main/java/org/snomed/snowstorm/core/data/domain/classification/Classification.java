package org.snomed.snowstorm.core.data.domain.classification;

import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.util.Date;

/**
 * Represents an active concept with fields to assist logical searching.
 */
@Document(indexName = "es-class", type = "classification", shards = 8)
public class Classification {

	public interface Fields {
		String PATH = "path";
		String STATUS = "status";
		String CREATION_DATE = "creationDate";
	}

	@Field(type = FieldType.keyword)
	private String id;

	@Field(type = FieldType.keyword)
	private String path;

	@Field(type = FieldType.keyword)
	private ClassificationStatus status;

	@Field(type = FieldType.keyword)
	private String errorMessage;

	@Field(type = FieldType.keyword)
	private String reasonerId;

	@Field(type = FieldType.keyword)
	private String userId;

	@Field(type = FieldType.Date)
	private Date creationDate;

	@Field(type = FieldType.Date)
	private Date completionDate;

	@Field(type = FieldType.Date)
	private Date lastCommitDate;

	@Field(type = FieldType.Boolean)
	private Boolean inferredRelationshipChangesFound;

	@Field(type = FieldType.Boolean)
	private Boolean redundantStatedRelationshipsFound;

	@Field(type = FieldType.Boolean)
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

	public ClassificationStatus getStatus() {
		return status;
	}

	public void setStatus(ClassificationStatus status) {
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
