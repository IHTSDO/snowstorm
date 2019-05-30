package org.snomed.snowstorm.rest.config;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.util.Date;

public abstract class ClassificationMixIn {

	private static final String DATE_FORMAT_STRING = "yyyy-MM-dd'T'HH:mm:ss'Z'";

	@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = DATE_FORMAT_STRING)
	abstract Date getCreationDate();

	@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = DATE_FORMAT_STRING)
	abstract Date getCompletionDate();

	@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = DATE_FORMAT_STRING)
	abstract Date getLastCommitDate();

	@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = DATE_FORMAT_STRING)
	abstract Date getSaveDate();

}
