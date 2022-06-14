package org.snomed.snowstorm.fhir.services;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.elasticsearch.common.Strings;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.hl7.fhir.r4.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.core.data.domain.ConceptMini;
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
import org.snomed.snowstorm.rest.ControllerHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.core.SearchAfterPageRequest;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.kaicode.elasticvc.api.ComponentService.LARGE_PAGE;
import static java.lang.Boolean.TRUE;
import static java.lang.String.format;
import static org.elasticsearch.index.query.QueryBuilders.*;
import static org.snomed.snowstorm.core.data.services.ReferenceSetMemberService.AGGREGATION_MEMBER_COUNTS_BY_REFERENCE_SET;
import static org.snomed.snowstorm.core.util.CollectionUtils.orEmpty;
import static org.snomed.snowstorm.fhir.services.FHIRHelper.*;

@Service
public class FHIRValueSetService {

	// Constant to help with "?fhir_vs=refset"
	public static final String REFSETS_WITH_MEMBERS = "Refsets";

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

	private final Map<String, Set<String>> codeSystemVersionToRefsetsWithMembersCache = new HashMap<>();

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public Optional<FHIRValueSet> findByUrl(String url) {
		return valueSetRepository.findByUrl(url);
	}

	public void saveAllValueSetsOfCodeSystemVersion(List<ValueSet> valueSets) {
		for (ValueSet valueSet : orEmpty(valueSets)) {
			try {
				logger.info("Saving ValueSet {}", valueSet.getIdElement());
				createValueset(valueSet);
			} catch (FHIROperationException e) {
				logger.error("Failed to store value set {}", valueSet.getIdElement(), e);
			}
		}
	}

	public FHIRValueSet createValueset(ValueSet valueSet) throws FHIROperationException {
		// Expand to validate
		ValueSet.ValueSetExpansionComponent originalExpansion = valueSet.getExpansion();
		expand(new ValueSetExpansionParameters(valueSet, true), null);
		valueSet.setExpansion(originalExpansion);

		// Save will replace any existing value set with the same id.
		return valueSetRepository.save(new FHIRValueSet(valueSet));
	}

	public ValueSet expand(final ValueSetExpansionParameters params, String acceptLanguageHeader) throws FHIROperationException {
		// Lots of not supported parameters
		notSupported("valueSetVersion", params.getValueSetVersion());
		notSupported("context", params.getContext());
		notSupported("contextDirection", params.getContextDirection());
		notSupported("date", params.getDate());
		notSupported("includeDesignations", params.getIncludeDesignations());
		notSupported("designation", params.getDesignations());
		notSupported("excludeNested", params.getExcludeNested());
		notSupported("excludeNotForUI", params.getExcludeNotForUI());
		notSupported("excludePostCoordinated", params.getExcludePostCoordinated());
		notSupported("version", params.getVersion());// Not part of the FHIR API spec but requested under MAINT-1363

		String id = params.getId();
		String url = params.getUrl();
		ValueSet hapiValueSet = params.getValueSet();
		String filter = params.getFilter();
		boolean activeOnly = TRUE == params.getActiveOnly();

		mutuallyExclusive("id", id, "url", url);
		mutuallyExclusive("id", id, "valueSet", hapiValueSet);
		mutuallyExclusive("url", url, "valueSet", hapiValueSet);

		PageRequest pageRequest = params.getPageRequest();

		if (id != null) {
			Optional<FHIRValueSet> valueSetOptional = valueSetRepository.findById(id);
			if (valueSetOptional.isEmpty()) {
				return null;
			}
			FHIRValueSet valueSet = valueSetOptional.get();
			idUrlCrosscheck(id, url, valueSet);

			hapiValueSet = valueSet.getHapi();
		} else if (FHIRHelper.isSnomedUri(url) && url.contains("?fhir_vs")) {
			// Create implicit value set
			hapiValueSet = createImplicitValueSet(url);
		} else if (hapiValueSet == null) {
			hapiValueSet = findByUrl(url).map(FHIRValueSet::getHapi).orElse(null);
		}

		if (hapiValueSet == null) {
			return null;
		}

		if (!hapiValueSet.hasCompose()) {
			return hapiValueSet;
		}

		ValueSet.ValueSetComposeComponent compose = hapiValueSet.getCompose();

		// Resolve the set of code system versions that will actually be used. Includes some input parameter validation.
		Map<CanonicalUri, FHIRCodeSystemVersion> codeSystemVersionsForExpansion = resolveCodeSystemVersionsForExpansion(compose,
				params.getSystemVersion(), params.getCheckSystemVersion(), params.getForceSystemVersion(), params.getExcludeSystem());

		// Restrict other code systems being used with SNOMED CT, to simplify pagination.
		// Do not restrict versioned and unversioned SNOMED URIs being mixed.
		if (codeSystemVersionsForExpansion.size() > 1
				// Some snomed (versioned or unversioned)
				&& codeSystemVersionsForExpansion.keySet().stream().anyMatch(CanonicalUri::isSnomed)
				// Some not snomed
				&& codeSystemVersionsForExpansion.keySet().stream().anyMatch(Predicate.not(CanonicalUri::isSnomed))) {

			throw exception("This server does not allow SNOMED CT content to be mixed with other code systems.", OperationOutcome.IssueType.NOTSUPPORTED, 400);
		}

		// Collate set of inclusion and exclusion constraints for each code system version
		Map<FHIRCodeSystemVersion, Pair<Set<ConceptConstraint>, Set<ConceptConstraint>>> inclusionExclusionConstraints = new HashMap<>();
		for (ValueSet.ConceptSetComponent include : compose.getInclude()) {
			FHIRCodeSystemVersion codeSystemVersion = codeSystemVersionsForExpansion.get(CanonicalUri.of(include.getSystem(), include.getVersion()));
			Set<ConceptConstraint> inclusionConstraints = inclusionExclusionConstraints.computeIfAbsent(codeSystemVersion, key -> Pair.of(new HashSet<>(), new HashSet<>())).getFirst();
			collectConstraints(include, codeSystemVersion, inclusionConstraints, activeOnly);
		}
		for (ValueSet.ConceptSetComponent exclude : compose.getExclude()) {
			// Apply exclude-constraint to all resolved versions from include statements
			List<CanonicalUri> includeVersionsToExcludeFrom = codeSystemVersionsForExpansion.keySet().stream().filter(includeVersion ->
					includeVersion.getSystem().equals(exclude.getSystem()) && (exclude.getVersion() == null || exclude.getVersion().equals(includeVersion.getVersion()))
			).collect(Collectors.toList());
			for (CanonicalUri version : includeVersionsToExcludeFrom) {
				FHIRCodeSystemVersion codeSystemVersion = codeSystemVersionsForExpansion.get(version);
				Set<ConceptConstraint> exclusionConstraints = inclusionExclusionConstraints.get(codeSystemVersion).getSecond();
				collectConstraints(exclude, codeSystemVersion, exclusionConstraints, activeOnly);
			}
		}

		if (inclusionExclusionConstraints.isEmpty()) {
			return hapiValueSet;
		}

		Page<FHIRConcept> conceptsPage;
		String copyright = null;
		if (inclusionExclusionConstraints.keySet().iterator().next().isSnomed()) {// If one is SNOMED then they will all be
			// SNOMED CT Expansion
			copyright = SNOMED_VALUESET_COPYRIGHT;

			if (inclusionExclusionConstraints.size() > 1) {
				throw exception(format("This server does not support ValueSet $expand operation using multiple SNOMED CT versions. Versions requested: %s",
								inclusionExclusionConstraints.keySet()), OperationOutcome.IssueType.NOTSUPPORTED, 400);
			}


			FHIRCodeSystemVersion codeSystemVersion = inclusionExclusionConstraints.keySet().iterator().next();
			List<LanguageDialect> languageDialects = ControllerHelper.parseAcceptLanguageHeader(acceptLanguageHeader);

			// Constraints:
			// - Elasticsearch prevents us from requesting results beyond the first 10K
			// Strategy:
			// - Load concept ids until we reach the requested page
			// - Then load the concepts for that page
			int offsetRequested = (int) pageRequest.getOffset();
			int limitRequested = (int) (pageRequest.getOffset() + pageRequest.getPageSize());


			QueryService.ConceptQueryBuilder conceptQuery = snomedQueryService.createQueryBuilder(false);
			Pair<Set<ConceptConstraint>, Set<ConceptConstraint>> inclusionExclusionClauses = inclusionExclusionConstraints.get(codeSystemVersion);
			if (Stream.concat(inclusionExclusionClauses.getFirst().stream(), inclusionExclusionClauses.getSecond().stream()).anyMatch(ConceptConstraint::hasEcl)) {
				// ECL search
				String ecl = inclusionExclusionClausesToEcl(inclusionExclusionClauses);
				conceptQuery.ecl(ecl);
			} else {
				// Just a set of concept codes
				Set<String> codes = new HashSet<>();
				inclusionExclusionClauses.getFirst().forEach(include -> codes.addAll(include.getCode()));
				inclusionExclusionClauses.getSecond().forEach(exclude -> codes.removeAll(exclude.getCode()));
				conceptQuery.conceptIds(codes);
				if (activeOnly) {
					conceptQuery.activeFilter(activeOnly);
				}
			}
			if (filter != null) {
				conceptQuery.descriptionCriteria(descriptionCriteria -> descriptionCriteria.term(filter));
			}

			int totalResults = 0;
			List<Long> conceptsToLoad;
			if (limitRequested > LARGE_PAGE.getPageSize()) {
				// Have to use search-after feature to paginate to the page requested because of Elasticsearch 10k limit.
				SearchAfterPage<Long> previousPage = null;
				List<Long> allConceptIds = new LongArrayList();
				boolean loadedAll = false;
				while (allConceptIds.size() < limitRequested || loadedAll) {
					PageRequest largePageRequest;
					if (previousPage == null) {
						largePageRequest = LARGE_PAGE;
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

			List<FHIRConcept> conceptsOnRequestedPage = conceptsToLoad.isEmpty() ? Collections.emptyList() :
					snomedConceptService.findConceptMinis(codeSystemVersion.getSnomedBranch(), conceptsToLoad, languageDialects)
							.getResultsMap().values().stream().map(snomedConceptMini -> new FHIRConcept(snomedConceptMini, codeSystemVersion)).collect(Collectors.toList());

			conceptsPage = new PageImpl<>(conceptsOnRequestedPage, pageRequest, totalResults);
		} else {
			// FHIR Concept Expansion (non-SNOMED)
			pageRequest = PageRequest.of(pageRequest.getPageNumber(), pageRequest.getPageSize(), Sort.Direction.ASC, "code");
			BoolQueryBuilder fhirConceptQuery = boolQuery();
			for (FHIRCodeSystemVersion codeSystemVersion : inclusionExclusionConstraints.keySet()) {
				BoolQueryBuilder versionQuery = boolQuery()
						.must(termQuery(FHIRConcept.Fields.CODE_SYSTEM_VERSION, codeSystemVersion.getId()));

				Pair<Set<ConceptConstraint>, Set<ConceptConstraint>> inclusionExclusionClauses = inclusionExclusionConstraints.get(codeSystemVersion);
				for (ConceptConstraint inclusion : inclusionExclusionClauses.getFirst()) {
					addQueryCriteria(inclusion, versionQuery, hapiValueSet);
				}
				Set<ConceptConstraint> exclusionClauses = inclusionExclusionClauses.getSecond();
				if (!exclusionClauses.isEmpty()) {
					BoolQueryBuilder mustNotClauses = boolQuery();
					versionQuery.mustNot(mustNotClauses);
					for (ConceptConstraint exclusionClause : exclusionClauses) {
						addQueryCriteria(exclusionClause, mustNotClauses, hapiValueSet);
					}
				}
			}
			conceptsPage = conceptService.findConcepts(fhirConceptQuery, pageRequest);
		}

		Map<String, String> idAndVersionToUrl = codeSystemVersionsForExpansion.values().stream()
				.collect(Collectors.toMap(FHIRCodeSystemVersion::getId, FHIRCodeSystemVersion::getUrl));
		ValueSet.ValueSetExpansionComponent expansion = new ValueSet.ValueSetExpansionComponent();
		expansion.setId(UUID.randomUUID().toString());
		expansion.setTimestamp(new Date());
		codeSystemVersionsForExpansion.values().forEach(codeSystemVersion ->
				expansion.addParameter(new ValueSet.ValueSetExpansionParameterComponent(new StringType("version"))
						.setValue(new CanonicalType(codeSystemVersion.getCanonical()))));
		expansion.setContains(conceptsPage.stream().map(concept ->
			new ValueSet.ValueSetExpansionContainsComponent()
					.setSystem(idAndVersionToUrl.get(concept.getCodeSystemVersion()))
					.setCode(concept.getCode())
					.setInactiveElement(concept.isActive() ? null : new BooleanType(false))
					.setDisplay(concept.getDisplay()))
				.collect(Collectors.toList()));
		expansion.setOffset(conceptsPage.getNumber() * conceptsPage.getSize());
		expansion.setTotal((int) conceptsPage.getTotalElements());
		hapiValueSet.setExpansion(expansion);

		if (copyright != null) {
			hapiValueSet.setCopyright(copyright);
		}

		if (!TRUE.equals(params.getIncludeDefinition())) {
			hapiValueSet.setCompose(null);
		}

		return hapiValueSet;

//		boolean includeDesignations = fhirHelper.setLanguageOptions(designations, params.getDesignations(),
//				params.getDisplayLanguage(), params.getIncludeDesignationsType(), acceptLanguageHeader);
	}

	private String inclusionExclusionClausesToEcl(Pair<Set<ConceptConstraint>, Set<ConceptConstraint>> inclusionExclusionClauses) {
		StringBuilder ecl = new StringBuilder();
		for (ConceptConstraint inclusion : inclusionExclusionClauses.getFirst()) {
			if (ecl.length() > 0) {
				ecl.append(" OR ");
			}
			ecl.append("( ").append(toEcl(inclusion)).append(" )");
		}

		if (ecl.length() == 0) {
			// This may be impossible because ValueSet.compose.include cardinality is 1..*
			ecl.append("*");
		}

		Set<ConceptConstraint> exclusions = inclusionExclusionClauses.getSecond();
		if (!exclusions.isEmpty()) {
			// Existing ECL must be made into sub expression, because disjunction and exclusion expressions can not be mixed.
			ecl = new StringBuilder().append("( ").append(ecl).append(" )");
		}
		for (ConceptConstraint exclusion : exclusions) {
			ecl.append(" MINUS ( ").append(exclusion.getEcl()).append(" )");
		}

		return ecl.toString();
	}

	private String toEcl(ConceptConstraint inclusion) {
		if (inclusion.hasEcl()) {
			return inclusion.getEcl();
		}
		return String.join(" OR ", inclusion.getCode());
	}

	private void addQueryCriteria(ConceptConstraint inclusion, BoolQueryBuilder versionQuery, ValueSet hapiValueSet) {
		if (inclusion.getCode() != null) {
			versionQuery.must(termsQuery(FHIRConcept.Fields.CODE, inclusion.getCode()));
		} else if (inclusion.getParent() != null) {
			versionQuery.must(termsQuery(FHIRConcept.Fields.PARENTS, inclusion.getParent()));
		} else if (inclusion.getAncestor() != null) {
			versionQuery.must(termsQuery(FHIRConcept.Fields.ANCESTORS, inclusion.getAncestor()));
		} else {
			String message = "Failed to construct query for ValueSet expansion of: " + hapiValueSet;
			logger.error(message);
			throw exception(message, OperationOutcome.IssueType.EXCEPTION, 500);
		}
	}

	private void collectConstraints(ValueSet.ConceptSetComponent include, FHIRCodeSystemVersion codeSystemVersion, Set<ConceptConstraint> inclusionConstraints, boolean activeOnly) {
		if (!include.getConcept().isEmpty()) {
			List<String> codes = include.getConcept().stream().map(ValueSet.ConceptReferenceComponent::getCode).collect(Collectors.toList());
			inclusionConstraints.add(new ConceptConstraint(codes));

		} else if (!include.getFilter().isEmpty()) {
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
					} else {
						throw exception(format("Unexpected property '%s' for SNOMED CT ValueSet filter.", property), OperationOutcome.IssueType.INVALID, 400);
					}
				} else if (codeSystemVersion.getUrl().equals("http://loinc.org")) {
					// LOINC filters:
					// parent/ancestor, =/in, [partCode]
					// [property], =/regex, [value] - not supported
					// copyright, =, LOINC/3rdParty - not supported

					Set<String> values = op == ValueSet.FilterOperator.IN ? new HashSet<>(Arrays.asList(value.split(","))) : Collections.singleton(value);
					if ("parent".equals(property)) {
						inclusionConstraints.add(new ConceptConstraint().setParent(values));
					} else if ("ancestor".equals(property)) {
						inclusionConstraints.add(new ConceptConstraint().setAncestor(values));
					}
					throw exception(format("This server does not support ValueSet filter using LOINC property '%s'. " +
									"Only parent and ancestor filters are supported for LOINC.", property), OperationOutcome.IssueType.NOTSUPPORTED,	400);

				} else if (codeSystemVersion.getUrl().startsWith("http://hl7.org/fhir/sid/icd-10")) {
					// Spec says there are no filters for ICD-9 and 10.
					throw exception("This server does not expect any ValueSet property filters for ICD-10.", OperationOutcome.IssueType.NOTSUPPORTED, 400);
				} else {
					throw exception("This server does not support ValueSet property filters on generic code systems.", OperationOutcome.IssueType.NOTSUPPORTED, 400);
				}
			}
		}
	}

	private ValueSet createImplicitValueSet(String url) throws FHIROperationException {
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
		return valueSet.getHapi();
	}

	/**
	 * See https://www.hl7.org/fhir/snomedct.html#implicit
	 */
	private String determineEcl(String url) throws FHIROperationException {
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
			throw new FHIROperationException(OperationOutcome.IssueType.VALUE, "url is expected to include parameter with value: 'fhir_vs=ecl/'");
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

	/**
	 * Returns map of CanonicalUris and FHIRCodeSystemVersions to be used in expansion.
	 * The CanonicalUris in the returned map represent the systems, and optionally versions, that are stated in the ValueSet.
	 * The FHIRCodeSystemVersions in the returned map are the versions that should actually be used, they reflect all the current constraints.
	 */
	private Map<CanonicalUri, FHIRCodeSystemVersion> resolveCodeSystemVersionsForExpansion(ValueSet.ValueSetComposeComponent compose,
			CanonicalUri systemVersion, CanonicalUri checkSystemVersion, CanonicalUri forceSystemVersion, CanonicalUri excludeSystem) throws FHIROperationException {

		Map<CanonicalUri, FHIRCodeSystemVersion> composeSystemVersions = new HashMap<>();
		// include
		resolveCodeSystemVersionForIncludeExclude(compose.getInclude(), systemVersion, checkSystemVersion, forceSystemVersion, excludeSystem, composeSystemVersions);
		// exclude
		resolveCodeSystemVersionForIncludeExclude(compose.getExclude(), systemVersion, checkSystemVersion, forceSystemVersion, excludeSystem, composeSystemVersions);
		return composeSystemVersions;
	}

	private void resolveCodeSystemVersionForIncludeExclude(List<ValueSet.ConceptSetComponent> includeExclude, CanonicalUri systemVersion, CanonicalUri checkSystemVersion,
			CanonicalUri forceSystemVersion, CanonicalUri excludeSystem, Map<CanonicalUri, FHIRCodeSystemVersion> composeSystemVersions) throws FHIROperationException {

		for (ValueSet.ConceptSetComponent component : includeExclude) {
			if (component.hasValueSet()) {
				throw exception("valueSet within a ValueSet compose include/exclude is not supported.", OperationOutcome.IssueType.NOTSUPPORTED, 400);
			}
			String componentSystem = component.getSystem();
			if (componentSystem == null) {
				throw exception("ValueSet compose include/exclude must include a system.", OperationOutcome.IssueType.INVARIANT, 400);
			}

			String componentVersion = component.getVersion();

			CanonicalUri canonicalUri = CanonicalUri.of(componentSystem, componentVersion);
			if (!composeSystemVersions.containsKey(canonicalUri)) {
				// Apply force system version if systems match
				if (forceSystemVersion != null && componentSystem.equals(forceSystemVersion.getSystem()) && forceSystemVersion.getVersion() != null) {
					componentVersion = forceSystemVersion.getVersion();

				// Apply system version if no version set and systems match
				} else if (componentVersion == null && systemVersion != null && componentSystem.equals(systemVersion.getSystem()) && systemVersion.getVersion() != null) {
					componentVersion = systemVersion.getVersion();
				}

				FHIRCodeSystemVersion codeSystemVersion = codeSystemService.findCodeSystemVersionOrThrow(
						FHIRHelper.getCodeSystemVersionParams(null, componentSystem, componentVersion, null));

				// Apply exclude-system param
				if (excludeSystem != null && componentSystem.equals(excludeSystem.getSystem())) {
					String excludeSystemVersion = excludeSystem.getVersion();
					if (excludeSystemVersion == null || excludeSystemVersion.equals(codeSystemVersion.getVersion())) {
						continue;
					}
				}

				// Validate check-system-version param
				if (checkSystemVersion != null && checkSystemVersion.getVersion() != null && checkSystemVersion.getSystem().equals(componentSystem)) {
					if (!codeSystemVersion.getVersion().equals(checkSystemVersion.getVersion())) {
						throw exception(format("ValueSet expansion includes CodeSystem '%s' version '%s', which does not match input parameter " +
								"check-system-version '%s' '%s'.", codeSystemVersion.getUrl(), codeSystemVersion.getVersion(),
								checkSystemVersion.getSystem(), checkSystemVersion.getVersion()), OperationOutcome.IssueType.INVALID, 400);
					}
				}

				// This system version will be used whenever an include/exclude has this componentSystem-componentVersion combination
				composeSystemVersions.put(canonicalUri, codeSystemVersion);
			}
		}
	}

	private void idUrlCrosscheck(String id, String url, FHIRValueSet valueSet) {
		if (url != null && !url.equals(valueSet.getUrl())) {
			throw exception(format("The requested ValueSet URL '%s' does not match the URL '%s' of the ValueSet found using identifier '%s'.",
					url, valueSet.getUrl(), id), OperationOutcome.IssueType.INVALID, 400);
		}
	}

}
