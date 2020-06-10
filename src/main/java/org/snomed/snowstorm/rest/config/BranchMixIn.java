package org.snomed.snowstorm.rest.config;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.kaicode.elasticvc.domain.Entity;

import java.util.Date;
import java.util.Map;
import java.util.Set;

@JsonPropertyOrder(value = {"path", "state", "containsContent", "locked", "creation", "base", "head",
		"creationTimestamp", "baseTimestamp", "headTimestamp", "userRoles", "metadata", "versionsReplacedCounts"})
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
