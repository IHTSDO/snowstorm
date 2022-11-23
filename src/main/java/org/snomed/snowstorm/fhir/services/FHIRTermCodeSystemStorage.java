package org.snomed.snowstorm.fhir.services;

import ca.uhn.fhir.jpa.entity.TermCodeSystemVersion;
import ca.uhn.fhir.jpa.entity.TermConcept;
import ca.uhn.fhir.jpa.model.entity.ResourceTable;
import ca.uhn.fhir.jpa.term.UploadStatistics;
import ca.uhn.fhir.jpa.term.api.ITermCodeSystemStorageSvc;
import ca.uhn.fhir.jpa.term.custom.CustomTerminologySet;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.api.server.storage.IResourcePersistentId;
import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.r4.model.CodeSystem;
import org.hl7.fhir.r4.model.ConceptMap;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.ValueSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.fhir.config.FHIRConstants;
import org.snomed.snowstorm.fhir.domain.FHIRCodeSystemVersion;
import org.snomed.snowstorm.fhir.domain.FHIRConceptMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

import static org.snomed.snowstorm.core.util.CollectionUtils.orEmpty;

@Service
public class FHIRTermCodeSystemStorage implements ITermCodeSystemStorageSvc {

	@Value("${snowstorm.rest-api.readonly}")
	private boolean readOnlyMode;

	@Autowired
	private FHIRCodeSystemService fhirCodeSystemService;

	@Autowired
	private FHIRConceptService fhirConceptService;

	@Autowired
	private FHIRValueSetService fhirValueSetService;

	@Autowired
	private FHIRConceptMapProvider fhirConceptMapProvider;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	@Override
	public void storeNewCodeSystemVersion(IResourcePersistentId theCodeSystemResourcePid, String theSystemUri, String theSystemName,
			String theSystemVersionId, TermCodeSystemVersion theCodeSystemVersion, ResourceTable theCodeSystemResourceTable,
			RequestDetails theRequestDetails) {

	}

	@Override
	public void storeNewCodeSystemVersion(IResourcePersistentId theCodeSystemResourcePid, String theSystemUri, String theSystemName, String theSystemVersionId, TermCodeSystemVersion theCodeSystemVersion, ResourceTable theCodeSystemResourceTable) {
		ITermCodeSystemStorageSvc.super.storeNewCodeSystemVersion(theCodeSystemResourcePid, theSystemUri, theSystemName, theSystemVersionId, theCodeSystemVersion, theCodeSystemResourceTable);
	}

	@Override
	public IIdType storeNewCodeSystemVersion(CodeSystem codeSystem, TermCodeSystemVersion termCodeSystemVersion, RequestDetails requestDetails,
			List<ValueSet> valueSets, List<ConceptMap> conceptMaps) {

		FHIRHelper.readOnlyCheck(readOnlyMode);
		FHIRCodeSystemVersion codeSystemVersion;
		codeSystem.setContent(CodeSystem.CodeSystemContentMode.COMPLETE);
		if (codeSystem.getUrl().startsWith(FHIRConstants.ICD10_URI)) {
			codeSystem.setHierarchyMeaning(CodeSystem.CodeSystemHierarchyMeaning.ISA);
		}
		codeSystemVersion = fhirCodeSystemService.createUpdate(codeSystem);
		fhirConceptService.saveAllConceptsOfCodeSystemVersion(termCodeSystemVersion, codeSystemVersion);

		valueSets = orEmpty(valueSets);
		logger.info("{} ValueSets found", valueSets.size());
		fhirValueSetService.saveAllValueSetsOfCodeSystemVersionWithoutExpandValidation(valueSets);

		conceptMaps = orEmpty(conceptMaps);
		logger.info("{} ConceptMaps found", conceptMaps.size());
		for (ConceptMap conceptMap : conceptMaps) {
			try {
				FHIRConceptMap map = new FHIRConceptMap(conceptMap);
				fhirConceptMapProvider.createMap(map);
			} catch (SnowstormFHIRServerResponseException e) {
				logger.error("Failed to store ConceptMap {}", conceptMap.getIdElement(), e);
			}
		}

		logger.info("Code System import complete for {}.", codeSystem.getUrl());
		return new IdType("CodeSystem", codeSystemVersion.getId(), codeSystemVersion.getVersion());
	}

	@Override
	public void storeNewCodeSystemVersionIfNeeded(CodeSystem codeSystem, ResourceTable resourceTable, RequestDetails requestDetails) {
	}

	@Override
	public UploadStatistics applyDeltaCodeSystemsAdd(String s, CustomTerminologySet customTerminologySet) {
		return null;
	}

	@Override
	public UploadStatistics applyDeltaCodeSystemsRemove(String s, CustomTerminologySet customTerminologySet) {
		return null;
	}

	@Override
	public int saveConcept(TermConcept termConcept) {
		return 0;
	}

}
