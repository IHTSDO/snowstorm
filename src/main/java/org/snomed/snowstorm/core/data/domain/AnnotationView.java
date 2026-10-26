package org.snomed.snowstorm.core.data.domain;

import com.fasterxml.jackson.annotation.JsonDeserializeAs;
import org.snomed.snowstorm.core.pojo.TermLangPojo;

@JsonDeserializeAs(Annotation.class)
public interface AnnotationView {

	String getAnnotationId();

	String getTypeId();

	TermLangPojo getTypePt();

	String getValue();

	String getLanguageDialectCode();

	String getModuleId();

	String getReferencedComponentId();

	boolean isActive();

	boolean isReleased();

	String getEffectiveTime();

	String getRefsetId();
}
