package org.snomed.snowstorm.fhir.domain;

import org.hl7.fhir.r4.model.CanonicalType;
import org.hl7.fhir.r4.model.ValueSet;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.snomed.snowstorm.core.util.CollectionUtils.orEmpty;

public class FHIRValueSetCriteriaConcept {

	@Field(type = FieldType.Keyword)
	private String code;



	private List<FHIRExtension> extensions;



	private List<FHIRDesignation> designations;



	public FHIRValueSetCriteriaConcept() {
	}

	public FHIRValueSetCriteriaConcept(ValueSet.ConceptReferenceComponent hapiConceptReferenceComponent) {
		code = hapiConceptReferenceComponent.getCode();
		hapiConceptReferenceComponent.getExtension().forEach( ext -> {
			if (extensions == null){
				extensions = new ArrayList<>();
			}
			extensions.add(new FHIRExtension(ext));
		});
		hapiConceptReferenceComponent.getDesignation().forEach( d -> {
			if (designations == null){
				designations = new ArrayList<>();
			}
			designations.add(new FHIRDesignation(d));
		});
	}

	public ValueSet.ConceptReferenceComponent getHapi() {
		ValueSet.ConceptReferenceComponent hapiConceptReferenceComponent = new ValueSet.ConceptReferenceComponent();
		hapiConceptReferenceComponent.setCode(code);
		hapiConceptReferenceComponent.setExtension(Optional.ofNullable(extensions).orElse(Collections.emptyList()).stream().map(e->e.getHapi()).toList());
		hapiConceptReferenceComponent.setDesignation(Optional.ofNullable(designations).orElse(Collections.emptyList()).stream().map(d->d.getHapi()).toList());
		return hapiConceptReferenceComponent;
	}

	public String getCode() {
		return code;
	}

	public void setCode(String code) {
		this.code = code;
	}

	public List<FHIRExtension> getExtensions() {
		return extensions;
	}

	public void setExtensions(List<FHIRExtension> extensions) {
		this.extensions = extensions;
	}

	public List<FHIRDesignation> getDesignations() {
		return designations;
	}

	public void setDesignations(List<FHIRDesignation> designations) {
		this.designations = designations;
	}

}
