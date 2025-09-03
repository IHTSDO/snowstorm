package org.snomed.snowstorm.fhir.services;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.hl7.fhir.r4.model.*;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.core.data.domain.ConceptMini;
import org.snomed.snowstorm.core.data.domain.Concepts;
import org.snomed.snowstorm.core.data.services.DescriptionService;
import org.snomed.snowstorm.core.data.services.QueryService;
import org.snomed.snowstorm.core.pojo.LanguageDialect;
import org.snomed.snowstorm.fhir.config.FHIRConstants;
import org.snomed.snowstorm.fhir.domain.*;
import org.snomed.snowstorm.fhir.pojo.CanonicalUri;
import org.snomed.snowstorm.fhir.repositories.FHIRValueSetRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.Queries;
import org.springframework.stereotype.Service;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

import static co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders.bool;
import static io.kaicode.elasticvc.helper.QueryHelper.*;
import static io.kaicode.elasticvc.helper.QueryHelper.termsQuery;
import static java.lang.String.format;
import static org.snomed.snowstorm.core.util.CollectionUtils.orEmpty;
import static org.snomed.snowstorm.fhir.services.FHIRHelper.exception;
import static org.snomed.snowstorm.fhir.services.FHIRHelper.mutuallyExclusive;
import static org.snomed.snowstorm.fhir.services.FHIRValueSetService.MISSING_VALUESET;

@Service
public class FHIRValueSetFinderService implements FHIRConstants {

	private static final PageRequest PAGE_OF_ONE = PageRequest.of(0, 1);

	private static final List<Long> defaultSearchDescTypeIds = List.of(Concepts.FSN_L, Concepts.SYNONYM_L);

	private final Logger logger = LoggerFactory.getLogger(getClass());

	@Autowired
	private FHIRValueSetRepository valueSetRepository;

	@Autowired
	private QueryService snomedQueryService;

	@Autowired
	private FHIRConceptService conceptService;

	@Autowired
	private FHIRValueSetConstraintsService constraintsService;

	public static final String REFSETS_WITH_MEMBERS = "Refsets";

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
			Extension extension = new Extension(MISSING_VALUESET,new CanonicalType(CanonicalUri.of(url,version).toString()));
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

	public void idUrlCrosscheck(String id, String url, FHIRValueSet valueSet) {
		if (url != null && !url.equals(valueSet.getUrl())) {
			throw exception(format("The requested ValueSet URL '%s' does not match the URL '%s' of the ValueSet found using identifier '%s'.",
					url, valueSet.getUrl(), id), OperationOutcome.IssueType.INVALID, 400);
		}
	}

	public ValueSet findOrInferValueSet(String id, String url, ValueSet hapiValueSet, String version) {
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
		} else if (FHIRHelper.isSnomedUri(url) && url.contains(FHIR_VS)) {
			// Create snomed implicit value set
			hapiValueSet = createSnomedImplicitValueSet(url);
		} else if (url != null && url.endsWith(FHIR_VS)) {
			// Create implicit value set
			FHIRValueSetCriteria includeCriteria = new FHIRValueSetCriteria();
			includeCriteria.setSystem(url.replace(FHIR_VS, ""));
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

	public Optional<FHIRValueSet> findLatestByUrl(String url) {
		return find(url, null);
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
		if (url.endsWith(FHIR_VS)) {
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


	public FHIRConcept findInValueSet(Coding coding,
	                                  Set<FHIRCodeSystemVersion> expansionVersions,
	                                  CodeSelectionCriteria criteria,
	                                  List<LanguageDialect> dialects) {

		VersionPartition versions = partitionVersionsBySystem(coding, expansionVersions);

		FHIRConcept snomedResult = findInSnomed(coding, versions.snomed(), criteria, dialects);
		if (snomedResult != null) {
			return snomedResult;
		}

		return findInGeneric(coding, versions.generic(), criteria);
	}

	private VersionPartition partitionVersionsBySystem(Coding coding,
	                                                   Set<FHIRCodeSystemVersion> expansionVersions) {
		Set<FHIRCodeSystemVersion> snomed = new HashSet<>();
		Set<FHIRCodeSystemVersion> generic = new HashSet<>();

		for (FHIRCodeSystemVersion version : expansionVersions) {
			if (coding.getSystem().equals(version.getUrl()) &&
					(coding.getVersion() == null || version.isVersionMatch(coding.getVersion()))) {
				if (version.isOnSnomedBranch()) {
					snomed.add(version);
				} else {
					generic.add(version);
				}
			}
		}
		return new VersionPartition(snomed, generic);
	}

	private FHIRConcept findInSnomed(Coding coding,
	                                 Set<FHIRCodeSystemVersion> snomedVersions,
	                                 CodeSelectionCriteria criteria,
	                                 List<LanguageDialect> dialects) {

		if (snomedVersions.isEmpty()) return null;

		QueryService.ConceptQueryBuilder query = null;

		for (FHIRCodeSystemVersion snomedVersion : snomedVersions) {
			if (query == null) {
				query = getSnomedConceptQuery(null, false, criteria, dialects);
			}
			query.conceptIds(Collections.singleton(coding.getCode()));

			List<ConceptMini> minis = snomedQueryService
					.search(query, snomedVersion.getSnomedBranch(), PAGE_OF_ONE)
					.getContent();

			if (!minis.isEmpty()) {
				return new FHIRConcept(minis.get(0), snomedVersion, true);
			}
		}
		return null;
	}

	private FHIRConcept findInGeneric(Coding coding,
	                                  Set<FHIRCodeSystemVersion> genericVersions,
	                                  CodeSelectionCriteria criteria) {

		if (genericVersions.isEmpty()) return null;

		BoolQuery.Builder query = getFhirConceptQuery(criteria, null);
		boolean caseSensitive = genericVersions.stream().anyMatch(FHIRCodeSystemVersion::isCaseSensitive);

		addCodeConstraintToQuery(coding, caseSensitive, query);

		List<FHIRConcept> concepts = conceptService.findConcepts(query, PAGE_OF_ONE).getContent();
		return concepts.isEmpty() ? null : concepts.get(0);
	}

	private record VersionPartition(Set<FHIRCodeSystemVersion> snomed,
	                                Set<FHIRCodeSystemVersion> generic) {}


	public QueryService.ConceptQueryBuilder getSnomedConceptQuery(String filter, boolean activeOnly, CodeSelectionCriteria codeSelectionCriteria,
	                                                               List<LanguageDialect> languageDialects) {

		QueryService.ConceptQueryBuilder conceptQuery = snomedQueryService.createQueryBuilder(false);
		if (codeSelectionCriteria.isAnyECL()) {
			// ECL search
			String ecl = inclusionExclusionClausesToEcl(codeSelectionCriteria);
			conceptQuery.ecl(ecl);
		} else {
			// Just a set of concept codes
			Set<String> codes = new HashSet<>();
			codeSelectionCriteria.getInclusionConstraints().values().stream().flatMap(andConstraints -> andConstraints.constraintsFlattened().stream()).forEach(include -> codes.addAll(include.getCodes()));
			codeSelectionCriteria.getExclusionConstraints().values().stream().flatMap(andConstraints -> andConstraints.constraintsFlattened().stream()).forEach(include -> codes.removeAll(include.getCodes()));
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
	public BoolQuery.Builder getFhirConceptQuery(CodeSelectionCriteria codeSelectionCriteria, String termFilter) {
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
		Map<FHIRCodeSystemVersion, AndConstraints> inclusionConstraints = constraintsService.combineConstraints(codeSelectionCriteria.getInclusionConstraints());
		Set<CodeSelectionCriteria> nestedSelections = constraintsService.combineConstraints(codeSelectionCriteria.getNestedSelections(), codeSelectionCriteria.getValueSetUserRef());
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

	public static void addCodeConstraintToQuery(Coding coding, boolean isCaseSensitive, BoolQuery.Builder fhirConceptQuery) {
		if (isCaseSensitive) {
			fhirConceptQuery.must(termQuery(FHIRConcept.Fields.CODE, coding.getCode()));
		} else {
			fhirConceptQuery.must(termQuery(FHIRConcept.Fields.CODE_LOWER, coding.getCode().toLowerCase()));
		}
	}

	private String inclusionExclusionClausesToEcl(CodeSelectionCriteria criteria) {
		StringBuilder ecl = new StringBuilder();

		appendInclusions(criteria, ecl);
		appendNested(criteria, ecl);

		if (ecl.isEmpty()) {
			// Fallback if there are no includes (compose.include is 1..*, so rare)
			ecl.append("*");
		}

		return applyExclusions(criteria, ecl).toString();
	}

	private void appendInclusions(CodeSelectionCriteria criteria, StringBuilder ecl) {
		if (criteria.getInclusionConstraints().isEmpty()) return;

		for (ConceptConstraint inclusion :
				criteria.getInclusionConstraints().values().iterator().next().constraintsFlattened()) {
			if (!ecl.isEmpty()) {
				ecl.append(" OR ");
			}
			ecl.append("( ").append(toEcl(inclusion)).append(" )");
		}
	}

	private void appendNested(CodeSelectionCriteria criteria, StringBuilder ecl) {
		if (criteria.getNestedSelections().isEmpty()) return;

		for (CodeSelectionCriteria nested : criteria.getNestedSelections()) {
			if (!ecl.isEmpty()) {
				ecl.append(" OR ");
			}
			ecl.append("( ").append(inclusionExclusionClausesToEcl(nested)).append(" )");
		}
	}

	private StringBuilder applyExclusions(CodeSelectionCriteria criteria, StringBuilder ecl) {
		if (criteria.getExclusionConstraints().isEmpty()) return ecl;

		// Wrap existing ECL before applying exclusions
		StringBuilder result = new StringBuilder().append("( ").append(ecl).append(" )");
		for (ConceptConstraint exclusion :
				criteria.getExclusionConstraints().values().iterator().next().constraintsFlattened()) {
			result.append(" MINUS ( ").append(exclusion.getEcl()).append(" )");
		}
		return result;
	}

	private String toEcl(ConceptConstraint inclusion) {
		if (inclusion.hasEcl()) {
			return inclusion.getEcl();
		}
		return String.join(" OR ", inclusion.getCodes());
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

	private void addQueryCriteria(ConceptConstraint inclusion,
	                              BoolQuery.Builder versionQuery,
	                              String valueSetUserRef) {

		if (Boolean.TRUE.equals(inclusion.isActiveOnly())) {
			versionQuery.mustNot(termsQuery(
					FHIRConcept.Fields.PROPERTIES + ".inactive.value",
					Collections.singleton("true")));
		}

		if (inclusion.getCodes() != null) {
			handleCodeConstraint(inclusion, versionQuery);
		} else if (inclusion.getParent() != null) {
			handleSimpleConstraint(inclusion, versionQuery, FHIRConcept.Fields.PARENTS, inclusion.getParent());
		} else if (inclusion.getAncestor() != null) {
			handleSimpleConstraint(inclusion, versionQuery, FHIRConcept.Fields.ANCESTORS, inclusion.getAncestor());
		} else if (inclusion.getProperties() != null) {
			handlePropertyConstraints(inclusion, versionQuery);
		} else if (inclusion.isActiveOnly() != null) {
			// valid case: constraint only contains activeOnly
		} else {
			String message = "Unrecognised constraints for ValueSet: " + valueSetUserRef;
			logger.error(message);
			throw exception(message, OperationOutcome.IssueType.EXCEPTION, 500);
		}
	}

	private void handleCodeConstraint(ConceptConstraint inclusion, BoolQuery.Builder query) {
		switch (inclusion.getType()) {
			case MATCH_REGEX ->
					query.must(regexpQuery(FHIRConcept.Fields.CODE, firstOrNull(inclusion.getCodes())));
			default ->
					query.must(termsQuery(FHIRConcept.Fields.CODE, inclusion.getCodes()));
		}
	}

	private void handleSimpleConstraint(ConceptConstraint inclusion,
	                                    BoolQuery.Builder query,
	                                    String field,
	                                    Set<String> values) {
		switch (inclusion.getType()) {
			case MATCH_REGEX ->
					query.must(regexpQuery(field, firstOrNull(values)));
			default ->
					query.must(termsQuery(field, values));
		}
	}

	private void handlePropertyConstraints(ConceptConstraint inclusion,
	                                       BoolQuery.Builder query) {
		inclusion.getProperties().forEach((key, values) -> {
			String baseField = FHIRConcept.Fields.PROPERTIES + "." + key + ".value";
			switch (inclusion.getType()) {
				case MATCH_REGEX ->
						query.must(regexpQuery(baseField, firstOrNull(values)));
				case INCLUDE_EXACT_MATCH ->
						query.must(termsQuery(baseField + ".keyword", values));
				case EXCLUDE_EXACT_MATCH ->
						query.mustNot(termsQuery(baseField + ".keyword", values));
				default ->
						query.must(termsQuery(baseField, values));
			}
		});
	}

	private String firstOrNull(Collection<String> values) {
		return values == null ? null : values.stream().findFirst().orElse(null);
	}


}
