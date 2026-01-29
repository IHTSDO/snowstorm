package org.snomed.snowstorm.fhir.services;

import com.google.common.base.Strings;
import org.hl7.fhir.r4.model.*;
import org.snomed.snowstorm.core.data.domain.ConceptMini;
import org.snomed.snowstorm.core.data.domain.ReferenceSetMember;
import org.snomed.snowstorm.core.data.services.ReferenceSetMemberService;
import org.snomed.snowstorm.core.data.services.pojo.MemberSearchRequest;
import org.snomed.snowstorm.core.data.services.pojo.PageWithBucketAggregations;
import org.snomed.snowstorm.fhir.config.FHIRConstants;
import org.snomed.snowstorm.fhir.domain.*;
import org.snomed.snowstorm.fhir.pojo.CanonicalUri;
import org.snomed.snowstorm.fhir.services.context.CodeSystemVersionProvider;
import org.snomed.snowstorm.rest.ControllerHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static org.snomed.snowstorm.core.data.services.ReferenceSetMemberService.AGGREGATION_MEMBER_COUNTS_BY_REFERENCE_SET;
import static org.snomed.snowstorm.fhir.domain.ConceptConstraint.Type.INCLUDE_EXACT_MATCH;
import static org.snomed.snowstorm.fhir.services.FHIRHelper.createOperationOutcomeWithIssue;
import static org.snomed.snowstorm.fhir.services.FHIRHelper.exception;
import static org.snomed.snowstorm.fhir.services.FHIRValueSetFinderService.REFSETS_WITH_MEMBERS;
import static org.snomed.snowstorm.fhir.services.FHIRValueSetService.*;

@Service
public class FHIRValueSetConstraintsService implements FHIRConstants {

	@Autowired
	private ReferenceSetMemberService snomedRefsetService;

	@Autowired
	private FHIRValueSetFinderService vsFinderService;

	private enum CodeSystemType { SNOMED, LOINC, ICD, GENERIC }

	private final Map<String, Set<String>> codeSystemVersionToRefsetsWithMembersCache = new HashMap<>();

	CodeSelectionCriteria generateInclusionExclusionConstraints(
			ValueSet valueSet,
			CodeSystemVersionProvider codeSystemVersionProvider,
			boolean activeOnly,
			boolean isExpandFlow) {
		CodeSelectionCriteria criteria = new CodeSelectionCriteria(getUserRef(valueSet));
		ValueSet.ValueSetComposeComponent compose = valueSet.getCompose();
		activeOnly = adjustActiveOnlyFlag(compose, activeOnly, isExpandFlow);

		processIncludes(valueSet, codeSystemVersionProvider, activeOnly, isExpandFlow, criteria, compose.getInclude());
		processExcludes(valueSet, activeOnly, criteria, compose.getExclude());
		return criteria;
	}

	private boolean adjustActiveOnlyFlag(ValueSet.ValueSetComposeComponent compose,
	                                     boolean activeOnly,
	                                     boolean isExpandFlow) {
		if (!activeOnly && isExpandFlow && compose.hasInactive()) {
			return !compose.getInactive();
		}
		return activeOnly;
	}

	private void processIncludes(ValueSet valueSet,
	                             CodeSystemVersionProvider codeSystemVersionProvider,
	                             boolean activeOnly,
	                             boolean isExpandFlow,
	                             CodeSelectionCriteria criteria,
	                             List<ValueSet.ConceptSetComponent> includes) {

		for (int i = 0; i < includes.size(); i++) {
			ValueSet.ConceptSetComponent include = includes.get(i);

			if (include.hasSystem()) {
				FHIRCodeSystemVersion csv = codeSystemVersionProvider.get(include.getSystem(), include.getVersion());
				AndConstraints constraints = criteria.addInclusion(csv);
				collectConstraints(valueSet, include, i, csv, constraints, activeOnly, true);
			} else if (include.hasValueSet()) {
				handleNestedValueSets(codeSystemVersionProvider, activeOnly, isExpandFlow, criteria, include);
			} else {
				throw exception("ValueSet clause has no system or nested value set",
						OperationOutcome.IssueType.INVARIANT, 400);
			}
		}
	}

	private void handleNestedValueSets(CodeSystemVersionProvider codeSystemVersionProvider,
	                                   boolean activeOnly,
	                                   boolean isExpandFlow,
	                                   CodeSelectionCriteria criteria,
	                                   ValueSet.ConceptSetComponent include) {
		for (CanonicalType canonicalType : include.getValueSet()) {
			CanonicalUri uri = CanonicalUri.fromString(canonicalType.getValueAsString());
			try {
				ValueSet nestedVs = vsFinderService.findOrThrow(uri.getSystem(), uri.getVersion()).getHapi();
				CodeSelectionCriteria nestedCriteria =
						generateInclusionExclusionConstraints(nestedVs, codeSystemVersionProvider, activeOnly, isExpandFlow);
				criteria.addNested(nestedCriteria);
			} catch (SnowstormFHIRServerResponseException e) {
				handleNestedValueSetException(uri, e);
			}
		}
	}

	private void handleNestedValueSetException(CanonicalUri uri,
	                                           SnowstormFHIRServerResponseException e) {
		if (e.getIssueCode() == OperationOutcome.IssueType.INVARIANT) {
			Extension ext = new Extension()
					.setUrl(MISSING_VALUESET)
					.setValue(new CanonicalType(uri.toString()));
			OperationOutcome oo = createOperationOutcomeWithIssue(
					new CodeableConcept(new Coding(TX_ISSUE_TYPE, NOT_FOUND, null))
							.setText(format("Unable to find included value set '%s' version '%s'",
									uri.getSystem(), uri.getVersion())),
					OperationOutcome.IssueSeverity.ERROR,
					null,
					OperationOutcome.IssueType.NOTFOUND,
					Collections.singletonList(ext),
					"$external:2$");
			throw new SnowstormFHIRServerResponseException(404, "ValueSet not found", oo);
		} else {
			throw e;
		}
	}

	private void processExcludes(ValueSet valueSet,
	                             boolean activeOnly,
	                             CodeSelectionCriteria criteria,
	                             List<ValueSet.ConceptSetComponent> excludes) {

		for (int i = 0; i < excludes.size(); i++) {
			ValueSet.ConceptSetComponent exclude = excludes.get(i);
			Set<FHIRCodeSystemVersion> allInclusions = criteria.gatherAllInclusionVersions();

			List<FHIRCodeSystemVersion> matchingVersions = allInclusions.stream()
					.filter(v -> v.getUrl().equals(exclude.getSystem())
							&& (exclude.getVersion() == null || exclude.getVersion().equals(v.getVersion())))
					.toList();

			for (FHIRCodeSystemVersion csv : matchingVersions) {
				AndConstraints constraints = criteria.addExclusion(csv);
				collectConstraints(valueSet, exclude, i, csv, constraints, activeOnly, false);
			}
		}
	}


	private void collectConstraints(ValueSet valueSet, ValueSet.ConceptSetComponent include, int includeIndex,
	                                FHIRCodeSystemVersion codeSystemVersion, AndConstraints andConstraints,
	                                boolean activeOnly, boolean isInclude) {

		// 1. Handle explicit concepts
		collectConceptConstraintsFromInclude(include, activeOnly, andConstraints);

		// 2. Handle filters
		collectConstraintsFromFilters(valueSet, include, includeIndex, codeSystemVersion, andConstraints, activeOnly, isInclude);

		// 3. Apply generic activeOnly fallback
		if (activeOnly && andConstraints.isEmpty() && isGenericConstraintApplicable(codeSystemVersion)) {
			ConceptConstraint constraint = new ConceptConstraint().setActiveOnly(activeOnly);
			andConstraints.addOrConstraints(Set.of(constraint));
		}
	}

	private void collectConceptConstraintsFromInclude(ValueSet.ConceptSetComponent include,
	                                                  boolean activeOnly, AndConstraints andConstraints) {
		if (!include.getConcept().isEmpty()) {
			Set<String> codes = include.getConcept().stream()
					.map(ValueSet.ConceptReferenceComponent::getCode)
					.collect(Collectors.toSet());
			ConceptConstraint constraint = new ConceptConstraint(codes).setActiveOnly(activeOnly);
			andConstraints.addOrConstraints(Set.of(constraint));
		}
	}

	private void collectConstraintsFromFilters(ValueSet valueSet, ValueSet.ConceptSetComponent include, int includeIndex,
	                                           FHIRCodeSystemVersion codeSystemVersion, AndConstraints andConstraints,
	                                           boolean activeOnly, boolean isInclude) {
		List<ValueSet.ConceptSetFilterComponent> filters = include.getFilter();
		for (int filterIndex = 0; filterIndex < filters.size(); filterIndex++) {
			ValueSet.ConceptSetFilterComponent filter = filters.get(filterIndex);
			String value = getFilterValueElseThrow(filter, valueSet, include.getSystem(),
					filter.getProperty(), filter.getOp().toCode(), includeIndex, filterIndex, isInclude);

			Set<ConceptConstraint> constraints = switch (getCodeSystemType(codeSystemVersion)) {
				case SNOMED -> handleSnomedFilter(filter.getProperty(), filter.getOp(), value, codeSystemVersion, activeOnly);
				case LOINC -> handleLoincFilter(filter.getProperty(), filter.getOp(), value);
				case ICD -> handleICDFilter();
				default -> handleGenericFilter(filter.getProperty(), filter.getOp(), value, activeOnly);
			};

			andConstraints.addOrConstraints(constraints);
		}
	}

	private boolean isGenericConstraintApplicable(FHIRCodeSystemVersion codeSystemVersion) {
		return !(FHIRHelper.isSnomedUri(codeSystemVersion.getUrl()) ||
				codeSystemVersion.getUrl().equals(FHIRConstants.LOINC_ORG) ||
				codeSystemVersion.getUrl().startsWith(FHIRConstants.HL_7_ORG_FHIR_SID_ICD_10));
	}

	private CodeSystemType getCodeSystemType(FHIRCodeSystemVersion version) {
		if (version.isOnSnomedBranch()) return CodeSystemType.SNOMED;
		if (version.getUrl().equals(FHIRConstants.LOINC_ORG)) return CodeSystemType.LOINC;
		if (version.getUrl().startsWith(FHIRConstants.HL_7_ORG_FHIR_SID_ICD_10)) return CodeSystemType.ICD;
		return CodeSystemType.GENERIC;
	}

	// --- SNOMED helpers ---
	private Set<ConceptConstraint> handleSnomedFilter(String property, ValueSet.FilterOperator op, String value,
	                                                  FHIRCodeSystemVersion version, boolean activeOnly) {
		if (Strings.isNullOrEmpty(value)) {
			throw exception("Value missing for SNOMED CT ValueSet filter '" + property + "'",
					OperationOutcome.IssueType.INVALID, 400);
		}
		return switch (property) {
			case "concept" -> handleSnomedConceptOperator(op, value, activeOnly);
			case "constraint" -> handleSnomedConstraintOperator(op, value);
			case "expression" -> handleSnomedExpressionOperator(op, value, version);
			case "expressions" -> handleSnomedExpressionsFlag(op, value);
			case "parent" -> handleSnomedParentFilter(op, value);
			default -> throw exception("Unexpected property '" + property + "' for SNOMED CT ValueSet filter.",
					OperationOutcome.IssueType.INVALID, 400);
		};
	}

	private Set<ConceptConstraint> handleSnomedConceptOperator(ValueSet.FilterOperator op, String value, boolean activeOnly) {
		return switch (op) {
			case ISA -> Set.of(new ConceptConstraint().setEcl("<< " + value));
			case DESCENDENTOF -> Set.of(new ConceptConstraint().setEcl("< " + value));
			case IN -> {
				String ecl = "^ " + value + (activeOnly ? " {{ C active=true }}" : "");
				yield Set.of(new ConceptConstraint().setEcl(ecl));
			}
			default -> throw exception(UNEXPECTED_OPERATION_QUOTE + op.toCode() + "' for SNOMED CT ValueSet 'concept' filter.",
					OperationOutcome.IssueType.INVALID, 400);
		};
	}

	private Set<ConceptConstraint> handleSnomedConstraintOperator(ValueSet.FilterOperator op, String value) {
		if (op != ValueSet.FilterOperator.EQUAL) {
			throw exception(UNEXPECTED_OPERATION_QUOTE + op.toCode() + "' for SNOMED CT ValueSet 'constraint' filter.",
					OperationOutcome.IssueType.INVALID, 400);
		}
		return Set.of(new ConceptConstraint().setEcl(value));
	}

	private Set<ConceptConstraint> handleSnomedExpressionOperator(ValueSet.FilterOperator op, String value, FHIRCodeSystemVersion version) {
		if (op != ValueSet.FilterOperator.EQUAL) {
			throw exception(UNEXPECTED_OPERATION_QUOTE + op.toCode() + "' for SNOMED CT ValueSet 'expression' filter.",
					OperationOutcome.IssueType.INVALID, 400);
		}
		if (REFSETS_WITH_MEMBERS.equals(value)) {
			return Set.of(new ConceptConstraint(findAllRefsetsWithActiveMembers(version)));
		} else {
			return Set.of(new ConceptConstraint().setEcl(value));
		}
	}

	private Set<ConceptConstraint> handleSnomedExpressionsFlag(ValueSet.FilterOperator op, String value) {
		if (op != ValueSet.FilterOperator.EQUAL) {
			throw exception(UNEXPECTED_OPERATION_QUOTE + op.toCode() + "' for SNOMED CT ValueSet 'expressions' flag.",
					OperationOutcome.IssueType.INVALID, 400);
		}
		if ("true".equalsIgnoreCase(value)) {
			throw exception("This server does not yet support SNOMED CT ValueSets with expressions.",
					OperationOutcome.IssueType.INVALID, 400);
		}
		return Collections.emptySet();
	}

	private Set<ConceptConstraint> handleSnomedParentFilter(ValueSet.FilterOperator op, String value) {
		if (op != ValueSet.FilterOperator.EQUAL) {
			throw exception(UNEXPECTED_OPERATION_QUOTE + op.toCode() + "' for SNOMED CT ValueSet 'parent' filter.",
					OperationOutcome.IssueType.INVALID, 400);
		}
		return Set.of(new ConceptConstraint().setEcl("<! " + value));
	}

	// --- LOINC helpers ---
	private Set<ConceptConstraint> handleLoincFilter(String property, ValueSet.FilterOperator op, String value) {
		if (Strings.isNullOrEmpty(value)) {
			throw exception("Value missing for LOINC ValueSet filter", OperationOutcome.IssueType.INVALID, 400);
		}
		Set<String> values = op == ValueSet.FilterOperator.IN ? new HashSet<>(Arrays.asList(value.split(","))) : Set.of(value);

		return switch (property) {
			case "parent" -> Set.of(new ConceptConstraint().setParent(values));
			case "ancestor" -> Set.of(new ConceptConstraint().setAncestor(values));
			default -> throw exception("This server does not support ValueSet filter using LOINC property '" + property + "'. Only parent and ancestor supported.", OperationOutcome.IssueType.NOTSUPPORTED, 400);
		};
	}

	// --- ICD helper ---
	private Set<ConceptConstraint> handleICDFilter() {
		throw exception("This server does not expect any ValueSet property filters for ICD-10.", OperationOutcome.IssueType.NOTSUPPORTED, 400);
	}

	// --- Generic helper ---
	private Set<ConceptConstraint> handleGenericFilter(String property, ValueSet.FilterOperator op, String value, boolean activeOnly) {
		Set<String> singleton = Set.of(value);
		ConceptConstraint constraint = new ConceptConstraint().setActiveOnly(activeOnly);

		switch (op) {
			case ISA -> {
				constraint.setCodes(singleton);
				ConceptConstraint ancestorConstraint = new ConceptConstraint().setActiveOnly(activeOnly).setAncestor(singleton);
				return Set.of(constraint, ancestorConstraint);
			}
			case DESCENDENTOF -> {
				constraint.setAncestor(singleton);
				return Set.of(constraint);
			}
			case EQUAL -> {
				Map<String, Collection<String>> props = Map.of(property, singleton);
				constraint.setProperties(props).setType(INCLUDE_EXACT_MATCH);
				return Set.of(constraint);
			}
			case REGEX -> {
				Set<String> regexSet = Set.of(normalizeRegexWhitespspace(value));
				if (CODE.equals(property)) constraint.setCodes(regexSet).setType(ConceptConstraint.Type.MATCH_REGEX);
				else constraint.setProperties(Map.of(property, regexSet)).setType(ConceptConstraint.Type.MATCH_REGEX);
				return Set.of(constraint);
			}
			case IN -> {
				Map<String, Collection<String>> props = Map.of(property, new HashSet<>(Arrays.asList(value.split(","))));
				constraint.setProperties(props).setType(INCLUDE_EXACT_MATCH);
				return Set.of(constraint);
			}
			case NOTIN -> {
				Map<String, Collection<String>> props = Map.of(property, new HashSet<>(Arrays.asList(value.split(","))));
				constraint.setProperties(props).setType(ConceptConstraint.Type.EXCLUDE_EXACT_MATCH);
				return Set.of(constraint);
			}
			default -> throw exception("This server does not support this ValueSet property filter on generic code systems.", OperationOutcome.IssueType.NOTSUPPORTED, 400);
		}
	}

	private String normalizeRegexWhitespspace(String value) {
		//Lucene doesn't like looking for tabs, newlines, so we'll just turn any checks for these into a general check for 'whitespace'
		//We could squish these back up, but leaving them potentially like \\s\\s\\s gives a clue as to what happened here
		return value.replace(" ","\\s").replace("\\t","\\s").replace("\\n","\\s").replace("\\r","\\s").replace("\\f","\\s");
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
					.toList();
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

	private static String getFilterValueElseThrow(ValueSet.ConceptSetFilterComponent filter, ValueSet valueSet, String system, String property, String op, int includeIndex, int filterIndex, boolean isInclude) {
		if(filter.getValue() != null) {
			return filter.getValue();
		}
		CodeableConcept cc = new CodeableConcept()
				.setCoding(List.of(new Coding(TX_ISSUE_TYPE, VS_INVALID, null)))
				.setText(format("The system %s filter with property = %s, op = %s has no value", system, property, op));
		String location = format("ValueSet['%s|%s].compose.%s[%d].filter[%d].value", valueSet.getUrl(), valueSet.getVersion(), isInclude ? "include" : "exclude", includeIndex, filterIndex);
		OperationOutcome operationOutcome = createOperationOutcomeWithIssue(cc, OperationOutcome.IssueSeverity.ERROR, location, OperationOutcome.IssueType.INVALID, null, null);
		throw new SnowstormFHIRServerResponseException(400, "Missing filter value", operationOutcome, null);
	}

	private static String getUserRef(ValueSet valueSet) {
		return valueSet.getUrl() != null ? valueSet.getUrl() : "inline value set";
	}

	public Map<FHIRCodeSystemVersion, AndConstraints> combineConstraints(Map<FHIRCodeSystemVersion, AndConstraints> constraints) {
		Map<FHIRCodeSystemVersion, AndConstraints> combinedConstraints = new HashMap<>();
		Map<FHIRCodeSystemVersion, ConceptConstraint> simpleConstraints = new HashMap<>();
		for (Map.Entry<FHIRCodeSystemVersion, AndConstraints> entry : constraints.entrySet()) {
			AndConstraints andConstraints = entry.getValue();
			AndConstraints newAndConstraints = new AndConstraints();
			for (AndConstraints.OrConstraints orConstraints : andConstraints.getAndConstraints()) {
				Set<ConceptConstraint> newOrConstraints = new HashSet<>();
				for(ConceptConstraint conceptConstraint: orConstraints.getOrConstraints()) {
					if (conceptConstraint.isSimpleCodeSet()) {
						simpleConstraints.computeIfAbsent(entry.getKey(), k -> new ConceptConstraint(new HashSet<>())).getCodes().addAll(conceptConstraint.getCodes());
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

	public Set<CodeSelectionCriteria> combineConstraints(Set<CodeSelectionCriteria> nestedSelections, String valueSetUserRef) {
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


}
