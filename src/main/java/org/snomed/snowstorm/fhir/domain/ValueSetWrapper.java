package org.snomed.snowstorm.fhir.domain;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;

import org.hl7.fhir.r4.model.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;


@Document(indexName = "valueset")
public class ValueSetWrapper {
	
	private static IParser fhirJsonParser;
	
	@Id
	private String id;
	
	private ValueSet valueset;
	
	public ValueSetWrapper () {
	}
	
	public ValueSetWrapper (IdType id, ValueSet vs) {
		this.valueset = vs;
		this.id = id.getIdPart();
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
		if (valueset != null) {
			this.valueset.setId(id);
		}
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
