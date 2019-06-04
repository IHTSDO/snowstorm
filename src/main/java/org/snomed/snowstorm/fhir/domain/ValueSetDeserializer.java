package org.snomed.snowstorm.fhir.domain;

import java.io.IOException;

import org.hl7.fhir.r4.model.ValueSet;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import ca.uhn.fhir.parser.IParser;

public class ValueSetDeserializer extends StdDeserializer<ValueSet> {

	private static final long serialVersionUID = -2394473877974921774L;

	public ValueSetDeserializer() {
		this(null);
	}

	public ValueSetDeserializer(Class<ValueSet> t) {
		super(t);
	}
 

	@Override
	public ValueSet deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JsonProcessingException {
		IParser fhirParser = ValueSetWrapper.getFhirParser();
		String json = p.readValueAsTree().toString();
		//Remove the leading and trailing quotes and unescape the remaining quotes
		json = json.substring(1, json.length()-1)
				.replaceAll("\\\\\"", "\"");
		return fhirParser.parseResource(ValueSet.class, json);
	}
}
