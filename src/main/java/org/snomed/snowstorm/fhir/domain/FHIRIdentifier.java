package org.snomed.snowstorm.fhir.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonView;
import org.hl7.fhir.r4.model.Identifier;

public class FHIRIdentifier {

	private String system;
	private String value;

	public FHIRIdentifier() {
	}

	public FHIRIdentifier(Identifier hapiIdentifier) {
		system = hapiIdentifier.getSystem();
		value = hapiIdentifier.getValue();
	}

	@JsonIgnore
	public Identifier getHapi() {
		Identifier identifier = new Identifier();
		identifier.setSystem(system);
		identifier.setValue(value);
		return identifier;
	}

	public String getSystem() {
		return system;
	}

	public void setSystem(String system) {
		this.system = system;
	}

	public String getValue() {
		return value;
	}

	public void setValue(String value) {
		this.value = value;
	}
}
