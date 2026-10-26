package org.snomed.snowstorm.core.data.domain;

import com.fasterxml.jackson.annotation.JsonDeserializeAs;

import java.util.Map;

@JsonDeserializeAs(ReferenceSetMember.class)
public interface ReferenceSetMemberView {

	String getMemberId();

	String getModuleId();

	String getRefsetId();

	String getReferencedComponentId();

	Map<String, String> getAdditionalFields();

	boolean isActive();

	boolean isReleased();

	String getEffectiveTime();

	Integer getReleasedEffectiveTime();
}
