package org.snomed.snowstorm.core.data.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.snomed.snowstorm.core.data.services.ApiError;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.util.Date;
import java.util.UUID;

@Document(indexName = "#{@indexNameProvider.indexName('branch-marge')}", createIndex = false)
public class BranchMergeJob {

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	@Id
	private String id;
	private String source;
	private String target;
	private Date scheduledDate;
	private Date startDate;
	private JobStatus status;
	private Date endDate;
	private String message;

	/**
	 * Stored as a single non-indexed string so nested integrity issue maps cannot explode the
	 * Elasticsearch field limit (each map key would otherwise become a mapped field).
	 * Must be Text (not Keyword) — large integrity payloads can exceed Lucene's 32KB keyword limit.
	 */
	@Field(type = FieldType.Text, index = false)
	private String apiErrorJson;

	public BranchMergeJob() {
	}

	public BranchMergeJob(String source, String target, JobStatus status) {
		id = UUID.randomUUID().toString();
		this.source = source;
		this.target = target;
		scheduledDate = new Date();
		this.status = status;
	}

	public void setStartDate(Date startDate) {
		this.startDate = startDate;
	}

	public void setStatus(JobStatus status) {
		this.status = status;
	}

	public void setEndDate(Date endDate) {
		this.endDate = endDate;
	}

	public String getId() {
		return id;
	}

	public String getSource() {
		return source;
	}

	public String getTarget() {
		return target;
	}

	public Date getStartDate() {
		return startDate;
	}

	public Date getScheduledDate() {
		return scheduledDate;
	}

	public JobStatus getStatus() {
		return status;
	}

	public Date getEndDate() {
		return endDate;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public String getMessage() {
		return message;
	}

	/**
	 * Application/REST accessor. Not an Elasticsearch field — only {@link #apiErrorJson} is persisted.
	 */
	@Transient
	public void setApiError(ApiError apiError) {
		if (apiError == null) {
			this.apiErrorJson = null;
			return;
		}
		try {
			this.apiErrorJson = OBJECT_MAPPER.writeValueAsString(apiError);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Failed to serialise merge apiError for storage", e);
		}
	}

	/**
	 * Application/REST accessor. Deserialises {@link #apiErrorJson} for API responses.
	 */
	@Transient
	public ApiError getApiError() {
		if (apiErrorJson == null) {
			return null;
		}
		try {
			return OBJECT_MAPPER.readValue(apiErrorJson, ApiError.class);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Failed to deserialise merge apiError from storage", e);
		}
	}

	@JsonIgnore
	public String getApiErrorJson() {
		return apiErrorJson;
	}

	@JsonIgnore
	public void setApiErrorJson(String apiErrorJson) {
		this.apiErrorJson = apiErrorJson;
	}
}
