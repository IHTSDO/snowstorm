package org.snomed.snowstorm.fhir.services;

import ca.uhn.fhir.jpa.entity.TermCodeSystem;
import ca.uhn.fhir.jpa.entity.TermCodeSystemVersion;
import ca.uhn.fhir.jpa.entity.TermConcept;
import ca.uhn.fhir.jpa.model.entity.ResourceTable;
import ca.uhn.fhir.jpa.term.UploadStatistics;
import ca.uhn.fhir.jpa.term.api.ITermCodeSystemStorageSvc;
import ca.uhn.fhir.jpa.term.custom.CustomTerminologySet;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.api.server.storage.ResourcePersistentId;
import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.r4.model.CodeSystem;
import org.hl7.fhir.r4.model.ConceptMap;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.ValueSet;
import org.snomed.snowstorm.fhir.domain.FHIRCodeSystemVersion;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class FHIRTermCodeSystemStorage implements ITermCodeSystemStorageSvc {

	@Autowired
	private FHIRCodeSystemService fhirCodeSystemService;

	@Autowired
	private FHIRConceptService fhirConceptService;

	@Override
	public void deleteCodeSystem(TermCodeSystem termCodeSystem) {
		System.out.println();
	}

	@Override
	public void deleteCodeSystemVersion(TermCodeSystemVersion termCodeSystemVersion) {
		System.out.println();

	}

	@Override
	public void storeNewCodeSystemVersion(ResourcePersistentId resourcePersistentId, String s, String s1, String s2, TermCodeSystemVersion termCodeSystemVersion, ResourceTable resourceTable, RequestDetails requestDetails) {
		System.out.println();

	}

	@Override
	public IIdType storeNewCodeSystemVersion(CodeSystem codeSystem, TermCodeSystemVersion termCodeSystemVersion, RequestDetails requestDetails, List<ValueSet> list, List<ConceptMap> list1) {
		System.out.println();

		FHIRCodeSystemVersion codeSystemVersion = fhirCodeSystemService.save(codeSystem);
		fhirConceptService.save(termCodeSystemVersion, codeSystemVersion);

		return new IdType("CodeSystem", codeSystemVersion.getId(), codeSystemVersion.getVersion());
	}

	@Override
	public void storeNewCodeSystemVersionIfNeeded(CodeSystem codeSystem, ResourceTable resourceTable, RequestDetails requestDetails) {
		System.out.println();

	}

	@Override
	public UploadStatistics applyDeltaCodeSystemsAdd(String s, CustomTerminologySet customTerminologySet) {
		System.out.println();
		return null;
	}

	@Override
	public UploadStatistics applyDeltaCodeSystemsRemove(String s, CustomTerminologySet customTerminologySet) {
		System.out.println();
		return null;
	}

	@Override
	public int saveConcept(TermConcept termConcept) {
		System.out.println();
		return 0;
	}

	@Override
	public ResourcePersistentId getValueSetResourcePid(IIdType iIdType) {
		System.out.println();
		return null;
	}
}
