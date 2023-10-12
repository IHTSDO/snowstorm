package org.snomed.snowstorm.core.data.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonView;
import io.micrometer.core.instrument.util.StringUtils;
import org.snomed.snowstorm.core.pojo.TermLangPojo;
import org.snomed.snowstorm.rest.View;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

public class Annotation extends ReferenceSetMember implements AnnotationView {

	private static final Pattern ANNOTATION_LANGUAGE_TYPE_PATTERN = Pattern.compile("^@+\\S.*\\\".*\\\"$");

	@JsonView(value = View.Component.class)
	private String annotationId;
	@JsonView(value = View.Component.class)
	private String annotationTypeId;
	private ConceptMini annotationType;
	@JsonView(value = View.Component.class)
	private String annotationValue;
	@JsonView(value = View.Component.class)
	private String annotationLanguage;

	@Override
	public String getAnnotationId() {
		return annotationId;
	}

	public void setAnnotationId(String annotationId) {
		this.annotationId = annotationId;
	}

	@Override
	public String getAnnotationTypeId() {
		return annotationTypeId;
	}

	public void setAnnotationTypeId(String annotationTypeId) {
		this.annotationTypeId = annotationTypeId;
	}

	@JsonIgnore
	public ConceptMini getAnnotationType() {
		return annotationType;
	}

	@Override
	@JsonView(value = View.Hidden.class)
	public String getMemberId() {
		return super.getMemberId();
	}

	@Override
	@JsonView(value = View.Hidden.class)
	public Map<String, String> getAdditionalFields() {
		return super.getAdditionalFields();
	}

	@Override
	@JsonView(value = View.Component.class)
	public TermLangPojo getAnnotationTypePt() {
		return annotationType != null ? annotationType.getPt() : null;
	}

	public void setAnnotationType(ConceptMini annotationType) {
		this.annotationType = annotationType;
	}

	@Override
	public String getAnnotationValue() {
		return annotationValue;
	}

	public void setAnnotationValue(String annotationValue) {
		this.annotationValue = annotationValue;
	}

	@Override
	public String getAnnotationLanguage() {
		return annotationLanguage;
	}

	public void setAnnotationLanguage(String annotationLanguage) {
		this.annotationLanguage = annotationLanguage;
	}

	public Annotation fromRefsetMember(ReferenceSetMember fromMember) {
		setAnnotationId(fromMember.getMemberId());
		setMemberId(fromMember.getMemberId());
		setRefsetId(fromMember.getRefsetId());
		setModuleId(fromMember.getModuleId());
		setActive(fromMember.isActive());
		setEffectiveTimeI(fromMember.getEffectiveTimeI());
		setReferencedComponentId(fromMember.getReferencedComponentId());
		setReleased(fromMember.isReleased());
		setAnnotationTypeId(fromMember.getAdditionalField(AnnotationFields.ANNOTATION_TYPE_ID));

		String annotationValue = fromMember.getAdditionalField(AnnotationFields.ANNOTATION_VALUE);
		if (StringUtils.isNotEmpty(annotationValue) && ANNOTATION_LANGUAGE_TYPE_PATTERN.matcher(annotationValue).matches()) {
			setAnnotationLanguage(annotationValue.substring(1, annotationValue.indexOf("\"")).trim());
			setAnnotationValue(annotationValue.substring(annotationValue.indexOf("\"") + 1, annotationValue.lastIndexOf("\"")));
		} else {
			setAnnotationValue(annotationValue);
		}
		return this;
	}

	public ReferenceSetMember toRefsetMember() {
		String refsetId = getRefsetId() != null ? getRefsetId() : Concepts.ANNOTATION_REFERENCE_SET;
		String annotationId = getAnnotationId() != null ? getAnnotationId() : UUID.randomUUID().toString();
		String moduleId = getModuleId() != null ? getModuleId() : Concepts.CORE_MODULE;
		ReferenceSetMember member = new ReferenceSetMember(annotationId, getEffectiveTimeI(), isActive(), moduleId, refsetId, getReferencedComponentId());
		member.setAdditionalField(AnnotationFields.ANNOTATION_TYPE_ID, getAnnotationTypeId());
		member.setAdditionalField(AnnotationFields.ANNOTATION_VALUE, getAnnotationLanguage() != null ? "@" + getAnnotationLanguage() + "\""  + getAnnotationValue() + "\"" : getAnnotationValue());
		return member;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof Annotation that)) return false;
		if (!super.equals(o)) return false;
		return  Objects.equals(getModuleId(), that.getModuleId()) &&
				Objects.equals(active, that.active) &&
				Objects.equals(getRefsetId(), that.getRefsetId()) &&
				Objects.equals(getReferencedComponentId(), that.getReferencedComponentId()) &&
				Objects.equals(getAnnotationTypeId(), that.getAnnotationTypeId()) &&
				Objects.equals(getAnnotationLanguage(), that.getAnnotationLanguage()) &&
				Objects.equals(getAnnotationValue(), that.getAnnotationValue());
	}

	@Override
	public int hashCode() {
		if (annotationId != null) {
			return annotationId.hashCode();
		}
		return Objects.hash(annotationId, active, getModuleId(), getRefsetId(), getReferencedComponentId(), getAnnotationTypeId(), getAnnotationLanguage(), getAnnotationValue());
	}

	@Override
	public String toString() {
		return "Annotation{" +
				"annotationId='" + annotationId + '\'' +
				", annotationTypeId='" + annotationTypeId + '\'' +
				", annotationType=" + annotationType +
				", annotationValue='" + annotationValue + '\'' +
				", annotationLanguage='" + annotationLanguage + '\'' +
				", effectiveTime='" + getEffectiveTimeI() + '\'' +
				", released='" + isReleased() + '\'' +
				", releasedEffectiveTime='" + getReleasedEffectiveTime() + '\'' +
				", releasedHash='" + getReleaseHash() + '\'' +
				", active=" + active +
				", moduleId='" + getModuleId() + '\'' +
				", refsetId='" + getRefsetId() + '\'' +
				", referencedComponentId='" + getReferencedComponentId() + '\'' +
				", internalId='" + getInternalId() + '\'' +
				", start='" + getStart() + '\'' +
				", end='" + getEnd() + '\'' +
				", path='" + getPath() + '\'' +
				'}';
	}
}
