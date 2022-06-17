package org.snomed.snowstorm.fhir.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.hl7.fhir.r4.model.ConceptMap;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.snomed.snowstorm.core.util.CollectionUtils.orEmpty;

@Document(indexName = "fhir-map-element")
public class FHIRMapElement {

	public interface Fields {
		String GROUP_ID = "groupId";
		String CODE = "code";
	}

	@Id
	private String id;

	@Field(type = FieldType.Keyword)
	private String groupId;

	@Field(type = FieldType.Keyword)
	private String code;

	@Field(type = FieldType.Keyword)
	private String display;

	private List<FHIRMapTarget> target;

	public FHIRMapElement() {
	}

	public FHIRMapElement(ConceptMap.SourceElementComponent hapiElement, String groupId) {
		this.groupId = groupId;
		code = hapiElement.getCode();
		display = hapiElement.getDisplay();
		target = new ArrayList<>();
		for (ConceptMap.TargetElementComponent hapiTarget : hapiElement.getTarget()) {
			target.add(new FHIRMapTarget(hapiTarget));
		}
	}

	@JsonIgnore
	public ConceptMap.SourceElementComponent getHapi() {
		ConceptMap.SourceElementComponent element = new ConceptMap.SourceElementComponent();
		element.setCode(code);
		element.setDisplay(display);
		for (FHIRMapTarget mapTarget : orEmpty(target)) {
			element.addTarget(mapTarget.getHapi());
		}
		return element;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getGroupId() {
		return groupId;
	}

	public void setGroupId(String groupId) {
		this.groupId = groupId;
	}

	public String getCode() {
		return code;
	}

	public FHIRMapElement setCode(String code) {
		this.code = code;
		return this;
	}

	public String getDisplay() {
		return display;
	}

	public void setDisplay(String display) {
		this.display = display;
	}

	public List<FHIRMapTarget> getTarget() {
		return target;
	}

	public FHIRMapElement setTarget(List<FHIRMapTarget> target) {
		this.target = target;
		return this;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		FHIRMapElement that = (FHIRMapElement) o;
		return Objects.equals(groupId, that.groupId) && Objects.equals(code, that.code) && Objects.equals(target, that.target);
	}

	@Override
	public int hashCode() {
		return Objects.hash(groupId, code, target);
	}
}
