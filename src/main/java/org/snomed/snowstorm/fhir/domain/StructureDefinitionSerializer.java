package org.snomed.snowstorm.fhir.domain;

import org.hl7.fhir.r4.model.StructureDefinition;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdSerializer;

import ca.uhn.fhir.parser.IParser;

public class StructureDefinitionSerializer extends StdSerializer<StructureDefinition> {

	private static final long serialVersionUID = -2394473877974921774L;

	public StructureDefinitionSerializer() {
		this(StructureDefinition.class);
	}

	public StructureDefinitionSerializer(Class<StructureDefinition> t) {
		super(t);
	}
 
	@Override
	public void serialize(StructureDefinition vs, JsonGenerator jgen, SerializationContext provider)
			throws JacksonException {
		IParser fhirParser = StructureDefinitionWrapper.getFhirParser();
		String json = fhirParser.encodeResourceToString(vs);
		jgen.writeString(json);
	}
}
