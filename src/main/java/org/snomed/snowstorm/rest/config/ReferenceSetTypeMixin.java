package org.snomed.snowstorm.rest.config;

import com.fasterxml.jackson.annotation.JsonIgnore;

public interface ReferenceSetTypeMixin {

	@JsonIgnore
	boolean isChanged();

	@JsonIgnore
	boolean isDeleted();

	@JsonIgnore
	String getFieldNames();

	@JsonIgnore
	String getConceptId();

	@JsonIgnore
	String getId();

}
