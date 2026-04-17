package org.snomed.snowstorm.fhir.services;

import ca.uhn.fhir.util.UrlUtil;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import org.hl7.fhir.r4.model.*;
import org.ihtsdo.otf.utils.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.snomed.snowstorm.core.pojo.LanguageDialect;
import org.snomed.snowstorm.fhir.domain.FHIRCodeSystemVersion;
import org.snomed.snowstorm.fhir.domain.FHIRConcept;
import org.snomed.snowstorm.fhir.domain.FHIRDesignation;
import org.snomed.snowstorm.fhir.domain.ValueSetCycleElement;
import org.snomed.snowstorm.fhir.pojo.CanonicalUri;
import org.snomed.snowstorm.fhir.pojo.FHIRCodeSystemVersionParams;
import org.snomed.snowstorm.fhir.pojo.FHIRCodeValidationRequest;
import org.snomed.snowstorm.fhir.services.context.CodeSystemVersionProvider;
import org.snomed.snowstorm.rest.ControllerHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders.bool;
import static io.kaicode.elasticvc.helper.QueryHelper.termQuery;
import static java.lang.Boolean.TRUE;
import static java.lang.String.format;
import static org.ihtsdo.otf.RF2Constants.LANG_EN;
import static org.snomed.snowstorm.config.Config.PAGE_OF_ONE;
import static org.snomed.snowstorm.fhir.services.FHIRHelper.*;
import static org.snomed.snowstorm.fhir.services.FHIRValueSetFinderService.addCodeConstraintToQuery;
import static org.snomed.snowstorm.fhir.services.FHIRValueSetService.*;

@Service
public class FHIRValueSetCodeValidationService {

	public static final String CODE_NOT_IN_VS = "The provided code '%s' was not found in the value set '%s'";
	public static final String SYSTEM_CODE_NOT_IN_VS = "The provided code '%s#%s' was not found in the value set '%s'";
	public static final String CS_DEF_NOT_FOUND = "A definition for CodeSystem %s could not be found, so the code cannot be validated";
	public static final String DISPLAY_COMMENT = "display-comment";

	public static final String INVALID_CODE ="invalid-code";
	public static final String INVALID_DATA = "invalid-data";
	public static final String INVALID_DISPLAY = "invalid-display";

	public static final String X_UNKNOWN_SYSTEM = "x-unknown-system";

	@Autowired
	private FHIRValueSetCycleDetectionService cycleDetectionService;

	@Autowired
	private FHIRValueSetFinderService vsFinderService;

	@Autowired
	private FHIRCodeSystemService codeSystemService;

	@Autowired
	private FHIRConceptService conceptService;

	@Autowired
	private FHIRWarningsService warningsService;

	@Autowired
	private FHIRValueSetConstraintsService constraintsService;

	public Parameters validate(FHIRCodeValidationRequest request) {

		validateRequestParameters(request);

		ValueSet hapiValueSet = initialValueSetRecovery(request);

		initialExtensionValidation(hapiValueSet);

		if (hasDisplayLanguage(hapiValueSet) && request.getDisplayLanguage() == null) {
			request.setDisplayLanguage(hapiValueSet.getCompose().getExtensionByUrl(HL7_SD_VS_EXPANSION_PARAMETER).getExtensionString(VALUE));
		}

		List<Coding> codings = getCodings(request);

		// Coding system versions: the version explicitly specified in the coding (or via systemVersion bare param)
		Set<CanonicalUri> codingSystemVersions = codings.stream()
				.map(codingA -> CanonicalUri.of(codingA.getSystem(), codingA.getVersion()))
				.collect(Collectors.toSet());

		// System-version hints: canonical system|version from the system-version parameter.
		// These are used to resolve versionless valueset includes, and take priority over codingSystemVersions.
		Set<CanonicalUri> systemVersionHints = request.getDefaultSystemVersions();

		// Coding versions (non-null) that can act as fallback hints for resolving versionless includes
		Set<CanonicalUri> codingSystemVersionsWithVersion = codingSystemVersions.stream()
				.filter(c -> c.getVersion() != null)
				.collect(Collectors.toSet());

		// check-system-version acts as a version hint for versionless includes when:
		// (a) coding has no explicit version, OR
		// (b) all codings with explicit versions are unknown to this server (bad versions)
		boolean codingHasNoExplicitVersion = codings.stream().allMatch(c -> c.getVersion() == null);
		boolean allCodingVersionsUnknownToServer = !codingSystemVersionsWithVersion.isEmpty() &&
				codingSystemVersionsWithVersion.stream().allMatch(cv -> !codeSystemVersionExistsOnServer(cv));
		boolean allowCheckSystemVersionAsFallback = codingHasNoExplicitVersion || allCodingVersionsUnknownToServer;

		// Use system-version hints when provided; otherwise use coding versions as hints only if they are
		// actually known to the server. Unknown/bad versions must not be used as resolution hints because
		// they would pollute versionless include resolution (e.g. a bad "2.4.0" would shadow the default version).
		Set<CanonicalUri> systemVersionParamForProvider = (systemVersionHints != null && !systemVersionHints.isEmpty())
				? systemVersionHints : (allCodingVersionsUnknownToServer ? Collections.emptySet() : codingSystemVersionsWithVersion);

		CodeSystemVersionProvider codeSystemVersionProvider = new CodeSystemVersionProvider(systemVersionParamForProvider, codingSystemVersionsWithVersion, request.getCheckSystemVersion(), request.getForceSystemVersion(), null, allowCheckSystemVersionAsFallback, codeSystemService);
		// Collate set of inclusion and exclusion constraints for each code system version
		CodeSelectionCriteria codeSelectionCriteria;
		try{
			codeSelectionCriteria = constraintsService.generateInclusionExclusionConstraints(hapiValueSet, codeSystemVersionProvider, false, false);
		} catch (SnowstormFHIRServerResponseException e){
			if (OperationOutcome.IssueType.INVARIANT.equals(e.getIssueCode())
					&& !e.getOperationOutcome().getIssue().stream()
					.filter(i -> OperationOutcome.IssueType.INVARIANT.equals(i.getCode()))
					.flatMap( ex -> ex.getExtension().stream())
					.filter(ex -> ex.getUrl().equals(MISSING_VALUESET))
					.toList().isEmpty() ){
				String valueSetCanonical = e.getOperationOutcome().getIssue().stream().filter(i -> OperationOutcome.IssueType.INVARIANT.equals(i.getCode())).flatMap( ex -> ex.getExtension().stream()).filter(ex -> ex.getUrl().equals(MISSING_VALUESET)).map(ext -> ext.getValue().primitiveValue()).findFirst().orElse(null);
				Parameters response = new Parameters();
				if (request.getCodeableConcept()!=null){
					response.addParameter(CODEABLE_CONCEPT, request.getCodeableConcept());
				} else if (request.getCoding() != null){
					response.addParameter(CODE, new CodeType(request.getCoding().getCode()));
				} else {
					response.addParameter(CODE, new CodeType(request.getCode()));
				}

				String message = format("A definition for the value Set '%s' could not be found; Unable to check whether the code is in the value set '%s' because the value set %s was not found", valueSetCanonical,CanonicalUri.of(hapiValueSet.getUrl(),hapiValueSet.getVersion()), valueSetCanonical);
				response.addParameter(MESSAGE, message);
				response.addParameter(RESULT, false);
				if (request.getCoding() != null){
					response.addParameter(SYSTEM, new UriType(request.getCoding().getSystem()));
				}else {
					response.addParameter(SYSTEM, request.getSystem());
				}
				CodeableConcept detail1 = new CodeableConcept(new Coding(TX_ISSUE_TYPE, NOT_FOUND, null)).setText(format(VS_DEF_NOT_FOUND, valueSetCanonical));
				CodeableConcept detail2 = new CodeableConcept(new Coding(TX_ISSUE_TYPE, VS_INVALID, null)).setText(format("Unable to check whether the code is in the value set '%s' because the value set %s was not found",CanonicalUri.of(hapiValueSet.getUrl(), hapiValueSet.getVersion()),valueSetCanonical));
				OperationOutcome.OperationOutcomeIssueComponent[] issues = new OperationOutcome.OperationOutcomeIssueComponent[2];
				issues[0] = createOperationOutcomeIssueComponent(detail1, OperationOutcome.IssueSeverity.ERROR, null, OperationOutcome.IssueType.NOTFOUND, null, null);
				issues[1] = createOperationOutcomeIssueComponent(detail2, OperationOutcome.IssueSeverity.WARNING, null, OperationOutcome.IssueType.NOTFOUND, null, null);
				response.addParameter(createParameterComponentWithOperationOutcomeWithIssues(Arrays.asList(issues)));
				return response;

			} else if (OperationOutcome.IssueType.NOTFOUND.equals(e.getIssueCode()) && !e.getOperationOutcome().getIssue().stream().filter(i -> OperationOutcome.IssueType.NOTFOUND.equals(i.getCode())).flatMap( ex -> ex.getExtension().stream()).filter(ex -> ex.getUrl().equals("https://github.com/IHTSDO/snowstorm/available-codesystem-version")).toList().isEmpty()) {
				Parameters response = new Parameters();
				// Get the code being validated
				String theCode = request.getCoding() != null ? request.getCoding().getCode()
						: (request.getCodeableConcept() != null && !request.getCodeableConcept().getCoding().isEmpty()
								? request.getCodeableConcept().getCoding().get(0).getCode() : request.getCode());
				// For CodeableConcept requests echo the CC; for Coding/bare-code add the code param
				if (request.getCodeableConcept() != null) {
					response.addParameter(CODEABLE_CONCEPT, request.getCodeableConcept());
				} else {
					response.addParameter(CODE, new CodeType(theCode));
				}
				CanonicalUri missing = e.getOperationOutcome().getIssue().stream().flatMap(i -> i.getExtensionsByUrl("https://github.com/IHTSDO/snowstorm/missing-codesystem-version").stream()).map(ext -> CanonicalUri.fromString(ext.getValue().primitiveValue())).findFirst().orElse(CanonicalUri.fromString(""));
				String availableVersions = e.getOperationOutcome().getIssue().stream().flatMap(i -> i.getExtensionsByUrl("https://github.com/IHTSDO/snowstorm/available-codesystem-version").stream()).map(ext -> CanonicalUri.fromString(ext.getValue().primitiveValue()).getVersion()).filter(Objects::nonNull).collect(Collectors.joining(" or "));
				String availableVersion = availableVersions.isEmpty() ? null : availableVersions;
				final String requestedSystem;
				if (request.getSystem() != null && request.getSystem().getValue() != null) {
					requestedSystem = request.getSystem().getValue();
				} else if (request.getCoding() != null && request.getCoding().getSystem() != null) {
					requestedSystem = request.getCoding().getSystem();
				} else if (request.getCodeableConcept() != null && !request.getCodeableConcept().getCoding().isEmpty()) {
					requestedSystem = request.getCodeableConcept().getCoding().get(0).getSystem();
				} else {
					requestedSystem = missing.getSystem();
				}
				boolean valueSetSystemMatchesRequestedSystem = hapiValueSet.getCompose().getInclude().stream()
						.filter(ValueSet.ConceptSetComponent::hasSystem).map(ValueSet.ConceptSetComponent::getSystem)
						.anyMatch(vsCodeSystem -> Objects.equals(vsCodeSystem, requestedSystem));
				if (valueSetSystemMatchesRequestedSystem) {
					// Determine location prefix based on input type
					String locationPrefix = request.getCodeableConcept() != null ? "CodeableConcept.coding[0]."
							: request.getCoding() != null ? "Coding." : "";
					String versionLocation = locationPrefix + "version";
					String systemLocation = locationPrefix + "system";

					// Get the coding's explicit version (from coding/CC/bare-code systemVersion)
					String codingVersion;
					if (request.getCoding() != null) {
						codingVersion = request.getCoding().getVersion();
					} else if (request.getCodeableConcept() != null && !request.getCodeableConcept().getCoding().isEmpty()) {
						codingVersion = request.getCodeableConcept().getCoding().get(0).getVersion();
					} else {
						codingVersion = request.getSystemVersion();
					}

					// Determine whether the original VS include was versionless
					String originalIncludeVersion = hapiValueSet.getCompose().getInclude().stream()
							.filter(inc -> inc.getSystem() != null && inc.getSystem().equals(requestedSystem))
							.findFirst().map(inc -> inc.hasVersion() ? inc.getVersion() : null).orElse(null);
					boolean isVersionlessInclude = (originalIncludeVersion == null);

					// Determine hint version and fallback version
					String hintVersion = null;
					FHIRCodeSystemVersion fallback = null;

					if (!isVersionlessInclude) {
						// Explicit VS include version was bad — find the best fallback for display
						if (codingVersion != null) {
							fallback = codeSystemService.findCodeSystemVersion(
									FHIRHelper.getCodeSystemVersionParams(null, requestedSystem, codingVersion, null));
						}
						if (fallback == null && systemVersionHints != null) {
							for (CanonicalUri hint : systemVersionHints) {
								if (requestedSystem.equals(hint.getSystem()) && hint.getVersion() != null) {
									fallback = codeSystemService.findCodeSystemVersion(
											FHIRHelper.getCodeSystemVersionParams(null, requestedSystem, hint.getVersion(), null));
									if (fallback != null) break;
								}
							}
						}
						if (fallback == null && request.getCheckSystemVersion() != null && request.getCheckSystemVersion().getVersion() != null
								&& (requestedSystem.equals(request.getCheckSystemVersion().getSystem())
								|| (FHIRHelper.isSnomedUri(requestedSystem) && FHIRHelper.isSnomedUri(request.getCheckSystemVersion().getSystem())))) {
							fallback = codeSystemService.findCodeSystemVersion(
									FHIRHelper.getCodeSystemVersionParams(null, requestedSystem, request.getCheckSystemVersion().getVersion(), null));
						}
						if (fallback == null) {
							List<FHIRCodeSystemVersion> allVersions = codeSystemService.findAllVersionsByUrl(requestedSystem);
							allVersions.sort(Comparator.comparing(FHIRCodeSystemVersion::getVersion));
							fallback = allVersions.isEmpty() ? null : allVersions.get(allVersions.size() - 1);
						}
					} else {
						// Versionless VS include — determine hint (for CHANGED) and fallback
						if (request.getForceSystemVersion() != null && request.getForceSystemVersion().getVersion() != null
								&& (requestedSystem.equals(request.getForceSystemVersion().getSystem())
								|| (FHIRHelper.isSnomedUri(requestedSystem) && FHIRHelper.isSnomedUri(request.getForceSystemVersion().getSystem())))) {
							hintVersion = request.getForceSystemVersion().getVersion();
						} else if (systemVersionHints != null) {
							for (CanonicalUri hint : systemVersionHints) {
								if (requestedSystem.equals(hint.getSystem()) && hint.getVersion() != null) {
									hintVersion = hint.getVersion();
									break;
								}
							}
						}
						if (hintVersion == null && request.getCheckSystemVersion() != null && request.getCheckSystemVersion().getVersion() != null
								&& (requestedSystem.equals(request.getCheckSystemVersion().getSystem())
								|| (FHIRHelper.isSnomedUri(requestedSystem) && FHIRHelper.isSnomedUri(request.getCheckSystemVersion().getSystem())))) {
							hintVersion = request.getCheckSystemVersion().getVersion();
						}
						if (hintVersion != null) {
							fallback = codeSystemService.findCodeSystemVersion(
									FHIRHelper.getCodeSystemVersionParams(null, requestedSystem, hintVersion, null));
						}
						if (fallback == null) {
							List<FHIRCodeSystemVersion> allVersions = codeSystemService.findAllVersionsByUrl(requestedSystem);
							allVersions.sort(Comparator.comparing(FHIRCodeSystemVersion::getVersion));
							fallback = allVersions.isEmpty() ? null : allVersions.get(allVersions.size() - 1);
						}
					}

					// Look up display from fallback version
					if (fallback != null) {
						FHIRConcept fallbackConcept = conceptService.findConcept(fallback, theCode);
						if (fallbackConcept != null && fallbackConcept.getDisplay() != null) {
							response.addParameter(DISPLAY, fallbackConcept.getDisplay());
						}
					}

					// Build UNKNOWN issue
					String unknownMsgText = availableVersion != null
							? format("A definition for CodeSystem '%s' version '%s' could not be found, so the code cannot be validated. Valid versions: %s",
									requestedSystem, missing.getVersion(), availableVersion)
							: format("A definition for CodeSystem '%s' version '%s' could not be found, so the code cannot be validated",
									requestedSystem, missing.getVersion());
					Extension unknownExt = new Extension(HL7_SD_OUTCOME_MESSAGE_ID);
					unknownExt.setValue(new StringType("UNKNOWN_CODESYSTEM_VERSION"));
					CodeableConcept unknownDetail = new CodeableConcept(new Coding(TX_ISSUE_TYPE, NOT_FOUND, null)).setText(unknownMsgText);
					OperationOutcome.OperationOutcomeIssueComponent unknownIssue = createOperationOutcomeIssueComponent(
							unknownDetail, OperationOutcome.IssueSeverity.ERROR, systemLocation,
							e.getOperationOutcome().getIssueFirstRep().getCode(), List.of(unknownExt), null);

					List<OperationOutcome.OperationOutcomeIssueComponent> issueList = new ArrayList<>();
					String messageStr;

					if (!isVersionlessInclude) {
						// Explicit VS include version was bad
						if (codingVersion != null) {
							// Coding carries a version → MISMATCH + UNKNOWN
							String mismatchText = format("The code system '%s' version '%s' in the ValueSet include is different to the one in the value ('%s')",
									requestedSystem, originalIncludeVersion, codingVersion);
							Extension mismatchExt = new Extension(HL7_SD_OUTCOME_MESSAGE_ID);
							mismatchExt.setValue(new StringType("VALUESET_VALUE_MISMATCH"));
							CodeableConcept mismatchDetail = new CodeableConcept(new Coding(TX_ISSUE_TYPE, VS_INVALID, null)).setText(mismatchText);
							OperationOutcome.OperationOutcomeIssueComponent mismatchIssue = createOperationOutcomeIssueComponent(
									mismatchDetail, OperationOutcome.IssueSeverity.ERROR, versionLocation,
									OperationOutcome.IssueType.INVALID, List.of(mismatchExt), null);
							issueList.add(mismatchIssue);
							issueList.add(unknownIssue);
							messageStr = unknownMsgText + "; " + mismatchText;
						} else {
							// Coding has no version → UNKNOWN only
							issueList.add(unknownIssue);
							messageStr = unknownMsgText;
						}
					} else {
						// Versionless VS include — coding version was tried as hint and failed
						if (codingVersion != null) {
							if (hintVersion != null) {
								// A hint was present → CHANGED + UNKNOWN
								String mismatchText = format("The code system '%s' version '%s' resulting from the version '' in the ValueSet include is different to the one in the value ('%s')",
										requestedSystem, hintVersion, codingVersion);
								Extension mismatchExt = new Extension(HL7_SD_OUTCOME_MESSAGE_ID);
								mismatchExt.setValue(new StringType("VALUESET_VALUE_MISMATCH_CHANGED"));
								CodeableConcept mismatchDetail = new CodeableConcept(new Coding(TX_ISSUE_TYPE, VS_INVALID, null)).setText(mismatchText);
								OperationOutcome.OperationOutcomeIssueComponent mismatchIssue = createOperationOutcomeIssueComponent(
										mismatchDetail, OperationOutcome.IssueSeverity.ERROR, versionLocation,
										OperationOutcome.IssueType.INVALID, List.of(mismatchExt), null);
								// CHANGED first, UNKNOWN second
								issueList.add(mismatchIssue);
								issueList.add(unknownIssue);
								messageStr = unknownMsgText + "; " + mismatchText;
							} else {
								// No hint → UNKNOWN first, then DEFAULT warning
								String fallbackVersionStr = fallback != null ? fallback.getVersion() : "";
								String mismatchText = format("The code system '%s' version '%s' for the versionless include in the ValueSet include is different to the one in the value ('%s')",
										requestedSystem, fallbackVersionStr, codingVersion);
								Extension mismatchExt = new Extension(HL7_SD_OUTCOME_MESSAGE_ID);
								mismatchExt.setValue(new StringType("VALUESET_VALUE_MISMATCH_DEFAULT"));
								CodeableConcept mismatchDetail = new CodeableConcept(new Coding(TX_ISSUE_TYPE, VS_INVALID, null)).setText(mismatchText);
								OperationOutcome.OperationOutcomeIssueComponent mismatchIssue = createOperationOutcomeIssueComponent(
										mismatchDetail, OperationOutcome.IssueSeverity.WARNING, versionLocation,
										OperationOutcome.IssueType.INVALID, List.of(mismatchExt), null);
								// UNKNOWN first, DEFAULT second; DEFAULT warning not included in message
								issueList.add(unknownIssue);
								issueList.add(mismatchIssue);
								messageStr = unknownMsgText;
							}
						} else {
							// No coding version → UNKNOWN only
							issueList.add(unknownIssue);
							messageStr = unknownMsgText;
						}
					}

					response.addParameter(MESSAGE, messageStr);
					response.addParameter(RESULT, false);
					if (request.getCodeableConcept() == null) {
						response.addParameter(SYSTEM, new UriType(requestedSystem));
						if (fallback != null) {
							response.addParameter("version", fallback.getVersion());
						}
					}
					response.addParameter("x-caused-by-unknown-system", new CanonicalType(missing.toString()));
					response.addParameter(createParameterComponentWithOperationOutcomeWithIssues(issueList));
					return response;
				} else {
					CanonicalUri valueSetCanonical = CanonicalUri.of(hapiValueSet.getUrl(), hapiValueSet.getVersion());
					String codeSystemWithCode = (requestedSystem != null ? requestedSystem : missing.getSystem()) + "#" + theCode;
					OperationOutcome.OperationOutcomeIssueComponent[] issues = new OperationOutcome.OperationOutcomeIssueComponent[2];
					String message = format("A definition for CodeSystem %s could not be found, so the code cannot be validated; The provided code '%s' was not found in the value set '%s'", requestedSystem, codeSystemWithCode, valueSetCanonical);
					response.addParameter(MESSAGE, message);
					response.addParameter(RESULT, false);
					response.addParameter(SYSTEM, new UriType(requestedSystem));
					response.addParameter(X_UNKNOWN_SYSTEM, new CanonicalType(requestedSystem));
					Extension e1 = new Extension(HL7_SD_OUTCOME_MESSAGE_ID);
					e1.setValue(new StringType("None_of_the_provided_codes_are_in_the_value_set_one"));
					CodeableConcept detail1 = new CodeableConcept(new Coding(TX_ISSUE_TYPE, NOT_IN_VS, null)).setText(format(CODE_NOT_IN_VS, codeSystemWithCode, valueSetCanonical));
					issues[0] = createOperationOutcomeIssueComponent(detail1, OperationOutcome.IssueSeverity.ERROR, CODE, OperationOutcome.IssueType.CODEINVALID, List.of(e1), null);
					String text = format(CS_DEF_NOT_FOUND, requestedSystem);
					CodeableConcept detail2 = new CodeableConcept(new Coding(TX_ISSUE_TYPE, NOT_FOUND, null)).setText(text);
					Extension e2 = new Extension(HL7_SD_OUTCOME_MESSAGE_ID);
					e2.setValue(new StringType("UNKNOWN_CODESYSTEM"));
					issues[1] = createOperationOutcomeIssueComponent(detail2, OperationOutcome.IssueSeverity.ERROR, SYSTEM, OperationOutcome.IssueType.NOTFOUND, List.of(e2), null);
					response.addParameter(createParameterComponentWithOperationOutcomeWithIssues(Arrays.asList(issues)));
					return response;
				}
			} else if(OperationOutcome.IssueType.NOTFOUND.equals(e.getIssueCode()) && !e.getOperationOutcome().getIssue().stream().filter(i -> OperationOutcome.IssueType.NOTFOUND.equals(i.getCode())).flatMap( ex -> ex.getExtension().stream()).filter(ex -> ex.getUrl().equals(MISSING_VALUESET)).toList().isEmpty() ){
				Parameters response = new Parameters();
				String valueSetCanonical = e.getOperationOutcome().getIssue().stream().filter(i -> OperationOutcome.IssueType.NOTFOUND.equals(i.getCode())).flatMap( ex -> ex.getExtension().stream()).filter(ex -> ex.getUrl().equals(MISSING_VALUESET)).map(ext -> ext.getValue().primitiveValue()).findFirst().orElse(null);
				if(request.getCodeableConcept()!=null){
					response.addParameter(CODEABLE_CONCEPT, request.getCodeableConcept());
				}else if (request.getCoding() != null){
					response.addParameter(CODE, new CodeType(request.getCoding().getCode()));
				}else {
					response.addParameter(CODE, new CodeType(request.getCode()));
				}

				String message = format("A definition for the value Set '%s' could not be found; Unable to check whether the code is in the value set '%s' because the value set %s was not found", valueSetCanonical,CanonicalUri.of(hapiValueSet.getUrl(),hapiValueSet.getVersion()), valueSetCanonical);
				response.addParameter(MESSAGE, message);
				response.addParameter(RESULT, false);
				if (request.getCoding() != null){
					response.addParameter(SYSTEM, new UriType(request.getCoding().getSystem()));
				}else {
					response.addParameter(SYSTEM, request.getSystem());
				}
				CodeableConcept detail1 = new CodeableConcept(new Coding(TX_ISSUE_TYPE, NOT_FOUND, null)).setText(format(VS_DEF_NOT_FOUND, valueSetCanonical));
				CodeableConcept detail2 = new CodeableConcept(new Coding(TX_ISSUE_TYPE, VS_INVALID, null)).setText(format("Unable to check whether the code is in the value set '%s' because the value set %s was not found",CanonicalUri.of(hapiValueSet.getUrl(), hapiValueSet.getVersion()),valueSetCanonical));
				OperationOutcome.OperationOutcomeIssueComponent[] issues = new OperationOutcome.OperationOutcomeIssueComponent[2];
				issues[0] = createOperationOutcomeIssueComponent(detail1, OperationOutcome.IssueSeverity.ERROR, null, OperationOutcome.IssueType.NOTFOUND, null, null);
				issues[1] = createOperationOutcomeIssueComponent(detail2, OperationOutcome.IssueSeverity.WARNING, null, OperationOutcome.IssueType.NOTFOUND, null, null);
				response.addParameter(createParameterComponentWithOperationOutcomeWithIssues(Arrays.asList(issues)));
				return response;
			} else {
				throw e;
			}
		}

		List<OperationOutcome.OperationOutcomeIssueComponent> versionCheckIssues = codeSystemVersionProvider.getVersionCheckIssues();

		List<String> possibleSystems = codeSelectionCriteria.getInclusionConstraints().keySet().stream().map(cs -> cs.getUrl()).toList();
		if (request.getInferSystem() != null && request.getInferSystem().booleanValue()) {
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
		if(request.getCodeableConcept() != null) {
			Parameters.ParametersParameterComponent ccParameter = new Parameters.ParametersParameterComponent();
			ccParameter.setName(CODEABLE_CONCEPT);
			ccParameter.setValue(request.getCodeableConcept());
			response.addParameter(ccParameter);
		}
		response.addParameter(CODE, codingA.getCodeElement());
		if (codingA.getSystem() != null) {
			response.addParameter(SYSTEM, codingA.getSystemElement());
		}

		if (resolvedCodeSystemVersionsMatchingCodings.isEmpty()) {
			response.addParameter(RESULT, false);
			if (systemMatch) {
				if (codings.size() == 1) {
					// Find the valueset's CS versions for this coding's system.
					// For SNOMED with an explicit coding version (systemVersion), only include VS
					// versions whose version string contains the coding's module reference — this
					// ensures that when the VS uses a different SNOMED edition, vsVersionsForSystem
					// comes back empty and the "version not included" message (line 409) is used
					// rather than the generic mismatch message.
					final String codingASystem = Optional.ofNullable(codingA.getSystem()).orElse("");
					final String codingAVersion = codingA.getVersion();
					List<FHIRCodeSystemVersion> vsVersionsForSystem = codeSelectionCriteria.gatherAllInclusionVersions().stream()
							.filter(v -> codingASystem.equals(v.getUrl().replace("xsct", "sct")))
							.filter(v -> codingAVersion == null ||
									!FHIRHelper.isSnomedUri(codingASystem) ||
									v.getVersion() == null ||
									v.getVersion().equals(codingAVersion) ||
									v.getVersion().contains(codingAVersion))
							.collect(Collectors.toList());

					// Determine location prefix based on input type
					String locationPrefix = request.getCodeableConcept() != null ? "CodeableConcept.coding[0]."
							: request.getCoding() != null ? "Coding." : "";
					String versionLocation = locationPrefix + "version";
					String systemLocation = locationPrefix + "system";

					if (!vsVersionsForSystem.isEmpty()) {
						FHIRCodeSystemVersion vsVersion = vsVersionsForSystem.get(0);

						// Look up the code's display using the valueset's CS version
						List<LanguageDialect> mismatchDialects;
						try {
							mismatchDialects = ControllerHelper.parseAcceptLanguageHeader(request.getDisplayLanguage());
						} catch (IllegalArgumentException ex) {
							mismatchDialects = Collections.emptyList();
						}
						Coding codingForDisplay = codingA.copy().setVersion(vsVersion.getVersion());
						FHIRConcept concept = vsFinderService.findInValueSet(codingForDisplay,
								new HashSet<>(vsVersionsForSystem), codeSelectionCriteria, mismatchDialects);
						if (concept != null && concept.getDisplay() != null) {
							response.addParameter(DISPLAY, concept.getDisplay());
						}

						// Check whether the coding's explicit version actually exists in the DB
						boolean codingVersionExists = codingA.getVersion() != null && codeSystemService.findCodeSystemVersion(
								FHIRHelper.getCodeSystemVersionParams(null, codingA.getSystem(), codingA.getVersion(), null)) != null;

						// Determine if the original valueset include for this system was versionless
						// Note: findFirst() is called on the component stream (before mapping to a nullable String)
						// to avoid Optional.of(null) NPE when a versionless include maps to null.
						String originalIncludeVersion = hapiValueSet.getCompose().getInclude().stream()
								.filter(inc -> inc.getSystem() != null && inc.getSystem().equals(codingASystem))
								.findFirst()
								.map(inc -> inc.hasVersion() ? inc.getVersion() : null)
								.orElse(null);
						boolean isVersionlessInclude = (originalIncludeVersion == null);

						// Determine which mismatch message type to use
						// A hint was "applied" only if the provider actually used it for a versionless include.
						boolean hintWasApplied = codeSystemVersionProvider.isSystemVersionWasUsedAsDefault()
								|| codeSystemVersionProvider.isCheckSystemVersionWasUsedAsDefault()
								|| request.getForceSystemVersion() != null;
						final String mismatchMsg;
						final String mismatchMsgId;
						final OperationOutcome.IssueSeverity mismatchSeverity;

						if (hintWasApplied) {
							// system-version, check-system-version, or force-system-version hint was applied — use CHANGED variant
							mismatchMsgId = "VALUESET_VALUE_MISMATCH_CHANGED";
							mismatchSeverity = OperationOutcome.IssueSeverity.ERROR;
							// Determine the "hint version" shown in the message (the hint pattern, not the DB-resolved version)
							String hintVersion;
							if (request.getForceSystemVersion() != null && codingASystem.equals(request.getForceSystemVersion().getSystem())) {
								hintVersion = request.getForceSystemVersion().getVersion();
							} else if (codeSystemVersionProvider.isCheckSystemVersionWasUsedAsDefault()
									&& request.getCheckSystemVersion() != null
									&& codingASystem.equals(request.getCheckSystemVersion().getSystem())) {
								// check-system-version was used as fallback — use its pattern as the hint version
								hintVersion = request.getCheckSystemVersion().getVersion();
							} else if (systemVersionHints != null) {
								hintVersion = systemVersionHints.stream()
										.filter(h -> codingASystem.equals(h.getSystem()))
										.map(CanonicalUri::getVersion)
										.findFirst().orElse(vsVersion.getVersion());
							} else {
								hintVersion = vsVersion.getVersion();
							}
							mismatchMsg = format("The code system '%s' version '%s' resulting from the version '%s' in the ValueSet include is different to the one in the value ('%s')",
									codingA.getSystem(), hintVersion,
									originalIncludeVersion != null ? originalIncludeVersion : "",
									codingA.getVersion());
						} else if (!codingVersionExists && isVersionlessInclude) {
							// No hint, coding version doesn't exist, versionless include — DEFAULT variant (warning)
							mismatchMsgId = "VALUESET_VALUE_MISMATCH_DEFAULT";
							mismatchSeverity = OperationOutcome.IssueSeverity.WARNING;
							mismatchMsg = format("The code system '%s' version '%s' for the versionless include in the ValueSet include is different to the one in the value ('%s')",
									codingA.getSystem(), vsVersion.getVersion(), codingA.getVersion());
						} else {
							// Normal mismatch (coding version exists but differs, or versioned include)
							mismatchMsgId = "VALUESET_VALUE_MISMATCH";
							mismatchSeverity = OperationOutcome.IssueSeverity.ERROR;
							mismatchMsg = format("The code system '%s' version '%s' in the ValueSet include is different to the one in the value ('%s')",
									codingA.getSystem(), vsVersion.getVersion(), codingA.getVersion());
						}
						Extension mismatchExt = new Extension(HL7_SD_OUTCOME_MESSAGE_ID, new StringType(mismatchMsgId));
						CodeableConcept mismatchDetail = new CodeableConcept(new Coding(TX_ISSUE_TYPE, VS_INVALID, null)).setText(mismatchMsg);
						OperationOutcome.OperationOutcomeIssueComponent mismatchIssue = createOperationOutcomeIssueComponent(
								mismatchDetail, mismatchSeverity, versionLocation, OperationOutcome.IssueType.INVALID,
								List.of(mismatchExt), null);

						List<OperationOutcome.OperationOutcomeIssueComponent> mismatchIssues = new ArrayList<>();
						String messageStr;
						if (!codingVersionExists && codingA.getVersion() != null) {
							// Unknown CS version issue
							List<FHIRCodeSystemVersion> allVersions = codeSystemService.findAllVersionsByUrl(codingA.getSystem());
							allVersions.sort(Comparator.comparing(FHIRCodeSystemVersion::getVersion));
							String validVersions = allVersions.stream().map(FHIRCodeSystemVersion::getVersion)
									.collect(Collectors.joining(" or "));
							String unknownMsg = format("A definition for CodeSystem '%s' version '%s' could not be found, so the code cannot be validated. Valid versions: %s",
									codingA.getSystem(), codingA.getVersion(), validVersions);
							Extension unknownExt = new Extension(HL7_SD_OUTCOME_MESSAGE_ID, new StringType("UNKNOWN_CODESYSTEM_VERSION"));
							CodeableConcept unknownDetail = new CodeableConcept(new Coding(TX_ISSUE_TYPE, NOT_FOUND, null)).setText(unknownMsg);
							OperationOutcome.OperationOutcomeIssueComponent unknownIssue = createOperationOutcomeIssueComponent(
									unknownDetail, OperationOutcome.IssueSeverity.ERROR, systemLocation, OperationOutcome.IssueType.NOTFOUND,
									List.of(unknownExt), null);
							response.addParameter("x-caused-by-unknown-system",
									new CanonicalType(CanonicalUri.of(codingA.getSystem(), codingA.getVersion()).toString()));
							// Ordering: DEFAULT puts UNKNOWN first; CHANGED puts MISMATCH first
							if ("VALUESET_VALUE_MISMATCH_DEFAULT".equals(mismatchMsgId)) {
								mismatchIssues.add(unknownIssue);
								mismatchIssues.add(mismatchIssue);
								messageStr = unknownMsg;  // DEFAULT: only unknown in message (warning not included)
							} else {
								mismatchIssues.add(mismatchIssue);
								mismatchIssues.add(unknownIssue);
								messageStr = unknownMsg + "; " + mismatchMsg;
							}
						} else {
							mismatchIssues.add(mismatchIssue);
							messageStr = mismatchMsg;
						}

						// Prepend any VALUESET_VERSION_CHECK issues to the mismatch issues list
						if (!versionCheckIssues.isEmpty()) {
							for (OperationOutcome.OperationOutcomeIssueComponent vci : versionCheckIssues) {
								vci.addLocation(versionLocation);
								vci.addExpression(versionLocation);
							}
							List<OperationOutcome.OperationOutcomeIssueComponent> combined = new ArrayList<>(versionCheckIssues);
							combined.addAll(mismatchIssues);
							mismatchIssues = combined;
							String versionCheckMsg = versionCheckIssues.stream()
									.map(vci -> vci.getDetails().getText())
									.collect(Collectors.joining("; "));
							messageStr = messageStr + "; " + versionCheckMsg;
						}

						response.addParameter("version", vsVersion.getVersion());
						response.addParameter(createParameterComponentWithOperationOutcomeWithIssues(mismatchIssues));
						response.addParameter(MESSAGE, messageStr);
					} else {
						// System matched but no include version available
						response.addParameter(MESSAGE, format("The system '%s' is included in this ValueSet but the version '%s' is not.",
								codingA.getSystem(), codingA.getVersion()));
					}
				} else {
					response.addParameter(MESSAGE, "One or more codes in the CodableConcept are within a system included by this ValueSet but none of the versions match.");
				}
			} else {
				if (codings.size() == 1) {
					OperationOutcome.OperationOutcomeIssueComponent[] issues = new OperationOutcome.OperationOutcomeIssueComponent[3];
					if (Optional.ofNullable(codingA.getSystem()).orElse("").contains("ValueSet")) {
						CodeableConcept details1 = new CodeableConcept(new Coding(TX_ISSUE_TYPE, NOT_IN_VS, null)).setText(format(CODE_NOT_IN_VS, createFullyQualifiedCodeString(codingA), CanonicalUri.of(hapiValueSet.getUrl(), hapiValueSet.getVersion())));
						issues[0] = createOperationOutcomeIssueComponent(details1, OperationOutcome.IssueSeverity.ERROR, OUTCOME_CODING_CODE, OperationOutcome.IssueType.CODEINVALID, null, null);
						CodeableConcept details2 = new CodeableConcept(new Coding(TX_ISSUE_TYPE, INVALID_DATA, null)).setText(format("The Coding references a value set, not a code system ('%s')", codingA.getSystem()));
						issues[1] = createOperationOutcomeIssueComponent(details2, OperationOutcome.IssueSeverity.ERROR, OUTCOME_CODING_SYSTEM, OperationOutcome.IssueType.INVALID, null, null);
						response.addParameter(MESSAGE, format("The Coding references a value set, not a code system ('%s'); The provided code '%s' was not found in the value set '%s'", codingA.getSystem(), createFullyQualifiedCodeString(codingA), CanonicalUri.of(hapiValueSet.getUrl(), hapiValueSet.getVersion())));
					} else {
						String systemVersionCanonical = codingA.getSystem() + (codingA.getVersion() == null ? "" : "|" + codingA.getVersion());
						if (request.getCodeableConcept() != null) {
							CanonicalUri valueSetCanonicalUri = CanonicalUri.of(hapiValueSet.getUrl(), hapiValueSet.getVersion());
							issues[0] = createOperationOutcomeIssueComponent(new CodeableConcept().addCoding(new Coding(TX_ISSUE_TYPE, NOT_IN_VS, null)).setText(format("No valid coding was found for the value set '%s'", valueSetCanonicalUri)), OperationOutcome.IssueSeverity.ERROR, null, OperationOutcome.IssueType.CODEINVALID, null /* Collections.singletonList(new Extension(HL7_SD_OUTCOME_MESSAGE_ID,new StringType("None_of_the_provided_codes_are_in_the_value_set_one")))*/, null);
							List<FHIRCodeSystemVersion> knownCsVersions = codingA.getVersion() == null ? List.of() : codeSystemService.findAllVersionsByUrl(codingA.getSystem());
							String text = codingA.getVersion() == null
									? format(CS_DEF_NOT_FOUND, codingA.getSystem())
									: knownCsVersions.isEmpty()
											? format("A definition for CodeSystem '%s' version '%s' could not be found, so the code cannot be validated. No versions of this code system are known", codingA.getSystem(), codingA.getVersion())
											: format("A definition for CodeSystem '%s' version '%s' could not be found, so the code cannot be validated. Valid versions: %s",
													codingA.getSystem(), codingA.getVersion(),
													knownCsVersions.stream().map(FHIRCodeSystemVersion::getVersion).sorted().collect(Collectors.joining(" or ")));
							CodeableConcept details2 = new CodeableConcept(new Coding(TX_ISSUE_TYPE, NOT_FOUND, null)).setText(text);
							issues[1] = createOperationOutcomeIssueComponent(details2, OperationOutcome.IssueSeverity.ERROR, "CodeableConcept.coding[0].system", OperationOutcome.IssueType.NOTFOUND, null, null);
							String textIssue2 = format("The provided code '%s|%s#%s' was not found in the value set '%s'", codingA.getSystem(), codingA.getVersion(), codingA.getCode(), valueSetCanonicalUri);
							issues[2] = createOperationOutcomeIssueComponent(new CodeableConcept().addCoding(new Coding(TX_ISSUE_TYPE, "this-code-not-in-vs", null)).setText(textIssue2), OperationOutcome.IssueSeverity.INFORMATION, "CodeableConcept.coding[0].code", OperationOutcome.IssueType.CODEINVALID, null /* Collections.singletonList(new Extension(HL7_SD_OUTCOME_MESSAGE_ID,new StringType("None_of_the_provided_codes_are_in_the_value_set_one")))*/, null);
							String xUnknownSystem = (codingA.getVersion() != null && knownCsVersions.isEmpty()) ? codingA.getSystem() : systemVersionCanonical;
							response.addParameter(X_UNKNOWN_SYSTEM, new CanonicalType(xUnknownSystem));
							List<Parameters.ParametersParameterComponent> systemParameters = new ArrayList<>(response.getParameters(SYSTEM));
							systemParameters.forEach(systemParameter -> response.removeChild(PARAMETER, systemParameter));
							List<Parameters.ParametersParameterComponent> codeParameters = new ArrayList<>(response.getParameters(CODE));
							codeParameters.forEach(codeParameter -> response.removeChild(PARAMETER, codeParameter));
						} else if (request.getCoding() != null) {
							CodeableConcept details1 = new CodeableConcept(new Coding(TX_ISSUE_TYPE, NOT_IN_VS, null)).setText(format(CODE_NOT_IN_VS, createFullyQualifiedCodeString(codingA), CanonicalUri.of(hapiValueSet.getUrl(), hapiValueSet.getVersion())));
							issues[0] = createOperationOutcomeIssueComponent(details1, OperationOutcome.IssueSeverity.ERROR, OUTCOME_CODING_CODE, OperationOutcome.IssueType.CODEINVALID, null, null);

							if(codingA.getSystem()==null){
								CodeableConcept details2 = new CodeableConcept(new Coding(TX_ISSUE_TYPE, INVALID_DATA, null)).setText("Coding has no system. A code with no system has no defined meaning, and it cannot be validated. A system should be provided");
								issues[1] = createOperationOutcomeIssueComponent(details2, OperationOutcome.IssueSeverity.WARNING, OUTCOME_CODING, OperationOutcome.IssueType.INVALID, null, null);
							} else{
								CodeableConcept details2 = new CodeableConcept(new Coding(TX_ISSUE_TYPE, NOT_FOUND, null)).setText(format(CS_DEF_NOT_FOUND, codingA.getSystem()));
								issues[1] = createOperationOutcomeIssueComponent(details2, OperationOutcome.IssueSeverity.ERROR, OUTCOME_CODING_SYSTEM, OperationOutcome.IssueType.NOTFOUND, null, null);
								response.addParameter(X_UNKNOWN_SYSTEM, new CanonicalType(systemVersionCanonical));
							}
							if(codingA.getSystem()!= null && !UrlUtil.isAbsolute(codingA.getSystem())){
								CodeableConcept details3 = new CodeableConcept(new Coding(TX_ISSUE_TYPE, INVALID_DATA, null)).setText("Coding.system must be an absolute reference, not a local reference");
								issues[2] = createOperationOutcomeIssueComponent(details3, OperationOutcome.IssueSeverity.ERROR, OUTCOME_CODING_SYSTEM, OperationOutcome.IssueType.INVALID, null, null);
							}
						} else {
							CodeableConcept details1 = new CodeableConcept(new Coding(TX_ISSUE_TYPE, NOT_IN_VS, null)).setText(format(CODE_NOT_IN_VS, createFullyQualifiedCodeString(codingA), CanonicalUri.of(hapiValueSet.getUrl(), hapiValueSet.getVersion())));
							issues[0] = createOperationOutcomeIssueComponent(details1, OperationOutcome.IssueSeverity.ERROR, CODE, OperationOutcome.IssueType.CODEINVALID, null, null);
							CodeableConcept details2 = new CodeableConcept(new Coding(TX_ISSUE_TYPE, NOT_FOUND, null)).setText(format("A definition for CodeSystem '%s' could not be found, so the code cannot be validated", codingA.getSystem()));
							issues[1] = createOperationOutcomeIssueComponent(details2, OperationOutcome.IssueSeverity.ERROR, SYSTEM, OperationOutcome.IssueType.NOTFOUND, null, null);
						}
						if(codingA.getSystem()==null){
							response.addParameter(MESSAGE, format("Coding has no system. A code with no system has no defined meaning, and it cannot be validated. A system should be provided; The provided code '%s' was not found in the value set '%s'", createFullyQualifiedCodeString(codingA), CanonicalUri.of(hapiValueSet.getUrl(), hapiValueSet.getVersion())));
						} else if(codingA.getVersion() == null){
							response.addParameter(MESSAGE, format("A definition for CodeSystem %s could not be found, so the code cannot be validated; The provided code '%s' was not found in the value set '%s'", codingA.getSystem(), createFullyQualifiedCodeString(codingA), CanonicalUri.of(hapiValueSet.getUrl(), hapiValueSet.getVersion())));
						} else {
							CanonicalUri canonicalUriValueSet = CanonicalUri.of(hapiValueSet.getUrl(), hapiValueSet.getVersion());
							if (request.getCodeableConcept() != null) {
								List<FHIRCodeSystemVersion> knownCsVersions = codeSystemService.findAllVersionsByUrl(codingA.getSystem());
								String csVersionMsg = knownCsVersions.isEmpty()
										? format("A definition for CodeSystem '%s' version '%s' could not be found, so the code cannot be validated. No versions of this code system are known", codingA.getSystem(), codingA.getVersion())
										: format("A definition for CodeSystem '%s' version '%s' could not be found, so the code cannot be validated. Valid versions: %s", codingA.getSystem(), codingA.getVersion(), knownCsVersions.stream().map(FHIRCodeSystemVersion::getVersion).sorted().collect(Collectors.joining(" or ")));
								response.addParameter(MESSAGE, format("%s; No valid coding was found for the value set '%s'", csVersionMsg, canonicalUriValueSet));
							} else {
								String validVersionsList = codeSystemService.findAllVersionsByUrl(codingA.getSystem()).stream()
										.map(FHIRCodeSystemVersion::getVersion).sorted().collect(Collectors.joining(" or "));
								response.addParameter(MESSAGE, format("A definition for CodeSystem '%s' version '%s' could not be found, so the code cannot be validated. Valid versions: %s; No valid coding was found for the value set '%s'; The provided code '%s' was not found in the value set '%s'", codingA.getSystem(), codingA.getVersion(), validVersionsList, canonicalUriValueSet, createFullyQualifiedCodeString(codingA), canonicalUriValueSet));
							}
						}

					}
					response.addParameter(createParameterComponentWithOperationOutcomeWithIssues(Arrays.asList(issues)));
				} else{
					response.addParameter(MESSAGE, "None of the codes in the CodableConcept are within a system included by this ValueSet.");
				}


			}
			return response;
		}
		// Version will be added after findInValueSet so we can report the actual CS version where the code was found.
		// This matters for mixed ValueSets that include the same system at multiple versions (e.g. 1.0.0 with code1,
		// 1.2.0 with code2): resolvedCodeSystemVersionsMatchingCodings contains both, and we must pick the one
		// that matched the coding rather than a non-deterministic iterator().next().
		List<LanguageDialect> languageDialects;

		try {
			languageDialects = ControllerHelper.parseAcceptLanguageHeader(request.getDisplayLanguage());
		} catch(IllegalArgumentException e) {
			CodeableConcept cc = new CodeableConcept(new Coding()).setText(e.getMessage());
			OperationOutcome oo = createOperationOutcomeWithIssue(cc, OperationOutcome.IssueSeverity.ERROR,null, OperationOutcome.IssueType.PROCESSING,null, e.getMessage());
			throw new SnowstormFHIRServerResponseException(404, e.getMessage(),oo);
		}

		List<OperationOutcome.OperationOutcomeIssueComponent> issues = new ArrayList<>();
		for (int i = 0; i < codings.size(); i++) {
			codingA = codings.get(i);
			FHIRConcept concept = vsFinderService.findInValueSet(codingA, resolvedCodeSystemVersionsMatchingCodings, codeSelectionCriteria, languageDialects);
			if (concept != null) {
				// Report the version of the CS where the code was actually found.
				// When resolvedCodeSystemVersionsMatchingCodings has multiple entries (mixed VS with the same system
				// at different versions), use concept.getCodeSystemVersion() (the ES document ID) to identify the
				// correct FHIRCodeSystemVersion rather than taking a non-deterministic iterator().next().
				if (codings.size() == 1) {
					String conceptCsVersionId = concept.getCodeSystemVersion();
					FHIRCodeSystemVersion foundInVersion = resolvedCodeSystemVersionsMatchingCodings.stream()
							.filter(v -> v.getId().equals(conceptCsVersionId))
							.findFirst()
							.orElse(resolvedCodeSystemVersionsMatchingCodings.iterator().next());
					String version = foundInVersion.getVersion();
					if (!"0".equals(version)) {
						response.addParameter("version", version);
					}
				}
				if (FHIRHelper.isSnomedUri(codingA.getSystem())) {
					response.addParameter("inactive", !concept.isActive());
				} else if (!FHIRHelper.isSnomedUri(codingA.getSystem()) && !concept.isActive()){
					response.addParameter("inactive", true);
					if(request.getActiveOnly() != null && request.getActiveOnly().booleanValue() || (hapiValueSet.getCompose().hasInactive() && !hapiValueSet.getCompose().getInactive())) {
						String locationExpression = "Coding.code";
						String message = format(CODE_NOT_IN_VS, createFullyQualifiedCodeString(codingA),CanonicalUri.of(hapiValueSet.getUrl(),hapiValueSet.getVersion()));
						issues.add(createOperationOutcomeIssueComponent(new CodeableConcept(new Coding(TX_ISSUE_TYPE, "code-rule",null)).setText(format("The code '%s' is valid but is not active", codingA.getCode())), OperationOutcome.IssueSeverity.ERROR,locationExpression, OperationOutcome.IssueType.BUSINESSRULE,null,null));
						issues.add(createOperationOutcomeIssueComponent(new CodeableConcept(new Coding(TX_ISSUE_TYPE, NOT_IN_VS,null)).setText(message), OperationOutcome.IssueSeverity.ERROR, locationExpression, OperationOutcome.IssueType.CODEINVALID, null, null));
						response.setParameter(MESSAGE, message);
						response.setParameter(RESULT, false);
						continue;
					}
				}
				// Check if the concept is deprecated in the context of this ValueSet via valueset-deprecated extension
				final Coding finalCodingA = codingA;
				boolean deprecatedInValueSet = hapiValueSet.getCompose().getInclude().stream()
						.filter(include -> finalCodingA.getSystem() != null && finalCodingA.getSystem().equals(include.getSystem()))
						.flatMap(include -> include.getConcept().stream())
						.filter(ref -> finalCodingA.getCode().equals(ref.getCode()))
						.anyMatch(ref -> ref.getExtension().stream()
								.anyMatch(ext -> HL7_SD_VS_DEPRECATED.equals(ext.getUrl())
										&& ext.hasValue() && "true".equals(ext.getValue().primitiveValue())));
				if (deprecatedInValueSet) {
					String vsCanonical = CanonicalUri.of(hapiValueSet.getUrl(), hapiValueSet.getVersion()).toString();
					String deprecatedMessage = format("The presence of the concept '%s' in the system '%s' in the value set %s is marked with a status of deprecated and its use should be reviewed",
							codingA.getCode(), codingA.getSystem(), vsCanonical);
					issues.add(createOperationOutcomeIssueComponent(
							new CodeableConcept(new Coding(TX_ISSUE_TYPE, "code-comment", null)).setText(deprecatedMessage),
							OperationOutcome.IssueSeverity.WARNING, "Coding.code", OperationOutcome.IssueType.BUSINESSRULE, null, null));
				}
				if(!concept.getCode().equals(codingA.getCode()) && concept.getCode().equalsIgnoreCase(codingA.getCode())) {
					response.addParameter("normalized-code", new CodeType(concept.getCode()));
					FHIRCodeSystemVersion caseInSensitiveCodeSystem = resolvedCodeSystemVersionsMatchingCodings.stream().filter(fhirCodeSystemVersion -> !fhirCodeSystemVersion.isCaseSensitive()).findFirst().orElseThrow();
					issues.add(createOperationOutcomeIssueComponent(
							new CodeableConcept(new Coding(TX_ISSUE_TYPE, "code-rule",null))
									.setText(format("The code '%s' differs from the correct code '%s' by case. Although the code system '%s' is case insensitive, implementers are strongly encouraged to use the correct case anyway", codingA.getCode(), concept.getCode(), caseInSensitiveCodeSystem.getCanonical())), OperationOutcome.IssueSeverity.INFORMATION,null, OperationOutcome.IssueType.BUSINESSRULE,null,null
					));
				}
				List<ValueSet.ValueSetExpansionParameterComponent> codeSystemWarnings = warningsService.collectCodeSystemSetWarnings(resolvedCodeSystemVersionsMatchingCodings);
				codeSystemWarnings.forEach(warning ->
						issues.add(createOperationOutcomeIssueComponent(
								new CodeableConcept(new Coding(TX_ISSUE_TYPE, "status-check",null))
										.setText(format("Reference to %s CodeSystem %s",
												warning.getName().split(WARNING_DASH)[1],
												warning.getValue().primitiveValue())),
								OperationOutcome.IssueSeverity.INFORMATION,null,
								OperationOutcome.IssueType.BUSINESSRULE,null,null)));
				List<ValueSet.ValueSetExpansionParameterComponent> valueSetWarnings = warningsService.collectValueSetWarnings(codeSelectionCriteria);
				valueSetWarnings.forEach(warning ->
						issues.add(createOperationOutcomeIssueComponent(
								new CodeableConcept(new Coding(TX_ISSUE_TYPE, "status-check",null))
										.setText(format("Reference to %s ValueSet %s",
												warning.getName().split(WARNING_DASH)[1],
												warning.getValue().primitiveValue())),
								OperationOutcome.IssueSeverity.INFORMATION,null,
								OperationOutcome.IssueType.BUSINESSRULE,null,null
						)));

				String codingADisplay = codingA.getDisplay();
				if (codingADisplay == null || Objects.equals(codingADisplay, concept.getDisplay())) {
					setResultTrueIfNotFalseAlready(response);
					FHIRCodeSystemVersion codeSystemVersion = codeSystemService.findCodeSystemVersion(new FHIRCodeSystemVersionParams(codingA.getSystem()));
					if(concept.getDisplay()!=null){
						SelectedDisplay selectedDisplay = selectDisplay(codingA.getSystem(), request.getDisplayLanguage(), concept);
						response.addParameter(DISPLAY, selectedDisplay.selectedDisplayValue);
					}

					if((!languageDialects.isEmpty()) && languageDialects.stream().map(LanguageDialect::getLanguageCode).map(l -> {
						List<String> languages = new ArrayList<>(concept.getDesignations().stream().map(d -> d.getLanguage()).toList());
						if(codeSystemVersion.getLanguage()!=null) {
							languages.add(codeSystemVersion.getLanguage());
						}
						return languages.isEmpty()||languages.contains(l);
					} ).noneMatch(b ->b.equals(TRUE))){
						CodeableConcept cc;
						if(codeSystemVersion.getAvailableLanguages().contains(request.getDisplayLanguage())){ // should be true for this testcase
							cc = new CodeableConcept(new Coding().setSystem(TX_ISSUE_TYPE).setCode(DISPLAY_COMMENT)).setText(format("'%s' is the default display; no valid Display Names found for %s#%s in the language %s", concept.getDisplay(), codingA.getSystem(), codingA.getCode(), request.getDisplayLanguage()));
						} else {
							cc = new CodeableConcept(new Coding().setSystem(TX_ISSUE_TYPE).setCode(DISPLAY_COMMENT)).setText(format("'%s' is the default display; the code system %s has no Display Names for the language %s", concept.getDisplay(), codingA.getSystem(), request.getDisplayLanguage()));
						}

						issues.add(createOperationOutcomeIssueComponent(cc, OperationOutcome.IssueSeverity.INFORMATION, OUTCOME_CODING_DISPLAY, OperationOutcome.IssueType.INVALID, null, null));
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
								response.addParameter(DISPLAY, termMatch.getValue());
								resultOk = true;
							} else if (languageDialects.isEmpty() && !LANG_EN.equals(designationLanguage) && (
									(request.getDisplayLanguage() != null && !designationLanguage.equals(request.getDisplayLanguage())) || (hapiValueSet.getLanguage() != null && !designationLanguage.equals(hapiValueSet.getLanguage())))) {
								termMatch = null;
							} else if (languageDialects.isEmpty()){
								setResultTrueIfNotFalseAlready(response);
								response.addParameter(DISPLAY, concept.getDisplay());
								resultOk = true;
							}
						}
					}
					if(!resultOk) {
						String codeableConceptDisplay = request.getCodeableConcept() != null ? "CodeableConcept.coding[" + i + "].display" : DISPLAY;
						String locationExpression = request.getCoding() != null ? OUTCOME_CODING_DISPLAY : codeableConceptDisplay;
						OperationOutcome.IssueSeverity severity;
						if (request.getLenientDisplayValidation() != null && request.getLenientDisplayValidation().booleanValue()) {
							setResultTrueIfNotFalseAlready(response);
							severity = OperationOutcome.IssueSeverity.WARNING;
						} else {
							response.setParameter(RESULT, false);
							severity = OperationOutcome.IssueSeverity.ERROR;
						}
						if (termMatch != null) {
							response.addParameter(DISPLAY, concept.getDisplay());
							String message = format("The code '%s' was found in the ValueSet and the display matched the designation with term '%s', " +
											"however the language of the designation '%s' did not match any of the languages in the requested display language '%s'.",
									codingA.getCode(), termMatch.getValue(), termMatch.getLanguage(), request.getDisplayLanguage());
							response.addParameter(MESSAGE, message);
							CodeableConcept cc = new CodeableConcept();
							cc.setText(message);
							cc.addCoding(new Coding().setSystem(TX_ISSUE_TYPE).setCode(INVALID_DISPLAY));
							issues.add(createOperationOutcomeIssueComponent(cc, OperationOutcome.IssueSeverity.ERROR, locationExpression, OperationOutcome.IssueType.INVALID, null, null));
						} else {
							SelectedDisplay selectedDisplay = selectDisplay(codingA.getSystem(), request.getDisplayLanguage(), concept);
							response.addParameter(DISPLAY, selectedDisplay.selectedDisplayValue);
							CodeableConcept cc;
							if(selectedDisplay.languageAvailable == null) {
								String message = "Wrong Display Name '%s' for %s#%s. Valid display is '%s' (for the language(s) '%s')";
								response.addParameter(MESSAGE, format(message, codingA.getDisplay(), codingA.getSystem(), codingA.getCode(), selectedDisplay.selectedDisplayValue, request.getDisplayLanguage()==null?"--":request.getDisplayLanguage()));
								cc = new CodeableConcept(new Coding().setSystem(TX_ISSUE_TYPE).setCode(INVALID_DISPLAY)).setText(format(message, codingA.getDisplay(), codingA.getSystem(), codingA.getCode(), selectedDisplay.selectedDisplayValue, request.getDisplayLanguage()==null?"--":request.getDisplayLanguage()));
							} else if( selectedDisplay.isLanguageAvailable()) {
								if (request.getDisplayLanguage() == null && !concept.getDesignations().isEmpty()){
									String prefix = "Wrong Display Name '%s' for %s#%s. Valid display is one of %d choices: ";
									String languageFormat = "'%s' (%s)";
									String interfix = " or ";
									String suffix = " (for the language(s) '%s')";
									StringBuilder fullString = new StringBuilder();
									fullString.append(format(prefix, codingA.getDisplay(), codingA.getSystem(), codingA.getCode(), concept.getDesignations().size()+1));
									//add language of codesystem
									fullString.append(format(languageFormat, selectedDisplay.selectedDisplayValue, selectedDisplay.selectedLanguage));
									for (FHIRDesignation d : concept.getDesignations()){
										fullString.append(interfix)
												.append(format(languageFormat, d.getValue(), d.getLanguage()));
									}
									fullString.append(format(suffix,"--"));
									response.addParameter(MESSAGE, fullString.toString());
									cc = new CodeableConcept(new Coding().setSystem(TX_ISSUE_TYPE).setCode(INVALID_DISPLAY)).setText(fullString.toString());
								} else {
									String message = "Wrong Display Name '%s' for %s#%s. Valid display is '%s' (%s) (for the language(s) '%s')";
									response.addParameter(MESSAGE, format(message, codingA.getDisplay(), codingA.getSystem(), codingA.getCode(), selectedDisplay.selectedDisplayValue, selectedDisplay.selectedLanguage, request.getDisplayLanguage() != null ? request.getDisplayLanguage() : "--"));
									cc = new CodeableConcept(new Coding().setSystem(TX_ISSUE_TYPE).setCode(INVALID_DISPLAY)).setText(format(message, codingA.getDisplay(), codingA.getSystem(), codingA.getCode(), selectedDisplay.selectedDisplayValue, selectedDisplay.selectedLanguage, request.getDisplayLanguage() != null ? request.getDisplayLanguage() : "--"));
								}
							} else {
								String message = "Wrong Display Name '%s' for %s#%s. There are no valid display names found for language(s) '%s'. Default display is '%s'";
								response.addParameter(MESSAGE, format(message, codingA.getDisplay(), codingA.getSystem(), codingA.getCode(), request.getDisplayLanguage(), concept.getDisplay()));
								cc = new CodeableConcept(new Coding().setSystem(TX_ISSUE_TYPE).setCode(INVALID_DISPLAY)).setText(format(message, codingA.getDisplay(), codingA.getSystem(), codingA.getCode(), request.getDisplayLanguage(), concept.getDisplay()));
							}
							issues.add(createOperationOutcomeIssueComponent(cc, severity, locationExpression, OperationOutcome.IssueType.INVALID, null, null));
						}
					}
				}
			} else {
				response.setParameter(RESULT, false);
				final String locationExpression;
				String message;
				if (request.getCodeableConcept() != null) {
					List<Parameters.ParametersParameterComponent> codeParameters = new ArrayList<>(response.getParameters(CODE));
					codeParameters.forEach(v -> response.removeChild(PARAMETER, v));
					List<Parameters.ParametersParameterComponent> systemParameters = new ArrayList<>(response.getParameters(SYSTEM));
					systemParameters.forEach(v -> response.removeChild(PARAMETER, v));
					locationExpression = "CodeableConcept.coding[" + i + "].code";
					String text;
					if(DEFAULT_VERSION.equals(hapiValueSet.getVersion())) {
						text = format(SYSTEM_CODE_NOT_IN_VS, codingA.getSystem(), codingA.getCode(), hapiValueSet.getUrl());
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
						issues.add(createOperationOutcomeIssueComponent(new CodeableConcept().addCoding(new Coding(TX_ISSUE_TYPE, NOT_IN_VS, null)).setText(text), OperationOutcome.IssueSeverity.ERROR, null, OperationOutcome.IssueType.CODEINVALID, null, null));
					} else if(!(request.getValueSetMembershipOnly() != null && request.getValueSetMembershipOnly().booleanValue())) {
						if (request.getContext() == null && codings.size() < 2) {
							text = "No valid coding was found for the value set '%s|%s'".formatted(hapiValueSet.getUrl(), hapiValueSet.getVersion());
							issues.add(createOperationOutcomeIssueComponent(new CodeableConcept().addCoding(new Coding(TX_ISSUE_TYPE, NOT_IN_VS, null)).setText(text), OperationOutcome.IssueSeverity.ERROR, null, OperationOutcome.IssueType.CODEINVALID, null, null));

						}
						String details2 = format("Unknown code '%s' in the CodeSystem '%s' version '%s'", codingA.getCode(), codingA.getSystem(), resolvedCodeSystemVersionsMatchingCodings.isEmpty() ? null : resolvedCodeSystemVersionsMatchingCodings.iterator().next().getVersion());
						issues.add(createOperationOutcomeIssueComponent(new CodeableConcept().addCoding(new Coding(TX_ISSUE_TYPE, INVALID_CODE, null)).setText(details2), OperationOutcome.IssueSeverity.ERROR, locationExpression, OperationOutcome.IssueType.CODEINVALID, null, null));
					}
					message = format("No valid coding was found for the value set '%s'; The provided code '%s#%s' was not found in the value set '%s'",  hapiValueSet.getUrl(), codingA.getSystem(), codingA.getCode(), hapiValueSet.getUrl());
				} else if (request.getCoding() != null) {
					locationExpression = "Coding.code";
					String details = format(SYSTEM_CODE_NOT_IN_VS, codingA.getSystem(), codingA.getCode(), CanonicalUri.of(hapiValueSet.getUrl(), hapiValueSet.getVersion()));
					if(resolvedCodeSystemVersionsMatchingCodings.size() == 1 && codeSystemIncludesConcept(resolvedCodeSystemVersionsMatchingCodings.iterator().next(), codingA)) {
						issues.add(createOperationOutcomeIssueComponent(new CodeableConcept().addCoding(new Coding(TX_ISSUE_TYPE, NOT_IN_VS, null)).setText(details), OperationOutcome.IssueSeverity.ERROR, locationExpression, OperationOutcome.IssueType.CODEINVALID, null /* Collections.singletonList(new Extension(HL7_SD_OUTCOME_MESSAGE_ID,new StringType("None_of_the_provided_codes_are_in_the_value_set_one")))*/, null));
						message = format(SYSTEM_CODE_NOT_IN_VS,  codingA.getSystem(), codingA.getCode(), CanonicalUri.of(hapiValueSet.getUrl(), hapiValueSet.getVersion()));
					} else {
						issues.add(createOperationOutcomeIssueComponent(new CodeableConcept().addCoding(new Coding(TX_ISSUE_TYPE, NOT_IN_VS, null)).setText(details), OperationOutcome.IssueSeverity.ERROR, locationExpression, OperationOutcome.IssueType.CODEINVALID, Collections.singletonList(new Extension(HL7_SD_OUTCOME_MESSAGE_ID,new StringType("None_of_the_provided_codes_are_in_the_value_set_one"))), null));
						String details2 = format("Unknown code '%s' in the CodeSystem '%s' version '%s'", codingA.getCode(), codingA.getSystem(), resolvedCodeSystemVersionsMatchingCodings.isEmpty() ? null : resolvedCodeSystemVersionsMatchingCodings.iterator().next().getVersion());
						issues.add(createOperationOutcomeIssueComponent(new CodeableConcept().addCoding(new Coding(TX_ISSUE_TYPE, INVALID_CODE, null)).setText(details2), OperationOutcome.IssueSeverity.ERROR, locationExpression, OperationOutcome.IssueType.CODEINVALID, Collections.singletonList(new Extension(HL7_SD_OUTCOME_MESSAGE_ID,new StringType("Unknown_Code_in_Version"))), null));
						message = details + "; " + details2;
					}
				} else {
					locationExpression = CODE;
					message = format(SYSTEM_CODE_NOT_IN_VS,  codingA.getSystem(), codingA.getCode(), CanonicalUri.of(hapiValueSet.getUrl(), hapiValueSet.getVersion()));
					issues.add(createOperationOutcomeIssueComponent(new CodeableConcept().addCoding(new Coding(TX_ISSUE_TYPE, NOT_IN_VS, null)).setText(format("There was no valid code provided that is in the value set '%s'", CanonicalUri.of(hapiValueSet.getUrl(), hapiValueSet.getVersion()))), OperationOutcome.IssueSeverity.ERROR, locationExpression, OperationOutcome.IssueType.CODEINVALID, null, null));
					if(!codeSystemIncludesConcept(resolvedCodeSystemVersionsMatchingCodings.iterator().next(), codingA) && (request.getInferSystem() == null || !request.getInferSystem().booleanValue())) {
						issues.add(createOperationOutcomeIssueComponent(new CodeableConcept().addCoding(new Coding(TX_ISSUE_TYPE, INVALID_CODE, null)).setText(format("Unknown code '%s' in the CodeSystem '%s'", codingA.getCode(), codingA.getSystem())), OperationOutcome.IssueSeverity.ERROR, locationExpression, OperationOutcome.IssueType.CODEINVALID, null, null));
					}
				}
				response.setParameter(MESSAGE, message);

				if(request.getInferSystem() != null && request.getInferSystem().booleanValue()){
					issues.add(createOperationOutcomeIssueComponent(new CodeableConcept().addCoding(new Coding(TX_ISSUE_TYPE, "cannot-infer",null)).setText(message), OperationOutcome.IssueSeverity.ERROR, locationExpression, OperationOutcome.IssueType.NOTFOUND, null , null));
					List<Parameters.ParametersParameterComponent> systemParameters = new ArrayList<>(response.getParameters(SYSTEM));
					systemParameters.forEach(v -> response.removeChild(PARAMETER, v));
				}
			}
		}

		// Add any version-check issues collected during constraint generation
		// (Must be processed before the early-return below so they appear in all result paths)
		String versionLoc = (request.getCodeableConcept() != null ? "CodeableConcept.coding[0]."
				: request.getCoding() != null ? "Coding." : "") + "version";
		for (OperationOutcome.OperationOutcomeIssueComponent vci : versionCheckIssues) {
			vci.addLocation(versionLoc);
			vci.addExpression(versionLoc);
			issues.add(vci);
			// Force result to false
			response.setParameter(RESULT, false);
			// Add to message
			List<Parameters.ParametersParameterComponent> msgParams = response.getParameters(MESSAGE);
			String existingMsg = msgParams.isEmpty() ? null : ((StringType) msgParams.get(0).getValue()).getValue();
			String checkMsg = vci.getDetails().getText();
			String newMsg = existingMsg == null ? checkMsg : existingMsg + "; " + checkMsg;
			if (msgParams.isEmpty()) {
				response.addParameter(MESSAGE, newMsg);
			} else {
				response.setParameter(MESSAGE, newMsg);
			}
		}

		if(response.hasParameter(RESULT)) {
			if(!issues.isEmpty()) {
				response.addParameter(createParameterComponentWithOperationOutcomeWithIssues(issues));
			}
			return response;
		}

		if (codings.size() != 1) {
			response.addParameter(RESULT, false);
			response.addParameter(MESSAGE, "None of the codes in the CodableConcept were found in this ValueSet.");
			CodeableConcept cc = new CodeableConcept();
			issues.add(createOperationOutcomeIssueComponent(cc, OperationOutcome.IssueSeverity.INFORMATION, OUTCOME_CODING_DISPLAY, OperationOutcome.IssueType.INVALID, null , null));
		}

		if(!issues.isEmpty()) {
			response.addParameter(createParameterComponentWithOperationOutcomeWithIssues(issues));
		}
		return response;
	}

	private static @NotNull List<Coding> getCodings(FHIRCodeValidationRequest request) {
		// Get set of codings - one of which needs to be valid
		List<Coding> codings = new ArrayList<>();
		if (request.getCode() != null) {
			codings.add(new Coding(FHIRHelper.toString(request.getSystem()), request.getCode(), request.getDisplay()).setVersion(request.getSystemVersion()));
		} else if (request.getCoding() != null) {
			if (request.getDisplay()!=null) {
				request.getCoding().setDisplay(request.getDisplay());
			}
			codings.add(request.getCoding());
		} else {
			codings.addAll(request.getCodeableConcept().getCoding());
		}
		if (codings.isEmpty()) {
			throw exception("No codings provided to validate.", OperationOutcome.IssueType.INVALID, 400);
		}
		return codings;
	}

	private void initialExtensionValidation(ValueSet hapiValueSet) {
		hapiValueSet.getExtension().forEach(
				ext ->{
					if (ext.getUrl().equals(HL7_SD_VS_SUPPLEMENT)
							&& !codeSystemService.supplementExists(ext.getValue().primitiveValue(), false)) {
						String message = SUPPLEMENT_NOT_EXIST.formatted(ext.getValue().primitiveValue());
						CodeableConcept cc = new CodeableConcept(new Coding(TX_ISSUE_TYPE, NOT_FOUND, null)).setText(message);
						throw exception(message,OperationOutcome.IssueType.NOTFOUND, 404, null, cc);
					}
				});
	}

	private @NotNull ValueSet initialValueSetRecovery(FHIRCodeValidationRequest request) {
		// Grab ValueSet
		ValueSet hapiValueSet = vsFinderService.findOrInferValueSet(request.getId(), FHIRHelper.toString(request.getUrl()), request.getValueSet(), request.getValueSetVersion());
		if (hapiValueSet == null) {
			String vsCanonical = CanonicalUri.of(request.getUrl().getValue(), request.getValueSetVersion()).toString();
			CodeableConcept detail = new CodeableConcept(new Coding(TX_ISSUE_TYPE,NOT_FOUND,null)).setText(format(VS_DEF_NOT_FOUND, vsCanonical));
			throw exception(MESSAGE, OperationOutcome.IssueType.NOTFOUND,404,null, detail);
		}

		List<ValueSetCycleElement> valueSetCycle = cycleDetectionService.getValueSetIncludeExcludeCycle(hapiValueSet);
		if(!valueSetCycle.isEmpty()) {
			String message = cycleDetectionService.getCyclicDiagnosticMessage(valueSetCycle);
			throw exception(message, OperationOutcome.IssueType.PROCESSING, 400, null, new CodeableConcept(new Coding()).setText(message));
		}

		Optional.ofNullable(request.getVersionValueSet()).ifPresent(v->
				hapiValueSet.getCompose().getInclude().stream()
						.filter(ValueSet.ConceptSetComponent::hasValueSet)
						.flatMap(x->x.getValueSet().stream())
						.filter(x-> CanonicalUri.fromString(x.getValueAsString()).getSystem().equals(CanonicalUri.fromString(request.getVersionValueSet().getValueAsString()).getSystem()))
						.forEach( x -> x.setValueAsString(request.getVersionValueSet().getValueAsString()))
		);
		return hapiValueSet;
	}

	private static void validateRequestParameters(FHIRCodeValidationRequest request) {
		notSupported("context", request.getContext());
		notSupported("date", request.getDate());
		notSupported("abstract", request.getAbstractBool());

		requireExactlyOneOf(CODE, request.getCode(), CODING, request.getCoding(), CODEABLE_CONCEPT, request.getCodeableConcept());
		mutuallyRequired(CODE, request.getCode(), SYSTEM, request.getSystem(), "inferSystem", request.getInferSystem());
		mutuallyRequired(DISPLAY, request.getDisplay(), CODE, request.getCode(), CODING, request.getCoding());
	}

	private static void setResultTrueIfNotFalseAlready(Parameters response) {
		if(!response.hasParameter(RESULT)) {
			response.addParameter(RESULT, true);
		}
	}

	private boolean codeSystemVersionExistsOnServer(CanonicalUri codingVersion) {
		try {
			codeSystemService.findCodeSystemVersionOrThrow(
					FHIRHelper.getCodeSystemVersionParams(null, codingVersion.getSystem(), codingVersion.getVersion(), null));
			return true;
		} catch (SnowstormFHIRServerResponseException e) {
			return false;
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


	private static class SelectedDisplay {
		Boolean languageAvailable;
		String selectedLanguage;
		String selectedDisplayValue;

		SelectedDisplay(String value, String language, Boolean b) {
			selectedDisplayValue = value;
			selectedLanguage = language;
			languageAvailable = b;
		}

		boolean isLanguageAvailable() {
			return languageAvailable != null && languageAvailable;
		}
	}


}
