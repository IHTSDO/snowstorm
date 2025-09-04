package org.snomed.snowstorm.fhir.services;

import co.elastic.clients.elasticsearch._types.query_dsl.*;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.*;
import org.ihtsdo.drools.helper.IdentifierHelper;
import org.jetbrains.annotations.NotNull;
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
import org.snomed.snowstorm.fhir.domain.FHIRConcept;
import org.snomed.snowstorm.fhir.domain.FHIRExtension;
import org.snomed.snowstorm.fhir.pojo.CanonicalUri;
import org.snomed.snowstorm.fhir.pojo.ConceptAndSystemResult;
import org.snomed.snowstorm.fhir.pojo.FHIRCodeSystemVersionParams;
import org.snomed.snowstorm.fhir.repositories.FHIRCodeSystemRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.stereotype.Service;

import java.util.*;

import static java.lang.String.format;
import static org.snomed.snowstorm.core.util.SearchAfterQueryHelper.updateQueryWithSearchAfter;
import static org.snomed.snowstorm.fhir.services.FHIRHelper.exception;
import static org.snomed.snowstorm.fhir.utils.FHIRPageHelper.toPage;

@Service
public class FHIRCodeSystemService {

	public static final String SCT_ID_PREFIX = "sct_";
	private static final int PAGESIZE = 1_000;

	@Autowired
	private ElasticsearchOperations elasticsearchOperations;

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

	public FHIRCodeSystemVersion createUpdate(CodeSystem codeSystem) throws ServiceException {
		FHIRCodeSystemVersion fhirCodeSystemVersion = new FHIRCodeSystemVersion(codeSystem);

		// Default version to 0
		if (fhirCodeSystemVersion.getVersion() == null && !FHIRHelper.isSnomedUri(fhirCodeSystemVersion.getUrl())) {
			fhirCodeSystemVersion.setVersion("0");
		}

		if (codeSystem.getContent() == CodeSystem.CodeSystemContentMode.SUPPLEMENT) {
			return createUpdateForSupplement(codeSystem, fhirCodeSystemVersion);
		} else {// Not Supplement
			return createUpdateForNotSupplement(codeSystem, fhirCodeSystemVersion);
		}
	}

	private @NotNull FHIRCodeSystemVersion createUpdateForNotSupplement(CodeSystem codeSystem, FHIRCodeSystemVersion fhirCodeSystemVersion) {
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

	private @NotNull FHIRCodeSystemVersion createUpdateForSupplement(CodeSystem codeSystem, FHIRCodeSystemVersion fhirCodeSystemVersion) throws ServiceException {
		// Attempt to process SNOMED CT code system supplement / expression repository
		if (!FHIRHelper.isSnomedUri(fhirCodeSystemVersion.getUrl())) {
			return handleNotSnomedSupplement(codeSystem, fhirCodeSystemVersion);
		}
		return handleSnomedSupplement(codeSystem);
	}

	private @NotNull FHIRCodeSystemVersion handleSnomedSupplement(CodeSystem codeSystem) {

		validateHasSupplements(codeSystem);

		FHIRCodeSystemVersionParams dependencyParams = parseAndValidateDependency(codeSystem);

		// Check dependency exists
		FHIRCodeSystemVersion dependantVersion = getSnomedVersionOrThrow(dependencyParams);

		// Validate requested code system params
		FHIRCodeSystemVersionParams existingCodeSystemParams = FHIRHelper.getCodeSystemVersionParams(codeSystem.getUrl(), codeSystem.getVersion());
		String snomedModule = validateSnomedModule(existingCodeSystemParams);

		// Ensure no duplicate
		ensureNoExistingCodeSystem(snomedModule);

		// Create new code system supplement
		org.snomed.snowstorm.core.data.domain.CodeSystem newCodeSystem =
				buildNewSupplementCodeSystem(dependantVersion.getSnomedCodeSystem(), snomedModule);

		org.snomed.snowstorm.core.data.domain.CodeSystem savedCodeSystem =
				snomedCodeSystemService.createCodeSystem(newCodeSystem);

		return new FHIRCodeSystemVersion(savedCodeSystem, true);
	}

	private void validateHasSupplements(CodeSystem codeSystem) {
		if (!codeSystem.hasSupplements()) {
			throw exception("SNOMED CT CodeSystem supplements must declare which SNOMED CT edition and version they supplement " +
							"using the 'supplements' property.",
					OperationOutcome.IssueType.INVARIANT, 400);
		}
	}

	private FHIRCodeSystemVersionParams parseAndValidateDependency(CodeSystem codeSystem) {
		FHIRCodeSystemVersionParams dependencyParams =
				FHIRHelper.getCodeSystemVersionParams(CanonicalUri.fromString(codeSystem.getSupplements()));
		if (!dependencyParams.isSnomed()
				|| dependencyParams.isUnversionedSnomed()
				|| dependencyParams.isUnspecifiedReleasedSnomed()
				|| dependencyParams.getVersion() == null) {
			throw exception(format("The CodeSystem supplement must be a canonical URL with the SNOMED CT code system and a version using the SNOMED CT URI standard to " +
									"quote a specific version of a specific edition. For example: http://snomed.info/sct|http://snomed.info/sct/900000000000207008/version/%s0131",
							new GregorianCalendar().get(Calendar.YEAR)),
					OperationOutcome.IssueType.INVARIANT, 400);
		}
		return dependencyParams;
	}

	private String validateSnomedModule(FHIRCodeSystemVersionParams existingCodeSystemParams) {
		String snomedModule = existingCodeSystemParams.getSnomedModule();
		if (!IdentifierHelper.isConceptId(snomedModule) || snomedModule.length() < 10) {
			Long suggestedModuleConceptId = tryGenerateSuggestedModuleId(snomedModule);
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
		return snomedModule;
	}

	private Long tryGenerateSuggestedModuleId(String snomedModule) {
		if (snomedModule != null && snomedModule.length() == 7) {
			int namespace = Integer.parseInt(snomedModule);
			try {
				List<Long> conceptIds = identifierSource.reserveIds(namespace, IdentifierService.EXTENSION_CONCEPT_PARTITION_ID, 1);
				if (!conceptIds.isEmpty()) {
					return conceptIds.get(0);
				}
			} catch (ServiceException e) {
				logger.warn("Failed to generate a concept id using assumed namespace '{}'", namespace, e);
			}
		}
		return null;
	}

	private void ensureNoExistingCodeSystem(String snomedModule) {
		org.snomed.snowstorm.core.data.domain.CodeSystem existingCodeSystem =
				snomedCodeSystemService.findByUriModule(snomedModule);
		if (existingCodeSystem != null) {
			throw exception("A code system supplement with the same URL and version already exists. Updating SNOMED CT code system supplements is not yet supported.",
					OperationOutcome.IssueType.NOTSUPPORTED, 400);
		}
	}

	private org.snomed.snowstorm.core.data.domain.CodeSystem buildNewSupplementCodeSystem(
			org.snomed.snowstorm.core.data.domain.CodeSystem dependentCodeSystem,
			String snomedModule) {

		org.snomed.snowstorm.core.data.domain.CodeSystem newCodeSystem =
				new org.snomed.snowstorm.core.data.domain.CodeSystem();

		String shortName = dependentCodeSystem.getShortName() + "-EXP";
		newCodeSystem.setShortName(shortName);

		// Append 2,3,4 etc to ensure uniqueness
		int a = 2;
		while (snomedCodeSystemService.find(newCodeSystem.getShortName()) != null) {
			newCodeSystem.setShortName(shortName + a);
			a++;
		}

		newCodeSystem.setName("SNOMED CT Postcoordinated Expression Repository");
		newCodeSystem.setBranchPath(String.join("/", dependentCodeSystem.getBranchPath(), newCodeSystem.getShortName()));
		newCodeSystem.setUriModuleId(snomedModule);

		return newCodeSystem;
	}


	private @NotNull FHIRCodeSystemVersion handleNotSnomedSupplement(CodeSystem supplement, FHIRCodeSystemVersion fhirCodeSystemVersion) throws ServiceException {
		// if no dependency
		if (!supplement.hasSupplements()) {
			throw exception("CodeSystem supplements must declare which codesystem they supplement " +
					"using the 'supplements' property.", OperationOutcome.IssueType.INVARIANT, 400);
		}
		// Check dependency is SNOMED
		FHIRCodeSystemVersionParams dependencyParams = FHIRHelper.getCodeSystemVersionParams(CanonicalUri.fromString(supplement.getSupplements()));
		if (dependencyParams.isSnomed()) {
			throw exception("Non SNOMED supplements cannot supplement a SNOMED codesystem on this server", OperationOutcome.IssueType.INVARIANT, 400);
		}
		// Check dependency exists
		FHIRCodeSystemVersion dependentVersion = findCodeSystemVersionOrThrow(dependencyParams);

		CodeSystem updatedCodeSystem = codeSystemRepository.findByUrlAndVersion(dependentVersion.getUrl(), dependentVersion.getVersion()).toHapiCodeSystem();

		List<Extension> extensions = new ArrayList<>(supplement.getExtension());
		Extension supExt = new Extension();
		supExt.setUrl("https://github.com/IHTSDO/snowstorm/codesystem-supplement");
		supExt.setValue(new CanonicalType(fhirCodeSystemVersion.getCanonical()));
		extensions.add(supExt);
		extensions.addAll(updatedCodeSystem.getExtension());
		updatedCodeSystem.setExtension(extensions);
        return createUpdate(updatedCodeSystem);
	}

	public @NotNull CodeSystem addSupplementToCodeSystem(CodeSystem codeSystem, FHIRCodeSystemVersion dependentVersion) {
		CodeSystem newCodeSystem = fetchCodeSystem(dependentVersion);

		List<FHIRConcept> conceptsList = fetchAllConcepts(dependentVersion);

		FHIRGraphBuilder graphBuilder = buildHierarchyGraph(dependentVersion, conceptsList);

		List<CodeSystem.ConceptDefinitionComponent> conceptComponents = buildConceptComponents(conceptsList, dependentVersion);

		linkParentChildRelationships(conceptComponents, graphBuilder);

		// Keep only root nodes
		conceptComponents = conceptComponents.stream()
				.filter(x -> graphBuilder.getNodeParents(x.getCode()).isEmpty())
				.toList();

		newCodeSystem.setConcept(conceptComponents);

		mergeExistingConcepts(codeSystem, newCodeSystem);

		return newCodeSystem;
	}

// -------------------- Helpers --------------------

	private CodeSystem fetchCodeSystem(FHIRCodeSystemVersion dependentVersion) {
		return codeSystemRepository
				.findByUrlAndVersion(dependentVersion.getUrl(), dependentVersion.getVersion())
				.toHapiCodeSystem();
	}

	private List<FHIRConcept> fetchAllConcepts(FHIRCodeSystemVersion dependentVersion) {
		Page<FHIRConcept> conceptsPage = conceptService.findConcepts(dependentVersion.getId(), PageRequest.of(0, PAGESIZE));
		List<FHIRConcept> conceptsList = new ArrayList<>();

		for (int x = 0; x < conceptsPage.getTotalPages(); x++) {
			conceptsList.addAll(conceptsPage.getContent());
			conceptsPage = conceptService.findConcepts(dependentVersion.getId(), PageRequest.of(x, PAGESIZE));
		}
		return conceptsList;
	}

	private FHIRGraphBuilder buildHierarchyGraph(FHIRCodeSystemVersion dependentVersion, List<FHIRConcept> conceptsList) {
		FHIRGraphBuilder graphBuilder = new FHIRGraphBuilder();
		if (Objects.isNull(dependentVersion.getHierarchyMeaning()) || "is-a".equals(dependentVersion.getHierarchyMeaning())) {
			for (FHIRConcept concept : conceptsList) {
				for (String parentCode : concept.getParents()) {
					graphBuilder.addParent(concept.getCode(), parentCode);
				}
			}
		}
		return graphBuilder;
	}

	private List<CodeSystem.ConceptDefinitionComponent> buildConceptComponents(List<FHIRConcept> conceptsList, FHIRCodeSystemVersion dependentVersion) {
		return conceptsList.stream()
				.map(concept -> {
					CodeSystem.ConceptDefinitionComponent component = new CodeSystem.ConceptDefinitionComponent();

					List<CodeSystem.ConceptDefinitionDesignationComponent> designations = concept.getDesignations().stream()
							.map(fd -> {
								CodeSystem.ConceptDefinitionDesignationComponent cd = new CodeSystem.ConceptDefinitionDesignationComponent();
								cd.setLanguage(fd.getLanguage());
								if (StringUtils.isNotEmpty(fd.getUse())) cd.setUse(fd.getUseCoding());
								cd.setValue(fd.getValue());
								fd.getExtensions().forEach(fhirExtension -> cd.addExtension(fhirExtension.getHapi()));
								return cd;
							}).toList();

					concept.getProperties().forEach((key, value) -> value.forEach(p -> {
						CodeSystem.ConceptPropertyComponent propertyComponent = new CodeSystem.ConceptPropertyComponent();
						propertyComponent.setCode(p.getCode());
						propertyComponent.setValue(p.toHapiValue(dependentVersion.getUrl()));
						component.addProperty(propertyComponent);
					}));

					concept.getExtensions().forEach((key, value) -> value.forEach(e -> {
						Extension t = new Extension();
						t.setUrl(e.getCode());
						t.setValue(e.toHapiValue(null));
						component.addExtension(t);
					}));

					component.setDesignation(designations)
							.setCode(concept.getCode())
							.setDisplay(concept.getDisplay())
							.setId(concept.getId());

					return component;
				}).toList();
	}

	private void linkParentChildRelationships(List<CodeSystem.ConceptDefinitionComponent> conceptComponents, FHIRGraphBuilder graphBuilder) {
		List<CodeSystem.ConceptDefinitionComponent> finalConcepts = conceptComponents;
		conceptComponents.forEach(x -> {
			Collection<String> children = graphBuilder.getNodeChildren(x.getCode());
			List<CodeSystem.ConceptDefinitionComponent> toAdd = finalConcepts.stream()
					.filter(y -> children.contains(y.getCode()))
					.toList();
			toAdd.forEach(x::addConcept);
		});
	}

	private void mergeExistingConcepts(CodeSystem originalCodeSystem, CodeSystem newCodeSystem) {
		List<CodeSystem.ConceptDefinitionComponent> modifiedConceptDefinitions = newCodeSystem.getConcept().stream()
				.map(conceptToUpdate -> {
					Optional<CodeSystem.ConceptDefinitionComponent> match = originalCodeSystem.getConcept().stream()
							.filter(y -> y.getCode().equals(conceptToUpdate.getCode()))
							.findFirst();
					match.ifPresent(existing -> {
						try {
							existing.getExtension().forEach(conceptToUpdate::addExtension);
							existing.getProperty().forEach(conceptToUpdate::addProperty);

							List<CodeSystem.ConceptDefinitionDesignationComponent> newList = new ArrayList<>(conceptToUpdate.getDesignation());
							newList.addAll(existing.getDesignation());
							conceptToUpdate.setDesignation(newList);
						} catch (RuntimeException e) {
							logger.error("Recoverable error while merging supplement concepts to CodeSystem {}", originalCodeSystem, e);
						}
					});
					return conceptToUpdate;
				}).toList();

		newCodeSystem.setConcept(modifiedConceptDefinitions);
	}


	public FHIRCodeSystemVersion findCodeSystemVersionOrThrow(FHIRCodeSystemVersionParams systemVersionParams) {
		FHIRCodeSystemVersion codeSystemVersion = findCodeSystemVersion(systemVersionParams);
		if (codeSystemVersion == null) {
			throwExceptionForMissingCodeSystemVersion(systemVersionParams);
		}

		if (systemVersionParams.getId() != null) {
			verifySystemVersionParams(systemVersionParams, codeSystemVersion);
		}

		return codeSystemVersion;
	}

	private static void verifySystemVersionParams(FHIRCodeSystemVersionParams systemVersionParams, FHIRCodeSystemVersion codeSystemVersion) {
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

	private void throwExceptionForMissingCodeSystemVersion(FHIRCodeSystemVersionParams systemVersionParams) {
		String codeSystem = systemVersionParams.getCodeSystem() + (systemVersionParams.getVersion() == null ? "*" : format("|%s", systemVersionParams.getVersion()));
		List<FHIRCodeSystemVersion> supplements = getSupplements(codeSystem, systemVersionParams.getVersion()==null);
		if(!supplements.isEmpty()){
			String codeSystemWithVersionIfFound = supplements.stream()
					.flatMap(supp -> supp.getExtensions().stream())
					.map(FHIRExtension::getValue)
					.filter(Objects::nonNull)
					.filter(ext -> ext.contains(systemVersionParams.getCodeSystem()))
					.findAny().orElse(systemVersionParams.getCodeSystem());
			String message = format("CodeSystem %s is a supplement, so can't be used as a value in Coding.system", codeSystemWithVersionIfFound);
			CodeableConcept cc = new CodeableConcept(new Coding("http://hl7.org/fhir/tools/CodeSystem/tx-issue-type", "invalid-data",null)).setText(message);
			OperationOutcome oo = FHIRHelper.createOperationOutcomeWithIssue(cc, OperationOutcome.IssueSeverity.ERROR, "Coding.system", OperationOutcome.IssueType.INVALID, Collections.singletonList(new Extension("http://hl7.org/fhir/StructureDefinition/operationoutcome-message-id", new StringType("CODESYSTEM_CS_NO_SUPPLEMENT"))), null);
			throw new SnowstormFHIRServerResponseException(400,message,oo);
		} else {
			FHIRCodeSystemVersion other = findCodeSystemVersion(new FHIRCodeSystemVersionParams(systemVersionParams.getCodeSystem()));
			String message = format("The CodeSystem %s version %s is unknown to this server. Valid versions: [%s]", systemVersionParams.getCodeSystem(), systemVersionParams.getVersion(), other==null?"":other.getVersion());
			CodeableConcept cc = new CodeableConcept(new Coding("http://hl7.org/fhir/tools/CodeSystem/tx-issue-type", "not-found",null)).setText(message);
			OperationOutcome oo = FHIRHelper.createOperationOutcomeWithIssue(cc, OperationOutcome.IssueSeverity.ERROR, "Coding.system", OperationOutcome.IssueType.NOTFOUND, Arrays.asList(new Extension("https://github.com/IHTSDO/snowstorm/missing-codesystem-version", new CanonicalType(CanonicalUri.of(systemVersionParams.getCodeSystem(), systemVersionParams.getVersion()).toString())),new Extension("https://github.com/IHTSDO/snowstorm/available-codesystem-version", new CanonicalType(other==null?"":other.getCanonical()))), null);
			throw new SnowstormFHIRServerResponseException(400,message,oo);
		}
	}

	public FHIRCodeSystemVersion findCodeSystemVersion(FHIRCodeSystemVersionParams systemVersionParams) {
		FHIRCodeSystemVersion version;

		if (systemVersionParams.isSnomed()) {
			version = getSnomedVersionOrThrow(systemVersionParams);
		} else {
			String id = systemVersionParams.getId();
			String versionParam = systemVersionParams.getVersion();
			String urlParam = systemVersionParams.getCodeSystem();
			if (id != null) { // version not needed if only one codeSystem possesses this id or if only latest version needed
				version = codeSystemRepository.findFirstByCodeSystemIdOrderByVersionDesc(id).orElse(null);
			} else if (versionParam != null && urlParam != null) {
				version = codeSystemRepository.findByUrlAndVersion(urlParam, versionParam);
			} else {
				version = codeSystemRepository.findFirstByUrlOrderByVersionDesc(urlParam);
			}
		}

		unwrap(version);
		return version;
	}

	public FHIRCodeSystemVersion getSnomedVersionOrThrow(FHIRCodeSystemVersionParams params) {
		if (!params.isSnomed()) {
			throw exception("Failed to find SNOMED branch for non SCT code system.",
					OperationOutcome.IssueType.CONFLICT, 500);
		}

		org.snomed.snowstorm.core.data.domain.CodeSystem snomedCodeSystem = resolveSnomedCodeSystem(params);

		if (params.isUnversionedSnomed()) {
			return new FHIRCodeSystemVersion(snomedCodeSystem, true);
		} else {
			return new FHIRCodeSystemVersion(resolveSnomedVersion(snomedCodeSystem, params));
		}
	}

	private org.snomed.snowstorm.core.data.domain.CodeSystem resolveSnomedCodeSystem(FHIRCodeSystemVersionParams params) {
		String snomedModule = params.getSnomedModule();
		org.snomed.snowstorm.core.data.domain.CodeSystem snomedCodeSystem = (snomedModule != null)
				? snomedCodeSystemService.findByUriModule(snomedModule)
				: snomedCodeSystemService.find(CodeSystemService.SNOMEDCT);

		if (snomedCodeSystem == null) {
			throw exception(format("The requested CodeSystem %s was not found.", params.toDiagnosticString()),
					OperationOutcome.IssueType.NOTFOUND, 404);
		}
		return snomedCodeSystem;
	}

	private CodeSystemVersion resolveSnomedVersion(org.snomed.snowstorm.core.data.domain.CodeSystem snomedCodeSystem,
	                                               FHIRCodeSystemVersionParams params) {
		String shortName = snomedCodeSystem.getShortName();
		String version = params.getVersion();
		CodeSystemVersion snomedVersion;

		if (version == null) {
			snomedVersion = snomedCodeSystemService.findLatestVisibleVersion(shortName);
			if (snomedVersion == null) {
				snomedVersion = snomedCodeSystemService.findLatestImportedVersion(shortName);
			}
			if (snomedVersion == null) {
				throw exception(format("The latest version of the requested CodeSystem %s was not found.",
								params.toDiagnosticString()),
						OperationOutcome.IssueType.NOTFOUND, 404);
			}
		} else {
			snomedVersion = snomedCodeSystemService.findVersion(shortName, Integer.parseInt(version));
			if (snomedVersion == null) {
				throw exception(format("The requested CodeSystem version %s was not found.",
								params.toDiagnosticString()),
						OperationOutcome.IssueType.NOTFOUND, 404);
			}
		}

		snomedVersion.setCodeSystem(snomedCodeSystem);
		return snomedVersion;
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
		return codeSystemRepository.findFirstByCodeSystemIdOrderByVersionDesc(id);
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

	public boolean supplementExists(String value, boolean containsWildcard) {
		return !getSupplements(value, containsWildcard).isEmpty();
	}

	public List<FHIRCodeSystemVersion> getSupplements(String value, boolean containsWildcard) {
		try {
			NestedQuery.Builder nested = new NestedQuery.Builder();
			BoolQuery.Builder query = new BoolQuery.Builder();
			if (containsWildcard) {
				query.filter(new WildcardQuery.Builder().field("extensions.value").value(value).build()._toQuery());
			} else {
				query.filter(new TermQuery.Builder().field("extensions.value").value(value).build()._toQuery());
			}
			nested.path("extensions").query(query.build()._toQuery());
			return find(PageRequest.of(0, 100), nested.build()._toQuery()).toList();
		} catch (Exception e) {
			logger.error("Failed to find supplements for value: {}", value, e);
			return Collections.emptyList();
		}
	}

	public Page<FHIRCodeSystemVersion> find(PageRequest pageRequest, Query query) {
		NativeQuery searchQuery = new NativeQueryBuilder()
				.withQuery(query)
				.withPageable(pageRequest)
				.build();
		searchQuery.setTrackTotalHits(true);
		updateQueryWithSearchAfter(searchQuery, pageRequest);

		logger.debug("QUERY: {}", searchQuery.getQuery());
		return toPage(elasticsearchOperations.search(searchQuery, FHIRCodeSystemVersion.class), pageRequest);
	}
}
