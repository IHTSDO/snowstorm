package org.snomed.snowstorm.rest.config;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.kaicode.elasticvc.domain.Entity;

import java.util.Date;
import java.util.Map;
import java.util.Set;

public abstract class CodeSystemVersionMixIn {

	@JsonIgnore
	abstract String getId();

	@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = Entity.DATE_FORMAT_STRING)
	abstract Date getImportDate();

}
