package org.snomed.snowstorm.fhir.domain;

import java.io.IOException;

import org.hl7.fhir.r4.model.StructureDefinition;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import ca.uhn.fhir.parser.IParser;

public class StructureDefinitionSerializer extends StdSerializer<StructureDefinition> {

	private static final long serialVersionUID = -2394473877974921774L;

	public StructureDefinitionSerializer() {
		this(null);
	}

	public StructureDefinitionSerializer(Class<StructureDefinition> t) {
		super(t);
	}
 
	@Override
	public void serialize(StructureDefinition vs, JsonGenerator jgen, SerializerProvider provider) 
			throws IOException, JsonProcessingException {
		IParser fhirParser = StructureDefinitionWrapper.getFhirParser();
		String json = fhirParser.encodeResourceToString(vs);
		jgen.writeString(json);
	}
}
