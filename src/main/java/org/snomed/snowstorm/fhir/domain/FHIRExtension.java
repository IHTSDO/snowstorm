package org.snomed.snowstorm.fhir.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.hl7.fhir.r4.model.*;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

public class FHIRExtension {

	@Field(type = FieldType.Keyword)
	private String uri;
	@Field(type = FieldType.Keyword)
	private String value;
	@Field(type = FieldType.Keyword)
	private String type;

	public FHIRExtension() {
	}

	public FHIRExtension(Extension hapiExtension) {
		uri = hapiExtension.getUrl();
		value = hapiExtension.getValue().primitiveValue();
		type = hapiExtension.getValue().fhirType();
	}

	@JsonIgnore
	public Extension getHapi() {
		Extension extension = new Extension();
		extension.setUrl(uri);
		extension.setValue(getType(value, type));
		return extension;
	}
	public static Type getType(String primitiveValue, String fhirType){
        return switch (fhirType) {
            case "integer" -> new IntegerType(primitiveValue);
            case "boolean" -> new BooleanType(primitiveValue);
            case "string" -> new StringType(primitiveValue);
            case "decimal" -> new DecimalType(primitiveValue);
			case "id" -> new IdType(primitiveValue);
			case "canonical" -> new CanonicalType(primitiveValue);
            default -> null;
        };
    }

	public String getUri() {
		return uri;
	}

	public void setUri(String uri) {
		this.uri = uri;
	}

	public String getValue() {
		return value;
	}

	public void setValue(String value) {
		this.value = value;
	}
}
