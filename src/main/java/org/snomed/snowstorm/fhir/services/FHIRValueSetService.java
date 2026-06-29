package org.snomed.snowstorm.fhir.services;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.apache.commons.lang3.tuple.Pair;
import org.hl7.fhir.r4.model.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.core.data.domain.ConceptMini;
import org.snomed.snowstorm.core.data.domain.Concepts;
import org.snomed.snowstorm.core.data.domain.QueryConcept;
import org.snomed.snowstorm.core.data.services.*;
import org.snomed.snowstorm.core.pojo.LanguageDialect;
import org.snomed.snowstorm.core.util.SearchAfterPage;
import org.snomed.snowstorm.fhir.config.FHIRConstants;
import org.snomed.snowstorm.fhir.domain.*;
import org.snomed.snowstorm.fhir.domain.ConceptConstraint;
import org.snomed.snowstorm.fhir.pojo.CanonicalUri;
import org.snomed.snowstorm.fhir.pojo.FHIRCodeValidationRequest;
import org.snomed.snowstorm.fhir.pojo.ValueSetExpansionParameters;
import org.snomed.snowstorm.fhir.repositories.FHIRValueSetRepository;
import org.snomed.snowstorm.fhir.services.context.CodeSystemVersionProvider;
import org.snomed.snowstorm.rest.ControllerHelper;
import org.snomed.snowstorm.rest.pojo.SearchAfterPageRequest;
import org.springframework.data.domain.*;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders.bool;
import static io.kaicode.elasticvc.api.ComponentService.LARGE_PAGE;
import static io.kaicode.elasticvc.helper.QueryHelper.*;
import static java.lang.Boolean.TRUE;
import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static org.snomed.snowstorm.config.Config.DEFAULT_LANGUAGE_CODE;
import static org.snomed.snowstorm.core.util.CollectionUtils.orEmpty;
import static org.snomed.snowstorm.fhir.services.FHIRHelper.*;
import static org.snomed.snowstorm.fhir.utils.FHIRPageHelper.toPage;

@Service
public class FHIRValueSetService implements FHIRConstants {

	public static final String SUPPLEMENT_NOT_EXIST = "Supplement %s does not exist.";
	public static final String TX_ISSUE_TYPE = "http://hl7.org/fhir/tools/CodeSystem/tx-issue-type";
	public static final String UNEXPECTED_OPERATION_QUOTE = "Unexpected operation '";
	public static final String USED_SUPPLEMENT = "used-supplement";

	public static final String LABEL = "label";
	public static final String NOT_FOUND = "not-found";
	private static final String PROPERTY_STATUS = "status";
	public static final String ORDER = "order";
	public static final String VS_INVALID = "vs-invalid";
	public static final String WARNING_DASH = "warning-";
	public static final String WEIGHT = "weight";

	public static final String HL7_SD_EVS_CONTAINS_PROPERTY = "http://hl7.org/fhir/5.0/StructureDefinition/extension-ValueSet.expansion.contains.property";
	public static final String HL7_SD_ITEM_WEIGHT = "http://hl7.org/fhir/StructureDefinition/itemWeight";
	public static final String HL7_SD_OUTCOME_MESSAGE_ID = "http://hl7.org/fhir/StructureDefinition/operationoutcome-message-id";
	public static final String HL7_SD_VS_CONCEPT_DEFINITION = "http://hl7.org/fhir/StructureDefinition/valueset-concept-definition";
	public static final String HL7_SD_VS_CONCEPT_ORDER = "http://hl7.org/fhir/StructureDefinition/valueset-conceptOrder";
	public static final String HL7_SD_VS_DEPRECATED = "http://hl7.org/fhir/StructureDefinition/valueset-deprecated";
	public static final String HL7_SD_VS_EXPANSION_PARAMETER = "http://hl7.org/fhir/StructureDefinition/valueset-expansion-parameter";
	public static final String HL7_SD_VS_LABEL = "http://hl7.org/fhir/StructureDefinition/valueset-label";
	public static final String HL7_SD_VS_SUPPLEMENT = "http://hl7.org/fhir/StructureDefinition/valueset-supplement";
	
	public static final String MISSING_VALUESET = "https://github.com/IHTSDO/snowstorm/missing-valueset";
	public static final String VS_DEF_NOT_FOUND = "A definition for the value Set '%s' could not be found";

	protected static final String[] URLS = {
			HL7_SD_ITEM_WEIGHT,
			HL7_SD_VS_LABEL,
			HL7_SD_VS_CONCEPT_ORDER,
			HL7_SD_VS_DEPRECATED,
			HL7_SD_VS_CONCEPT_DEFINITION,
			HL7_SD_VS_SUPPLEMENT
	};

	protected static final Map<String,String> PROPERTY_TO_URL = new HashMap<>();

	public static final Comparator<ValueSet.ConceptReferenceDesignationComponent> CONCEPT_REFERENCE_DESIGNATION_COMPONENT_COMPARATOR = (a, b) -> {
		int langCompare = Comparator.nullsLast(String::compareTo).compare(a.getLanguage(), b.getLanguage());
		if (langCompare != 0) {
			return langCompare;
		}
		Coding aUse = a.getUse();
		Coding bUse = b.getUse();

		int aRank = 2;
		int bRank = 2;
		if (aUse != null && FHIRConstants.HL7_CS_DESIGNATION_USAGE.equals(aUse.getSystem())) {
			aRank = FHIRConstants.DISPLAY.equals(aUse.getCode()) ? 0 : 1;
		}
		if (bUse != null && FHIRConstants.HL7_CS_DESIGNATION_USAGE.equals(bUse.getSystem())) {
			bRank = FHIRConstants.DISPLAY.equals(bUse.getCode()) ? 0 : 1;
		}
		int rankCompare = Integer.compare(aRank, bRank);
		if (rankCompare != 0) {
			return rankCompare;
		}

		int systemCompare = Comparator.nullsLast(String::compareTo).compare(
				aUse != null ? aUse.getSystem() : null,
				bUse != null ? bUse.getSystem() : null);
		if (systemCompare != 0) {
			return systemCompare;
		}

		int useCodeCompare = Comparator.nullsLast(String::compareTo).compare(
				aUse != null ? aUse.getCode() : null,
				bUse != null ? bUse.getCode() : null);
		if (useCodeCompare != 0) {
			return useCodeCompare;
		}

		return Comparator.nullsLast(String::compareTo).compare(a.getValue(), b.getValue());
	};

	static{
		PROPERTY_TO_URL.put("definition","http://hl7.org/fhir/concept-properties#definition");
		PROPERTY_TO_URL.put("prop","http://hl7.org/fhir/test/CodeSystem/properties#prop");
		PROPERTY_TO_URL.put("alternateCode", "http://hl7.org/fhir/concept-properties#alternateCode");
	}

	private final FHIRCodeSystemService codeSystemService;

	private final FHIRConceptService conceptService;

	private final FHIRValueSetRepository valueSetRepository;

	private final QueryService snomedQueryService;

	private final ConceptService snomedConceptService;

	private final ElasticsearchOperations elasticsearchOperations;

	private final FHIRValueSetFinderService vsFinderService;

	private final FHIRValueSetCycleDetectionService vsCycleDetectionService;

	private final FHIRValueSetCodeValidationService codeValidationService;

	private final FHIRValueSetConstraintsService constraintsService;

	private final FHIRWarningsService warningsService;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public FHIRValueSetService(FHIRCodeSystemService codeSystemService, FHIRConceptService conceptService, FHIRValueSetRepository valueSetRepository, QueryService snomedQueryService, ConceptService snomedConceptService, ElasticsearchOperations elasticsearchOperations, FHIRValueSetFinderService vsFinderService, FHIRValueSetCycleDetectionService vsCycleDetectionService, FHIRValueSetCodeValidationService codeValidationService, FHIRValueSetConstraintsService constraintsService, FHIRWarningsService warningsService) {
		this.codeSystemService = codeSystemService;
		this.conceptService = conceptService;
		this.valueSetRepository = valueSetRepository;
		this.snomedQueryService = snomedQueryService;
		this.snomedConceptService = snomedConceptService;
		this.elasticsearchOperations = elasticsearchOperations;
		this.vsFinderService = vsFinderService;
		this.vsCycleDetectionService = vsCycleDetectionService;
		this.codeValidationService = codeValidationService;
		this.constraintsService = constraintsService;
		this.warningsService = warningsService;
	}

	public Page<FHIRValueSet> findAll(Pageable pageable) {
		NativeQuery searchQuery = new NativeQueryBuilder()
				.withPageable(pageable)
				.build();
		searchQuery.setTrackTotalHits(true);
		SearchHits<FHIRValueSet> search = elasticsearchOperations.search(searchQuery, FHIRValueSet.class);
		return toPage(search, pageable);
	}

	public void saveAllValueSetsOfCodeSystemVersionWithoutExpandValidation(List<ValueSet> valueSets) {
		for (ValueSet valueSet : orEmpty(valueSets)) {
			try {
				logger.info("Saving ValueSet {}", valueSet.getIdElement());
				createOrUpdateValuesetWithoutExpandValidation(valueSet);
			} catch (SnowstormFHIRServerResponseException e) {
				logger.error("Failed to store value set {}", valueSet.getIdElement(), e);
			}
		}
	}

	public FHIRValueSet createOrUpdateValueset(ValueSet valueSet) {
		if (valueSet.getUrl().contains(FHIR_VS)) {
			throw exception("ValueSet url must not contain 'fhir_vs', this is reserved for implicit value sets.", OperationOutcome.IssueType.INVARIANT, 400);
		}

		// Expand to validate
		ValueSet.ValueSetExpansionComponent originalExpansion = valueSet.getExpansion();
		expand(new ValueSetExpansionParameters(valueSet, true, true), null);
		valueSet.setExpansion(originalExpansion);
		return createOrUpdateValuesetWithoutExpandValidation(valueSet);
	}

	public FHIRValueSet createOrUpdateValuesetWithoutExpandValidation(ValueSet valueSet) {
		// Delete existing ValueSets with the same URL and version (could be different ID)
		valueSetRepository.findAllByUrl(valueSet.getUrl()).stream()
				.filter(otherVs -> equalVersions(otherVs.getVersion(), valueSet.getVersion()))
				.forEach(otherVs -> valueSetRepository.deleteById(otherVs.getId()));

		// Save will replace any existing value set with the same id.
		return valueSetRepository.save(new FHIRValueSet(valueSet));
	}

	private boolean equalVersions(String versionA, String versionB) {
		return versionA == null && versionB == null
				|| (versionA != null && versionA.equals(versionB));
	}

	public ValueSet expand(final ValueSetExpansionParameters params, String displayLanguage) {

		validateExpansionParameters(params);

		//Do we have any sort of display language set?  Use the default if not, to ensure at least some display value is set.
		//Discuss Config.DEFAULT_LANGUAGE_CODE currently 'en'

		ValueSet hapiValueSet = vsFinderService.findOrInferValueSet(params.getId(), params.getUrl(), params.getValueSet(), params.getValueSetVersion());
		if (hapiValueSet == null) {
			return null;
		}

		if (!hapiValueSet.hasCompose()) {
			return hapiValueSet;
		}

		vsCycleDetectionService.verifyNoCycles(hapiValueSet);

		applyVersionValueSetOverride(hapiValueSet, params);

		String filter = params.getFilter();
		boolean activeOnly = TRUE == params.getActiveOnly();
		PageRequest pageRequest = params.getPageRequest(Sort.sort(QueryConcept.class).by(QueryConcept::getConceptIdL).descending());

		// Resolve the set of code system versions that will actually be used. Includes some input parameter validation.
		Set<CanonicalUri> systemVersionParam = params.getSystemVersion() != null ? Collections.singleton(params.getSystemVersion()) : Collections.emptySet();

		CodeSystemVersionProvider codeSystemVersionProvider = new CodeSystemVersionProvider(systemVersionParam,
				null, // no coding version hints in expansion context
				params.getCheckSystemVersion(), params.getForceSystemVersion(), params.getExcludeSystem(),
				true, // expansion: always allow check-system-version as fallback for versionless includes
				codeSystemService);

		// Collate set of inclusion and exclusion constraints for each code system version
		CodeSelectionCriteria codeSelectionCriteria = constraintsService.generateInclusionExclusionConstraints(hapiValueSet, codeSystemVersionProvider, activeOnly, true);

		// Fail expand if check-system-version constraint was violated
		failIfVersionCheckViolated(codeSystemVersionProvider);

		// Restrict the expansion of ValueSets with multiple code system versions if any are SNOMED CT, to simplify pagination.
		Set<FHIRCodeSystemVersion> allInclusionVersions = codeSelectionCriteria.gatherAllInclusionVersions();
		boolean isSnomed = allInclusionVersions.stream().anyMatch(FHIRCodeSystemVersion::isOnSnomedBranch);
		validateSnomedExpansionSupported(isSnomed, allInclusionVersions, codeSelectionCriteria);

		if (allInclusionVersions.isEmpty()) {
			return hapiValueSet;
		}

		boolean includeDesignations = TRUE.equals(params.getIncludeDesignations());
		Page<FHIRConcept> conceptsPage;
		if (isSnomed) {
			conceptsPage = expandSnomedConceptsPage(allInclusionVersions, codeSelectionCriteria, filter, activeOnly, pageRequest, params, displayLanguage, includeDesignations);
		} else if (allInclusionVersions.stream().allMatch(v -> v.getInlineCodeSystem() != null)) {
			// All inclusion versions carry inline concepts from the tx-resource overlay — expand in-memory.
			conceptsPage = buildInlineConceptsPage(allInclusionVersions, codeSelectionCriteria, filter, activeOnly, pageRequest);
		} else {
			conceptsPage = expandFhirConceptsPage(codeSelectionCriteria, filter, pageRequest);
		}
		// Only SNOMED expansions carry the SNOMED copyright notice.
		String copyright = isSnomed ? SNOMED_VALUESET_COPYRIGHT : null;

		if (expansionRequestExceedsLimits(conceptsPage, params)) {
			String message = format("The value set '%s' expansion has too many codes to produce (>%d)", hapiValueSet.getUrl(), pageRequest.getPageSize());
			throw exception(message, OperationOutcome.IssueType.TOOCOSTLY, 404, null, new CodeableConcept(new Coding()).setText(message));
		}

		ExpansionVersionMaps versionMaps = buildExpansionVersionMaps(allInclusionVersions);
		ValueSet.ValueSetExpansionComponent expansion = createExpansionComponent(params, filter, codeSystemVersionProvider,
				versionMaps.idToVersionObj, allInclusionVersions, codeSelectionCriteria, hapiValueSet);

		validateAndApplySupplements(hapiValueSet, expansion, conceptsPage);

		Optional.ofNullable(params.getProperty()).ifPresent( x ->{
					if (!"alternateCode".equals(x)){
						addPropertyToExpansion(x, getUrlForProperty(x), expansion);
					}
				}
		);

		final String fhirDisplayLanguage = determineFhirDisplayLanguage(params, displayLanguage, expansion, hapiValueSet);

		return finalizeExpansion(hapiValueSet, expansion, conceptsPage, versionMaps, params, fhirDisplayLanguage, copyright);
	}

	private void applyVersionValueSetOverride(ValueSet hapiValueSet, ValueSetExpansionParameters params) {
		if (params.getVersionValueSet() != null){
			hapiValueSet.getCompose().getInclude().stream()
					.filter(ValueSet.ConceptSetComponent::hasValueSet).flatMap(x -> x.getValueSet().stream())
					.filter(x -> x.getValueAsString().equals(params.getVersionValueSet().getSystem()))
					.findFirst()
					.ifPresent(fixVersion -> fixVersion.setValueAsString(params.getVersionValueSet().toString()));
		}
	}

	// Fail expand if a check-system-version constraint was violated during constraint generation.
	private void failIfVersionCheckViolated(CodeSystemVersionProvider codeSystemVersionProvider) {
		List<OperationOutcome.OperationOutcomeIssueComponent> versionCheckIssues = codeSystemVersionProvider.getVersionCheckIssues();
		if (!versionCheckIssues.isEmpty()) {
			OperationOutcome operationOutcome = new OperationOutcome();
			operationOutcome.setIssue(versionCheckIssues);
			throw new SnowstormFHIRServerResponseException(422, versionCheckIssues.get(0).getDetails().getText(), operationOutcome);
		}
	}

	// Restrict SNOMED CT expansions with multiple code systems or nested value sets, to simplify pagination.
	private void validateSnomedExpansionSupported(boolean isSnomed, Set<FHIRCodeSystemVersion> allInclusionVersions, CodeSelectionCriteria codeSelectionCriteria) {
		if (!isSnomed) {
			return;
		}
		if (allInclusionVersions.size() > 1) {
			throw exception("This server does not yet support ValueSet$expand on ValueSets with multiple code systems if any are SNOMED CT, " +
							"because of the complexities around pagination and result totals.",
					OperationOutcome.IssueType.NOTSUPPORTED, 400);
		}
		if (!codeSelectionCriteria.getNestedSelections().isEmpty()) {
			throw exception("This server does not yet support ValueSet$expand on SNOMED CT ValueSets with nested value sets, " +
							"because of the complexities around pagination and result totals.",
					OperationOutcome.IssueType.NOTSUPPORTED, 400);
		}
	}

	// SNOMED CT expansion — only single-version expansion is supported.
	private Page<FHIRConcept> expandSnomedConceptsPage(Set<FHIRCodeSystemVersion> allInclusionVersions,
			CodeSelectionCriteria codeSelectionCriteria, String filter, boolean activeOnly, PageRequest pageRequest,
			ValueSetExpansionParameters params, String displayLanguage, boolean includeDesignations) {
		FHIRCodeSystemVersion codeSystemVersion = allInclusionVersions.iterator().next();
		List<LanguageDialect> languageDialects = ControllerHelper.parseAcceptLanguageHeaderWithDefaultFallback(FHIRHelper.getDisplayLanguage(params.getDisplayLanguage(),displayLanguage));

		// Constraints:
		// - Elasticsearch prevents us from requesting results beyond the first 10K
		// Strategy:
		// - Load concept ids until we reach the requested page
		// - Then load the concepts for that page
		int offsetRequested = (int) pageRequest.getOffset();
		int limitRequested = (int) (pageRequest.getOffset() + pageRequest.getPageSize());

		QueryService.ConceptQueryBuilder conceptQuery = vsFinderService.getSnomedConceptQuery(filter, activeOnly, codeSelectionCriteria, languageDialects, codeSystemVersion.getSnomedBranch());

		int totalResults;
		List<Long> conceptsToLoad;
		if (limitRequested > LARGE_PAGE.getPageSize()) {
			SnomedIdLoadResult loaded = loadSnomedConceptIdsForPage(conceptQuery, codeSystemVersion, pageRequest, offsetRequested, limitRequested);
			conceptsToLoad = loaded.conceptsToLoad;
			totalResults = loaded.totalResults;
		} else {
			SearchAfterPage<Long> resultsPage = snomedQueryService.searchForIds(conceptQuery, codeSystemVersion.getSnomedBranch(), pageRequest);
			conceptsToLoad = resultsPage.getContent();
			totalResults = (int) resultsPage.getTotalElements();
		}

		List<FHIRConcept> conceptsOnRequestedPage = new ArrayList<>();
		if (!conceptsToLoad.isEmpty()) {
			Map<String, ConceptMini> conceptMinis = snomedConceptService.findConceptMinis(codeSystemVersion.getSnomedBranch(), conceptsToLoad, languageDialects).getResultsMap();
			for (Long conceptToLoad : conceptsToLoad) {
				ConceptMini snomedConceptMini = conceptMinis.get(conceptToLoad.toString());
				if (snomedConceptMini != null) {
					conceptsOnRequestedPage.add(new FHIRConcept(snomedConceptMini, codeSystemVersion, includeDesignations));
				}
			}
		}

		return new PageImpl<>(conceptsOnRequestedPage, pageRequest, totalResults);
	}

	// Uses the Elasticsearch search-after feature to paginate past the 10k limit to the requested SNOMED page.
	private SnomedIdLoadResult loadSnomedConceptIdsForPage(QueryService.ConceptQueryBuilder conceptQuery,
			FHIRCodeSystemVersion codeSystemVersion, PageRequest pageRequest, int offsetRequested, int limitRequested) {
		SearchAfterPage<Long> previousPage = null;
		List<Long> allConceptIds = new LongArrayList();
		boolean loadedAll = false;
		int totalResults = 0;
		while (allConceptIds.size() < limitRequested && !loadedAll) {
			PageRequest largePageRequest;
			if (previousPage == null) {
				largePageRequest = PageRequest.of(0, LARGE_PAGE.getPageSize(), pageRequest.getSort());
			} else {
				int pageSize = Math.min(limitRequested - allConceptIds.size(), LARGE_PAGE.getPageSize());
				largePageRequest = SearchAfterPageRequest.of(previousPage.getSearchAfter(), pageSize, previousPage.getSort());
			}
			SearchAfterPage<Long> page = snomedQueryService.searchForIds(conceptQuery, codeSystemVersion.getSnomedBranch(), largePageRequest);
			allConceptIds.addAll(page.getContent());
			loadedAll = page.getNumberOfElements() < largePageRequest.getPageSize();
			if (previousPage == null) {
				// Collect results total
				totalResults = (int) page.getTotalElements();
			}
			previousPage = page;
		}
		List<Long> conceptsToLoad;
		if (allConceptIds.size() > offsetRequested) {
			conceptsToLoad = new LongArrayList(allConceptIds).subList(offsetRequested, Math.min(limitRequested, allConceptIds.size()));
		} else {
			conceptsToLoad = new ArrayList<>();
		}
		return new SnomedIdLoadResult(conceptsToLoad, totalResults);
	}

	// FHIR Concept Expansion (non-SNOMED).
	private Page<FHIRConcept> expandFhirConceptsPage(CodeSelectionCriteria codeSelectionCriteria, String filter, PageRequest pageRequest) {
		String sortField = filter != null ? "displayLen" : CODE;
		pageRequest = getPageRequest(pageRequest, sortField);
		BoolQuery fhirConceptQuery = vsFinderService.getFhirConceptQuery(codeSelectionCriteria, filter).build();

		int offsetRequested = (int) pageRequest.getOffset();
		int limitRequested = (int) (pageRequest.getOffset() + pageRequest.getPageSize());

		if (limitRequested > LARGE_PAGE.getPageSize()) {
			return loadFhirConceptsPageWithSearchAfter(fhirConceptQuery, pageRequest, offsetRequested, limitRequested);
		}
		return conceptService.findConcepts(bool().must(fhirConceptQuery._toQuery()), pageRequest);
	}

	// Uses the Elasticsearch search-after feature to paginate past the 10k limit to the requested non-SNOMED page.
	private Page<FHIRConcept> loadFhirConceptsPageWithSearchAfter(BoolQuery fhirConceptQuery, PageRequest pageRequest,
			int offsetRequested, int limitRequested) {
		SearchAfterPage<String> previousPage = null;
		List<String> allConceptCodes = new ArrayList<>();
		boolean loadedAll = false;
		int totalResults = 0;
		while (allConceptCodes.size() < limitRequested && !loadedAll) {
			PageRequest largePageRequest;
			if (previousPage == null) {
				largePageRequest = PageRequest.of(0, LARGE_PAGE.getPageSize(), pageRequest.getSort());
			} else {
				int pageSize = Math.min(limitRequested - allConceptCodes.size(), LARGE_PAGE.getPageSize());
				largePageRequest = SearchAfterPageRequest.of(previousPage.getSearchAfter(), pageSize, previousPage.getSort());
			}
			SearchAfterPage<String> page = conceptService.findConceptCodes(fhirConceptQuery, largePageRequest);
			allConceptCodes.addAll(page.getContent());
			loadedAll = page.getNumberOfElements() < largePageRequest.getPageSize();
			if (previousPage == null) {
				// Collect results total
				totalResults = (int) page.getTotalElements();
			}
			previousPage = page;
		}
		List<String> conceptsToLoad;
		if (allConceptCodes.size() > offsetRequested) {
			conceptsToLoad = new ArrayList<>(allConceptCodes).subList(offsetRequested, Math.min(limitRequested, allConceptCodes.size()));
		} else {
			conceptsToLoad = new ArrayList<>();
		}
		if (!conceptsToLoad.isEmpty()) {
			BoolQuery.Builder conceptsToLoadQuery = bool()
					.must(fhirConceptQuery._toQuery())
					.must(termsQuery(FHIRConcept.Fields.CODE, conceptsToLoad));
			Page<FHIRConcept> conceptsPage = conceptService.findConcepts(conceptsToLoadQuery, LARGE_PAGE);
			return new PageImpl<>(conceptsPage.getContent(), pageRequest, totalResults);
		}
		return new PageImpl<>(new ArrayList<>(), pageRequest, totalResults);
	}

	// Deduplicate inclusion versions by ID (multiple includes may resolve to the same version, e.g. after force-system-version).
	private ExpansionVersionMaps buildExpansionVersionMaps(Set<FHIRCodeSystemVersion> allInclusionVersions) {
		boolean multipleIncludes = allInclusionVersions.size() > 1;
		Map<String, FHIRCodeSystemVersion> idToVersionObj = allInclusionVersions.stream()
				.collect(Collectors.toMap(FHIRCodeSystemVersion::getId, v -> v, (a, b) -> a));
		Map<String, String> idAndVersionToUrl = idToVersionObj.entrySet().stream()
				.collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getUrl().replace(SNOMED_URI_UNVERSIONED, SNOMED_URI)));
		Map<String, String> idToVersionStr = idToVersionObj.entrySet().stream()
				.filter(e -> e.getValue().getVersion() != null)
				.collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getVersion()));
		Map<String, String> idAndVersionToLanguage = allInclusionVersions.stream()
				.filter(fhirCodeSystemVersion -> fhirCodeSystemVersion.getLanguage() != null).collect(Collectors.toMap(FHIRCodeSystemVersion::getId, FHIRCodeSystemVersion::getLanguage, (a, b) -> a));
		return new ExpansionVersionMaps(multipleIncludes, idToVersionObj, idAndVersionToUrl, idToVersionStr, idAndVersionToLanguage);
	}

	// Creates the expansion component and populates its parameters (offset/count/filter, used-codesystem, used-valueset, warnings).
	private ValueSet.ValueSetExpansionComponent createExpansionComponent(ValueSetExpansionParameters params, String filter,
			CodeSystemVersionProvider codeSystemVersionProvider, Map<String, FHIRCodeSystemVersion> idToVersionObj,
			Set<FHIRCodeSystemVersion> allInclusionVersions, CodeSelectionCriteria codeSelectionCriteria, ValueSet hapiValueSet) {
		ValueSet.ValueSetExpansionComponent expansion = new ValueSet.ValueSetExpansionComponent();
		String id = UUID.randomUUID().toString();
		expansion.setId(id);
		expansion.setIdentifier("urn:uuid:"+id);
		expansion.setTimestamp(new Date());

		addExpansionRequestParameters(expansion, params, filter, codeSystemVersionProvider);
		addUsedCodeSystemParameters(expansion, idToVersionObj);

		warningsService.collectCodeSystemSetWarnings(allInclusionVersions).forEach(expansion::addParameter);
		warningsService.collectValueSetWarnings(codeSelectionCriteria).forEach(expansion::addParameter);

		addUsedValueSetParameters(expansion, hapiValueSet);

		allInclusionVersions.forEach(codeSystemVersion ->
			orEmpty(codeSystemVersion.getExtensions()).forEach(fe ->
				hapiValueSet.addExtension(fe.getHapi())));

		return expansion;
	}

	// Echoes the request parameters (offset/count/filter/activeOnly/... and applied system-version hints) into the expansion.
	private void addExpansionRequestParameters(ValueSet.ValueSetExpansionComponent expansion, ValueSetExpansionParameters params,
			String filter, CodeSystemVersionProvider codeSystemVersionProvider) {
		Optional.ofNullable(params.getOffset()).ifPresent(x->expansion.addParameter(new ValueSet.ValueSetExpansionParameterComponent(new StringType("offset")).setValue(new IntegerType(x))));
		Optional.ofNullable(params.getCount()).ifPresent(x->expansion.addParameter(new ValueSet.ValueSetExpansionParameterComponent(new StringType("count")).setValue(new IntegerType(x))));
		Optional.ofNullable(filter).ifPresent(x->expansion.addParameter(new ValueSet.ValueSetExpansionParameterComponent(new StringType("filter")).setValue(new StringType(x))));
		Optional.ofNullable(params.getActiveOnly()).ifPresent(x->expansion.addParameter(new ValueSet.ValueSetExpansionParameterComponent(new StringType("activeOnly")).setValue(new BooleanType(x))));
		Optional.ofNullable(params.getExcludeNested()).ifPresent(x->expansion.addParameter(new ValueSet.ValueSetExpansionParameterComponent(new StringType("excludeNested")).setValue(new BooleanType(x))));
		Optional.ofNullable(params.getIncludeDesignations()).ifPresent(x->expansion.addParameter(new ValueSet.ValueSetExpansionParameterComponent(new StringType("includeDesignations")).setValue(new BooleanType(x))));
		Optional.ofNullable(params.getForceSystemVersion()).ifPresent(x ->
				expansion.addParameter(new ValueSet.ValueSetExpansionParameterComponent(new StringType("force-system-version")).setValue(new UriType(x.toString()))));
		if (codeSystemVersionProvider.isSystemVersionWasUsedAsDefault()) {
			Optional.ofNullable(params.getSystemVersion()).ifPresent(x ->
					expansion.addParameter(new ValueSet.ValueSetExpansionParameterComponent(new StringType("system-version")).setValue(new UriType(x.toString()))));
		}
		if (codeSystemVersionProvider.isCheckSystemVersionWasUsedAsDefault()) {
			Optional.ofNullable(params.getCheckSystemVersion()).ifPresent(x ->
					expansion.addParameter(new ValueSet.ValueSetExpansionParameterComponent(new StringType("check-system-version")).setValue(new UriType(x.toString()))));
		}
		Optional.ofNullable(params.getDesignations()).ifPresent(x->
			x.forEach(language ->
				expansion.addParameter(new ValueSet.ValueSetExpansionParameterComponent(new StringType("designation")).setValue(new StringType(language)))
			));
	}

	// Adds used-codesystem (and codesystem-supplement) parameters for each deduplicated inclusion version.
	private void addUsedCodeSystemParameters(ValueSet.ValueSetExpansionComponent expansion, Map<String, FHIRCodeSystemVersion> idToVersionObj) {
		idToVersionObj.values().forEach(codeSystemVersion -> {
				if (codeSystemVersion.getVersion() != null) {
					String csUrl = codeSystemVersion.getUrl().replace(SNOMED_URI_UNVERSIONED, SNOMED_URI);
					String csCanonical = "0".equals(codeSystemVersion.getVersion()) ? csUrl : csUrl + "|" + codeSystemVersion.getVersion();
					expansion.addParameter(new ValueSet.ValueSetExpansionParameterComponent(new StringType("used-codesystem"))
							.setValue(new CanonicalType(csCanonical)));
				}
				if (codeSystemVersion.getExtensions() != null){
					for( FHIRExtension fe: codeSystemVersion.getExtensions()){
						if ("https://github.com/IHTSDO/snowstorm/codesystem-supplement".equals(fe.getUri())){
							expansion.addParameter(new ValueSet.ValueSetExpansionParameterComponent(new StringType(USED_SUPPLEMENT))
									.setValue(new CanonicalType(fe.getValue())));
						}
					}
				}
			}
		);
	}

	// Adds used-valueset (and version) parameters for each nested value set include, resolving versionless includes to latest.
	private void addUsedValueSetParameters(ValueSet.ValueSetExpansionComponent expansion, ValueSet hapiValueSet) {
		hapiValueSet.getCompose().getInclude().stream()
				.filter(ValueSet.ConceptSetComponent::hasValueSet)
				.flatMap(x -> x.getValueSet().stream())
				.forEach(x ->{
					CanonicalUri uri = CanonicalUri.fromString(x.getValueAsString());
					if (uri.getVersion()==null){
						Optional<FHIRValueSet> latest = vsFinderService.findLatestByUrl(uri.getSystem());
						uri = CanonicalUri.of(uri.getSystem(), latest.flatMap(v -> Optional.ofNullable(v.getVersion())).orElse(null));
					}
					expansion.addParameter(new ValueSet.ValueSetExpansionParameterComponent(new StringType("used-valueset")).setValue(new UriType(uri.toString())));
					expansion.addParameter(new ValueSet.ValueSetExpansionParameterComponent(new StringType(VERSION)).setValue(new UriType(uri.toString())));
		});
	}

	// Validates declared supplements (throwing if missing) and applies tx-resource overlay supplements to the fetched concepts.
	private void validateAndApplySupplements(ValueSet hapiValueSet, ValueSet.ValueSetExpansionComponent expansion, Page<FHIRConcept> conceptsPage) {
		// Collect supplement URLs before clearing extensions, for post-check overlay application
		List<String> supplementUrls = hapiValueSet.getExtension().stream()
				.filter(e -> HL7_SD_VS_SUPPLEMENT.equals(e.getUrl()))
				.map(e -> e.getValue().primitiveValue())
				.toList();

		validateDeclaredSupplements(hapiValueSet, expansion);
		hapiValueSet.getExtension().clear();

		applyOverlaySupplements(supplementUrls, conceptsPage);
	}

	// Validates each declared vs-supplement extension: records used-supplement params, or throws 404 if a supplement is missing.
	private void validateDeclaredSupplements(ValueSet hapiValueSet, ValueSet.ValueSetExpansionComponent expansion) {
		hapiValueSet.getExtension().forEach(
				ext ->{
			if(ext.getUrl().equals(HL7_SD_VS_SUPPLEMENT)) {
				String supplementUrl = ext.getValue().primitiveValue();
				if (codeSystemService.supplementExists(supplementUrl, false)) {
					boolean alreadyAdded = expansion.getParameter().stream()
							.anyMatch(p -> USED_SUPPLEMENT.equals(p.getName()));
					if (!alreadyAdded) {
						expansion.addParameter(new ValueSet.ValueSetExpansionParameterComponent(new StringType(USED_SUPPLEMENT))
								.setValue(new CanonicalType(resolveSupplementCanonical(supplementUrl))));
					}
				} else {
					String message = SUPPLEMENT_NOT_EXIST.formatted(supplementUrl);
					CodeableConcept cc = new CodeableConcept(new Coding(TX_ISSUE_TYPE, NOT_FOUND, null)).setText(message);
					throw exception(message,
							OperationOutcome.IssueType.NOTFOUND, 404, null, cc);

				}
			}
		});
	}

	// Apply concept-level data from any tx-resource supplement that is not persisted in Elasticsearch.
	// Use TxResourceContext.lookup so versioned resources (stored as url|version) are also found.
	private void applyOverlaySupplements(List<String> supplementUrls, Page<FHIRConcept> conceptsPage) {
		for (String supplementUrl : supplementUrls) {
			String urlBase = supplementUrl.contains("|") ? supplementUrl.substring(0, supplementUrl.indexOf("|")) : supplementUrl;
			String versionPart = supplementUrl.contains("|") ? supplementUrl.substring(supplementUrl.indexOf("|") + 1) : null;
			Resource inlined = TxResourceContext.lookup(urlBase, versionPart);
			if (inlined instanceof CodeSystem supplementCs) {
				applyOverlaySupplementToConcepts(supplementCs, conceptsPage);
			}
		}
	}

	// Builds the expansion contents, sets totals, marks unclosed/copyright, and clears the compose if not requested.
	private ValueSet finalizeExpansion(ValueSet hapiValueSet, ValueSet.ValueSetExpansionComponent expansion,
			Page<FHIRConcept> conceptsPage, ExpansionVersionMaps versionMaps, ValueSetExpansionParameters params,
			String fhirDisplayLanguage, String copyright) {
		List<ValueSet.ValueSetExpansionContainsComponent> expansionContents = createExpansionContents(conceptsPage, hapiValueSet, versionMaps.idAndVersionToLanguage, versionMaps.idAndVersionToUrl, versionMaps.idToVersionStr, versionMaps.multipleIncludes, expansion, params, fhirDisplayLanguage);
		expansion.setContains(expansionContents);
		expansion.setTotal((int) conceptsPage.getTotalElements());
		Optional.ofNullable(params.getOffset()).ifPresent(expansion::setOffset);
		long offset = params.getOffset() != null ? params.getOffset() : 0;
		boolean truncated = conceptsPage.getTotalElements() > offset + expansionContents.size();
		// SNOMED CT (and other post-coordinated systems) are inherently unbounded per FHIR spec
		boolean inherentlyOpen = hapiValueSet.getCompose().getInclude().stream()
				.anyMatch(include -> FHIRHelper.isSnomedUri(include.getSystem()) && !include.getFilter().isEmpty());
		if (truncated || inherentlyOpen) {
			expansion.addExtension("http://hl7.org/fhir/StructureDefinition/valueset-unclosed", new BooleanType(true));
		}
		hapiValueSet.setExpansion(expansion);

		if (hapiValueSet.getId() == null) {
			hapiValueSet.setId(UUID.randomUUID().toString());
		}

		if (copyright != null) {
			hapiValueSet.setCopyright(copyright);
		}

		if (!TRUE.equals(params.getIncludeDefinition())) {
			hapiValueSet.setCompose(null);
		}

		return hapiValueSet;
	}

	// Deduplicated inclusion-version lookup maps used while building the expansion.
	private static final class ExpansionVersionMaps {
		private final boolean multipleIncludes;
		private final Map<String, FHIRCodeSystemVersion> idToVersionObj;
		private final Map<String, String> idAndVersionToUrl;
		private final Map<String, String> idToVersionStr;
		private final Map<String, String> idAndVersionToLanguage;

		private ExpansionVersionMaps(boolean multipleIncludes, Map<String, FHIRCodeSystemVersion> idToVersionObj,
				Map<String, String> idAndVersionToUrl, Map<String, String> idToVersionStr, Map<String, String> idAndVersionToLanguage) {
			this.multipleIncludes = multipleIncludes;
			this.idToVersionObj = idToVersionObj;
			this.idAndVersionToUrl = idAndVersionToUrl;
			this.idToVersionStr = idToVersionStr;
			this.idAndVersionToLanguage = idAndVersionToLanguage;
		}
	}

	// Result of paginating SNOMED concept ids to the requested page via search-after.
	private static final class SnomedIdLoadResult {
		private final List<Long> conceptsToLoad;
		private final int totalResults;

		private SnomedIdLoadResult(List<Long> conceptsToLoad, int totalResults) {
			this.conceptsToLoad = conceptsToLoad;
			this.totalResults = totalResults;
		}
	}

	private static @NotNull PageRequest getPageRequest(PageRequest pageRequest, String sortField) {
		if (pageRequest instanceof ControllerHelper.ZeroSizePageRequest) {
			return new ControllerHelper.ZeroSizePageRequest(Sort.by(Sort.Direction.ASC, sortField));
		}
		return PageRequest.of(pageRequest.getPageNumber(), pageRequest.getPageSize(), Sort.Direction.ASC, sortField);
	}

	private static @Nullable String determineFhirDisplayLanguage(ValueSetExpansionParameters params, String displayLanguage, ValueSet.ValueSetExpansionComponent expansion, ValueSet hapiValueSet) {
		final String fhirDisplayLanguage;
		if (Optional.ofNullable(params.getDisplayLanguage()).isPresent()){
			fhirDisplayLanguage = params.getDisplayLanguage();
			expansion.addParameter(new ValueSet.ValueSetExpansionParameterComponent(new StringType(DISPLAY_LANGUAGE)).setValue(new CodeType(fhirDisplayLanguage)));
		} else if (hasDisplayLanguage(hapiValueSet)){
			fhirDisplayLanguage = hapiValueSet.getCompose().getExtensionByUrl(HL7_SD_VS_EXPANSION_PARAMETER).getExtensionString(VALUE);
			expansion.addParameter(new ValueSet.ValueSetExpansionParameterComponent(new StringType(DISPLAY_LANGUAGE)).setValue(new CodeType(fhirDisplayLanguage)));
		} else if (displayLanguage != null){
			fhirDisplayLanguage = displayLanguage;
			expansion.addParameter(new ValueSet.ValueSetExpansionParameterComponent(new StringType(DISPLAY_LANGUAGE)).setValue(new CodeType(fhirDisplayLanguage)));
		} else {
			fhirDisplayLanguage = null;
		}
		return fhirDisplayLanguage;
	}

	/**
	 * Expands concepts in-memory from tx-resource overlay CodeSystems when no Elasticsearch documents exist.
	 * Mirrors the filter/active logic applied during Elasticsearch-based non-SNOMED expansion.
	 */
	private Page<FHIRConcept> buildInlineConceptsPage(Set<FHIRCodeSystemVersion> versions,
			CodeSelectionCriteria codeSelectionCriteria, String filter, boolean activeOnly, PageRequest pageRequest) {

		List<FHIRConcept> allConcepts = new ArrayList<>();

		for (FHIRCodeSystemVersion version : versions) {
			allConcepts.addAll(collectInlineConceptsForVersion(version, codeSelectionCriteria, activeOnly, filter));
		}

		// Sort: by display length when filtering (matches ES behaviour), otherwise by code
		if (filter != null) {
			allConcepts.sort(Comparator.comparingInt(c -> (c.getDisplay() != null ? c.getDisplay().length() : 0)));
		} else {
			allConcepts.sort(Comparator.comparing(FHIRConcept::getCode));
		}

		int total = allConcepts.size();
		int offset = (int) pageRequest.getOffset();
		int toIndex = Math.min(offset + pageRequest.getPageSize(), total);
		List<FHIRConcept> page = offset < total ? new ArrayList<>(allConcepts.subList(offset, toIndex)) : new ArrayList<>();
		return new PageImpl<>(page, pageRequest, total);
	}

	// Builds the list of included FHIRConcepts for a single inline (tx-resource overlay) code system version.
	private List<FHIRConcept> collectInlineConceptsForVersion(FHIRCodeSystemVersion version,
			CodeSelectionCriteria codeSelectionCriteria, boolean activeOnly, String filter) {
		CodeSystem inline = version.getInlineCodeSystem();
		if (inline == null) {
			return Collections.emptyList();
		}

		// Collect all concepts including nested children
		Set<CodeSystem.ConceptDefinitionComponent> allDefs = new LinkedHashSet<>();
		for (CodeSystem.ConceptDefinitionComponent root : inline.getConcept()) {
			collectInlineConcepts(root, allDefs);
		}

		// Build code→ancestor-set map so ancestor constraints can be evaluated inline.
		Map<String, Set<String>> codeToAncestors = new HashMap<>();
		for (CodeSystem.ConceptDefinitionComponent root : inline.getConcept()) {
			collectInlineAncestors(root, Collections.emptySet(), codeToAncestors);
		}
		Set<String> allInlineCodes = allDefs.stream().map(CodeSystem.ConceptDefinitionComponent::getCode).collect(Collectors.toSet());

		final Set<String> finalIncludedCodes = computeInlineIncludedCodes(
				codeSelectionCriteria.getInclusionConstraints().get(version), allInlineCodes, codeToAncestors);
		Set<String> excludedCodes = computeInlineExcludedCodes(codeSelectionCriteria.getExclusionConstraints().get(version));

		List<FHIRConcept> concepts = new ArrayList<>();
		for (CodeSystem.ConceptDefinitionComponent def : allDefs) {
			FHIRConcept concept = buildInlineConceptIfIncluded(def, version, excludedCodes, finalIncludedCodes, activeOnly, filter);
			if (concept != null) {
				concepts.add(concept);
			}
		}
		return concepts;
	}

	// Determine included codes using proper AND-of-ORs evaluation.
	// null means "all" (no constraint narrowed the set).
	private Set<String> computeInlineIncludedCodes(ConjunctionConstraints conjunctionConstraints,
			Set<String> allInlineCodes, Map<String, Set<String>> codeToAncestors) {
		Set<String> includedCodes = null;
		if (conjunctionConstraints != null) {
			for (ConjunctionConstraints.DisjunctionConstraints orGroup : conjunctionConstraints.getDisjunctionConstraints()) {
				Set<String> orMatched = resolveInlineDisjunctionConstraints(orGroup.getConstraints(), allInlineCodes, codeToAncestors);
				if (orMatched == null) continue; // unevaluable (e.g. ECL) — treat as unconstrained
				if (includedCodes == null) includedCodes = orMatched;
				else includedCodes.retainAll(orMatched);
			}
		}
		return includedCodes;
	}

	private Set<String> computeInlineExcludedCodes(ConjunctionConstraints exclConstraints) {
		Set<String> excludedCodes = new HashSet<>();
		if (exclConstraints != null) {
			for (ConceptConstraint constraint : exclConstraints.constraintsFlattened()) {
				if (constraint.getCodes() != null) excludedCodes.addAll(constraint.getCodes());
			}
		}
		return excludedCodes;
	}

	// Returns the concept to include in the inline expansion, or null when it is excluded/filtered out.
	private FHIRConcept buildInlineConceptIfIncluded(CodeSystem.ConceptDefinitionComponent def, FHIRCodeSystemVersion version,
			Set<String> excludedCodes, Set<String> finalIncludedCodes, boolean activeOnly, String filter) {
		String code = def.getCode();
		if (excludedCodes.contains(code)) return null;
		if (finalIncludedCodes != null && !finalIncludedCodes.contains(code)) return null;

		FHIRConcept concept = new FHIRConcept(def, version);
		// Apply extensions-as-properties merge (mirrors FHIRConceptService.saveAllConceptsOfCodeSystemVersion)
		concept.getExtensions().forEach((key, value) -> concept.getProperties().put(key, value));

		if (activeOnly && !concept.isActive()) return null;
		if (filter != null && !filter.isBlank()) {
			String lowerFilter = filter.toLowerCase();
			String display = concept.getDisplay() != null ? concept.getDisplay() : "";
			if (!code.toLowerCase().contains(lowerFilter) && !display.toLowerCase().contains(lowerFilter)) return null;
		}
		return concept;
	}

	private void collectInlineConcepts(CodeSystem.ConceptDefinitionComponent parent,
			Set<CodeSystem.ConceptDefinitionComponent> result) {
		result.add(parent);
		for (CodeSystem.ConceptDefinitionComponent child : parent.getConcept()) {
			collectInlineConcepts(child, result);
		}
	}

	/** Recursively populates {@code codeToAncestors} with the full ancestor set for every concept in the subtree. */
	private void collectInlineAncestors(CodeSystem.ConceptDefinitionComponent node, Set<String> parentAncestors,
			Map<String, Set<String>> codeToAncestors) {
		codeToAncestors.put(node.getCode(), parentAncestors);
		Set<String> childAncestors = new HashSet<>(parentAncestors);
		childAncestors.add(node.getCode());
		for (CodeSystem.ConceptDefinitionComponent child : node.getConcept()) {
			collectInlineAncestors(child, childAncestors, codeToAncestors);
		}
	}

	/**
	 * Resolves an OR group of constraints against the inline CS concept set.
	 * Returns the union of all codes matching any evaluable constraint, or null if none are evaluable
	 * (meaning the group imposes no restriction on inline concepts).
	 */
	private Set<String> resolveInlineDisjunctionConstraints(Set<ConceptConstraint> orGroup, Set<String> allCodes,
			Map<String, Set<String>> codeToAncestors) {
		Set<String> matched = new HashSet<>();
		boolean hasEvaluable = false;
		for (ConceptConstraint c : orGroup) {
			if (c.hasEcl()) continue; // ECL requires SNOMED — skip
			hasEvaluable = true;
			if (c.getCodes() != null && !c.getCodes().isEmpty()) {
				matched.addAll(c.getCodes());
			}
			if (c.getAncestor() != null && !c.getAncestor().isEmpty()) {
				matchCodesByAncestor(c, allCodes, codeToAncestors, matched);
			}
		}
		return hasEvaluable ? matched : null;
	}

	/**
	 * Adds to {@code matched} every code from {@code allCodes} whose ancestors intersect the constraint's ancestor set.
	 */
	private void matchCodesByAncestor(ConceptConstraint c, Set<String> allCodes,
			Map<String, Set<String>> codeToAncestors, Set<String> matched) {
		for (String code : allCodes) {
			Set<String> ancestors = codeToAncestors.getOrDefault(code, Collections.emptySet());
			if (!Collections.disjoint(ancestors, c.getAncestor())) {
				matched.add(code);
			}
		}
	}

	/**
	 * Returns a versioned canonical for the supplement URL. If the supplement is in the tx-resource overlay
	 * and carries a version, appends "|version". Otherwise returns the URL as-is.
	 */
	private String resolveSupplementCanonical(String supplementUrl) {
		String urlBase = supplementUrl.contains("|") ? supplementUrl.substring(0, supplementUrl.indexOf("|")) : supplementUrl;
		String versionPart = supplementUrl.contains("|") ? supplementUrl.substring(supplementUrl.indexOf("|") + 1) : null;
		Resource inlined = TxResourceContext.lookup(urlBase, versionPart);
		if (inlined instanceof CodeSystem cs && cs.getVersion() != null && !cs.getVersion().isBlank()) {
			return urlBase + "|" + cs.getVersion();
		}
		return supplementUrl;
	}

	/**
	 * Applies concept-level data (designations, extensions-as-properties, formal properties) from a
	 * tx-resource supplement CodeSystem onto the already-fetched concepts. Mirrors the
	 * "treat extensions as properties" merge done in FHIRConceptService at save time.
	 */
	private void applyOverlaySupplementToConcepts(CodeSystem supplement, Page<FHIRConcept> conceptsPage) {
		if (supplement.getConcept().isEmpty()) return;

		Map<String, CodeSystem.ConceptDefinitionComponent> supplementByCode = supplement.getConcept().stream()
				.collect(Collectors.toMap(CodeSystem.ConceptDefinitionComponent::getCode, c -> c, (a, b) -> a));

		for (FHIRConcept concept : conceptsPage) {
			CodeSystem.ConceptDefinitionComponent supplementConcept = supplementByCode.get(concept.getCode());
			if (supplementConcept == null) continue;

			mergeSupplementDesignations(concept, supplementConcept);
			mergeSupplementExtensionsAsProperties(concept, supplementConcept);
			mergeSupplementFormalProperties(concept, supplementConcept);
		}
	}

	private void mergeSupplementDesignations(FHIRConcept concept, CodeSystem.ConceptDefinitionComponent supplementConcept) {
		List<FHIRDesignation> designations = new ArrayList<>(concept.getDesignations());
		for (CodeSystem.ConceptDefinitionDesignationComponent d : supplementConcept.getDesignation()) {
			designations.add(new FHIRDesignation(d));
		}
		concept.setDesignations(designations);
	}

	/** Mirrors saveAllConceptsOfCodeSystemVersion behaviour: treat supplement extensions as properties. */
	private void mergeSupplementExtensionsAsProperties(FHIRConcept concept, CodeSystem.ConceptDefinitionComponent supplementConcept) {
		for (Extension ext : supplementConcept.getExtension()) {
			if (ext.getValue() == null) continue;
			try {
				FHIRProperty property = new FHIRProperty(ext.getUrl(), null,
						ext.getValue().primitiveValue(),
						FHIRProperty.typeToFHIRPropertyType(ext.getValue()));
				concept.getProperties().computeIfAbsent(ext.getUrl(), k -> new ArrayList<>()).add(property);
			} catch (IllegalArgumentException ignored) {
				// Unknown extension type — skip, same as storage path
			}
		}
	}

	private void mergeSupplementFormalProperties(FHIRConcept concept, CodeSystem.ConceptDefinitionComponent supplementConcept) {
		for (CodeSystem.ConceptPropertyComponent prop : supplementConcept.getProperty()) {
			concept.getProperties().computeIfAbsent(prop.getCode(), k -> new ArrayList<>())
					.add(new FHIRProperty(prop));
		}
	}

	private List<ValueSet.ValueSetExpansionContainsComponent> createExpansionContents(Page<FHIRConcept> conceptsPage, ValueSet hapiValueSet, Map<String, String> idAndVersionToLanguage, Map<String, String> idAndVersionToUrl, Map<String, String> idToVersionStr, boolean multipleIncludes, ValueSet.ValueSetExpansionComponent expansion, ValueSetExpansionParameters params, String fhirDisplayLanguage) {
		return conceptsPage.stream()
				.map(concept -> createExpansionContainsComponent(concept, hapiValueSet, idAndVersionToLanguage, idAndVersionToUrl, idToVersionStr, multipleIncludes, expansion, params, fhirDisplayLanguage))
				.toList();
	}

	private ValueSet.ValueSetExpansionContainsComponent createExpansionContainsComponent(FHIRConcept concept, ValueSet hapiValueSet, Map<String, String> idAndVersionToLanguage, Map<String, String> idAndVersionToUrl, Map<String, String> idToVersionStr, boolean multipleIncludes, ValueSet.ValueSetExpansionComponent expansion, ValueSetExpansionParameters params, String fhirDisplayLanguage) {
		List<ValueSet.ConceptReferenceComponent> references = hapiValueSet.getCompose().getInclude().stream()
				.flatMap(set -> set.getConcept().stream()).filter(c -> c.getCode().equals(concept.getCode())).toList();

		ValueSet.ValueSetExpansionContainsComponent component = new ValueSet.ValueSetExpansionContainsComponent()
				.setSystem(idAndVersionToUrl.get(concept.getCodeSystemVersion()))
				.setCode(concept.getCode())
				.setInactiveElement(concept.isActive() ? null : new BooleanType(true))
				.setDisplay(concept.getDisplay());
		if (multipleIncludes) {
			component.setVersion(idToVersionStr.get(concept.getCodeSystemVersion()));
		}
		if (!concept.isActive()) {
			addPropertyToContains(PROPERTY_STATUS, component, new CodeType("inactive"));
			addPropertyToExpansion(PROPERTY_STATUS, "http://hl7.org/fhir/concept-properties#status", expansion);
		}

		concept.getProperties().forEach((key, value) -> applyConceptPropertyToContains(key, value, component, expansion));

		Optional.ofNullable(params.getProperty()).ifPresent(x ->{
			List<FHIRProperty> properties =concept.getProperties().getOrDefault(x, emptyList());
			properties.stream()
					.findFirst()
					.ifPresent(y->
							addPropertyToContains(y.getCode(), component, y.toHapiValue(null))
					);
		});
		addInfoFromReferences(component, references);
		setDisplayAndDesignations(component, concept, idAndVersionToLanguage.get(concept.getCodeSystemVersion()), params.getIncludeDesignationsAsBool(), fhirDisplayLanguage, params.getDesignations());
		return component;
	}

	private void applyConceptPropertyToContains(String key, List<FHIRProperty> value, ValueSet.ValueSetExpansionContainsComponent component, ValueSet.ValueSetExpansionComponent expansion) {
		if (key.equals(PROPERTY_STATUS)) {
			value.stream()
					.filter(x -> x.getValue().equals("retired") || x.getValue().equals("deprecated"))
					.findFirst()
					.ifPresent(x -> {
						if ("retired".equals(x.getValue())) {
							component.setInactive(true);
						}
						addPropertyToContains(PROPERTY_STATUS, component, new CodeType(x.getValue()));
						addPropertyToExpansion(PROPERTY_STATUS, "http://hl7.org/fhir/concept-properties#status", expansion);
					});
		} else if (key.equals("notSelectable") || key.equals("not-selectable")) {
			value.stream()
					.filter(val -> val.getValue().equals("true"))
					.findFirst()
					.ifPresent(y -> component.setAbstract(true));
		} else if (key.equals(HL7_SD_ITEM_WEIGHT)) {
			value.stream()
					.findFirst()
					.ifPresent(y -> {
						addPropertyToContains(WEIGHT, component, y.toHapiValue(null));
						addPropertyToExpansion(WEIGHT, "http://hl7.org/fhir/concept-properties#itemWeight", expansion);
					});
		} else if (key.equals("http://hl7.org/fhir/StructureDefinition/codesystem-label")) {
			value.stream()
					.findFirst()
					.ifPresent(y -> {
						addPropertyToContains(LABEL, component, y.toHapiValue(null));
						addPropertyToExpansion(LABEL, "http://hl7.org/fhir/concept-properties#label", expansion);
					});
		} else if (key.equals("http://hl7.org/fhir/StructureDefinition/codesystem-conceptOrder")) {
			value.stream()
					.findFirst()
					.ifPresent(y -> {
						addPropertyToContains(ORDER, component, new DecimalType(y.toHapiValue(null).primitiveValue()));
						addPropertyToExpansion(ORDER, "http://hl7.org/fhir/concept-properties#order", expansion);
					});
		} else if (key.equals("http://hl7.org/fhir/StructureDefinition/rendering-style") ||
				key.equals("http://hl7.org/fhir/StructureDefinition/rendering-xhtml")) {
			value.stream()
					.findFirst()
					.ifPresent(y -> component.addExtension(key, y.toHapiValue(null)));
		}
	}

	private static void validateExpansionParameters(ValueSetExpansionParameters params) {
		// Lots of not supported parameters
		notSupported("context", params.getContext());
		notSupported("contextDirection", params.getContextDirection());
		notSupported(DATE, params.getDate());
		notSupported("excludeNotForUI", params.getExcludeNotForUI());
		notSupported("excludePostCoordinated", params.getExcludePostCoordinated());
		notSupported(VERSION, params.getVersion());// Not part of the FHIR API spec but requested under MAINT-1363
	}

	private boolean expansionRequestExceedsLimits(Page<FHIRConcept> conceptsPage, ValueSetExpansionParameters params) {
		// If the user explicitly requested a count, honour it up to the absolute maximum — not too costly.
		if (params.getCount() != null && params.getCount() <= MAXIMUM_PAGESIZE) {
			return false;
		}
		// No count specified: apply the default limit unless the client explicitly allows large expansions.
		int maximumPageSize = params.getAllowMaximumSizeExpansionAsBoolean() ? MAXIMUM_PAGESIZE : DEFAULT_PAGESIZE;
		return conceptsPage.getTotalElements() > maximumPageSize;
	}

	static boolean hasDisplayLanguage(ValueSet hapiValueSet) {
        return Optional.ofNullable(hapiValueSet.getCompose().getExtensionByUrl(HL7_SD_VS_EXPANSION_PARAMETER)).isPresent()
		        && DISPLAY_LANGUAGE.equals(hapiValueSet.getCompose().getExtensionByUrl(HL7_SD_VS_EXPANSION_PARAMETER).getExtensionString("name"));
	}

	private void setDisplayAndDesignations(ValueSet.ValueSetExpansionContainsComponent component,
	                                              FHIRConcept concept,
	                                              String defaultConceptLanguage,
	                                              boolean includeDesignations,
	                                              String displayLanguage,
	                                              List<String> designationLanguages) {

		// Parse requested designation languages
		List<String> designationLang = Optional.ofNullable(designationLanguages)
				.orElse(emptyList())
				.stream()
				.map(x -> {
					String[] parts = x.split("\\|");
					return parts.length < 2 ? parts[0] : parts[1];
				})
				.toList();

		Map<String, List<Locale>> languageToVarieties = new HashMap<>();
		if (defaultConceptLanguage != null) {
			Locale defaultLocale = Locale.forLanguageTag(defaultConceptLanguage);
			languageToVarieties.put(defaultLocale.getLanguage(), new ArrayList<>(List.of(defaultLocale)));
		}

		// Convert component and concept designations to ValueSetDesignationComponents
		List<ValueSet.ConceptReferenceDesignationComponent> allDesignations = Stream.concat(
				component.getDesignation().stream(),
				concept.getDesignations().stream()
						.map(d -> {
							ValueSet.ConceptReferenceDesignationComponent c = new ValueSet.ConceptReferenceDesignationComponent();
							c.setLanguage(d.getLanguage());
							c.setUse(d.getUseCoding());
							c.setValue(d.getValue());
							Optional.ofNullable(d.getExtensions()).orElse(emptyList())
									.forEach(e -> c.addExtension(e.getHapi()));
							return c;
						})
		).toList();

		// Group by language and populate locales
		Map<String, List<ValueSet.ConceptReferenceDesignationComponent>> languageToDesignation =
				allDesignations.stream()
						.filter(d -> d.getLanguage() != null)
						.collect(Collectors.groupingBy(d -> {
							if (d.getLanguage() != null) {
								Locale locale = Locale.forLanguageTag(d.getLanguage());
								if (locale == null) {
									throw new IllegalArgumentException("Unable to determine locale for language tag: " + d.getLanguage());
								}
								languageToVarieties.computeIfAbsent(locale.getLanguage(), k -> new ArrayList<>()).add(locale);
							}
							return d.getLanguage();
						}));

		// Handle designations with no language
		List<ValueSet.ConceptReferenceDesignationComponent> noLanguage =
				allDesignations.stream()
						.filter(d -> d.getLanguage() == null)
						.toList();

		// Determine requested language and set display
		List<Pair<LanguageDialect, Double>> weightedLanguages = ControllerHelper.parseAcceptLanguageHeaderWithWeights(displayLanguage, true);
		String requestedLanguage = determineRequestedLanguage(defaultConceptLanguage, weightedLanguages, languageToDesignation.keySet(), languageToVarieties);
		String originalDisplayTerm = component.getDisplay();
		String promotedDesignationLanguage = promoteDisplayFromDesignations(component, requestedLanguage, includeDesignations,
				displayLanguage, defaultConceptLanguage, languageToDesignation);

		// Set component designations based on requested languages
		buildComponentDesignations(component, includeDesignations, originalDisplayTerm, promotedDesignationLanguage,
				defaultConceptLanguage, requestedLanguage, languageToDesignation, designationLang, noLanguage);
	}

	// Promotes a designation to the component display when required, returning the promoted designation's language (or null).
	private String promoteDisplayFromDesignations(ValueSet.ValueSetExpansionContainsComponent component, String requestedLanguage,
			boolean includeDesignations, String displayLanguage, String defaultConceptLanguage,
			Map<String, List<ValueSet.ConceptReferenceDesignationComponent>> languageToDesignation) {
		if (!((component.getDisplay() == null && includeDesignations)
			|| (displayLanguage != null && defaultConceptLanguage != null && !defaultConceptLanguage.equals(displayLanguage)))) {
			return null;
		}
		String displayTerm = languageToDesignation.getOrDefault(requestedLanguage, emptyList()).stream()
				.filter(d -> d.getUse() != null && FHIRConstants.HL7_CS_DESIGNATION_USAGE.equals(d.getUse().getSystem()))
				.findFirst()
				.orElse(new ValueSet.ConceptReferenceDesignationComponent())
				.getValue();
		if (displayTerm == null) {
			//Not clear on the use of HL7_DESIGNATION_USAGE.   If we only have one designation in the required language, use it for display
			List<ValueSet.ConceptReferenceDesignationComponent> designationsInRequestedLanguage = languageToDesignation.getOrDefault(requestedLanguage, emptyList());
			if (designationsInRequestedLanguage.size() == 1) {
				component.setDisplay(designationsInRequestedLanguage.get(0).getValue());
				return designationsInRequestedLanguage.get(0).getLanguage();
			}
			logger.warn("Multiple or no designations found for requested display language '{}', unable to determine single display value for concept code '{}'.",
					requestedLanguage, component.getCode());
			return null;
		}
		component.setDisplay(displayTerm);
		return null;
	}

	private void buildComponentDesignations(ValueSet.ValueSetExpansionContainsComponent component, boolean includeDesignations,
			String originalDisplayTerm, String promotedDesignationLanguage, String defaultConceptLanguage, String requestedLanguage,
			Map<String, List<ValueSet.ConceptReferenceDesignationComponent>> languageToDesignation, List<String> designationLang,
			List<ValueSet.ConceptReferenceDesignationComponent> noLanguage) {
		if (!includeDesignations) {
			component.setDesignation(emptyList());
			return;
		}
		List<ValueSet.ConceptReferenceDesignationComponent> newDesignations = new ArrayList<>();
		addPromotedDisplayAsDesignation(newDesignations, component, originalDisplayTerm, defaultConceptLanguage);
		addLanguageDesignations(newDesignations, component, languageToDesignation, designationLang, promotedDesignationLanguage, noLanguage);
		normalizeDisplayUseSystem(newDesignations);

		String displayDesignationLanguage = resolveDisplayDesignationLanguage(promotedDesignationLanguage, requestedLanguage, defaultConceptLanguage);
		String derivedDisplayValue = deriveDisplayValue(component, newDesignations, displayDesignationLanguage);
		applyDisplayLanguageAndValue(newDesignations, displayDesignationLanguage, derivedDisplayValue);
		inferMissingDesignationUse(newDesignations, derivedDisplayValue, displayDesignationLanguage);

		// Remove HL7 "display" designations — redundant with component.display and not expected by the FHIR conformance suite.
		newDesignations.removeIf(d -> d.getUse() != null
				&& HL7_CS_DESIGNATION_USAGE.equals(d.getUse().getSystem())
				&& DISPLAY.equals(d.getUse().getCode()));
		// Ensure deterministic ordering for designations to avoid flaky expansions.
		newDesignations.sort(CONCEPT_REFERENCE_DESIGNATION_COMPONENT_COMPARATOR);
		component.setDesignation(newDesignations);
	}

	// If we replaced the display term and defaultConceptLanguage differs from what was requested, shift the old display term into a designation.
	private void addPromotedDisplayAsDesignation(List<ValueSet.ConceptReferenceDesignationComponent> newDesignations,
			ValueSet.ValueSetExpansionContainsComponent component, String originalDisplayTerm, String defaultConceptLanguage) {
		if (originalDisplayTerm != null && !originalDisplayTerm.equals(component.getDisplay())) {
			ValueSet.ConceptReferenceDesignationComponent existingDisplayAsDesignation = new ValueSet.ConceptReferenceDesignationComponent();
			existingDisplayAsDesignation.setValue(originalDisplayTerm);
			existingDisplayAsDesignation.setLanguage(defaultConceptLanguage);
			existingDisplayAsDesignation.setUse(new Coding(HL7_CS_TERM_INFRA, PREFERRED_FOR_LANGUAGE, null));
			newDesignations.add(existingDisplayAsDesignation);
		}
	}

	private void addLanguageDesignations(List<ValueSet.ConceptReferenceDesignationComponent> newDesignations,
			ValueSet.ValueSetExpansionContainsComponent component,
			Map<String, List<ValueSet.ConceptReferenceDesignationComponent>> languageToDesignation, List<String> designationLang,
			String promotedDesignationLanguage, List<ValueSet.ConceptReferenceDesignationComponent> noLanguage) {
		// Something I disagree with, and we might want to do this for non-SNOMED system only, but the Validator expects that if a designation
		// has been promoted to the display term, then we don't also include it as a separate designation.
		final String wrappedPromotedDesignationLanguage = promotedDesignationLanguage;
		final String componentDisplay = component.getDisplay();
		newDesignations.addAll(languageToDesignation.values().stream()
				.flatMap(List::stream)
				.filter(d -> designationLang.isEmpty() || designationLang.contains(d.getLanguage()))
				.filter((d -> !(d.getValue().equals(componentDisplay)
								&& d.getLanguage().equals(wrappedPromotedDesignationLanguage))))
				.filter(d -> {
					// For SNOMED, only include FSN and PT (synonym matching the display). Non-SNOMED: keep all.
					if (!FHIRHelper.isSnomedUri(component.getSystem())) return true;
					boolean isFsn = d.getUse() != null && "900000000000003001".equals(d.getUse().getCode());
					boolean isPt = d.getValue() != null && d.getValue().equals(componentDisplay);
					return isFsn || isPt;
				})
				.toList());
		// For SNOMED, noLanguage designations are also filtered to FSN + PT only
		if (FHIRHelper.isSnomedUri(component.getSystem())) {
			newDesignations.addAll(noLanguage.stream()
					.filter(d -> {
						boolean isFsn = d.getUse() != null && "900000000000003001".equals(d.getUse().getCode());
						boolean isPt = d.getValue() != null && d.getValue().equals(componentDisplay);
						return isFsn || isPt;
					}).toList());
		} else {
			newDesignations.addAll(noLanguage);
		}
	}

	// Some designation sources may populate a Coding with a missing `system`. Normalize the HL7 "display" use system to ensure stable output.
	private void normalizeDisplayUseSystem(List<ValueSet.ConceptReferenceDesignationComponent> newDesignations) {
		newDesignations.forEach(d -> {
			if (d.getUse() != null && d.getUse().getSystem() == null && FHIRConstants.DISPLAY.equals(d.getUse().getCode())) {
				// Replace coding instance to ensure HAPI model state is updated.
				d.setUse(new Coding(FHIRConstants.HL7_CS_DESIGNATION_USAGE, d.getUse().getCode(), d.getUse().getDisplay()));
			}
		});
	}

	private String resolveDisplayDesignationLanguage(String promotedDesignationLanguage, String requestedLanguage, String defaultConceptLanguage) {
		if (promotedDesignationLanguage != null) {
			return promotedDesignationLanguage;
		}
		if (requestedLanguage != null) {
			return requestedLanguage;
		}
		return defaultConceptLanguage;
	}

	// If the ValueSet component display is missing, derive the display value from other in-language designations.
	private String deriveDisplayValue(ValueSet.ValueSetExpansionContainsComponent component,
			List<ValueSet.ConceptReferenceDesignationComponent> newDesignations, String displayDesignationLanguage) {
		if (component.getDisplay() != null) {
			return component.getDisplay();
		}
		return newDesignations.stream()
				.filter(d -> displayDesignationLanguage != null && displayDesignationLanguage.equals(d.getLanguage()))
				.filter(d -> {
					Coding use = d.getUse();
					return !(use != null
							&& FHIRConstants.HL7_CS_DESIGNATION_USAGE.equals(use.getSystem())
							&& FHIRConstants.DISPLAY.equals(use.getCode()));
				})
				.map(ValueSet.ConceptReferenceDesignationComponent::getValue)
				.filter(Objects::nonNull)
				.min(Comparator
						.<String>comparingInt(String::length)
						.thenComparing(Comparator.naturalOrder()))
				.orElse(null);
	}

	// Some serialized designations may retain only the HL7 "display" coding without language/value.
	// Ensure the HAPI model has stable language/value so validators/tests can match deterministically.
	private void applyDisplayLanguageAndValue(List<ValueSet.ConceptReferenceDesignationComponent> newDesignations,
			String displayDesignationLanguage, String derivedDisplayValue) {
		newDesignations.forEach(d -> {
			if (d.getUse() != null
					&& FHIRConstants.HL7_CS_DESIGNATION_USAGE.equals(d.getUse().getSystem())
					&& FHIRConstants.DISPLAY.equals(d.getUse().getCode())) {
				if (d.getLanguage() == null && displayDesignationLanguage != null) {
					d.setLanguage(displayDesignationLanguage);
				}
				if (d.getValue() == null && derivedDisplayValue != null) {
					d.setValue(derivedDisplayValue);
				}
			}
		});
	}

	// If we have the HL7 display designation but the other in-language designations are missing `use`,
	// infer them (FSN vs synonym) to provide stable output for clients/tests.
	private void inferMissingDesignationUse(List<ValueSet.ConceptReferenceDesignationComponent> newDesignations,
			String derivedDisplayValue, String displayDesignationLanguage) {
		if (derivedDisplayValue == null || displayDesignationLanguage == null) {
			return;
		}
		List<ValueSet.ConceptReferenceDesignationComponent> noUseInLanguage = newDesignations.stream()
				.filter(d -> displayDesignationLanguage.equals(d.getLanguage()))
				.filter(d -> {
					Coding use = d.getUse();
					return use == null || use.getSystem() == null || use.getCode() == null;
				})
				.toList();

		if (noUseInLanguage.size() == 2) {
			noUseInLanguage.forEach(d -> {
				if (derivedDisplayValue.equals(d.getValue())) {
					// Synonym
					d.setUse(new Coding(FHIRConstants.SNOMED_URI, Concepts.SYNONYM, null));
				} else {
					// Fully specified name
					d.setUse(new Coding(FHIRConstants.SNOMED_URI, Concepts.FSN, null));
				}
			});
		}
	}


	private static String determineRequestedLanguage(String defaultConceptLanguage, List<Pair<LanguageDialect, Double>> weightedLanguages, Set<String> availableVarieties, Map<String, List<Locale>> languageToVarieties) {
		List<Pair<LanguageDialect,Double>> allowedLanguages = new ArrayList<>(weightedLanguages.stream().filter(x -> (x.getRight()>0d)).toList());
		allowedLanguages.sort( (a,b) -> a.getRight().compareTo(b.getRight())*-1);
		String requestedLanguage = allowedLanguages.isEmpty() ?defaultConceptLanguage:allowedLanguages.get(0).getLeft().getLanguageCode();
		if (requestedLanguage != null && !availableVarieties.contains(requestedLanguage)){
			Locale requested = Locale.forLanguageTag(requestedLanguage);
			if(languageToVarieties.get(requested.getLanguage())==null){
				List<String> forbiddenLanguages = weightedLanguages.stream().filter(x -> x.getRight().equals(0d)).map(x -> x.getLeft().getLanguageCode()).toList();
				if (forbiddenLanguages.contains(defaultConceptLanguage) || forbiddenLanguages.contains("*")) {
					requestedLanguage = null;
				} else {
					requestedLanguage = defaultConceptLanguage;
				}
			} else {
					requestedLanguage = languageToVarieties.get(requested.getLanguage()).stream()
							.findFirst()
							.map(Locale::toLanguageTag)
							.orElse(null);
			}
		}
		return requestedLanguage;
	}

	private static void addPropertyToContains(String code, ValueSet.ValueSetExpansionContainsComponent component, Type value) {
		Extension extension = new Extension();
		extension.addExtension(CODE, new CodeType(code));
		extension.addExtension(VALUE, value);
		extension.setUrl(HL7_SD_EVS_CONTAINS_PROPERTY);
		component.addExtension(extension);
	}

	private static void addPropertyToExpansion(String code, @NotNull String url, ValueSet.ValueSetExpansionComponent expansion) {
		if(expansion.getExtensionsByUrl("http://hl7.org/fhir/5.0/StructureDefinition/extension-ValueSet.expansion.property")
				.stream()
				.filter( extension -> extension.hasExtension(CODE))
				.noneMatch(extension -> extension.getExtensionByUrl(CODE).getValue().equalsDeep(new CodeType(code)))) {
			Extension expExtension = new Extension();
			expExtension.addExtension(CODE, new CodeType(code));
			expExtension.addExtension("uri", new UriType(url));
			expExtension.setUrl("http://hl7.org/fhir/5.0/StructureDefinition/extension-ValueSet.expansion.property");
			expansion.addExtension(expExtension);
		}
	}

	private static void removeExtension(Element component,String uri, String uri2,  Type value){
		List<Extension> extensions = component.getExtensionsByUrl(uri);
        for (Extension extension : extensions) {
            List<Extension> extensions2 = extension.getExtensionsByUrl(uri2);
            for (Extension item : extensions2) {
                if (item.getValue().equalsDeep(value)) {
                    component.getExtension().remove(extension);
                    return;
                }
            }
        }
	}

	private static void addInfoFromReferences(ValueSet.ValueSetExpansionContainsComponent component, List<ValueSet.ConceptReferenceComponent> references) {
		references.stream().filter(reference -> reference.getCode().equals(component.getCode())).forEach( reference -> {
			reference.getDesignation().forEach(
					rd->{
						Optional<ValueSet.ConceptReferenceDesignationComponent> od = component.getDesignation().stream().filter(ode -> ode.getLanguage().equals(rd.getLanguage())).findFirst();
						od.ifPresentOrElse(x ->{
							x.setValue(rd.getValue());
							rd.getExtension().forEach(x::addExtension);
						}, ()-> {
							if(rd.getLanguage() == null) {
								rd.setLanguage(DEFAULT_LANGUAGE_CODE);
							}
							component.addDesignation(rd);
						});
					}
			);
			reference.getExtension().forEach(
					re->{
						if (Arrays.asList(FHIRValueSetService.URLS).contains(re.getUrl())){
							Extension property = new Extension();
							switch (re.getUrl()){
								case HL7_SD_ITEM_WEIGHT:
									removeExtension(component,HL7_SD_EVS_CONTAINS_PROPERTY,CODE ,new CodeType(WEIGHT));
									property.addExtension(CODE,new CodeType(WEIGHT));
									property.addExtension(VALUE, re.getValue());
									property.setUrl(HL7_SD_EVS_CONTAINS_PROPERTY);
									break;
								case HL7_SD_VS_LABEL:
									removeExtension(component,HL7_SD_EVS_CONTAINS_PROPERTY,CODE ,new CodeType(LABEL));
									property.addExtension(CODE,new CodeType(LABEL));
									property.addExtension(VALUE, re.getValue());
									property.setUrl(HL7_SD_EVS_CONTAINS_PROPERTY);
									break;
								case HL7_SD_VS_CONCEPT_ORDER:
									removeExtension(component,HL7_SD_EVS_CONTAINS_PROPERTY,CODE ,new CodeType(ORDER));
									property.addExtension(CODE,new CodeType(ORDER));
									property.addExtension(VALUE, new DecimalType(re.getValue().primitiveValue()));
									property.setUrl(HL7_SD_EVS_CONTAINS_PROPERTY);
									break;
								case HL7_SD_VS_DEPRECATED:
									property = re;
									break;
								case HL7_SD_VS_CONCEPT_DEFINITION:
									property = re;
									break;
								default:
							}
							component.addExtension(property);
						}
					}
			);


		});
	}

	public Parameters validateCode(FHIRCodeValidationRequest request) {
		return codeValidationService.validate(request);
	}

	private static String getUrlForProperty(String propertyName){
		String url = PROPERTY_TO_URL.get(propertyName);
		if (url==null){
			return "Unknown property %s".formatted(propertyName);
		} else {
			return url;
		}
	}

}
