package org.snomed.snowstorm.fhir.services;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.apache.commons.lang3.tuple.Pair;
import org.hl7.fhir.r4.model.*;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.core.data.domain.ConceptMini;
import org.snomed.snowstorm.core.data.domain.QueryConcept;
import org.snomed.snowstorm.core.data.services.*;
import org.snomed.snowstorm.core.pojo.LanguageDialect;
import org.snomed.snowstorm.core.util.SearchAfterPage;
import org.snomed.snowstorm.fhir.config.FHIRConstants;
import org.snomed.snowstorm.fhir.domain.*;
import org.snomed.snowstorm.fhir.pojo.CanonicalUri;
import org.snomed.snowstorm.fhir.pojo.FHIRCodeValidationRequest;
import org.snomed.snowstorm.fhir.pojo.ValueSetExpansionParameters;
import org.snomed.snowstorm.fhir.repositories.FHIRValueSetRepository;
import org.snomed.snowstorm.fhir.services.context.CodeSystemVersionProvider;
import org.snomed.snowstorm.rest.ControllerHelper;
import org.snomed.snowstorm.rest.pojo.SearchAfterPageRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
	public static final String ORDER = "order";
	public static final String VS_INVALID = "vs-invalid";
	public static final String WARNING_DASH = "warning-";
	public static final String WEIGHT = "weight";

	public static final String HL7_SD_EVS_CONTAINS_PROPERTY = "http://hl7.org/fhir/5.0/StructureDefinition/extension-ValueSet.expansion.contains.property";
	public static final String HL7_SD_ITEM_WEIGHT = "http://hl7.org/fhir/StructureDefinition/item-weight";
	public static final String HL7_SD_OUTCOME_MESSAGE_ID = "http://hl7.org/fhir/StructureDefinition/operationoutcome-message-id";
	public static final String HL7_SD_VS_CONCEPT_DEFINITION = "http://hl7.org/fhir/StructureDefinition/valueset-concept-definition";
	public static final String HL7_SD_VS_CONCEPT_ORDER = "http://hl7.org/fhir/StructureDefinition/valueset-conceptOrder";
	public static final String HL7_SD_VS_DEPRECATED = "http://hl7.org/fhir/StructureDefinition/valueset-deprecated";
	public static final String HL7_SD_VS_EXPANSION_PARAMETER = "http://hl7.org/fhir/tools/StructureDefinition/valueset-expansion-parameter";
	public static final String HL7_SD_VS_LABEL = "http://hl7.org/fhir/StructureDefinition/valueset-label";
	public static final String HL7_SD_VS_SUPPLEMENT = "http://hl7.org/fhir/StructureDefinition/valueset-supplement";
	
	public static final String MISSING_VALUESET = "https://github.com/IHTSDO/snowstorm/missing-valueset";
	public static final String VS_DEF_NOT_FOUND = "A definition for the value Set '%s' could not be found";

	@Value("${fhir.default.langDialectCode}")
	private String defaultLangDialectCode;

	protected static final String[] URLS = {
			HL7_SD_ITEM_WEIGHT,
			HL7_SD_VS_LABEL,
			HL7_SD_VS_CONCEPT_ORDER,
			HL7_SD_VS_DEPRECATED,
			HL7_SD_VS_CONCEPT_DEFINITION,
			HL7_SD_VS_SUPPLEMENT
	};

	protected static final Map<String,String> PROPERTY_TO_URL = new HashMap<>();

	static{
		PROPERTY_TO_URL.put("definition","http://hl7.org/fhir/concept-properties#definition");
		PROPERTY_TO_URL.put("prop","http://hl7.org/fhir/test/CodeSystem/properties#prop");
		PROPERTY_TO_URL.put("alternateCode", "http://hl7.org/fhir/concept-properties#alternateCode");
	}

	@Autowired
	private FHIRCodeSystemService codeSystemService;

	@Autowired
	private FHIRConceptService conceptService;

	@Autowired
	private FHIRValueSetRepository valueSetRepository;

	@Autowired
	private QueryService snomedQueryService;

	@Autowired
	private ConceptService snomedConceptService;

	@Autowired
	private ElasticsearchOperations elasticsearchOperations;

	@Autowired
	private FHIRValueSetFinderService vsFinderService;

	@Autowired
	private FHIRValueSetCycleDetectionService vsCycleDetectionService;

	@Autowired
	private FHIRValueSetCodeValidationService codeValidationService;

	@Autowired
	private FHIRValueSetConstraintsService constraintsService;

	@Autowired
	private FHIRWarningsService warningsService;

	private final Logger logger = LoggerFactory.getLogger(getClass());

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
		// Lots of not supported parameters
		notSupported("context", params.getContext());
		notSupported("contextDirection", params.getContextDirection());
		notSupported(DATE, params.getDate());
		notSupported("excludeNotForUI", params.getExcludeNotForUI());
		notSupported("excludePostCoordinated", params.getExcludePostCoordinated());
		notSupported(VERSION, params.getVersion());// Not part of the FHIR API spec but requested under MAINT-1363

		//Do we have any sort of diplay language set?  Use the default if not, to ensure at least some display value is set.
		//Discuss Config.DEFAULT_LANGUAGE_CODE currently 'en'
		if (displayLanguage == null && params.getDisplayLanguage() == null) {
			displayLanguage = defaultLangDialectCode;
		}

		ValueSet hapiValueSet = vsFinderService.findOrInferValueSet(params.getId(), params.getUrl(), params.getValueSet(), params.getValueSetVersion());
		if (hapiValueSet == null) {
			return null;
		}

		if (!hapiValueSet.hasCompose()) {
			return hapiValueSet;
		}

		vsCycleDetectionService.verifyNoCycles(hapiValueSet);

		if (params.getVersionValueSet() != null){
			hapiValueSet.getCompose().getInclude().stream()
					.filter(ValueSet.ConceptSetComponent::hasValueSet).flatMap(x -> x.getValueSet().stream())
					.filter(x -> x.getValueAsString().equals(params.getVersionValueSet().getSystem()))
					.findFirst()
					.ifPresent(fixVersion -> fixVersion.setValueAsString(params.getVersionValueSet().toString()));
		}

		String filter = params.getFilter();
		boolean activeOnly = TRUE == params.getActiveOnly();
		PageRequest pageRequest = params.getPageRequest(Sort.sort(QueryConcept.class).by(QueryConcept::getConceptIdL).descending());

		// Resolve the set of code system versions that will actually be used. Includes some input parameter validation.
		Set<CanonicalUri> systemVersionParam = params.getSystemVersion() != null ? Collections.singleton(params.getSystemVersion()) : Collections.emptySet();

		CodeSystemVersionProvider codeSystemVersionProvider = new CodeSystemVersionProvider(systemVersionParam,
				params.getCheckSystemVersion(), params.getForceSystemVersion(), params.getExcludeSystem(), codeSystemService);

		// Collate set of inclusion and exclusion constraints for each code system version
		CodeSelectionCriteria codeSelectionCriteria = constraintsService.generateInclusionExclusionConstraints(hapiValueSet, codeSystemVersionProvider, activeOnly, true);

		// Restrict the expansion of ValueSets with multiple code system versions if any are SNOMED CT, to simplify pagination.
		Set<FHIRCodeSystemVersion> allInclusionVersions = codeSelectionCriteria.gatherAllInclusionVersions();
		boolean isSnomed = allInclusionVersions.stream().anyMatch(FHIRCodeSystemVersion::isOnSnomedBranch);
		if (isSnomed) {
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

		if (allInclusionVersions.isEmpty()) {
			return hapiValueSet;
		}

		Page<FHIRConcept> conceptsPage;
		String copyright = null;
		boolean includeDesignations = TRUE.equals(params.getIncludeDesignations());
		if (isSnomed) {
			// SNOMED CT Expansion
			// Only expansion of single version is supported.
			copyright = SNOMED_VALUESET_COPYRIGHT;

			FHIRCodeSystemVersion codeSystemVersion = allInclusionVersions.iterator().next();
			List<LanguageDialect> languageDialects = ControllerHelper.parseAcceptLanguageHeader(FHIRHelper.getDisplayLanguage(params.getDisplayLanguage(),displayLanguage));

			// Constraints:
			// - Elasticsearch prevents us from requesting results beyond the first 10K
			// Strategy:
			// - Load concept ids until we reach the requested page
			// - Then load the concepts for that page
			int offsetRequested = (int) pageRequest.getOffset();
			int limitRequested = (int) (pageRequest.getOffset() + pageRequest.getPageSize());

			QueryService.ConceptQueryBuilder conceptQuery = vsFinderService.getSnomedConceptQuery(filter, activeOnly, codeSelectionCriteria, languageDialects);

			int totalResults = 0;
			List<Long> conceptsToLoad;
			if (limitRequested > LARGE_PAGE.getPageSize()) {
				// Have to use search-after feature to paginate to the page requested because of Elasticsearch 10k limit.
				SearchAfterPage<Long> previousPage = null;
				List<Long> allConceptIds = new LongArrayList();
				boolean loadedAll = false;
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
				if (allConceptIds.size() > offsetRequested) {
					conceptsToLoad = new LongArrayList(allConceptIds).subList(offsetRequested, Math.min(limitRequested, allConceptIds.size()));
				} else {
					conceptsToLoad = new ArrayList<>();
				}
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

			conceptsPage = new PageImpl<>(conceptsOnRequestedPage, pageRequest, totalResults);
		} else {
			// FHIR Concept Expansion (non-SNOMED)
			String sortField = filter != null ? "displayLen" : CODE;
			pageRequest = PageRequest.of(pageRequest.getPageNumber(), pageRequest.getPageSize(), Sort.Direction.ASC, sortField);
			BoolQuery fhirConceptQuery = vsFinderService.getFhirConceptQuery(codeSelectionCriteria, filter).build();

			int offsetRequested = (int) pageRequest.getOffset();
			int limitRequested = (int) (pageRequest.getOffset() + pageRequest.getPageSize());

			int totalResults = 0;
			List<String> conceptsToLoad;
			if (limitRequested > LARGE_PAGE.getPageSize()) {
				// Have to use search-after feature to paginate to the page requested because of Elasticsearch 10k limit.
				SearchAfterPage<String> previousPage = null;
				List<String> allConceptCodes = new ArrayList<>();
				boolean loadedAll = false;
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
				if (allConceptCodes.size() > offsetRequested) {
					conceptsToLoad = new ArrayList<>(allConceptCodes).subList(offsetRequested, Math.min(limitRequested, allConceptCodes.size()));
				} else {
					conceptsToLoad = new ArrayList<>();
				}
				if (!conceptsToLoad.isEmpty()) {
					BoolQuery.Builder conceptsToLoadQuery = bool()
							.must(fhirConceptQuery._toQuery())
							.must(termsQuery(FHIRConcept.Fields.CODE, conceptsToLoad));
					conceptsPage = conceptService.findConcepts(conceptsToLoadQuery, LARGE_PAGE);
					conceptsPage = new PageImpl<>(conceptsPage.getContent(), pageRequest, totalResults);
				} else {
					conceptsPage = new PageImpl<>(new ArrayList<>(), pageRequest, totalResults);
				}
			} else {
				conceptsPage = conceptService.findConcepts(bool().must(fhirConceptQuery._toQuery()), pageRequest);
			}
		}

		if (expansionRequestExceedsLimits(conceptsPage, pageRequest, params)) {
			String message = format("The operation was stopped to protect server resources, the number of resulting concepts exceeded the maximum number (%d) allowed", pageRequest.getPageSize());
			throw exception(message, OperationOutcome.IssueType.TOOCOSTLY, 404, null, new CodeableConcept(new Coding()).setText(message));
		}

		Map<String, String> idAndVersionToUrl = allInclusionVersions.stream()
				.collect(Collectors.toMap(FHIRCodeSystemVersion::getId, FHIRCodeSystemVersion::getUrl));
		Map<String, String> idAndVersionToLanguage = allInclusionVersions.stream()
				.filter(fhirCodeSystemVersion -> fhirCodeSystemVersion.getLanguage() != null).collect(Collectors.toMap(FHIRCodeSystemVersion::getId, FHIRCodeSystemVersion::getLanguage));
		ValueSet.ValueSetExpansionComponent expansion = new ValueSet.ValueSetExpansionComponent();
		String id = UUID.randomUUID().toString();
		expansion.setId(id);
		expansion.setIdentifier("urn:uuid:"+id);
		expansion.setTimestamp(new Date());
		Optional.ofNullable(params.getOffset()).ifPresent(x->expansion.addParameter(new ValueSet.ValueSetExpansionParameterComponent(new StringType("offset")).setValue(new IntegerType(x))));
		Optional.ofNullable(params.getCount()).ifPresent(x->expansion.addParameter(new ValueSet.ValueSetExpansionParameterComponent(new StringType("count")).setValue(new IntegerType(x))));
		Optional.ofNullable(params.getActiveOnly()).ifPresent(x->expansion.addParameter(new ValueSet.ValueSetExpansionParameterComponent(new StringType("activeOnly")).setValue(new BooleanType(x))));
		Optional.ofNullable(params.getExcludeNested()).ifPresent(x->expansion.addParameter(new ValueSet.ValueSetExpansionParameterComponent(new StringType("excludeNested")).setValue(new BooleanType(x))));
		Optional.ofNullable(params.getIncludeDesignations()).ifPresent(x->expansion.addParameter(new ValueSet.ValueSetExpansionParameterComponent(new StringType("includeDesignations")).setValue(new BooleanType(x))));
		Optional.ofNullable(params.getDesignations()).ifPresent(x->
			x.forEach(language -> 
				expansion.addParameter(new ValueSet.ValueSetExpansionParameterComponent(new StringType("designation")).setValue(new StringType(language)))
			));
		allInclusionVersions.forEach(codeSystemVersion -> {
				if (codeSystemVersion.getVersion() != null) {
					expansion.addParameter(new ValueSet.ValueSetExpansionParameterComponent(new StringType(VERSION))
							.setValue(new CanonicalType(codeSystemVersion.getCanonical())));
					expansion.addParameter(new ValueSet.ValueSetExpansionParameterComponent(new StringType("used-codesystem"))
							.setValue(new CanonicalType(codeSystemVersion.getCanonical())));
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

		warningsService.collectCodeSystemSetWarnings(allInclusionVersions).forEach(expansion::addParameter);

		warningsService.collectValueSetWarnings(codeSelectionCriteria).forEach(expansion::addParameter);

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

		allInclusionVersions.forEach(codeSystemVersion -> 
			orEmpty(codeSystemVersion.getExtensions()).forEach(fe ->
				hapiValueSet.addExtension(fe.getHapi())));

		hapiValueSet.getExtension().forEach(
				ext ->{
			if(ext.getUrl().equals(HL7_SD_VS_SUPPLEMENT)) {
				if (codeSystemService.supplementExists(ext.getValue().primitiveValue(), false)) {

					if (expansion.getParameter(USED_SUPPLEMENT).isEmpty()) {
						expansion.addParameter(new ValueSet.ValueSetExpansionParameterComponent(new StringType(USED_SUPPLEMENT))
								.setValue(ext.getValue()));
					}
				} else {
					String message = SUPPLEMENT_NOT_EXIST.formatted(ext.getValue().primitiveValue());
					CodeableConcept cc = new CodeableConcept().setText(message);
					throw exception(message,
							OperationOutcome.IssueType.NOTFOUND, 404, null, cc);

				}
			}
		});

		Optional.ofNullable(params.getProperty()).ifPresent( x ->{
					if (!"alternateCode".equals(x)){
						addPropertyToExpansion(x, getUrlForProperty(x), expansion);
					}
				}
		);
		final String fhirDisplayLanguage;
		if(Optional.ofNullable(params.getDisplayLanguage()).isPresent()){
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

		expansion.setContains(conceptsPage.stream().map(concept -> {
					List<ValueSet.ConceptReferenceComponent> references = hapiValueSet.getCompose().getInclude().stream()
							.flatMap(set -> set.getConcept().stream()).filter(c -> c.getCode().equals(concept.getCode())).toList();

					ValueSet.ValueSetExpansionContainsComponent component = new ValueSet.ValueSetExpansionContainsComponent()
							.setSystem(idAndVersionToUrl.get(concept.getCodeSystemVersion()))
							.setCode(concept.getCode())
							.setInactiveElement(concept.isActive() ? null : new BooleanType(true))
							.setDisplay(concept.getDisplay());

					concept.getProperties().forEach((key, value) -> {
						if (key.equals("status")) {
							value.stream()
									.filter(x -> x.getValue().equals("retired") || x.getValue().equals("deprecated"))
									.findFirst()
									.ifPresent(x ->
											//component.setAbstract(true); // testcase inactive-expand doesn't expect abstract in the response
											component.setInactive(true));
						} else if (key.equals("notSelectable") || key.equals("not-selectable")) {
							value.stream()
									.filter(val -> val.getValue().equals("true"))
									.findFirst()
									.ifPresent(y -> component.setAbstract(true));
						} else if (key.equals("http://hl7.org/fhir/StructureDefinition/itemWeight")) {
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
						}
					});

					Optional.ofNullable(params.getProperty()).ifPresent(x ->{
						List<FHIRProperty> properties =concept.getProperties().getOrDefault(x, emptyList());
						properties.stream()
								.findFirst()
								.ifPresent(y->
									addPropertyToContains(y.getCode(), component, y.toHapiValue(null))
								);
					});
					addInfoFromReferences(component, references);
					setDisplayAndDesignations(component, concept, idAndVersionToLanguage.getOrDefault(concept.getCodeSystemVersion(), "en"), includeDesignations, fhirDisplayLanguage, params.getDesignations());
					return component;
		})
				.toList());
		expansion.setOffset(conceptsPage.getNumber() * conceptsPage.getSize());
		expansion.setTotal((int) conceptsPage.getTotalElements());
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

	private boolean expansionRequestExceedsLimits(Page<FHIRConcept> conceptsPage, PageRequest pageRequest, ValueSetExpansionParameters params) {
		int maximumPageSize = params.getAllowMaximumSizeExpansionAsBoolean() ? MAXIMUM_PAGESIZE : DEFAULT_PAGESIZE;
		return conceptsPage.getTotalElements() > pageRequest.getPageSize() && pageRequest.getPageSize() > maximumPageSize;
	}

	static boolean hasDisplayLanguage(ValueSet hapiValueSet) {
        return Optional.ofNullable(hapiValueSet.getCompose().getExtensionByUrl(HL7_SD_VS_EXPANSION_PARAMETER)).isPresent() && DISPLAY_LANGUAGE.equals(hapiValueSet.getCompose().getExtensionByUrl(HL7_SD_VS_EXPANSION_PARAMETER).getExtensionString("name"));
	}

	private static void setDisplayAndDesignations(ValueSet.ValueSetExpansionContainsComponent component,
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
		Locale defaultLocale = Locale.forLanguageTag(defaultConceptLanguage);
		languageToVarieties.put(defaultLocale.getLanguage(), new ArrayList<>(List.of(defaultLocale)));

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
						.collect(Collectors.groupingBy(d -> {
							if (d.getLanguage() != null) {
								Locale locale = Locale.forLanguageTag(d.getLanguage());
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

		if (requestedLanguage == null) {
			component.setDisplay(null);
		} else if (includeDesignations) {
			String displayTerm = languageToDesignation.getOrDefault(requestedLanguage, emptyList()).stream()
					.filter(d -> d.getUse() != null && FHIRConstants.HL7_DESIGNATION_USAGE.equals(d.getUse().getSystem()))
					.findFirst()
					.orElse(new ValueSet.ConceptReferenceDesignationComponent())
					.getValue();
			component.setDisplay(displayTerm);
		}

		// Set component designations based on requested languages
		if (includeDesignations) {
			List<ValueSet.ConceptReferenceDesignationComponent> newDesignations = languageToDesignation.values().stream()
					.flatMap(List::stream)
					.filter(d -> designationLang.isEmpty() || designationLang.contains(d.getLanguage()))
					.collect(Collectors.toList());

			newDesignations.addAll(noLanguage);
			component.setDesignation(newDesignations);
		} else {
			component.setDesignation(emptyList());
		}
	}


	private static String determineRequestedLanguage(String defaultConceptLanguage, List<Pair<LanguageDialect, Double>> weightedLanguages, Set<String> availableVarieties, Map<String, List<Locale>> languageToVarieties) {
		List<Pair<LanguageDialect,Double>> allowedLanguages = new ArrayList<>(weightedLanguages.stream().filter(x -> (x.getRight()>0d)).toList());
		allowedLanguages.sort( (a,b) -> a.getRight().compareTo(b.getRight())*-1);
		String requestedLanguage = allowedLanguages.isEmpty() ?defaultConceptLanguage:allowedLanguages.get(0).getLeft().getLanguageCode();
		if (!availableVarieties.contains(requestedLanguage)){
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
								case "http://hl7.org/fhir/StructureDefinition/itemWeight":
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
