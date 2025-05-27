package org.snomed.snowstorm.fhir.services;

import ca.uhn.fhir.util.UrlUtil;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import com.google.common.base.Strings;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.hl7.fhir.r4.model.*;
import org.ihtsdo.otf.utils.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.core.data.domain.ConceptMini;
import org.snomed.snowstorm.core.data.domain.Concepts;
import org.snomed.snowstorm.core.data.domain.QueryConcept;
import org.snomed.snowstorm.core.data.domain.ReferenceSetMember;
import org.snomed.snowstorm.core.data.services.*;
import org.snomed.snowstorm.core.data.services.pojo.MemberSearchRequest;
import org.snomed.snowstorm.core.data.services.pojo.PageWithBucketAggregations;
import org.snomed.snowstorm.core.pojo.LanguageDialect;
import org.snomed.snowstorm.core.util.SearchAfterPage;
import org.snomed.snowstorm.fhir.config.FHIRConstants;
import org.snomed.snowstorm.fhir.domain.*;
import org.snomed.snowstorm.fhir.pojo.CanonicalUri;
import org.snomed.snowstorm.fhir.pojo.FHIRCodeSystemVersionParams;
import org.snomed.snowstorm.fhir.pojo.ValueSetExpansionParameters;
import org.snomed.snowstorm.fhir.repositories.FHIRValueSetRepository;
import org.snomed.snowstorm.fhir.services.context.CodeSystemVersionProvider;
import org.snomed.snowstorm.rest.ControllerHelper;
import org.snomed.snowstorm.rest.pojo.SearchAfterPageRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.client.elc.Queries;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
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
import static org.ihtsdo.otf.RF2Constants.LANG_EN;
import static org.snomed.snowstorm.core.data.services.ReferenceSetMemberService.AGGREGATION_MEMBER_COUNTS_BY_REFERENCE_SET;
import static org.snomed.snowstorm.core.util.CollectionUtils.orEmpty;
import static org.snomed.snowstorm.fhir.domain.ConceptConstraint.Type.INCLUDE_EXACT_MATCH;
import static org.snomed.snowstorm.fhir.services.FHIRHelper.*;
import static org.snomed.snowstorm.fhir.utils.FHIRPageHelper.toPage;

@Service
public class FHIRValueSetService {

	public static final String TX_ISSUE_TYPE = "http://hl7.org/fhir/tools/CodeSystem/tx-issue-type";
	public static final String DISPLAY_COMMENT = "display-comment";

	private class SelectedDisplay{
		public Boolean languageAvailable;
		public String selectedLanguage;
		public String selectedDisplay;

		public SelectedDisplay(String value, String language, Boolean b) {
			selectedDisplay = value;
			selectedLanguage = language;
			languageAvailable = b;
		}
	}

	public static final String[] URLS = {"http://hl7.org/fhir/StructureDefinition/itemWeight",
			"http://hl7.org/fhir/StructureDefinition/valueset-label",
			"http://hl7.org/fhir/StructureDefinition/valueset-conceptOrder",
			"http://hl7.org/fhir/StructureDefinition/valueset-deprecated",
			"http://hl7.org/fhir/StructureDefinition/valueset-concept-definition",
			"http://hl7.org/fhir/StructureDefinition/valueset-supplement"
	};

	public static final HashMap<String,String> PROPERTY_TO_URL = new HashMap<>();

	static{
		PROPERTY_TO_URL.put("definition","http://hl7.org/fhir/concept-properties#definition");
		PROPERTY_TO_URL.put("prop","http://hl7.org/fhir/test/CodeSystem/properties#prop");
		PROPERTY_TO_URL.put("alternateCode", "http://hl7.org/fhir/concept-properties#alternateCode");
	}

	// Constant to help with "?fhir_vs=refset"
	public static final String REFSETS_WITH_MEMBERS = "Refsets";

	private static final PageRequest PAGE_OF_ONE = PageRequest.of(0, 1);

	private static List<Long> defaultSearchDescTypeIds = List.of(Concepts.FSN_L, Concepts.SYNONYM_L);

	@Autowired
	private FHIRCodeSystemService codeSystemService;

	@Autowired
	private FHIRConceptService conceptService;

	@Autowired
	private FHIRValueSetRepository valueSetRepository;

	@Autowired
	private ReferenceSetMemberService snomedRefsetService;

	@Autowired
	private QueryService snomedQueryService;

	@Autowired
	private ConceptService snomedConceptService;

	@Autowired
	private ElasticsearchOperations elasticsearchOperations;

	private final Map<String, Set<String>> codeSystemVersionToRefsetsWithMembersCache = new HashMap<>();

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public Page<FHIRValueSet> findAll(Pageable pageable) {
		NativeQuery searchQuery = new NativeQueryBuilder()
				.withPageable(pageable)
				.build();
		searchQuery.setTrackTotalHits(true);
		SearchHits<FHIRValueSet> search = elasticsearchOperations.search(searchQuery, FHIRValueSet.class);
		return toPage(search, pageable);
	}

	public Optional<FHIRValueSet> findLatestByUrl(String url) {
		return find(url, null);
	}

	public FHIRValueSet findOrThrow(String url, String version) {
		Optional<FHIRValueSet> fhirValueSet = find(url, version);
		if (fhirValueSet.isEmpty()) {
			String message = format("ValueSet not found %s %s", url, version != null ? version : "");
			OperationOutcome operationOutcome = new OperationOutcome();
			OperationOutcome.OperationOutcomeIssueComponent issue = new OperationOutcome.OperationOutcomeIssueComponent();
			issue.setSeverity(OperationOutcome.IssueSeverity.ERROR)
					.setCode(OperationOutcome.IssueType.INVARIANT)
					.setDetails(null);
            issue.setDiagnostics(message);
			Extension extension = new Extension("https://github.com/IHTSDO/snowstorm/missing-valueset",new CanonicalType(CanonicalUri.of(url,version).toString()));
			issue.addExtension(extension);
			operationOutcome.addIssue(issue);
            throw new SnowstormFHIRServerResponseException(400, message, operationOutcome, null);
		}
		return fhirValueSet.get();
	}

	public Optional<FHIRValueSet> find(String url, String version) {
		List<FHIRValueSet> allByUrl = valueSetRepository.findAllByUrl(url);

		// Sort to get "latest" version first if version param is null
		allByUrl.sort(Comparator.comparing(FHIRValueSet::getVersion).reversed());

		for (FHIRValueSet valueSet : allByUrl) {
			if (version == null || version.equals(valueSet.getVersion())) {
				return Optional.of(valueSet);
			}
		}
		return Optional.empty();
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
		if (valueSet.getUrl().contains("?fhir_vs")) {
			throw exception("ValueSet url must not contain 'fhir_vs', this is reserved for implicit value sets.", OperationOutcome.IssueType.INVARIANT, 400);
		}

		// Expand to validate
		ValueSet.ValueSetExpansionComponent originalExpansion = valueSet.getExpansion();
		expand(new ValueSetExpansionParameters(valueSet, true), null);
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
		//notSupported("valueSetVersion", params.getValueSetVersion());
		notSupported("context", params.getContext());
		notSupported("contextDirection", params.getContextDirection());
		notSupported("date", params.getDate());
		//notSupported("designation", params.getDesignations());
		//notSupported("excludeNested", params.getExcludeNested());
		notSupported("excludeNotForUI", params.getExcludeNotForUI());
		notSupported("excludePostCoordinated", params.getExcludePostCoordinated());
		notSupported("version", params.getVersion());// Not part of the FHIR API spec but requested under MAINT-1363

		ValueSet hapiValueSet = findOrInferValueSet(params.getId(), params.getUrl(), params.getValueSet(), params.getValueSetVersion());
		if (hapiValueSet == null) {
			return null;
		}

		if (!hapiValueSet.hasCompose()) {
			return hapiValueSet;
		}

		List<ValueSetCycleElement> valueSetCycle = getValueSetIncludeExcludeCycle(hapiValueSet);
		if(!valueSetCycle.isEmpty()) {
			String message = getCyclicDiagnosticMessage(valueSetCycle);
			throw exception(message, OperationOutcome.IssueType.PROCESSING, 400, null, new CodeableConcept(new Coding()).setText(message));
		}

		if (params.getVersionValueSet() != null){
			CanonicalType fixVersion = hapiValueSet.getCompose().getInclude().stream().filter(x -> x.hasValueSet()).flatMap(x -> x.getValueSet().stream()).filter(x -> x.getValueAsString().equals(params.getVersionValueSet().getSystem())).findFirst().orElse(null);
			if(fixVersion!=null) {
				fixVersion.setValueAsString(params.getVersionValueSet().toString());
			}
		}

		String filter = params.getFilter();
		boolean activeOnly = TRUE == params.getActiveOnly();
		PageRequest pageRequest = params.getPageRequest(Sort.sort(QueryConcept.class).by(QueryConcept::getConceptIdL).descending());

		// Resolve the set of code system versions that will actually be used. Includes some input parameter validation.
		Set<CanonicalUri> systemVersionParam = params.getSystemVersion() != null ? Collections.singleton(params.getSystemVersion()) : Collections.emptySet();

		CodeSystemVersionProvider codeSystemVersionProvider = new CodeSystemVersionProvider(systemVersionParam,
				params.getCheckSystemVersion(), params.getForceSystemVersion(), params.getExcludeSystem(), codeSystemService);

		// Collate set of inclusion and exclusion constraints for each code system version
		CodeSelectionCriteria codeSelectionCriteria = generateInclusionExclusionConstraints(hapiValueSet, codeSystemVersionProvider, activeOnly, true);

		// Restrict expansion of ValueSets with multiple code system versions if any are SNOMED CT, to simplify pagination.
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

			QueryService.ConceptQueryBuilder conceptQuery = getSnomedConceptQuery(filter, activeOnly, codeSelectionCriteria, languageDialects);

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
			String sortField = filter != null ? "displayLen" : "code";
			pageRequest = PageRequest.of(pageRequest.getPageNumber(), pageRequest.getPageSize(), Sort.Direction.ASC, sortField);
			BoolQuery fhirConceptQuery = getFhirConceptQuery(codeSelectionCriteria, filter).build();

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

		if(conceptsPage.getTotalElements() > pageRequest.getPageSize() && pageRequest.getPageSize() >= DEFAULT_PAGESIZE) {
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
		Optional.ofNullable(params.getDesignations()).ifPresent(x->{
			x.stream().forEach( language -> {
				expansion.addParameter(new ValueSet.ValueSetExpansionParameterComponent(new StringType("designation")).setValue(new StringType(language)));
			});
		});
		allInclusionVersions.forEach(codeSystemVersion -> {
				if (codeSystemVersion.getVersion() != null) {
					expansion.addParameter(new ValueSet.ValueSetExpansionParameterComponent(new StringType("version"))
							.setValue(new CanonicalType(codeSystemVersion.getCanonical())));
					expansion.addParameter(new ValueSet.ValueSetExpansionParameterComponent(new StringType("used-codesystem"))
							.setValue(new CanonicalType(codeSystemVersion.getCanonical())));
				}
				if (codeSystemVersion.getExtensions() != null){
					for( FHIRExtension fe: codeSystemVersion.getExtensions()){
						if ("https://github.com/IHTSDO/snowstorm/codesystem-supplement".equals(fe.getUri())){
							expansion.addParameter(new ValueSet.ValueSetExpansionParameterComponent(new StringType("used-supplement"))
									.setValue(new CanonicalType(fe.getValue())));
						}
					}
				}
			}
		);
		collectCodeSystemSetWarnings(allInclusionVersions).forEach(expansion::addParameter);
		collectValueSetWarnings(codeSelectionCriteria).forEach(expansion::addParameter);

		hapiValueSet.getCompose().getInclude().stream().filter(x -> x.hasValueSet()).flatMap(x -> x.getValueSet().stream()).forEach(x ->{
			CanonicalUri uri = CanonicalUri.fromString(x.getValueAsString());
			if (uri.getVersion()==null){
				Optional<FHIRValueSet> latest = findLatestByUrl(uri.getSystem());
				uri = CanonicalUri.of(uri.getSystem(), latest.flatMap(v -> Optional.ofNullable(v.getVersion())).orElse(null));
			}
			expansion.addParameter(new ValueSet.ValueSetExpansionParameterComponent(new StringType("used-valueset")).setValue(new UriType(uri.toString())));
			expansion.addParameter(new ValueSet.ValueSetExpansionParameterComponent(new StringType("version")).setValue(new UriType(uri.toString())));
		});

		allInclusionVersions.forEach(codeSystemVersion -> {
			orEmpty(codeSystemVersion.getExtensions()).forEach(fe ->{
				hapiValueSet.addExtension(fe.getHapi());
			});
		});

		hapiValueSet.getExtension().forEach(
				ext ->{
			if(ext.getUrl().equals("http://hl7.org/fhir/StructureDefinition/valueset-supplement")) {
				if (codeSystemService.supplementExists(ext.getValue().primitiveValue(), false)) {

					if (expansion.getParameter("used-supplement").isEmpty()) {
						expansion.addParameter(new ValueSet.ValueSetExpansionParameterComponent(new StringType("used-supplement"))
								.setValue(ext.getValue()));
					}
				} else {
					String message = "Supplement %s does not exist.".formatted(ext.getValue().primitiveValue());
					CodeableConcept cc = new CodeableConcept().setText(message);
					throw exception(message,
							OperationOutcome.IssueType.NOTFOUND, 404, null, cc);

				}
			}
		});


		Optional.ofNullable(params.getProperty()).ifPresent( x ->{
					if ("alternateCode".equals(x)){
						//do nothing
					} else {
						addPropertyToExpansion(x, getUrlForProperty(x), expansion);
					}
				}
		);
		final String fhirDisplayLanguage;
		if(Optional.ofNullable(params.getDisplayLanguage()).isPresent()){
			fhirDisplayLanguage = params.getDisplayLanguage();
			expansion.addParameter(new ValueSet.ValueSetExpansionParameterComponent(new StringType("displayLanguage")).setValue(new CodeType(fhirDisplayLanguage)));
		} else if (hasDisplayLanguage(hapiValueSet)){
			fhirDisplayLanguage = hapiValueSet.getCompose().getExtensionByUrl("http://hl7.org/fhir/tools/StructureDefinition/valueset-expansion-parameter").getExtensionString("value");
			expansion.addParameter(new ValueSet.ValueSetExpansionParameterComponent(new StringType("displayLanguage")).setValue(new CodeType(fhirDisplayLanguage)));

		} else if (displayLanguage != null){
			fhirDisplayLanguage = displayLanguage;
			expansion.addParameter(new ValueSet.ValueSetExpansionParameterComponent(new StringType("displayLanguage")).setValue(new CodeType(fhirDisplayLanguage)));
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

					concept.getProperties().entrySet().forEach( p -> {
						if (p.getKey().equals("status")){
							p.getValue().stream()
									.filter(x -> x.getValue().equals("retired") || x.getValue().equals("deprecated"))
									.findFirst()
									.ifPresent(x-> {
										//component.setAbstract(true); // testcase inactive-expand doesn't expect abstract in the response
										component.setInactive(true);
									});
						} else if (p.getKey().equals("notSelectable") || p.getKey().equals("not-selectable")) {
							p.getValue().stream()
									.filter(val -> val.getValue().equals("true"))
									.findFirst()
									.ifPresent(y -> component.setAbstract(true));
						}
						else if (p.getKey().equals("http://hl7.org/fhir/StructureDefinition/itemWeight")){
							p.getValue().stream()
									.findFirst()
									.ifPresent(y-> {
										addPropertyToContains("weight", component, y.toHapiValue(null));
										addPropertyToExpansion("weight", "http://hl7.org/fhir/concept-properties#itemWeight", expansion);
									});
						} else if (p.getKey().equals("http://hl7.org/fhir/StructureDefinition/codesystem-label")){
							p.getValue().stream()
									.findFirst()
									.ifPresent(y-> {
										addPropertyToContains("label", component, y.toHapiValue(null));
										addPropertyToExpansion("label", "http://hl7.org/fhir/concept-properties#label", expansion);
									});
						} else if (p.getKey().equals("http://hl7.org/fhir/StructureDefinition/codesystem-conceptOrder")){
							p.getValue().stream()
									.findFirst()
									.ifPresent(y-> {
										addPropertyToContains("order", component, new DecimalType(y.toHapiValue(null).primitiveValue()));
										addPropertyToExpansion("order", "http://hl7.org/fhir/concept-properties#order", expansion);
									});
						}
					});

					Optional.ofNullable(params.getProperty()).ifPresent(x ->{
						List<FHIRProperty> properties =concept.getProperties().getOrDefault(x, emptyList());
						properties.stream()
								.findFirst()
								.ifPresent(y-> {
									addPropertyToContains(y.getCode(), component, y.toHapiValue(null));
								});
					});

					concept.getExtensions().forEach((key, value) ->{
						value.stream().filter(x ->!x.isSpecialExtension()).forEach( fe ->{
								//addition of these extensions is optional according to the G.G. tests
								//component.addExtension(fe.getCode(), fe.toHapiValue(null));
						});
					});
					addInfoFromReferences(component, references);
					setDisplayAndDesignations(component, concept, idAndVersionToLanguage.getOrDefault(concept.getCodeSystemVersion(), "en"), includeDesignations, fhirDisplayLanguage, params.getDesignations());
					return component;
		})
				.collect(Collectors.toList()));
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

	private List<ValueSet.ValueSetExpansionParameterComponent> collectCodeSystemSetWarnings(Set<FHIRCodeSystemVersion> codeSystems) {
        List<ValueSet.ValueSetExpansionParameterComponent> list = new ArrayList<>();
        for (FHIRCodeSystemVersion codeSystem : codeSystems) {
            for (FHIRExtension ext : orEmpty(codeSystem.getExtensions())) {
                if (ext != null && "http://hl7.org/fhir/StructureDefinition/structuredefinition-standards-status".equals(ext.getUri())) {
					list.add(new ValueSet.ValueSetExpansionParameterComponent(new StringType("warning-" + ext.getValue()))
							.setValue(new CanonicalType(codeSystem.getCanonical())));
                }
            }
			if("draft".equals(codeSystem.getStatus())) {
				list.add(new ValueSet.ValueSetExpansionParameterComponent(new StringType("warning-draft"))
						.setValue(new CanonicalType(codeSystem.getCanonical())));
			}
			if(codeSystem.isExperimental()) {
				list.add(new ValueSet.ValueSetExpansionParameterComponent(new StringType("warning-experimental"))
						.setValue(new CanonicalType(codeSystem.getCanonical())));
			}
        }
        return list;
	}

	private List<ValueSet.ValueSetExpansionParameterComponent> collectValueSetWarnings(CodeSelectionCriteria codeSelectionCriteria) {
		ArrayList<ValueSet.ValueSetExpansionParameterComponent> result = new ArrayList<>();
		collectValueSetWarnings(codeSelectionCriteria, result);
		return result;
	}

	private void collectValueSetWarnings(CodeSelectionCriteria criteria, List<ValueSet.ValueSetExpansionParameterComponent> result) {
		ValueSet valueset = findOrInferValueSet(null, criteria.getValueSetUserRef(), null, null);
		if (valueset != null) {
			valueset.getExtension().stream()
					.filter(ext -> "http://hl7.org/fhir/StructureDefinition/structuredefinition-standards-status".equals(ext.getUrl()))
					.map(warnExt ->
							new ValueSet.ValueSetExpansionParameterComponent(new StringType("warning-" + warnExt.getValue()))
									.setValue(new CanonicalType(valueset.getUrl() + "|" + valueset.getVersion())))
					.forEach(result::add);
			criteria.getNestedSelections().forEach(nestedValueSetCriteria -> collectValueSetWarnings(nestedValueSetCriteria, result));
		}
	}

	private static boolean hasDisplayLanguage(ValueSet hapiValueSet) {
        return Optional.ofNullable(hapiValueSet.getCompose().getExtensionByUrl("http://hl7.org/fhir/tools/StructureDefinition/valueset-expansion-parameter")).isPresent() && "displayLanguage".equals(hapiValueSet.getCompose().getExtensionByUrl("http://hl7.org/fhir/tools/StructureDefinition/valueset-expansion-parameter").getExtensionString("name"));
	}

	private static void setDisplayAndDesignations(ValueSet.ValueSetExpansionContainsComponent component, FHIRConcept concept, String defaultConceptLanguage, boolean includeDesignations, String displayLanguage, List<String> designationLanguages) {
		List<String> designationLang = Optional.ofNullable(designationLanguages).orElse(emptyList()).stream().map(x -> {
			String[] systemAndLanguage = x.split("\\|");
			if (systemAndLanguage.length < 2){
				return systemAndLanguage[0];
			} else {
				return systemAndLanguage[1];
			}

		}).toList();
		Map<String, ValueSet.ConceptReferenceDesignationComponent> languageToDesignation = new HashMap<>();
		Map<String, List<Locale>> languageToVarieties = new HashMap<>();
		List<Pair<LanguageDialect, Double>> weightedLanguages = ControllerHelper.parseAcceptLanguageHeaderWithWeights(displayLanguage,true);
		Locale defaultLocale = Locale.forLanguageTag(defaultConceptLanguage);;
		languageToVarieties.put(defaultLocale.getLanguage(), new ArrayList<>());
		languageToVarieties.get(defaultLocale.getLanguage()).add(defaultLocale);

		languageToDesignation.put(defaultConceptLanguage, new ValueSet.ConceptReferenceDesignationComponent().setValue(component.getDisplay())
				.setLanguage(defaultConceptLanguage) );

		List<ValueSet.ConceptReferenceDesignationComponent> noLanguage = new ArrayList<>();

		for (ValueSet.ConceptReferenceDesignationComponent designation : component.getDesignation()){
			if (designation.getLanguage()==null) {
				noLanguage.add(designation);
			} else {
				Locale designationLocale = Locale.forLanguageTag(designation.getLanguage());
				if (languageToVarieties.get(designationLocale.getLanguage()) == null) {
					List<Locale> allVarieties = new ArrayList<>();
					languageToVarieties.put(designationLocale.getLanguage(), allVarieties);
				}
				languageToVarieties.get(designationLocale.getLanguage()).add(designationLocale);
				languageToDesignation.put(designation.getLanguage(), designation);
			}

		}


			for (FHIRDesignation designation : concept.getDesignations()) {
				ValueSet.ConceptReferenceDesignationComponent designationComponent = new ValueSet.ConceptReferenceDesignationComponent();
				designationComponent.setLanguage(designation.getLanguage());
				designationComponent.setUse(designation.getUseCoding());
				designationComponent.setValue(designation.getValue());
				Optional.ofNullable(designation.getExtensions()).orElse(emptyList()).forEach(
						e -> {
							designationComponent.addExtension(e.getHapi());
						}
				);
				if (designation.getLanguage()==null) {
					noLanguage.add(designationComponent);
				} else {
					Locale designationLocale = Locale.forLanguageTag(designation.getLanguage());
					if (languageToVarieties.get(designationLocale.getLanguage()) == null) {
						List<Locale> allVarieties = new ArrayList<>();
						languageToVarieties.put(designationLocale.getLanguage(), allVarieties);
					}
					languageToVarieties.get(designationLocale.getLanguage()).add(designationLocale);
					languageToDesignation.put(designation.getLanguage(), designationComponent);
				}
			}

		String requestedLanguage = determineRequestedLanguage(defaultConceptLanguage, weightedLanguages, languageToDesignation.keySet(), languageToVarieties);
		if (requestedLanguage == null) {
			component.setDisplay(null);
		}
		else if(includeDesignations) {  // "act-class" test case from "tho" test group is expecting the "display" field to be in the expansion, not the one in "designation". Param "includeDesignations" not present for this test case
			component.setDisplay(languageToDesignation.get(requestedLanguage).getValue());
		}

		if (includeDesignations) {
			List<ValueSet.ConceptReferenceDesignationComponent> newDesignations = new ArrayList<>();
			for (Map.Entry<String, ValueSet.ConceptReferenceDesignationComponent> entry : languageToDesignation.entrySet() ){

				if (!entry.getKey().equals(requestedLanguage)) {
					if (entry.getKey().equals(defaultConceptLanguage)) {
						entry.getValue().setUse(new Coding("http://terminology.hl7.org/CodeSystem/designation-usage", "display", null));
					}


					if(designationLang.isEmpty() || designationLang.contains(entry.getValue().getLanguage())) {
						newDesignations.add(entry.getValue());
					}

				}
			}
			newDesignations.addAll(noLanguage);
			component.setDesignation(newDesignations);
		} else {
			component.setDesignation(emptyList());
		}

	}

	private static String determineRequestedLanguage(String defaultConceptLanguage, List<Pair<LanguageDialect, Double>> weightedLanguages, Set<String> availableVarieties, Map<String, List<Locale>> languageToVarieties) {
		List<Pair<LanguageDialect,Double>> allowedLanguages = new ArrayList<>(weightedLanguages.stream().filter(x -> (x.getRight()>0d)).toList());
		allowedLanguages.sort( (a,b) ->{ return a.getRight().compareTo(b.getRight())*-1;});
		String requestedLanguage = allowedLanguages.isEmpty() ?defaultConceptLanguage:allowedLanguages.get(0).getLeft().getLanguageCode();
		if (!availableVarieties.contains(requestedLanguage)){
			Locale requested = Locale.forLanguageTag(requestedLanguage);
			if(languageToVarieties.get(requested.getLanguage())==null){
				List<String> forbiddenLanguages = weightedLanguages.stream().filter(x -> x.getRight().equals(0d)).map(x -> x.getLeft().getLanguageCode()).toList();
				if(forbiddenLanguages.contains(defaultConceptLanguage)||forbiddenLanguages.contains("*")){
					requestedLanguage = null;
				} else {
					requestedLanguage = defaultConceptLanguage;
				}
			} else {
				requestedLanguage = languageToVarieties.get(requested.getLanguage()).stream().findFirst().get().toLanguageTag();
			}
		}
		return requestedLanguage;
	}

	private static void addPropertyToContains(String code, ValueSet.ValueSetExpansionContainsComponent component, Type value) {
		Extension extension = new Extension();
		extension.addExtension("code", new CodeType(code));
		extension.addExtension("value", value);
		extension.setUrl("http://hl7.org/fhir/5.0/StructureDefinition/extension-ValueSet.expansion.contains.property");
		component.addExtension(extension);
	}

	private static void addPropertyToExpansion(String code, @NotNull String url, ValueSet.ValueSetExpansionComponent expansion) {
		if(expansion.getExtensionsByUrl("http://hl7.org/fhir/5.0/StructureDefinition/extension-ValueSet.expansion.property")
				.stream()
				.filter( extension -> extension.hasExtension("code"))
				.noneMatch(extension -> extension.getExtensionByUrl("code").getValue().equalsDeep(new CodeType(code)))) {
			Extension expExtension = new Extension();
			expExtension.addExtension("code", new CodeType(code));
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
									removeExtension(component,"http://hl7.org/fhir/5.0/StructureDefinition/extension-ValueSet.expansion.contains.property","code" ,new CodeType("weight"));
									property.addExtension("code",new CodeType("weight"));
									property.addExtension("value", re.getValue());
									property.setUrl("http://hl7.org/fhir/5.0/StructureDefinition/extension-ValueSet.expansion.contains.property");
									break;
								case "http://hl7.org/fhir/StructureDefinition/valueset-label":
									removeExtension(component,"http://hl7.org/fhir/5.0/StructureDefinition/extension-ValueSet.expansion.contains.property","code" ,new CodeType("label"));
									property.addExtension("code",new CodeType("label"));
									property.addExtension("value", re.getValue());
									property.setUrl("http://hl7.org/fhir/5.0/StructureDefinition/extension-ValueSet.expansion.contains.property");
									break;
								case "http://hl7.org/fhir/StructureDefinition/valueset-conceptOrder":
									removeExtension(component,"http://hl7.org/fhir/5.0/StructureDefinition/extension-ValueSet.expansion.contains.property","code" ,new CodeType("order"));
									property.addExtension("code",new CodeType("order"));
									property.addExtension("value", new DecimalType(re.getValue().primitiveValue()));
									property.setUrl("http://hl7.org/fhir/5.0/StructureDefinition/extension-ValueSet.expansion.contains.property");
									break;
								case "http://hl7.org/fhir/StructureDefinition/valueset-deprecated":
									property = re;
									break;
								case "http://hl7.org/fhir/StructureDefinition/valueset-concept-definition":
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

	private String getUserRef(ValueSet valueSet) {
		return valueSet.getUrl() != null ? valueSet.getUrl() : "inline value set";
	}

	@NotNull
	private BoolQuery.Builder getFhirConceptQuery(CodeSelectionCriteria codeSelectionCriteria, String termFilter) {
		BoolQuery.Builder contentQuery = doGetFhirConceptQuery(codeSelectionCriteria);

		BoolQuery.Builder masterQuery = bool();
		masterQuery.must(contentQuery.build()._toQuery());
		if (termFilter != null) {
			List<String> elasticAnalyzedWords = DescriptionService.analyze(termFilter, new StandardAnalyzer());
			String searchTerm = DescriptionService.constructSearchTerm(elasticAnalyzedWords);
			String query = DescriptionService.constructSimpleQueryString(searchTerm);
			masterQuery.filter(Queries.queryStringQuery(FHIRConcept.Fields.DISPLAY, query, Operator.And, 2.0f)._toQuery());
		}
		return masterQuery;
	}

	private BoolQuery.Builder doGetFhirConceptQuery(CodeSelectionCriteria codeSelectionCriteria) {
		BoolQuery.Builder valueSetQuery = bool();

		// Attempt to combine value set constraints to reduce the required Elasticsearch clause count.
		// (Some LOINC nested value sets exceed the default 1024 clause limit).
		Map<FHIRCodeSystemVersion, AndConstraints> inclusionConstraints = combineConstraints(codeSelectionCriteria.getInclusionConstraints());
		Set<CodeSelectionCriteria> nestedSelections = combineConstraints(codeSelectionCriteria.getNestedSelections(), codeSelectionCriteria.getValueSetUserRef());
		Map<FHIRCodeSystemVersion, AndConstraints> exclusionConstraints = codeSelectionCriteria.getExclusionConstraints();

		// Inclusions
		for (Map.Entry<FHIRCodeSystemVersion, AndConstraints> versionInclusionConstraints : inclusionConstraints.entrySet()) {
			BoolQuery.Builder versionQueryBuilder = getInclusionQueryBuilder(versionInclusionConstraints, codeSelectionCriteria.getValueSetUserRef());
			valueSetQuery.should(versionQueryBuilder.build()._toQuery());// Must match at least one of these
		}

		// Nested value sets
		for (CodeSelectionCriteria nestedSelection : nestedSelections) {
			BoolQuery.Builder nestedQueryBuilder = doGetFhirConceptQuery(nestedSelection);
			valueSetQuery.should(nestedQueryBuilder.build()._toQuery());// Must match at least one of these
		}

		// Exclusions
		for (Map.Entry<FHIRCodeSystemVersion, AndConstraints> versionExclusionConstraints : exclusionConstraints.entrySet()) {
			BoolQuery.Builder versionQueryBuilder = getInclusionQueryBuilder(versionExclusionConstraints, codeSelectionCriteria.getValueSetUserRef());
			valueSetQuery.mustNot(versionQueryBuilder.build()._toQuery());
		}

		return valueSetQuery;
	}

	private Map<FHIRCodeSystemVersion, AndConstraints> combineConstraints(Map<FHIRCodeSystemVersion, AndConstraints> constraints) {
		Map<FHIRCodeSystemVersion, AndConstraints> combinedConstraints = new HashMap<>();
		Map<FHIRCodeSystemVersion, ConceptConstraint> simpleConstraints = new HashMap<>();
		for (Map.Entry<FHIRCodeSystemVersion, AndConstraints> entry : constraints.entrySet()) {
			AndConstraints andConstraints = entry.getValue();
			AndConstraints newAndConstraints = new AndConstraints();
			for (AndConstraints.OrConstraints orConstraints : andConstraints.getAndConstraints()) {
				Set<ConceptConstraint> newOrConstraints = new HashSet<>();
				for(ConceptConstraint conceptConstraint: orConstraints.getOrConstraints()) {
					if (conceptConstraint.isSimpleCodeSet()) {
						simpleConstraints.computeIfAbsent(entry.getKey(), k -> new ConceptConstraint(new HashSet<>())).getCode().addAll(conceptConstraint.getCode());
					} else {
						newOrConstraints.add(conceptConstraint);
					}
				}
				newAndConstraints.addOrConstraints(newOrConstraints);
			}
			combinedConstraints.put(entry.getKey(), newAndConstraints);
		}

		for (Map.Entry<FHIRCodeSystemVersion, ConceptConstraint> entry : simpleConstraints.entrySet()) {
			combinedConstraints.computeIfAbsent(entry.getKey(), k -> new AndConstraints()).addOrConstraints(new HashSet<>(List.of(entry.getValue())));
		}

		return combinedConstraints;
	}

	private Set<CodeSelectionCriteria> combineConstraints(Set<CodeSelectionCriteria> nestedSelections, String valueSetUserRef) {
		Set<CodeSelectionCriteria> combinedConstraints = new HashSet<>();
		Map<FHIRCodeSystemVersion, Set<ConceptConstraint>> simpleInclusionConstraints = new HashMap<>();
		for (CodeSelectionCriteria nestedSelection : nestedSelections) {
			if (nestedSelection.isOnlyInclusionsForOneVersionAndAllSimple()) {
				FHIRCodeSystemVersion codeSystemVersion = nestedSelection.getInclusionConstraints().keySet().iterator().next();
				nestedSelection.getInclusionConstraints().values().forEach(andConstraints -> simpleInclusionConstraints
						.computeIfAbsent(codeSystemVersion, v -> new HashSet<>())
						.addAll(andConstraints.constraintsFlattened()));
			} else {
				combinedConstraints.add(nestedSelection);
			}
		}

		for (Map.Entry<FHIRCodeSystemVersion, Set<ConceptConstraint>> entry : simpleInclusionConstraints.entrySet()) {
			CodeSelectionCriteria selectionCriteria = new CodeSelectionCriteria(format("nested within %s", valueSetUserRef));
			selectionCriteria.addInclusion(entry.getKey()).addOrConstraints(entry.getValue());
			combinedConstraints.add(selectionCriteria);
		}
		
		return combinedConstraints;
	}

	@NotNull
	private BoolQuery.Builder getInclusionQueryBuilder(Map.Entry<FHIRCodeSystemVersion, AndConstraints> versionInclusionConstraints, String valueSetUserRef) {
		BoolQuery.Builder versionQueryBuilder = bool().must(termQuery(FHIRConcept.Fields.CODE_SYSTEM_VERSION, versionInclusionConstraints.getKey().getId()));

		AndConstraints andConstraints = versionInclusionConstraints.getValue();
		if(!andConstraints.getAndConstraints().isEmpty()) {
			BoolQuery.Builder conjunctionQueries = bool();
			for (AndConstraints.OrConstraints orConstraints : andConstraints.getAndConstraints()) {
				if (!orConstraints.getOrConstraints().isEmpty()) {
					BoolQuery.Builder disjunctionQueries = bool();
					for (ConceptConstraint constraint : orConstraints.getOrConstraints()) {
						BoolQuery.Builder disjunctionQueryBuilder = bool();
						addQueryCriteria(constraint, disjunctionQueryBuilder, valueSetUserRef);
						disjunctionQueries.should(disjunctionQueryBuilder.build()._toQuery());// "disjunctionQueries" contains only "should" conditions, Elasticsearch forces at least one of them to match.
					}
					conjunctionQueries.must(disjunctionQueries.build()._toQuery());
				}
			}
			versionQueryBuilder.must(conjunctionQueries.build()._toQuery());// Concept must meet the two conditions
		}

		return versionQueryBuilder;
	}

	private QueryService.ConceptQueryBuilder getSnomedConceptQuery(String filter, boolean activeOnly, CodeSelectionCriteria codeSelectionCriteria,
			List<LanguageDialect> languageDialects) {

		QueryService.ConceptQueryBuilder conceptQuery = snomedQueryService.createQueryBuilder(false);
		if (codeSelectionCriteria.isAnyECL()) {
			// ECL search
			String ecl = inclusionExclusionClausesToEcl(codeSelectionCriteria);
			conceptQuery.ecl(ecl);
		} else {
			// Just a set of concept codes
			Set<String> codes = new HashSet<>();
			codeSelectionCriteria.getInclusionConstraints().values().stream().flatMap(andConstraints -> andConstraints.constraintsFlattened().stream()).forEach(include -> codes.addAll(include.getCode()));
			codeSelectionCriteria.getExclusionConstraints().values().stream().flatMap(andConstraints -> andConstraints.constraintsFlattened().stream()).forEach(include -> codes.removeAll(include.getCode()));
			conceptQuery.conceptIds(codes);
			if (activeOnly) {
				conceptQuery.activeFilter(activeOnly);
			}
		}
		if (filter != null) {
			conceptQuery.descriptionCriteria(descriptionCriteria -> {
				descriptionCriteria.term(filter)
									.type(defaultSearchDescTypeIds);
				if (!orEmpty(languageDialects).isEmpty()) {
					descriptionCriteria.searchLanguageCodes(languageDialects.stream().map(LanguageDialect::getLanguageCode).collect(Collectors.toSet()));
				}
			});
		}
		return conceptQuery;
	}

	@NotNull
	private CodeSelectionCriteria generateInclusionExclusionConstraints(ValueSet valueSet, CodeSystemVersionProvider codeSystemVersionProvider, boolean activeOnly, boolean isExpandFlow) {

		CodeSelectionCriteria codeSelectionCriteria = new CodeSelectionCriteria(getUserRef(valueSet));

		ValueSet.ValueSetComposeComponent compose = valueSet.getCompose();
        if (!activeOnly && isExpandFlow) { // testcase inactive-2a-validate, code needs to be found
			if (compose.hasInactive()) {
				activeOnly = (!compose.getInactive());
			}
		}

		List<ValueSet.ConceptSetComponent> composeInclude = compose.getInclude();
		for (int i = 0; i < composeInclude.size(); i++) {
			ValueSet.ConceptSetComponent include = composeInclude.get(i);
			if (include.hasSystem()) {
				FHIRCodeSystemVersion codeSystemVersion = codeSystemVersionProvider.get(include.getSystem(), include.getVersion());
				AndConstraints inclusionConstraints = codeSelectionCriteria.addInclusion(codeSystemVersion);
				collectConstraints(valueSet, include, i, codeSystemVersion, inclusionConstraints, activeOnly, true);
			} else if (include.hasValueSet()) {
				for (CanonicalType canonicalType : include.getValueSet()) {
					CanonicalUri canonicalUri = CanonicalUri.fromString(canonicalType.getValueAsString());
					try{
						ValueSet nestedValueSet = findOrThrow(canonicalUri.getSystem(), canonicalUri.getVersion()).getHapi();
						CodeSelectionCriteria nestedCriteria = generateInclusionExclusionConstraints(nestedValueSet, codeSystemVersionProvider, activeOnly, isExpandFlow);
						codeSelectionCriteria.addNested(nestedCriteria);
					} catch (SnowstormFHIRServerResponseException e){
						if(e.getIssueCode()== OperationOutcome.IssueType.INVARIANT){
							Extension ext = new Extension().setUrl("https://github.com/IHTSDO/snowstorm/missing-valueset").setValue(new CanonicalType(canonicalUri.toString()));
							OperationOutcome oo = createOperationOutcomeWithIssue(new CodeableConcept(new Coding(TX_ISSUE_TYPE,"not-found",null)).setText(format("Unable to find included value set '%s' version '%s'", canonicalUri.getSystem(), canonicalUri.getVersion())), OperationOutcome.IssueSeverity.ERROR, null, OperationOutcome.IssueType.NOTFOUND, Collections.singletonList(ext), "$external:2$");
							throw new SnowstormFHIRServerResponseException(404,"ValueSet not found",oo);
						}else{
							throw e;
						}
					}

				}
			} else {
				throw exception("ValueSet clause has no system or nested value set", OperationOutcome.IssueType.INVARIANT, 400);
			}
		}
		List<ValueSet.ConceptSetComponent> composeExclude = compose.getExclude();
		for (int i = 0; i < composeExclude.size(); i++) {
			ValueSet.ConceptSetComponent exclude = composeExclude.get(i);
			// Apply exclude-constraint to all resolved versions from include statements
			Set<FHIRCodeSystemVersion> codeSystemVersionsForExpansion = codeSelectionCriteria.gatherAllInclusionVersions();
			List<FHIRCodeSystemVersion> includeVersionsToExcludeFrom = codeSystemVersionsForExpansion.stream().filter(includeVersion ->
					includeVersion.getUrl().equals(exclude.getSystem()) && (exclude.getVersion() == null || exclude.getVersion().equals(includeVersion.getVersion()))
			).toList();

			for (FHIRCodeSystemVersion codeSystemVersion : includeVersionsToExcludeFrom) {
				AndConstraints exclusionConstraints = codeSelectionCriteria.addExclusion(codeSystemVersion);
				collectConstraints(valueSet, exclude, i, codeSystemVersion, exclusionConstraints, activeOnly, false);
			}
		}
		return codeSelectionCriteria;
	}

	public Parameters validateCode(String id, UriType url, UriType context, ValueSet valueSet, String valueSetVersion, String code, UriType system, String systemVersion,
								   String display, Coding coding, CodeableConcept codeableConcept, DateTimeType date, BooleanType abstractBool, String displayLanguage, BooleanType inferSystem, BooleanType activeOnly, CanonicalType versionValueSet, BooleanType lenientDisplayValidation, BooleanType valueSetMembershipOnly) {

		notSupported("context", context);
		notSupported("date", date);
		notSupported("abstract", abstractBool);

		requireExactlyOneOf("code", code, "coding", coding, "codeableConcept", codeableConcept);
		mutuallyRequired("code", code, "system", system, "inferSystem", inferSystem);
		mutuallyRequired("display", display, "code", code, "coding", coding);

		// Grab value set
		ValueSet hapiValueSet = findOrInferValueSet(id, FHIRHelper.toString(url), valueSet, valueSetVersion);
		if (hapiValueSet == null) {
			CodeableConcept detail = new CodeableConcept(new Coding(TX_ISSUE_TYPE,"not-found",null)).setText(format("A definition for the value Set '%s' could not be found",url.getValue()));
			throw exception("message", OperationOutcome.IssueType.NOTFOUND,404,null, detail);
		}

		List<ValueSetCycleElement> valueSetCycle = getValueSetIncludeExcludeCycle(hapiValueSet);
		if(!valueSetCycle.isEmpty()) {
			String message = getCyclicDiagnosticMessage(valueSetCycle);
			throw exception(message, OperationOutcome.IssueType.PROCESSING, 400, null, new CodeableConcept(new Coding()).setText(message));
		}

		Optional.ofNullable(versionValueSet).ifPresent(v->{
			hapiValueSet.getCompose().getInclude().stream().filter(x->x.hasValueSet()).flatMap(x->x.getValueSet().stream()).filter(x->CanonicalUri.fromString(x.getValueAsString()).getSystem().equals(CanonicalUri.fromString(versionValueSet.getValueAsString()).getSystem())).forEach( x -> x.setValueAsString(versionValueSet.getValueAsString()));
		});

		hapiValueSet.getExtension().forEach(
				ext ->{
					if(ext.getUrl().equals("http://hl7.org/fhir/StructureDefinition/valueset-supplement")) {
						if (!codeSystemService.supplementExists(ext.getValue().primitiveValue(), false)) {


							String message = "Supplement %s does not exist.".formatted(ext.getValue().primitiveValue());
							CodeableConcept cc = new CodeableConcept().setText(message);
							throw exception(message,
									OperationOutcome.IssueType.NOTFOUND, 404, null, cc);

						}
					}
				});

		if (hasDisplayLanguage(hapiValueSet) && displayLanguage == null) {
			displayLanguage = hapiValueSet.getCompose().getExtensionByUrl("http://hl7.org/fhir/tools/StructureDefinition/valueset-expansion-parameter").getExtensionString("value");
		}

		hapiValueSet.getExtension().forEach(
				ext ->{
					if(ext.getUrl().equals("http://hl7.org/fhir/StructureDefinition/valueset-supplement")) {
						if (!codeSystemService.supplementExists(ext.getValue().primitiveValue(), false)) {


							String message = "Supplement %s does not exist.".formatted(ext.getValue().primitiveValue());
							CodeableConcept cc = new CodeableConcept().setText(message);
							throw exception(message,
									OperationOutcome.IssueType.NOTFOUND, 404, null, cc);

						}
					}
				});

		// Get set of codings - one of which needs to be valid
		List<Coding> codings = new ArrayList<>();
		if (code != null) {
			codings.add(new Coding(FHIRHelper.toString(system), code, display).setVersion(systemVersion));
		} else if (coding != null) {
			if (display!=null) {
				coding.setDisplay(display);
			}
			codings.add(coding);
		} else {
			codings.addAll(codeableConcept.getCoding());
		}
		if (codings.isEmpty()) {
			throw exception("No codings provided to validate.", OperationOutcome.IssueType.INVALID, 400);
		}

		CanonicalUri defaultSystemVersion = systemVersion != null
				? CanonicalUri.fromString(systemVersion)
				: null;

		Set<CanonicalUri> codingSystemVersions = codings.stream()
				.map(codingA -> {
					String version = (codingA.getVersion() == null && defaultSystemVersion != null && defaultSystemVersion.getSystem().equals(codingA.getSystem()))
							? defaultSystemVersion.getVersion()
							: codingA.getVersion();
					return CanonicalUri.of(codingA.getSystem(), version);
				})
				.collect(Collectors.toSet());

		CodeSystemVersionProvider codeSystemVersionProvider = new CodeSystemVersionProvider(codingSystemVersions, null, null, null, codeSystemService);
		// Collate set of inclusion and exclusion constraints for each code system version
		CodeSelectionCriteria codeSelectionCriteria;
		try{
			codeSelectionCriteria = generateInclusionExclusionConstraints(hapiValueSet, codeSystemVersionProvider, false, false);
		} catch (SnowstormFHIRServerResponseException e){
			if(OperationOutcome.IssueType.INVARIANT.equals(e.getIssueCode()) && !e.getOperationOutcome().getIssue().stream().filter(i -> OperationOutcome.IssueType.INVARIANT.equals(i.getCode())).flatMap( ex -> ex.getExtension().stream()).filter(ex -> ex.getUrl().equals("https://github.com/IHTSDO/snowstorm/missing-valueset")).toList().isEmpty() ){
				String valueSetCanonical = e.getOperationOutcome().getIssue().stream().filter(i -> OperationOutcome.IssueType.INVARIANT.equals(i.getCode())).flatMap( ex -> ex.getExtension().stream()).filter(ex -> ex.getUrl().equals("https://github.com/IHTSDO/snowstorm/missing-valueset")).map(ext -> ext.getValue().primitiveValue()).findFirst().orElse(null);
				Parameters response = new Parameters();
				if(codeableConcept!=null){
					response.addParameter("codeableConcept", codeableConcept);
				}else if (coding != null){
                    response.addParameter("code", new CodeType(coding.getCode()));
				}else {
                    response.addParameter("code", new CodeType(code));
				}

				String message = format("A definition for the value Set '%s' could not be found; Unable to check whether the code is in the value set '%s' because the value set %s was not found", valueSetCanonical,CanonicalUri.of(hapiValueSet.getUrl(),hapiValueSet.getVersion()), valueSetCanonical);
				response.addParameter("message", message);
				response.addParameter("result", false);
                if (coding != null){
                    response.addParameter("system", new UriType(coding.getSystem()));
				}else {
                    response.addParameter("system", system);
				}
				CodeableConcept detail1 = new CodeableConcept(new Coding(TX_ISSUE_TYPE, "not-found", null)).setText(format("A definition for the value Set '%s' could not be found", valueSetCanonical));
				CodeableConcept detail2 = new CodeableConcept(new Coding(TX_ISSUE_TYPE, "vs-invalid", null)).setText(format("Unable to check whether the code is in the value set '%s' because the value set %s was not found",CanonicalUri.of(hapiValueSet.getUrl(), hapiValueSet.getVersion()),valueSetCanonical));
				OperationOutcome.OperationOutcomeIssueComponent[] issues = new OperationOutcome.OperationOutcomeIssueComponent[2];
				issues[0] = createOperationOutcomeIssueComponent(detail1, OperationOutcome.IssueSeverity.ERROR, null, OperationOutcome.IssueType.NOTFOUND, null, null);
				issues[1] = createOperationOutcomeIssueComponent(detail2, OperationOutcome.IssueSeverity.WARNING, null, OperationOutcome.IssueType.NOTFOUND, null, null);
				response.addParameter(createParameterComponentWithOperationOutcomeWithIssues(Arrays.asList(issues)));
				return response;

			} else if (OperationOutcome.IssueType.NOTFOUND.equals(e.getIssueCode()) && !e.getOperationOutcome().getIssue().stream().filter(i -> OperationOutcome.IssueType.NOTFOUND.equals(i.getCode())).flatMap( ex -> ex.getExtension().stream()).filter(ex -> ex.getUrl().equals("https://github.com/IHTSDO/snowstorm/available-codesystem-version")).toList().isEmpty()) {
				Parameters response = new Parameters();
				String theCode = coding != null ? coding.getCode() : code;
				if(codeableConcept!=null){
					response.addParameter("codeableConcept", codeableConcept);
				} else {
					response.addParameter("code", new CodeType(theCode));
				}
				CanonicalUri missing = e.getOperationOutcome().getIssue().stream().flatMap(i -> i.getExtensionsByUrl("https://github.com/IHTSDO/snowstorm/missing-codesystem-version").stream()).map(ext -> CanonicalUri.fromString(ext.getValue().primitiveValue())).findFirst().orElse(CanonicalUri.fromString(""));
				String availableVersion = e.getOperationOutcome().getIssue().stream().flatMap(i -> i.getExtensionsByUrl("https://github.com/IHTSDO/snowstorm/available-codesystem-version").stream()).map(ext -> CanonicalUri.fromString(ext.getValue().primitiveValue()).getVersion()).filter(Objects::nonNull).findFirst().orElse(null);
				final String requestedSystem;
				if (system!=null && system.getValue() != null){
					requestedSystem = system.getValue();
				} else if (coding != null && coding.getSystem() != null) {
					requestedSystem = coding.getSystem();
				} else {
					requestedSystem = missing.getSystem();
				}
				CanonicalUri valueSetCanonical = CanonicalUri.of(hapiValueSet.getUrl(), hapiValueSet.getVersion());
				String codeSystemWithCode = (requestedSystem != null ? requestedSystem : missing.getSystem()) + "#" + theCode;
				OperationOutcome.OperationOutcomeIssueComponent[] issues = new OperationOutcome.OperationOutcomeIssueComponent[2];
				boolean valueSetSystemMatchesRequestedSystem = hapiValueSet.getCompose().getInclude().stream().filter(ValueSet.ConceptSetComponent::hasSystem).map(ValueSet.ConceptSetComponent::getSystem).anyMatch(vsCodeSystem -> Objects.equals(vsCodeSystem, requestedSystem));
				if(valueSetSystemMatchesRequestedSystem) {
					String message = missing.getVersion() == null
							? format("A definition for CodeSystem '%s' could not be found, so the code cannot be validated; Unable to check whether the code is in the value set '%s' because the code system %s was not found", requestedSystem, valueSetCanonical, requestedSystem)
							: format("A definition for CodeSystem '%s' version '%s' could not be found, so the code cannot be validated. Valid versions: [%s]; Unable to check whether the code is in the value set '%s' because the code system %s was not found", requestedSystem, missing.getVersion(), availableVersion, valueSetCanonical, requestedSystem + "|" + missing.getVersion());
					response.addParameter("message", message);
					response.addParameter("result", false);
					response.addParameter("system", new UriType(requestedSystem));
					response.addParameter("version", missing.getVersion());
					response.addParameter("x-caused-by-unknown-system", new CanonicalType(missing.toString()));
					Extension e1 = new Extension("http://hl7.org/fhir/StructureDefinition/operationoutcome-message-id");
					e1.setValue(new StringType("UNKNOWN_CODESYSTEM_VERSION"));
					String ccText;
					if(availableVersion!=null){
						if(missing.getVersion() == null) {
							ccText = format("A definition for CodeSystem '%s' could not be found, so the code cannot be validated. Valid versions: [%s]", requestedSystem, availableVersion);
						} else {
							ccText = format("A definition for CodeSystem '%s' version '%s' could not be found, so the code cannot be validated. Valid versions: [%s]", requestedSystem, missing.getVersion(), availableVersion);
						}
					} else {
						ccText = format("A definition for CodeSystem '%s' could not be found, so the code cannot be validated", requestedSystem);
					}
					CodeableConcept detail1 = new CodeableConcept(new Coding(TX_ISSUE_TYPE, "not-found", null)).setText(ccText);
					issues[0] = createOperationOutcomeIssueComponent(detail1,OperationOutcome.IssueSeverity.ERROR,null, e.getOperationOutcome().getIssueFirstRep().getCode(), List.of(e1),null);
					String text = format("Unable to check whether the code is in the value set '%s' because the code system %s was not found", valueSetCanonical, missing.getVersion() == null ? requestedSystem : requestedSystem + "|" + missing.getVersion());
					CodeableConcept detail2 = new CodeableConcept(new Coding(TX_ISSUE_TYPE, "vs-invalid", null)).setText(text);
					Extension e2 = new Extension("http://hl7.org/fhir/StructureDefinition/operationoutcome-message-id");
					e2.setValue(new StringType("UNABLE_TO_CHECK_IF_THE_PROVIDED_CODES_ARE_IN_THE_VALUE_SET_CS"));
					issues[1] = createOperationOutcomeIssueComponent(detail2, OperationOutcome.IssueSeverity.WARNING, null, OperationOutcome.IssueType.NOTFOUND, List.of(e2), null);
				} else {
					String message = format("A definition for CodeSystem %s could not be found, so the code cannot be validated; The provided code '%s' was not found in the value set '%s'", requestedSystem, codeSystemWithCode, valueSetCanonical);
					response.addParameter("message", message);
					response.addParameter("result", false);
					response.addParameter("system", new UriType(requestedSystem));
					response.addParameter("x-unknown-system", new CanonicalType(requestedSystem));
					Extension e1 = new Extension("http://hl7.org/fhir/StructureDefinition/operationoutcome-message-id");
					e1.setValue(new StringType("None_of_the_provided_codes_are_in_the_value_set_one"));
					CodeableConcept detail1 = new CodeableConcept(new Coding(TX_ISSUE_TYPE, "not-in-vs", null)).setText(format("The provided code '%s' was not found in the value set '%s'", codeSystemWithCode, valueSetCanonical));
					issues[0] = createOperationOutcomeIssueComponent(detail1,OperationOutcome.IssueSeverity.ERROR,"code", OperationOutcome.IssueType.CODEINVALID, List.of(e1),null);
					String text = format("A definition for CodeSystem %s could not be found, so the code cannot be validated", requestedSystem);
					CodeableConcept detail2 = new CodeableConcept(new Coding(TX_ISSUE_TYPE, "not-found", null)).setText(text);
					Extension e2 = new Extension("http://hl7.org/fhir/StructureDefinition/operationoutcome-message-id");
					e2.setValue(new StringType("UNKNOWN_CODESYSTEM"));
					issues[1] = createOperationOutcomeIssueComponent(detail2, OperationOutcome.IssueSeverity.ERROR, "system", OperationOutcome.IssueType.NOTFOUND, List.of(e2), null);
				}
				response.addParameter(createParameterComponentWithOperationOutcomeWithIssues(Arrays.asList(issues)));
				return response;
			} else if(OperationOutcome.IssueType.NOTFOUND.equals(e.getIssueCode()) && !e.getOperationOutcome().getIssue().stream().filter(i -> OperationOutcome.IssueType.NOTFOUND.equals(i.getCode())).flatMap( ex -> ex.getExtension().stream()).filter(ex -> ex.getUrl().equals("https://github.com/IHTSDO/snowstorm/missing-valueset")).toList().isEmpty() ){
				Parameters response = new Parameters();
				String valueSetCanonical = e.getOperationOutcome().getIssue().stream().filter(i -> OperationOutcome.IssueType.NOTFOUND.equals(i.getCode())).flatMap( ex -> ex.getExtension().stream()).filter(ex -> ex.getUrl().equals("https://github.com/IHTSDO/snowstorm/missing-valueset")).map(ext -> ext.getValue().primitiveValue()).findFirst().orElse(null);
				if(codeableConcept!=null){
					response.addParameter("codeableConcept", codeableConcept);
				}else if (coding != null){
					response.addParameter("code", new CodeType(coding.getCode()));
				}else {
					response.addParameter("code", new CodeType(code));
				}

				String message = format("A definition for the value Set '%s' could not be found; Unable to check whether the code is in the value set '%s' because the value set %s was not found", valueSetCanonical,CanonicalUri.of(hapiValueSet.getUrl(),hapiValueSet.getVersion()), valueSetCanonical);
				response.addParameter("message", message);
				response.addParameter("result", false);
				if (coding != null){
					response.addParameter("system", new UriType(coding.getSystem()));
				}else {
					response.addParameter("system", system);
				}
				CodeableConcept detail1 = new CodeableConcept(new Coding(TX_ISSUE_TYPE, "not-found", null)).setText(format("A definition for the value Set '%s' could not be found", valueSetCanonical));
				CodeableConcept detail2 = new CodeableConcept(new Coding(TX_ISSUE_TYPE, "vs-invalid", null)).setText(format("Unable to check whether the code is in the value set '%s' because the value set %s was not found",CanonicalUri.of(hapiValueSet.getUrl(), hapiValueSet.getVersion()),valueSetCanonical));
				OperationOutcome.OperationOutcomeIssueComponent[] issues = new OperationOutcome.OperationOutcomeIssueComponent[2];
				issues[0] = createOperationOutcomeIssueComponent(detail1, OperationOutcome.IssueSeverity.ERROR, null, OperationOutcome.IssueType.NOTFOUND, null, null);
				issues[1] = createOperationOutcomeIssueComponent(detail2, OperationOutcome.IssueSeverity.WARNING, null, OperationOutcome.IssueType.NOTFOUND, null, null);
				response.addParameter(createParameterComponentWithOperationOutcomeWithIssues(Arrays.asList(issues)));
				return response;
			} else {
				throw e;
			}
		}

		List<String> possibleSystems = codeSelectionCriteria.getInclusionConstraints().keySet().stream().map(cs -> cs.getUrl()).toList();
		if (inferSystem != null && inferSystem.booleanValue()) {
			codings = codings.stream().flatMap(c -> {
				if (StringUtils.isEmpty(c.getSystem())) {
					return possibleSystems.stream().map(s -> c.copy().setSystem(s));
				} else {
					return Stream.of(c);
				}
			}).toList();
		}

		Set<FHIRCodeSystemVersion> resolvedCodeSystemVersionsMatchingCodings = new HashSet<>();
		boolean systemMatch = false;
		for (Coding codingA : codings) {
			for (FHIRCodeSystemVersion version : codeSelectionCriteria.gatherAllInclusionVersions()) {
				if (Optional.ofNullable(codingA.getSystem()).orElse("").equals(version.getUrl().replace("xsct", "sct"))) {
					systemMatch = true;
					if (codingA.getVersion() == null || codingA.getVersion().equals(version.getVersion()) ||
							(FHIRHelper.isSnomedUri(codingA.getSystem()) && version.getVersion().contains(codingA.getVersion()))) {
						resolvedCodeSystemVersionsMatchingCodings.add(version);
					}
				}
			}
		}

		Parameters response = new Parameters();

		Coding codingA = codings.iterator().next();
		if(codeableConcept != null) {
			Parameters.ParametersParameterComponent ccParameter = new Parameters.ParametersParameterComponent();
			ccParameter.setName("codeableConcept");
			ccParameter.setValue(codeableConcept);
			response.addParameter(ccParameter);
		}
		response.addParameter("code", codingA.getCodeElement());
		if (codingA.getSystem() != null) {
			response.addParameter("system", codingA.getSystemElement());
		}

		if (resolvedCodeSystemVersionsMatchingCodings.isEmpty()) {
			response.addParameter("result", false);
			if (systemMatch) {
				if (codings.size() == 1) {
					response.addParameter("message", format("The system '%s' is included in this ValueSet but the version '%s' is not.", codingA.getSystem(), codingA.getVersion()));
				} else {
					response.addParameter("message", "One or more codes in the CodableConcept are within a system included by this ValueSet but none of the versions match.");
				}
			} else {
				if (codings.size() == 1) {
					OperationOutcome.OperationOutcomeIssueComponent[] issues = new OperationOutcome.OperationOutcomeIssueComponent[3];
					if (Optional.ofNullable(codingA.getSystem()).orElse("").contains("ValueSet")) {
						CodeableConcept details1 = new CodeableConcept(new Coding(TX_ISSUE_TYPE, "not-in-vs", null)).setText(format("The provided code '%s' was not found in the value set '%s'", createFullyQualifiedCodeString(codingA), CanonicalUri.of(hapiValueSet.getUrl(), hapiValueSet.getVersion())));
						issues[0] = createOperationOutcomeIssueComponent(details1, OperationOutcome.IssueSeverity.ERROR, "Coding.code", OperationOutcome.IssueType.CODEINVALID, null, null);
						CodeableConcept details2 = new CodeableConcept(new Coding(TX_ISSUE_TYPE, "invalid-data", null)).setText(format("The Coding references a value set, not a code system ('%s')", codingA.getSystem()));
						issues[1] = createOperationOutcomeIssueComponent(details2, OperationOutcome.IssueSeverity.ERROR, "Coding.system", OperationOutcome.IssueType.INVALID, null, null);
						response.addParameter("message", format("The Coding references a value set, not a code system ('%s'); The provided code '%s' was not found in the value set '%s'", codingA.getSystem(), createFullyQualifiedCodeString(codingA), CanonicalUri.of(hapiValueSet.getUrl(), hapiValueSet.getVersion())));
					} else {
						String systemVersionCanonical = codingA.getSystem() + (codingA.getVersion() == null ? "" : "|" + codingA.getVersion());
						if (codeableConcept != null) {
							CanonicalUri valueSetCanonicalUri = CanonicalUri.of(hapiValueSet.getUrl(), hapiValueSet.getVersion());
							issues[0] = createOperationOutcomeIssueComponent(new CodeableConcept().addCoding(new Coding(TX_ISSUE_TYPE, "not-in-vs", null)).setText(format("No valid coding was found for the value set '%s'", valueSetCanonicalUri)), OperationOutcome.IssueSeverity.ERROR, null, OperationOutcome.IssueType.CODEINVALID, null /* Collections.singletonList(new Extension("http://hl7.org/fhir/StructureDefinition/operationoutcome-message-id",new StringType("None_of_the_provided_codes_are_in_the_value_set_one")))*/, null);
							String text = codingA.getVersion() == null
									? format("A definition for CodeSystem %s could not be found, so the code cannot be validated", codingA.getSystem())
									: format("A definition for CodeSystem '%s' version '%s' could not be found, so the code cannot be validated. Valid versions: []", codingA.getSystem(), codingA.getVersion());
							CodeableConcept details2 = new CodeableConcept(new Coding(TX_ISSUE_TYPE, "not-found", null)).setText(text);
							issues[1] = createOperationOutcomeIssueComponent(details2, OperationOutcome.IssueSeverity.ERROR, "CodeableConcept.coding[0].system", OperationOutcome.IssueType.NOTFOUND, null, null);
							String textIssue2 = format("The provided code '%s|%s#%s' was not found in the value set '%s'", codingA.getSystem(), codingA.getVersion(), codingA.getCode(), valueSetCanonicalUri);
							issues[2] = createOperationOutcomeIssueComponent(new CodeableConcept().addCoding(new Coding(TX_ISSUE_TYPE, "this-code-not-in-vs", null)).setText(textIssue2), OperationOutcome.IssueSeverity.INFORMATION, "CodeableConcept.coding[0].code", OperationOutcome.IssueType.CODEINVALID, null /* Collections.singletonList(new Extension("http://hl7.org/fhir/StructureDefinition/operationoutcome-message-id",new StringType("None_of_the_provided_codes_are_in_the_value_set_one")))*/, null);
							response.addParameter("x-unknown-system", new CanonicalType(systemVersionCanonical));
							List<Parameters.ParametersParameterComponent> systemParameters = new ArrayList<>(response.getParameters("system"));
							systemParameters.forEach(systemParameter -> response.removeChild("parameter", systemParameter));
						} else if (coding != null) {
							CodeableConcept details1 = new CodeableConcept(new Coding(TX_ISSUE_TYPE, "not-in-vs", null)).setText(format("The provided code '%s' was not found in the value set '%s'", createFullyQualifiedCodeString(codingA), CanonicalUri.of(hapiValueSet.getUrl(), hapiValueSet.getVersion())));
							issues[0] = createOperationOutcomeIssueComponent(details1, OperationOutcome.IssueSeverity.ERROR, "Coding.code", OperationOutcome.IssueType.CODEINVALID, null, null);

							if(codingA.getSystem()==null){
								CodeableConcept details2 = new CodeableConcept(new Coding(TX_ISSUE_TYPE, "invalid-data", null)).setText("Coding has no system. A code with no system has no defined meaning, and it cannot be validated. A system should be provided");
								issues[1] = createOperationOutcomeIssueComponent(details2, OperationOutcome.IssueSeverity.WARNING, "Coding", OperationOutcome.IssueType.INVALID, null, null);
							} else{
								CodeableConcept details2 = new CodeableConcept(new Coding(TX_ISSUE_TYPE, "not-found", null)).setText(format("A definition for CodeSystem %s could not be found, so the code cannot be validated", codingA.getSystem()));
								issues[1] = createOperationOutcomeIssueComponent(details2, OperationOutcome.IssueSeverity.ERROR, "Coding.system", OperationOutcome.IssueType.NOTFOUND, null, null);
								response.addParameter("x-unknown-system", new CanonicalType(systemVersionCanonical));
							}
							if(codingA.getSystem()!= null && !UrlUtil.isAbsolute(codingA.getSystem())){
								CodeableConcept details3 = new CodeableConcept(new Coding(TX_ISSUE_TYPE, "invalid-data", null)).setText("Coding.system must be an absolute reference, not a local reference");
								issues[2] = createOperationOutcomeIssueComponent(details3, OperationOutcome.IssueSeverity.ERROR, "Coding.system", OperationOutcome.IssueType.INVALID, null, null);
							}

						} else {
							CodeableConcept details1 = new CodeableConcept(new Coding(TX_ISSUE_TYPE, "not-in-vs", null)).setText(format("The provided code '%s' was not found in the value set '%s'", createFullyQualifiedCodeString(codingA), CanonicalUri.of(hapiValueSet.getUrl(), hapiValueSet.getVersion())));
							issues[0] = createOperationOutcomeIssueComponent(details1, OperationOutcome.IssueSeverity.ERROR, "code", OperationOutcome.IssueType.CODEINVALID, null, null);
							CodeableConcept details2 = new CodeableConcept(new Coding(TX_ISSUE_TYPE, "not-found", null)).setText(format("A definition for CodeSystem '%s' could not be found, so the code cannot be validated", codingA.getSystem()));
							issues[1] = createOperationOutcomeIssueComponent(details2, OperationOutcome.IssueSeverity.ERROR, "system", OperationOutcome.IssueType.NOTFOUND, null, null);
						}
						if(codingA.getSystem()==null){
							response.addParameter("message", format("Coding has no system. A code with no system has no defined meaning, and it cannot be validated. A system should be provided; The provided code '%s' was not found in the value set '%s'", createFullyQualifiedCodeString(codingA), CanonicalUri.of(hapiValueSet.getUrl(), hapiValueSet.getVersion())));
						} else if(codingA.getVersion() == null){
							response.addParameter("message", format("A definition for CodeSystem %s could not be found, so the code cannot be validated; The provided code '%s' was not found in the value set '%s'", codingA.getSystem(), createFullyQualifiedCodeString(codingA), CanonicalUri.of(hapiValueSet.getUrl(), hapiValueSet.getVersion())));
						} else {
							CanonicalUri canonicalUriValueSet = CanonicalUri.of(hapiValueSet.getUrl(), hapiValueSet.getVersion());
							response.addParameter("message", format("A definition for CodeSystem '%s' version '%s' could not be found, so the code cannot be validated. Valid versions: []; No valid coding was found for the value set '%s'; The provided code '%s' was not found in the value set '%s'", codingA.getSystem(), codingA.getVersion(), canonicalUriValueSet, createFullyQualifiedCodeString(codingA), canonicalUriValueSet));
						}

					}
					response.addParameter(createParameterComponentWithOperationOutcomeWithIssues(Arrays.asList(issues)));
				} else{
					response.addParameter("message", "None of the codes in the CodableConcept are within a system included by this ValueSet.");
				}


			}
			return response;
		}
		// Add version actually used in the response
		if (codings.size() == 1) {
			String version = resolvedCodeSystemVersionsMatchingCodings.iterator().next().getVersion();
			if (!"0".equals(version)) {
				response.addParameter("version", version);
			}
		}
		List<LanguageDialect> languageDialects;

		try {
			languageDialects = ControllerHelper.parseAcceptLanguageHeader(displayLanguage);
		} catch(IllegalArgumentException e) {
			CodeableConcept cc = new CodeableConcept(new Coding()).setText(e.getMessage());
			OperationOutcome oo = createOperationOutcomeWithIssue(cc, OperationOutcome.IssueSeverity.ERROR,null, OperationOutcome.IssueType.PROCESSING,null, e.getMessage());
			throw new SnowstormFHIRServerResponseException(404, e.getMessage(),oo);
		}

		List<OperationOutcome.OperationOutcomeIssueComponent> issues = new ArrayList<>();
		for (int i = 0; i < codings.size(); i++) {
			codingA = codings.get(i);
			FHIRConcept concept = findInValueSet(codingA, resolvedCodeSystemVersionsMatchingCodings, codeSelectionCriteria, languageDialects);
			if (concept != null) {
				if (FHIRHelper.isSnomedUri(codingA.getSystem())) {
					response.addParameter("inactive", !concept.isActive());
				} else if (!FHIRHelper.isSnomedUri(codingA.getSystem()) && !concept.isActive()){
					response.addParameter("inactive", true);
					if(activeOnly != null && activeOnly.booleanValue() || (hapiValueSet.getCompose().hasInactive() && !hapiValueSet.getCompose().getInactive())) {
						String locationExpression = "Coding.code";
						String message = format("The provided code '%s' was not found in the value set '%s'", createFullyQualifiedCodeString(codingA),CanonicalUri.of(hapiValueSet.getUrl(),hapiValueSet.getVersion()));
						issues.add(createOperationOutcomeIssueComponent(new CodeableConcept(new Coding(TX_ISSUE_TYPE, "code-rule",null)).setText(format("The code '%s' is valid but is not active", codingA.getCode())), OperationOutcome.IssueSeverity.ERROR,locationExpression, OperationOutcome.IssueType.BUSINESSRULE,null,null));
						issues.add(createOperationOutcomeIssueComponent(new CodeableConcept(new Coding(TX_ISSUE_TYPE, "not-in-vs",null)).setText(message), OperationOutcome.IssueSeverity.ERROR, locationExpression, OperationOutcome.IssueType.CODEINVALID, null, null));
						response.setParameter("message", message);
						response.setParameter("result", false);
						continue;
					}
				}
				if(!concept.getCode().equals(codingA.getCode()) && concept.getCode().equalsIgnoreCase(codingA.getCode())) {
					response.addParameter("normalized-code", new CodeType(concept.getCode()));
					FHIRCodeSystemVersion caseInSensitiveCodeSystem = resolvedCodeSystemVersionsMatchingCodings.stream().filter(fhirCodeSystemVersion -> !fhirCodeSystemVersion.isCaseSensitive()).findFirst().orElseThrow();
					issues.add(createOperationOutcomeIssueComponent(
							new CodeableConcept(new Coding(TX_ISSUE_TYPE, "code-rule",null))
									.setText(format("The code '%s' differs from the correct code '%s' by case. Although the code system '%s' is case insensitive, implementers are strongly encouraged to use the correct case anyway", codingA.getCode(), concept.getCode(), caseInSensitiveCodeSystem.getCanonical())), OperationOutcome.IssueSeverity.INFORMATION,null, OperationOutcome.IssueType.BUSINESSRULE,null,null
					));
				}
				List<ValueSet.ValueSetExpansionParameterComponent> codeSystemWarnings = collectCodeSystemSetWarnings(resolvedCodeSystemVersionsMatchingCodings);
				codeSystemWarnings.forEach(warning -> {
					issues.add(createOperationOutcomeIssueComponent(
							new CodeableConcept(new Coding(TX_ISSUE_TYPE, "status-check",null))
									.setText(format("Reference to %s CodeSystem %s", warning.getName().split("warning-")[1], warning.getValue().primitiveValue())), OperationOutcome.IssueSeverity.INFORMATION,null, OperationOutcome.IssueType.BUSINESSRULE,null,null
					));
				});
				List<ValueSet.ValueSetExpansionParameterComponent> valueSetWarnings = collectValueSetWarnings(codeSelectionCriteria);
				valueSetWarnings.forEach(warning -> {
					issues.add(createOperationOutcomeIssueComponent(
							new CodeableConcept(new Coding(TX_ISSUE_TYPE, "status-check",null))
									.setText(format("Reference to %s ValueSet %s", warning.getName().split("warning-")[1], warning.getValue().primitiveValue())), OperationOutcome.IssueSeverity.INFORMATION,null, OperationOutcome.IssueType.BUSINESSRULE,null,null
					));
				});

				String codingADisplay = codingA.getDisplay();
				if (codingADisplay == null || Objects.equals(codingADisplay, concept.getDisplay())) {
					setResultTrueIfNotFalseAlready(response);
					FHIRCodeSystemVersion codeSystemVersion = codeSystemService.findCodeSystemVersion(new FHIRCodeSystemVersionParams(codingA.getSystem()));
					if(concept.getDisplay()!=null){
						SelectedDisplay selectedDisplay = selectDisplay(codingA.getSystem(),displayLanguage,concept);
						response.addParameter("display", selectedDisplay.selectedDisplay);
					}

					if((!languageDialects.isEmpty()) && languageDialects.stream().map(LanguageDialect::getLanguageCode).map(l -> {
							List<String> languages = new ArrayList<>(concept.getDesignations().stream().map(d -> d.getLanguage()).toList());
							if(codeSystemVersion.getLanguage()!=null) {
								languages.add(codeSystemVersion.getLanguage());
							}
							return languages.isEmpty()||languages.contains(l);
						} ).noneMatch(b ->b.equals(TRUE))){
						CodeableConcept cc;
						if(codeSystemVersion.getAvailableLanguages().contains(displayLanguage)){ // should be true for this testcase
							cc = new CodeableConcept(new Coding().setSystem(TX_ISSUE_TYPE).setCode(DISPLAY_COMMENT)).setText(format("'%s' is the default display; no valid Display Names found for %s#%s in the language %s", concept.getDisplay(), codingA.getSystem(), codingA.getCode(), displayLanguage));
						} else {
							cc = new CodeableConcept(new Coding().setSystem(TX_ISSUE_TYPE).setCode(DISPLAY_COMMENT)).setText(format("'%s' is the default display; the code system %s has no Display Names for the language %s", concept.getDisplay(), codingA.getSystem(), displayLanguage));
						}

						issues.add(createOperationOutcomeIssueComponent(cc, OperationOutcome.IssueSeverity.INFORMATION, "Coding.display", OperationOutcome.IssueType.INVALID, null, null));
					}
                } else {
					FHIRDesignation termMatch = null;
					List<FHIRDesignation> designations = concept.getDesignations();
					boolean resultOk = false;
					for (int j = 0; j < designations.size() && !resultOk; j++) {
						FHIRDesignation designation = designations.get(j);
						if (codingADisplay.equalsIgnoreCase(designation.getValue())) {
							termMatch = designation;
							String designationLanguage = designation.getLanguage();
							if (designationLanguage == null || languageDialects.stream()
										.anyMatch(languageDialect -> designationLanguage.equals(languageDialect.getLanguageCode()))) {
								setResultTrueIfNotFalseAlready(response);
								response.addParameter("display", termMatch.getValue());
								resultOk = true;
							} else if (languageDialects.isEmpty() && !LANG_EN.equals(designationLanguage) && (
									(displayLanguage != null && !designationLanguage.equals(displayLanguage)) || (hapiValueSet.getLanguage() != null && !designationLanguage.equals(hapiValueSet.getLanguage())))) {
								termMatch = null;
							} else if (languageDialects.isEmpty()){
								setResultTrueIfNotFalseAlready(response);
								response.addParameter("display", concept.getDisplay());
								resultOk = true;
							}
						}
					}
					if(!resultOk) {
						String locationExpression = coding != null ? "Coding.display" : (codeableConcept != null ? "CodeableConcept.coding[" + i + "].display" : "display");
						OperationOutcome.IssueSeverity severity;
						if (lenientDisplayValidation != null && lenientDisplayValidation.booleanValue()) {
							setResultTrueIfNotFalseAlready(response);
							severity = OperationOutcome.IssueSeverity.WARNING;
						} else {
							response.setParameter("result", false);
							severity = OperationOutcome.IssueSeverity.ERROR;
						}
						if (termMatch != null) {
							response.addParameter("display", concept.getDisplay());
							String message = format("The code '%s' was found in the ValueSet and the display matched the designation with term '%s', " +
											"however the language of the designation '%s' did not match any of the languages in the requested display language '%s'.",
									codingA.getCode(), termMatch.getValue(), termMatch.getLanguage(), displayLanguage);
							response.addParameter("message", message);
							CodeableConcept cc = new CodeableConcept();
							cc.setText(message);
							cc.addCoding(new Coding().setSystem(TX_ISSUE_TYPE).setCode("invalid-display"));
							issues.add(createOperationOutcomeIssueComponent(cc, OperationOutcome.IssueSeverity.ERROR, locationExpression, OperationOutcome.IssueType.INVALID, null, null));
						} else {
							SelectedDisplay selectedDisplay = selectDisplay(codingA.getSystem(),displayLanguage,concept);
							response.addParameter("display", selectedDisplay.selectedDisplay);
							CodeableConcept cc;
							if(selectedDisplay.languageAvailable == null){
								String message = "Wrong Display Name '%s' for %s#%s. Valid display is '%s' (for the language(s) '%s')";
								response.addParameter("message", format(message, codingA.getDisplay(), codingA.getSystem(), codingA.getCode(), selectedDisplay.selectedDisplay, displayLanguage==null?"--":displayLanguage));
								cc = new CodeableConcept(new Coding().setSystem(TX_ISSUE_TYPE).setCode("invalid-display")).setText(format(message, codingA.getDisplay(), codingA.getSystem(), codingA.getCode(), selectedDisplay.selectedDisplay, displayLanguage==null?"--":displayLanguage));
							}
							else if(selectedDisplay.languageAvailable) {
								if (displayLanguage == null & concept.getDesignations().size()>0){
									String prefix = "Wrong Display Name '%s' for %s#%s. Valid display is one of %d choices: ";
									String languageFormat = "'%s' (%s)";
									String interfix = " or ";
									String suffix = " (for the language(s) '%s')";
									String fullString = format(prefix, codingA.getDisplay(), codingA.getSystem(), codingA.getCode(), concept.getDesignations().size()+1);
									//add language of codesystem
									fullString += format(languageFormat, selectedDisplay.selectedDisplay, selectedDisplay.selectedLanguage);
									for (FHIRDesignation d : concept.getDesignations()){
										fullString += (interfix + format(languageFormat, d.getValue(), d.getLanguage()));
									}
									fullString += format(suffix,"--");
									response.addParameter("message", fullString);
									cc = new CodeableConcept(new Coding().setSystem(TX_ISSUE_TYPE).setCode("invalid-display")).setText(fullString);


								} else {
									String message = "Wrong Display Name '%s' for %s#%s. Valid display is '%s' (%s) (for the language(s) '%s')";
									response.addParameter("message", format(message, codingA.getDisplay(), codingA.getSystem(), codingA.getCode(), selectedDisplay.selectedDisplay, selectedDisplay.selectedLanguage, displayLanguage != null ? displayLanguage : "--"));
									cc = new CodeableConcept(new Coding().setSystem(TX_ISSUE_TYPE).setCode("invalid-display")).setText(format(message, codingA.getDisplay(), codingA.getSystem(), codingA.getCode(), selectedDisplay.selectedDisplay, selectedDisplay.selectedLanguage, displayLanguage != null ? displayLanguage : "--"));
								}
							} else {
								String message = "Wrong Display Name '%s' for %s#%s. There are no valid display names found for language(s) '%s'. Default display is '%s'";
								response.addParameter("message", format(message, codingA.getDisplay(), codingA.getSystem(), codingA.getCode(), displayLanguage, concept.getDisplay()));
								cc = new CodeableConcept(new Coding().setSystem(TX_ISSUE_TYPE).setCode("invalid-display")).setText(format(message, codingA.getDisplay(), codingA.getSystem(), codingA.getCode(), displayLanguage, concept.getDisplay()));
							}
							issues.add(createOperationOutcomeIssueComponent(cc, severity, locationExpression, OperationOutcome.IssueType.INVALID, null, null));
						}
					}
				}
			} else {
				response.setParameter("result", false);
				final String locationExpression;
				String message;
				if (codeableConcept != null) {
					List<Parameters.ParametersParameterComponent> codeParameters = new ArrayList<>(response.getParameters("code"));
					codeParameters.forEach(v -> response.removeChild("parameter", v));
					List<Parameters.ParametersParameterComponent> systemParameters = new ArrayList<>(response.getParameters("system"));
					systemParameters.forEach(v -> response.removeChild("parameter", v));
					locationExpression = "CodeableConcept.coding[" + i + "].code";
					String text;
					if(DEFAULT_VERSION.equals(hapiValueSet.getVersion())) {
						text = format("The provided code '%s#%s' was not found in the value set '%s'", codingA.getSystem(), codingA.getCode(), hapiValueSet.getUrl());
					} else {
						text = format("The provided code '%s#%s' was not found in the value set '%s|%s'", codingA.getSystem(), codingA.getCode(), hapiValueSet.getUrl(), hapiValueSet.getVersion());
					}
					issues.add(createOperationOutcomeIssueComponent(new CodeableConcept().addCoding(new Coding(TX_ISSUE_TYPE, "this-code-not-in-vs", null)).setText(text), OperationOutcome.IssueSeverity.INFORMATION, locationExpression, OperationOutcome.IssueType.CODEINVALID, null, null));
					Coding finalCodingA = codingA;
					boolean codeSystemIncludesConcept = resolvedCodeSystemVersionsMatchingCodings.stream().anyMatch(codeSystem -> codeSystemIncludesConcept(codeSystem, finalCodingA));
						if(codeSystemIncludesConcept) {
							if(DEFAULT_VERSION.equals(hapiValueSet.getVersion())) {
								text = format("No valid coding was found for the value set '%s'", hapiValueSet.getUrl());
							} else {
								text = format("No valid coding was found for the value set '%s|%s'", hapiValueSet.getUrl(), hapiValueSet.getVersion());
							}
							issues.add(createOperationOutcomeIssueComponent(new CodeableConcept().addCoding(new Coding(TX_ISSUE_TYPE, "not-in-vs", null)).setText(text), OperationOutcome.IssueSeverity.ERROR, null, OperationOutcome.IssueType.CODEINVALID, null, null));
						} else if(!(valueSetMembershipOnly != null && valueSetMembershipOnly.booleanValue())) {
							if (context == null && codings.size() < 2) {
								text = "No valid coding was found for the value set '%s|%s'".formatted(hapiValueSet.getUrl(), hapiValueSet.getVersion());
								issues.add(createOperationOutcomeIssueComponent(new CodeableConcept().addCoding(new Coding(TX_ISSUE_TYPE, "not-in-vs", null)).setText(text), OperationOutcome.IssueSeverity.ERROR, null, OperationOutcome.IssueType.CODEINVALID, null, null));

							}
							String details2 = format("Unknown code '%s' in the CodeSystem '%s' version '%s'", codingA.getCode(), codingA.getSystem(), resolvedCodeSystemVersionsMatchingCodings.isEmpty() ? null : resolvedCodeSystemVersionsMatchingCodings.iterator().next().getVersion());
							issues.add(createOperationOutcomeIssueComponent(new CodeableConcept().addCoding(new Coding(TX_ISSUE_TYPE, "invalid-code", null)).setText(details2), OperationOutcome.IssueSeverity.ERROR, locationExpression, OperationOutcome.IssueType.CODEINVALID, null, null));
						}
					message = format("No valid coding was found for the value set '%s'; The provided code '%s#%s' was not found in the value set '%s'",  hapiValueSet.getUrl(), codingA.getSystem(), codingA.getCode(), hapiValueSet.getUrl());
				} else if (coding != null) {
					locationExpression = "Coding.code";
					String details = format("The provided code '%s#%s' was not found in the value set '%s'", codingA.getSystem(), codingA.getCode(), CanonicalUri.of(hapiValueSet.getUrl(), hapiValueSet.getVersion()));
					if(resolvedCodeSystemVersionsMatchingCodings.size() == 1 && codeSystemIncludesConcept(resolvedCodeSystemVersionsMatchingCodings.iterator().next(), codingA)) {
						issues.add(createOperationOutcomeIssueComponent(new CodeableConcept().addCoding(new Coding(TX_ISSUE_TYPE, "not-in-vs", null)).setText(details), OperationOutcome.IssueSeverity.ERROR, locationExpression, OperationOutcome.IssueType.CODEINVALID, null /* Collections.singletonList(new Extension("http://hl7.org/fhir/StructureDefinition/operationoutcome-message-id",new StringType("None_of_the_provided_codes_are_in_the_value_set_one")))*/, null));
						message = format("The provided code '%s#%s' was not found in the value set '%s'",  codingA.getSystem(), codingA.getCode(), CanonicalUri.of(hapiValueSet.getUrl(), hapiValueSet.getVersion()));
					} else {
						issues.add(createOperationOutcomeIssueComponent(new CodeableConcept().addCoding(new Coding(TX_ISSUE_TYPE, "not-in-vs", null)).setText(details), OperationOutcome.IssueSeverity.ERROR, locationExpression, OperationOutcome.IssueType.CODEINVALID, Collections.singletonList(new Extension("http://hl7.org/fhir/StructureDefinition/operationoutcome-message-id",new StringType("None_of_the_provided_codes_are_in_the_value_set_one"))), null));
						String details2 = format("Unknown code '%s' in the CodeSystem '%s' version '%s'", codingA.getCode(), codingA.getSystem(), resolvedCodeSystemVersionsMatchingCodings.isEmpty() ? null : resolvedCodeSystemVersionsMatchingCodings.iterator().next().getVersion());
						issues.add(createOperationOutcomeIssueComponent(new CodeableConcept().addCoding(new Coding(TX_ISSUE_TYPE, "invalid-code", null)).setText(details2), OperationOutcome.IssueSeverity.ERROR, locationExpression, OperationOutcome.IssueType.CODEINVALID, Collections.singletonList(new Extension("http://hl7.org/fhir/StructureDefinition/operationoutcome-message-id",new StringType("Unknown_Code_in_Version"))), null));
						message = details + "; " + details2;
					}
				} else {
					locationExpression = "code";
					message = format("The provided code '%s#%s' was not found in the value set '%s'",  codingA.getSystem(), codingA.getCode(), CanonicalUri.of(hapiValueSet.getUrl(), hapiValueSet.getVersion()));
					issues.add(createOperationOutcomeIssueComponent(new CodeableConcept().addCoding(new Coding(TX_ISSUE_TYPE, "not-in-vs", null)).setText(format("There was no valid code provided that is in the value set '%s'", CanonicalUri.of(hapiValueSet.getUrl(), hapiValueSet.getVersion()))), OperationOutcome.IssueSeverity.ERROR, locationExpression, OperationOutcome.IssueType.CODEINVALID, null, null));
					if(!codeSystemIncludesConcept(resolvedCodeSystemVersionsMatchingCodings.iterator().next(), codingA) && (inferSystem == null || !inferSystem.booleanValue())) {
						issues.add(createOperationOutcomeIssueComponent(new CodeableConcept().addCoding(new Coding(TX_ISSUE_TYPE, "invalid-code", null)).setText(format("Unknown code '%s' in the CodeSystem '%s'", codingA.getCode(), codingA.getSystem())), OperationOutcome.IssueSeverity.ERROR, locationExpression, OperationOutcome.IssueType.CODEINVALID, null, null));
					}
				}
				response.setParameter("message", message);

				if(inferSystem != null && inferSystem.booleanValue()){
					issues.add(createOperationOutcomeIssueComponent(new CodeableConcept().addCoding(new Coding(TX_ISSUE_TYPE, "cannot-infer",null)).setText(message), OperationOutcome.IssueSeverity.ERROR, locationExpression, OperationOutcome.IssueType.NOTFOUND, null , null));
					List<Parameters.ParametersParameterComponent> systemParameters = new ArrayList<>(response.getParameters("system"));
					systemParameters.forEach(v -> response.removeChild("parameter", v));
				}
			}
		}

		if(response.hasParameter("result")) {
			if(!issues.isEmpty()) {
				response.addParameter(createParameterComponentWithOperationOutcomeWithIssues(issues));
			}
			return response;
		}

		if (codings.size() != 1) {
			response.addParameter("result", false);
			response.addParameter("message", "None of the codes in the CodableConcept were found in this ValueSet.");
			CodeableConcept cc = new CodeableConcept();
			issues.add(createOperationOutcomeIssueComponent(cc, OperationOutcome.IssueSeverity.INFORMATION, "Coding.display", OperationOutcome.IssueType.INVALID, null , null));
		}
		if(!issues.isEmpty()) {
			response.addParameter(createParameterComponentWithOperationOutcomeWithIssues(issues));
		}
		return response;
	}

	private static void setResultTrueIfNotFalseAlready(Parameters response) {
		if(!response.hasParameter("result")) {
			response.addParameter("result", true);
		}
	}

	private boolean codeSystemIncludesConcept(FHIRCodeSystemVersion codeSystem, Coding coding) {
		BoolQuery.Builder query = bool().must(termQuery(FHIRConcept.Fields.CODE_SYSTEM_VERSION, codeSystem.getId()));
		addCodeConstraintToQuery(coding, codeSystem.isCaseSensitive(), query);
		List<FHIRConcept> concepts = conceptService.findConcepts(query, PAGE_OF_ONE).getContent();
		return !concepts.isEmpty();
	}

	private SelectedDisplay selectDisplay(String system, String displayLanguage, FHIRConcept concept) {
		FHIRCodeSystemVersion codeSystemVersion = codeSystemService.findCodeSystemVersion(new FHIRCodeSystemVersionParams(system));
		if (displayLanguage == null){
			if (!StringUtils.isEmpty(codeSystemVersion.getLanguage())){
				displayLanguage = codeSystemVersion.getLanguage();
			} else {
				displayLanguage = "en";
			}
		}
		final String fhirDisplayLanguage = displayLanguage;
		SelectedDisplay selectedDisplay;
		if(StringUtils.isEmpty(codeSystemVersion.getLanguage())){
			selectedDisplay = new SelectedDisplay(concept.getDisplay(),fhirDisplayLanguage,null);
			//language is not available, but it doesn't matter, because the codesystem has no language
		} else if(fhirDisplayLanguage.equals(codeSystemVersion.getLanguage()) || codeSystemVersion.getAvailableLanguages().contains(fhirDisplayLanguage)) {
			selectedDisplay = concept.getDesignations().stream()
					.filter(d -> fhirDisplayLanguage.equals(d.getLanguage()))
					.findFirst().map(d -> new SelectedDisplay(d.getValue(),d.getLanguage(),true)).orElse(new SelectedDisplay(concept.getDisplay(),codeSystemVersion.getLanguage(),Objects.equals(codeSystemVersion.getLanguage(),fhirDisplayLanguage)));

		}else{
			selectedDisplay = new SelectedDisplay(concept.getDisplay(),fhirDisplayLanguage,false);
		}
		return selectedDisplay;
	}

	@Nullable
	private ValueSet findOrInferValueSet(String id, String url, ValueSet hapiValueSet, String version) {
		mutuallyExclusive("id", id, "url", url);
		mutuallyExclusive("id", id, "valueSet", hapiValueSet);
		mutuallyExclusive("url", url, "valueSet", hapiValueSet);

		if (id != null) {
			Optional<FHIRValueSet> valueSetOptional = valueSetRepository.findById(id);
			if (valueSetOptional.isEmpty()) {
				return null;
			}
			FHIRValueSet valueSet = valueSetOptional.get();
			idUrlCrosscheck(id, url, valueSet);

			hapiValueSet = valueSet.getHapi();
		} else if (FHIRHelper.isSnomedUri(url) && url.contains("?fhir_vs")) {
			// Create snomed implicit value set
			hapiValueSet = createSnomedImplicitValueSet(url);
		} else if (url != null && url.endsWith("?fhir_vs")) {
			// Create implicit value set
			FHIRValueSetCriteria includeCriteria = new FHIRValueSetCriteria();
			includeCriteria.setSystem(url.replace("?fhir_vs", ""));
			FHIRValueSetCompose compose = new FHIRValueSetCompose();
			compose.addInclude(includeCriteria);
			FHIRValueSet valueSet = new FHIRValueSet();
			valueSet.setUrl(url);
			valueSet.setCompose(compose);
			valueSet.setStatus(Enumerations.PublicationStatus.ACTIVE.toCode());
			hapiValueSet = valueSet.getHapi();
		} else if (version != null){
			hapiValueSet = find(url, version).map(FHIRValueSet::getHapi).orElse(null);

		} else if (hapiValueSet == null) {
			hapiValueSet = findLatestByUrl(url).map(FHIRValueSet::getHapi).orElse(null);
		}
		return hapiValueSet;
	}

	private FHIRConcept findInValueSet(Coding coding, Set<FHIRCodeSystemVersion> codeSystemVersionsForExpansion, CodeSelectionCriteria codeSelectionCriteria,
			List<LanguageDialect> languageDialects) {

		// Collect sets of SNOMED and FHIR-concept constraints relevant to this coding. The later can be evaluated in a single query.
		Set<FHIRCodeSystemVersion> snomedVersions = new HashSet<>();
		Set<FHIRCodeSystemVersion> genericVersions = new HashSet<>();
		for (FHIRCodeSystemVersion codeSystemVersionForExpansion : codeSystemVersionsForExpansion) {

			// Check system and version match
			String system = coding.getSystem();
			String url = codeSystemVersionForExpansion.getUrl();
			if (system.equals(url) &&
					(coding.getVersion() == null || codeSystemVersionForExpansion.isVersionMatch(coding.getVersion()))) {

				if (codeSystemVersionForExpansion.isOnSnomedBranch()) {
					snomedVersions.add(codeSystemVersionForExpansion);
				} else {
					genericVersions.add(codeSystemVersionForExpansion);
				}
			}
		}

		QueryService.ConceptQueryBuilder snomedConceptQuery = null;
		for (FHIRCodeSystemVersion snomedVersion : snomedVersions) {
			if (snomedConceptQuery == null) {
				snomedConceptQuery = getSnomedConceptQuery(null, false, codeSelectionCriteria, languageDialects);
			}
			// Add criteria to select just this code
			snomedConceptQuery.conceptIds(Collections.singleton(coding.getCode()));
			List<ConceptMini> conceptMinis = snomedQueryService.search(snomedConceptQuery, snomedVersion.getSnomedBranch(), PAGE_OF_ONE).getContent();
			if (!conceptMinis.isEmpty()) {
				return new FHIRConcept(conceptMinis.get(0), snomedVersion, true);
			}
		}

		if (!genericVersions.isEmpty()) {
			BoolQuery.Builder fhirConceptQuery = getFhirConceptQuery(codeSelectionCriteria, null);
			// Add criteria to select just this code
			addCodeConstraintToQuery(coding, genericVersions.stream().anyMatch(FHIRCodeSystemVersion::isCaseSensitive), fhirConceptQuery);
			List<FHIRConcept> concepts = conceptService.findConcepts(fhirConceptQuery, PAGE_OF_ONE).getContent();
			if (!concepts.isEmpty()) {
				return concepts.get(0);
			}
		}

		return null;
	}

	private static void addCodeConstraintToQuery(Coding coding, boolean isCaseSensitive, BoolQuery.Builder fhirConceptQuery) {
		if (isCaseSensitive) {
			fhirConceptQuery.must(termQuery(FHIRConcept.Fields.CODE, coding.getCode()));
		} else {
			fhirConceptQuery.must(termQuery(FHIRConcept.Fields.CODE_LOWER, coding.getCode().toLowerCase()));
		}
	}

	private String inclusionExclusionClausesToEcl(CodeSelectionCriteria codeSelectionCriteria) {
		StringBuilder ecl = new StringBuilder();
		for (ConceptConstraint inclusion : codeSelectionCriteria.getInclusionConstraints().values().iterator().next().constraintsFlattened()) {
			if (ecl.length() > 0) {
				ecl.append(" OR ");
			}
			ecl.append("( ").append(toEcl(inclusion)).append(" )");
		}

		if (ecl.length() == 0) {
			// This may be impossible because ValueSet.compose.include cardinality is 1..*
			ecl.append("*");
		}

		if (!codeSelectionCriteria.getExclusionConstraints().isEmpty()) {
			// Existing ECL must be made into sub expression, because disjunction and exclusion expressions can not be mixed.
			ecl = new StringBuilder().append("( ").append(ecl).append(" )");
			for (ConceptConstraint exclusion : codeSelectionCriteria.getExclusionConstraints().values().iterator().next().constraintsFlattened()) {
				ecl.append(" MINUS ( ").append(exclusion.getEcl()).append(" )");
			}
		}

		return ecl.toString();
	}

	private String toEcl(ConceptConstraint inclusion) {
		if (inclusion.hasEcl()) {
			return inclusion.getEcl();
		}
		return String.join(" OR ", inclusion.getCode());
	}

	private void addQueryCriteria(ConceptConstraint inclusion, BoolQuery.Builder versionQuery, String valueSetUserRef) {
		if (inclusion.isActiveOnly()!=null && inclusion.isActiveOnly()) {
			versionQuery.mustNot(termsQuery(FHIRConcept.Fields.PROPERTIES + ".inactive.value", Collections.singleton(Boolean.toString(inclusion.isActiveOnly()))));
		}

		if (inclusion.getCode() != null) {
			switch(inclusion.getType()){
				case MATCH_REGEX:
					versionQuery.must(regexpQuery(FHIRConcept.Fields.CODE, inclusion.getCode().stream().findFirst().orElseGet(()->null)));
					break;
				default:
					versionQuery.must(termsQuery(FHIRConcept.Fields.CODE, inclusion.getCode()));
			}

		} else if (inclusion.getParent() != null) {
			switch(inclusion.getType()){
				case MATCH_REGEX:
					versionQuery.must(regexpQuery(FHIRConcept.Fields.PARENTS, inclusion.getParent().stream().findFirst().orElseGet(()->null)));
					break;
				default:
					versionQuery.must(termsQuery(FHIRConcept.Fields.PARENTS, inclusion.getParent()));
			}
		} else if (inclusion.getAncestor() != null) {
			switch(inclusion.getType()){
				case MATCH_REGEX:
					versionQuery.must(regexpQuery(FHIRConcept.Fields.ANCESTORS, inclusion.getAncestor().stream().findFirst().orElseGet(()->null)));
					break;
				default:
					versionQuery.must(termsQuery(FHIRConcept.Fields.ANCESTORS, inclusion.getAncestor()));
			}
		} else if (inclusion.getProperties() != null ){
			switch(inclusion.getType()){
				case MATCH_REGEX:
					inclusion.getProperties().keySet().forEach(x ->
							versionQuery.must(regexpQuery(FHIRConcept.Fields.PROPERTIES + "." + x + ".value", Optional.ofNullable(inclusion.getProperties().get(x)).orElseGet(()->Collections.emptySet()).stream().findFirst().orElseGet(()->null)))
					);
					break;
				case INCLUDE_EXACT_MATCH:
					inclusion.getProperties().keySet().forEach(x ->
							versionQuery.must(termsQuery(FHIRConcept.Fields.PROPERTIES + "." + x + ".value.keyword", inclusion.getProperties().get(x)))
					);
					break;
				case EXCLUDE_EXACT_MATCH:
					inclusion.getProperties().keySet().forEach(x ->
							versionQuery.mustNot(termsQuery(FHIRConcept.Fields.PROPERTIES + "." + x + ".value.keyword", inclusion.getProperties().get(x)))
					);
					break;
				default:
					inclusion.getProperties().keySet().forEach(x ->
							versionQuery.must(termsQuery(FHIRConcept.Fields.PROPERTIES + "." + x + ".value", inclusion.getProperties().get(x)))
					);
			}
		} else if (inclusion.isActiveOnly()!=null){
			//prevent exception in case of inclusion criterion that only contains activeOnly
		} else {
			String message = "Unrecognised constraints for ValueSet: " + valueSetUserRef;
			logger.error(message);
			throw exception(message, OperationOutcome.IssueType.EXCEPTION, 500);
		}
	}

	private void collectConstraints(ValueSet valueSet, ValueSet.ConceptSetComponent include, int includeIndex, FHIRCodeSystemVersion codeSystemVersion, AndConstraints andConstraints, boolean activeOnly, boolean isInclude) {
		Set<ConceptConstraint> constraints = new HashSet<>();
		if (!include.getConcept().isEmpty()) {
			List<String> codes = include.getConcept().stream().map(ValueSet.ConceptReferenceComponent::getCode).collect(Collectors.toList());
			constraints.add(new ConceptConstraint(codes).setActiveOnly(activeOnly));
			andConstraints.addOrConstraints(constraints);
			constraints = new HashSet<>();
		}
		if (!include.getFilter().isEmpty()) {
			var includeFilter = include.getFilter();
			for (int filterIndex = 0; filterIndex < includeFilter.size(); filterIndex++) {
				ValueSet.ConceptSetFilterComponent filter = includeFilter.get(filterIndex);
				String property = filter.getProperty();
				ValueSet.FilterOperator op = filter.getOp();
				String value = getFilterValueElseThrow(filter, valueSet, include.getSystem(), property, op.toCode(), includeIndex, filterIndex, isInclude);
				if (codeSystemVersion.isOnSnomedBranch()) {
					// SNOMED CT filters:
					// concept, is-a, [conceptId]
					// concept, in, [refset]
					// constraint, =, [ECL]
					// expression, =, Refsets - special case to deal with '?fhir_vs=refset'. Matches the Ontoserver compose for these, not part of the spec but at least consistent.
					// expressions, =, true/false
					if ("concept".equals(property)) {
						if (op == ValueSet.FilterOperator.ISA) {
							if (Strings.isNullOrEmpty(value)) {
								throw exception("Value missing for SNOMED CT ValueSet concept 'is-a' filter", OperationOutcome.IssueType.INVALID, 400);
							}
							constraints.add(new ConceptConstraint().setEcl("<< " + value));
						} else if (op == ValueSet.FilterOperator.IN) {
							if (Strings.isNullOrEmpty(value)) {
								throw exception("Value missing for SNOMED CT ValueSet concept 'in' filter.", OperationOutcome.IssueType.INVALID, 400);
							}
							// Concept must be in the specified refset
							String ecl = "^ " + value;
							if (activeOnly) {
								ecl += " {{ C active=true }}";
							}
							constraints.add(new ConceptConstraint().setEcl(ecl));
						} else {
							throw exception(format("Unexpected operation '%s' for SNOMED CT ValueSet 'concept' filter.", op.toCode()), OperationOutcome.IssueType.INVALID, 400);
						}
					} else if ("constraint".equals(property)) {
						if (op == ValueSet.FilterOperator.EQUAL) {
							if (Strings.isNullOrEmpty(value)) {
								throw exception("Value missing for SNOMED CT ValueSet 'constraint' filter.", OperationOutcome.IssueType.INVALID, 400);
							}
							constraints.add(new ConceptConstraint().setEcl(value));
						} else {
							throw exception(format("Unexpected operation '%s' for SNOMED CT ValueSet 'constraint' filter.", op.toCode()), OperationOutcome.IssueType.INVALID, 400);
						}
					} else if ("expression".equals(property)) {
						if (op == ValueSet.FilterOperator.EQUAL) {
							if (REFSETS_WITH_MEMBERS.equals(value)) {
								// Concept must represent a reference set which has members in this code system version.
								// Lookup uses a cache.
								constraints.add(new ConceptConstraint(findAllRefsetsWithActiveMembers(codeSystemVersion)));
							} else if (value != null) {
								constraints.add(new ConceptConstraint().setEcl(value));
							} else {
								throw exception("Value missing for SNOMED CT ValueSet 'expression' filter.", OperationOutcome.IssueType.INVALID, 400);
							}
						} else {
							throw exception(format("Unexpected operation '%s' for SNOMED CT ValueSet 'expression' filter.", op.toCode()), OperationOutcome.IssueType.INVALID, 400);
						}
					} else if ("expressions".equals(property)) {
						if (op == ValueSet.FilterOperator.EQUAL) {
							if ("true".equalsIgnoreCase(value)) {
								throw exception("This server does not yet support SNOMED CT ValueSets with expressions.", OperationOutcome.IssueType.INVALID,	400);
							}// else false, which has no effect.
						} else {
							throw exception(format("Unexpected operation '%s' for SNOMED CT ValueSet 'expressions' flag.", op.toCode()), OperationOutcome.IssueType.INVALID, 400);
						}
					} else if ("parent".equals(property)) {
						if (op == ValueSet.FilterOperator.EQUAL) {
							constraints.add(new ConceptConstraint().setEcl("<! " + value));
						} else {
							throw exception(format("Unexpected operation '%s' for SNOMED CT ValueSet 'parent' filter.", op.toCode()), OperationOutcome.IssueType.INVALID, 400);
						}
					} else {
						throw exception(format("Unexpected property '%s' for SNOMED CT ValueSet filter.", property), OperationOutcome.IssueType.INVALID, 400);
					}
				} else if (codeSystemVersion.getUrl().equals(FHIRConstants.LOINC_ORG)) {
					// LOINC filters:
					// parent/ancestor, =/in, [partCode]
					// [property], =/regex, [value] - not supported
					// copyright, =, LOINC/3rdParty - not supported

					if (Strings.isNullOrEmpty(value)) {
						throw exception("Value missing for LOINC ValueSet filter", OperationOutcome.IssueType.INVALID, 400);
					}
					Set<String> values = op == ValueSet.FilterOperator.IN ? new HashSet<>(Arrays.asList(value.split(","))) : Collections.singleton(value);
					if ("parent".equals(property)) {
						constraints.add(new ConceptConstraint().setParent(values));
					} else if ("ancestor".equals(property)) {
						constraints.add(new ConceptConstraint().setAncestor(values));
					} else {
						throw exception(format("This server does not support ValueSet filter using LOINC property '%s'. " +
								"Only parent and ancestor filters are supported for LOINC.", property), OperationOutcome.IssueType.NOTSUPPORTED, 400);
					}
				} else if (codeSystemVersion.getUrl().startsWith(FHIRConstants.HL_7_ORG_FHIR_SID_ICD_10)) {
					// Spec says there are no filters for ICD-9 and 10.
					throw exception("This server does not expect any ValueSet property filters for ICD-10.", OperationOutcome.IssueType.NOTSUPPORTED, 400);
				} else {
					// Generic code system
					if ("concept".equals(property) && op == ValueSet.FilterOperator.ISA) {
						Set<String> singleton = Collections.singleton(value);
						constraints.add(new ConceptConstraint(singleton).setActiveOnly(activeOnly));
						constraints.add(new ConceptConstraint().setAncestor(singleton));
					} else if ("concept".equals(property) && op == ValueSet.FilterOperator.DESCENDENTOF) {
						Set<String> singleton = Collections.singleton(value);
						constraints.add(new ConceptConstraint().setAncestor(singleton).setActiveOnly(activeOnly));
					} else if (op == ValueSet.FilterOperator.EQUAL){
						Set<String> singleton = Collections.singleton(value);
						Map<String, Collection<String>> properties = new HashMap<>();
						properties.put(property,singleton);
						constraints.add(new ConceptConstraint().setProperties(properties).setType(INCLUDE_EXACT_MATCH).setActiveOnly(activeOnly));
					} else if (op == ValueSet.FilterOperator.REGEX){
						Set<String> singleton = Collections.singleton(value.replace(" ","\\s").replace("\\t","\\s").replace("\\n","\\s").replace("\\r","\\s").replace("\\f","\\s"));
						if ("code".equals(property)){
							constraints.add(new ConceptConstraint(singleton).setType(ConceptConstraint.Type.MATCH_REGEX).setActiveOnly(activeOnly));
						} else {
							Map<String, Collection<String>> properties = new HashMap<>();
							properties.put(property, singleton);
							constraints.add(new ConceptConstraint().setProperties(properties).setType(ConceptConstraint.Type.MATCH_REGEX).setActiveOnly(activeOnly));
						}
					} else if (op == ValueSet.FilterOperator.IN){
						Set<String> values = Arrays.stream(value.split(",")).collect(Collectors.toSet());
						Map<String, Collection<String>> properties = new HashMap<>();
						properties.put(property, values);
						constraints.add(new ConceptConstraint().setProperties(properties).setType(INCLUDE_EXACT_MATCH).setActiveOnly(activeOnly));
					} else if (op == ValueSet.FilterOperator.NOTIN){
						Set<String> values = Arrays.stream(value.split(",")).collect(Collectors.toSet());
						Map<String, Collection<String>> properties = new HashMap<>();
						properties.put(property, values);
						constraints.add(new ConceptConstraint().setProperties(properties).setType(ConceptConstraint.Type.EXCLUDE_EXACT_MATCH).setActiveOnly(activeOnly));
					}
					else{
						throw exception("This server does not support this ValueSet property filter on generic code systems. " +
								"Supported filters for generic code systems are: (concept, is-a), (concept, descendant-of), (<any>, =), (concept, in), (concept, not-in).", OperationOutcome.IssueType.NOTSUPPORTED, 400);
					}
				}
				andConstraints.addOrConstraints(constraints);
				constraints = new HashSet<>();
			}
		}
		if(activeOnly && andConstraints.isEmpty()) {
			if (FHIRHelper.isSnomedUri(codeSystemVersion.getUrl()) || codeSystemVersion.getUrl().equals(FHIRConstants.LOINC_ORG) || codeSystemVersion.getUrl().startsWith(FHIRConstants.HL_7_ORG_FHIR_SID_ICD_10)) {
				//do nothing
			} else {
				constraints.add(new ConceptConstraint().setActiveOnly(activeOnly));
				andConstraints.addOrConstraints(constraints);
			}
		}
	}

	private static String getFilterValueElseThrow(ValueSet.ConceptSetFilterComponent filter, ValueSet valueSet, String system, String property, String op, int includeIndex, int filterIndex, boolean isInclude) {
		if(filter.getValue() != null) {
			return filter.getValue();
		}
		CodeableConcept cc = new CodeableConcept()
				.setCoding(List.of(new Coding("http://hl7.org/fhir/tools/CodeSystem/tx-issue-type", "vs-invalid", null)))
				.setText(format("The system %s filter with property = %s, op = %s has no value", system, property, op));
		String location = format("ValueSet['%s|%s].compose.%s[%d].filter[%d].value", valueSet.getUrl(), valueSet.getVersion(), isInclude ? "include" : "exclude", includeIndex, filterIndex);
		OperationOutcome operationOutcome = createOperationOutcomeWithIssue(cc, OperationOutcome.IssueSeverity.ERROR, location, OperationOutcome.IssueType.INVALID, null, null);
		throw new SnowstormFHIRServerResponseException(400, "Missing filter value", operationOutcome, null);
	}

	private ValueSet createSnomedImplicitValueSet(String url) {
		FHIRValueSetCriteria includeCriteria = new FHIRValueSetCriteria();
		includeCriteria.setSystem(url.startsWith(SNOMED_URI_UNVERSIONED) ? SNOMED_URI_UNVERSIONED : SNOMED_URI);
		String urlWithoutParams = url.substring(0, url.indexOf("?"));
		if (!urlWithoutParams.equals(includeCriteria.getSystem())) {
			includeCriteria.setVersion(urlWithoutParams);
		}

		FHIRValueSetFilter filter;
		// Are we looking for all known refsets? Special case.
		if (url.endsWith("?fhir_vs=refset")) {
			filter = new FHIRValueSetFilter("expression", "=", REFSETS_WITH_MEMBERS);
		} else {
			String ecl = determineEcl(url);
			filter = new FHIRValueSetFilter("constraint", "=", ecl);
		}
		includeCriteria.setFilter(Collections.singletonList(filter));
		FHIRValueSetCompose compose = new FHIRValueSetCompose();
		compose.addInclude(includeCriteria);
		FHIRValueSet valueSet = new FHIRValueSet();
		valueSet.setUrl(url);
		valueSet.setCompose(compose);
		valueSet.setStatus(Enumerations.PublicationStatus.ACTIVE.toCode());
		return valueSet.getHapi();
	}

	/*
	 See https://www.hl7.org/fhir/snomedct.html#implicit
	 */
	private String determineEcl(String url) {
		String ecl;
		if (url.endsWith("?fhir_vs")) {
			// Return all of SNOMED CT in this situation
			ecl = "*";
		} else if (url.contains(IMPLICIT_ISA)) {
			String sctId = url.substring(url.indexOf(IMPLICIT_ISA) + IMPLICIT_ISA.length());
			ecl = "<<" + sctId;
		} else if (url.contains(IMPLICIT_REFSET)) {
			String sctId = url.substring(url.indexOf(IMPLICIT_REFSET) + IMPLICIT_REFSET.length());
			ecl = "^" + sctId;
		} else if (url.contains(IMPLICIT_ECL)) {
			ecl = url.substring(url.indexOf(IMPLICIT_ECL) + IMPLICIT_ECL.length());
			ecl = URLDecoder.decode(ecl, StandardCharsets.UTF_8);
		} else {
			throw exception("url is expected to include parameter with value: 'fhir_vs=ecl/'", OperationOutcome.IssueType.VALUE, 400);
		}
		return ecl;
	}

	private Set<String> findAllRefsetsWithActiveMembers(FHIRCodeSystemVersion codeSystemVersion) {

		String versionKey = codeSystemVersion.getVersion();// contains module and effective time

		// Check cache
		if (!codeSystemVersion.isSnomedUnversioned()) {// No cache for daily build
			synchronized (codeSystemVersionToRefsetsWithMembersCache) {
				Set<String> refsets = codeSystemVersionToRefsetsWithMembersCache.get(versionKey);
				if (refsets != null) {
					return refsets;
				}
			}
		}

		PageWithBucketAggregations<ReferenceSetMember> bucketPage = snomedRefsetService.findReferenceSetMembersWithAggregations(codeSystemVersion.getSnomedBranch(),
				ControllerHelper.getPageRequest(0, 1, FHIRHelper.MEMBER_SORT), new MemberSearchRequest().active(true));

		List<ConceptMini> allRefsets = new ArrayList<>();
		if (bucketPage.getBuckets() != null && bucketPage.getBuckets().containsKey(AGGREGATION_MEMBER_COUNTS_BY_REFERENCE_SET)) {
			allRefsets = bucketPage.getBuckets().get(AGGREGATION_MEMBER_COUNTS_BY_REFERENCE_SET).keySet().stream()
					.map(s -> new ConceptMini(s, null))
					.collect(Collectors.toList());
		}
		Set<String> refsets = allRefsets.stream().map(ConceptMini::getConceptId).collect(Collectors.toSet());

		// Add to cache
		if (!codeSystemVersion.isSnomedUnversioned()) {
			synchronized (codeSystemVersionToRefsetsWithMembersCache) {
				codeSystemVersionToRefsetsWithMembersCache.put(versionKey, refsets);
			}
		}

		return refsets;
	}

	private void idUrlCrosscheck(String id, String url, FHIRValueSet valueSet) {
		if (url != null && !url.equals(valueSet.getUrl())) {
			throw exception(format("The requested ValueSet URL '%s' does not match the URL '%s' of the ValueSet found using identifier '%s'.",
					url, valueSet.getUrl(), id), OperationOutcome.IssueType.INVALID, 400);
		}
	}

	private static String getUrlForProperty(String propertyName){
		String url = PROPERTY_TO_URL.get(propertyName);
		if (url==null){
			return "Unknown property %s".formatted(propertyName);
		} else {
			return url;
		}
	}

	private List<ValueSetCycleElement> getValueSetIncludeExcludeCycle(ValueSet valueSet) {
		return getValueSetIncludeExcludeCycle(valueSet, new HashSet<>(), true);
	}

	private List<ValueSetCycleElement> getValueSetIncludeExcludeCycle(ValueSet valueSet, Set<String> visited, boolean isIncluded) {
		if (valueSet.getCompose() == null) {
			return Collections.emptyList();
		}

		String key = valueSet.getUrl() + "|" + valueSet.getVersion();
		if (!visited.add(key)) {
			// Cycle detected
			return new ArrayList<>(List.of(new ValueSetCycleElement(isIncluded, valueSet.getUrl(), valueSet.getVersion())));
		}

		ValueSetCycleElement current = new ValueSetCycleElement(isIncluded, valueSet.getUrl(), valueSet.getVersion());

		if (valueSet.getCompose().hasInclude()) {
			var cycle = detectCycle(valueSet.getCompose().getInclude(), visited, true);
			if (!cycle.isEmpty()) {
				cycle.add(current);
				return cycle;
			}
		}

		if (valueSet.getCompose().hasExclude()) {
			var cycle = detectCycle(valueSet.getCompose().getExclude(), visited, false);
			if (!cycle.isEmpty()) {
				cycle.add(current);
				return cycle;
			}
		}

		visited.remove(key);
		return Collections.emptyList();
	}

	private List<ValueSetCycleElement> detectCycle(List<ValueSet.ConceptSetComponent> components, Set<String> visited, boolean isIncluded) {
		for (var component : components) {
			for (CanonicalType canonical : component.getValueSet()) {
				ValueSet child = findOrInferValueSet(null, canonical.getValueAsString(), null, null);
				if (child != null) {
					var cycle = getValueSetIncludeExcludeCycle(child, visited, isIncluded);
					if (!cycle.isEmpty()) {
						return cycle;
					}
				}
			}
		}
		return Collections.emptyList();
	}

	private String getCyclicDiagnosticMessage(List<ValueSetCycleElement> valueSetCycle) {
		ValueSetCycleElement last = valueSetCycle.get(0);
		String lastConstraint = (last.include() ? "including " : "excluding ") + last.getCanonicalUrlVersion();
		StringBuilder parentPath = new StringBuilder();
		for(int i = 1; i < valueSetCycle.size(); i++) {
			parentPath.append(valueSetCycle.get(i).getCanonicalUrlVersion());
			if(i < valueSetCycle.size() - 1) {
				parentPath.append(", ");
			}
		}
		return format("Cyclic reference detected when %s via [%s]", lastConstraint, parentPath);
	}
}
