package org.snomed.snowstorm.fhir.services.context;

import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.StringType;
import org.snomed.snowstorm.fhir.domain.FHIRCodeSystemVersion;
import org.snomed.snowstorm.fhir.pojo.CanonicalUri;
import org.snomed.snowstorm.fhir.services.FHIRCodeSystemService;
import org.snomed.snowstorm.fhir.services.FHIRHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.lang.String.format;
import static org.snomed.snowstorm.fhir.services.FHIRHelper.exception;
import static org.snomed.snowstorm.fhir.services.FHIRHelper.isSnomedUri;
import static org.snomed.snowstorm.fhir.services.FHIRValueSetService.TX_ISSUE_TYPE;

public class CodeSystemVersionProvider {

	private final Map<CanonicalUri, FHIRCodeSystemVersion> codeSystemVersions;
	private final Set<CanonicalUri> systemVersionParam;
	private final Set<CanonicalUri> codingVersionHints;
	private final CanonicalUri checkSystemVersion;
	private final CanonicalUri forceSystemVersion;
	private final CanonicalUri excludeSystem;
	private final boolean allowCheckSystemVersionAsFallback;
	private final FHIRCodeSystemService codeSystemService;
	private final List<OperationOutcome.OperationOutcomeIssueComponent> versionCheckIssues;
	private boolean systemVersionWasUsedAsDefault;
	private boolean checkSystemVersionWasUsedAsDefault;

	public CodeSystemVersionProvider(Set<CanonicalUri> systemVersionParam, Set<CanonicalUri> codingVersionHints, CanonicalUri checkSystemVersion, CanonicalUri forceSystemVersion, CanonicalUri excludeSystem,
			boolean allowCheckSystemVersionAsFallback, FHIRCodeSystemService codeSystemService) {

		this.systemVersionParam = systemVersionParam;
		this.codingVersionHints = codingVersionHints != null ? codingVersionHints : Collections.emptySet();
		this.checkSystemVersion = checkSystemVersion;
		this.forceSystemVersion = forceSystemVersion;
		this.excludeSystem = excludeSystem;
		this.allowCheckSystemVersionAsFallback = allowCheckSystemVersionAsFallback;
		this.codeSystemService = codeSystemService;
		this.codeSystemVersions = new HashMap<>();
		this.versionCheckIssues = new ArrayList<>();
		this.systemVersionWasUsedAsDefault = false;
		this.checkSystemVersionWasUsedAsDefault = false;
	}

	public FHIRCodeSystemVersion get(String system, String version) {
		return codeSystemVersions.computeIfAbsent(CanonicalUri.of(system, version), (key) -> getCodeSystemVersion(system, version));
	}

	private FHIRCodeSystemVersion getCodeSystemVersion(String componentSystem, String componentVersion) {
		// Track whether the include specified a version explicitly
		boolean hadExplicitVersion = componentVersion != null;

		// Apply force system version if systems match
		boolean forceVersionWasApplied = false;
		if (forceSystemVersion != null && componentSystem.equals(forceSystemVersion.getSystem()) && forceSystemVersion.getVersion() != null) {
			componentVersion = forceSystemVersion.getVersion();
			forceVersionWasApplied = true;

			// Apply system version if no version set and systems match
		} else if (componentVersion == null) {
			String componentSystemTmp = componentSystem;
			CanonicalUri systemVersion = systemVersionParam.stream()
					.filter(canonicalUri1 -> componentSystemTmp.equals(canonicalUri1.getSystem()) ||
							(isSnomedUri(componentSystemTmp) && isSnomedUri(canonicalUri1.getSystem())))// Both SCT, may not be equal if one using xsct
					.findFirst().orElse(null);
			if (systemVersion != null) {
				componentVersion = systemVersion.getVersion();
				// Take system too, in case it's unversioned snomed (sctx)
				componentSystem = systemVersion.getSystem();
				systemVersionWasUsedAsDefault = true;
			} else if (allowCheckSystemVersionAsFallback && checkSystemVersion != null && checkSystemVersion.getVersion() != null &&
					(componentSystem.equals(checkSystemVersion.getSystem()) ||
					(isSnomedUri(componentSystem) && isSnomedUri(checkSystemVersion.getSystem())))) {
				// Fall back to check-system-version as a default, but only if that version actually exists on this server
				try {
					codeSystemService.findCodeSystemVersionOrThrow(
							FHIRHelper.getCodeSystemVersionParams(null, componentSystem, checkSystemVersion.getVersion(), null));
					componentVersion = checkSystemVersion.getVersion();
					// Take system too, in case it's unversioned snomed (sctx)
					componentSystem = checkSystemVersion.getSystem();
					checkSystemVersionWasUsedAsDefault = true;
				} catch (Exception e) {
					// Version not found on this server — fall through to use the server default version,
					// then validate against check-system-version below (which will produce a 422)
				}
			}
		}

		// When the include specifies a wildcard version (e.g. "1.x.x") and the CODING carries an
		// explicit version that matches the wildcard, use the coding's version so that e.g. a coding
		// with version "1.0.0" is validated against a ValueSet include whose version is "1.x.x".
		// Note: system-version / check-system-version hints are intentionally excluded here — they
		// should not override wildcard resolution; the wildcard resolves to the latest matching version
		// and the check constraint is applied afterwards.
		if (FHIRCodeSystemService.isWildcardVersion(componentVersion)) {
			final String componentSystemForWild = componentSystem;
			final String componentVersionForWild = componentVersion;
			CanonicalUri matchingHint = codingVersionHints.stream()
					.filter(h -> componentSystemForWild.equals(h.getSystem()) ||
							(isSnomedUri(componentSystemForWild) && isSnomedUri(h.getSystem())))
					.filter(h -> h.getVersion() != null &&
							FHIRCodeSystemService.versionMatchesPattern(h.getVersion(), componentVersionForWild))
					.findFirst().orElse(null);
			if (matchingHint != null) {
				componentVersion = matchingHint.getVersion();
				componentSystem = matchingHint.getSystem();
				systemVersionWasUsedAsDefault = true;
			}
		}

		FHIRCodeSystemVersion codeSystemVersion = codeSystemService.findCodeSystemVersionOrThrow(
				FHIRHelper.getCodeSystemVersionParams(null, componentSystem, componentVersion, null));

		// Apply exclude-system param
		if (excludeSystem != null && componentSystem.equals(excludeSystem.getSystem())) {
			String excludeSystemVersion = excludeSystem.getVersion();
			if (excludeSystemVersion == null || excludeSystemVersion.equals(codeSystemVersion.getVersion())) {
				return codeSystemVersion;
			}
		}

		// Validate check-system-version when the resolved version was NOT sourced from check-system-version itself.
		// This covers:
		//   (a) includes with an explicit version — same as before
		//   (b) versionless includes whose check-system-version was not found on the server (fell through to server default)
		// Force-system-version overrides take precedence over the check constraint for versionless includes.
		if (checkSystemVersion != null && checkSystemVersion.getVersion() != null && !checkSystemVersionWasUsedAsDefault &&
				(hadExplicitVersion || !forceVersionWasApplied) &&
				(checkSystemVersion.getSystem().equals(componentSystem) ||
				(isSnomedUri(checkSystemVersion.getSystem()) && isSnomedUri(componentSystem)))) {
			if (!FHIRCodeSystemService.versionMatchesPattern(codeSystemVersion.getVersion(), checkSystemVersion.getVersion())) {
				String message = format("The version '%s' is not allowed for system '%s': required to be '%s' by a version-check parameter",
						codeSystemVersion.getVersion(), codeSystemVersion.getUrl(), checkSystemVersion.getVersion());
				CodeableConcept detail = new CodeableConcept(new Coding(TX_ISSUE_TYPE, "version-error", null)).setText(message);
				List<Extension> extensions = Collections.singletonList(new Extension(
						"http://hl7.org/fhir/StructureDefinition/operationoutcome-message-id", new StringType("VALUESET_VERSION_CHECK")));
				// Build issue WITHOUT location/expression
				OperationOutcome.OperationOutcomeIssueComponent issue = new OperationOutcome.OperationOutcomeIssueComponent();
				issue.setSeverity(OperationOutcome.IssueSeverity.ERROR);
				issue.setCode(OperationOutcome.IssueType.EXCEPTION);
				issue.setDetails(detail);
				issue.setExtension(new ArrayList<>(extensions));
				versionCheckIssues.add(issue);
			}
		}

		return codeSystemVersion;
	}

	public List<OperationOutcome.OperationOutcomeIssueComponent> getVersionCheckIssues() {
		return versionCheckIssues;
	}

	public boolean isSystemVersionWasUsedAsDefault() {
		return systemVersionWasUsedAsDefault;
	}

	public boolean isCheckSystemVersionWasUsedAsDefault() {
		return checkSystemVersionWasUsedAsDefault;
	}

}
