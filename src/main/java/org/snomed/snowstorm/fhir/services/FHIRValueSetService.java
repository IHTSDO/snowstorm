package org.snomed.snowstorm.fhir.services;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.elasticsearch.common.Strings;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.hl7.fhir.r4.model.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.core.data.domain.ConceptMini;
import org.snomed.snowstorm.core.data.domain.QueryConcept;
import org.snomed.snowstorm.core.data.domain.ReferenceSetMember;
import org.snomed.snowstorm.core.data.services.ConceptService;
import org.snomed.snowstorm.core.data.services.QueryService;
import org.snomed.snowstorm.core.data.services.ReferenceSetMemberService;
import org.snomed.snowstorm.core.data.services.pojo.MemberSearchRequest;
import org.snomed.snowstorm.core.data.services.pojo.PageWithBucketAggregations;
import org.snomed.snowstorm.core.pojo.LanguageDialect;
import org.snomed.snowstorm.core.util.SearchAfterPage;
import org.snomed.snowstorm.fhir.domain.*;
import org.snomed.snowstorm.fhir.pojo.CanonicalUri;
import org.snomed.snowstorm.fhir.pojo.ValueSetExpansionParameters;
import org.snomed.snowstorm.fhir.repositories.FHIRValueSetRepository;
import org.snomed.snowstorm.fhir.services.context.CodeSystemVersionProvider;
import org.snomed.snowstorm.rest.ControllerHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.data.elasticsearch.core.SearchAfterPageRequest;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.stereotype.Service;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

import static io.kaicode.elasticvc.api.ComponentService.LARGE_PAGE;
import static java.lang.Boolean.TRUE;
import static java.lang.String.format;
import static org.elasticsearch.index.query.QueryBuilders.*;
import static org.snomed.snowstorm.core.data.services.ReferenceSetMemberService.AGGREGATION_MEMBER_COUNTS_BY_REFERENCE_SET;
import static org.snomed.snowstorm.core.util.CollectionUtils.orEmpty;
import static org.snomed.snowstorm.fhir.services.FHIRHelper.*;
import static org.snomed.snowstorm.fhir.utils.FHIRPageHelper.toPage;

@Service
public class FHIRValueSetService {

	// Constant to help with "?fhir_vs=refset"
	public static final String REFSETS_WITH_MEMBERS = "Refsets";

	private static final PageRequest PAGE_OF_ONE = PageRequest.of(0, 1);

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
	private ElasticsearchRestTemplate elasticsearchTemplate;

	private final Map<String, Set<String>> codeSystemVersionToRefsetsWithMembersCache = new HashMap<>();

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public Page<FHIRValueSet> findAll(Pageable pageable) {
		NativeSearchQuery searchQuery = new NativeSearchQueryBuilder()
				.withPageable(pageable)
				.build();
		searchQuery.setTrackTotalHits(true);
		SearchHits<FHIRValueSet> search = elasticsearchTemplate.search(searchQuery, FHIRValueSet.class);
		return toPage(search, pageable);
	}

	public Optional<FHIRValueSet> findLatestByUrl(String url) {
		return find(url, null);
	}

	public FHIRValueSet findOrThrow(String url, String version) {
		Optional<FHIRValueSet> fhirValueSet = find(url, version);
		if (fhirValueSet.isEmpty()) {
			throw exception(format("ValueSet not found %s %s", url, version != null ? version : ""), OperationOutcome.IssueType.INVARIANT, 400);
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

	public void saveAllValueSetsOfCodeSystemVersion(List<ValueSet> valueSets) {
		for (ValueSet valueSet : orEmpty(valueSets)) {
			try {
				logger.info("Saving ValueSet {}", valueSet.getIdElement());
				createOrUpdateValueset(valueSet);
			} catch (SnowstormFHIRServerResponseException e) {
				logger.error("Failed to store value set {}", valueSet.getIdElement(), e);
			}
		}
	}

	public FHIRValueSet createOrUpdateValueset(ValueSet valueSet) {
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
		notSupported("valueSetVersion", params.getValueSetVersion());
		notSupported("context", params.getContext());
		notSupported("contextDirection", params.getContextDirection());
		notSupported("date", params.getDate());
		notSupported("designation", params.getDesignations());
		notSupported("excludeNested", params.getExcludeNested());
		notSupported("excludeNotForUI", params.getExcludeNotForUI());
		notSupported("excludePostCoordinated", params.getExcludePostCoordinated());
		notSupported("version", params.getVersion());// Not part of the FHIR API spec but requested under MAINT-1363

		ValueSet hapiValueSet = findOrInferValueSet(params.getId(), params.getUrl(), params.getValueSet());
		if (hapiValueSet == null) {
			return null;
		}

		if (!hapiValueSet.hasCompose()) {
			return hapiValueSet;
		}

		String filter = params.getFilter();
		boolean activeOnly = TRUE == params.getActiveOnly();
		PageRequest pageRequest = params.getPageRequest(Sort.sort(QueryConcept.class).by(QueryConcept::getConceptIdL).descending());

		// Resolve the set of code system versions that will actually be used. Includes some input parameter validation.
		Set<CanonicalUri> systemVersionParam = params.getSystemVersion() != null ? Collections.singleton(params.getSystemVersion()) : Collections.emptySet();

		CodeSystemVersionProvider codeSystemVersionProvider = new CodeSystemVersionProvider(systemVersionParam,
				params.getCheckSystemVersion(), params.getForceSystemVersion(), params.getExcludeSystem(), codeSystemService);

		// Collate set of inclusion and exclusion constraints for each code system version
		CodeSelectionCriteria codeSelectionCriteria = generateInclusionExclusionConstraints(hapiValueSet, codeSystemVersionProvider, activeOnly);

		// Restrict expansion of ValueSets with multiple code system versions if any are SNOMED CT, to simplify pagination.
		Set<FHIRCodeSystemVersion> allInclusionVersions = codeSelectionCriteria.gatherAllInclusionVersions();
		boolean isSnomed = allInclusionVersions.stream().anyMatch(FHIRCodeSystemVersion::isSnomed);
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
			List<LanguageDialect> languageDialects = ControllerHelper.parseAcceptLanguageHeader(displayLanguage);

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
			BoolQueryBuilder fhirConceptQuery = getFhirConceptQuery(codeSelectionCriteria, filter);

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
					fhirConceptQuery.must(termsQuery(FHIRConcept.Fields.CODE, conceptsToLoad));
					conceptsPage = conceptService.findConcepts(fhirConceptQuery, LARGE_PAGE);
					conceptsPage = new PageImpl<>(conceptsPage.getContent(), pageRequest, totalResults);
				} else {
					conceptsPage = new PageImpl<>(new ArrayList<>(), pageRequest, totalResults);
				}
			} else {
				conceptsPage = conceptService.findConcepts(fhirConceptQuery, pageRequest);
			}
		}

		Map<String, String> idAndVersionToUrl = allInclusionVersions.stream()
				.collect(Collectors.toMap(FHIRCodeSystemVersion::getId, FHIRCodeSystemVersion::getUrl));
		ValueSet.ValueSetExpansionComponent expansion = new ValueSet.ValueSetExpansionComponent();
		expansion.setId(UUID.randomUUID().toString());
		expansion.setTimestamp(new Date());
		allInclusionVersions.forEach(codeSystemVersion ->
				expansion.addParameter(new ValueSet.ValueSetExpansionParameterComponent(new StringType("version"))
						.setValue(new CanonicalType(codeSystemVersion.getCanonical()))));
		expansion.addParameter(new ValueSet.ValueSetExpansionParameterComponent(new StringType("displayLanguage")).setValue(new StringType(displayLanguage)));
		expansion.setContains(conceptsPage.stream().map(concept -> {
					ValueSet.ValueSetExpansionContainsComponent component = new ValueSet.ValueSetExpansionContainsComponent()
							.setSystem(idAndVersionToUrl.get(concept.getCodeSystemVersion()))
							.setCode(concept.getCode())
							.setInactiveElement(concept.isActive() ? null : new BooleanType(false))
							.setDisplay(concept.getDisplay());
					if (includeDesignations) {
						for (FHIRDesignation designation : concept.getDesignations()) {
							ValueSet.ConceptReferenceDesignationComponent designationComponent = new ValueSet.ConceptReferenceDesignationComponent();
							designationComponent.setLanguage(designation.getLanguage());
							designationComponent.setUse(designation.getUseCoding());
							designationComponent.setValue(designation.getValue());
							component.addDesignation(designationComponent);
						}
					}
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

	private String getUserRef(ValueSet valueSet) {
		return valueSet.getUrl() != null ? valueSet.getUrl() : "inline value set";
	}

	@NotNull
	private BoolQueryBuilder getFhirConceptQuery(CodeSelectionCriteria codeSelectionCriteria, String termFilter) {
		QueryBuilder contentQuery = doGetFhirConceptQuery(codeSelectionCriteria);

		BoolQueryBuilder masterQuery = boolQuery().must(contentQuery);
		if (termFilter != null) {
			masterQuery.must(prefixQuery(FHIRConcept.Fields.DISPLAY, termFilter.toLowerCase()));
		}
		return masterQuery;
	}

	private QueryBuilder doGetFhirConceptQuery(CodeSelectionCriteria codeSelectionCriteria) {
		BoolQueryBuilder valueSetQuery = new BoolQueryBuilder();

		Map<FHIRCodeSystemVersion, Set<ConceptConstraint>> inclusionConstraints = codeSelectionCriteria.getInclusionConstraints();
		Set<CodeSelectionCriteria> nestedSelections = codeSelectionCriteria.getNestedSelections();
		Map<FHIRCodeSystemVersion, Set<ConceptConstraint>> exclusionConstraints = codeSelectionCriteria.getExclusionConstraints();

		// Inclusions
		for (Map.Entry<FHIRCodeSystemVersion, Set<ConceptConstraint>> versionInclusionConstraints : inclusionConstraints.entrySet()) {
			BoolQueryBuilder versionQuery = getInclusionQueryBuilder(versionInclusionConstraints, codeSelectionCriteria.getValueSetUserRef());
			valueSetQuery.should(versionQuery);// Must match at least one of these
		}

		// Nested value sets
		for (CodeSelectionCriteria nestedSelection : nestedSelections) {
			QueryBuilder nestedQuery = doGetFhirConceptQuery(nestedSelection);
			valueSetQuery.should(nestedQuery);
		}

		// Exclusions
		for (Map.Entry<FHIRCodeSystemVersion, Set<ConceptConstraint>> versionExclusionConstraints : exclusionConstraints.entrySet()) {
			BoolQueryBuilder versionQuery = getInclusionQueryBuilder(versionExclusionConstraints, codeSelectionCriteria.getValueSetUserRef());
			valueSetQuery.mustNot(versionQuery);
		}

		return valueSetQuery;
	}

	@NotNull
	private BoolQueryBuilder getInclusionQueryBuilder(Map.Entry<FHIRCodeSystemVersion, Set<ConceptConstraint>> versionInclusionConstraints, String valueSetUserRef) {
		BoolQueryBuilder versionQuery = boolQuery().must(termQuery(FHIRConcept.Fields.CODE_SYSTEM_VERSION, versionInclusionConstraints.getKey().getId()));

		BoolQueryBuilder disjunctionQueries = boolQuery();
		versionQuery.must(disjunctionQueries);// Concept must meet one of the conditions
		for (ConceptConstraint inclusion : versionInclusionConstraints.getValue()) {
			BoolQueryBuilder disjunctionQuery = boolQuery();
			disjunctionQueries.should(disjunctionQuery);// "disjunctionQueries" contains only "should" conditions, Elasticsearch forces at least one of them to match.
			addQueryCriteria(inclusion, disjunctionQuery, valueSetUserRef);
		}
		return versionQuery;
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
			codeSelectionCriteria.getInclusionConstraints().values().stream().flatMap(Collection::stream).forEach(include -> codes.addAll(include.getCode()));
			codeSelectionCriteria.getExclusionConstraints().values().stream().flatMap(Collection::stream).forEach(include -> codes.removeAll(include.getCode()));
			conceptQuery.conceptIds(codes);
			if (activeOnly) {
				conceptQuery.activeFilter(activeOnly);
			}
		}
		if (filter != null) {
			conceptQuery.descriptionCriteria(descriptionCriteria -> {
				descriptionCriteria.term(filter);
				if (!orEmpty(languageDialects).isEmpty()) {
					descriptionCriteria.searchLanguageCodes(languageDialects.stream().map(LanguageDialect::getLanguageCode).collect(Collectors.toSet()));
				}
			});
		}
		return conceptQuery;
	}

	@NotNull
	private CodeSelectionCriteria generateInclusionExclusionConstraints(ValueSet valueSet, CodeSystemVersionProvider codeSystemVersionProvider, boolean activeOnly) {

		CodeSelectionCriteria codeSelectionCriteria = new CodeSelectionCriteria(getUserRef(valueSet));

		ValueSet.ValueSetComposeComponent compose = valueSet.getCompose();
		for (ValueSet.ConceptSetComponent include : compose.getInclude()) {
			if (include.hasSystem()) {
				FHIRCodeSystemVersion codeSystemVersion = codeSystemVersionProvider.get(include.getSystem(), include.getVersion());
				collectConstraints(include, codeSystemVersion, codeSelectionCriteria.addInclusion(codeSystemVersion), activeOnly);
			} else if (include.hasValueSet()) {
				for (CanonicalType canonicalType : include.getValueSet()) {
					CanonicalUri canonicalUri = CanonicalUri.fromString(canonicalType.getValueAsString());
					ValueSet nestedValueSet = findOrThrow(canonicalUri.getSystem(), canonicalUri.getVersion()).getHapi();
					CodeSelectionCriteria nestedCriteria = generateInclusionExclusionConstraints(nestedValueSet, codeSystemVersionProvider, activeOnly);
					codeSelectionCriteria.addNested(nestedCriteria);
				}
			} else {
				throw exception("ValueSet clause has no system or nested value set", OperationOutcome.IssueType.INVARIANT, 400);
			}
		}
		for (ValueSet.ConceptSetComponent exclude : compose.getExclude()) {
			// Apply exclude-constraint to all resolved versions from include statements
			Set<FHIRCodeSystemVersion> codeSystemVersionsForExpansion = codeSelectionCriteria.gatherAllInclusionVersions();
			List<FHIRCodeSystemVersion> includeVersionsToExcludeFrom = codeSystemVersionsForExpansion.stream().filter(includeVersion ->
					includeVersion.getUrl().equals(exclude.getSystem()) && (exclude.getVersion() == null || exclude.getVersion().equals(includeVersion.getVersion()))
			).collect(Collectors.toList());

			for (FHIRCodeSystemVersion codeSystemVersion : includeVersionsToExcludeFrom) {
				collectConstraints(exclude, codeSystemVersion, codeSelectionCriteria.addExclusion(codeSystemVersion), activeOnly);
			}
		}
		return codeSelectionCriteria;
	}

	public Parameters validateCode(String id, UriType url, UriType context, ValueSet valueSet, String valueSetVersion, String code, UriType system, String systemVersion,
			String display, Coding coding, CodeableConcept codeableConcept, DateTimeType date, BooleanType abstractBool, String displayLanguage) {

		notSupported("context", context);
		notSupported("valueSetVersion", valueSetVersion);
		notSupported("date", date);
		notSupported("abstract", abstractBool);

		requireExactlyOneOf("code", code, "coding", coding, "codeableConcept", codeableConcept);
		mutuallyRequired("code", code, "system", system);
		mutuallyRequired("display", display, "code", code, "coding", coding);

		// Grab value set
		ValueSet hapiValueSet = findOrInferValueSet(id, FHIRHelper.toString(url), valueSet);
		if (hapiValueSet == null) {
			return null;
		}

		// Get set of codings - one of which needs to be valid
		Set<Coding> codings = new HashSet<>();
		if (code != null) {
			codings.add(new Coding(FHIRHelper.toString(system), code, display).setVersion(systemVersion));
		} else if (coding != null) {
			coding.setDisplay(display);
			codings.add(coding);
		} else {
			codings.addAll(codeableConcept.getCoding());
		}
		if (codings.isEmpty()) {
			throw exception("No codings provided to validate.", OperationOutcome.IssueType.INVALID, 400);
		}

		Set<CanonicalUri> codingSystemVersions = codings.stream()
				.filter(Coding::hasVersion).map(codingA -> CanonicalUri.of(codingA.getSystem(), codingA.getVersion())).collect(Collectors.toSet());

		CodeSystemVersionProvider codeSystemVersionProvider = new CodeSystemVersionProvider(codingSystemVersions, null, null, null, codeSystemService);
		// Collate set of inclusion and exclusion constraints for each code system version
		CodeSelectionCriteria codeSelectionCriteria = generateInclusionExclusionConstraints(hapiValueSet, codeSystemVersionProvider, false);

		Set<FHIRCodeSystemVersion> resolvedCodeSystemVersionsMatchingCodings = new HashSet<>();
		boolean systemMatch = false;
		for (Coding codingA : codings) {
			for (FHIRCodeSystemVersion version : codeSelectionCriteria.gatherAllInclusionVersions()) {
				if (codingA.getSystem().equals(version.getUrl().replace("xsct", "sct"))) {
					systemMatch = true;
					if (codingA.getVersion() == null || codingA.getVersion().equals(version.getVersion()) ||
							(FHIRHelper.isSnomedUri(codingA.getSystem()) && version.getVersion().contains(codingA.getVersion()))) {
						resolvedCodeSystemVersionsMatchingCodings.add(version);
					}
				}
			}
		}

		Parameters response = new Parameters();
		if (codings.size() == 1) {
			// Add response details about the coding, if there is only one
			Coding codingA = codings.iterator().next();
			response.addParameter("code", codingA.getCode());
			response.addParameter("system", codingA.getSystem());
		}

		if (resolvedCodeSystemVersionsMatchingCodings.isEmpty()) {
			response.addParameter("result", false);
			if (systemMatch) {
				if (codings.size() == 1) {
					Coding codingA = codings.iterator().next();
					response.addParameter("message", format("The system '%s' is included in this ValueSet but the version '%s' is not.", codingA.getSystem(), codingA.getVersion()));
				} else {
					response.addParameter("message", "One or more codes in the CodableConcept are within a system included by this ValueSet but none of the versions match.");
				}
			} else {
				if (codings.size() == 1) {
					Coding codingA = codings.iterator().next();
					response.addParameter("message", format("The system '%s' is not included in this ValueSet.", codingA.getSystem()));
				} else {
					response.addParameter("message", "None of the codes in the CodableConcept are within a system included by this ValueSet.");
				}
			}
			return response;
		}
		// Add version actually used in the response
		if (codings.size() == 1) {
			response.addParameter("version", resolvedCodeSystemVersionsMatchingCodings.iterator().next().getVersion());
		}

		List<LanguageDialect> languageDialects = ControllerHelper.parseAcceptLanguageHeader(displayLanguage);
		for (Coding codingA : codings) {
			FHIRConcept concept = findInValueSet(codingA, resolvedCodeSystemVersionsMatchingCodings, codeSelectionCriteria, languageDialects);
			if (concept != null) {
				String codingADisplay = codingA.getDisplay();
				if (codingADisplay == null) {
					response.addParameter("result", true);
					return response;
				} else {
					FHIRDesignation termMatch = null;
					for (FHIRDesignation designation : concept.getDesignations()) {
						if (codingADisplay.equalsIgnoreCase(designation.getValue())) {
							termMatch = designation;
							if (designation.getLanguage() == null || languageDialects.isEmpty() || languageDialects.stream()
										.anyMatch(languageDialect -> designation.getLanguage().equals(languageDialect.getLanguageCode()))) {
								response.addParameter("result", true);
								response.addParameter("message", format("The code '%s' was found in the ValueSet and the display matched one of the designations.",
										codingA.getCode()));
								return response;
							}
						}
					}
					if (termMatch != null) {
						response.addParameter("result", false);
						response.addParameter("message", format("The code '%s' was found in the ValueSet and the display matched the designation with term '%s', " +
								"however the language of the designation '%s' did not match any of the languages in the requested display language '%s'.",
								codingA.getCode(), termMatch.getValue(), termMatch.getLanguage(), displayLanguage));
						return response;
					} else {
						response.addParameter("result", false);
						response.addParameter("message", format("The code '%s' was found in the ValueSet, however the display '%s' did not match any designations.",
								codingA.getCode(), codingA.getDisplay()));
						return response;
					}
				}
			}
		}

		response.addParameter("result", false);
		if (codings.size() == 1) {
			Coding codingA = codings.iterator().next();
			String codingAVersion = codingA.getVersion();
			response.addParameter("message", format("The code '%s' from CodeSystem '%s'%s was not found in this ValueSet.", codingA.getCode(), codingA.getSystem(),
					codingAVersion != null ? format(" version '%s'", codingAVersion) : ""));
		} else {
			response.addParameter("message", "None of the codes in the CodableConcept were found in this ValueSet.");
		}
		return response;
	}

	@Nullable
	private ValueSet findOrInferValueSet(String id, String url, ValueSet hapiValueSet) {
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
			if (coding.getSystem().equals(codeSystemVersionForExpansion.getUrl()) &&
					(coding.getVersion() == null || coding.getVersion().equals(codeSystemVersionForExpansion.getVersion())) ||
					(FHIRHelper.isSnomedUri(coding.getSystem()) && codeSystemVersionForExpansion.getVersion().contains(coding.getVersion()))) {

				if (codeSystemVersionForExpansion.isSnomed()) {
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
			BoolQueryBuilder fhirConceptQuery = getFhirConceptQuery(codeSelectionCriteria, null);
			// Add criteria to select just this code
			fhirConceptQuery.must(termQuery(FHIRConcept.Fields.CODE, coding.getCode()));
			List<FHIRConcept> concepts = conceptService.findConcepts(fhirConceptQuery, PAGE_OF_ONE).getContent();
			if (!concepts.isEmpty()) {
				return concepts.get(0);
			}
		}

		return null;
	}

	private String inclusionExclusionClausesToEcl(CodeSelectionCriteria codeSelectionCriteria) {
		StringBuilder ecl = new StringBuilder();
		for (ConceptConstraint inclusion : codeSelectionCriteria.getInclusionConstraints().values().iterator().next()) {
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
			for (ConceptConstraint exclusion : codeSelectionCriteria.getExclusionConstraints().values().iterator().next()) {
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

	private void addQueryCriteria(ConceptConstraint inclusion, BoolQueryBuilder versionQuery, String valueSetUserRef) {
		if (inclusion.getCode() != null) {
			versionQuery.must(termsQuery(FHIRConcept.Fields.CODE, inclusion.getCode()));
		} else if (inclusion.getParent() != null) {
			versionQuery.must(termsQuery(FHIRConcept.Fields.PARENTS, inclusion.getParent()));
		} else if (inclusion.getAncestor() != null) {
			versionQuery.must(termsQuery(FHIRConcept.Fields.ANCESTORS, inclusion.getAncestor()));
		} else {
			String message = "Unrecognised constraints for ValueSet: " + valueSetUserRef;
			logger.error(message);
			throw exception(message, OperationOutcome.IssueType.EXCEPTION, 500);
		}
	}

	private void collectConstraints(ValueSet.ConceptSetComponent include, FHIRCodeSystemVersion codeSystemVersion, Set<ConceptConstraint> inclusionConstraints, boolean activeOnly) {
		if (!include.getConcept().isEmpty()) {
			List<String> codes = include.getConcept().stream().map(ValueSet.ConceptReferenceComponent::getCode).collect(Collectors.toList());
			inclusionConstraints.add(new ConceptConstraint(codes));
		}
		if (!include.getFilter().isEmpty()) {
			for (ValueSet.ConceptSetFilterComponent filter : include.getFilter()) {
				String property = filter.getProperty();
				ValueSet.FilterOperator op = filter.getOp();
				String value = filter.getValue();
				if (codeSystemVersion.isSnomed()) {
					// SNOMED CT filters:
					// concept, is-a, [conceptId]
					// concept, in, [refset]
					// constraint, =, [ECL]
					// expression, =, Refsets - special case to deal with '?fhir_vs=refset'. Matches the Ontoserver compose for these, not part of the spec but at least consistent.
					// expressions, =, true/false
					if ("concept".equals(property)) {
						if (op == ValueSet.FilterOperator.ISA) {
							if (Strings.hasLength(value)) {
								throw exception("Value missing for SNOMED CT ValueSet concept 'is-a' filter", OperationOutcome.IssueType.INVALID, 400);
							}
							inclusionConstraints.add(new ConceptConstraint().setEcl("<< " + value));
						} else if (op == ValueSet.FilterOperator.IN) {
							if (Strings.hasLength(value)) {
								throw exception("Value missing for SNOMED CT ValueSet concept 'in' filter.", OperationOutcome.IssueType.INVALID, 400);
							}
							// Concept must be in the specified refset
							String ecl = "^ " + value;
							if (activeOnly) {
								ecl += " {{ C active=true }}";
							}
							inclusionConstraints.add(new ConceptConstraint().setEcl(ecl));
						} else {
							throw exception(format("Unexpected operation '%s' for SNOMED CT ValueSet 'concept' filter.", op.toCode()), OperationOutcome.IssueType.INVALID, 400);
						}
					} else if ("constraint".equals(property)) {
						if (op == ValueSet.FilterOperator.EQUAL) {
							if (Strings.isEmpty(value)) {
								throw exception("Value missing for SNOMED CT ValueSet 'constraint' filter.", OperationOutcome.IssueType.INVALID, 400);
							}
							inclusionConstraints.add(new ConceptConstraint().setEcl(value));
						} else {
							throw exception(format("Unexpected operation '%s' for SNOMED CT ValueSet 'constraint' filter.", op.toCode()), OperationOutcome.IssueType.INVALID, 400);
						}
					} else if ("expression".equals(property)) {
						if (op == ValueSet.FilterOperator.EQUAL) {
							if (REFSETS_WITH_MEMBERS.equals(value)) {
								// Concept must represent a reference set which has members in this code system version.
								// Lookup uses a cache.
								inclusionConstraints.add(new ConceptConstraint(findAllRefsetsWithActiveMembers(codeSystemVersion)));
							} else if (value != null) {
								inclusionConstraints.add(new ConceptConstraint().setEcl(value));
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
							inclusionConstraints.add(new ConceptConstraint().setEcl("<! " + value));
						} else {
							throw exception(format("Unexpected operation '%s' for SNOMED CT ValueSet 'parent' filter.", op.toCode()), OperationOutcome.IssueType.INVALID, 400);
						}
					} else {
						throw exception(format("Unexpected property '%s' for SNOMED CT ValueSet filter.", property), OperationOutcome.IssueType.INVALID, 400);
					}
				} else if (codeSystemVersion.getUrl().equals("http://loinc.org")) {
					// LOINC filters:
					// parent/ancestor, =/in, [partCode]
					// [property], =/regex, [value] - not supported
					// copyright, =, LOINC/3rdParty - not supported

					if (Strings.hasLength(value)) {
						throw exception("Value missing for LOINC ValueSet filter", OperationOutcome.IssueType.INVALID, 400);
					}
					Set<String> values = op == ValueSet.FilterOperator.IN ? new HashSet<>(Arrays.asList(value.split(","))) : Collections.singleton(value);
					if ("parent".equals(property)) {
						inclusionConstraints.add(new ConceptConstraint().setParent(values));
					} else if ("ancestor".equals(property)) {
						inclusionConstraints.add(new ConceptConstraint().setAncestor(values));
					} else {
						throw exception(format("This server does not support ValueSet filter using LOINC property '%s'. " +
								"Only parent and ancestor filters are supported for LOINC.", property), OperationOutcome.IssueType.NOTSUPPORTED, 400);
					}
				} else if (codeSystemVersion.getUrl().startsWith("http://hl7.org/fhir/sid/icd-10")) {
					// Spec says there are no filters for ICD-9 and 10.
					throw exception("This server does not expect any ValueSet property filters for ICD-10.", OperationOutcome.IssueType.NOTSUPPORTED, 400);
				} else {
					// Generic code system
					if ("concept".equals(property) && op == ValueSet.FilterOperator.ISA) {
						Set<String> singleton = Collections.singleton(value);
						inclusionConstraints.add(new ConceptConstraint(singleton));
						inclusionConstraints.add(new ConceptConstraint().setAncestor(singleton));
					} else if ("concept".equals(property) && op == ValueSet.FilterOperator.DESCENDENTOF) {
						Set<String> singleton = Collections.singleton(value);
						inclusionConstraints.add(new ConceptConstraint().setAncestor(singleton));
					} else {
						throw exception("This server does not support this ValueSet property filter on generic code systems. " +
								"Supported filters for generic code systems are: (concept, is-a) and (concept, descendant-of).", OperationOutcome.IssueType.NOTSUPPORTED, 400);
					}
				}
			}
		}
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

}
