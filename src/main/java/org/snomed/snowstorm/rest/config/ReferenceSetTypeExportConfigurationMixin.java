package org.snomed.snowstorm.rest.config;

import com.fasterxml.jackson.annotation.JsonIgnore;

public interface ReferenceSetTypeExportConfigurationMixin {

	@JsonIgnore
	String getFieldNames();

	@JsonIgnore
	String getConceptId();

}
