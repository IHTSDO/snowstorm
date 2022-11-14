package org.snomed.snowstorm.fhir.services.context;

import org.hl7.fhir.r4.model.OperationOutcome;
import org.snomed.snowstorm.fhir.domain.FHIRCodeSystemVersion;
import org.snomed.snowstorm.fhir.pojo.CanonicalUri;
import org.snomed.snowstorm.fhir.services.FHIRCodeSystemService;
import org.snomed.snowstorm.fhir.services.FHIRHelper;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static java.lang.String.format;
import static org.snomed.snowstorm.fhir.services.FHIRHelper.exception;
import static org.snomed.snowstorm.fhir.services.FHIRHelper.isSnomedUri;

public class CodeSystemVersionProvider {

	private final Map<CanonicalUri, FHIRCodeSystemVersion> codeSystemVersions;
	private final Set<CanonicalUri> systemVersionParam;
	private final CanonicalUri checkSystemVersion;
	private final CanonicalUri forceSystemVersion;
	private final CanonicalUri excludeSystem;
	private final FHIRCodeSystemService codeSystemService;

	public CodeSystemVersionProvider(Set<CanonicalUri> systemVersionParam, CanonicalUri checkSystemVersion, CanonicalUri forceSystemVersion, CanonicalUri excludeSystem,
			FHIRCodeSystemService codeSystemService) {

		this.systemVersionParam = systemVersionParam;
		this.checkSystemVersion = checkSystemVersion;
		this.forceSystemVersion = forceSystemVersion;
		this.excludeSystem = excludeSystem;
		this.codeSystemService = codeSystemService;
		this.codeSystemVersions = new HashMap<>();
	}

	public FHIRCodeSystemVersion get(String system, String version) {
		return codeSystemVersions.computeIfAbsent(CanonicalUri.of(system, version), (key) -> getCodeSystemVersion(system, version));
	}

	private FHIRCodeSystemVersion getCodeSystemVersion(String componentSystem, String componentVersion) {
		// Apply force system version if systems match
		if (forceSystemVersion != null && componentSystem.equals(forceSystemVersion.getSystem()) && forceSystemVersion.getVersion() != null) {
			componentVersion = forceSystemVersion.getVersion();

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

		// Validate check-system-version param
		if (checkSystemVersion != null && checkSystemVersion.getVersion() != null && checkSystemVersion.getSystem().equals(componentSystem)) {
			if (!codeSystemVersion.getVersion().equals(checkSystemVersion.getVersion())) {
				throw exception(format("ValueSet expansion includes CodeSystem '%s' version '%s', which does not match input parameter " +
								"check-system-version '%s' '%s'.", codeSystemVersion.getUrl(), codeSystemVersion.getVersion(),
						checkSystemVersion.getSystem(), checkSystemVersion.getVersion()), OperationOutcome.IssueType.INVALID, 400);
			}
		}

		return codeSystemVersion;
	}
}
