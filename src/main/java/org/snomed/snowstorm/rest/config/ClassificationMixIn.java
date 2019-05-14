package org.snomed.snowstorm.rest.config;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.kaicode.elasticvc.domain.Entity;

import java.util.Date;

public abstract class ClassificationMixIn {

	@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = Entity.DATE_FORMAT_STRING)
	abstract Date getCreationDate();

	@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = Entity.DATE_FORMAT_STRING)
	abstract Date getCompletionDate();

	@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = Entity.DATE_FORMAT_STRING)
	abstract Date getLastCommitDate();

	@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = Entity.DATE_FORMAT_STRING)
	abstract Date getSaveDate();

}
