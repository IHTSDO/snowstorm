package org.snomed.snowstorm.fhir.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.hl7.fhir.r4.model.ConceptMap;
import org.springframework.data.annotation.Transient;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.util.*;

import static org.snomed.snowstorm.core.util.CollectionUtils.orEmpty;

public class FHIRConceptMapGroup {

	@Field(type = FieldType.Keyword)
	private String groupId;

	@Field(type = FieldType.Keyword)
	private String source;

	@Field(type = FieldType.Keyword)
	private String sourceVersion;

	@Field(type = FieldType.Keyword)
	private String target;

	@Field(type = FieldType.Keyword)
	private String targetVersion;

	@Transient
	private List<FHIRMapElement> element;

	public FHIRConceptMapGroup() {
		groupId = UUID.randomUUID().toString();
	}

	public FHIRConceptMapGroup(ConceptMap.ConceptMapGroupComponent hapiGroup) {
		this();
		source = hapiGroup.getSource();
		sourceVersion = hapiGroup.getSourceVersion();
		target = hapiGroup.getTarget();
		targetVersion = hapiGroup.getTargetVersion();
		element = new ArrayList<>();
		for (ConceptMap.SourceElementComponent hapiElement : hapiGroup.getElement()) {
			element.add(new FHIRMapElement(hapiElement, groupId));
		}
	}

	@JsonIgnore
	public ConceptMap.ConceptMapGroupComponent getHapi() {
		ConceptMap.ConceptMapGroupComponent group = new ConceptMap.ConceptMapGroupComponent();
		group.setSource(source);
		group.setSourceVersion(sourceVersion);
		group.setTarget(target);
		group.setTargetVersion(targetVersion);
		for (FHIRMapElement mapElement : orEmpty(element)) {
			group.addElement(mapElement.getHapi());
		}
		return group;
	}

	public String getGroupId() {
		return groupId;
	}

	public void setGroupId(String groupId) {
		this.groupId = groupId;
	}

	public String getSource() {
		return source;
	}

	public void setSource(String source) {
		this.source = source;
	}

	public String getSourceVersion() {
		return sourceVersion;
	}

	public void setSourceVersion(String sourceVersion) {
		this.sourceVersion = sourceVersion;
	}

	public String getTarget() {
		return target;
	}

	public void setTarget(String target) {
		this.target = target;
	}

	public String getTargetVersion() {
		return targetVersion;
	}

	public void setTargetVersion(String targetVersion) {
		this.targetVersion = targetVersion;
	}

	public List<FHIRMapElement> getElement() {
		return element;
	}

	public void setElement(List<FHIRMapElement> element) {
		this.element = element;
	}
}
