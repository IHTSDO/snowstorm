package org.snomed.snowstorm.core.data.domain;

import com.fasterxml.jackson.annotation.JsonDeserializeAs;


@JsonDeserializeAs(Identifier.class)
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
