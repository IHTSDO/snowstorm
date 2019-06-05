package org.snomed.snowstorm.fhir.domain;

import java.io.IOException;

import org.hl7.fhir.r4.model.ValueSet;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import ca.uhn.fhir.parser.IParser;

public class ValueSetSerializer extends StdSerializer<ValueSet> {

	private static final long serialVersionUID = -2394473877974921774L;

	public ValueSetSerializer() {
		this(null);
	}

	public ValueSetSerializer(Class<ValueSet> t) {
		super(t);
	}
 
	@Override
	public void serialize(ValueSet vs, JsonGenerator jgen, SerializerProvider provider) 
			throws IOException, JsonProcessingException {
		IParser fhirParser = ValueSetWrapper.getFhirParser();
		String json = fhirParser.encodeResourceToString(vs);
		jgen.writeString(json);
	}
}
