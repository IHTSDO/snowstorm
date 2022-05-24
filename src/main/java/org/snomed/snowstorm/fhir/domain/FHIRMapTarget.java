package org.snomed.snowstorm.fhir.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.hl7.fhir.r4.model.ConceptMap;
import org.hl7.fhir.r4.model.Enumerations;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

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
}
