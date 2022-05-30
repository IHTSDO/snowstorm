package org.snomed.snowstorm.fhir.pojo;

import org.hl7.fhir.r4.model.StringType;
import org.snomed.snowstorm.fhir.services.FHIRHelper;

import static org.snomed.snowstorm.fhir.config.FHIRConstants.SNOMED_URI;
import static org.snomed.snowstorm.fhir.config.FHIRConstants.SNOMED_URI_UNVERSIONED;
import static org.snomed.snowstorm.fhir.services.FHIRCodeSystemService.SCT_ID_PREFIX;

public class FHIRCodeSystemVersionParams {

	private final String codeSystem;
	private String snomedModule;
	private String version;
	private String id;

	public FHIRCodeSystemVersionParams(String codeSystem) {
		this.codeSystem = codeSystem;
	}

	public FHIRCodeSystemVersionParams(String codeSystem, String version) {
		this.codeSystem = codeSystem;
		this.version = version;
	}

	public boolean isSnomed() {
		return FHIRHelper.isSnomedUri(codeSystem) || (id != null && id.startsWith(SCT_ID_PREFIX));
	}

	public boolean isUnversionedSnomed() {
		return codeSystem != null && codeSystem.startsWith(SNOMED_URI_UNVERSIONED);
	}

	public StringType toSnomedUri() {
		if (codeSystem == null || !isSnomed()) {
			return null;
		}
		return new StringType(codeSystem + (version != null ? ( "/version/" + version ) : ""));
	}

	public boolean isUnspecifiedReleasedSnomed() {
		return id == null && codeSystem != null && codeSystem.equals(SNOMED_URI) && snomedModule == null;
	}

	public void setVersion(String version) {
		this.version = version;
	}

	public String getCodeSystem() {
		return codeSystem;
	}

	public String getVersion() {
		return version;
	}

	public void setSnomedModule(String snomedModule) {
		this.snomedModule = snomedModule;
	}

	public String getSnomedModule() {
		return snomedModule;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getId() {
		return id;
	}

	@Override
	public String toString() {
		return "CodeSystemParams{" +
				"id='" + id + '\'' +
				", system='" + codeSystem + '\'' +
				", version='" + version + '\'' +
				'}';
	}
}
