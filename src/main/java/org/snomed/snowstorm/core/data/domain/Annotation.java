package org.snomed.snowstorm.core.data.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonView;
import org.snomed.snowstorm.core.pojo.TermLangPojo;
import org.snomed.snowstorm.rest.View;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class Annotation extends ReferenceSetMember implements AnnotationView {

	@JsonView(value = View.Component.class)
	private String annotationId;
	@JsonView(value = View.Component.class)
	private String typeId;
	private ConceptMini type;
	@JsonView(value = View.Component.class)
	private String value;
	@JsonView(value = View.Component.class)
	private String languageDialectCode;

	@Override
	public String getAnnotationId() {
		return annotationId;
	}

	public void setAnnotationId(String annotationId) {
		this.annotationId = annotationId;
	}

	@Override
	public String getTypeId() {
		return typeId;
	}

	public void setTypeId(String typeId) {
		this.typeId = typeId;
	}

	@JsonIgnore
	public ConceptMini getType() {
		return type;
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
	public TermLangPojo getTypePt() {
		return type != null ? type.getPt() : null;
	}

	public void setType(ConceptMini type) {
		this.type = type;
	}

	@Override
	public String getValue() {
		return value;
	}

	public void setValue(String value) {
		this.value = value;
	}

	@Override
	public String getLanguageDialectCode() {
		return languageDialectCode;
	}

	public void setLanguageDialectCode(String languageDialectCode) {
		this.languageDialectCode = languageDialectCode;
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
		setTypeId(fromMember.getAdditionalField(AnnotationFields.TYPE_ID));
		setValue(fromMember.getAdditionalField(AnnotationFields.VALUE));
		setLanguageDialectCode(fromMember.getAdditionalField(AnnotationFields.LANGUAGE_DIALECT_CODE));
		return this;
	}

	public ReferenceSetMember toRefsetMember() {
		String refsetId = getRefsetId() != null ? getRefsetId() : Concepts.ANNOTATION_REFERENCE_SET;
		String annotationId = getAnnotationId() != null ? getAnnotationId() : UUID.randomUUID().toString();
		String moduleId = getModuleId() != null ? getModuleId() : Concepts.CORE_MODULE;
		ReferenceSetMember member = new ReferenceSetMember(annotationId, getEffectiveTimeI(), isActive(), moduleId, refsetId, getReferencedComponentId());
		member.setAdditionalField(AnnotationFields.TYPE_ID, getTypeId());
		member.setAdditionalField(AnnotationFields.VALUE, getValue());
		member.setAdditionalField(AnnotationFields.LANGUAGE_DIALECT_CODE, getLanguageDialectCode());
		return member;
	}

	public void clone(Annotation annotation) {
		setAnnotationId(annotation.getAnnotationId());
		setMemberId(annotation.getMemberId());
		setRefsetId(annotation.getRefsetId());
		setModuleId(annotation.getModuleId());
		setActive(annotation.isActive());
		setEffectiveTimeI(annotation.getEffectiveTimeI());
		setReferencedComponentId(annotation.getReferencedComponentId());
		setReleased(annotation.isReleased());
		setTypeId(annotation.getTypeId());
		setType(annotation.getType());
		setValue(annotation.getValue());
		setLanguageDialectCode(annotation.getLanguageDialectCode());
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
				Objects.equals(getTypeId(), that.getTypeId()) &&
				Objects.equals(getLanguageDialectCode(), that.getLanguageDialectCode()) &&
				Objects.equals(getValue(), that.getValue());
	}

	@Override
	public int hashCode() {
		if (annotationId != null) {
			return annotationId.hashCode();
		}
		return Objects.hash(annotationId, active, getModuleId(), getRefsetId(), getReferencedComponentId(), getTypeId(), getLanguageDialectCode(), getValue());
	}

	@Override
	public String toString() {
		return "Annotation{" +
				"annotationId='" + annotationId + '\'' +
				", annotationTypeId='" + typeId + '\'' +
				", annotationType=" + type +
				", annotationValue='" + value + '\'' +
				", annotationLanguageDialectCode='" + languageDialectCode + '\'' +
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
