package org.snomed.snowstorm.fhir.domain;

import java.io.IOException;

import org.hl7.fhir.r4.model.StructureDefinition;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import ca.uhn.fhir.parser.IParser;

public class StructureDefinitionDeserializer extends StdDeserializer<StructureDefinition> {

	private static final long serialVersionUID = -2394473877974921774L;

	public StructureDefinitionDeserializer() {
		this(null);
	}

	public StructureDefinitionDeserializer(Class<StructureDefinition> t) {
		super(t);
	}
 

	@Override
	public StructureDefinition deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JsonProcessingException {
		IParser fhirParser = StructureDefinitionWrapper.getFhirParser();
		String json = p.readValueAsTree().toString();
		//Remove the leading and trailing quotes and unescape the remaining quotes
		json = json.substring(1, json.length()-1)
				.replaceAll("\\\\\"", "\"");
		return fhirParser.parseResource(StructureDefinition.class, json);
	}
}
