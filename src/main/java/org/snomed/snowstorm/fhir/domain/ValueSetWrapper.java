package org.snomed.snowstorm.fhir.domain;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;

import org.hl7.fhir.r4.model.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

@Document(indexName = "valueset")
public class ValueSetWrapper {
	
	private static IParser fhirJsonParser;
	
	@Id
	@Field(type = FieldType.Keyword)
	private String id;

	@Field(type = FieldType.Text, name = "valueset")
	private String valuesetJson;

	@Transient
	private ValueSet valueSet;

	public ValueSetWrapper () {
	}

	public ValueSetWrapper (IdType id, ValueSet vs) {
		this.valuesetJson = getFhirParser().encodeResourceToString(vs);
		this.valueSet = vs;
		this.id = id.getIdPart();
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
		if (valueSet != null) {
			this.valueSet.setId(id);
		}
	}

	public ValueSet getValueSet() {
		if (valueSet == null && valuesetJson != null) {
			valueSet = getFhirParser().parseResource(ValueSet.class, valuesetJson);
		}
		return valueSet;
	}

	public String getValuesetJson() {
		return this.valuesetJson;
	}

	public void setValuesetJson(String vs) {
		this.valuesetJson = vs;
	}
	
	public static IParser getFhirParser() {
		if (fhirJsonParser == null) {
			fhirJsonParser = FhirContext.forR4().newJsonParser();
		}
		return fhirJsonParser;
	}
	
}
