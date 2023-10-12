package org.snomed.snowstorm.core.data.domain;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.snomed.snowstorm.core.pojo.TermLangPojo;

@JsonDeserialize(as = Annotation.class)
public interface AnnotationView {

	String getAnnotationId();

	String getAnnotationTypeId();

	TermLangPojo getAnnotationTypePt();

	String getAnnotationValue();

	String getAnnotationLanguage();

	String getModuleId();

	String getReferencedComponentId();

	boolean isActive();

	boolean isReleased();

	String getEffectiveTime();

	String getRefsetId();
}
