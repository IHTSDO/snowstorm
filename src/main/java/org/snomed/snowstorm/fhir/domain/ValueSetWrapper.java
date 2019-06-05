package org.snomed.snowstorm.fhir.domain;

import com.fasterxml.jackson.annotation.*;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;

import org.hl7.fhir.r4.model.*;
import org.springframework.data.elasticsearch.annotations.Document;


@Document(indexName = "valueset")
public class ValueSetWrapper {
	
	private static IParser fhirJsonParser;
	
	@JsonProperty("id")
	private String id;
	
	private ValueSet valueset;
	
	public ValueSetWrapper () {
	}
	
	public ValueSetWrapper (ValueSet vs) {
		this.valueset = vs;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public ValueSet getValueset() {
		return valueset;
	}

	public void setValueset(ValueSet valueset) {
		this.valueset = valueset;
	}
	
	public static IParser getFhirParser() {
		if (fhirJsonParser == null) {
			fhirJsonParser = FhirContext.forR4().newJsonParser();
		}
		return fhirJsonParser;
	}
	
}
