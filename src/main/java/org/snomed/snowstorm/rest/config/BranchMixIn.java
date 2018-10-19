package org.snomed.snowstorm.rest.config;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.kaicode.elasticvc.domain.Entity;

import java.util.Date;
import java.util.Map;
import java.util.Set;

public abstract class BranchMixIn {

	@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = Entity.DATE_FORMAT_STRING)
	abstract Date getBase();

	@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = Entity.DATE_FORMAT_STRING)
	abstract Date getHead();

	@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = Entity.DATE_FORMAT_STRING)
	abstract Date getCreation();

	@JsonIgnore
	abstract Set<String> getVersionsReplaced();

	@JsonIgnore
	abstract String getInternalId();

	@JsonIgnore
	abstract Date getStart();

	@JsonIgnore
	abstract Map<String, String> getMetadataInternal();

}
