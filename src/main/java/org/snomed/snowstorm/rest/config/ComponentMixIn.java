package org.snomed.snowstorm.rest.config;

import com.fasterxml.jackson.annotation.JsonView;
import org.snomed.snowstorm.rest.View;

public interface ComponentMixIn {

	@JsonView(value = View.Component.class)
	String getId();

	@JsonView(value = View.Component.class)
	boolean isActive();

	@JsonView(value = View.Component.class)
	boolean isPublished();

	@JsonView(value = View.Component.class)
	boolean isReleased();

	@JsonView(value = View.Component.class)
	String getModuleId();
}
