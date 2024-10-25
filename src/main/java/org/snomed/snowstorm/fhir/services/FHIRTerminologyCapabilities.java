package org.snomed.snowstorm.fhir.services;

import java.util.*;

import org.hl7.fhir.instance.model.api.IBaseConformance;
import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.r4.model.ContactPoint.ContactPointSystem;
import org.hl7.fhir.r4.model.Enumerations.PublicationStatus;
import org.snomed.snowstorm.core.data.domain.CodeSystemVersion;
import org.snomed.snowstorm.fhir.config.FHIRConstants;

import ca.uhn.fhir.model.api.annotation.ChildOrder;
import ca.uhn.fhir.model.api.annotation.ResourceDef;
import org.snomed.snowstorm.fhir.domain.FHIRCodeSystemVersion;
import org.springframework.boot.info.BuildProperties;

@ResourceDef(name="TerminologyCapabilities", profile="http://hl7.org/fhir/StructureDefinition/TerminologyCapabilities")
@ChildOrder(names={"url", "version", "name", "title", "status", "experimental", "date", "publisher", "contact", "description", "useContext", "jurisdiction", "purpose", "copyright", "kind", "software", "implementation", "lockedDate", "codeSystem", "expansion", "codeSearch", "validateCode", "translation", "closure"})
public class FHIRTerminologyCapabilities extends TerminologyCapabilities implements IBaseConformance, FHIRConstants {

	private static final long serialVersionUID = 1L;

	public static final String CAPABILITY_TITLE = "SnowstormX Terminology Capability Statement";
	public static final String CAPABILITY_MAIL = "implementation@snomed.org";

	public FHIRTerminologyCapabilities withDefaults(BuildProperties buildProperties, FHIRCodeSystemService codeSystemService) {
		setContact();
		addCodeSystems(codeSystemService);
		setName("SnowstormTerminologyCapabilities");
		setStatus(PublicationStatus.DRAFT);
		setTitle(CAPABILITY_TITLE);
		setVersion(buildProperties == null ? "development" : buildProperties.getVersion());
		return this;
	}

	private void addCodeSystems(FHIRCodeSystemService codeSystemService) {
		Map<String, List<FHIRCodeSystemVersion>> codeSystemsByUri = new LinkedHashMap<>();

		List<CodeSystemVersion> allSnomedVersions = codeSystemService.findAllSnomedVersions();
		for (CodeSystemVersion systemVersion : allSnomedVersions) {
			FHIRCodeSystemVersion fhirCSVersion = new FHIRCodeSystemVersion(systemVersion);
			codeSystemsByUri.computeIfAbsent(fhirCSVersion.getUrl(), k -> new ArrayList<>()).add(fhirCSVersion);
		}

		for (FHIRCodeSystemVersion fhirCodeSystemVersion : codeSystemService.findAll()) {
			codeSystemsByUri.computeIfAbsent(fhirCodeSystemVersion.getUrl(), k -> new ArrayList<>()).add(fhirCodeSystemVersion);
		}

		for (Map.Entry<String, List<FHIRCodeSystemVersion>> entry : codeSystemsByUri.entrySet()) {
			String uri = entry.getKey();
			TerminologyCapabilitiesCodeSystemComponent codeSystemComponent = new TerminologyCapabilitiesCodeSystemComponent();
			codeSystemComponent.setUri(uri);
			if (SNOMED_URI.equals(uri)) {
				codeSystemComponent.setSubsumption(true);
			}
			codeSystemComponent.setVersion(entry.getValue().stream().map(version -> {
				TerminologyCapabilitiesCodeSystemVersionComponent versionComponent = new TerminologyCapabilitiesCodeSystemVersionComponent();
				versionComponent.setCode(version.getVersion());
				if (SNOMED_URI.equals(uri)) {
					versionComponent.addLanguage("en");
				}
				return versionComponent;
			}).toList());
			addCodeSystem(codeSystemComponent);
		}
	}

	private void setContact() {
		ContactPoint contactPoint = new ContactPoint();
		contactPoint.setSystem(ContactPointSystem.EMAIL);
		contactPoint.setValue(CAPABILITY_MAIL);
		ContactDetail contactDetail = new ContactDetail();
		contactDetail.addTelecom(contactPoint);
		setContact(Collections.singletonList(contactDetail));
	}

}
