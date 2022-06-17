package org.snomed.snowstorm.fhir.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.hl7.fhir.r4.model.ConceptMap;
import org.hl7.fhir.r4.model.Enumerations;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.util.Objects;

public class FHIRMapTarget {

	@Field(type = FieldType.Keyword)
	private String code;

	@Field(type = FieldType.Keyword)
	private String display;

	@Field(type = FieldType.Keyword)
	private String equivalence;

	@Field(type = FieldType.Keyword)
	private String comment;

	public FHIRMapTarget() {
	}

	public FHIRMapTarget(ConceptMap.TargetElementComponent hapiTarget) {
		code = hapiTarget.getCode();
		display = hapiTarget.getDisplay();
		equivalence = hapiTarget.getEquivalence() != null ? hapiTarget.getEquivalence().toCode() : null;
		comment = hapiTarget.getComment();
	}

	public FHIRMapTarget(String code, String equivalence, String comment) {
		this.code = code;
		this.equivalence = equivalence;
		this.comment = comment;
	}

	@JsonIgnore
	public ConceptMap.TargetElementComponent getHapi() {
		ConceptMap.TargetElementComponent component = new ConceptMap.TargetElementComponent();
		component.setCode(code);
		component.setDisplay(display);
		if (equivalence != null) {
			component.setEquivalence(Enumerations.ConceptMapEquivalence.fromCode(equivalence));
		}
		component.setComment(comment);
		return component;
	}

	public String getCode() {
		return code;
	}

	public void setCode(String code) {
		this.code = code;
	}

	public String getDisplay() {
		return display;
	}

	public void setDisplay(String display) {
		this.display = display;
	}

	public String getEquivalence() {
		return equivalence;
	}

	public void setEquivalence(String equivalence) {
		this.equivalence = equivalence;
	}

	public String getComment() {
		return comment;
	}

	public void setComment(String comment) {
		this.comment = comment;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		FHIRMapTarget that = (FHIRMapTarget) o;
		return Objects.equals(code, that.code) && Objects.equals(equivalence, that.equivalence);
	}

	@Override
	public int hashCode() {
		return Objects.hash(code, equivalence);
	}
}
