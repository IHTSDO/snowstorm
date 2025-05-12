package org.snomed.snowstorm.core.data.domain;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;


@JsonDeserialize(as = Identifier.class)
public interface IdentifierView {

	String getAlternateIdentifier();

	String getModuleId();

	String getReferencedComponentId();

	String getIdentifierSchemaId();

	boolean isActive();

	boolean isReleased();

	String getEffectiveTime();

	Integer getReleasedEffectiveTime();
}
