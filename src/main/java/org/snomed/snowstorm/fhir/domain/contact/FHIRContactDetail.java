package org.snomed.snowstorm.fhir.domain.contact;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.hl7.fhir.r4.model.ContactDetail;
import org.hl7.fhir.r4.model.ContactPoint;

import java.util.ArrayList;
import java.util.List;

import static org.snomed.snowstorm.core.util.CollectionUtils.orEmpty;

public class FHIRContactDetail {

	private String name;

	private List<FHIRContactPoint> telecom;

	public FHIRContactDetail() {
	}

	public FHIRContactDetail(ContactDetail contactDetail) {
		name = contactDetail.getName();
		for (ContactPoint contactPoint : contactDetail.getTelecom()) {
			addContactPoint(new FHIRContactPoint(contactPoint));
		}
	}

	public void addContactPoint(FHIRContactPoint contactPoint) {
		if (telecom == null) {
			telecom = new ArrayList<>();
		}
		telecom.add(contactPoint);
	}

	@JsonIgnore
	public ContactDetail getHapi() {
		ContactDetail contactDetail = new ContactDetail();
		contactDetail.setName(name);
		for (FHIRContactPoint fhirContactPoint : orEmpty(telecom)) {
			contactDetail.addTelecom(fhirContactPoint.getHapi());
		}
		return contactDetail;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public List<FHIRContactPoint> getTelecom() {
		return telecom;
	}

	public void setTelecom(List<FHIRContactPoint> telecom) {
		this.telecom = telecom;
	}
}
