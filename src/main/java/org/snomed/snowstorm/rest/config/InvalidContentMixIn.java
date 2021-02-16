package org.snomed.snowstorm.rest.config;

import com.fasterxml.jackson.annotation.JsonView;
import org.ihtsdo.drools.domain.Component;
import org.ihtsdo.drools.response.Severity;
import org.snomed.snowstorm.rest.View;

public interface InvalidContentMixIn {

	@JsonView(value = View.Component.class)
	void ignorePublishedCheck();

	@JsonView(value = View.Component.class)
	String getConceptId();

	@JsonView(value = View.Component.class)
	String getComponentId();

	@JsonView(value = View.Component.class)
	boolean isIgnorePublishedCheck();

	@JsonView(value = View.Component.class)
	boolean isPublished();

	@JsonView(value = View.Component.class)
	String getMessage();

	@JsonView(value = View.Component.class)
	Severity getSeverity();

	@JsonView(value = View.Component.class)
	Component getComponent();

	@JsonView(value = View.Component.class)
	String getConceptFsn();
}
