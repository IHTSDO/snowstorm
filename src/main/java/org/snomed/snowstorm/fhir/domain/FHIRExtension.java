package org.snomed.snowstorm.fhir.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.hl7.fhir.r4.model.*;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.snomed.snowstorm.core.util.CollectionUtils.orEmpty;

public class FHIRExtension {

	@Field(type = FieldType.Keyword)
	private String uri;
	@Field(type = FieldType.Keyword)
	private String value;
	@Field(type = FieldType.Keyword)
	private String type;

	private List<FHIRExtension> extensions;

	public FHIRExtension() {
	}

	public FHIRExtension(Extension hapiExtension) {
		uri = hapiExtension.getUrl();
		if (hapiExtension.getValue()!= null) {
			value = hapiExtension.getValue().primitiveValue();
			type = hapiExtension.getValue().fhirType();
		} else {
			hapiExtension.getExtension().forEach( extension -> {
				if (extensions == null){
					extensions = new ArrayList<>();
				}
				extensions.add(new FHIRExtension(extension));
			});
		}
	}

	@JsonIgnore
	public Extension getHapi() {
		Extension extension = new Extension();
		extension.setUrl(uri);
		Optional.ofNullable(getType(value, type))
				.ifPresentOrElse(extension::setValue, () ->{
					orEmpty(extensions).forEach( fhirExtension -> {
						extension.addExtension(fhirExtension.getHapi());
					});

		});
		return extension;
	}
	public static Type getType(String primitiveValue, String fhirType){
		if (fhirType == null) return null;
        return switch (fhirType) {
            case "integer" -> new IntegerType(primitiveValue);
            case "boolean" -> new BooleanType(primitiveValue);
            case "string" -> new StringType(primitiveValue);
            case "decimal" -> new DecimalType(primitiveValue);
			case "id" -> new IdType(primitiveValue);
			case "canonical" -> new CanonicalType(primitiveValue);
			case "code" -> new CodeType(primitiveValue);
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

	public List<FHIRExtension> getExtensions() {
		return extensions;
	}

	public void setExtensions(List<FHIRExtension> extensions) {
		this.extensions = extensions;
	}
}
