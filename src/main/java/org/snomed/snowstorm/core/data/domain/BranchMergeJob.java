package org.snomed.snowstorm.core.data.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.core.data.services.ApiError;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Dynamic;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.util.Date;
import java.util.UUID;

@Document(indexName = "#{@indexNameProvider.indexName('branch-merge')}", createIndex = false, dynamic = Dynamic.FALSE)
public class BranchMergeJob {

	private static final Logger logger = LoggerFactory.getLogger(BranchMergeJob.class);
	// apiErrorJson is persisted in Elasticsearch, and ApiError.additionalInfo is a Map<String, Object>
	// that can hold enums or dates - so this uses the same Jackson 2 defaults as every other mapper.
	private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builderWithJackson2Defaults()
			.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
			.build();

	@Id
	@Field(type = FieldType.Keyword)
	private String id;

	@Field(type = FieldType.Keyword)
	private String source;

	@Field(type = FieldType.Keyword)
	private String target;

	@Field(type = FieldType.Long)
	private Date scheduledDate;

	@Field(type = FieldType.Long)
	private Date startDate;

	@Field(type = FieldType.Keyword)
	private JobStatus status;

	@Field(type = FieldType.Long)
	private Date endDate;

	@Field(type = FieldType.Text, index = false)
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
		} catch (JacksonException e) {
			throw new IllegalStateException("Failed to serialise merge apiError for storage", e);
		}
	}

	/**
	 * Application/REST accessor. Deserialises {@link #apiErrorJson} for API responses.
	 * Returns null if stored JSON is unparseable so GET /merges/{id} still returns status/message.
	 */
	@Transient
	public ApiError getApiError() {
		if (apiErrorJson == null) {
			return null;
		}
		try {
			return OBJECT_MAPPER.readValue(apiErrorJson, ApiError.class);
		} catch (JacksonException e) {
			logger.error("Failed to deserialise apiErrorJson for merge job {}; returning null", id, e);
			return null;
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
