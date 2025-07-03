package org.snomed.snowstorm.fhir.services;

import org.hl7.fhir.r4.model.CodeSystem;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.ihtsdo.drools.helper.IdentifierHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.core.data.domain.CodeSystemVersion;
import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.core.data.repositories.CodeSystemVersionRepository;
import org.snomed.snowstorm.core.data.services.CodeSystemService;
import org.snomed.snowstorm.core.data.services.ConceptService;
import org.snomed.snowstorm.core.data.services.MultiSearchService;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.snomed.snowstorm.core.data.services.identifier.IdentifierService;
import org.snomed.snowstorm.core.data.services.identifier.IdentifierSource;
import org.snomed.snowstorm.core.data.services.pojo.ConceptCriteria;
import org.snomed.snowstorm.core.pojo.LanguageDialect;
import org.snomed.snowstorm.fhir.domain.FHIRCodeSystemVersion;
import org.snomed.snowstorm.fhir.pojo.CanonicalUri;
import org.snomed.snowstorm.fhir.pojo.ConceptAndSystemResult;
import org.snomed.snowstorm.fhir.pojo.FHIRCodeSystemVersionParams;
import org.snomed.snowstorm.fhir.repositories.FHIRCodeSystemRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.*;

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

	@Autowired
	private IdentifierSource identifierSource;

	private final Logger logger = LoggerFactory.getLogger(getClass());
	@Autowired
	private CodeSystemVersionRepository codeSystemVersionRepository;

	public FHIRCodeSystemVersion createUpdate(CodeSystem codeSystem) {
		FHIRCodeSystemVersion fhirCodeSystemVersion = new FHIRCodeSystemVersion(codeSystem);

		// Default version to 0
		if (fhirCodeSystemVersion.getVersion() == null && !FHIRHelper.isSnomedUri(fhirCodeSystemVersion.getUrl())) {
			fhirCodeSystemVersion.setVersion("0");
		}

		if (codeSystem.getContent() == CodeSystem.CodeSystemContentMode.SUPPLEMENT) {
			// Attempt to process SNOMED CT code system supplement / expression repository

			/*
			 * Validation
			 */
			// if not SNOMED
			if (!FHIRHelper.isSnomedUri(fhirCodeSystemVersion.getUrl())) {
				throw exception("At this time this server only supports CodeSystem supplements that supplement SNOMED CT",
						OperationOutcome.IssueType.NOTSUPPORTED, 400);
			}
			// if no dependency
			if (!codeSystem.hasSupplements()) {
				throw exception("SNOMED CT CodeSystem supplements must declare which SNOMED CT edition and version they supplement " +
						"using the 'supplements' property.", OperationOutcome.IssueType.INVARIANT, 400);
			}
			// Check dependency is SNOMED
			FHIRCodeSystemVersionParams dependencyParams = FHIRHelper.getCodeSystemVersionParams(CanonicalUri.fromString(codeSystem.getSupplements()));
			if (!dependencyParams.isSnomed() || dependencyParams.isUnversionedSnomed()
					|| dependencyParams.isUnspecifiedReleasedSnomed() || dependencyParams.getVersion() == null) {
				throw exception(format("The CodeSystem supplement must be a canonical URL with the SNOMED CT code system and a version using the SNOMED CT URI standard to " +
								"quote a specific version of a specific edition. For example: http://snomed.info/sct|http://snomed.info/sct/900000000000207008/version/%s0131",
						new GregorianCalendar().get(Calendar.YEAR)), OperationOutcome.IssueType.INVARIANT, 400);
			}
			// Check dependency exists
			FHIRCodeSystemVersion dependantVersion = getSnomedVersionOrThrow(dependencyParams);

			// Does the requested code system already exist?
			FHIRCodeSystemVersionParams existingCodeSystemParams = FHIRHelper.getCodeSystemVersionParams(codeSystem.getUrl(), codeSystem.getVersion());
			String snomedModule = existingCodeSystemParams.getSnomedModule();
			if (!IdentifierHelper.isConceptId(snomedModule) || snomedModule.length() < 10) {// Long format concept identifier including namespace.
				Long suggestedModuleConceptId = null;
				if (snomedModule != null && snomedModule.length() == 7) {
					// The module id is actually a SNOMED CT namespace... generate concept id that could be used
					int namespace = Integer.parseInt(snomedModule);
					try {
						List<Long> conceptIds = identifierSource.reserveIds(namespace, IdentifierService.EXTENSION_CONCEPT_PARTITION_ID, 1);
						if (!conceptIds.isEmpty()) {
							suggestedModuleConceptId = conceptIds.get(0);
						}
					} catch (ServiceException e) {
						logger.warn("Failed to generate a concept id using assumed namespace '{}'", namespace, e);
					}
				}
				if (suggestedModuleConceptId != null) {
					throw exception(format("The URL of this SNOMED CT CodeSystem supplement must have a version that follows the SNOMED CT URI standard and includes a module id." +
											" If a namespace was given in the version URL then the module id '%s' could be used." +
											" This id has been generated using the namespace given and is the next id sequence, considering all content currently loaded into Snowstorm.",
									suggestedModuleConceptId),
							OperationOutcome.IssueType.INVARIANT, 400);
				} else {
					throw exception("The URL of this SNOMED CT CodeSystem supplement must have a version that follows the SNOMED CT URI standard and includes a module id.",
							OperationOutcome.IssueType.INVARIANT, 400);
				}
			}
			org.snomed.snowstorm.core.data.domain.CodeSystem existingCodeSystem = snomedCodeSystemService.findByUriModule(snomedModule);
			if (existingCodeSystem != null) {
				throw exception("Updating SNOMED CT code system supplements is not yet supported.", OperationOutcome.IssueType.NOTSUPPORTED, 400);
			}

			org.snomed.snowstorm.core.data.domain.CodeSystem newCodeSystem = new org.snomed.snowstorm.core.data.domain.CodeSystem();
			org.snomed.snowstorm.core.data.domain.CodeSystem dependentCodeSystem = dependantVersion.getSnomedCodeSystem();
			newCodeSystem.setShortName(dependentCodeSystem.getShortName() + "-EXP");
			newCodeSystem.setBranchPath(String.join("/", dependentCodeSystem.getBranchPath(), newCodeSystem.getShortName()));
			newCodeSystem.setUriModuleId(snomedModule);
			org.snomed.snowstorm.core.data.domain.CodeSystem savedCodeSystem = snomedCodeSystemService.createCodeSystem(newCodeSystem);
			return new FHIRCodeSystemVersion(savedCodeSystem, true);
		} else {// Not Supplement
			// Check not SNOMED id
			if (fhirCodeSystemVersion.getId().startsWith(SCT_ID_PREFIX)) {
				throw exception(format("Code System id prefix '%s' is reserved for SNOMED CT code systems. " +
						"Please create and import these via the native API.", SCT_ID_PREFIX), OperationOutcome.IssueType.NOTSUPPORTED, 400);
			}
			// Check not SNOMED URL
			if (FHIRHelper.isSnomedUri(fhirCodeSystemVersion.getUrl())) {
				throw exception(format("Code System url '%s' is reserved for SNOMED CT code systems. " +
						"Please create and import these via the native API or mark the code system as a supplement.",
								fhirCodeSystemVersion.getUrl()), OperationOutcome.IssueType.NOTSUPPORTED, 400);
			}

			if (fhirCodeSystemVersion.getVersion() == null) {
				fhirCodeSystemVersion.setVersion("0");
			}

			wrap(fhirCodeSystemVersion);
			logger.debug("Saving fhir code system '{}'.", fhirCodeSystemVersion.getId());

			FHIRCodeSystemVersion existingCodeSystem = codeSystemRepository.findByUrlAndVersion(fhirCodeSystemVersion.getUrl(), fhirCodeSystemVersion.getVersion());
			if (existingCodeSystem != null) {
				// Prevent changing the id
				if (codeSystem.getId() != null && !codeSystem.getIdElement().getIdPart().equals(existingCodeSystem.getId())) {
					throw exception("A CodeSystem with the same URL and version already exists but with a different id. To change the id please delete the existing CodeSystem first.",
							OperationOutcome.IssueType.INVARIANT, 400);
				}
				codeSystemRepository.delete(existingCodeSystem);
			}
			codeSystemRepository.save(fhirCodeSystemVersion);
			return fhirCodeSystemVersion;
		}
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
			version = getSnomedVersionOrThrow(systemVersionParams);
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

	public FHIRCodeSystemVersion getSnomedVersionOrThrow(FHIRCodeSystemVersionParams params) {
		if (!params.isSnomed()) {
			throw exception("Failed to find SNOMED branch for non SCT code system.", OperationOutcome.IssueType.CONFLICT, 500);
		}

		org.snomed.snowstorm.core.data.domain.CodeSystem snomedCodeSystem;
		String snomedModule = params.getSnomedModule();
		if (snomedModule != null) {
			snomedCodeSystem = snomedCodeSystemService.findByUriModule(snomedModule);
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

	public List<CodeSystemVersion> findAllSnomedVersions() {
		List<org.snomed.snowstorm.core.data.domain.CodeSystem> editions = snomedCodeSystemService.findAll();
		List<CodeSystemVersion> allVersions = new ArrayList<>();
		for (org.snomed.snowstorm.core.data.domain.CodeSystem edition : editions) {
			List<CodeSystemVersion> editionVersions = snomedCodeSystemService.findAllVersions(edition.getShortName(), true, true);
			for (CodeSystemVersion editionVersion : editionVersions) {
				editionVersion.setCodeSystem(edition);
			}
			allVersions.addAll(editionVersions);
		}
		return allVersions;
	}

	public Optional<FHIRCodeSystemVersion> findById(String id) {
		return codeSystemRepository.findById(id);
	}

	public void deleteCodeSystemVersion(FHIRCodeSystemVersion codeSystemVersion) {
		if (codeSystemVersion == null) {
			return;
		}
		if (FHIRHelper.isSnomedUri(codeSystemVersion.getUrl())) {
			// Only allow deletion of expression repositories
				throw FHIRHelper.exception("Please use the native API to maintain SNOMED CT code systems.",
						OperationOutcome.IssueType.NOTSUPPORTED, 400);
		} else {
			String versionId = codeSystemVersion.getId();
			logger.info("Deleting code system (version) {}", versionId);
			conceptService.deleteExistingCodes(versionId);
			codeSystemRepository.deleteById(versionId);
		}
	}

	public ConceptAndSystemResult findSnomedConcept(String code, List<LanguageDialect> languageDialects, FHIRCodeSystemVersionParams codeSystemParams) {

		Concept concept;
		FHIRCodeSystemVersion codeSystemVersion;
		if (codeSystemParams.isUnspecifiedReleasedSnomed()) {
			// Multi-search is expensive, so we'll try on default branch first
			codeSystemVersion = getSnomedVersionOrThrow(codeSystemParams);
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
			codeSystemVersion = getSnomedVersionOrThrow(codeSystemParams);
			concept = snomedConceptService.find(code, languageDialects, codeSystemVersion.getSnomedBranch());
		}
		return new ConceptAndSystemResult(concept, codeSystemVersion);
	}

	public boolean conceptExistsOrThrow(String code, FHIRCodeSystemVersion codeSystemVersion) {
		if (codeSystemVersion.isOnSnomedBranch()) {
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
