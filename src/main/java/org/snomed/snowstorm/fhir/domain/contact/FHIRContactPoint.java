package org.snomed.snowstorm.fhir.domain.contact;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.hl7.fhir.r4.model.ContactPoint;

public class FHIRContactPoint {

	private String system;
	private String value;
	private String use;

	public FHIRContactPoint() {
	}

	public FHIRContactPoint(ContactPoint contactPoint) {
		ContactPoint.ContactPointSystem hapiSystem = contactPoint.getSystem();
		system = hapiSystem != null ? hapiSystem.toCode() : null;

		ContactPoint.ContactPointUse hapiUse = contactPoint.getUse();
		use = hapiUse != null ? hapiUse.toCode() : null;

		value = contactPoint.getValue();
	}

	@JsonIgnore
	public ContactPoint getHapi() {
		ContactPoint contactPoint = new ContactPoint();
		if (system != null) {
			contactPoint.setSystem(ContactPoint.ContactPointSystem.fromCode(system));
		}
		contactPoint.setValue(value);
		if (use != null) {
			contactPoint.setUse(ContactPoint.ContactPointUse.fromCode(use));
		}
		return contactPoint;
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

	public String getUse() {
		return use;
	}

	public void setUse(String use) {
		this.use = use;
	}
}
