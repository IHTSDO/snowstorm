package org.snomed.snowstorm.fhir.domain;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;

import org.hl7.fhir.r4.model.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceConstructor;
import org.springframework.data.elasticsearch.annotations.Document;

@Document(indexName = "fhir-structure-definition")
public class StructureDefinitionWrapper {
	
	private static IParser fhirJsonParser;
	
	@Id
	private String id;
	
	private StructureDefinition structureDefinition;
	
	public StructureDefinitionWrapper () {
	}

	@PersistenceConstructor
	public StructureDefinitionWrapper (IdType id, StructureDefinition vs) {
		this.structureDefinition = vs;
		this.id = id.getIdPart();
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
		if (structureDefinition != null) {
			this.structureDefinition.setId(id);
		}
	}

	public StructureDefinition getStructureDefinition() {
		return structureDefinition;
	}

	public void setStructureDefinition(StructureDefinition structureDefinition) {
		this.structureDefinition = structureDefinition;
	}
	
	public static IParser getFhirParser() {
		if (fhirJsonParser == null) {
			fhirJsonParser = FhirContext.forR4().newJsonParser();
		}
		return fhirJsonParser;
	}
	
}
