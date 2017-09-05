package org.ihtsdo.elasticsnomed.rest.config;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.kaicode.elasticvc.domain.Entity;

import java.util.Date;
import java.util.Set;

public abstract class ClassificationMixIn {

	@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = Entity.DATE_FORMAT_STRING)
	abstract Date getCreationDate();

	@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = Entity.DATE_FORMAT_STRING)
	abstract Date getCompletionDate();

	@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = Entity.DATE_FORMAT_STRING)
	abstract Date getLastCommitDate();

}
