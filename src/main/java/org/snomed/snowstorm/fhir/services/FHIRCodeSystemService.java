package org.snomed.snowstorm.fhir.services;

import org.hl7.fhir.r4.model.CodeSystem;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.core.data.domain.CodeSystemVersion;
import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.core.data.services.CodeSystemService;
import org.snomed.snowstorm.core.data.services.ConceptService;
import org.snomed.snowstorm.core.data.services.MultiSearchService;
import org.snomed.snowstorm.core.data.services.pojo.ConceptCriteria;
import org.snomed.snowstorm.core.pojo.LanguageDialect;
import org.snomed.snowstorm.fhir.domain.FHIRCodeSystemVersion;
import org.snomed.snowstorm.fhir.pojo.ConceptAndSystemResult;
import org.snomed.snowstorm.fhir.pojo.FHIRCodeSystemVersionParams;
import org.snomed.snowstorm.fhir.repositories.FHIRCodeSystemRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static java.lang.String.format;
import static org.snomed.snowstorm.fhir.services.FHIRHelper.exception;

@Service
public class FHIRCodeSystemService {

	public static final String SCT_ID_PREFIX = "sct_";

	@Autowired
	private FHIRCodeSystemRepository codeSystemRepository;

	@Autowired
	private FHIRConceptService conceptService;

	@Autowired
	private CodeSystemService snomedCodeSystemService;

	@Autowired
	private ConceptService snomedConceptService;

	@Autowired
	private MultiSearchService snomedMultiSearchService;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public FHIRCodeSystemVersion save(CodeSystem codeSystem) {
		FHIRCodeSystemVersion fhirCodeSystemVersion = new FHIRCodeSystemVersion(codeSystem);

		// Prevent saving SNOMED CT this way
		if (fhirCodeSystemVersion.getId().startsWith(SCT_ID_PREFIX)) {
			throw exception(format("Code System id prefix '%s' is reserved for SNOMED CT code system. " +
					"Please save these via the native API RF2 import function.", SCT_ID_PREFIX), OperationOutcome.IssueType.NOTSUPPORTED, 400);
		}

		wrap(fhirCodeSystemVersion);
		logger.debug("Saving fhir code system '{}'.", fhirCodeSystemVersion.getId());
		codeSystemRepository.save(fhirCodeSystemVersion);
		return fhirCodeSystemVersion;
	}

	public FHIRCodeSystemVersion findCodeSystemVersionOrThrow(FHIRCodeSystemVersionParams systemVersionParams) {
		FHIRCodeSystemVersion codeSystemVersion = findCodeSystemVersion(systemVersionParams);
		if (codeSystemVersion == null) {
			throw exception(format("Code system not found for parameters %s.", systemVersionParams), OperationOutcome.IssueType.NOTFOUND, 400);
		}

		if (systemVersionParams.getId() != null) {
			// Crosscheck version found by id against any other system params
			String requestedCodeSystemUrl = systemVersionParams.getCodeSystem();
			if (requestedCodeSystemUrl != null && !requestedCodeSystemUrl.equals(codeSystemVersion.getUrl())) {
				throw exception(String.format("The requested system URL '%s' does not match the URL '%s' of the code system found using identifier '%s'.",
						requestedCodeSystemUrl, codeSystemVersion.getUrl(), codeSystemVersion.getId()), OperationOutcome.IssueType.INVALID, 400);
			}
			String requestedVersion = systemVersionParams.getVersion();
			if (requestedVersion != null && !requestedVersion.isEmpty() && !requestedVersion.equals(codeSystemVersion.getVersion())) {
				throw exception(String.format("The requested version '%s' does not match the version '%s' of the code system found using identifier '%s'.",
						requestedVersion, codeSystemVersion.getVersion(), codeSystemVersion.getId()), OperationOutcome.IssueType.INVALID, 400);
			}
		}

		return codeSystemVersion;
	}

	public FHIRCodeSystemVersion findCodeSystemVersion(FHIRCodeSystemVersionParams systemVersionParams) {
		FHIRCodeSystemVersion version;

		if (systemVersionParams.isSnomed()) {
			version = getSnomedVersion(systemVersionParams);
		} else {
			String id = systemVersionParams.getId();
			String versionParam = systemVersionParams.getVersion();
			if (id != null) {// ID is unique, version not needed
				version = codeSystemRepository.findById(id).orElse(null);
			} else if (versionParam != null) {
				version = codeSystemRepository.findByUrlAndVersion(systemVersionParams.getCodeSystem(), versionParam);
			} else {
				version = codeSystemRepository.findFirstByUrlOrderByVersionDesc(systemVersionParams.getCodeSystem());
			}
		}

		unwrap(version);
		return version;
	}

	public FHIRCodeSystemVersion getSnomedVersion(FHIRCodeSystemVersionParams params) {
		if (!params.isSnomed()) {
			throw exception("Failed to find SNOMED branch for non SCT code system.", OperationOutcome.IssueType.CONFLICT, 500);
		}

		org.snomed.snowstorm.core.data.domain.CodeSystem snomedCodeSystem;
		String snomedModule = params.getSnomedModule();
		if (snomedModule != null) {
			snomedCodeSystem = snomedCodeSystemService.findByDefaultModule(snomedModule);
		} else {
			// Use root code system
			snomedCodeSystem = snomedCodeSystemService.find(CodeSystemService.SNOMEDCT);
		}
		if (snomedCodeSystem == null) {
			throw exception(format("The requested CodeSystem %s was not found.", params.toDiagnosticString()), OperationOutcome.IssueType.NOTFOUND, 404);
		}
		if (params.isUnversionedSnomed()) {
			// Use working branch
			return new FHIRCodeSystemVersion(snomedCodeSystem, true);
		} else {
			String shortName = snomedCodeSystem.getShortName();
			String version = params.getVersion();
			CodeSystemVersion snomedVersion;
			if (version == null) {
				// Use the latest published branch
				snomedVersion = snomedCodeSystemService.findLatestVisibleVersion(shortName);
				if (snomedVersion == null) {
					// Fall back to any imported version
					snomedVersion = snomedCodeSystemService.findLatestImportedVersion(shortName);
				}
				if (snomedVersion == null) {
					throw exception(format("The latest version of the requested CodeSystem %s was not found.", params.toDiagnosticString()),
							OperationOutcome.IssueType.NOTFOUND, 404);
				}
			} else {
				snomedVersion = snomedCodeSystemService.findVersion(shortName, Integer.parseInt(version));
				if (snomedVersion == null) {
					throw exception(format("The requested CodeSystem version %s was not found.", params.toDiagnosticString()), OperationOutcome.IssueType.NOTFOUND, 404);
				}
			}
			snomedVersion.setCodeSystem(snomedCodeSystem);
			return new FHIRCodeSystemVersion(snomedVersion);
		}
	}

	private void wrap(FHIRCodeSystemVersion fhirCodeSystemVersion) {
		if (fhirCodeSystemVersion.getVersion() == null) {
			fhirCodeSystemVersion.setVersion("");
		}
	}

	private void unwrap(FHIRCodeSystemVersion version) {
		if (version != null && "".equals(version.getVersion())) {
			version.setVersion(null);
		}
	}

	public Iterable<FHIRCodeSystemVersion> findAll() {
		return codeSystemRepository.findAll();
	}

	public Optional<FHIRCodeSystemVersion> findById(String id) {
		return codeSystemRepository.findById(id);
	}

	public void deleteCodeSystemVersion(String idWithVersion) {
		Optional<FHIRCodeSystemVersion> version = codeSystemRepository.findById(idWithVersion);
		if (version.isPresent()) {
			conceptService.deleteExistingCodes(idWithVersion);
			codeSystemRepository.deleteById(idWithVersion);
		}
	}

	public ConceptAndSystemResult findSnomedConcept(String code, List<LanguageDialect> languageDialects, FHIRCodeSystemVersionParams codeSystemParams) {

		Concept concept;
		FHIRCodeSystemVersion codeSystemVersion;
		if (codeSystemParams.isUnspecifiedReleasedSnomed()) {
			// Multi-search is expensive, so we'll try on default branch first
			codeSystemVersion = getSnomedVersion(codeSystemParams);
			concept = snomedConceptService.find(code, languageDialects, codeSystemVersion.getSnomedBranch());
			if (concept == null) {
				// Multi-search
				ConceptCriteria criteria = new ConceptCriteria().conceptIds(Collections.singleton(code));
				List<Concept> content = snomedMultiSearchService.findConcepts(criteria, PageRequest.of(0, 1)).getContent();
				if (!content.isEmpty()) {
					Concept bareConcept = content.get(0);
					// Recover published version where this concept was found
					CodeSystemVersion systemVersion = snomedMultiSearchService.getNearestPublishedVersion(bareConcept.getPath());
					if (systemVersion != null) {
						codeSystemVersion = new FHIRCodeSystemVersion(systemVersion);
						// Load whole concept for this code
						concept = snomedConceptService.find(code, languageDialects, codeSystemVersion.getSnomedBranch());
					}
				}
			}
		} else {
			codeSystemVersion = getSnomedVersion(codeSystemParams);
			concept = snomedConceptService.find(code, languageDialects, codeSystemVersion.getSnomedBranch());
		}
		return new ConceptAndSystemResult(concept, codeSystemVersion);
	}

	public boolean conceptExistsOrThrow(String code, FHIRCodeSystemVersion codeSystemVersion) {
		if (codeSystemVersion.isSnomed()) {
			if (!snomedConceptService.exists(code, codeSystemVersion.getSnomedBranch())) {
				throwCodeNotFound(code, codeSystemVersion);
			}
		} else if (conceptService.findConcept(codeSystemVersion, code) == null) {
			throwCodeNotFound(code, codeSystemVersion);
		}
		return true;
	}

	private void throwCodeNotFound(String code, FHIRCodeSystemVersion codeSystemVersion) {
		throw exception(String.format("Code '%s' was not found in code system '%s'.", code, codeSystemVersion), OperationOutcome.IssueType.INVALID, 400);
	}
}
